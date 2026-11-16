"""Focus-scoped hero artwork for the existing 13.2 Live TV category row."""
from pathlib import Path
from shutil import copy2
from home_visual_owner_patch import replace

HERE = Path(__file__).resolve().parent


def apply(root):
    source = root / 'app/src/main/java/com/nuvio/tv'
    copy2(HERE / 'PetalixLiveCategoryFocus.kt', source / 'petalix/PetalixLiveCategoryFocus.kt')
    copy2(HERE / 'PetalixCategoryHero.kt', source / 'ui/screens/home/PetalixCategoryHero.kt')
    tests = root / 'app/src/test/java/com/nuvio/tv/ui/screens/home'
    tests.mkdir(parents=True, exist_ok=True)
    copy2(HERE / 'PetalixCategoryHeroTest.kt', tests / 'PetalixCategoryHeroTest.kt')
    categories = source / 'petalix/PetalixHomeCategories.kt'
    replace(categories, 'import androidx.compose.foundation.background',
            'import androidx.compose.foundation.focusGroup\nimport androidx.compose.runtime.DisposableEffect\nimport androidx.compose.runtime.onDispose\nimport androidx.compose.foundation.background')
    # onDispose is a DisposableEffectScope member; no top-level import is needed.
    categories.write_text(categories.read_text().replace('import androidx.compose.runtime.onDispose\n', ''))
    replace(categories, '    val actions = LocalLiveTvHomeActions.current', '''    DisposableEffect(Unit) {
        onDispose { PetalixLiveCategoryFocus.category.value = null }
    }
    val actions = LocalLiveTvHomeActions.current''')
    replace(categories, 'modifier = Modifier.focusRestorer(),', '''modifier = Modifier.onFocusChanged {
                if (!it.hasFocus) PetalixLiveCategoryFocus.category.value = null
            }.focusGroup().focusRestorer(),''')
    replace(categories, '.onFocusChanged { focused = it.isFocused }', '''.onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) PetalixLiveCategoryFocus.category.value = title
                        }''')
    hero = source / 'ui/screens/home/ModernHomeContent.kt'
    replace(hero, 'val currentLive = currentLiveHeroSceneStateHolder.value', '''val currentLive = petalixCategoryHero(currentLiveHeroSceneStateHolder.value,
                        com.nuvio.tv.petalix.PetalixLiveCategoryFocus.category.value)''')
    replace(hero, 'derivedStateOf { liveHeroSceneState.value.fullScreenBackdrop }', '''derivedStateOf { petalixCategoryHero(liveHeroSceneState.value,
                    com.nuvio.tv.petalix.PetalixLiveCategoryFocus.category.value).fullScreenBackdrop }''')
    replace(hero, 'targetValue = if (fullScreenBackdropUpdated &&',
            'targetValue = if (com.nuvio.tv.petalix.PetalixLiveCategoryFocus.category.value == null && fullScreenBackdropUpdated &&')
    replace(hero, '{ shouldPlayCatalogHeroTrailerUpdated || shouldPlayCollectionHeroVideoUpdated }',
            '{ com.nuvio.tv.petalix.PetalixLiveCategoryFocus.category.value == null && (shouldPlayCatalogHeroTrailerUpdated || shouldPlayCollectionHeroVideoUpdated) }')
    settings = source / 'ui/screens/settings/LiveTvSettingsContent.kt'
    replace(settings, 'SettingsActionRow("Local stations", "Change ZIP code or local stations"',
            'SettingsActionRow("Local channels", "Change ZIP code or choose your local stations"')
