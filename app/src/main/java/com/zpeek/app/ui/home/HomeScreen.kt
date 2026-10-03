package com.zpeek.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zpeek.app.R
import com.zpeek.app.core.ArchiveFormat
import com.zpeek.app.data.RecentItem
import com.zpeek.app.ui.archiveColor
import com.zpeek.app.ui.archiveIcon
import com.zpeek.app.ui.components.AppButton
import com.zpeek.app.ui.components.EmptyHint
import com.zpeek.app.util.Format

@Composable
fun HomeScreen(
    recents: List<RecentItem>,
    onOpenArchive: () -> Unit,
    onCompress: () -> Unit,
    onOpenRecent: (RecentItem) -> Unit,
    onRemoveRecent: (RecentItem) -> Unit,
    onClearRecents: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { HeaderCard() }
        item { Spacer(Modifier.height(2.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton(
                    text = "打开压缩包",
                    onClick = onOpenArchive,
                    primary = true,
                    modifier = Modifier.weight(1f),
                    icon = painterResource(R.drawable.ic_open_folder),
                )
                AppButton(
                    text = "新建压缩包",
                    onClick = onCompress,
                    modifier = Modifier.weight(1f),
                    icon = painterResource(R.drawable.ic_compress),
                )
            }
        }
        item {
            Spacer(Modifier.height(6.dp))
            FormatStrip()
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "最近浏览",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                if (recents.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onClearRecents)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "清空",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (recents.isEmpty()) {
            item {
                EmptyHint(
                    painter = painterResource(R.drawable.ic_empty_placeholder),
                    title = "还没有浏览记录",
                    subtitle = "选择一个压缩包即可直接浏览其中的图片、视频、音频与文档",
                )
            }
        } else {
            items(recents, key = { it.uri }) { item ->
                RecentRow(item, onClick = { onOpenRecent(item) }, onRemove = { onRemoveRecent(item) })
            }
        }
    }
}

@Composable
private fun HeaderCard() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF2F6BFF), Color(0xFF6D5BF5))
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = Color.Unspecified,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "压缩速览",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "无需解压，直接查看压缩包内的图片、视频、音频与文本",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "支持 ZIP · RAR · 7Z · TAR 及 GZ/BZ2/XZ 单文件压缩",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun FormatStrip() {
    val formats = listOf(
        ArchiveFormat.ZIP, ArchiveFormat.RAR, ArchiveFormat.SEVEN_Z, ArchiveFormat.TAR,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        formats.forEach { f ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = archiveColor(f).copy(alpha = 0.12f),
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    Modifier.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painterResource(archiveIcon(f)),
                        contentDescription = f.label,
                        modifier = Modifier.size(22.dp),
                        tint = Color.Unspecified,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        f.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = archiveColor(f),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentRow(item: RecentItem, onClick: () -> Unit, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(archiveColor(item.format).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(archiveIcon(item.format)),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Color.Unspecified,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${item.format.label} · ${Format.size(item.size)} · ${Format.date(item.time)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "移除",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
