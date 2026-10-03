package com.zpeek.app.data

import android.content.Context
import android.net.Uri
import com.zpeek.app.core.ArchiveFormat
import org.json.JSONArray
import org.json.JSONObject

data class RecentItem(
    val uri: String,
    val name: String,
    val size: Long,
    val format: ArchiveFormat,
    val time: Long,
)

/** 最近打开记录（最多 12 条），持久化在 SharedPreferences */
class Recents(context: Context) {

    private val prefs = context.getSharedPreferences("zipeek_recents", Context.MODE_PRIVATE)

    fun load(): List<RecentItem> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                RecentItem(
                    uri = o.optString("uri"),
                    name = o.optString("name"),
                    size = o.optLong("size"),
                    format = runCatching { ArchiveFormat.valueOf(o.optString("format")) }
                        .getOrDefault(ArchiveFormat.UNKNOWN),
                    time = o.optLong("time"),
                )
            }.filter { it.uri.isNotEmpty() }
        }.getOrDefault(emptyList())
    }

    fun touch(item: RecentItem): List<RecentItem> {
        val list = ArrayList(load())
        list.removeAll { it.uri == item.uri }
        list.add(0, item)
        while (list.size > MAX) list.removeAt(list.size - 1)
        save(list)
        return list
    }

    fun remove(uri: String): List<RecentItem> {
        val list = ArrayList(load())
        list.removeAll { it.uri == uri }
        save(list)
        return list
    }

    fun clear(): List<RecentItem> {
        save(emptyList())
        return emptyList()
    }

    private fun save(list: List<RecentItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().apply {
                    put("uri", it.uri)
                    put("name", it.name)
                    put("size", it.size)
                    put("format", it.format.name)
                    put("time", it.time)
                }
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun hasPermission(context: Context, uri: String): Boolean = runCatching {
        val p = context.contentResolver.persistedUriPermissions
        p.any { it.uri.toString() == uri && it.isReadPermission }
    }.getOrDefault(false)

    companion object {
        private const val KEY = "items"
        private const val MAX = 12
    }
}
