package com.zpeek.app.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zpeek.app.R
import com.zpeek.app.core.ArchiveEngine
import com.zpeek.app.core.ArchiveEntry
import com.zpeek.app.core.TextCharset
import com.zpeek.app.ui.components.AppButton
import com.zpeek.app.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

private const val MAX_TEXT_BYTES = 6L * 1024 * 1024

private data class LoadedText(
    val lines: List<String>,
    val charsetName: String,
    val truncated: Boolean,
    val binary: Boolean,
    val raw: ByteArray,
)

@Composable
fun TextViewer(
    engine: ArchiveEngine,
    entry: ArchiveEntry,
    onBack: () -> Unit,
    onExtract: (ArchiveEntry) -> Unit,
) {
    var fontSize by remember { mutableIntStateOf(15) }
    var monospace by remember { mutableStateOf(true) }
    var charsetOverride by remember { mutableStateOf<String?>(null) }
    var searchOn by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var loaded by remember { mutableStateOf<LoadedText?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val hScroll = rememberScrollState()

    suspend fun load(cs: String?) = withContext(Dispatchers.IO) {
        val bytes = engine.openStream(entry).use { input ->
            val buf = ByteArray(1 shl 16)
            val out = java.io.ByteArrayOutputStream()
            var total = 0L
            while (total < MAX_TEXT_BYTES) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                total += n
            }
            out.toByteArray()
        }
        val detected = TextCharset.detect(bytes)
        val charset: Charset = cs?.let { name ->
            TextCharset.candidates.firstOrNull { it.first == name }?.second
        } ?: detected.charset
        val text = String(bytes, charset)
        LoadedText(
            lines = text.split('\n'),
            charsetName = charset.name(),
            truncated = bytes.size >= MAX_TEXT_BYTES,
            binary = TextCharset.isBinary(bytes),
            raw = bytes,
        )
    }

    LaunchedEffect(entry.path, charsetOverride) {
        loaded = null
        error = null
        runCatching { load(charsetOverride) }
            .onSuccess { loaded = it }
            .onFailure { error = it.message ?: "读取失败" }
    }

    // 搜索定位
    LaunchedEffect(searchQuery, loaded) {
        if (searchQuery.isBlank()) return@LaunchedEffect
        val text = loaded ?: return@LaunchedEffect
        val idx = text.lines.indexOfFirst { it.contains(searchQuery, ignoreCase = true) }
        if (idx >= 0) listState.scrollToItem(idx)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 4.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    buildString {
                        append(Format.size(entry.size))
                        loaded?.let {
                            append(" · ${it.charsetName}")
                            if (it.truncated) append(" · 已截断显示前 6MB")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FontSizeControl(fontSize, monospace, onSize = { fontSize = it }, onToggleMono = { monospace = !monospace })
            CharsetMenu(loaded?.charsetName) { charsetOverride = it }
            IconButton(onClick = { searchOn = !searchOn }) {
                Icon(
                    if (searchOn) Icons.Default.Close else Icons.Default.Search,
                    "搜索",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (searchOn) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    decorationBox = { inner ->
                        Box {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "搜索文本",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        }
                    },
                )
                Spacer(Modifier.width(8.dp))
                AppButton(text = "提取", onClick = { onExtract(entry) })
            }
        }

        val data = loaded
        when {
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }

            data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }

            data.binary -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painterResource(R.drawable.ic_type_other),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(42.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "这是二进制文件，无法作为文本预览",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(hScroll),
                ) {
                    items(
                        count = data.lines.size,
                        key = { it },
                    ) { i ->
                        val line = data.lines[i]
                        val highlight = searchQuery.isNotEmpty() && line.contains(searchQuery, ignoreCase = true)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .padding(horizontal = 12.dp, vertical = 1.dp)
                        ) {
                            if (monospace) {
                                Text(
                                    text = "${i + 1}",
                                    fontSize = (fontSize - 3).sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.width(46.dp),
                                )
                            }
                            Text(
                                text = line.ifEmpty { " " },
                                fontSize = fontSize.sp,
                                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                                lineHeight = (fontSize * 1.45f).sp,
                                fontWeight = FontWeight.Normal,
                                softWrap = false,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FontSizeControl(
    size: Int,
    monospace: Boolean,
    onSize: (Int) -> Unit,
    onToggleMono: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = RoundedCornerShape(9.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .clickable { menu = true }
                .padding(horizontal = 9.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_font),
                    contentDescription = "字体",
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "$size",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            listOf(11, 13, 15, 17, 20, 24, 28).forEach { s ->
                DropdownMenuItem(
                    text = { Text("$s sp") },
                    onClick = {
                        onSize(s)
                        menu = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(if (monospace) "切换为普通字体" else "切换为等宽字体") },
                onClick = {
                    onToggleMono()
                    menu = false
                },
            )
        }
    }
}

@Composable
private fun CharsetMenu(current: String?, onPick: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = RoundedCornerShape(9.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .clickable { menu = true }
                .padding(horizontal = 9.dp, vertical = 6.dp),
        ) {
            Text(
                current?.take(5) ?: "编码",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            TextCharset.candidates.forEach { (name, _) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onPick(name)
                        menu = false
                    },
                )
            }
        }
    }
}
