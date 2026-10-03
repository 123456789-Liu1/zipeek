package com.zpeek.app.ui.view

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize

/** 监听视频分辨率，供外层按原始比例布局 */
@Composable
fun rememberVideoSize(player: Player): State<VideoSize> {
    val state = remember { mutableStateOf(VideoSize.UNKNOWN) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                state.value = size
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return state
}

/**
 * 使用 TextureView 承载视频画面：不引入 media3-ui 控件，
 * 既减少安装包体积，也便于在 Compose 中做缩放与手势处理。
 */
@Composable
fun VideoSurface(player: Player, modifier: Modifier = Modifier) {
    val sizeState = rememberVideoSize(player)
    val current = sizeState.value

    Box(
        modifier
            .fillMaxSize()
            .clip(RectangleShape)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).also { tv ->
                    tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) {
                            player.setVideoTextureView(tv)
                        }

                        override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) = Unit

                        override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean {
                            player.clearVideoTextureView(tv)
                            return true
                        }

                        override fun onSurfaceTextureUpdated(s: SurfaceTexture) = Unit
                    }
                }
            },
        )
    }
}
