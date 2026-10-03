package com.zpeek.app.core

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.tukaani.xz.LZMA2Options
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

data class ExtractProgress(
    val done: Long,
    val total: Long,
    val current: String,
    val index: Int,
    val count: Int,
) {
    val ratio: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

object Extractor {

    suspend fun extract(
        engine: ArchiveEngine,
        entries: List<ArchiveEntry>,
        dest: DestWriter,
        overwrite: Boolean,
        onProgress: (ExtractProgress) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        val dirs = entries.filter { it.isDirectory }
        val files = entries.filter { !it.isDirectory }
        val total = files.sumOf { it.size }
        var done = 0L
        var index = 0

        for (d in dirs) {
            coroutineContext.ensureActive()
            dest.ensureDirectory(d.path)
        }
        for (f in files) {
            coroutineContext.ensureActive()
            index++
            if (!f.readable) throw ArchiveException("「${f.name}」暂不支持解压（加密或特殊压缩方式）")
            val rel = PathUtil.safeRelative(f.path) ?: continue
            if (!overwrite && dest.exists(rel)) {
                done += f.size
                onProgress(ExtractProgress(done, total, f.name, index, files.size))
                continue
            }
            dest.writeFile(rel, f.size, { engine.openStream(f) }) { n ->
                onProgress(ExtractProgress(done + n, total, f.name, index, files.size))
            }
            done += f.size
            onProgress(ExtractProgress(done, total, f.name, index, files.size))
        }
        onProgress(ExtractProgress(done, total, "", files.size, files.size))
        done
    }
}

// ---------------------------------------------------------------- 压缩

enum class PackFormat(val label: String, val ext: String, val hint: String) {
    ZIP("ZIP", "zip", "通用格式，手机与电脑都能直接打开"),
    SEVEN_Z("7Z", "7z", "压缩率最高，适合大体积文件与安装包"),
    TAR_GZ("TAR.GZ", "tar.gz", "只打包不压缩，速度最快"),
}

enum class PackLevel(val label: String, val desc: String, val zipLevel: Int) {
    FAST("快速", "体积略大，耗时最短", 1),
    NORMAL("标准", "速度与体积均衡", 5),
    MAX("极致", "体积最小，耗时最长", 9),
}

class PackItem(
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val open: () -> InputStream,
    val children: List<PackItem> = emptyList(),
) {
    val totalSize: Long get() = if (isDirectory) children.sumOf { it.totalSize } else size
    val fileCount: Int get() = if (isDirectory) 1 + children.sumOf { it.fileCount } else 1
}

object PackInput {

    fun fromUris(context: Context, uris: List<Uri>): List<PackItem> = uris.map { uri ->
        val name = ArchiveSource.queryName(context, uri)
        val size = ArchiveSource.querySize(context, uri)
        PackItem(
            name = name,
            size = if (size > 0) size else 0L,
            isDirectory = false,
            open = {
                context.contentResolver.openInputStream(uri)
                    ?: throw ArchiveException("无法读取：$name")
            },
        )
    }

    fun fromTree(context: Context, treeUri: Uri, maxDepth: Int = 12): List<PackItem> {
        val rootName = DocumentsContract.getTreeDocumentId(treeUri)
            .substringAfterLast('/').ifEmpty { "所选目录" }
        return listOf(
            PackItem(
                name = rootName,
                size = 0,
                isDirectory = true,
                open = { ByteArray(0).inputStream() },
                children = listChildren(context, treeUri, 0, maxDepth),
            )
        )
    }

    private fun listChildren(context: Context, treeUri: Uri, depth: Int, maxDepth: Int): List<PackItem> {
        if (depth >= maxDepth) return emptyList()
        val resolver = context.contentResolver
        val out = ArrayList<PackItem>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        runCatching {
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null, null, null,
            )?.use { c ->
                val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                while (c.moveToNext()) {
                    val docId = c.getString(idIdx) ?: continue
                    val name = c.getString(nameIdx) ?: continue
                    val mime = c.getString(mimeIdx) ?: ""
                    val size = if (c.isNull(sizeIdx)) 0L else c.getLong(sizeIdx)
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    out.add(
                        PackItem(
                            name = name,
                            size = size,
                            isDirectory = isDir,
                            open = {
                                resolver.openInputStream(docUri) ?: throw ArchiveException("无法读取：$name")
                            },
                            children = if (isDir) listChildren(context, docUri, depth + 1, maxDepth) else emptyList(),
                        )
                    )
                }
            }
        }
        return out
    }
}

object Packer {

    /** 打包到本地临时文件（随后再拷贝到用户选择的目标位置），返回结果文件大小 */
    fun pack(
        items: List<PackItem>,
        output: File,
        format: PackFormat,
        level: PackLevel,
        onProgress: (done: Long, total: Long, current: String) -> Unit,
    ): Long {
        val total = items.sumOf { it.totalSize }
        var done = 0L
        fun report(name: String, n: Long) {
            done += n
            onProgress(done, total, name)
        }
        when (format) {
            PackFormat.ZIP -> packZip(items, output, level, ::report)
            PackFormat.SEVEN_Z -> packSevenZ(items, output, level, ::report)
            PackFormat.TAR_GZ -> packTarGz(items, output, level, ::report)
        }
        onProgress(total, total, "")
        return output.length()
    }

    private inline fun writeItem(item: PackItem, crossinline sink: (ByteArray, Int, Int) -> Unit) {
        item.open().use { src ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = src.read(buf)
                if (n < 0) break
                sink(buf, 0, n)
            }
        }
    }

    private fun packZip(
        items: List<PackItem>,
        output: File,
        level: PackLevel,
        report: (String, Long) -> Unit,
    ) {
        val out: OutputStream = BufferedOutputStream(output.outputStream(), 1 shl 16)
        ZipOutputStream(out).use { zos ->
            zos.setLevel(level.zipLevel)
            zos.setMethod(ZipOutputStream.DEFLATED)
            fun walk(list: List<PackItem>, prefix: String) {
                for (item in list) {
                    val path = if (prefix.isEmpty()) item.name else "$prefix/${item.name}"
                    if (item.isDirectory) {
                        zos.putNextEntry(ZipEntry("$path/").apply { time = System.currentTimeMillis() })
                        zos.closeEntry()
                        walk(item.children, path)
                    } else {
                        zos.putNextEntry(ZipEntry(path).apply { time = System.currentTimeMillis() })
                        writeItem(item) { b, o, n -> zos.write(b, o, n); report(path, n.toLong()) }
                        zos.closeEntry()
                    }
                }
            }
            walk(items, "")
            zos.finish()
        }
    }

    private fun packTarGz(
        items: List<PackItem>,
        output: File,
        level: PackLevel,
        report: (String, Long) -> Unit,
    ) {
        val fileOut = BufferedOutputStream(output.outputStream(), 1 shl 16)
        val params = GzipParameters().apply { compressionLevel = level.zipLevel }
        val gz = GzipCompressorOutputStream(fileOut, params)
        val tos = TarArchiveOutputStream(gz, "UTF-8")
        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
        tos.use {
            fun walk(list: List<PackItem>, prefix: String) {
                for (item in list) {
                    val path = if (prefix.isEmpty()) item.name else "$prefix/${item.name}"
                    if (item.isDirectory) {
                        tos.putArchiveEntry(
                            TarArchiveEntry("$path/").apply { modTime = java.util.Date() }
                        )
                        tos.closeArchiveEntry()
                        walk(item.children, path)
                    } else {
                        tos.putArchiveEntry(
                            TarArchiveEntry(path).apply {
                                size = item.size
                                modTime = java.util.Date()
                            }
                        )
                        writeItem(item) { b, o, n -> tos.write(b, o, n); report(path, n.toLong()) }
                        tos.closeArchiveEntry()
                    }
                }
            }
            walk(items, "")
            tos.finish()
        }
        gz.finish()
        fileOut.flush()
    }

    private fun packSevenZ(
        items: List<PackItem>,
        output: File,
        level: PackLevel,
        report: (String, Long) -> Unit,
    ) {
        val preset = when (level) {
            PackLevel.FAST -> 1
            PackLevel.NORMAL -> 5
            PackLevel.MAX -> 9
        }
        SevenZOutputFile(output).use { sz ->
            val options = LZMA2Options()
            options.setPreset(preset)
            sz.setContentMethods(listOf(SevenZMethodConfiguration(SevenZMethod.LZMA2, options)))
            fun walk(list: List<PackItem>, prefix: String) {
                for (item in list) {
                    val path = if (prefix.isEmpty()) item.name else "$prefix/${item.name}"
                    val e = SevenZArchiveEntry()
                    e.name = path
                    e.size = if (item.isDirectory) 0 else item.size
                    e.setHasStream(!item.isDirectory)
                    e.setDirectory(item.isDirectory)
                    e.lastModifiedDate = java.util.Date(System.currentTimeMillis())
                    sz.putArchiveEntry(e)
                    if (!item.isDirectory) {
                        writeItem(item) { b, o, n -> sz.write(b, o, n); report(path, n.toLong()) }
                    }
                    sz.closeArchiveEntry()
                    if (item.isDirectory) walk(item.children, path)
                }
            }
            walk(items, "")
            sz.finish()
        }
    }
}
