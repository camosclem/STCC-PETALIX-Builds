"""Restore playback changes from the last signed Petalix owner build."""
from home_visual_owner_patch import replace


def apply(root):
    source = root / 'app/src/main/java/com/nuvio/tv'
    streams = source / 'data/repository/StreamRepositoryImpl.kt'
    replace(streams, '                        addonRepository.fetchAddon(penguUrl)',
            '                        retryPengu503 { addonRepository.fetchAddon(penguUrl) }')
    replace(streams, '                            val streamsResult = getStreamsFromAddon(addon, type, sourceVideoId)', '''                            val streamsResult = if (isPengu) {
                                retryPengu503 { getStreamsFromAddon(addon, type, sourceVideoId) }
                            } else {
                                getStreamsFromAddon(addon, type, sourceVideoId)
                            }''')
    replace(streams, '    override suspend fun getStreamsFromAddon(', '''    private suspend fun <T> retryPengu503(
        request: suspend () -> NetworkResult<T>
    ): NetworkResult<T> {
        var result = request()
        for (delayMillis in listOf(750L, 1_500L)) {
            if ((result as? NetworkResult.Error)?.code != 503) return result
            kotlinx.coroutines.delay(delayMillis)
            result = request()
        }
        return result
    }

    override suspend fun getStreamsFromAddon(''')

    vod = source / 'livetv/ondemand'
    replace(vod / 'OnDemandRepository.kt', '    /** Imports new providers and drops ones that were turned off (no re-download of the rest). */', '''    private var catalogLoadJob: kotlinx.coroutines.Job? = null

    @Synchronized
    fun loadCatalogOnDemand() {
        start()
        if (_hasContent.value || _status.value.loading || catalogLoadJob?.isActive == true) return
        _status.value = OnDemandStatus(loading = true, message = "Loading movies and series…")
        catalogLoadJob = scope.launch {
            try {
                if (runCatching { db.hasAny() }.getOrDefault(false)) {
                    _hasContent.value = true
                } else {
                    refresh(force = false)
                }
            } finally {
                _status.value = OnDemandStatus()
            }
        }
    }

    /** Imports new providers and drops ones that were turned off (no re-download of the rest). */''')
    replace(vod / 'OnDemandStreams.kt', '        repo.start()', '        repo.loadCatalogOnDemand()')
    replace(vod / 'OnDemandViewModel.kt', '''    /** Builds the page: every group in your order, hidden and locked ones left out. */
    fun loadHome() {
        viewModelScope.launch {''', '''    private var homeLoadJob: kotlinx.coroutines.Job? = null

    /** Builds the page: every group in your order, hidden and locked ones left out. */
    fun loadHome() {
        repository.loadCatalogOnDemand()
        homeLoadJob?.cancel()
        homeEnrichJob?.cancel()
        homeLoadJob = viewModelScope.launch {
            if (!repository.hasContent.value && repository.status.value.loading) {
                kotlinx.coroutines.flow.combine(repository.hasContent, repository.status) { hasContent, status ->
                    hasContent || !status.loading
                }.first { it }
            }''')

    home = source / 'ui/screens/home'
    replace(home / 'ModernHomeContent.kt', '''                        activeRowFallbackBackdrop
                    )''', '''                        activeRowFallbackBackdrop,
                        "file:///android_asset/petalix/home-art/home.webp"
                    )''')
    replace(home / 'ModernHomeRowsList.kt', '''                if (row.key == com.nuvio.tv.petalix.PETALIX_SERVICES_ROW) {
                    com.nuvio.tv.petalix.PetalixHomeCategories()
                }''', '''                if (row.key == com.nuvio.tv.petalix.PETALIX_SERVICES_ROW) {
                    com.nuvio.tv.livetv.home.LiveTvHomeRow(above = true, startPadding = 52.dp, persistent = true)
                    com.nuvio.tv.petalix.PetalixHomeCategories()
                }''')
    replace(source / 'petalix/PetalixHomeServices.kt', '        if (services != null) {', '''        com.nuvio.tv.livetv.home.LiveTvHomeRow(above = true, startPadding = 52.dp, persistent = true)
        PetalixHomeCategories()
        if (services != null) {''')
    replace(source / 'livetv/model/LiveTvModels.kt', '    val homeRowSource: String = "favorites",',
            '    val homeRowSource: String = "recent",')
    replace(source / 'livetv/model/LiveTvModels.kt', '    val homeRowTitle: String = "",',
            '    val homeRowTitle: String = "Recently Watched Channels",')

    rows = home / 'ModernHomeRows.kt'
    replace(rows, '    val imageUrl = petalixArtwork ?: petalixLogoUri ?: baseImageUrl', '''    val imageUrl = petalixArtwork ?: petalixLogoUri ?: baseImageUrl
    val serviceTagline = payload?.folderId?.let { com.nuvio.tv.petalix.petalixServiceTagline(it) }''')
    replace(rows, 'Modifier.padding(16.dp) else Modifier', 'Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 26.dp) else Modifier')
    replace(rows, '''                    if (shouldPlayTrailerInCard) {''', '''                    if (serviceTagline != null) {
                        Text(
                            text = serviceTagline,
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 1,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 6.dp, vertical = 8.dp)
                        )
                    }

                    if (shouldPlayTrailerInCard) {''')
    replace(source / 'petalix/PetalixHomeCategories.kt',
            'private fun homeArtworkUri(name: String)', '''internal fun petalixServiceTagline(folderId: String): String? = when (folderId) {
    "folder-OA4T8NYO" -> "HBO Originals, Movies & More"
    "folder-IQTF27K8" -> "Award-Winning Apple Originals"
    "folder-9YQK1ZF1" -> "TV, Movies & Live Sports"
    "folder-ILJM8PD0" -> "Hit Shows, Movies & Originals"
    "folder-petalix-britbox" -> "The Best of British TV"
    else -> null
}

private fun homeArtworkUri(name: String)''')
    replace(source / 'MainActivity.kt', '''    try {
        navController.navigate(targetRoute) {''', '''    if (targetRoute == Screen.Home.route && navController.popBackStack(targetRoute, inclusive = false)) {
        return
    }
    try {
        navController.navigate(targetRoute) {''')

    live_row = source / 'livetv/home/LiveTvHomeRow.kt'
    replace(live_row, 'titleStyle: androidx.compose.ui.text.TextStyle? = null\n) {',
            'titleStyle: androidx.compose.ui.text.TextStyle? = null,\n    persistent: Boolean = false\n) {')
    replace(live_row, 'hideUnlessFocused = hideUnlessFocused && above)',
            'hideUnlessFocused = hideUnlessFocused && above, persistent = persistent)')
    replace(live_row, '    hideUnlessFocused: Boolean,\n    viewModel:',
            '    hideUnlessFocused: Boolean,\n    persistent: Boolean,\n    viewModel:')
    replace(live_row, '        visible = open,', '        visible = persistent || open,')
    replace(live_row, '                    !bannerShown &&\n',
            '                    !persistent && !bannerShown &&\n')

    replace(home / 'ModernHomeRowsList.kt', 'val liveTvHere = liveTvRowAbove != null &&',
            '''val liveTvHere = liveTvRowAbove != null &&
                    carouselRows.list.none { it.key == com.nuvio.tv.petalix.PETALIX_SERVICES_ROW } &&''')
    activity = source / 'MainActivity.kt'
    replace(activity, '    val openSidebarWidth = sidebarTokens.expandedWidth', '    val openSidebarWidth = 92.dp')
    replace(activity, '    val sidebarLabelAlpha = 1f', '    val sidebarLabelAlpha = 0f')
    replace(activity, 'iconOnly = isFloatingPillIconOnly && !keepFloatingPillExpanded,', 'iconOnly = true,')
    replace(activity, '    val openDrawerWidth = sidebarTokens.legacyExpandedWidth', '    val openDrawerWidth = 72.dp')
    replace(activity, '    val openDrawerItemWidth = sidebarTokens.itemWidth', '    val openDrawerItemWidth = 54.dp')
    replace(activity, '                                    expanded = isExpanded,', '                                    expanded = false,')
