package com.nuvio.tv.petalix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Refresh visible remote art without clearing the disk cache or changing signed URLs. */
@Composable
internal fun rememberPetalixArtworkEpoch(url: String?): Int {
    if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) return 0
    fun currentEpoch() = (System.currentTimeMillis() / (6 * 60 * 60_000L)).toInt()
    val ticks = remember(url) {
        flow { while (true) { emit(currentEpoch()); delay(60_000) } }.distinctUntilChanged()
    }
    val epoch by ticks.collectAsStateWithLifecycle(initialValue = currentEpoch())
    return epoch
}
