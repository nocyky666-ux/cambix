package com.shii.dev.cambix.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ProviderStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("cambix_ai", Context.MODE_PRIVATE)
    private val secrets = SecretStore(context)

    fun newId(): String = UUID.randomUUID().toString().take(8)

    fun list(): List<ProviderConfig> {
        val text = sp.getString("providers", null) ?: return emptyList()
        return try {
            val arr = JSONArray(text)
            (0 until arr.length()).map { ProviderConfig.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun get(id: String): ProviderConfig? = list().firstOrNull { it.id == id }

    fun save(p: ProviderConfig) {
        val all = list().filter { it.id != p.id } + p
        sp.edit().putString("providers", ProviderConfig.listToJson(all)).apply()
        if (defaultId == null) defaultId = p.id
    }

    fun delete(id: String) {
        sp.edit().putString("providers", ProviderConfig.listToJson(list().filter { it.id != id })).apply()
        secrets.remove(keyName(id))
        if (defaultId == id) defaultId = list().firstOrNull()?.id
    }

    var defaultId: String?
        get() = sp.getString("default", null)
        set(value) {
            sp.edit().putString("default", value).apply()
        }

    fun default(): ProviderConfig? {
        val all = list()
        return all.firstOrNull { it.id == defaultId } ?: all.firstOrNull()
    }

    fun apiKey(id: String): String? = secrets.get(keyName(id))

    fun hasKey(id: String): Boolean = secrets.has(keyName(id))

    fun setApiKey(id: String, key: String?) {
        if (key.isNullOrBlank()) secrets.remove(keyName(id)) else secrets.put(keyName(id), key.trim())
    }

    /** Settings only. Keys are deliberately left out, so a shared file never leaks them. */
    fun exportJson(): String = JSONObject().put("cambix_ai_providers", JSONArray(ProviderConfig.listToJson(list()))).toString(2)

    /** Adds every provider in the file under a fresh id and returns how many were added. */
    fun importJson(text: String): Int {
        val arr = JSONObject(text).getJSONArray("cambix_ai_providers")
        var added = 0
        for (i in 0 until arr.length()) {
            save(ProviderConfig.fromJson(arr.getJSONObject(i), forceId = newId()))
            added++
        }
        return added
    }

    private fun keyName(id: String) = "key_$id"
}
