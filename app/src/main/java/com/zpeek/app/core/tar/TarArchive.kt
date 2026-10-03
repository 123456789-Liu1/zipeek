package com.zpeek.app.core.tar

import com.zpeek.app.core.ArchiveEngine
import com.zpeek.app.core.ArchiveEntry
import com.zpeek.app.core.ArchiveException
import com.zpeek.app.core.ArchiveFormat
import com.zpeek.app.core.LimitedInputStream
import com.zpeek.app.core.PathUtil
import com.zpeek.app.core.RandomAccessSource
import com.zpeek.app.core.skipTo
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.InputStream

/** TAR / TAR.GZ / TAR.BZ2 / TAR.XZ 读取 */
class TarArchive(
    private val src: RandomAccessSource,
    override val format: ArchiveFormat,
) : ArchiveEngine {

    private class Rec(val path: String, val dir: Boolean, val size: Long, val time: Long)

    private val records: List<Rec> by lazy { scan() }

    private val cache: List<ArchiveEntry> by lazy {
        records.mapIndexed { i, r -> ArchiveEntry(r.path, r.dir, r.size, 0, r.time, i, true) }
    }

    override fun entries(): List<ArchiveEntry> = cache
    override val size: Int get() = cache.size

    private fun wrap(raw: InputStream): InputStream = when (format) {
        ArchiveFormat.TAR_GZ -> GzipCompressorInputStream(raw, true)
        ArchiveFormat.TAR_BZ2 -> BZip2CompressorInputStream(raw, true)
        ArchiveFormat.TAR_XZ -> XZCompressorInputStream(raw, true)
        else -> raw
    }

    private fun openRaw(): TarArchiveInputStream = try {
        TarArchiveInputStream(wrap(src.stream(0)))
    } catch (t: Throwable) {
        throw ArchiveException("解压失败：${t.message}", t)
    }

    private fun scan(): List<Rec> {
        val out = ArrayList<Rec>()
        openRaw().use { tar ->
            while (true) {
                val e = try {
                    tar.nextEntry
                } catch (t: Throwable) {
                    throw ArchiveException("TAR 目录读取失败：${t.message}", t)
                } ?: break
                val path = PathUtil.normalize(e.name)
                if (path.isNotEmpty()) {
                    out.add(Rec(path, e.isDirectory, if (e.isDirectory) 0 else e.size, e.lastModifiedDate?.time ?: 0L))
                }
            }
        }
        return out
    }

    override fun openStream(entry: ArchiveEntry): InputStream = openStreamAt(entry, 0, entry.size)

    override fun openStreamAt(entry: ArchiveEntry, position: Long, length: Long): InputStream {
        val tar = openRaw()
        var i = 0
        while (i < entry.index) {
            if (tar.nextEntry == null) {
                runCatching { tar.close() }
                throw ArchiveException("条目「${entry.name}」定位失败")
            }
            i++
        }
        val s: InputStream = tar
        if (position > 0) s.skipTo(position)
        val limit = if (length < 0) (records.getOrNull(entry.index)?.size ?: -1L) - position else length
        return LimitedInputStream(s, limit) { runCatching { tar.close() } }
    }
}

/** 独立的 .gz / .bz2 / .xz 单文件流，作为只有一个条目的压缩包处理 */
class SingleCompressedArchive(
    private val src: RandomAccessSource,
    override val format: ArchiveFormat,
    entryName: String,
) : ArchiveEngine {

    private val name = entryName.ifBlank { "内容" }
    private val entry = ArchiveEntry(name, false, -1L, src.size, 0L, 0, true)
    private val list = listOf(entry)

    override fun entries(): List<ArchiveEntry> = list
    override val size: Int get() = 1

    private fun wrap(raw: InputStream): InputStream = try {
        when (format) {
            ArchiveFormat.GZ -> GzipCompressorInputStream(raw, true)
            ArchiveFormat.BZ2 -> BZip2CompressorInputStream(raw, true)
            ArchiveFormat.XZ -> XZCompressorInputStream(raw, true)
            else -> raw
        }
    } catch (t: Throwable) {
        throw ArchiveException("解压失败：${t.message}", t)
    }

    override fun openStream(e: ArchiveEntry): InputStream = wrap(src.stream(0))

    override fun openStreamAt(e: ArchiveEntry, position: Long, length: Long): InputStream {
        val s = wrap(src.stream(0))
        if (position > 0) s.skipTo(position)
        return if (length < 0) s else LimitedInputStream(s, length)
    }
}
