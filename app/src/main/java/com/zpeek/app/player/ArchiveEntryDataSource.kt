package com.zpeek.app.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.InputStream
import kotlin.math.min

/** 为播放器提供"压缩包内条目"的随机访问能力 */
class EntryStreamProvider(val engine: com.zpeek.app.core.ArchiveEngine, val entry: com.zpeek.app.core.ArchiveEntry) {
    val size: Long get() = if (entry.size > 0) entry.size else 0L
    fun openAt(position: Long, length: Long): InputStream = engine.openStreamAt(entry, position, length)
}

/**
 * 直接从压缩包内读取媒体数据的 DataSource —— 全程不落地任何临时文件。
 * 播放器 seek 时只会重新定位到目标偏移并继续流式解压；
 * 对 7z / RAR 这类实体压缩包，额外配合本地 LRU 缓存实现秒级跳转。
 */
class ArchiveEntryDataSource : BaseDataSource(/* isNetwork = */ false) {

    private var stream: InputStream? = null
    private var remaining = 0L
    private var opened = false
    private var provider: EntryStreamProvider? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val p = PlayerHolder.resolve(dataSpec.uri) ?: throw java.io.FileNotFoundException("条目已失效")
        provider = p
        val position = dataSpec.position
        val length = if (dataSpec.length == C.LENGTH_UNSET.toLong()) (p.size - position).coerceAtLeast(0) else dataSpec.length
        stream = p.openAt(position, length)
        remaining = length
        opened = true
        transferStarted(dataSpec)
        return if (length == C.LENGTH_UNSET.toLong()) 0L else length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val s = stream ?: throw IllegalStateException("数据源尚未打开")
        val want = if (remaining == C.LENGTH_UNSET.toLong()) length else min(length.toLong(), remaining).toInt()
        val n = s.read(buffer, offset, want)
        if (n == -1) {
            if (opened) {
                opened = false
                transferEnded()
            }
            return C.RESULT_END_OF_INPUT
        }
        if (remaining != C.LENGTH_UNSET.toLong()) remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri = Uri.EMPTY

    override fun close() {
        runCatching { stream?.close() }
        stream = null
        provider = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    companion object {
        val FACTORY: DataSource.Factory = DataSource.Factory { ArchiveEntryDataSource() }
    }
}
