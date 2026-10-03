package com.zpeek.app.core.zip

import com.zpeek.app.core.ArchiveException
import com.zpeek.app.core.ArchiveFactory
import com.zpeek.app.core.ArchiveFormat
import com.zpeek.app.core.ArchiveFormatDetector
import com.zpeek.app.core.ArchiveTree
import com.zpeek.app.core.RandomAccessSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipArchiveTest {

    private fun buildZip(target: File, block: (ZipOutputStream) -> Unit) {
        ZipOutputStream(target.outputStream()).use { block(it) }
    }

    private fun openOf(file: File): Triple<ZipArchive, ArchiveTree, RandomAccessSource> {
        val raf = RandomAccessFile(file, "r")
        val source = RandomAccessSource(raf.channel, raf.length())
        val engine = ZipArchive(source)
        return Triple(engine, ArchiveTree(engine.entries()), source)
    }

    @Test
    fun readsUtf8NamesAndNestedDirectories() {
        val zip = File.createTempFile("zipeek", ".zip")
        val payload = ByteArray(5000) { (it % 251).toByte() }
        buildZip(zip) { z ->
            z.putNextEntry(ZipEntry("root/"))
            z.closeEntry()
            z.putNextEntry(ZipEntry("root/中文文档.txt"))
            z.write("你好，压缩速览！".toByteArray())
            z.closeEntry()
            z.putNextEntry(ZipEntry("root/sub/"))
            z.closeEntry()
            z.putNextEntry(ZipEntry("root/sub/data.bin"))
            z.write(payload)
            z.closeEntry()
        }

        val (engine, tree, source) = openOf(zip)
        assertEquals(4, tree.all.size)

        val doc = tree.all.first { it.name == "中文文档.txt" }
        assertEquals("root/中文文档.txt", doc.path)
        assertEquals("root", doc.parent)
        assertTrue(doc.readable)
        assertEquals(
            "你好，压缩速览！",
            engine.openStream(doc).use { it.readBytes().toString(Charsets.UTF_8) }
        )

        val bin = tree.all.first { it.name == "data.bin" }
        assertEquals(5000L, bin.size)
        assertEquals(payload.toList(), engine.openStream(bin).use { it.readBytes() }.toList())

        // 目录层级
        assertEquals(listOf("root"), tree.childrenOf("").map { it.name })
        assertEquals(listOf("sub", "中文文档.txt"), tree.childrenOf("root").map { it.name }.sorted())
        assertEquals(5000L + "你好，压缩速览！".toByteArray().size.toLong(), tree.sizeOf("root"))
        assertTrue(tree.isDirectory("root/sub"))
        assertEquals(2, tree.filesUnder("root").size)

        // 随机定位读取（播放器 seek 的基础）
        val slice = engine.openStreamAt(bin, 100, 50).use { it.readBytes() }
        assertEquals(50, slice.size)
        assertEquals(payload.copyOfRange(100, 150).toList(), slice.toList())

        source.close()
        zip.delete()
    }

    @Test
    fun readsStoredAndEmptyEntries() {
        val zip = File.createTempFile("zipeek-store", ".zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.setLevel(0)
            z.putNextEntry(ZipEntry("empty.txt"))
            z.closeEntry()
            z.putNextEntry(ZipEntry("stored.txt"))
            z.write("abc".toByteArray())
            z.closeEntry()
        }
        val (engine, tree, source) = openOf(zip)
        val stored = tree.all.first { it.name == "stored.txt" }
        assertEquals("abc", engine.openStream(stored).use { it.readBytes().toString() })
        assertEquals(0L, tree.all.first { it.name == "empty.txt" }.size)
        source.close()
        zip.delete()
    }

    @Test
    fun searchFindsNestedFiles() {
        val zip = File.createTempFile("zipeek-search", ".zip")
        buildZip(zip) { z ->
            z.putNextEntry(ZipEntry("a/b/readme.md"))
            z.write("# hi".toByteArray())
            z.closeEntry()
            z.putNextEntry(ZipEntry("a/b/photo.jpg"))
            z.write(byteArrayOf(1, 2, 3))
            z.closeEntry()
        }
        val (_, tree, source) = openOf(zip)
        assertEquals(listOf("a/b/readme.md"), tree.search("readme").map { it.path })
        assertEquals(2, tree.search(".b").size)
        source.close()
        zip.delete()
    }

    @Test
    fun handlesMissingCentralDirectoryGracefully() {
        val broken = File.createTempFile("zipeek-bad", ".zip")
        broken.writeBytes(ByteArray(64))
        val raf = RandomAccessFile(broken, "r")
        val source = RandomAccessSource(raf.channel, raf.length())
        val error = runCatching { ZipArchive(source).entries() }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error is ArchiveException)
        source.close()
        broken.delete()
    }

    @Test
    fun detectsFormatByMagic() {
        val zip = File.createTempFile("zipeek-detect", ".zip")
        buildZip(zip) { z ->
            z.putNextEntry(ZipEntry("a.txt"))
            z.write("x".toByteArray())
            z.closeEntry()
        }
        FileInputStream(zip).use { input ->
            val head = ByteArray(64)
            val n = input.read(head)
            assertEquals(ArchiveFormat.ZIP, ArchiveFormatDetector.detect(head.copyOf(n), zip.name))
        }
        assertEquals(ArchiveFormat.RAR, ArchiveFormatDetector.detect(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00), "x.rar"))
        assertEquals(ArchiveFormat.SEVEN_Z, ArchiveFormatDetector.detect(byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C), "x.7z"))
        zip.delete()
    }

    @Test
    fun stripsKnownArchiveExtensions() {
        assertEquals("backup", ArchiveFactory.stripArchiveExt("backup.tar.gz"))
        assertEquals("photos", ArchiveFactory.stripArchiveExt("photos.zip"))
        assertEquals("demo", ArchiveFactory.stripArchiveExt("demo.7z"))
        assertEquals("plain", ArchiveFactory.stripArchiveExt("plain"))
    }

    @Test
    fun blocksZipSlipPaths() {
        val evil = File.createTempFile("zipeek-evil", ".zip")
        buildZip(evil) { z ->
            z.putNextEntry(ZipEntry("../../etc/passwd"))
            z.write("x".toByteArray())
            z.closeEntry()
        }
        val (_, tree, source) = openOf(evil)
        val entry = tree.all.first()
        val rel = com.zpeek.app.core.PathUtil.safeRelative(entry.path)
        source.close()
        evil.delete()
        assertNotNull(rel)
        assertTrue("路径应被规范化且不含上级目录", !rel!!.contains(".."))
    }
}
