// Sonora (https://github.com/cherifad/sonora), a fork of Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
// Reuse requires credit to Myspoti and Sonora, these notices kept, and your full source published under GPL-3.0.
package com.adlen.sonora

import android.content.Context

/**
 * Sonora was Myspoti: a device that already ran the old build keeps its settings instead of
 * starting from scratch after the package rename. Runs once, before anything reads the prefs.
 */
internal object PrefsMigration {
    private const val OLD = "myspoti_prefs"
    private const val NEW = "sonora_prefs"

    fun run(context: Context) {
        val old = context.getSharedPreferences(OLD, Context.MODE_PRIVATE)
        val new = context.getSharedPreferences(NEW, Context.MODE_PRIVATE)
        if (new.all.isNotEmpty()) return
        val entries = old.all
        if (entries.isEmpty()) return
        new.edit().apply {
            entries.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(key, value as Set<String>)
                }
            }
        }.apply()
    }
}
