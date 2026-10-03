package com.zpeek.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zpeek.app.core.ArchiveEntry
import com.zpeek.app.core.ArchiveFactory
import com.zpeek.app.core.ArchiveSource
import com.zpeek.app.core.DestWriter
import com.zpeek.app.core.ExtractProgress
import com.zpeek.app.core.Extractor
import com.zpeek.app.core.FileDestWriter
import com.zpeek.app.core.FileKind
import com.zpeek.app.core.FileKinds
import com.zpeek.app.core.OpenArchive
import com.zpeek.app.core.PackFormat
import com.zpeek.app.core.PackInput
import com.zpeek.app.core.PackItem
import com.zpeek.app.core.PackLevel
import com.zpeek.app.core.Packer
import com.zpeek.app.core.SafDestWriter
import com.zpeek.app.data.RecentItem
import com.zpeek.app.data.Recents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Route {
    data object Home : Route
    data object Compress : Route
    data class Browse(val dir: String) : Route
    data class Preview(val path: String, val siblings: List<String>) : Route
}

enum class SortMode(val label: String) { NAME("名称"), SIZE("大小"), TIME("时间"), RATIO("压缩率") }

data class TaskState(
    val title: String,
    val ratio: Float,
    val detail: String,
    val cancellable: Boolean = true,
)

enum class CompressInput { NONE, FILES, FOLDER }

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val recentsStore = Recents(app)

    val backStack = mutableStateListOf<Route>(Route.Home)
    var archive by mutableStateOf<OpenArchive?>(null)
        private set
    var recents by mutableStateOf<List<RecentItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        var loadingText by mutableStateOf("")
    var loadError by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)

    var sortMode by mutableStateOf(SortMode.NAME)
    var searchQuery by mutableStateOf("")

    var task by mutableStateOf<TaskState?>(null)
    private var taskJob: Job? = null

    // 压缩向导状态
    var compressItems by mutableStateOf<List<PackItem>>(emptyList())
    var compressInputKind by mutableStateOf(CompressInput.NONE)
    var compressName by mutableStateOf("")
    var compressFormat by mutableStateOf(PackFormat.ZIP)
    var compressLevel by mutableStateOf(PackLevel.NORMAL)

    // 解压目标记忆
    var lastExtractTree by mutableStateOf<Uri?>(null)

    init {
        recents = recentsStore.load()
    }

    // ------------------------------------------------------------ 导航

    val canPop: Boolean get() = backStack.size > 1

    fun push(route: Route) {
        backStack.add(route)
    }

    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        if (backStack.none { it is Route.Browse || it is Route.Preview }) {
            // 回到首页时释放压缩包句柄
            releaseArchive()
        }
        return true
    }

    fun popToHome() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        releaseArchive()
    }

    /** 面包屑跳转：把浏览层级直接切换到目标目录 */
    fun navigateBrowseTo(target: String) {
        val idx = backStack.indexOfLast { it is Route.Browse }
        if (idx < 0) return
        while (backStack.size > idx + 1) backStack.removeAt(backStack.lastIndex)
        if (target.isEmpty()) {
            if (idx == 0) backStack.add(Route.Browse("")) else backStack[idx] = Route.Browse("")
        } else {
            backStack[idx] = Route.Browse(target)
        }
    }

    private fun releaseArchive() {
        archive?.close()
        archive = null
    }

    fun consumeToast() {
        toast = null
    }

    // ------------------------------------------------------------ 打开压缩包

    fun openUri(uri: Uri) {
        viewModelScope.launch {
            loading = true
            loadingText = "正在读取压缩包…"
            loadError = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val source = ArchiveSource.open(getApplication(), uri)
                    ArchiveFactory.open(source)
                }
            }
            loading = false
            loadingText = ""
            result.onSuccess { opened ->
                releaseArchive()
                archive = opened
                backStack.clear()
                backStack.add(Route.Home)
                backStack.add(Route.Browse(""))
                searchQuery = ""
                recents = recentsStore.touch(
                    RecentItem(
                        uri = uri.toString(),
                        name = opened.source.displayName,
                        size = opened.source.size,
                        format = opened.format,
                        time = System.currentTimeMillis(),
                    )
                )
            }.onFailure { e ->
                loadError = friendlyError(e)
            }
        }
    }

    fun openRecent(item: RecentItem) {
        val ctx = getApplication<Application>()
        if (!recentsStore.hasPermission(ctx, item.uri)) {
            toast = "缺少访问权限，请重新选择该文件"
            recents = recents.filterNot { it.uri == item.uri }
            return
        }
        openUri(Uri.parse(item.uri))
    }

    fun removeRecent(item: RecentItem) {
        recents = recents.filterNot { it.uri == item.uri }
        recentsStore.remove(item.uri)
    }

    fun clearRecents() {
        recents = recentsStore.clear()
    }

    // ------------------------------------------------------------ 条目打开

    fun entryKind(entry: ArchiveEntry): FileKind = FileKinds.kindOf(entry.name)

    fun preview(entry: ArchiveEntry, dir: String) {
        val siblings = archive?.tree?.childrenOf(dir).orEmpty()
            .filter { !it.isDirectory && FileKinds.kindOf(it.name) == FileKinds.kindOf(entry.name) }
            .map { it.path }
            .ifEmpty { listOf(entry.path) }
        push(Route.Preview(entry.path, siblings))
    }

    fun siblingsOf(route: Route.Preview): List<String> = route.siblings

    // ------------------------------------------------------------ 排序

    fun sorted(list: List<ArchiveEntry>): List<ArchiveEntry> = when (sortMode) {
        SortMode.NAME -> list
        SortMode.SIZE -> list.sortedByDescending { if (it.isDirectory) -1L else it.size }
        SortMode.TIME -> list.sortedByDescending { it.modified }
        SortMode.RATIO -> list.sortedByDescending { if (it.isDirectory) -1 else it.ratio }
    }

    // ------------------------------------------------------------ 解压

    fun startExtract(
        entries: List<ArchiveEntry>,
        destTree: Uri,
    ) {
        val arc = archive ?: return
        val ctx = getApplication<Application>()
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                destTree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        lastExtractTree = destTree
        launchTask("正在解压") {
            val dest: DestWriter = SafDestWriter(ctx, destTree)
            val written = Extractor.extract(arc.engine, entries, dest, overwrite = true) { p ->
                postProgress(p)
            }
            "已解压 ${entries.count { !it.isDirectory }} 个文件（${com.zpeek.app.util.Format.size(written)}）"
        }
    }

    fun extractToAppCache(entry: ArchiveEntry, onDone: (File) -> Unit) {
        val arc = archive ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val dir = File(getApplication<Application>().cacheDir, "share").apply { mkdirs() }
                val f = File(dir, entry.name)
                arc.engine.extractTo(entry, f)
                f
            }.onSuccess { onDone(it) }.onFailure { toast = friendlyError(it) }
        }
    }

    // ------------------------------------------------------------ 压缩

    fun setCompressFiles(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { PackInput.fromUris(getApplication(), uris) }
                .onSuccess {
                    compressItems = it
                    compressInputKind = CompressInput.FILES
                    if (compressName.isBlank()) compressName = defaultArchiveName(it)
                }
                .onFailure { toast = friendlyError(it) }
        }
    }

    fun setCompressFolder(tree: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { PackInput.fromTree(getApplication(), tree) }
                .onSuccess {
                    compressItems = it
                    compressInputKind = CompressInput.FOLDER
                    if (compressName.isBlank()) compressName = defaultArchiveName(it)
                }
                .onFailure { toast = friendlyError(it) }
        }
    }

    fun resetCompress() {
        compressItems = emptyList()
        compressInputKind = CompressInput.NONE
        compressName = ""
    }

    fun startCompress(target: Uri) {
        val ctx = getApplication<Application>()
        val items = compressItems
        val format = compressFormat
        val level = compressLevel
        val name = compressName.ifBlank { defaultArchiveName(items) }
        launchTask("正在压缩") {
            val temp = File(ctx.cacheDir, "pack").apply { mkdirs() }
            val out = File(temp, "out_${System.currentTimeMillis()}.${format.ext}")
            withContext(Dispatchers.IO) {
                Packer.pack(items, out, format, level) { done, total, current ->
                    val ratio = if (total > 0) done.toFloat() / total else 0f
                    task = TaskState("正在压缩", ratio, current)
                }
            }
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(target, "wt")?.use { os ->
                    out.inputStream().use { it.copyTo(os, 1 shl 16) }
                } ?: throw IllegalStateException("无法写入所选位置")
            }
            val finalSize = out.length()
            withContext(Dispatchers.IO) { out.delete() }
            "已生成 ${name}（${com.zpeek.app.util.Format.size(finalSize)}）"
        }
    }

    private fun defaultArchiveName(items: List<PackItem>): String {
        val base = items.firstOrNull()?.name?.substringBeforeLast('.') ?: "新建压缩包"
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date())
        return "${base}_$stamp"
    }

    // ------------------------------------------------------------ 任务

    private fun launchTask(title: String, block: suspend () -> String) {
        taskJob?.cancel()
        task = TaskState(title, 0f, "准备中…")
        taskJob = viewModelScope.launch {
            val message = runCatching { block() }.getOrElse { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                friendlyError(e)
            }
            task = null
            if (message.isNotEmpty()) toast = message
        }
    }

    private fun postProgress(p: ExtractProgress) {
        task = TaskState(
            title = "正在解压",
            ratio = p.ratio,
            detail = "${p.index}/${p.count}  ${p.current}",
        )
    }

    fun cancelTask() {
        taskJob?.cancel()
        taskJob = null
        task = null
        toast = "已取消"
    }

    fun dismissTask() {
        task = null
    }

    fun friendlyError(e: Throwable): String {
        android.util.Log.e("ZipPeek", "fail", e)
        val msg = generateSequence(e) { it.cause }
            .firstNotNullOfOrNull { it.message?.takeIf { m -> m.isNotBlank() } }
            ?: e::class.java.simpleName
        return when {
            msg.contains("encrypted", true) || msg.contains("加密") -> "该文件已加密，本应用暂不支持加密压缩包"
            msg.contains("OutOfMemory") -> "文件过大，内存不足"
            else -> msg
        }
    }
}
