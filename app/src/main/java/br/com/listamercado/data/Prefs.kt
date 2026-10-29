package br.com.listamercado.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dados locais do aparelho. Os mercados ficam guardados aqui também para que
 * os avisos possam ser recadastrados depois de reiniciar o celular, sem internet.
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("lista_mercado", Context.MODE_PRIVATE)

    var groupId: String?
        get() = sp.getString(KEY_GROUP, null)
        set(value) = sp.edit { putString(KEY_GROUP, value) }

    var userName: String?
        get() = sp.getString(KEY_NAME, null)
        set(value) = sp.edit { putString(KEY_NAME, value) }

    fun cachedStores(): List<Store> {
        val raw = sp.getString(KEY_STORES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Store(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    lat = o.getDouble("lat"),
                    lng = o.getDouble("lng"),
                    radius = o.getInt("radius"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun cacheStores(stores: List<Store>) {
        val arr = JSONArray()
        stores.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("lat", s.lat)
                    .put("lng", s.lng)
                    .put("radius", s.radius),
            )
        }
        sp.edit { putString(KEY_STORES, arr.toString()) }
    }

    fun lastNotifiedAt(storeId: String): Long = sp.getLong(KEY_NOTIFIED + storeId, 0L)

    fun markNotified(storeId: String, at: Long) = sp.edit { putLong(KEY_NOTIFIED + storeId, at) }

    private companion object {
        const val KEY_GROUP = "group_id"
        const val KEY_NAME = "user_name"
        const val KEY_STORES = "stores_cache"
        const val KEY_NOTIFIED = "notified_"
    }
}
