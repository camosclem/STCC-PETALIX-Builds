"""Existing 13.2 presentation: animated service tiles and focus-driven recent TV hero."""
from pathlib import Path
import re
from home_visual_owner_patch import replace

def apply(root):
    source=root/'app/src/main/java/com/nuvio/tv'
    categories=source/'petalix/PetalixHomeCategories.kt'
    text=categories.read_text()
    labels={
        'folder-WFGY1XPD':'Movies, TV Shows & Originals',
        'folder-AT8KYHZO':'Movies, TV Shows & Originals',
        'folder-8ZBZOMW9':'Disney, Pixar, Marvel & More',
        'folder-LN9SPMQN':'TV Shows, Movies & Originals',
        'folder-OA4T8NYO':'HBO Originals, Movies & More',
        'folder-IQTF27K8':'Apple Original Shows & Movies',
        'folder-9YQK1ZF1':'TV, Movies & Live Sports',
        'folder-ILJM8PD0':'A Mountain of Entertainment',
        'folder-petalix-britbox':'The Best of British TV'}
    replacement='internal fun petalixServiceTagline(folderId: String): String? = when (folderId) {\n'+''.join('    "'+k+'" -> "'+v+'"\n' for k,v in labels.items())+'    else -> null\n}'
    text,n=re.subn(r'internal fun petalixServiceTagline\(folderId: String\): String\? = when \(folderId\) \{.*?\n\}',lambda _:replacement,text,flags=re.S)
    assert n==1
    categories.write_text(text)
    state=source/'petalix/PetalixLiveCategoryFocus.kt'
    replace(state,'    val category = mutableStateOf<String?>(null)', '''    data class Recent(val key: String, val title: String, val channel: String, val art: String)
    val recent = mutableStateOf<Recent?>(null)
    val category = mutableStateOf<String?>(null)''')
    hero=source/'ui/screens/home/PetalixCategoryHero.kt'
    replace(hero,'        else -> return base', '''        else -> {
            val recent = com.nuvio.tv.petalix.PetalixLiveCategoryFocus.recent.value ?: return base
            return base.copy(heroBackdrop = recent.art,
                preview = HeroPreview(title = recent.title, logo = null, description = recent.channel,
                    contentTypeText = "Live TV", yearText = null, imdbText = null, genres = StableList(),
                    poster = null, backdrop = recent.art, imageUrl = recent.art),
                enrichmentActive = false, shouldPlayTrailer = false, trailerFirstFrameRendered = false,
                trailerUrl = null, trailerAudioUrl = null, trailerPlaybackKey = null, fullScreenBackdrop = false)
        }''')
    row=source/'livetv/home/LiveTvHomeRow.kt'
    replace(row,'.onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }', '''.onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    com.nuvio.tv.petalix.PetalixLiveCategoryFocus.category.value = null
                    com.nuvio.tv.petalix.PetalixLiveCategoryFocus.recent.value =
                        com.nuvio.tv.petalix.PetalixLiveCategoryFocus.Recent(entry.channel.key,
                            entry.now?.title ?: entry.channel.name, entry.channel.name,
                            entry.now?.icon?.takeIf { it.isNotBlank() } ?: entry.channel.logo?.takeIf { it.isNotBlank() }
                                ?: "file:///android_asset/petalix/home-art/home.webp")
                    onFocused()
                } else if (com.nuvio.tv.petalix.PetalixLiveCategoryFocus.recent.value?.key == entry.channel.key) {
                    com.nuvio.tv.petalix.PetalixLiveCategoryFocus.recent.value = null
                }
            }''')
    cards=source/'ui/screens/home/ModernHomeRows.kt'
    replace(cards,'                    if (it.isFocused) com.nuvio.tv.petalix.PetalixLiveCategoryFocus.category.value = categoryTitle', '''                    if (it.isFocused) {
                        com.nuvio.tv.petalix.PetalixLiveCategoryFocus.recent.value = null
                        com.nuvio.tv.petalix.PetalixLiveCategoryFocus.category.value = categoryTitle
                    }''')
    content=source/'ui/screens/home/ModernHomeContent.kt'
    s=content.read_text().replace('com.nuvio.tv.petalix.PetalixLiveCategoryFocus.category.value == null &&', 'com.nuvio.tv.petalix.PetalixLiveCategoryFocus.category.value == null && com.nuvio.tv.petalix.PetalixLiveCategoryFocus.recent.value == null &&')
    content.write_text(s)
    # Apply the TV browsing defaults once per profile after its initial panel assignment.
    # Subsequent unchanged check-ins must not reset the viewer's choices.
    panel=source/'petalix/PetalixPanelSettings.kt'
    replace(panel,'if (stamp == prefs.getString("stamp", "") && !values.optBoolean("mode.lockSettings")) {',
            'if (stamp == prefs.getString("stamp", "")) {')
    coordinator=source/'petalix/PetalixManagedCoordinator.kt'
    replace(coordinator,'        applyPanelSettings(doc)\n', '''        applyPanelSettings(doc)
        val browsingDefaultsKey = "landscapeTrailerBrowsingV1:${profiles.activeProfileId.value}"
        if (!prefs.getBoolean(browsingDefaultsKey, false)) {
            layout.setModernLandscapePostersEnabled(true)
            layout.setPosterCardWidthDp(190)
            layout.setFocusedPosterBackdropExpandEnabled(true)
            layout.setFocusedPosterBackdropExpandDelaySeconds(1)
            layout.setFocusedPosterBackdropTrailerEnabled(true)
            layout.setFocusedPosterBackdropTrailerMuted(true)
            layout.setFocusedPosterBackdropTrailerPlaybackTarget(
                com.nuvio.tv.domain.model.FocusedPosterTrailerPlaybackTarget.EXPANDED_CARD
            )
            tmdb.setUseTrailers(true)
            check(prefs.edit().putBoolean(browsingDefaultsKey, true).commit())
        }
''')
