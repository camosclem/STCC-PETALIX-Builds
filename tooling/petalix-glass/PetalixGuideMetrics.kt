// SPDX-License-Identifier: GPL-3.0-only
// IMD TV v0.5.2 GuideGrid.kt and Timeline.kt; channel IDs in Petalix are strings.
package com.nuvio.tv.livetv.ui

/** Whole-pixel row height for [count] rows plus their gaps in [areaPx]: rows that fill the list exactly scroll by whole
 *  rows (fractional ones left a sliver at the top and cut the last row off). */
internal fun guideRowPx(areaPx: Int, count: Int, gapPx: Int): Int = (areaPx - gapPx * (count - 1)) / count

internal enum class OkAction { PREVIEW, FULL_SCREEN }

/** Spec §4.1: without autoplay, OK previews a channel and a second OK on the previewing one goes full screen. */
internal fun okAction(immediateFullscreen: Boolean, previewingId: String?, channelId: String): OkAction =
    if (immediateFullscreen || previewingId == channelId) OkAction.FULL_SCREEN else OkAction.PREVIEW
