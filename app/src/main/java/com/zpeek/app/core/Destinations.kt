package com.zpeek.app.core

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * 解压目标抽象：既支持直接写入本地目录，也支持写入系统文件选择器返回的目录树（SAF），
 * 因此本应用无需申请任何存储权限。
 */
interface DestWriter {
    val displayPath: String
    suspend fun writeFile(path: String, size: Long, open: () -> InputStream, onBytes: (Long) -> Unit)
    suspend fun ensureDirectory(path: String)
    suspend fun exists(path: String): Boolean
    suspend fun delete(path: String)
}

class FileDestWriter(val root: File) : DestWriter {
    override val displayPath: String get() = root.absolutePath

    private fun resolve(path: String): File? {
        val rel = PathUtil.safeRelative(path) ?: return null
        val f = File(root, rel)
        val canon = runCatching { f.canonicalPath }.getOrNull() ?: return null
        val rootCanon = runCatching { root.canonicalPath }.getOrNull() ?: return null
        return if (canon == rootCanon || canon.startsWith(rootCanon + File.separator)) f else null
    }

    override suspend fun ensureDirectory(path: String) = withContext(Dispatchers.IO) {
        resolve(path)?.let { if (!it.exists()) it.mkdirs() }
        Unit
    }

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) {
        resolve(path)?.exists() ?: false
    }

    override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        resolve(path)?.let { if (it.isFile) it.delete() }
        Unit
    }

    override suspend fun writeFile(
        path: String,
        size: Long,
        open: () -> InputStream,
        onBytes: (Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val f = resolve(path) ?: throw ArchiveException("非法路径：$path")
        f.parentFile?.mkdirs()
        var done = 0L
        f.outputStream().buffered(1 shl 16).use { out ->
            open().use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    onBytes(done)
                }
            }
        }
        Unit
    }
}

class SafDestWriter(private val context: Context, val treeUri: Uri) : DestWriter {
    private val resolver = context.contentResolver
    private val cache = HashMap<String, Uri>()

    override val displayPath: String
        get() = DocumentsContract.getTreeDocumentId(treeUri).substringAfterLast(':').substringAfterLast('/')
            .ifEmpty { "所选目录" }

    private fun rootDoc(): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))

    private fun child(parent: Uri, mime: String, name: String): Uri? = try {
        DocumentsContract.createDocument(resolver, parent, mime, name)
    } catch (t: Throwable) {
        null
    }

    private fun findChild(parent: Uri, name: String): Uri? = try {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getDocumentId(parent))
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c ->
                val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                var found: Uri? = null
                var i = 0
                while (found == null && c.moveToNext()) {
                    if (c.getString(nameIdx) == name) {
                        found = DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(idIdx))
                    }
                    i++
                }
                found
            }
    } catch (t: Throwable) {
        null
    }

    private suspend fun dirFor(path: String): Uri? {
        val rel = PathUtil.safeRelative(path) ?: return null
        cache[rel]?.let { return it }
        var current = rootDoc()
        val segments = if (rel.isEmpty()) emptyList() else rel.split('/')
        val acc = ArrayList<String>()
        for (seg in segments) {
            acc.add(seg)
            val key = acc.joinToString("/")
            val cached = cache[key]
            if (cached != null) {
                current = cached
                continue
            }
            val existing = findChild(current, seg)
            val uri = existing ?: child(current, DocumentsContract.Document.MIME_TYPE_DIR, seg) ?: return null
            cache[key] = uri
            current = uri
        }
        return current
    }

    override suspend fun ensureDirectory(path: String) {
        dirFor(path)
    }

    override suspend fun exists(path: String): Boolean {
        val rel = PathUtil.safeRelative(path) ?: return false
        val idx = rel.lastIndexOf('/')
        val parent = if (idx < 0) "" else rel.substring(0, idx)
        val name = if (idx < 0) rel else rel.substring(idx + 1)
        val dir = dirFor(parent) ?: return false
        return findChild(dir, name) != null
    }

    override suspend fun delete(path: String) {
        val rel = PathUtil.safeRelative(path) ?: return
        val idx = rel.lastIndexOf('/')
        val parent = if (idx < 0) "" else rel.substring(0, idx)
        val name = if (idx < 0) rel else rel.substring(idx + 1)
        val dir = dirFor(parent) ?: return
        findChild(dir, name)?.let {
            runCatching { DocumentsContract.deleteDocument(resolver, it) }
        }
    }

    override suspend fun writeFile(
        path: String,
        size: Long,
        open: () -> InputStream,
        onBytes: (Long) -> Unit,
    ) {
        val rel = PathUtil.safeRelative(path) ?: throw ArchiveException("非法路径：$path")
        val idx = rel.lastIndexOf('/')
        val parentPath = if (idx < 0) "" else rel.substring(0, idx)
        val name = if (idx < 0) rel else rel.substring(idx + 1)
        val parent = dirFor(parentPath) ?: throw ArchiveException("无法创建目录：$parentPath")
        val mime = MimeTypes.of(name)
        val doc = findChild(parent, name) ?: child(parent, mime, name)
            ?: throw ArchiveException("无法创建文件：$name")
        var done = 0L
        resolver.openOutputStream(doc, "wt")?.use { out ->
            open().use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    onBytes(done)
                }
            }
        } ?: throw ArchiveException("无法写入文件：$name")
    }
}

object MimeTypes {
    private val map = mapOf(
        "txt" to "text/plain", "md" to "text/markdown", "json" to "application/json", "xml" to "text/xml",
        "html" to "text/html", "csv" to "text/csv", "log" to "text/plain", "pdf" to "application/pdf",
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif",
        "webp" to "image/webp", "bmp" to "image/bmp", "heic" to "image/heic",
        "mp4" to "video/mp4", "mkv" to "video/x-matroska", "webm" to "video/webm", "avi" to "video/x-msvideo",
        "mov" to "video/quicktime", "3gp" to "video/3gpp",
        "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "flac" to "audio/flac", "wav" to "audio/wav",
        "ogg" to "audio/ogg", "aac" to "audio/aac", "opus" to "audio/opus",
        "apk" to "application/vnd.android.package-archive", "zip" to "application/zip",
    )

    fun of(name: String): String = map[FileKinds.ext(name)] ?: "application/octet-stream"
}
