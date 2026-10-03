package com.zpeek.app.ui.view

import android.content.Context
import android.media.audiofx.Equalizer

/** 系统均衡器封装：不可用时静默降级，不影响播放 */
class EqualizerController(private val context: Context) {

    private var eq: Equalizer? = null
    private var sessionId: Int = 0

    val available: Boolean get() = eq != null
    val bandCount: Int get() = eq?.numberOfBands?.toInt() ?: 0
    private val range: IntArray
        get() = eq?.let {
            val r = it.bandLevelRange
            intArrayOf((r[0] / 100).toInt(), (r[1] / 100).toInt())
        } ?: intArrayOf(-15, 15)

    val minLevel: Int get() = range[0]
    val maxLevel: Int get() = range[1]

    fun bandLevel(band: Int): Int = eq?.let {
        runCatching { (it.getBandLevel(band.toShort()) / 100).toInt() }.getOrDefault(0)
    } ?: 0

    fun attach(audioSessionId: Int): Boolean {
        if (audioSessionId <= 0) return false
        if (sessionId == audioSessionId && eq != null) return true
        release()
        return runCatching {
            val e = Equalizer(0, audioSessionId)
            e.enabled = true
            eq = e
            sessionId = audioSessionId
            true
        }.getOrDefault(false)
    }

    fun setBand(band: Int, level: Int) {
        val e = eq ?: return
        val millibel = (level * 100).coerceIn(
            e.bandLevelRange[0].toInt(), e.bandLevelRange[1].toInt()
        )
        runCatching { e.setBandLevel(band.toShort(), millibel.toShort()) }
    }

    fun applyPreset(preset: Preset) {
        val e = eq ?: return
        val n = e.numberOfBands.toInt()
        if (n <= 0) return
        val lo = e.bandLevelRange[0].toInt()
        val hi = e.bandLevelRange[1].toInt()
        for (b in 0 until n) {
            val t = if (n == 1) 0.5f else b.toFloat() / (n - 1)
            val gain = preset.gainAt(t)
            val value = (lo + (hi - lo) * ((gain + 1f) / 2f)).toInt()
            runCatching { e.setBandLevel(b.toShort(), value.toShort()) }
        }
    }

    fun release() {
        runCatching { eq?.release() }
        eq = null
        sessionId = 0
    }

    enum class Preset(val label: String, val gainAt: (Float) -> Float) {
        FLAT("标准", { 0f }),
        BASS("重低音", { t -> if (t < 0.4f) 0.85f - t else 0f }),
        VOCAL("人声", { t -> if (t in 0.3f..0.7f) 0.7f else -0.3f }),
        TREBLE("高音增强", { t -> if (t > 0.6f) (t - 0.6f) * 2f else 0f }),
        LIVE("现场", { t -> (kotlin.math.sin(t * Math.PI).toFloat()) * 0.6f }),
    }
}
