package com.zpeek.app.player

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** 后台播放服务：提供通知栏控制与锁屏控制，播放由 [PlayerHolder] 中的实例执行 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        PlayerHolder.init(this)
        session = MediaSession.Builder(this, PlayerHolder.player)
            .setId("zipeek-playback")
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = PlayerHolder.player
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.isPlaying) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
