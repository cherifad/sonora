// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.subsonic

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * A bounded disk cache for streamed songs, so a replay, a seek back or the next song of a queue
 * does not go out to the network again. It lives under `cacheDir`, which the storage screen's
 * "clear cache" already empties.
 *
 * The cache key is the song's own URI, never the stream URL: the URL carries a fresh salt on
 * every build (see `SubsonicClient.endpoint`), so keying on it would miss every time.
 */
@OptIn(UnstableApi::class)
internal object StreamCache {
    /** Roughly four hours at 320 kbit/s; the least recently used songs make room. */
    private const val SIZE_BYTES = 256L * 1024 * 1024

    @Volatile
    private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache =
        cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(context.cacheDir, "streams"),
                LeastRecentlyUsedCacheEvictor(SIZE_BYTES),
                StandaloneDatabaseProvider(context),
            ).also { cache = it }
        }

    /**
     * The data source the player reads through. It never writes: the warm-up below is the only
     * writer, so a song being carried into the cache while the player reads it cannot leave two
     * writers fighting over the same span (which fails the second one).
     */
    fun playerSource(context: Context): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(get(context))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** The writer side, used by the warm-up only. */
    private fun writingSource(context: Context): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(get(context))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory()
                    .setCache(get(context))
                    .setFragmentSize(CacheDataSink.DEFAULT_FRAGMENT_SIZE),
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun isCached(context: Context, key: String): Boolean =
        runCatching { get(context).isCached(key, 0, 1) }.getOrDefault(false)

    /** A warm-up of [uri] into the cache under [key], run on a background thread. */
    fun prefetch(context: Context, key: String, uri: Uri): Prefetch = Prefetch(context, key, uri)

    /** One warm-up. [cancel] is safe from another thread; [run] blocks until done or cancelled. */
    class Prefetch internal constructor(context: Context, key: String, uri: Uri) {
        private val source: CacheDataSource = writingSource(context).createDataSource() as CacheDataSource
        private val spec: DataSpec = DataSpec.Builder().setUri(uri).setKey(key).build()
        private val written = AtomicLong()

        private val writer = CacheWriter(
            source,
            spec,
            ByteArray(CacheWriter.DEFAULT_BUFFER_SIZE_BYTES),
        ) { _, _, added -> written.addAndGet(added) }

        fun cancel() = writer.cancel()

        /** How many bytes were written, 0 when it was cancelled or the network failed. */
        fun run(): Long = try {
            writer.cache()
            written.get()
        } catch (e: Exception) {
            0
        } finally {
            runCatching { source.close() }
        }
    }
}

/**
 * Whether warming the next song is worth it. Pure, so it is tested off-device.
 */
internal object Precache {
    fun wanted(
        unmetered: Boolean,
        hasNext: Boolean,
        cached: Boolean,
        playing: Boolean,
        loading: Boolean,
    ): Boolean = unmetered && hasNext && !cached && playing && !loading
}
