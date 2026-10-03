package com.zpeek.app.core

/** 压缩包格式 */
enum class ArchiveFormat(val label: String, val extensions: List<String>) {
    ZIP("ZIP", listOf("zip", "zipx", "jar", "apk", "epub", "apk", "docx", "xlsx", "pptx", "aab", "xapk")),
    RAR("RAR", listOf("rar", "cbr", "cbz")),
    SEVEN_Z("7Z", listOf("7z")),
    TAR_GZ("TAR.GZ", listOf("tar.gz", "tgz")),
    TAR_BZ2("TAR.BZ2", listOf("tar.bz2", "tbz", "tbz2")),
    TAR_XZ("TAR.XZ", listOf("tar.xz", "txz")),
    TAR("TAR", listOf("tar")),
    GZ("GZ", listOf("gz")),
    BZ2("BZ2", listOf("bz2")),
    XZ("XZ", listOf("xz")),
    UNKNOWN("未知", emptyList());

    val isTarFamily: Boolean
        get() = this == TAR || this == TAR_GZ || this == TAR_BZ2 || this == TAR_XZ
}

class ArchiveException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 压缩包内的条目。路径统一使用 "/" 分隔、无首尾斜杠。
 */
class ArchiveEntry(
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val packedSize: Long,
    val modified: Long,
    val index: Int,
    val readable: Boolean = true,
) {
    val name: String get() = path.substringAfterLast('/')
    val parent: String get() = if ('/' in path) path.substringBeforeLast('/') else ""

    /** 压缩率，0 表示未压缩或无数据 */
    val ratio: Int
        get() = if (isDirectory || size <= 0 || packedSize <= 0) 0
        else (((size - packedSize) * 100) / size).toInt().coerceIn(0, 100)

    override fun toString(): String = path
}

internal object PathUtil {
    fun normalize(raw: String): String {
        val s = raw.replace('\\', '/')
        val sb = StringBuilder(s.length)
        var lastSlash = true
        for (ch in s) {
            when {
                ch == '/' -> {
                    if (!lastSlash) {
                        sb.append('/')
                        lastSlash = true
                    }
                }
                lastSlash && (ch == '.' || ch == ' ') -> Unit
                else -> {
                    sb.append(ch)
                    lastSlash = false
                }
            }
        }
        var out = sb.toString()
        while (out.endsWith("/")) out = out.dropLast(1)
        return out
    }

    /** 阻断 zip-slip：返回安全的相对路径 */
    fun safeRelative(path: String): String? {
        val parts = ArrayList<String>()
        for (seg in normalize(path).split('/')) {
            when (seg) {
                "", "." -> Unit
                ".." -> return null
                else -> parts.add(seg.replace(':', '_'))
            }
        }
        return parts.joinToString("/").ifEmpty { null }
    }
}
