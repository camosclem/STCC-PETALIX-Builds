"""Keep artwork responsive while respecting panel trailer and preview-delay settings."""
from home_visual_owner_patch import replace

def apply(root):
    source = root / 'app/src/main/java/com/nuvio/tv'
    content = source / 'ui/screens/home/ModernHomeContent.kt'
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
