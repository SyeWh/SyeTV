package com.sye.tv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object LinkStorage {

    private const val PREFS = "sye_tv"
    private const val KEY  = "links"

    fun save(context: Context, items: List<MediaItem>) {
        val arr = JSONArray()
        for (item in items) {
            arr.put(JSONObject().apply {
                put("id",    item.id)
                put("title", item.title)
                put("url",   item.url)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun load(context: Context): MutableList<MediaItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<MediaItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(MediaItem(
                id    = o.optString("id",    System.currentTimeMillis().toString()),
                title = o.getString("title"),
                url   = o.getString("url")
            ))
        }
        return list
    }
}
