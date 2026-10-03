package com.zpeek.app.ui.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zpeek.app.R
import com.zpeek.app.core.ArchiveEntry
import com.zpeek.app.core.ArchiveTree
import com.zpeek.app.core.OpenArchive
import com.zpeek.app.ui.SortMode
import com.zpeek.app.ui.archiveColor
import com.zpeek.app.ui.archiveIcon
import com.zpeek.app.ui.components.AppButton
import com.zpeek.app.ui.components.AppTopBar
import com.zpeek.app.ui.components.EmptyHint
import com.zpeek.app.ui.entryColor
import com.zpeek.app.ui.entryIcon
import com.zpeek.app.util.Format

@Composable
fun ArchiveScreen(
    archive: OpenArchive,
    dir: String,
    sortMode: SortMode,
    searchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    onSortChange: (SortMode) -> Unit,
    onOpenEntry: (ArchiveEntry) -> Unit,
    onOpenDir: (String) -> Unit,
    onBack: () -> Unit,
    onExtractAll: () -> Unit,
    onExtractSelected: (List<ArchiveEntry>) -> Unit,
    onCloseArchive: () -> Unit,
    onShowInfo: () -> Unit,
    sorted: (List<ArchiveEntry>) -> List<ArchiveEntry>,
) {
    val tree = archive.tree
    val selection = remember { mutableStateListOf<String>() }
    var sortMenu by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }

    val searching = searchActive && searchQuery.isNotBlank()
    val rawList = if (searching) tree.search(searchQuery) else tree.childrenOf(dir)
    val list = if (searching) rawList else sorted(rawList)

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        AppTopBar(
            title = archive.source.displayName,
            subtitle = "${archive.format.label} · ${Format.size(archive.source.size)} · ${tree.all.count { !it.isDirectory }} 个文件",
            onBack = onBack,
            actions = {
                IconButton(onClick = onSearchToggle) {
                    Icon(
                        if (searchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(
                            painterResource(R.drawable.ic_sort),
                            contentDescription = "排序",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortMode.entries.forEach { m ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        m.label,
                                        fontWeight = if (m == sortMode) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                trailingIcon = if (m == sortMode) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                                onClick = {
                                    onSortChange(m)
                                    sortMenu = false
                                },
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = { overflow = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "更多",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                        DropdownMenuItem(
                            text = { Text("全部解压") },
                            onClick = {
                                overflow = false
                                onExtractAll()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("关闭压缩包") },
                            onClick = {
                                overflow = false
                                onCloseArchive()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("压缩包信息") },
                            onClick = {
                                overflow = false
                                onShowInfo()
                            },
                        )
                    }
                }
            },
        )

        AnimatedVisibility(visible = searchActive) {
            SearchBar(searchQuery, onSearchQueryChange, onSearchToggle)
        }

        if (!searching) {
            Breadcrumb(tree = tree, dir = dir, onOpenDir = onOpenDir)
        }

        Box(Modifier.weight(1f)) {
            if (list.isEmpty()) {
                EmptyHint(
                    painter = painterResource(R.drawable.ic_empty_placeholder),
                    title = if (searching) "没有匹配的文件" else "这个文件夹是空的",
                    subtitle = if (searching) "换个关键词试试" else null,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = if (selection.isEmpty()) 24.dp else 92.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(list, key = { "${it.index}_${it.path}" }) { entry ->
                        EntryRow(
                            entry = entry,
                            tree = tree,
                            searching = searching,
                            selected = selection.contains(entry.path),
                            selectionMode = selection.isNotEmpty(),
                            onClick = {
                                if (selection.isNotEmpty()) {
                                    if (!selection.remove(entry.path)) selection.add(entry.path)
                                } else {
                                    onOpenEntry(entry)
                                }
                            },
                            onLongClick = {
                                if (!selection.remove(entry.path)) selection.add(entry.path)
                            },
                        )
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = selection.isNotEmpty(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        SelectionBar(
            count = selection.size,
            onSelectAll = {
                selection.clear()
                selection.addAll(list.map { it.path })
            },
            onClear = { selection.clear() },
            onExtract = {
                val chosen = tree.all.filter { selection.contains(it.path) }
                onExtractSelected(chosen)
                selection.clear()
            },
        )
    }
    }
}

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "搜索包内文件名",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onChange("") }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "清空",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Breadcrumb(tree: ArchiveTree, dir: String, onOpenDir: (String) -> Unit) {
    val segments = if (dir.isEmpty()) emptyList() else dir.split("/")
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CrumbText("全部文件", selected = segments.isEmpty()) { onOpenDir("") }
        var acc = ""
        segments.forEach { seg ->
            acc = if (acc.isEmpty()) seg else "$acc/$seg"
            val target = acc
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(13.dp),
            )
            CrumbText(seg, selected = target == dir) { onOpenDir(target) }
        }
    }
}

@Composable
private fun CrumbText(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: ArchiveEntry,
    tree: ArchiveTree,
    searching: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isDir = entry.isDirectory
    val color = if (isDir) Color(0xFFF59E0B) else entryColor(entry.name, false)
    val meta = buildString {
        if (isDir) {
            val n = tree.childrenOf(entry.path).size
            append("$n 个项目")
            val s = tree.sizeOf(entry.path)
            if (s > 0) append(" · ${Format.size(s)}")
        } else {
            append(Format.size(entry.size))
            if (entry.modified > 0) append(" · ${Format.date(entry.modified)}")
            if (entry.ratio > 0) append(" · 省 ${entry.ratio}%")
            if (!entry.readable) append(" · 加密")
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline,
                ),
            )
            Spacer(Modifier.width(4.dp))
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(color.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(entryIcon(entry.name, isDir)),
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = Color.Unspecified,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (searching && !isDir) "${entry.parent}/$meta" else meta,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isDir) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SelectionBar(count: Int, onSelectAll: () -> Unit, onClear: () -> Unit, onExtract: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "已选 $count 项",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            AppButton(text = "全选", onClick = onSelectAll)
            Spacer(Modifier.width(8.dp))
            AppButton(text = "取消", onClick = onClear)
            Spacer(Modifier.width(8.dp))
            AppButton(
                text = "解压",
                onClick = onExtract,
                primary = true,
                icon = painterResource(R.drawable.ic_extract),
            )
        }
    }
}
