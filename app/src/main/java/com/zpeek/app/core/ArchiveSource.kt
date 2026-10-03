package com.zpeek.app.core

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * 压缩包的底层数据来源。
 *
 * 优先使用内容提供器返回的可随机访问文件描述符（零拷贝、可 seek）；
 * 若提供器给出的是管道（云端文档等不可寻址流），则回退到复制到缓存目录。
 */
class ArchiveSource private constructor(
    val displayName: String,
    val uri: Uri,
    val size: Long,
    val format: ArchiveFormat,
    private val access: RandomAccessSource?,
    private val pfd: ParcelFileDescriptor?,
    private val tempFile: File?,
) : Closeable {

    val isRandomAccess: Boolean get() = access != null

    fun openAccess(): RandomAccessSource =
        access ?: throw ArchiveException("该压缩包不支持随机访问")

    override fun close() {
        runCatching { access?.close() }
        runCatching { pfd?.close() }
        runCatching { tempFile?.delete() }
    }

    companion object {
        fun open(context: Context, uri: Uri): ArchiveSource {
            val name = queryName(context, uri)
            val declared = querySize(context, uri)
            val head = readHead(context, uri)
            val format = ArchiveFormatDetector.detect(head, name)

            // 1) 尝试直接使用提供器的文件描述符
            runCatching {
                val p = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw IOException("openFileDescriptor 返回 null")
                try {
                    val channel = FileInputStream(p.fileDescriptor).channel
                    val len = channel.size()
                    if (len <= 0) throw IOException("不可随机访问")
                    val probe = ByteArray(1)
                    channel.position(0)
                    if (channel.read(ByteBuffer.wrap(probe)) <= 0) throw IOException("不可随机访问")
                    val source = RandomAccessSource(channel, len)
                    return ArchiveSource(name, uri, len, format, source, p, null)
                } catch (t: Throwable) {
                    runCatching { p.close() }
                    throw t
                }
            }

            // 2) 回退：复制到缓存目录
            val cacheDir = File(context.cacheDir, "open").apply { mkdirs() }
            val tmp = File(cacheDir, "op_${System.currentTimeMillis()}_${safeName(name)}")
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法读取所选文件" }
                tmp.outputStream().buffered(1 shl 16).use { out ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                }
            }
            val raf = RandomAccessFile(tmp, "r")
            val source = RandomAccessSource(raf.channel, raf.length())
            return ArchiveSource(name, uri, source.size, format, source, null, tmp)
        }

        /** 清理历史缓存（应用启动时调用） */
        fun cleanCache(context: Context) {
            val dir = File(context.cacheDir, "open")
            val files = dir.listFiles() ?: return
            val expired = System.currentTimeMillis() - 6 * 3600_000L
            files.forEach { if (it.lastModified() < expired) it.delete() }
        }

        fun queryName(context: Context, uri: Uri): String {
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val n = cursor.getString(idx)
                        if (!n.isNullOrBlank()) return n
                    }
                }
            } catch (_: Exception) {
            } finally {
                cursor?.close()
            }
            return uri.lastPathSegment?.substringAfterLast('/') ?: "archive"
        }

        fun querySize(context: Context, uri: Uri): Long {
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx)
                }
            } catch (_: Exception) {
            } finally {
                cursor?.close()
            }
            return -1L
        }

        private fun readHead(context: Context, uri: Uri): ByteArray = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(512)
                var n = 0
                while (n < buf.size) {
                    val r = input.read(buf, n, buf.size - n)
                    if (r < 0) break
                    n += r
                }
                if (n == buf.size) buf else buf.copyOf(n)
            } ?: ByteArray(0)
        }.getOrDefault(ByteArray(0))

        private fun safeName(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
    }
}

object ArchiveFormatDetector {
    fun detect(head: ByteArray, fileName: String): ArchiveFormat {
        if (head.size >= 4) {
            val b0 = head[0].toInt() and 0xFF
            val b1 = head[1].toInt() and 0xFF
            val b2 = head[2].toInt() and 0xFF
            val b3 = head[3].toInt() and 0xFF
            val b4 = if (head.size > 4) head[4].toInt() and 0xFF else 0
            val b5 = if (head.size > 5) head[5].toInt() and 0xFF else 0

            if (b0 == 0x50 && b1 == 0x4B && (b3 == 0x04 || b3 == 0x05 || b3 == 0x06 || b3 == 0x07)) return ArchiveFormat.ZIP
            if (b0 == 0x52 && b1 == 0x61 && b2 == 0x72 && b3 == 0x21 && b4 == 0x1A && b5 == 0x07) return ArchiveFormat.RAR
            if (b0 == 0x37 && b1 == 0x7A && b2 == 0xBC && b3 == 0xAF && b4 == 0x27 && b5 == 0x1C) return ArchiveFormat.SEVEN_Z
            if (b0 == 0x1F && b1 == 0x8B) return ArchiveFormat.GZ
            if (b0 == 0x42 && b1 == 0x5A && b2 == 0x68) return ArchiveFormat.BZ2
            if (b0 == 0xFD && b1 == 0x37 && b2 == 0x7A && b3 == 0x58 && b4 == 0x5A) return ArchiveFormat.XZ
            // tar: 偏移 257 处为 "ustar"
            if (head.size > 262 && head[257] == 'u'.code.toByte() && head[258] == 's'.code.toByte() &&
                head[259] == 't'.code.toByte() && head[260] == 'a'.code.toByte() && head[261] == 'r'.code.toByte()
            ) return ArchiveFormat.TAR
        }
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> ArchiveFormat.TAR_GZ
            lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") || lower.endsWith(".tbz") -> ArchiveFormat.TAR_BZ2
            lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> ArchiveFormat.TAR_XZ
            lower.endsWith(".zip") -> ArchiveFormat.ZIP
            lower.endsWith(".rar") -> ArchiveFormat.RAR
            lower.endsWith(".7z") -> ArchiveFormat.SEVEN_Z
            lower.endsWith(".tar") -> ArchiveFormat.TAR
            lower.endsWith(".gz") -> ArchiveFormat.GZ
            lower.endsWith(".bz2") -> ArchiveFormat.BZ2
            lower.endsWith(".xz") -> ArchiveFormat.XZ
            else -> ArchiveFormat.UNKNOWN
        }
    }
}
