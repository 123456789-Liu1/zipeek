package com.zpeek.app.util

import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Format {

    private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

    fun size(bytes: Long): String {
        if (bytes < 0) return "未知"
        if (bytes < 1024) return "$bytes B"
        var value = bytes.toDouble()
        var idx = 0
        while (value >= 1024 && idx < UNITS.size - 1) {
            value /= 1024
            idx++
        }
        return if (value >= 100) String.format(Locale.US, "%.0f %s", value, UNITS[idx])
        else String.format(Locale.US, "%.1f %s", value, UNITS[idx])
    }

    fun speed(bytesPerSec: Long): String = "${
        if (bytesPerSec > 1024 * 1024) String.format(Locale.US, "%.1f MB/s", bytesPerSec / 1048576.0)
        else String.format(Locale.US, "%.0f KB/s", bytesPerSec / 1024.0)
    }"

    fun date(millis: Long): String {
        if (millis <= 0) return "—"
        val d = Date(millis)
        val cal = java.util.Calendar.getInstance()
        val now = cal.timeInMillis
        val prefix = when {
            now - millis < 7 * 24 * 3600_000L -> ""
            cal.get(java.util.Calendar.YEAR) == java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) -> ""
            else -> "${cal.get(java.util.Calendar.YEAR)}-"
        }
        return String.format(
            Locale.US, "%s%02d-%02d %02d:%02d", prefix,
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
        )
    }

    fun duration(ms: Long): String {
        if (ms <= 0) return "00:00"
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun ratio(ratio: Int): String = if (ratio <= 0) "未压缩" else "省 $ratio%"

    fun elapsed(millis: Long): String = when {
        millis < 60_000 -> "${millis / 1000} 秒"
        millis < 3600_000 -> "${millis / 60_000} 分钟"
        else -> String.format(Locale.US, "%.1f 小时", millis / 3600_000.0)
    }
}
