package com.linkdeck.android.widget

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class WidgetQuickLink(
    val id: String,
    val title: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis()
)

class WidgetQuickLinksStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getQuickLinks(): List<WidgetQuickLink> {
        val jsonStr = prefs.getString(KEY_QUICK_LINKS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<WidgetQuickLink>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    WidgetQuickLink(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", ""),
                        url = obj.getString("url"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addQuickLink(url: String, customTitle: String? = null): Boolean {
        val links = getQuickLinks().toMutableList()
        if (links.size >= MAX_LINKS) return false
        val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
        val uri = Uri.parse(cleanUrl)
        val title = if (!customTitle.isNullOrBlank()) {
            customTitle.trim()
        } else {
            uri.host ?: cleanUrl
        }
        val newLink = WidgetQuickLink(
            id = UUID.randomUUID().toString(),
            title = title,
            url = cleanUrl
        )
        links.add(0, newLink)
        saveLinks(links)
        return true
    }

    fun removeQuickLink(id: String) {
        val links = getQuickLinks().filter { it.id != id }
        saveLinks(links)
    }

    fun clearQuickLinks() {
        prefs.edit().remove(KEY_QUICK_LINKS).apply()
    }

    private fun saveLinks(links: List<WidgetQuickLink>) {
        val jsonArray = JSONArray()
        for (link in links) {
            val obj = JSONObject().apply {
                put("id", link.id)
                put("title", link.title)
                put("url", link.url)
                put("createdAt", link.createdAt)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_QUICK_LINKS, jsonArray.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "linkdeck_widget_quick_links"
        private const val KEY_QUICK_LINKS = "saved_widget_quick_links"
        const val MAX_LINKS = 20
    }
}
