package com.zpeek.app.core

import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import kotlin.math.min

/**
 * 线程安全的随机访问源。
 *
 * 所有读取都在同一把锁内串行执行，并按需校正底层通道位置，
 * 因此可以同时存在多个互相独立的顺序流（图片预加载、播放 + 缩略图等）。
 */
class RandomAccessSource(private val channel: FileChannel, val length: Long) : Closeable {

    private val lock = Any()
    private var closed = false

    val size: Long get() = length

    fun readAt(position: Long, buffer: ByteArray, offset: Int, count: Int): Int = synchronized(lock) {
        if (closed) throw IOException("压缩包已关闭")
        if (count <= 0) return@synchronized 0
        if (position < 0 || position >= length) return@synchronized -1
        channel.position(position)
        var total = 0
        while (total < count) {
            val n = channel.read(ByteBuffer.wrap(buffer, offset + total, count - total))
            if (n <= 0) break
            total += n
        }
        if (total == 0) -1 else total
    }

    fun readFullyAt(position: Long, buffer: ByteArray, offset: Int, count: Int) {
        var done = 0
        while (done < count) {
            val n = readAt(position + done, buffer, offset + done, count - done)
            if (n < 0) throw EOFException("压缩包在偏移 $position 处被截断")
            done += n
        }
    }

    fun bytesAt(position: Long, count: Int): ByteArray {
        val out = ByteArray(count)
        readFullyAt(position, out, 0, count)
        return out
    }

    /** 顺序流：从 position 开始最多读取 length 字节（length<0 表示到文件末尾） */
    fun stream(position: Long, length: Long = -1L): InputStream =
        PositionedStream(this, position, if (length < 0) length - position else length)

    fun asSeekableByteChannel(): SeekableByteChannel = SourceChannel(this)

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            runCatching { channel.close() }
        }
    }

    private class PositionedStream(
        private val src: RandomAccessSource,
        private val start: Long,
        private var remaining: Long,
    ) : InputStream() {
        private var pos = start

        override fun read(): Int {
            if (remaining == 0L) return -1
            val n = src.readAt(pos, ONE, 0, 1)
            if (n <= 0) return -1
            pos++
            remaining--
            return ONE[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining == 0L) return -1
            if (len <= 0) return 0
            val want = min(len.toLong(), remaining).toInt()
            val n = src.readAt(pos, b, off, want)
            if (n <= 0) return -1
            pos += n
            remaining -= n
            return n
        }

        override fun available(): Int = remaining.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

        override fun skip(n: Long): Long {
            val s = min(n, remaining).coerceAtLeast(0)
            pos += s
            remaining -= s
            return s
        }

        override fun close() {
            remaining = 0
        }

        companion object {
            val ONE = ByteArray(1)
        }
    }

    private class SourceChannel(private val src: RandomAccessSource) : SeekableByteChannel {
        private var pos = 0L
        private var open = true
        private var scratch = ByteArray(1 shl 15)

        override fun read(dst: ByteBuffer): Int {
            if (!open) throw IOException("通道已关闭")
            val n = minOf(dst.remaining().toLong(), scratch.size.toLong()).toInt()
            val r = src.readAt(pos, scratch, 0, n)
            if (r <= 0) return -1
            dst.put(scratch, 0, r)
            pos += r
            return r
        }

        override fun write(src: ByteBuffer): Int = throw IOException("只读通道")

        override fun position(): Long = pos

        override fun position(newPosition: Long): SeekableByteChannel {
            pos = newPosition
            return this
        }

        override fun size(): Long = src.size

        override fun truncate(size: Long): SeekableByteChannel = throw IOException("只读通道")

        override fun isOpen(): Boolean = open

        override fun close() {
            open = false
        }
    }
}

/** 顺序流上跳到指定位置（用于不支持随机访问的格式做定位） */
fun InputStream.skipTo(position: Long): InputStream {
    if (position <= 0) return this
    var left = position
    val buf = ByteArray(1 shl 15)
    while (left > 0) {
        val n = read(buf, 0, min(buf.size.toLong(), left).toInt())
        if (n < 0) break
        left -= n
    }
    return this
}

/** 限制读取长度；limit < 0 表示不限制 */
class LimitedInputStream(
    private val upstream: InputStream,
    limit: Long,
    private val onClose: () -> Unit = {},
) : InputStream() {
    private var limit: Long = limit

    override fun read(): Int {
        if (limit == 0L) return -1
        val v = upstream.read()
        if (v >= 0 && limit > 0) limit--
        return v
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (limit == 0L) {
            close()
            return -1
        }
        if (len <= 0) return 0
        val want = if (limit < 0) len else min(len.toLong(), limit).toInt()
        val n = upstream.read(b, off, want)
        if (n > 0 && limit > 0) limit -= n else if (n < 0) close()
        return n
    }

    override fun available(): Int =
        if (limit < 0) upstream.available() else min(upstream.available().toLong(), limit).toInt()

    override fun close() {
        onClose()
        runCatching { upstream.close() }
    }
}

/** 把非线程安全的流包装成线程安全流（同一时刻只有一个读者） */
class LockedInputStream(private val upstream: InputStream, private val lock: Any) : InputStream() {
    override fun read(): Int = synchronized(lock) { upstream.read() }
    override fun read(b: ByteArray, off: Int, len: Int): Int = synchronized(lock) { upstream.read(b, off, len) }
    override fun available(): Int = synchronized(lock) { upstream.available() }
    override fun skip(n: Long): Long = synchronized(lock) { upstream.skip(n) }
    override fun markSupported(): Boolean = false
    override fun close() {
        synchronized(lock) { runCatching { upstream.close() } }
    }
}

internal fun ByteArray.toCharsetStrict(charset: java.nio.charset.Charset): String? = try {
    charset.newDecoder()
        .apply {
            onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        }
        .decode(ByteBuffer.wrap(this))
        .toString()
} catch (e: Exception) {
    null
}
