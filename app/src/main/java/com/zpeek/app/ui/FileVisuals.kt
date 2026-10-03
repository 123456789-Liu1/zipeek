package com.zpeek.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.zpeek.app.R
import com.zpeek.app.core.ArchiveFormat
import com.zpeek.app.core.FileKind
import com.zpeek.app.core.FileKinds

@DrawableRes
fun archiveIcon(format: ArchiveFormat): Int = when (format) {
    ArchiveFormat.ZIP -> R.drawable.ic_fmt_zip
    ArchiveFormat.RAR -> R.drawable.ic_fmt_rar
    ArchiveFormat.SEVEN_Z -> R.drawable.ic_fmt_7z
    else -> R.drawable.ic_fmt_tar
}

fun archiveColor(format: ArchiveFormat): Color = when (format) {
    ArchiveFormat.ZIP -> Color(0xFFF5A524)
    ArchiveFormat.RAR -> Color(0xFF8B5CF6)
    ArchiveFormat.SEVEN_Z -> Color(0xFF16A34A)
    else -> Color(0xFF64748B)
}

@DrawableRes
fun entryIcon(name: String, isDirectory: Boolean): Int {
    if (isDirectory) return R.drawable.ic_folder
    return when (FileKinds.kindOf(name)) {
        FileKind.IMAGE -> R.drawable.ic_type_image
        FileKind.VIDEO -> R.drawable.ic_type_video
        FileKind.AUDIO -> R.drawable.ic_type_audio
        FileKind.TEXT -> R.drawable.ic_type_text
        FileKind.CODE -> R.drawable.ic_type_code
        FileKind.DOCUMENT -> R.drawable.ic_type_doc
        FileKind.OTHER -> R.drawable.ic_type_other
    }
}

fun entryColor(name: String, isDirectory: Boolean): Color {
    if (isDirectory) return Color(0xFFF59E0B)
    return when (FileKinds.kindOf(name)) {
        FileKind.IMAGE -> Color(0xFF10B981)
        FileKind.VIDEO -> Color(0xFFF97316)
        FileKind.AUDIO -> Color(0xFFEC4899)
        FileKind.TEXT -> Color(0xFF0EA5E9)
        FileKind.CODE -> Color(0xFF6366F1)
        FileKind.DOCUMENT -> Color(0xFF8B5CF6)
        FileKind.OTHER -> Color(0xFF94A3B8)
    }
}

/** 该类型是否有内置浏览器 */
fun hasViewer(kind: FileKind): Boolean = when (kind) {
    FileKind.IMAGE, FileKind.VIDEO, FileKind.AUDIO, FileKind.TEXT, FileKind.CODE -> true
    else -> false
}
