package com.zpeek.app.core

import java.nio.charset.Charset

/**
 * 文本编码自动识别：BOM -> 严格 UTF-8 校验 -> GB18030 -> Latin-1。
 * 中文压缩包里 GBK 编码极为常见，单纯按 UTF-8 解码会出现乱码。
 */
object TextCharset {

    val GB18030: Charset = runCatching { Charset.forName("GB18030") }.getOrDefault(Charsets.UTF_8)

    data class Result(val charset: Charset, val text: String, val binary: Boolean)

    fun detect(bytes: ByteArray, limit: Int = 1 shl 20): Result {
        val head = if (bytes.size > limit) bytes.copyOf(limit) else bytes
        if (isBinary(head)) {
            val cs = guess(head) ?: Charsets.UTF_8
            return Result(cs, "", true)
        }
        val cs = guess(head) ?: Charsets.UTF_8
        val text = String(bytes, cs)
        return Result(cs, text, false)
    }

    private fun guess(head: ByteArray): Charset? {
        if (head.size >= 3 && head[0] == 0xEF.toByte() && head[1] == 0xBB.toByte() && head[2] == 0xBF.toByte()) {
            return Charsets.UTF_8
        }
        if (head.size >= 2 && head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte()) return Charsets.UTF_16LE
        if (head.size >= 2 && head[0] == 0xFE.toByte() && head[1] == 0xFF.toByte()) return Charsets.UTF_16BE
        if (head.size >= 4 && head[0] == 0x00.toByte() && head[1] == 0x00.toByte() &&
            head[2] == 0xFE.toByte() && head[3] == 0xFF.toByte()
        ) return Charsets.UTF_32BE
        if (head.size >= 4 && head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte() &&
            head[2] == 0x00.toByte() && head[3] == 0x00.toByte()
        ) return Charsets.UTF_32LE
        if (head.isEmpty()) return Charsets.UTF_8
        head.toCharsetStrict(Charsets.UTF_8)?.let { return Charsets.UTF_8 }
        return GB18030
    }

    /** 二进制判定：出现 NUL 字节或大量控制字符即视为非文本 */
    fun isBinary(bytes: ByteArray): Boolean {
        val n = minOf(bytes.size, 4096)
        if (n == 0) return false
        var control = 0
        for (i in 0 until n) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) return true
            if (b < 9 || (b in 14..31)) control++
        }
        return control * 100 / n > 5
    }

    val candidates: List<Pair<String, Charset>> = listOf(
        "UTF-8" to Charsets.UTF_8,
        "GB18030" to GB18030,
        "Big5" to runCatching { Charset.forName("Big5") }.getOrDefault(GB18030),
        "Shift_JIS" to runCatching { Charset.forName("Shift_JIS") }.getOrDefault(GB18030),
        "UTF-16LE" to Charsets.UTF_16LE,
        "ISO-8859-1" to Charsets.ISO_8859_1,
    )
}
