package com.zpeek.app.core

import com.zpeek.app.core.rar.RarArchive
import com.zpeek.app.core.sevenz.SevenZArchive
import com.zpeek.app.core.tar.SingleCompressedArchive
import com.zpeek.app.core.tar.TarArchive
import com.zpeek.app.core.zip.ZipArchive

/**
 * 目录树：把各引擎给出的扁平条目列表整理成可导航的层级结构，
 * 并统计每个目录的累计大小与压缩体积。
 */
class ArchiveTree(entries: List<ArchiveEntry>) {

    val all: List<ArchiveEntry> = entries

    private val childrenMap = HashMap<String, MutableList<ArchiveEntry>>()
    private val dirSize = HashMap<String, Long>()
    private val dirPacked = HashMap<String, Long>()
    private val entryByPath = HashMap<String, ArchiveEntry>()

    val topLevel: List<ArchiveEntry> get() = childrenOf("")

    init {
        val roots = ArrayList<ArchiveEntry>()
        val synthetic = HashMap<String, ArchiveEntry>()
        for (e in entries) {
            if (e.path.isEmpty()) continue
            if (!entryByPath.containsKey(e.path)) entryByPath[e.path] = e
            val parent = e.parent
            if (parent.isEmpty()) {
                roots.add(e)
            } else {
                childrenMap.getOrPut(parent) { ArrayList() }.add(e)
            }
        }
        // 补齐隐式目录
        for (e in entries) {
            var p = e.parent
            while (p.isNotEmpty()) {
                if (!synthetic.containsKey(p) && !entryByPath.containsKey(p)) {
                    synthetic[p] = ArchiveEntry(p, true, 0, 0, 0, -1, true)
                }
                val idx = p.lastIndexOf('/')
                p = if (idx < 0) "" else p.substring(0, idx)
            }
        }
        for ((path, e) in synthetic) {
            val parent = e.parent
            if (parent.isEmpty()) roots.add(e) else childrenMap.getOrPut(parent) { ArrayList() }.add(e)
        }
        roots.sortWith(compareEntries())
        childrenMap.values.forEach { it.sortWith(compareEntries()) }
        childrenMap[""] = roots

        // 目录体积自底向上累计
        val depthCache = HashMap<String, Int>()
        fun depthOf(p: String): Int = depthCache.getOrPut(p) {
            if (p.isEmpty()) 0 else {
                val idx = p.lastIndexOf('/')
                depthOf(if (idx < 0) "" else p.substring(0, idx)) + 1
            }
        }
        val sorted = entries.sortedByDescending { depthOf(it.path) }
        for (e in sorted) {
            if (e.index < 0) continue
            if (!e.isDirectory) {
                var p = e.parent
                while (true) {
                    dirSize[p] = (dirSize[p] ?: 0L) + e.size
                    dirPacked[p] = (dirPacked[p] ?: 0L) + e.packedSize
                    if (p.isEmpty()) break
                    val idx = p.lastIndexOf('/')
                    p = if (idx < 0) "" else p.substring(0, idx)
                }
            }
        }
    }

    fun childrenOf(dir: String): List<ArchiveEntry> = childrenMap[dir] ?: emptyList()

    fun find(path: String): ArchiveEntry? = entryByPath[path]

    fun sizeOf(path: String): Long = dirSize[path] ?: 0L

    fun packedOf(path: String): Long = dirPacked[path] ?: 0L

    fun isDirectory(path: String): Boolean {
        val e = entryByPath[path] ?: return childrenMap.containsKey(path)
        return e.isDirectory
    }

    /** 递归收集目录下的所有文件条目 */
    fun filesUnder(path: String): List<ArchiveEntry> {
        val out = ArrayList<ArchiveEntry>()
        fun walk(p: String) {
            childrenMap[p]?.forEach { child ->
                if (child.isDirectory) walk(child.path) else out.add(child)
            }
        }
        walk(if (path.isEmpty()) "" else path)
        return out
    }

    fun search(query: String, limit: Int = 300): List<ArchiveEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return all.asSequence()
            .filter { !it.isDirectory && it.path.lowercase().contains(q) }
            .take(limit)
            .toList()
    }

    private companion object {
        fun compareEntries(): Comparator<ArchiveEntry> = Comparator { a, b ->
            if (a.isDirectory != b.isDirectory) return@Comparator if (a.isDirectory) -1 else 1
            val c = a.name.compareTo(b.name, ignoreCase = true)
            if (c != 0) c else a.name.compareTo(b.name)
        }
    }
}

/** 已打开的压缩包会话 */
class OpenArchive(
    val source: ArchiveSource,
    val engine: ArchiveEngine,
    val tree: ArchiveTree,
) : AutoCloseable {
    val format: ArchiveFormat get() = engine.format
    override fun close() {
        runCatching { engine.close() }
        runCatching { source.close() }
    }
}

object ArchiveFactory {

    fun open(source: ArchiveSource): OpenArchive {
        val access = source.openAccess()
        val format = source.format
        val engine: ArchiveEngine = try {
            when (format) {
                ArchiveFormat.ZIP -> ZipArchive(access)
                ArchiveFormat.RAR -> RarArchive(access)
                ArchiveFormat.SEVEN_Z -> SevenZArchive(access)
                ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_BZ2, ArchiveFormat.TAR_XZ ->
                    TarArchive(access, format)
                ArchiveFormat.GZ, ArchiveFormat.BZ2, ArchiveFormat.XZ ->
                    SingleCompressedArchive(access, format, stripArchiveExt(source.displayName))
                ArchiveFormat.UNKNOWN -> throw ArchiveException(
                    "暂不支持的压缩格式：${source.displayName}"
                )
            }
        } catch (t: Throwable) {
            if (t is ArchiveException) throw t
            throw ArchiveException("打开失败：${t.message ?: t::class.java.simpleName}", t)
        }
        return try {
            OpenArchive(source, engine, ArchiveTree(engine.entries()))
        } catch (t: Throwable) {
            runCatching { engine.close() }
            if (t is ArchiveException) throw t
            throw ArchiveException("读取压缩包目录失败：${t.message}", t)
        }
    }

    fun stripArchiveExt(name: String): String {
        val patterns = listOf(".tar.gz", ".tar.bz2", ".tar.xz", ".tgz", ".tbz2", ".tbz", ".txz", ".zip", ".rar", ".7z", ".tar", ".gz", ".bz2", ".xz")
        val lower = name.lowercase()
        for (p in patterns) if (lower.endsWith(p)) return name.substring(0, name.length - p.length)
        return name
    }
}
