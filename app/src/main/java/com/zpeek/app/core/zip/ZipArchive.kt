package com.zpeek.app.core.zip

import com.zpeek.app.core.ArchiveEngine
import com.zpeek.app.core.ArchiveEntry
import com.zpeek.app.core.ArchiveException
import com.zpeek.app.core.ArchiveFormat
import com.zpeek.app.core.LimitedInputStream
import com.zpeek.app.core.PathUtil
import com.zpeek.app.core.RandomAccessSource
import com.zpeek.app.core.toCharsetStrict
import com.zpeek.app.core.skipTo
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * 纯 Kotlin 实现的 ZIP 读取器：直接解析中央目录 + 局部头，
 * 支持 Zip64、UTF-8 / GBK 文件名、按需 inflate，全程不落地任何中间文件。
 */
class ZipArchive(private val src: RandomAccessSource) : ArchiveEngine {

    override val format: ArchiveFormat get() = ArchiveFormat.ZIP

    private class Rec(
        val path: String,
        val dir: Boolean,
        val method: Int,
        val encrypted: Boolean,
        val size: Long,
        val packedSize: Long,
        val localOffset: Long,
        val time: Long,
    ) {
        val supported: Boolean get() = dir || (!encrypted && (method == 0 || method == 8))
    }

    private val records: List<Rec> by lazy { parseCentralDirectory() }
    private val cache: List<ArchiveEntry> by lazy {
        records.mapIndexed { i, r ->
            ArchiveEntry(r.path, r.dir, r.size, if (r.dir) 0 else r.packedSize, r.time, i, r.supported)
        }
    }

    override fun entries(): List<ArchiveEntry> = cache
    override val size: Int get() = cache.size

    // ------------------------------------------------------------------ 解析

    private fun parseCentralDirectory(): List<Rec> {
        val fileSize = src.size
        if (fileSize < 22) throw ArchiveException("文件过小，可能不是有效的 ZIP 压缩包")
        val tailLen = minOf(fileSize, 66_000L).toInt()
        val tailPos = fileSize - tailLen
        val tail = src.bytesAt(tailPos, tailLen)

        var eocd = -1
        var i = tail.size - 22
        while (i >= 0) {
            if (le32(tail, i) == EOCD_SIG) {
                eocd = i
                break
            }
            i--
        }
        if (eocd < 0) throw ArchiveException("未找到 ZIP 中央目录，文件可能已损坏或被截断")

        var total = (le16(tail, eocd + 10)).toLong()
        var cdSize = le32(tail, eocd + 12)
        var cdOffset = le32(tail, eocd + 16)

        if (cdOffset == U32_MAX || total == 0xFFFFL || cdSize == U32_MAX) {
            val locOff = eocd - 20
            if (locOff >= 0 && le32(tail, locOff) == ZIP64_LOCATOR_SIG) {
                val z64 = le64(tail, locOff + 8)
                if (z64 >= 0 && z64 + 56 <= fileSize) {
                    val head = src.bytesAt(z64, 56)
                    if (le32(head, 0) == ZIP64_EOCD_SIG) {
                        total = le64(head, 32)
                        cdSize = le64(head, 40)
                        cdOffset = le64(head, 48)
                    }
                }
            }
        }
        if (cdOffset < 0 || cdOffset >= fileSize) {
            if (total == 0L) return emptyList()
            throw ArchiveException("ZIP 中央目录偏移异常，文件可能已损坏")
        }

        val out = ArrayList<Rec>(minOf(total, 200_000L).toInt())
        var p = cdOffset
        var guard = 0
        while (out.size < total && guard < total + 16 && p + 46 <= fileSize) {
            guard++
            val h = src.bytesAt(p, 46)
            if (le32(h, 0) != CEN_SIG) break
            val flags = le16(h, 8)
            val method = le16(h, 10)
            val dosTime = le16(h, 12)
            val dosDate = le16(h, 14)
            var csize = le32(h, 20)
            var usize = le32(h, 24)
            val nameLen = le16(h, 28)
            val extraLen = le16(h, 30)
            val commentLen = le16(h, 32)
            var localOff = le32(h, 42)

            val nameBytes = if (nameLen > 0) src.bytesAt(p + 46, nameLen) else ByteArray(0)
            if (extraLen > 0) {
                val extra = src.bytesAt(p + 46 + nameLen, extraLen)
                var q = 0
                while (q + 4 <= extra.size) {
                    val hid = le16(extra, q)
                    val hsz = le16(extra, q + 2)
                    if (hid == 0x0001) {
                        var r = q + 4
                        val end = minOf(q + 4 + hsz, extra.size)
                        if (usize == U32_MAX && r + 8 <= end) { usize = le64(extra, r); r += 8 }
                        if (csize == U32_MAX && r + 8 <= end) { csize = le64(extra, r); r += 8 }
                        if (localOff == U32_MAX && r + 8 <= end) { localOff = le64(extra, r); r += 8 }
                        break
                    }
                    q += 4 + hsz
                }
            }

            val rawName = decodeName(nameBytes, flags)
            val path = PathUtil.normalize(rawName)
            p += 46L + nameLen + extraLen + commentLen

            if (path.isEmpty()) continue
            val dir = rawName.endsWith("/") || rawName.endsWith("\\")
            if (usize == 0L && csize == 0L && !dir && method == 0 && rawName.indexOf('.') < 0) {
                // 某些工具写出的空目录条目
            }
            out.add(
                Rec(
                    path = path,
                    dir = dir,
                    method = method,
                    encrypted = (flags and 0x0001) != 0,
                    size = if (usize == U32_MAX) 0L else usize.coerceAtLeast(0L),
                    packedSize = if (csize == U32_MAX) 0L else csize.coerceAtLeast(0L),
                    localOffset = localOff,
                    time = dosToMillis(dosDate, dosTime),
                )
            )
        }
        return out
    }

    private fun decodeName(bytes: ByteArray, flags: Int): String {
        if (bytes.isEmpty()) return ""
        if (flags and 0x0800 != 0) return String(bytes, Charsets.UTF_8)
        // 无 UTF-8 标记：先严格按 UTF-8 尝试，失败则按 GBK（中文压缩包最常见）
        return bytes.toCharsetStrict(Charsets.UTF_8) ?: runCatching {
            String(bytes, Charset.forName("GBK"))
        }.getOrElse { String(bytes, Charsets.ISO_8859_1) }
    }

    // ------------------------------------------------------------------ 读取

    private fun dataRange(r: Rec): Pair<Long, Long> {
        val local = r.localOffset
        if (local < 0 || local + 30 > src.size) {
            throw ArchiveException("条目「${r.path}」的局部文件头缺失，压缩包可能已损坏")
        }
        val lh = src.bytesAt(local, 30)
        if (le32(lh, 0) != LOC_SIG) {
            throw ArchiveException("条目「${r.path}」的局部文件头损坏")
        }
        val nameLen = le16(lh, 26)
        val extraLen = le16(lh, 28)
        val start = local + 30L + nameLen + extraLen
        var len = r.packedSize
        val avail = src.size - start
        if (len <= 0 || len > avail) len = avail
        return start to len
    }

    override fun openStream(entry: ArchiveEntry): InputStream = openStreamAt(entry, 0, entry.size)

    /**
     * position / length 均以**解压后**的数据为准。
     * 存储方式（method=0）可直接定位到压缩数据偏移；
     * deflate 必须从条目起点解压后丢弃前缀。
     */
    override fun openStreamAt(entry: ArchiveEntry, position: Long, length: Long): InputStream {
        val r = records.getOrNull(entry.index) ?: throw ArchiveException("无效的条目")
        if (r.dir) return ByteArray(0).inputStream()
        if (r.encrypted) throw ArchiveException("「${entry.name}」已加密，本应用暂不支持加密压缩包")
        if (!r.supported) throw ArchiveException("「${entry.name}」使用了不支持的压缩方式（method=${r.method}）")

        val (start, dataLen) = dataRange(r)
        val skip = position.coerceAtLeast(0)
        if (r.size > 0 && skip > r.size) throw ArchiveException("读取位置超出条目范围")

        if (r.method == 0) {
            if (skip > dataLen) throw ArchiveException("读取位置超出条目范围")
            val raw = src.stream(start + skip, if (length < 0) dataLen - skip else length)
            return if (length < 0) raw else LimitedInputStream(raw, length)
        }

        val inflated = InflateStream(src.stream(start, dataLen))
        if (skip > 0) inflated.skipTo(skip)
        return if (length < 0) inflated else LimitedInputStream(inflated, length)
    }

    /** Inflater 由外部创建，关闭时必须显式 end()，否则会泄漏 native 内存 */
    private class InflateStream(raw: InputStream) : InputStream() {
        private val inflater = Inflater(true)
        private val stream = InflaterInputStream(raw, inflater, 1 shl 15)
        private var closed = false

        override fun read(): Int = stream.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = stream.read(b, off, len)
        override fun available(): Int = stream.available()
        override fun skip(n: Long): Long = stream.skip(n)

        override fun close() {
            if (closed) return
            closed = true
            runCatching { stream.close() }
            runCatching { inflater.end() }
        }
    }

    // ------------------------------------------------------------------ 工具

    private fun dosToMillis(dosDate: Int, dosTime: Int): Long {
        if (dosDate == 0) return 0
        val year = ((dosDate shr 9) and 0x7F) + 1980
        val month = (dosDate shr 5) and 0x0F
        val day = dosDate and 0x1F
        val hour = (dosTime shr 11) and 0x1F
        val minute = (dosTime shr 5) and 0x3F
        val second = (dosTime and 0x1F) * 2
        if (month !in 1..12 || day !in 1..31) return 0
        return runCatching {
            java.util.Calendar.getInstance().apply {
                clear()
                set(year, month - 1, day, hour, minute, second)
            }.timeInMillis
        }.getOrDefault(0L)
    }

    companion object {
        const val U32_MAX = 0xFFFFFFFFL
        const val EOCD_SIG = 0x06054B50L
        const val CEN_SIG = 0x02014B50L
        const val LOC_SIG = 0x04034B50L
        const val ZIP64_EOCD_SIG = 0x06064B50L
        const val ZIP64_LOCATOR_SIG = 0x07064B50L

        fun le16(b: ByteArray, off: Int): Int =
            (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

        fun le32(b: ByteArray, off: Int): Long =
            (b[off].toLong() and 0xFF) or ((b[off + 1].toLong() and 0xFF) shl 8) or
                ((b[off + 2].toLong() and 0xFF) shl 16) or ((b[off + 3].toLong() and 0xFF) shl 24)

        fun le64(b: ByteArray, off: Int): Long {
            var v = 0L
            for (i in 7 downTo 0) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
            return v
        }
    }
}
