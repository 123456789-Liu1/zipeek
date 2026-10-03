package com.zpeek.app.ui.view

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zpeek.app.R
import com.zpeek.app.core.ArchiveEngine
import com.zpeek.app.core.ArchiveEntry
import com.zpeek.app.player.PlaybackServiceLauncher
import com.zpeek.app.player.PlayerHolder
import com.zpeek.app.ui.components.AppButton
import com.zpeek.app.util.Format
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun AudioScreen(
    engine: ArchiveEngine,
    entry: ArchiveEntry,
    playlist: List<ArchiveEntry>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember { PlayerHolder.also { it.init(context) }.player }
    val eq = remember { EqualizerController(context) }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var showSpeed by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var eqAvailable by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var preset by remember { mutableStateOf(EqualizerController.Preset.FLAT) }
    val bandLevels = remember { mutableStateListOf<Int>() }
    val listState = rememberLazyListState()

    val tracks = remember(playlist, entry.path) {
        if (playlist.isEmpty()) listOf(entry) else playlist
    }

    LaunchedEffect(entry.path) {
        if (player.currentMediaItem?.mediaId != entry.path) {
            PlaybackServiceLauncher.ensureStarted(context)
            val idx = tracks.indexOfFirst { it.path == entry.path }.coerceAtLeast(0)
            PlayerHolder.playList(engine, tracks, idx, 0L, playWhenReady = true)
        }
    }

    DisposableEffect(Unit) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) eq.attach(player.audioSessionId)
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                currentIndex = player.currentMediaItemIndex
                PlayerHolder.currentEntry?.let { eq.attach(player.audioSessionId) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_READY) {
                    eqAvailable = eq.attach(player.audioSessionId)
                    if (eqAvailable && bandLevels.isEmpty()) {
                        bandLevels.clear()
                        repeat(eq.bandCount) { bandLevels.add(eq.bandLevel(it)) }
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    DisposableEffect(Unit) {
        onDispose { eq.release() }
    }
    DisposableEffect(Unit) {
        onDispose { PlaybackServiceLauncher.stopIfIdle(context) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (!dragging) {
                position = player.currentPosition.coerceAtLeast(0)
                duration = player.duration.coerceAtLeast(0)
                currentIndex = player.currentMediaItemIndex
            }
            delay(400)
        }
    }

    val current = tracks.getOrNull(currentIndex) ?: entry

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // 顶部
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
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
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                "音频播放",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (showEq) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { showEq = !showEq }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_eq),
                        contentDescription = "均衡器",
                        modifier = Modifier.size(16.dp),
                        tint = if (showEq) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "均衡器",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (showEq) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 封面
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 34.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFEC4899), Color(0xFF8B5CF6))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_type_audio),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(78.dp),
                )
            }
        }

        Column(Modifier.padding(horizontal = 26.dp)) {
            Text(
                current.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${Format.size(current.size)} · ${Format.date(current.modified)} · 压缩包内直读",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(10.dp))

        // 进度
        Column(Modifier.padding(horizontal = 22.dp)) {
            Slider(
                value = if (duration > 0) (if (dragging) dragPosition else position).toFloat() / duration else 0f,
                onValueChange = { v ->
                    dragging = true
                    dragPosition = (v * duration).toLong().coerceAtLeast(0)
                },
                onValueChangeFinished = {
                    player.seekTo(dragPosition)
                    dragging = false
                },
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    Format.duration(if (dragging) dragPosition else position),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    Format.duration(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 控制
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            SpeedChip(speed, onClick = { showSpeed = !showSpeed }, dark = false)
            Spacer(Modifier.width(18.dp))
            Icon(
                painterResource(R.drawable.ic_prev),
                contentDescription = "上一首",
                tint = if (currentIndex > 0) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier
                    .size(30.dp)
                    .clickable(enabled = currentIndex > 0) { player.seekTo(currentIndex - 1, 0L) },
            )
            Spacer(Modifier.width(20.dp))
            PlayPauseButton(
                onClick = { if (player.isPlaying) player.pause() else player.play() },
                size = 66,
                dark = false,
            )
            Spacer(Modifier.width(20.dp))
            Icon(
                painterResource(R.drawable.ic_next),
                contentDescription = "下一首",
                tint = if (currentIndex < tracks.lastIndex) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier
                    .size(30.dp)
                    .clickable(enabled = currentIndex < tracks.lastIndex) { player.seekTo(currentIndex + 1, 0L) },
            )
            Spacer(Modifier.width(18.dp))
        }

        // 倍速
        AnimatedVisibility(showSpeed) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 4.dp)) {
                Text("播放速度", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SPEED_OPTIONS.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { s ->
                                val active = abs(s - speed) < 0.01f
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (active) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable {
                                            speed = s
                                            player.setPlaybackSpeed(s)
                                            showSpeed = false
                                        }
                                        .padding(horizontal = 9.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "${if (s % 1f == 0f) s.toInt().toString() else s.toString()}x",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 均衡器
        AnimatedVisibility(showEq) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EqualizerController.Preset.entries.forEach { p ->
                        val active = p == preset
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    preset = p
                                    eq.applyPreset(p)
                                    bandLevels.clear()
                                    repeat(eq.bandCount) { bandLevels.add(eq.bandLevel(it)) }
                                }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Text(
                                p.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (!eqAvailable) {
                    Text(
                        "当前设备不支持系统均衡器",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(eq.bandCount) { band ->
                            val level = bandLevels.getOrElse(band) { 0 }
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "$level dB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Slider(
                                    value = level.toFloat(),
                                    onValueChange = { v ->
                                        val l = v.toInt()
                                        bandLevels[band] = l
                                        eq.setBand(band, l)
                                    },
                                    valueRange = eq.minLevel.toFloat()..eq.maxLevel.toFloat().coerceAtLeast(
                                        eq.minLevel.toFloat() + 1f
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(24.dp),
                                )
                                Text(
                                    "频段 ${band + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 播放列表
        Text(
            "同目录音频（${tracks.size}）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 22.dp, top = 6.dp, bottom = 4.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            items(tracks, key = { it.path }) { t ->
                val active = t.path == current.path
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
                        )
                        .clickable {
                            val idx = tracks.indexOfFirst { it.path == t.path }
                            if (idx >= 0) player.seekTo(idx, 0L)
                        }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_type_audio),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        t.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Format.size(t.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
