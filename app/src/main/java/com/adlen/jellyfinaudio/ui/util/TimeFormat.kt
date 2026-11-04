package com.adlen.jellyfinaudio.ui.util

import java.util.Locale

fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return String.format(
        Locale.ROOT,
        "%d:%02d",
        totalSeconds / 60,
        totalSeconds % 60,
    )
}

fun formatSeconds(seconds: Int?): String {
    val totalSeconds = (seconds ?: 0).coerceAtLeast(0)
    return String.format(
        Locale.ROOT,
        "%d:%02d",
        totalSeconds / 60,
        totalSeconds % 60,
    )
}
