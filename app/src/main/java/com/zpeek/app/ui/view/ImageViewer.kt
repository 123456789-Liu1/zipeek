package com.zpeek.app.ui.view

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.zpeek.app.R
import com.zpeek.app.core.ArchiveEngine
import com.zpeek.app.core.ArchiveEntry
import com.zpeek.app.core.FileKinds
import com.zpeek.app.ui.components.AppButton
import com.zpeek.app.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun ImageViewer(
    engine: ArchiveEngine,
    entries: List<ArchiveEntry>,
    currentPath: String,
    onBack: () -> Unit,
    onExtract: (ArchiveEntry) -> Unit,
) {
    val startIndex = remember(entries, currentPath) {
        entries.indexOfFirst { it.path == currentPath }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = startIndex) { entries.size }
    val cache = remember { mutableStateMapOf<String, Bitmap>() }
    var zoomed by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    val current = entries.getOrNull(pagerState.currentPage) ?: entries.first()

    // 预加载前后各一张
    LaunchedEffect(pagerState.currentPage) {
        val pages = listOf(
            pagerState.currentPage - 1,
            pagerState.currentPage + 1,
        )
        pages.filter { it in entries.indices }.forEach { i ->
            val e = entries[i]
            if (!cache.containsKey(e.path)) {
                val bmp = withContext(Dispatchers.IO) { ImageDecoder.decode(engine, e) }
                if (bmp != null) cache[e.path] = bmp
            }
        }
    }
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            kotlinx.coroutines.delay(3200)
            controlsVisible = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val entry = entries[page]
            val bitmap = cache[entry.path]
            if (bitmap != null) {
                ZoomableImage(
                    bitmap = bitmap,
                    onZoomChanged = { zoomed = it },
                )
            } else if (FileKinds.isAnimatedImage(entry.name) && ImageDecoder.supportsAnimation()) {
                AnimatedImage(engine = engine, entry = entry)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.7f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                }
                LaunchedEffect(entry.path) {
                    val bmp = withContext(Dispatchers.IO) { ImageDecoder.decode(engine, entry) }
                    if (bmp != null) cache[entry.path] = bmp
                }
            }
        }

        // 顶部信息
        if (controlsVisible) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .statusBarsPadding()
                    .padding(top = 6.dp, bottom = 12.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.ArrowBack, "返回", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            current.name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${pagerState.currentPage + 1}/${entries.size} · ${Format.size(current.size)}",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    AppButton(
                        text = "提取",
                        onClick = { onExtract(current) },
                        tint = Color.White.copy(alpha = 0.22f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(bitmap: Bitmap, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val ratio = remember(bitmap) {
        if (bitmap.width > 0) bitmap.width.toFloat() / bitmap.height.toFloat() else 1f
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 8f)
                    scale = newScale
                    offset = if (newScale <= 1.01f) Offset.Zero else offset + pan
                    onZoomChanged(newScale > 1.01f)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (abs(scale - 1f) < 0.01f) {
                            scale = 2.5f
                            onZoomChanged(true)
                        } else {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomChanged(false)
                        }
                    },
                    onTap = { },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

@Composable
private fun AnimatedImage(engine: ArchiveEngine, entry: ArchiveEntry) {
    val drawable = remember(entry.path) { ImageDecoder.animatedDrawable(engine, entry) }
    if (drawable == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("无法渲染该动图", color = Color.White.copy(alpha = 0.7f))
        }
        return
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            ImageView(ctx).apply {
                setImageDrawable(drawable as? Drawable ?: drawable)
                scaleType = ImageView.ScaleType.FIT_CENTER
                (drawable as? android.graphics.drawable.AnimatedImageDrawable)?.start()
            }
        },
    )
}
