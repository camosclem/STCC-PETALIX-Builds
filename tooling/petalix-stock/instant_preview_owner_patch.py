"""Change focus artwork immediately; keep automatic trailer playback independent of enrichment."""
from home_visual_owner_patch import replace

def apply(root):
    source = root / 'app/src/main/java/com/nuvio/tv'
    coordinator = source / 'petalix/PetalixManagedCoordinator.kt'
    replace(coordinator, '    private suspend fun ensureLocalHome(profileId: Int) {', '''    private suspend fun ensureLocalHome(profileId: Int) {
        if (!prefs.getBoolean("nativeInstantPreviewV1:$profileId", false)) {
            tmdb.setUseTrailers(true)
            layout.setFocusedPosterBackdropExpandEnabled(true)
            layout.setFocusedPosterBackdropExpandDelaySeconds(0)
            layout.setFocusedPosterBackdropTrailerEnabled(true)
            layout.setFocusedPosterBackdropTrailerPlaybackTarget(
                com.nuvio.tv.domain.model.FocusedPosterTrailerPlaybackTarget.HERO_MEDIA
            )
            check(prefs.edit().putBoolean("nativeInstantPreviewV1:$profileId", true).commit())
        }''')
    replace(coordinator, 'layout.setFocusedPosterBackdropExpandDelaySeconds(2)', 'layout.setFocusedPosterBackdropExpandDelaySeconds(0)')
    replace(source / 'data/local/LayoutPreferenceDataStore.kt',
            'DEFAULT_FOCUSED_POSTER_BACKDROP_EXPAND_DELAY_SECONDS = 3',
            'DEFAULT_FOCUSED_POSTER_BACKDROP_EXPAND_DELAY_SECONDS = 0')
    content = source / 'ui/screens/home/ModernHomeContent.kt'
    replace(content, 'mutableLongStateOf(MODERN_HERO_FOCUS_DEBOUNCE_MS)', 'mutableLongStateOf(0L)')
    replace(content, 'heroFocusSettleDelayMs.longValue = delay', 'heroFocusSettleDelayMs.longValue = 0L')
    replace(content, '(uiState.focusedPosterBackdropExpandDelaySeconds.coerceAtLeast(0) * 1000L).coerceAtLeast(150L)',
            '(uiState.focusedPosterBackdropExpandDelaySeconds.coerceAtLeast(0) * 1000L)')
    replace(content, 'isScrolling && stableHasPreview -> stable!!',
            'isScrolling && stableHasPreview -> stable!!.copy(heroBackdrop = currentLive.heroBackdrop)')
    replace(content, 'isRapidNav && stable != null -> stable',
            'isRapidNav && stable != null -> stable.copy(heroBackdrop = currentLive.heroBackdrop)')
    hero = source / 'ui/screens/home/ModernHomeHero.kt'
    replace(hero, 'rawBackdrop != displayedBackdrop && !enriching', 'rawBackdrop != displayedBackdrop')
    replace(hero, 'animationSpec = tween(durationMillis = NuvioMotion.tokens.durations.overlay),\n            label = "heroBackdropCrossfade"',
            'animationSpec = tween(durationMillis = 0),\n            label = "heroBackdropCrossfade"')
