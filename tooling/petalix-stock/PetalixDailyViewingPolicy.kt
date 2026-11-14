package com.nuvio.tv.petalix

internal fun petalixStartupChoice(page: String, autoPlayLast: Boolean): String =
    if (page == "LIVE_TV" && autoPlayLast) "LAST_CHANNEL" else page

internal fun petalixGuideResumeRow(
    channelKeys: List<String>, playingKey: String?, lastWatchedKey: String?,
    rememberLastGroup: Boolean, previousRow: Int
): Int {
    val playing = channelKeys.indexOf(playingKey)
    if (playing >= 0) return playing
    val remembered = if (rememberLastGroup) channelKeys.indexOf(lastWatchedKey) else -1
    return if (remembered >= 0) remembered
        else previousRow.coerceIn(0, (channelKeys.size - 1).coerceAtLeast(0))
}

internal fun petalixPlaybackNeedsRecovery(
    hasError: Boolean, autoReconnect: Boolean, reconnectAttempt: Int
): Boolean = hasError && (!autoReconnect || reconnectAttempt > 8)
