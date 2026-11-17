"""Replace the Home composition with a TV cinema layout; retain native navigation and playback."""
from pathlib import Path
from shutil import copy2
from home_visual_owner_patch import replace
HERE = Path(__file__).resolve().parent

def apply(root):
    source = root / 'app/src/main/java/com/nuvio/tv'
    copy2(HERE/'PetalixCinemaHero.kt', source/'ui/screens/home/PetalixCinemaHero.kt')
    copy2(HERE/'PetalixCinemaPolicy.kt', source/'petalix/PetalixCinemaPolicy.kt')
    copy2(HERE/'PetalixCinemaPolicyTest.kt', root/'app/src/test/java/com/nuvio/tv/petalix/PetalixCinemaPolicyTest.kt')
    content = source/'ui/screens/home/ModernHomeContent.kt'
    replace(content, 'import androidx.lifecycle.compose.LocalLifecycleOwner',
        'import androidx.lifecycle.compose.LocalLifecycleOwner\nimport androidx.lifecycle.compose.currentStateAsState')
    replace(content, '    val lifecycleOwner = LocalLifecycleOwner.current',
        '    val lifecycleOwner = LocalLifecycleOwner.current\n    val cinemaLifecycle = lifecycleOwner.lifecycle.currentStateAsState()')
    replace(content, '    val fullScreenBackdrop = uiState.modernHeroFullScreenBackdropEnabled',
        '    val fullScreenBackdrop = false // Browsing previews never hide shelves or take over navigation.')
    replace(content, '    val landscapeCatalogCardWidth = portraitBaseWidth * 1.24f * landscapeModernPosterScale',
        '    val landscapeCatalogCardWidth = com.nuvio.tv.petalix.PetalixCinemaPolicy.landscapeWidthDp(LocalConfiguration.current.screenWidthDp).dp')
    replace(content, '    val continueWatchingStyle = uiState.continueWatchingCardStyle',
        '    val continueWatchingStyle = ContinueWatchingCardStyle.CARD')
    replace(content, 'ContinueWatchingCardStyle.CARD -> portraitBaseWidth * 1.24f * continueWatchingScale',
        'ContinueWatchingCardStyle.CARD -> landscapeCatalogCardWidth')
    replace(content, 'val rowsViewportHeightFraction = if (useLandscapePosters) 0.49f else 0.52f',
        'val rowsViewportHeightFraction = if (useLandscapePosters) 0.47f else 0.52f')
    replace(content, 'val posterCardCornerRadius = remember(uiState.posterCardCornerRadiusDp) { uiState.posterCardCornerRadiusDp.dp }',
        'val posterCardCornerRadius = 8.dp')
    replace(content, '.offset(x = NuvioTheme.spacing.huge).fillMaxWidth(MODERN_HERO_MEDIA_WIDTH_FRACTION)',
        '.fillMaxWidth()')
    replace(content, 'else (screenWidth * MODERN_HERO_MEDIA_WIDTH_FRACTION).roundToPx()',
        'else screenWidth.roundToPx()')
    replace(content, 'shouldPlayTrailer = shouldPlayHeroTrailerState.value,', '''shouldPlayTrailer = com.nuvio.tv.petalix.PetalixCinemaPolicy.canPreview(
                            shouldPlayHeroTrailerState.value,
                            cinemaLifecycle.value.isAtLeast(Lifecycle.State.RESUMED),
                            isSidebarExpanded.value, verticalRowListState.isScrollInProgress,
                            isRapidHorizontalNav.value),''')
    # No full-screen takeover or fading out the shelves on a browsing preview.
    s = content.read_text()
    start = s.index('            HeroTitleBlock(')
    end = s.index('            val latestOnFocusedRowKeyChanged', start)
    s = s[:start] + '''            PetalixCinemaHeader(Modifier.align(Alignment.TopStart).padding(start = rowHorizontalPadding, top = 20.dp))
            PetalixCinemaHero(
                scene = heroSceneStateLambda,
                selection = { activeCarouselItemState.value?.payload },
                rowTitle = { activeRowKey.value?.let { rowByKey[it]?.title } },
                showRatings = uiState.homeImdbRatingsVisibility.showRatings,
                modifier = heroMetadataModifier
            )

''' + s[end:]
    s = s.replace('.fillMaxWidth(MODERN_HERO_TEXT_WIDTH_FRACTION)', '.fillMaxWidth(0.47f)')
    # Ask for trailer data only when focus has settled; expansion still controls actual playback.
    assert s.count('            delay(150)') == 1
    s = s.replace('            delay(150)', '            delay(650)')
    content.write_text(s)
    replace(content, 'Box(modifier = Modifier.fillMaxSize().background(NuvioTheme.colors.Background))',
        'Box(modifier = Modifier.fillMaxSize().background(Color(0xFF101116)))')
    replace(content, 'val bgColor = NuvioTheme.colors.Background', 'val bgColor = Color(0xFF101116)')
    hero = source/'ui/screens/home/ModernHomeHero.kt'
    replace(hero, 'val horizontalFadeEndX = size.width * if (fullScreen) 0.65f else 0.45f',
        'val horizontalFadeEndX = size.width * if (fullScreen) 0.65f else 0.85f')
    replace(hero, 'val bottomStripStartY = size.height * if (fullScreen) 0.64f else 0.82f',
        'val bottomStripStartY = size.height * if (fullScreen) 0.64f else 0.64f')
    models = source/'ui/screens/home/ModernHomeModels.kt'
    replace(models, 'internal const val MODERN_TRAILER_OVERSCAN_ZOOM = 1.35f',
        'internal const val MODERN_TRAILER_OVERSCAN_ZOOM = 1.02f')
    replace(models, 'internal const val MODERN_HERO_FOCUS_DEBOUNCE_MS = 450L',
        'internal const val MODERN_HERO_FOCUS_DEBOUNCE_MS = 120L')
    cards = source/'ui/screens/home/ModernHomeRows.kt'
    replace(cards, 'val focusRingBorder = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs)',
        'val focusRingBorder = BorderStroke(2.dp, Color.White)')
    replace(cards, 'val rowTitleStyle = remember(titleMediumStyle) {\n            titleMediumStyle.copy(fontWeight = FontWeight.SemiBold)',
        'val rowTitleStyle = remember(titleMediumStyle) {\n            titleMediumStyle.copy(fontWeight = FontWeight.Bold)')
    coordinator = source/'petalix/PetalixManagedCoordinator.kt'
    replace(coordinator, 'landscapeTrailerBrowsingV1:', 'cinemaHomeRedesignV1:')
    replace(coordinator, '            layout.setPosterCardWidthDp(190)', '''            layout.setLayout(com.nuvio.tv.domain.model.HomeLayout.MODERN)
            layout.setPosterCardWidthDp(164)
            layout.setPosterCardCornerRadiusDp(8)
            layout.setModernHeroFullScreenBackdropEnabled(false)
            layout.setContinueWatchingCardStyle(com.nuvio.tv.domain.model.ContinueWatchingCardStyle.CARD)''')
    replace(coordinator, '            layout.setFocusedPosterBackdropExpandDelaySeconds(1)',
        '            layout.setFocusedPosterBackdropExpandDelaySeconds(2)')
    # This exact multiline occurrence belongs to the browsing migration, not the old seed.
    replace(coordinator, '                com.nuvio.tv.domain.model.FocusedPosterTrailerPlaybackTarget.EXPANDED_CARD',
        '                com.nuvio.tv.domain.model.FocusedPosterTrailerPlaybackTarget.HERO_MEDIA')
