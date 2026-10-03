package com.zpeek.app.core

import java.io.Closeable
import java.io.File
import java.io.InputStream

/**
 * 压缩包读取引擎。所有格式共用一套接口，从而让上层 UI 与各类浏览器
 * 完全无需感知具体格式。
 */
interface ArchiveEngine : Closeable {

    val format: ArchiveFormat

    /** 包内全部条目（含目录），扁平列表 */
    fun entries(): List<ArchiveEntry>

    /** 条目总数 */
    val size: Int get() = entries().size

    /** 打开完整内容流 */
    fun openStream(entry: ArchiveEntry): InputStream

    /**
     * 从条目内的指定位置打开长度为 length 的流（用于播放器随机 seek）。
     * 默认实现通过读取并丢弃前缀实现，正确但较慢。
     */
    fun openStreamAt(entry: ArchiveEntry, position: Long, length: Long): InputStream {
        val full = openStream(entry)
        full.skipTo(position)
        return if (length < 0) full else LimitedInputStream(full, length)
    }

    /** 提取单个文件到目标路径 */
    fun extractTo(entry: ArchiveEntry, dest: File): Long {
        dest.parentFile?.mkdirs()
        var total = 0L
        dest.outputStream().buffered(1 shl 16).use { out ->
            openStream(entry).use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                }
            }
        }
        if (entry.modified > 0) dest.setLastModified(entry.modified)
        return total
    }

    override fun close() {}
}
