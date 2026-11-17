package com.nuvio.tv.petalix

/** TV-safe sizing and preview rules shared by the redesigned Home screen. */
internal object PetalixCinemaPolicy {
    fun landscapeWidthDp(screenWidthDp: Int): Float =
        ((screenWidthDp - 104).coerceAtLeast(1) / 3.25f).coerceIn(224f, 320f)

    fun canPreview(enabled: Boolean, resumed: Boolean, sidebarOpen: Boolean,
                   scrolling: Boolean, rapidNavigation: Boolean): Boolean =
        enabled && resumed && !sidebarOpen && !scrolling && !rapidNavigation

    fun actionLabel(service: Boolean, liveGroup: Boolean, liveChannel: Boolean,
                    resume: Boolean, series: Boolean): String = when {
        liveChannel -> "Watch channel"
        liveGroup -> "Open guide"
        service -> "Explore collection"
        resume -> "Resume watching"
        series -> "Episodes & details"
        else -> "Details & play"
    }
}
