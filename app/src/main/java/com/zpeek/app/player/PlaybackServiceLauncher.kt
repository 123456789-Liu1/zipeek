package com.zpeek.app.player

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * 播放服务启动策略。
 *
 * 不能直接用 startForegroundService：若音频很短、播放很快结束，
 * MediaSessionService 来不及前台化，系统会在 5 秒后抛出
 * ForegroundServiceDidNotStartInTimeException 导致应用崩溃。
 * 因此这里优先使用普通 startService，由 MediaSessionService 在真正开始播放时自行前台化。
 */
object PlaybackServiceLauncher {

    fun ensureStarted(context: Context) {
        val intent = Intent(context, PlaybackService::class.java)
        val ok = runCatching { context.startService(intent) }.isSuccess
        if (!ok) {
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }
    }

    /** 离开播放页且当前没有播放时，顺带收起通知，避免留下空前台服务 */
    fun stopIfIdle(context: Context) {
        val player = PlayerHolder.player
        if (player.isPlaying || player.playWhenReady) return
        runCatching { context.stopService(Intent(context, PlaybackService::class.java)) }
    }
}
