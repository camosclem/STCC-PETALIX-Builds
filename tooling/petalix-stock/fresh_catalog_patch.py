"""Fresh daily movie/TV charts and remote artwork revalidation, without touching playback."""
from pathlib import Path
from shutil import copy2
import json
from home_visual_owner_patch import replace
HERE=Path(__file__).resolve().parent

def apply(root):
    source=root/'app/src/main/java/com/nuvio/tv'
    for name in ('PetalixCatalogPolicy.kt','PetalixArtworkRefresh.kt'):
        copy2(HERE/name,source/'petalix'/name)
    copy2(HERE/'PetalixCatalogPolicyTest.kt',root/'app/src/test/java/com/nuvio/tv/petalix/PetalixCatalogPolicyTest.kt')
    home=source/'ui/screens/home/HomeScreen.kt'
    s=home.read_text()
    start=s.index('    val penguRows = sourceState.catalogRows.filter { row ->')
    end=s.index('    val uiState = sourceState.copy(',start)
    s=s[:start]+'''    val homeLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(homeLifecycleOwner, homeCatalogViewModel) {
        homeLifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                homeCatalogViewModel.load()
                kotlinx.coroutines.delay(60_000)
            }
        }
    }
    val vodVersion by vodViewModel.catalogVersion.collectAsStateWithLifecycle()
    LaunchedEffect(vodVersion) { vodViewModel.loadHome() }
    val homeRows = remember(sourceState.homeRows, homeChartRows) {
        // Rankings come from actual chart endpoints; category names are not popularity data.
        sourceState.homeRows.filterIsInstance<HomeRow.CollectionRow>() +
            homeChartRows.map { HomeRow.Catalog(it) }
    }
''' +s[end:]
    s=s.replace('import androidx.compose.runtime.Composable','import androidx.compose.runtime.Composable\nimport androidx.lifecycle.repeatOnLifecycle',1)
    home.write_text(s)
    api=source/'data/remote/api/TmdbApi.kt'
    replace(api, '    @GET("{chart}")', '    @retrofit2.http.Headers("Cache-Control: no-cache")\n    @GET("{chart}")')
    categories=source/'petalix/PetalixHomeCategories.kt'
    s=categories.read_text()
    entries=json.loads((HERE/'service-artwork.json').read_text())
    old='artworkByService[folderId]?.let(::homeArtworkUri) ?: if (folderId in setOf('+','.join('"'+key+'"' for key in entries)+')) "file:///android_asset/petalix/service-art-v1/$folderId.jpg" else null'
    new='artworkByService[folderId]?.let(::homeArtworkUri) ?: when (folderId) {\n'+''.join('    "'+key+'" -> "'+e['url']+'"\n' for key,e in entries.items())+'    else -> null\n}'
    assert s.count(old)==1
    categories.write_text(s.replace(old,new))
    cards=source/'ui/screens/home/ModernHomeRows.kt'
    replace(cards,'val fallbackUrl = if (isLandscapeCustomPoster) {', '''val fallbackUrl = if (petalixArtwork?.startsWith("https://art.kaptain.stream/") == true) {
                "file:///android_asset/petalix/service-art-v1/${payload?.folderId}.jpg"
            } else if (isLandscapeCustomPoster) {''')
    # Existing invalidation subscriptions now also expire visible memory entries every six hours.
    key=source/'core/image/RememberImageRevalidationKey.kt'
    replace(key,'    return version', '''    val epoch = com.nuvio.tv.petalix.rememberPetalixArtworkEpoch(imageUrl)
    return epoch * 1000 + version''')
    # Check changed validators even when a host advertises a very long cache lifetime.
    strategy=source/'core/image/StaleWhileRevalidateCacheStrategy.kt'
    replace(strategy,'        if (delegateResult.response != null) return delegateResult',
        '        // Revalidate remote artwork validators even with a long CDN cache lifetime.')
    replace(strategy,'private const val REVALIDATION_COOLDOWN_MS = 10L * 60 * 1000 // 10 min',
        'private const val REVALIDATION_COOLDOWN_MS = 60L * 60 * 1000 // one hour per URL')
    # The native logo component did not subscribe to refreshed image versions.
    logos=source/'livetv/ui/LiveTvComponents.kt'
    replace(logos,'internal fun ChannelLogo(url: String?, size: Dp, modifier: Modifier = Modifier) {', '''internal fun ChannelLogo(url: String?, size: Dp, modifier: Modifier = Modifier) {
    val imageVersion = com.nuvio.tv.core.image.rememberImageRevalidationKey(url)
    val logoContext = androidx.compose.ui.platform.LocalContext.current
    val refreshedLogo = androidx.compose.runtime.remember(url, imageVersion) {
        coil3.request.ImageRequest.Builder(logoContext).data(url)
            .memoryCacheKey("$url:logo:$imageVersion").build()
    }''')
    replace(logos,'                model = url,','                model = refreshedLogo,')
    hero=source/'ui/screens/home/ModernHomeHero.kt'
    replace(hero,'    val imageModel = remember(\n        localContext,\n        displayedBackdrop,', '''    val artworkVersion = com.nuvio.tv.core.image.rememberImageRevalidationKey(displayedBackdrop)
    val imageModel = remember(
        localContext,
        artworkVersion,
        displayedBackdrop,''')
    replace(hero,'                .data(it)\n                .size(width = requestWidthPx, height = requestHeightPx)',
        '                .data(it)\n                .memoryCacheKey("$it:hero:$artworkVersion")\n                .size(width = requestWidthPx, height = requestHeightPx)')
    coordinator=source/'petalix/PetalixManagedCoordinator.kt'
    replace(coordinator,'        ensureBritBoxService(profiles.activeProfileId.value)\n', '''        ensureBritBoxService(profiles.activeProfileId.value)
        val refreshDefaultsKey = "freshHomeDataV1:${profiles.activeProfileId.value}"
        if (!prefs.getBoolean(refreshDefaultsKey, false)) {
            live.updateSettings { it.copy(playlistRefreshHours = 4, epgRefreshHours = 4) }
            check(prefs.edit().putBoolean(refreshDefaultsKey, true).commit())
        }
''')
