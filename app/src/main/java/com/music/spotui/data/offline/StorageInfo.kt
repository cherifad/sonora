package com.music.spotui.data.offline

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What the app keeps on the phone, for the storage screen: downloads (private, encrypted),
 * the disposable cache, and the free space left.
 */
object StorageInfo {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun downloadsCount(): Int = OfflineStore.tracks.size

    fun downloadsBytes(): Long = OfflineStore.totalBytes()

    /** Glide's images, shared cards, the video cache, a downloaded update: all safe to drop. */
    fun cacheBytes(context: Context): Long = size(context.cacheDir)

    fun freeBytes(context: Context): Long = runCatching { context.filesDir.usableSpace }.getOrDefault(0L)

    /** Empties the cache (never the downloads), then calls [onDone] on the main thread with what is left. */
    fun clearCache(context: Context, onDone: (Long) -> Unit = {}) {
        val app = context.applicationContext
        runCatching { com.bumptech.glide.Glide.get(app).clearMemory() }
        scope.launch {
            runCatching { com.bumptech.glide.Glide.get(app).clearDiskCache() }
            app.cacheDir.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
            withContext(Dispatchers.Main) { onDone(size(app.cacheDir)) }
        }
    }

    private fun size(dir: File): Long =
        runCatching { dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)
}
