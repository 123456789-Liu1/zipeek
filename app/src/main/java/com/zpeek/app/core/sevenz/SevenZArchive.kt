package com.zpeek.app.core.sevenz

import com.zpeek.app.core.ArchiveEngine
import com.zpeek.app.core.ArchiveEntry
import com.zpeek.app.core.ArchiveException
import com.zpeek.app.core.ArchiveFormat
import com.zpeek.app.core.LimitedInputStream
import com.zpeek.app.core.LockedInputStream
import com.zpeek.app.core.PathUtil
import com.zpeek.app.core.RandomAccessSource
import com.zpeek.app.core.skipTo
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.InputStream

/** 7Z 读取（基于 commons-compress，纯 Java，无需 native 库） */
class SevenZArchive(src: RandomAccessSource) : ArchiveEngine {

    override val format: ArchiveFormat get() = ArchiveFormat.SEVEN_Z

    private val lock = Any()
    private val channel = src.asSeekableByteChannel()
    private val file: SevenZFile = try {
        SevenZFile.builder()
            .setSeekableByteChannel(channel)
            .setTryToRecoverBrokenArchives(true)
            .get()
    } catch (t: Throwable) {
        runCatching { channel.close() }
        throw ArchiveException("无法打开 7Z 压缩包：${t.message ?: t::class.java.simpleName}", t)
    }

    private val records: List<SevenZArchiveEntry> by lazy {
        try {
            file.entries.toList()
        } catch (t: Throwable) {
            throw ArchiveException("读取 7Z 目录失败：${t.message}", t)
        }
    }

    private val cache: List<ArchiveEntry> by lazy {
        records.mapIndexed { i, e ->
            val path = PathUtil.normalize(e.name ?: "entry$i")
            ArchiveEntry(
                path = path,
                isDirectory = e.isDirectory,
                size = if (e.isDirectory) 0 else e.size,
                packedSize = 0,
                modified = e.lastModifiedDate?.time ?: 0L,
                index = i,
                readable = e.hasStream() || e.isDirectory,
            )
        }
    }

    override fun entries(): List<ArchiveEntry> = cache
    override val size: Int get() = cache.size

    override fun openStream(entry: ArchiveEntry): InputStream {
        val rec = records.getOrNull(entry.index) ?: throw ArchiveException("无效的条目")
        if (rec.isDirectory) return ByteArray(0).inputStream()
        val stream = try {
            file.getInputStream(rec)
        } catch (t: Throwable) {
            throw ArchiveException("打开「${entry.name}」失败：${t.message ?: "文件可能已加密或损坏"}", t)
        }
        return LockedInputStream(stream, lock)
    }

    override fun openStreamAt(entry: ArchiveEntry, position: Long, length: Long): InputStream {
        val s = openStream(entry)
        if (position > 0) s.skipTo(position)
        return if (length < 0) s else LimitedInputStream(s, length)
    }

    override fun close() {
        runCatching { file.close() }
        runCatching { channel.close() }
    }
}
