// SPDX-License-Identifier: GPL-3.0-only
// IMD TV v0.5.2 GuideGrid.kt and Timeline.kt; channel IDs in Petalix are strings.
package com.nuvio.tv.livetv.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.tv.livetv.model.LiveTvSettings

/** Keep logo-only rows compact, while retaining room when names or numbers are enabled.
 * The caller shares this width with the date ruler, program grid and current-time line. */
internal fun glassChannelColumnWidth(settings: LiveTvSettings, availableWidth: Dp): Dp {
    val available = availableWidth.coerceAtLeast(0.dp)
    if (!settings.showChannelLogos || settings.showChannelNames) {
        return (available * .21f).coerceIn(minOf(available, 94.dp), minOf(available, 245.dp))
    }
    val scale = guideScale(settings).coerceAtLeast(1f)
    val width = (104.dp + if (settings.showChannelNumbers) 38.dp else 0.dp) * scale
    return minOf(width, available)
}

/** Whole-pixel row height for [count] rows plus their gaps in [areaPx]: rows that fill the list exactly scroll by whole
 *  rows (fractional ones left a sliver at the top and cut the last row off). */
internal fun guideRowPx(areaPx: Int, count: Int, gapPx: Int): Int = (areaPx - gapPx * (count - 1)) / count

internal enum class OkAction { PREVIEW, FULL_SCREEN }

/** Spec §4.1: without autoplay, OK previews a channel and a second OK on the previewing one goes full screen. */
internal fun okAction(immediateFullscreen: Boolean, previewingId: String?, channelId: String): OkAction =
    if (immediateFullscreen || previewingId == channelId) OkAction.FULL_SCREEN else OkAction.PREVIEW
