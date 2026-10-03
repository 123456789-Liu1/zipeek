package com.zpeek.app.ui.view

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.Intent
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableLongStateOf
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

val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f)

private sealed interface Hud {
    data class Speed(val text: String) : Hud
    data class Brightness(val text: String) : Hud
    data class Volume(val text: String) : Hud
    data class Seek(val from: String, val to: String) : Hud
}

@Composable
fun VideoScreen(
    engine: ArchiveEngine,
    entry: ArchiveEntry,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember { PlayerHolder.also { it.init(context) }.player }
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val originalBrightness = remember {
        activity?.window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var showSpeed by remember { mutableStateOf(false) }
    var landscape by remember {
        mutableStateOf(
            activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
                activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        )
    }
    var hud by remember { mutableStateOf<Hud?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(entry.path) {
        if (player.currentMediaItem?.mediaId != entry.path) {
            PlaybackServiceLauncher.ensureStarted(context)
            PlayerHolder.playEntry(engine, entry, 0L, playWhenReady = true)
        }
    }

    DisposableEffect(Unit) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                errorText = error.localizedMessage ?: "该编码不受支持"
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    DisposableEffect(Unit) {
        onDispose {
            player.pause()
            activity?.window?.let { w ->
                w.attributes = w.attributes.apply { screenBrightness = originalBrightness }
            }
            PlaybackServiceLauncher.stopIfIdle(context)
        }
    }

    LaunchedEffect(landscape) {
        activity?.requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    DisposableEffect(Unit) {
        // 离开播放页时恢复自动旋转，避免影响其他页面
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (!dragging) {
                position = player.currentPosition.coerceAtLeast(0)
                duration = player.duration.coerceAtLeast(0)
            }
            delay(300)
        }
    }
    LaunchedEffect(controlsVisible, errorText, showSpeed) {
        if (controlsVisible && errorText == null && !showSpeed) {
            delay(3600)
            controlsVisible = false
        }
    }

    val videoSize = rememberVideoSize(player).value
    val ratio = if (videoSize.width > 0 && videoSize.height > 0)
        videoSize.width.toFloat() / videoSize.height.toFloat() else 16f / 9f

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .playerGestures(
                    onSingleTap = { controlsVisible = !controlsVisible },
                    onDoubleTapLeft = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) },
                    onDoubleTapRight = { player.seekTo(player.currentPosition + 10_000) },
                    onLongPressStart = {
                        player.setPlaybackSpeed(2.0f)
                        hud = Hud.Speed("2.0")
                    },
                    onLongPressEnd = {
                        player.setPlaybackSpeed(speed)
                        hud = null
                    },
                    onSeekDrag = { total, fraction ->
                        val span = duration.coerceAtLeast(0)
                        val to = (fraction * span).toLong().coerceIn(0, span)
                        val from = (position + total).toLong().coerceIn(0, span)
                        dragPosition = to
                        hud = Hud.Seek(Format.duration(from), Format.duration(to))
                    },
                    onSeekEnd = { fraction ->
                        val target = (fraction * duration.coerceAtLeast(0)).toLong().coerceAtLeast(0)
                        player.seekTo(target)
                        dragPosition = target
                        hud = null
                    },
                    onVolumeBrightness = { delta, xRatio ->
                        if (xRatio < 0.5f) {
                            val w = activity?.window
                            if (w != null) {
                                val attrs = w.attributes
                                val current = if (attrs.screenBrightness < 0f) 0.5f else attrs.screenBrightness
                                val next = (current + delta * 0.9f).coerceIn(0.02f, 1f)
                                attrs.screenBrightness = next
                                w.attributes = attrs
                                hud = Hud.Brightness("${(next * 100).toInt()}")
                            }
                        } else {
                            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val next = (current + delta * maxVolume * 1.4f).toInt().coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                            hud = Hud.Volume("${next * 100 / maxVolume.coerceAtLeast(1)}")
                        }
                    },
                    onVolumeBrightnessEnd = { hud = null },
                )
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .align(Alignment.Center)
            ) {
                VideoSurface(player)
            }
        }

        AnimatedVisibility(
            visible = hud != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            hud?.let { h ->
                Surface(shape = RoundedCornerShape(12.dp), color = Color.Black.copy(alpha = 0.74f)) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(
                                when (h) {
                                    is Hud.Speed -> R.drawable.ic_speed
                                    is Hud.Brightness -> R.drawable.ic_brightness
                                    is Hud.Volume -> R.drawable.ic_volume
                                    is Hud.Seek -> R.drawable.ic_speed
                                }
                            ),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (h) {
                                is Hud.Speed -> "${h.text}x 倍速播放中"
                                is Hud.Brightness -> "亮度 ${h.text}%"
                                is Hud.Volume -> "音量 ${h.text}%"
                                is Hud.Seek -> "${h.from} → ${h.to}"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.68f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.ArrowBack, "返回", tint = Color.White)
                }
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${Format.size(entry.size)} · 压缩包内直读，无需解压",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Slider(
                    value = if (duration > 0) {
                        (if (dragging) dragPosition else position).toFloat() / duration
                    } else 0f,
                    onValueChange = { v ->
                        dragging = true
                        dragPosition = (v * duration).toLong().coerceAtLeast(0)
                    },
                    onValueChangeFinished = {
                        player.seekTo(dragPosition)
                        dragging = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    ),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Format.duration(if (dragging) dragPosition else position),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        " / ${Format.duration(duration)}",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.weight(1f))
                    SpeedChip(speed, onClick = { showSpeed = !showSpeed })
                    Spacer(Modifier.width(10.dp))
                    PlayPauseButton(onClick = { if (player.isPlaying) player.pause() else player.play() })
                    Spacer(Modifier.width(10.dp))
                    // 横竖屏切换
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable { landscape = !landscape },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_rotate),
                            contentDescription = if (landscape) "切换到竖屏" else "切换到横屏",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }

        errorText?.let { msg ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painterResource(R.drawable.ic_error),
                        contentDescription = null,
                        tint = Color(0xFFFF6B6E),
                        modifier = Modifier.size(38.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "无法播放该视频\n$msg",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    AppButton(text = "返回", onClick = onBack, primary = true)
                }
            }
        }

        if (showSpeed) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showSpeed = false },
                contentAlignment = Alignment.Center,
            ) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF1B1E26)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("播放速度", color = Color.White, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.size(10.dp))
                        SPEED_OPTIONS.chunked(4).forEach { row ->
                            Row(
                                Modifier.padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                row.forEach { s ->
                                    val active = abs(s - speed) < 0.01f
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (active) MaterialTheme.colorScheme.primary
                                                else Color.White.copy(alpha = 0.1f)
                                            )
                                            .clickable {
                                                speed = s
                                                player.setPlaybackSpeed(s)
                                                showSpeed = false
                                            }
                                            .padding(horizontal = 14.dp, vertical = 9.dp)
                                    ) {
                                        Text(
                                            "${trimSpeed(s)}x",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpeedChip(speed: Float, onClick: () -> Unit, dark: Boolean = true) {
    val bg = if (dark) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (dark) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text("${trimSpeed(speed)}x", color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

private fun trimSpeed(s: Float): String = if (s % 1f == 0f) s.toInt().toString() else s.toString()

@Composable
fun PlayPauseButton(onClick: () -> Unit, size: Int = 46, dark: Boolean = true) {
    val player = PlayerHolder.player
    var playing by remember { mutableStateOf(player.isPlaying) }
    DisposableEffect(Unit) {
        val l = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    val bg = if (dark) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary
    val fg = if (dark) Color.White else MaterialTheme.colorScheme.onPrimary
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = if (playing) "暂停" else "播放",
            tint = fg,
            modifier = Modifier.size((size * 0.58f).dp),
        )
    }
}
