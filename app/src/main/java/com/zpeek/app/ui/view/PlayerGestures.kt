package com.zpeek.app.ui.view

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

private const val AXIS_NONE = 0
private const val AXIS_H = 1
private const val AXIS_V = 2

/**
 * 播放器手势（全部基于稳定 API）：
 * - 左右拖动：快进 / 快退预览
 * - 左半屏上下：亮度；右半屏上下：音量
 * - 长按：加速播放（松手恢复）
 * - 单击：播放 / 暂停；左右双击：±10 秒
 */
fun Modifier.playerGestures(
    onSingleTap: () -> Unit = {},
    onDoubleTapLeft: () -> Unit = {},
    onDoubleTapRight: () -> Unit = {},
    onLongPressStart: () -> Unit = {},
    onLongPressEnd: () -> Unit = {},
    onSeekDrag: (totalDx: Float, fraction: Float) -> Unit = { _, _ -> },
    onSeekEnd: (fraction: Float) -> Unit = {},
    onVolumeBrightness: (deltaY: Float, xFraction: Float) -> Unit = { _, _ -> },
    onVolumeBrightnessEnd: () -> Unit = {},
): Modifier = this
    .pointerInput(Unit) {
        var axis = AXIS_NONE
        var dx = 0f
        detectDragGestures(
            onDragStart = {
                axis = AXIS_NONE
                dx = 0f
            },
            onDrag = { change, dragAmount ->
                dx += dragAmount.x
                val dy = dragAmount.y
                if (axis == AXIS_NONE && (abs(dx) > 8f || abs(dy) > 8f)) {
                    axis = if (abs(dx) >= abs(dy)) AXIS_H else AXIS_V
                }
                when (axis) {
                    AXIS_H -> onSeekDrag(dx, dx / size.width.coerceAtLeast(1))
                    AXIS_V -> onVolumeBrightness(-dy / size.height.coerceAtLeast(1), change.position.x / size.width.coerceAtLeast(1))
                }
                change.consume()
            },
            onDragEnd = {
                when (axis) {
                    AXIS_H -> onSeekEnd(dx / size.width.coerceAtLeast(1))
                    AXIS_V -> onVolumeBrightnessEnd()
                }
                axis = AXIS_NONE
                dx = 0f
            },
            onDragCancel = {
                axis = AXIS_NONE
                dx = 0f
                onVolumeBrightnessEnd()
            },
        )
    }
    .pointerInput(Unit) {
        var speedUp = false
        detectTapGestures(
            onDoubleTap = { pos ->
                if (pos.x > size.width * 0.6f) onDoubleTapRight() else onDoubleTapLeft()
            },
            onLongPress = {
                speedUp = true
                onLongPressStart()
            },
            onTap = { onSingleTap() },
            onPress = {
                val released = tryAwaitRelease()
                if (released && speedUp) {
                    speedUp = false
                    onLongPressEnd()
                }
            },
        )
    }
