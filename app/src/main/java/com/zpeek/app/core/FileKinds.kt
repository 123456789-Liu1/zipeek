package com.zpeek.app.core

enum class FileKind { IMAGE, VIDEO, AUDIO, TEXT, CODE, DOCUMENT, OTHER }

object FileKinds {

    private val images = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif", "ico", "jfif"
    )
    private val videos = setOf(
        "mp4", "m4v", "mkv", "webm", "avi", "mov", "flv", "wmv", "ts", "3gp", "mpeg", "mpg", "rmvb", "rm", "vob", "m2ts"
    )
    private val audios = setOf(
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "ape", "amr", "mid", "midi", "aiff", "dsf", "m4b"
    )
    private val codes = setOf(
        "java", "kt", "kts", "c", "h", "cpp", "hpp", "cc", "py", "js", "jsx", "ts", "tsx", "go", "rs", "rb", "php", "swift",
        "cs", "sh", "bat", "ps1", "sql", "gradle", "properties", "yml", "yaml", "toml", "ini", "conf", "lua", "dart", "vue", "css", "scss", "less", "html", "htm", "xml", "pl", "r", "m", "mm", "scala", "groovy", "ex", "exs", "erl", "clj", "hs", "jl"
    )
    private val documents = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "epub", "mobi", "azw3", "chm")

    fun ext(name: String): String {
        val n = name.substringAfterLast('/')
        val i = n.lastIndexOf('.')
        return if (i < 0 || i == n.length - 1) "" else n.substring(i + 1).lowercase()
    }

    fun kindOf(name: String): FileKind {
        val e = ext(name)
        return when {
            e in images -> FileKind.IMAGE
            e in videos -> FileKind.VIDEO
            e in audios -> FileKind.AUDIO
            e in codes -> FileKind.CODE
            e in documents -> FileKind.DOCUMENT
            e in setOf("txt", "log", "md", "markdown", "json", "csv", "tsv", "srt", "ass", "vtt", "nfo", "diff", "patch", "text") -> FileKind.TEXT
            else -> FileKind.OTHER
        }
    }

    fun isTextLike(kind: FileKind) = kind == FileKind.TEXT || kind == FileKind.CODE

    /** 是否为动图（API 28+ 用 AnimatedImageDrawable 播放） */
    fun isAnimatedImage(name: String): Boolean = ext(name) in setOf("gif", "webp", "apng")
}
