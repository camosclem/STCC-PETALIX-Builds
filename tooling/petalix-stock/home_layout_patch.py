"""Small Home defaults with profile-scoped panel bindings and official BritBox assets."""
from pathlib import Path
from shutil import copy2
from home_visual_owner_patch import replace
HERE=Path(__file__).resolve().parent

def apply(root):
    source=root/'app/src/main/java/com/nuvio/tv'
    for name in ('PetalixHomeLayout.kt','PetalixHomePreferences.kt'):
        copy2(HERE/name,source/'petalix'/name)
    copy2(HERE/'PetalixHomeLayoutTest.kt',root/'app/src/test/java/com/nuvio/tv/petalix/PetalixHomeLayoutTest.kt')
    home=source/'ui/screens/home/HomeScreen.kt'
    replace(home,'    val liveArtwork = com.nuvio.tv.petalix.PetalixLiveArtwork.images.value', '''    val homeLayout by homeCatalogViewModel.homeLayout.collectAsStateWithLifecycle()
    LaunchedEffect(homeLayout) {
        homeCatalogViewModel.load()
        if (!homeLayout.showLiveTv) {
            com.nuvio.tv.petalix.PetalixLiveCategoryFocus.category.value = null
            com.nuvio.tv.petalix.PetalixLiveCategoryFocus.recent.value = null
        }
    }
    val liveArtwork = com.nuvio.tv.petalix.PetalixLiveArtwork.images.value''')
    replace(home,'remember(sourceState.homeRows, homeChartRows, liveArtwork)',
        'remember(sourceState.homeRows, homeChartRows, liveArtwork, homeLayout)')
    replace(home,'petalixRepairedHomeRows(sourceState.homeRows, homeChartRows)',
        'petalixRepairedHomeRows(sourceState.homeRows, homeChartRows, homeLayout)')
    fallback=source/'petalix/PetalixHomeServices.kt'
    replace(fallback,'    fun collections(): CollectionsDataStore','    fun collections(): CollectionsDataStore\n    fun homePreferences(): PetalixHomePreferences')
    replace(fallback,'    val context = LocalContext.current','    val homeLayout = petalixHomeLayout()\n    val context = LocalContext.current')
    replace(fallback,'    val services = collections?.firstOrNull',
        '    val services = collections?.takeIf { homeLayout.showServices }?.firstOrNull')
    replace(fallback,'        } else {\n            Text(',
        '        } else if (homeLayout.showServices) {\n            Text(')
    # This fallback still has the old category composable, so gate it as well as the real row.
    categories=source/'petalix/PetalixHomeCategories.kt'
    replace(categories,'    val actions = LocalLiveTvHomeActions.current',
        '    if (!petalixHomeLayout().showLiveTv) return\n    val actions = LocalLiveTvHomeActions.current')
    live=source/'livetv/home/LiveTvHomeRow.kt'
    replace(live,'fun rememberLiveTvHomeRowPosition(): Boolean? {',
        'fun rememberLiveTvHomeRowPosition(): Boolean? {\n    if (!com.nuvio.tv.petalix.petalixHomeLayout().showLiveTv) return null')
    replace(live,'fun LiveTvHomeHint(modifier: Modifier = Modifier) {',
        'fun LiveTvHomeHint(modifier: Modifier = Modifier) {\n    if (!com.nuvio.tv.petalix.petalixHomeLayout().showLiveTv) return')
    # The native row can be called directly by fallback Home, independently of row position.
    text=live.read_text();start=text.index('fun LiveTvHomeRow(');body=text.index(') {',start)+3
    live.write_text(text[:body]+'\n    if (!com.nuvio.tv.petalix.petalixHomeLayout().showLiveTv) return'+text[body:])
    cards=source/'ui/screens/home/ModernHomeRows.kt'
    replace(cards,'    val petalixArtwork = payload?.folderId?.let { com.nuvio.tv.petalix.petalixServiceArtworkUri(it) }',
        '''    val homeLayout = com.nuvio.tv.petalix.petalixHomeLayout()
    val petalixArtwork = payload?.folderId?.let { homeLayout.serviceArtwork[it]
        ?: com.nuvio.tv.petalix.petalixServiceArtworkUri(it) }''')
    replace(categories,'artworkByService[folderId]?.let(::homeArtworkUri)',
        'if (folderId == "folder-petalix-britbox") "file:///android_asset/petalix/home-art/britbox-v2.png" else artworkByService[folderId]?.let(::homeArtworkUri)')
    assets=root/'app/src/main/assets/petalix'
    copy2(HERE/'home-art/britbox-v2.png',assets/'home-art/britbox-v2.png')
    copy2(HERE/'britbox-official-logo.svg',assets/'britbox-intro/logo-v2.svg')
    for name in ('PetalixServiceMedia.kt','ui2/PetalixServiceWidgets.kt'):
        target=source/'petalix'/name
        replace(target,'file:///android_asset/petalix/britbox-intro/logo.png',
            'file:///android_asset/petalix/britbox-intro/logo-v2.svg')
    coordinator=source/'petalix/PetalixManagedCoordinator.kt'
    replace(coordinator,'"guide-only-feeds-v1"','"guide-only-feeds-v1", "petalix-home-layout-v1"')
    text=coordinator.read_text();start=text.index('    private suspend fun ensureBritBoxService(');end=text.index('    private suspend fun ensureLocalHome(',start)
    text=text[:start]+'''    private suspend fun ensureBritBoxService(serviceProfileId: Int) {
        val saved = collections.getCurrentCollections(serviceProfileId)
        val id = "collection-streaming-services-only"
        val prior = saved.firstOrNull { it.id == id }
        val existing = prior?.folders?.firstOrNull { it.id == "folder-petalix-britbox" }
        val legacy = setOf(null, "", "file:///android_asset/petalix/home-art/britbox.webp",
            "file:///android_asset/petalix/britbox-intro/logo.png")
        if (existing != null && existing.coverImageUrl !in legacy) return
        val bundled = context.assets.open("petalix/streaming-services.json").bufferedReader()
            .use { collections.importFromJson(it.readText()) }.single()
        val fresh = bundled.folders.single { it.id == "folder-petalix-britbox" }
        val revised = prior?.copy(folders = if (existing == null) prior.folders + fresh
            else prior.folders.map { if (it.id == fresh.id) it.copy(coverImageUrl = fresh.coverImageUrl,
                heroBackdropUrl = fresh.heroBackdropUrl, titleLogoUrl = fresh.titleLogoUrl) else it }) ?: bundled
        collections.setCollections(saved.filterNot { it.id == id } + revised, serviceProfileId)
    }

'''+text[end:];coordinator.write_text(text)
