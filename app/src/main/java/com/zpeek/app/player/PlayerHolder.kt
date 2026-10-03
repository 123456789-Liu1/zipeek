package com.zpeek.app.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.zpeek.app.core.ArchiveEngine
import com.zpeek.app.core.ArchiveEntry
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 全局唯一播放器：UI 与后台播放服务共用同一个实例，
 * 配合 [PlaybackService] 实现息屏 / 切后台继续播放与通知栏控制。
 */
object PlayerHolder {

    private const val CACHE_DIR = "media_cache"
    private const val CACHE_BYTES = 384L * 1024 * 1024

    lateinit var player: ExoPlayer
        private set

    private val registry = ConcurrentHashMap<String, EntryStreamProvider>()
    private var token = 0L

    @Volatile
    var currentEntry: ArchiveEntry? = null
        private set

    fun init(context: Context) {
        if (::player.isInitialized) return
        val app = context.applicationContext
        val factory: DataSource.Factory = runCatching {
            val cache = SimpleCache(File(app.cacheDir, CACHE_DIR), LeastRecentlyUsedCacheEvictor(CACHE_BYTES))
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(ArchiveEntryDataSource.FACTORY)
                .setCacheWriteDataSinkFactory(
                    CacheDataSink.Factory().setCache(cache).setFragmentSize(20L * 1024 * 1024)
                )
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }.getOrDefault(ArchiveEntryDataSource.FACTORY)

        player = ExoPlayer.Builder(app)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    fun resolve(uri: Uri): EntryStreamProvider? = registry[uri.toString()]

    /** 以列表形式播放（音频可连续播放同目录下的所有曲目） */
    fun playList(
        engine: ArchiveEngine,
        entries: List<ArchiveEntry>,
        startIndex: Int,
        startPosition: Long = 0L,
        playWhenReady: Boolean = true,
    ) {
        if (entries.isEmpty()) return
        registry.clear()
        token++
        val session = token
        val items = entries.mapIndexed { i, e ->
            val uri = Uri.parse("zipeek://m/$session/$i")
            registry[uri.toString()] = EntryStreamProvider(engine, e)
            MediaItem.Builder()
                .setUri(uri)
                .setMediaId(e.path)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(e.name).build())
                .build()
        }
        currentEntry = entries.getOrNull(startIndex)
        player.setMediaItems(items, startIndex.coerceIn(0, items.size - 1), startPosition)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    fun playEntry(
        engine: ArchiveEngine,
        entry: ArchiveEntry,
        startPosition: Long = 0L,
        playWhenReady: Boolean = true,
    ) = playList(engine, listOf(entry), 0, startPosition, playWhenReady)

    fun stop() {
        if (::player.isInitialized) {
            player.stop()
            player.clearMediaItems()
        }
        registry.clear()
        currentEntry = null
    }

    fun release() {
        if (::player.isInitialized) player.release()
        registry.clear()
    }
}
