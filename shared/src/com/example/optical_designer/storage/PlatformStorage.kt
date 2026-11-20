package com.example.optical_designer.storage

expect object PlatformStorage {
    fun init(context: Any? = null)
    fun saveString(key: String, value: String)
    fun getString(key: String): String?
    fun remove(key: String)
}
