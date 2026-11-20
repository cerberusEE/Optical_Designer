package com.example.optical_designer.storage

import platform.Foundation.NSUserDefaults

actual object PlatformStorage {
    actual fun init(context: Any?) {
        // No-op for iOS since NSUserDefaults standardUserDefaults does not require Context
    }

    actual fun saveString(key: String, value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = key)
    }

    actual fun getString(key: String): String? {
        return NSUserDefaults.standardUserDefaults.stringForKey(key)
    }

    actual fun remove(key: String) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(key)
    }
}
