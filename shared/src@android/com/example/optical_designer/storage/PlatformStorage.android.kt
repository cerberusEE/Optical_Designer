package com.example.optical_designer.storage

import android.content.Context
import android.content.SharedPreferences

actual object PlatformStorage {
    private var prefs: SharedPreferences? = null
    private val memoryStore = mutableMapOf<String, String>()

    actual fun init(context: Any?) {
        if (context is Context) {
            prefs = context.applicationContext.getSharedPreferences("optical_designer_storage", Context.MODE_PRIVATE)
            memoryStore.forEach { (k, v) ->
                prefs?.edit()?.putString(k, v)?.apply()
            }
            memoryStore.clear()
        }
    }

    actual fun saveString(key: String, value: String) {
        val p = prefs
        if (p != null) {
            p.edit().putString(key, value).apply()
        } else {
            memoryStore[key] = value
        }
    }

    actual fun getString(key: String): String? {
        val p = prefs
        if (p != null) {
            return p.getString(key, null)
        }
        return memoryStore[key]
    }

    actual fun remove(key: String) {
        val p = prefs
        if (p != null) {
            p.edit().remove(key).apply()
        } else {
            memoryStore.remove(key)
        }
    }
}
