package com.zpeek.app.core.rar

import com.github.junrar.Archive
import com.github.junrar.io.SeekableReadOnlyByteChannel
import com.github.junrar.rarfile.FileHeader
import com.github.junrar.volume.Volume
import com.github.junrar.volume.VolumeManager
import com.zpeek.app.core.ArchiveEngine
import com.zpeek.app.core.ArchiveEntry
import com.zpeek.app.core.ArchiveException
import com.zpeek.app.core.ArchiveFormat
import com.zpeek.app.core.LimitedInputStream
import com.zpeek.app.core.LockedInputStream
import com.zpeek.app.core.PathUtil
import com.zpeek.app.core.RandomAccessSource
import com.zpeek.app.core.skipTo
import java.io.InputStream

/**
 * RAR 读取（junrar，纯 Java；支持 RAR1.5~RAR5）。
 * 通过自定义 VolumeManager 复用底层随机访问源，无需把压缩包复制到本地。
 */
class RarArchive(private val src: RandomAccessSource) : ArchiveEngine {

    override val format: ArchiveFormat get() = ArchiveFormat.RAR

    private val lock = Any()
    private val channel = object : SeekableReadOnlyByteChannel {
        private var pos = 0L
        private val one = ByteArray(1)
        override fun getPosition(): Long = pos
        override fun setPosition(newPosition: Long) {
            pos = newPosition
        }

        override fun read(): Int {
            val n = src.readAt(pos, one, 0, 1)
            if (n <= 0) return -1
            pos++
            return one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = src.readAt(pos, b, off, len)
            if (n > 0) pos += n
            return n
        }

        override fun readFully(b: ByteArray, len: Int): Int {
            var done = 0
            while (done < len) {
                val n = read(b, done, len - done)
                if (n < 0) throw java.io.EOFException("RAR 文件被截断")
                done += n
            }
            return done
        }

        override fun close() {}
    }

    private val archive: Archive = try {
        lateinit var self: Archive
        val manager = object : VolumeManager {
            private var volume: Volume? = null
            override fun nextVolume(archive: Archive, volume: Volume?): Volume {
                if (this.volume != null) return this.volume!!
                val v = object : Volume {
                    override fun getChannel(): SeekableReadOnlyByteChannel = channel
                    override fun getLength(): Long = src.size
                    override fun getArchive(): Archive = self
                }
                this.volume = v
                return v
            }
        }
        self = Archive(manager, null, null)
        self
    } catch (t: Throwable) {
        throw ArchiveException("无法打开 RAR 压缩包：${t.message ?: t::class.java.simpleName}", t)
    }

    private val records: List<FileHeader> by lazy { archive.fileHeaders ?: emptyList() }

    private val cache: List<ArchiveEntry> by lazy {
        records.mapIndexed { i, h ->
            val name = runCatching { h.fileName }.getOrNull().orEmpty()
            val path = PathUtil.normalize(name)
            ArchiveEntry(
                path = path.ifEmpty { "entry$i" },
                isDirectory = h.isDirectory,
                size = if (h.isDirectory) 0 else h.fullUnpackSize,
                packedSize = h.fullPackSize,
                modified = h.mTime?.time ?: 0L,
                index = i,
                readable = !h.isEncrypted,
            )
        }
    }

    override fun entries(): List<ArchiveEntry> = cache
    override val size: Int get() = cache.size

    override fun openStream(entry: ArchiveEntry): InputStream {
        val h = records.getOrNull(entry.index) ?: throw ArchiveException("无效的条目")
        if (h.isDirectory) return ByteArray(0).inputStream()
        if (h.isEncrypted) throw ArchiveException("「${entry.name}」已加密，本应用暂不支持加密压缩包")
        val s = try {
            archive.getInputStream(h)
        } catch (t: Throwable) {
            throw ArchiveException("打开「${entry.name}」失败：${t.message ?: "文件可能已损坏"}", t)
        }
        return LockedInputStream(s, lock)
    }

    override fun openStreamAt(entry: ArchiveEntry, position: Long, length: Long): InputStream {
        val s = openStream(entry)
        if (position > 0) s.skipTo(position)
        return if (length < 0) s else LimitedInputStream(s, length)
    }

    override fun close() {
        runCatching { archive.close() }
    }
}
