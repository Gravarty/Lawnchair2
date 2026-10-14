package app.lawnchair.jb

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.UserHandle
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class AppInfo(
    val component: ComponentName,
    val user: UserHandle,
    val label: String,
    val icon: Drawable,
)

class ItemInfo(
    val type: Int,
    var container: Int,
    var page: Int,
    var cellX: Int,
    var cellY: Int,
    var spanX: Int = 1,
    var spanY: Int = 1,
    val component: ComponentName? = null,
    val widgetId: Int = -1,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        put("container", container)
        put("page", page)
        put("x", cellX)
        put("y", cellY)
        put("sx", spanX)
        put("sy", spanY)
        component?.let { put("component", it.flattenToString()) }
        put("widgetId", widgetId)
    }

    companion object {
        const val TYPE_APP = 0
        const val TYPE_WIDGET = 1
        const val CONTAINER_DESKTOP = 0
        const val CONTAINER_HOTSEAT = 1

        fun fromJson(o: JSONObject) = ItemInfo(
            type = o.getInt("type"),
            container = o.getInt("container"),
            page = o.getInt("page"),
            cellX = o.getInt("x"),
            cellY = o.getInt("y"),
            spanX = o.optInt("sx", 1),
            spanY = o.optInt("sy", 1),
            component = o.optString("component").takeIf { it.isNotEmpty() }
                ?.let { ComponentName.unflattenFromString(it) },
            widgetId = o.optInt("widgetId", -1),
        )
    }
}

class ItemStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("workspace", Context.MODE_PRIVATE)

    /** Returns null on first run. */
    fun load(): MutableList<ItemInfo>? {
        val s = prefs.getString("items", null) ?: return null
        return try {
            val a = JSONArray(s)
            MutableList(a.length()) { ItemInfo.fromJson(a.getJSONObject(it)) }
        } catch (e: JSONException) {
            null
        }
    }

    fun save(items: List<ItemInfo>) {
        val a = JSONArray()
        items.forEach { a.put(it.toJson()) }
        prefs.edit().putString("items", a.toString()).apply()
    }
}
