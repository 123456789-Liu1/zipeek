package com.zpeek.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zpeek.app.core.ArchiveEntry
import com.zpeek.app.core.FileKind
import com.zpeek.app.core.FileKinds
import com.zpeek.app.ui.AppViewModel
import com.zpeek.app.ui.Route
import com.zpeek.app.ui.browse.ArchiveScreen
import com.zpeek.app.ui.components.AppButton
import com.zpeek.app.ui.components.ErrorHint
import com.zpeek.app.ui.components.LoadingBox
import com.zpeek.app.ui.components.TaskOverlay
import com.zpeek.app.ui.compress.CompressScreen
import com.zpeek.app.ui.home.HomeScreen
import com.zpeek.app.ui.theme.ZipPeekTheme
import com.zpeek.app.ui.view.AudioScreen
import com.zpeek.app.ui.view.ImageViewer
import com.zpeek.app.ui.view.TextViewer
import com.zpeek.app.ui.view.VideoScreen
import com.zpeek.app.util.Format
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    private var pendingExtract: List<ArchiveEntry> = emptyList()

    private val openArchive = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.openUri(it) }
    }

    private val pickExtractDir = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { vm.startExtract(pendingExtract, it) }
    }

    private val pickCompressFiles =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) vm.setCompressFiles(uris)
        }

    private val pickCompressFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { vm.setCompressFolder(it) }
    }

    private val createArchiveFile =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            uri?.let { vm.startCompress(it) }
        }

    private val requestNotification =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handleViewIntent(intent)

        setContent {
            ZipPeekTheme {
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot(
                        vm = vm,
                        onOpenArchive = { openArchive.launch(ARCHIVE_MIME_TYPES) },
                        onPickExtractDir = { entries ->
                            pendingExtract = entries
                            pickExtractDir.launch(null)
                        },
                        onPickFiles = { pickCompressFiles.launch(ARCHIVE_MIME_TYPES) },
                        onPickFolder = { pickCompressFolder.launch(null) },
                        onSaveArchive = { name ->
                            createArchiveFile.launch(name)
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    private fun handleViewIntent(intent: Intent?) {
        val uri: Uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            else -> null
        } ?: return
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        vm.openUri(uri)
    }

    companion object {
        val ARCHIVE_MIME_TYPES = arrayOf(
            "application/zip",
            "application/x-7z-compressed",
            "application/vnd.rar",
            "application/x-rar-compressed",
            "application/gzip",
            "application/x-tar",
            "application/x-bzip2",
            "application/octet-stream",
            "*/*",
        )
    }
}

@Composable
private fun AppRoot(
    vm: AppViewModel,
    onOpenArchive: () -> Unit,
    onPickExtractDir: (List<ArchiveEntry>) -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onSaveArchive: (String) -> Unit,
) {
    val route = vm.backStack.last()
    var showInfo by remember { mutableStateOf(false) }

    BackHandler(enabled = vm.canPop) { vm.pop() }

    Box(Modifier.fillMaxSize()) {
        when (route) {
            is Route.Home -> HomeScreen(
                recents = vm.recents,
                onOpenArchive = onOpenArchive,
                onCompress = { vm.push(Route.Compress) },
                onOpenRecent = vm::openRecent,
                onRemoveRecent = vm::removeRecent,
                onClearRecents = vm::clearRecents,
            )

            is Route.Compress -> CompressScreen(
                items = vm.compressItems,
                name = vm.compressName,
                format = vm.compressFormat,
                level = vm.compressLevel,
                onNameChange = { vm.compressName = it },
                onFormatChange = { vm.compressFormat = it },
                onLevelChange = { vm.compressLevel = it },
                onPickFiles = onPickFiles,
                onPickFolder = onPickFolder,
                onRemoveItem = { i ->
                    vm.compressItems = vm.compressItems.filterIndexed { idx, _ -> idx != i }
                },
                onStart = {
                    onSaveArchive("${vm.compressName}.${vm.compressFormat.ext}")
                },
                onBack = {
                    vm.resetCompress()
                    vm.pop()
                },
            )

            is Route.Browse -> {
                val archive = vm.archive
                if (archive == null) {
                    LoadingBox("正在准备…")
                } else {
                    var searchActive by remember { mutableStateOf(false) }
                    ArchiveScreen(
                        archive = archive,
                        dir = route.dir,
                        sortMode = vm.sortMode,
                        searchActive = searchActive,
                        searchQuery = vm.searchQuery,
                        onSearchQueryChange = { vm.searchQuery = it },
                        onSearchToggle = { searchActive = !searchActive },
                        onSortChange = { vm.sortMode = it },
                        onOpenEntry = { entry ->
                            if (entry.isDirectory) {
                                vm.push(Route.Browse(entry.path))
                            } else {
                                vm.preview(entry, route.dir)
                            }
                        },
                        onOpenDir = { path -> vm.navigateBrowseTo(path) },
                        onBack = { vm.popToHome() },
                        onExtractAll = {
                            val files = archive.tree.filesUnder(route.dir)
                            onPickExtractDir(if (files.isEmpty()) archive.tree.all else files)
                        },
                        onExtractSelected = { entries -> onPickExtractDir(entries) },
                        onCloseArchive = { vm.popToHome() },
                        onShowInfo = { showInfo = true },
                        sorted = vm::sorted,
                    )
                }
            }

            is Route.Preview -> {
                val archive = vm.archive
                val entry = archive?.tree?.find(route.path)
                if (archive == null || entry == null) {
                    ErrorHint("文件已不存在")
                } else {
                    val kind = FileKinds.kindOf(entry.name)
                    val siblings = route.siblings.mapNotNull { archive.tree.find(it) }
                    when (kind) {
                        FileKind.IMAGE -> ImageViewer(
                            engine = archive.engine,
                            entries = siblings.ifEmpty { listOf(entry) },
                            currentPath = entry.path,
                            onBack = { vm.pop() },
                            onExtract = { onPickExtractDir(listOf(it)) },
                        )

                        FileKind.VIDEO -> VideoScreen(
                            engine = archive.engine,
                            entry = entry,
                            onBack = { vm.pop() },
                        )

                        FileKind.AUDIO -> AudioScreen(
                            engine = archive.engine,
                            entry = entry,
                            playlist = siblings,
                            onBack = { vm.pop() },
                        )

                        FileKind.TEXT, FileKind.CODE -> TextViewer(
                            engine = archive.engine,
                            entry = entry,
                            onBack = { vm.pop() },
                            onExtract = { onPickExtractDir(listOf(it)) },
                        )

                        else -> UnsupportedEntry(
                            entry = entry,
                            onBack = { vm.pop() },
                            onExtract = { onPickExtractDir(listOf(it)) },
                        )
                    }
                }
            }
        }

        // 全局加载
        if (vm.loading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
            ) {
                LoadingBox(vm.loadingText)
            }
        }

        // 任务进度
        vm.task?.let { state ->
            TaskOverlay(state = state, onCancel = vm::cancelTask)
        }

        // 轻提示
        vm.toast?.let { message ->
            LaunchedEffect(message) {
                delay(2600)
                vm.consumeToast()
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 40.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Snackbar(
                    Modifier.padding(horizontal = 20.dp),
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface,
                ) {
                    Text(message, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // 打开失败
        vm.loadError?.let { msg ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ErrorHint(msg) {
                        vm.loadError = null
                        onOpenArchive()
                    }
                    AppButton(text = "关闭", onClick = { vm.loadError = null })
                }
            }
        }

        // 压缩包信息
        if (showInfo && vm.archive != null) {
            ArchiveInfoDialog(
                onDismiss = { showInfo = false },
                lines = buildList {
                    add("格式：${vm.archive!!.format.label}")
                    add("大小：${Format.size(vm.archive!!.source.size)}")
                    add("条目：${vm.archive!!.tree.all.size} 项")
                    add("文件：${vm.archive!!.tree.all.count { !it.isDirectory }} 个")
                    add("解压后总大小：${Format.size(vm.archive!!.tree.all.filter { !it.isDirectory }.sumOf { it.size })}")
                    add("随机访问：${if (vm.archive!!.source.isRandomAccess) "支持（无需复制压缩包）" else "受限（已复制到缓存）"}")
                },
            )
        }
    }
}

@Composable
private fun UnsupportedEntry(entry: ArchiveEntry, onBack: () -> Unit, onExtract: (ArchiveEntry) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.size(40.dp))
        Icon(
            painterResource(R.drawable.ic_type_other),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = Color.Unspecified,
        )
        Spacer(Modifier.size(14.dp))
        Text(
            entry.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            "该格式暂不支持在线预览\n可先提取到本地，再用其他应用打开",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.size(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppButton(text = "返回", onClick = onBack)
            AppButton(
                text = "提取文件",
                onClick = { onExtract(entry) },
                primary = true,
                icon = painterResource(R.drawable.ic_extract),
            )
        }
    }
}

@Composable
private fun ArchiveInfoDialog(onDismiss: () -> Unit, lines: List<String>) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("压缩包信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lines.forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
        shape = RoundedCornerShape(18.dp),
    )
}
