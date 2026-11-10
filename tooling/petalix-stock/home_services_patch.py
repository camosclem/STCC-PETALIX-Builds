"""Keep service selection on native Home, including a usable offline/loading fallback.

Applies to the pinned Nuvio + IPTV 13.1 source after core Petalix branding/auth.
This does not add a Services destination or replace native catalog/Live TV rows.
"""
from pathlib import Path
import shutil

HERE = Path(__file__).resolve().parent


def once(path, old, new, count=1):
    text = path.read_text()
    found = text.count(old)
    if found != count:
        raise RuntimeError(f"Home services source anchor changed: {path.name}: expected {count}, found {found}: {old[:90]!r}")
    path.write_text(text.replace(old, new))


def apply(root):
    root = Path(root)
    source = root / "app/src/main/java/com/nuvio/tv"
    home = source / "ui/screens/home/HomeScreen.kt"
    modern = source / "ui/screens/home/ModernHomeContent.kt"
    nav = source / "ui/navigation/NuvioNavHost.kt"
    target = source / "petalix"
    target.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(HERE / "PetalixHomeServices.kt", target / "PetalixHomeServices.kt")

    # Keep folder navigation on Home; each folder opens its original service experience.
    once(home, "fun HomeScreen(\n", """fun HomeScreen(
    onPetalixLiveTv: () -> Unit = {},
    onPetalixSettings: () -> Unit = {},
""")
    once(nav, "            HomeScreen(\n", """            HomeScreen(
                onPetalixLiveTv = { navController.navigate(Screen.LiveTv.route) },
                onPetalixSettings = { navController.navigate(Screen.Settings.route) },
""")
    collection_anchor = "    val hasCollectionContent = uiState.homeRows.any { it is HomeRow.CollectionRow }\n"
    once(home, collection_anchor, collection_anchor + """    val hasPetalixServices = uiState.homeRows.any {
        it is HomeRow.CollectionRow &&
            it.collection.id == com.nuvio.tv.petalix.PETALIX_SERVICES_COLLECTION &&
            it.collection.folders.isNotEmpty()
    }
""")
    once(home, "    LaunchedEffect(Unit) {\n        // Safety timeout", """    // Local service folders are immediately usable; they do not depend on addon catalogs.
    LaunchedEffect(hasPetalixServices, modernPresentationReady) {
        if (hasPetalixServices && modernPresentationReady) homeStableGateReleased = true
    }

    LaunchedEffect(Unit) {
        // Safety timeout""")
    # Source errors must not obscure a collection already stored on the TV.
    once(home,
         "uiState.error == noAddonsError && uiState.catalogRows.isEmpty() ->",
         "uiState.error == noAddonsError && uiState.catalogRows.isEmpty() && !hasCollectionContent && !hasHeroContent ->",
         count=2)
    once(home,
         "uiState.error != null && uiState.catalogRows.isEmpty() ->",
         "uiState.error != null && uiState.catalogRows.isEmpty() && !hasCollectionContent && !hasHeroContent ->",
         count=2)
    once(home, "    val showStartupLoader = when {", """    val petalixFallback = homeStableGateReleased && (!hasAnyContent || !modernPresentationReady)
    val showStartupLoader = if (petalixFallback) false else when {""")
    once(home, "        when {\n            !uiState.layoutPreferencesReady -> {", """        when {
            petalixFallback -> com.nuvio.tv.petalix.PetalixHomeServicesFallback(
                onLiveTv = onPetalixLiveTv,
                onSettings = onPetalixSettings,
                onFolder = onNavigateToFolderDetailStable
            )
            !uiState.layoutPreferencesReady -> {""")

    once(modern,
         "    if (hasCollections && !hasCatalogs && uiState.installedAddonsCount > 0 && uiState.isLoading) {",
         """    val hasPetalixServices = uiState.homeRows.any {
        it is HomeRow.CollectionRow &&
            it.collection.id == com.nuvio.tv.petalix.PETALIX_SERVICES_COLLECTION &&
            it.collection.folders.isNotEmpty()
    }
    if (hasCollections && !hasPetalixServices && !hasCatalogs && uiState.installedAddonsCount > 0 && uiState.isLoading) {""")

    # Use native catalog rows and native playlist VOD, with a small Home selection.
    once(home, "    onPetalixLiveTv: () -> Unit = {},", """    onPetalixLiveTv: () -> Unit = {},
    onPetalixVod: () -> Unit = {},
    onPetalixVodDetail: (String, String, String?, String) -> Unit = { _, _, _, _ -> },
""".rstrip())
    once(nav, "                onPetalixLiveTv = { navController.navigate(Screen.LiveTv.route) },", """                onPetalixLiveTv = { navController.navigate(Screen.LiveTv.route) },
                onPetalixVod = { navController.navigate(Screen.OnDemand.route) },
                onPetalixVodDetail = { id, type, addon, uid ->
                    navController.navigate(Screen.Detail.createRoute(id, type, addon))
                    navController.currentBackStackEntry?.savedStateHandle?.set(
                        com.nuvio.tv.livetv.ondemand.OnDemandPlay.KEY, uid
                    )
                },""")
    once(home, "    val uiState by viewModel.uiState.collectAsStateWithLifecycle()", """    val sourceState by viewModel.uiState.collectAsStateWithLifecycle()
    val vodViewModel: com.nuvio.tv.livetv.ondemand.OnDemandViewModel = hiltViewModel()
    val vodState by vodViewModel.home.collectAsStateWithLifecycle()
    val penguRows = sourceState.catalogRows.filter { row ->
        listOf(row.addonId, row.addonName).any {
            it.lowercase().filter(Char::isLetterOrDigit).contains("penguplay")
        } && row.items.isNotEmpty()
    }
    val referenceShelves = remember {
        linkedMapOf(
            "Top Rated" to listOf("top rated", "highest rated"),
            "Trending" to listOf("trending"),
            "Upcoming" to listOf("upcoming", "coming soon"),
            "Popular" to listOf("popular", "most popular"),
            "Now Playing" to listOf("now playing", "in theaters", "in theatres")
        )
    }
    val managedPlaylistRows = vodState.rows.filter { it.catalogId.contains(":petalix-managed:") }
    val playlistRows = managedPlaylistRows.ifEmpty { vodState.rows }
    fun matchesShelf(name: String, labels: List<String>): Boolean {
        val words = name.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("").split(' ').filter { it.isNotBlank() }.joinToString(" ")
        return labels.any { label ->
            words == label || words in setOf("movies $label", "$label movies", "tv $label", "$label tv", "$label tv shows")
        }
    }
    val requestedPenguCatalogs = sourceState.homeRows.filterIsInstance<HomeRow.PlaceholderCatalog>()
        .filter { placeholder ->
            listOf(placeholder.addonId, placeholder.addonName).any {
                it.lowercase().filter(Char::isLetterOrDigit).contains("penguplay")
            } && referenceShelves.values.any { matchesShelf(placeholder.catalogName, it) }
        }.map { it.catalogKey }
    LaunchedEffect(requestedPenguCatalogs) {
        requestedPenguCatalogs.forEach(viewModel::requestLazyCatalogLoad)
    }
    val needsPlaylist = referenceShelves.values.any { labels -> penguRows.none { matchesShelf(it.catalogName, labels) } }
    val vodVersion by vodViewModel.catalogVersion.collectAsStateWithLifecycle()
    LaunchedEffect(needsPlaylist, vodVersion) {
        if (needsPlaylist) vodViewModel.loadHome()
    }
    val homeRows = remember(sourceState.homeRows, penguRows, playlistRows) {
        val seenTitles = mutableSetOf<Pair<String, String>>()
        val seenIds = mutableSetOf<Pair<String, String>>()
        val usedCatalogs = mutableSetOf<CatalogRow>()
        fun selectRow(row: CatalogRow, title: String): CatalogRow? {
            if (row in usedCatalogs) return null
            val items = row.items.asSequence().filter { item ->
                val kind = item.type.toApiString()
                val name = item.name.lowercase().filter(Char::isLetterOrDigit)
                val titleKey = kind to name
                val idKey = kind to (item.imdbId?.takeIf { it.isNotBlank() } ?: item.id)
                if (titleKey in seenTitles || idKey in seenIds) false
                else {
                    seenTitles.add(titleKey)
                    seenIds.add(idKey)
                    true
                }
            }.take(16).toList()
            if (items.isEmpty()) return null
            usedCatalogs.add(row)
            return row.copy(catalogName = title, items = items, hasMore = false, supportsSkip = false)
        }
        val matched = referenceShelves.mapValues { (title, labels) ->
            val candidates = penguRows.filter { matchesShelf(it.catalogName, labels) } +
                playlistRows.filter { matchesShelf(it.catalogName, labels) }
            candidates.firstNotNullOfOrNull { selectRow(it, title) }
        }
        val fallbackRows = (penguRows + playlistRows).iterator()
        val rows = matched.values.mapNotNull { matchedRow ->
            if (matchedRow != null) matchedRow else {
                var fallback: CatalogRow? = null
                while (fallback == null && fallbackRows.hasNext()) {
                    val candidate = fallbackRows.next()
                    fallback = selectRow(candidate, candidate.catalogName)
                }
                fallback
            }
        }
        sourceState.homeRows.filterIsInstance<HomeRow.CollectionRow>() + rows.map { HomeRow.Catalog(it) }
    }
    val uiState = sourceState.copy(
        homeRows = homeRows, catalogRows = homeRows.filterIsInstance<HomeRow.Catalog>().map { it.row }
    )
    val petalixVodScope = androidx.compose.runtime.rememberCoroutineScope()
    val openPetalixDetail: (String, String, String) -> Unit = { id, type, addon ->
        val item = if (addon == com.nuvio.tv.livetv.ondemand.VodHomeIds.ADDON_ID) vodViewModel.homeItem(id) else null
        if (item == null) onNavigateToDetail(id, type, addon)
        else petalixVodScope.launch {
            when (val target = vodViewModel.targetFor(item)) {
                is com.nuvio.tv.livetv.ondemand.OnDemandTarget.Details ->
                    onPetalixVodDetail(target.itemId, target.itemType, target.addonBaseUrl, target.uid)
                is com.nuvio.tv.livetv.ondemand.OnDemandTarget.Provider -> onPetalixVod()
            }
        }
    }""")
    once(home, "import androidx.compose.runtime.Composable", "import androidx.compose.runtime.Composable\nimport kotlinx.coroutines.launch\nimport com.nuvio.tv.domain.model.CatalogRow")
    once(home, "    val onNavigateToDetailStable = remember(onNavigateToDetail) { onNavigateToDetail }",
         "    val onNavigateToDetailStable = openPetalixDetail")
    vod = source / "livetv/ondemand/OnDemandViewModel.kt"
    once(vod, "    val status = repository.status", """    val status = repository.status
    val catalogVersion = repository.version""")
    modern_observation = "    val modernPresentation by viewModel.modernHomePresentation.collectAsStateWithLifecycle()"
    once(home, modern_observation + "\n    val initialCwResolved", """    val presentationContext = androidx.compose.ui.platform.LocalContext.current
    val presentationCache = remember { ModernCarouselRowBuildCache() }
    val presentationInput = ModernHomePresentationInput(
        homeRows = uiState.homeRows,
        catalogRows = uiState.catalogRows,
        continueWatchingItems = emptyList(),
        upcomingItems = emptyList(),
        useLandscapePosters = uiState.modernLandscapePostersEnabled,
        showCatalogTypeSuffix = false,
        showFullReleaseDate = uiState.showFullReleaseDate,
        showImdbRatings = uiState.homeImdbRatingsVisibility.showRatings,
        detailImdbRatingsVisibility = uiState.detailImdbRatingsVisibility,
        localeTag = com.nuvio.tv.LocaleCache.localeTag
    )
    val modernPresentation by androidx.compose.runtime.produceState(
        ModernHomePresentationState(), presentationInput, presentationContext
    ) {
        value = buildModernHomePresentation(presentationInput, presentationCache, presentationContext)
    }
    val initialCwResolved""")
    once(home, """                            HomeLayout.MODERN -> ModernHomeRoute(
                                viewModel = viewModel,
                                uiState = uiState,""", """                            HomeLayout.MODERN -> ModernHomeRoute(
                                viewModel = viewModel,
                                uiState = uiState,
                                modernPresentation = modernPresentation,""")
    once(home, """private fun ModernHomeRoute(
    viewModel: HomeViewModel,
    uiState: HomeUiState,""", """private fun ModernHomeRoute(
    viewModel: HomeViewModel,
    uiState: HomeUiState,
    modernPresentation: ModernHomePresentationState,""")
    once(home, modern_observation + "\n", "")

    # The eight recovered logos are available before the network or video renderer is ready.
    # Keep native artwork for other folders and put native GIF/video layers above the logo.
    modern_rows = source / "ui/screens/home/ModernHomeRows.kt"
    once(modern_rows, """    val imageUrl = when {
        payload == null -> baseImageUrl
        !payload.focusGifEnabled -> baseImageUrl
        else -> baseImageUrl
    }
""", """    val petalixLogoUri = payload?.folderId?.let { com.nuvio.tv.petalix.petalixServiceLogoUri(it) }
    val imageUrl = petalixLogoUri ?: baseImageUrl
""")
    once(modern_rows,
         "                    val isPlaceholderItem = item.imageUrl.isPlaceholder()",
         "                    val isPlaceholderItem = petalixLogoUri == null && item.imageUrl.isPlaceholder()")
    once(modern_rows, "                Box(modifier = mediaLayerModifier) {\n", """                Box(modifier = mediaLayerModifier) {
                    if (petalixLogoUri != null) com.nuvio.tv.petalix.PetalixServiceTileBackdrop(
                        folderId = payload!!.folderId,
                        focused = isFocused,
                        modifier = Modifier.matchParentSize()
                    )
""")
    once(modern_rows, """                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(),
                            placeholder = backgroundPainter,
""", """                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize().then(
                                if (petalixLogoUri != null) Modifier.padding(16.dp) else Modifier
                            ),
                            placeholder = if (petalixLogoUri != null) null else backgroundPainter,
""")
    once(modern_rows, """                            error = backgroundPainter,
                            fallback = backgroundPainter,
                            contentScale = imageContentScale,
""", """                            error = if (petalixLogoUri != null) null else backgroundPainter,
                            fallback = if (petalixLogoUri != null) null else backgroundPainter,
                            contentScale = if (petalixLogoUri != null) ContentScale.Fit else imageContentScale,
""")

    content = source / "ui/screens/home/ModernHomeContent.kt"
    once(content, "            val resolvedHeroState = remember(", '''            val petalixBackgroundItems = remember(carouselRows) {
                carouselRows.list.flatMap { it.items.list }.filter {
                    it.payload is ModernPayload.Catalog &&
                        (!it.heroPreview.backdrop.isNullOrBlank() || !it.heroPreview.poster.isNullOrBlank())
                }.distinctBy { it.key }.take(24)
            }
            val petalixBackgroundIndex = remember { mutableIntStateOf(0) }
            val petalixServiceFocused = activeRowKey.value == com.nuvio.tv.petalix.PETALIX_SERVICES_ROW
            LaunchedEffect(petalixServiceFocused, petalixBackgroundItems) {
                if (petalixServiceFocused && petalixBackgroundItems.isNotEmpty()) {
                    while (true) {
                        delay(8_000L)
                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                            petalixBackgroundIndex.intValue =
                                (petalixBackgroundIndex.intValue + 1) % petalixBackgroundItems.size
                        }
                    }
                }
            }

            val resolvedHeroState = remember(petalixServiceFocused, petalixBackgroundItems, petalixBackgroundIndex, ''')
    once(content, "                    val activeCarouselItem = activeCarouselItemState.value",
         '''                    val focusedItem = activeCarouselItemState.value
                    val activeCarouselItem = if (petalixServiceFocused) {
                        focusedItem?.takeIf {
                            !it.heroPreview.backdrop.isNullOrBlank() || !it.heroPreview.poster.isNullOrBlank()
                        } ?: petalixBackgroundItems.getOrNull(
                            petalixBackgroundIndex.intValue % petalixBackgroundItems.size.coerceAtLeast(1)
                        )
                    } else focusedItem''')

    # The recovered service media renderer stays transparent until its first real frame.
    once(modern_rows, "                    if (shouldPlayTrailerInCard) {", """                    if (payload != null) {
                        com.nuvio.tv.petalix.PetalixServiceMediaOverlay(
                            folderId = payload.folderId,
                            focused = isFocused && payload.focusGifEnabled,
                            modifier = Modifier.matchParentSize()
                        )
                    }

                    if (shouldPlayTrailerInCard) {""")
    for relative in ("ui/components/CollectionRowSection.kt", "ui/screens/home/GridHomeContent.kt"):
        path = source / relative
        once(path,
             "            val activeImageUrl = collectionFolderCardImageUrl(folder, isFocused)",
             """            val petalixLogoUri = com.nuvio.tv.petalix.petalixServiceLogoUri(folder.id)
            val activeImageUrl = petalixLogoUri ?: collectionFolderCardImageUrl(folder, isFocused)""")
        once(path, "            if (!activeImageUrl.isNullOrBlank()) {", """            if (petalixLogoUri != null) com.nuvio.tv.petalix.PetalixServiceTileBackdrop(
                folderId = folder.id,
                focused = isFocused,
                modifier = Modifier.matchParentSize()
            )
            if (!activeImageUrl.isNullOrBlank()) {""")
        if relative.endswith("CollectionRowSection.kt"):
            once(path, """                    contentDescription = if (folder.hideTitle) folder.title else null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape),
                    contentScale = ContentScale.FillBounds
""", """                    contentDescription = if (folder.hideTitle) folder.title else null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .then(if (petalixLogoUri != null) Modifier.padding(16.dp) else Modifier),
                    contentScale = if (petalixLogoUri != null) ContentScale.Fit else ContentScale.FillBounds
""")
        else:
            once(path, """                    model = activeImageUrl,
                    contentDescription = folder.title,
                    modifier = Modifier.fillMaxSize().clip(cardShape),
                    contentScale = ContentScale.FillBounds
""", """                    model = activeImageUrl,
                    contentDescription = folder.title,
                    modifier = Modifier.fillMaxSize().clip(cardShape)
                        .then(if (petalixLogoUri != null) Modifier.padding(16.dp) else Modifier),
                    contentScale = if (petalixLogoUri != null) ContentScale.Fit else ContentScale.FillBounds
""")
        once(path, "            if (!folder.hideTitle) {", """            com.nuvio.tv.petalix.PetalixServiceMediaOverlay(
                folderId = folder.id,
                focused = isFocused && folder.focusGifEnabled,
                modifier = Modifier.matchParentSize()
            )

            if (!folder.hideTitle) {""")

    # Fail at preparation time if an old generic Services screen remains wired into navigation.
    for path in (source / "MainActivity.kt", nav, home):
        text = path.read_text()
        for forbidden in ("PETALIX_SERVICES_ROUTE", "PetalixServicesScreen", "onPetalixServices", "PetalixEmptyHome"):
            if forbidden in text:
                raise RuntimeError(f"Separate generic Services navigation remains in {path.name}: {forbidden}")
    if nav.read_text().count("onPetalixLiveTv =") != 1 or nav.read_text().count("onPetalixSettings =") != 1:
        raise RuntimeError("Native Home navigation callbacks are incomplete")
    for path in (modern_rows, source / "ui/components/CollectionRowSection.kt", source / "ui/screens/home/GridHomeContent.kt"):
        if path.read_text().count("PetalixServiceMediaOverlay(") != 1:
            raise RuntimeError(f"Service tile media binding is incomplete in {path.name}")
        if path.read_text().count("petalixServiceLogoUri(") != 1:
            raise RuntimeError(f"Service tile offline logo binding is incomplete in {path.name}")
    return {"homeServices": "native-horizontal-row", "separateServicesNavigation": False, "nativeRowsPreserved": True, "serviceMediaBindings": 3, "serviceLogoBindings": 3}


if __name__ == "__main__":
    import argparse
    import json
    parser = argparse.ArgumentParser()
    parser.add_argument("upstream", type=Path)
    args = parser.parse_args()
    print(json.dumps(apply(args.upstream)))
