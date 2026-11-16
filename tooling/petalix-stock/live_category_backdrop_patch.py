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
    replace(categories, 'import androidx.compose.ui.draw.clip', 'import androidx.compose.ui.draw.shadow\nimport androidx.compose.ui.draw.clip')
    replace(categories, 'val shape = RoundedCornerShape(9.dp)', '''val shape = RoundedCornerShape(14.dp)
                val accent = when (artwork) {
                    "sports" -> Color(0xFF55B8FF)
                    "news" -> Color(0xFFFF8C83)
                    "entertainment" -> Color(0xFFC49BFF)
                    else -> Color(0xFF67DCCB)
                }''')
    replace(categories, '.clip(shape).background(Color(0xFF102742))', '''.shadow(if (focused) 18.dp else 0.dp, shape, clip = false,
                            ambientColor = accent, spotColor = accent)
                        .clip(shape).background(Color(0x99102742))''')
    replace(categories, '.border(if (focused) 2.dp else 1.dp, if (focused) Color(0xFF50A7FF) else Color(0xFF263348), shape)',
            '.border(if (focused) 3.dp else 1.dp, if (focused) accent else Color(0x667C91AB), shape)')
    replace(categories, '0f to Color(0xEC050C1D), .66f to Color(0x44050C1D), 1f to Color.Transparent',
            '0f to Color(0xD9050C1D), .66f to Color(0x55050C1D), 1f to Color(0x22050C1D)')
    models = source / 'ui/screens/home/ModernHomeModels.kt'
    replace(models, 'MODERN_HERO_MEDIA_WIDTH_FRACTION = 0.72f', 'MODERN_HERO_MEDIA_WIDTH_FRACTION = 0.90f')
