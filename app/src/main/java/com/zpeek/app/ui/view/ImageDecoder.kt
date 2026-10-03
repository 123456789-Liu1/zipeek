package com.zpeek.app.ui.view

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.zpeek.app.core.ArchiveEngine
import com.zpeek.app.core.ArchiveEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * 大图快速加载：先只读文件头拿到尺寸，再按目标像素量选择采样率，
 * 避免一次性把 100MB 级照片解码进内存。
 */
object ImageDecoder {

    private fun maxPixels(): Int {
        val maxMem = (Runtime.getRuntime().maxMemory() / 1024 / 1024).toInt()
        return (maxMem / 8).coerceIn(2_000_000, 12_000_000)
    }

    suspend fun decode(engine: ArchiveEngine, entry: ArchiveEntry): Bitmap? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { engine.openStream(entry).use { BitmapFactory.decodeStream(it, null, bounds) } }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        var sample = 1
        val limit = maxPixels()
        while (max(1, bounds.outWidth / sample).toLong() * max(1, bounds.outHeight / sample) > limit) {
            sample *= 2
        }
        // 超宽/超高图额外限制最长边，保证缩放操作流畅
        while (max(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        runCatching { engine.openStream(entry).use { BitmapFactory.decodeStream(it, null, opts) } }.getOrNull()
    }

    /** API 28+ 支持动图播放 */
    fun supportsAnimation(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    fun animatedDrawable(engine: ArchiveEngine, entry: ArchiveEntry): android.graphics.drawable.Drawable? {
        if (!supportsAnimation()) return null
        return runCatching {
            engine.openStream(entry).use {
                android.graphics.drawable.AnimatedImageDrawable::class.java
                    .getMethod("createFromStream", java.io.InputStream::class.java)
                    .invoke(null, it) as android.graphics.drawable.Drawable
            }
        }.getOrNull()
    }
}
