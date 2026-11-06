package com.adlen.sonora.app.data.preferences

/*
 * Download preferences: availability and quality.
 */

import android.content.Context

// The stored file keeps its first name: renaming it would reset everyone's settings.
private const val PREF = "LosslessPref"
private const val KEY_HI_RES = "hires_24bit"
private const val KEY_ENABLED = "enabled"
private const val KEY_SMALL = "small_aac"
private const val KEY_ASK = "ask_each_time"
private const val KEY_WIFI_ONLY = "wifi_only"
private const val KEY_MAX_BYTES = "max_download_bytes"
private const val KEY_KEEP_FREE = "keep_free_bytes"

/** Defaults: 8 GB of downloads, and 500 MB of free space left on the phone. */
private const val DEFAULT_MAX_BYTES = 8L * 1024 * 1024 * 1024
private const val DEFAULT_KEEP_FREE = 500L * 1024 * 1024

/**
 * Hi-res downloads (24-bit when the source has it: bigger files, ~3× a CD-quality FLAC).
 * Default off: CD quality is asked for.
 */
fun isHiRes(context: Context): Boolean = prefs(context).getBoolean(KEY_HI_RES, false)

fun setHiRes(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_HI_RES, on).apply()

/** Downloads on. Off: no downloads at all, "Downloaded" becomes Local Files only. */
fun isDownloadsEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

fun setDownloadsEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()

/** Downloads kept small: the checked FLAC re-encoded to AAC on the phone. */
fun isSmallDownloads(context: Context): Boolean = prefs(context).getBoolean(KEY_SMALL, false)

fun setSmallDownloads(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_SMALL, on).apply()

/** The quality is asked at each download (once for a whole list). */
fun isAskDownloadQuality(context: Context): Boolean = prefs(context).getBoolean(KEY_ASK, false)

fun setAskDownloadQuality(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_ASK, on).apply()

/** Even a download the user asked for waits for Wi-Fi. */
fun isWifiOnly(context: Context): Boolean = prefs(context).getBoolean(KEY_WIFI_ONLY, false)

fun setWifiOnly(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_WIFI_ONLY, on).apply()

/** 0 = no limit. */
fun maxDownloadBytes(context: Context): Long = prefs(context).getLong(KEY_MAX_BYTES, DEFAULT_MAX_BYTES)

fun setMaxDownloadBytes(context: Context, bytes: Long) =
    prefs(context).edit().putLong(KEY_MAX_BYTES, bytes.coerceAtLeast(0L)).apply()

/** Free space kept for the rest of the phone. */
fun keepFreeBytes(context: Context): Long = prefs(context).getLong(KEY_KEEP_FREE, DEFAULT_KEEP_FREE)

fun setKeepFreeBytes(context: Context, bytes: Long) =
    prefs(context).edit().putLong(KEY_KEEP_FREE, bytes.coerceAtLeast(0L)).apply()

private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
