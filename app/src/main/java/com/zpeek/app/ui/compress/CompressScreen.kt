package com.zpeek.app.ui.compress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zpeek.app.R
import com.zpeek.app.core.PackFormat
import com.zpeek.app.core.PackItem
import com.zpeek.app.core.PackLevel
import com.zpeek.app.ui.components.AppButton
import com.zpeek.app.ui.components.AppTopBar
import com.zpeek.app.util.Format

@Composable
fun CompressScreen(
    items: List<PackItem>,
    name: String,
    format: PackFormat,
    level: PackLevel,
    onNameChange: (String) -> Unit,
    onFormatChange: (PackFormat) -> Unit,
    onLevelChange: (PackLevel) -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onRemoveItem: (Int) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val totalSize = items.sumOf { it.totalSize }
    val fileCount = items.sumOf { it.fileCount }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        AppTopBar(title = "新建压缩包", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 选择来源
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppButton(
                    text = "选择文件",
                    onClick = onPickFiles,
                    primary = items.isEmpty(),
                    modifier = Modifier.weight(1f),
                    icon = painterResource(R.drawable.ic_fmt_doc),
                )
                AppButton(
                    text = "选择文件夹",
                    onClick = onPickFolder,
                    primary = items.isEmpty(),
                    modifier = Modifier.weight(1f),
                    icon = painterResource(R.drawable.ic_open_folder),
                )
            }

            Spacer(Modifier.height(14.dp))

            if (items.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "还没有选择内容",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "可以选择若干个文件，或直接选择一个文件夹（自动包含子目录）。整个过程在本地完成，不上传任何数据。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "已选 $fileCount 个文件 · ${Format.size(totalSize)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "重选",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(onClick = onPickFiles),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        items.take(12).forEachIndexed { i, item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painterResource(
                                        if (item.isDirectory) R.drawable.ic_folder else R.drawable.ic_type_other
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    Format.size(item.totalSize),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .clickable { onRemoveItem(i) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        "移除",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (items.size > 12) {
                            Text(
                                "…以及其他 ${items.size - 12} 项",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "压缩包名称",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("例如：我的文件") },
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "格式",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            PackFormat.entries.forEach { f ->
                SelectRow(
                    title = f.label,
                    subtitle = f.hint,
                    selected = format == f,
                    onClick = { onFormatChange(f) },
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "压缩强度",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PackLevel.entries.forEach { l ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (level == l) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { onLevelChange(l) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                l.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (level == l) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                l.desc,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (level == l) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            AppButton(
                text = "选择保存位置并开始压缩",
                onClick = onStart,
                primary = true,
                enabled = items.isNotEmpty() && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                icon = painterResource(R.drawable.ic_compress),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "提示：7Z 压缩率最高；TAR.GZ 仅打包不压缩，适合网络传输或快速归档。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SelectRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
