"""Home presentation and navigation adapters; the native guide views are untouched."""
from pathlib import Path
from shutil import copy2
import re
from home_visual_owner_patch import replace
HERE = Path(__file__).resolve().parent

def apply(root):
    source = root / 'app/src/main/java/com/nuvio/tv'
    for name in ('PetalixNativeHomeShortcut.kt', 'PetalixGuideGroupIndex.kt'):
        copy2(HERE/name, source/'petalix'/name)
    copy2(HERE/'PetalixGuideGroupIndexTest.kt', root/'app/src/test/java/com/nuvio/tv/petalix/PetalixGuideGroupIndexTest.kt')
    hero = source/'ui/screens/home/PetalixCategoryHero.kt'
    replace(hero, 'val backdrop = com.nuvio.tv.petalix.PetalixLiveArtwork.forCategory(art)',
        'val backdrop = "file:///android_asset/petalix/home-art/live/$art.jpg"')
    rows = source/'ui/screens/home/ModernHomeRowsList.kt'
    # The upstream merge removed the old Home-category insertion. Add it after Services.
    text = rows.read_text()
    text = re.sub(r'\s*if \(row.key == com.nuvio.tv.petalix.PETALIX_SERVICES_ROW\) \{\s*com.nuvio.tv.petalix.PetalixHomeCategories\(\)\s*\}', '', text)
    anchor = '                if (liveTvHere && liveTvRowAbove == false && hasContinueWatching) {'
    assert text.count(anchor) == 1
    text = text.replace(anchor, '''                if (row.key == com.nuvio.tv.petalix.PETALIX_SERVICES_ROW) {
                    com.nuvio.tv.petalix.PetalixHomeCategories()
                }
''' + anchor)
    rows.write_text(text)
    nav = source/'ui/navigation/NuvioNavHost.kt'
    text = nav.read_text()
    start = text.index('                val match = if (title == "Sports") {')
    end = text.index('                if (match != null) viewModel.selectGroup(match.id)',start)
    text = text[:start] + '''                val match = com.nuvio.tv.petalix.petalixNativeHomeGroup(title, groups)
''' + text[end:]
    text = text.replace('if (match != null) viewModel.selectGroup(match.id)', 'if (match != null) viewModel.selectGroup(match)')
    nav.write_text(text)
    # Consolidate duplicate labels at the data boundary. Each source group's native
    # membership function still enforces hidden channels, parental locks and saved lists.
    vm = source/'livetv/ui/LiveTvViewModel.kt'
    text = vm.read_text()
    start = text.index('    val rawList = when {')
    end = text.index('    // TiviMate\'s number override:', start)
    native = text[start:end].replace('    val rawList = when {', '    fun nativeChannels(nativeId: String): List<LiveChannel> = when {')
    native = re.sub(r'\bgroupId\b', 'nativeId', native)
    text = text[:start] + '''    val rawList = guideIndex.idsFor(groupId).let { ids ->
        val combined = ids.flatMap(::nativeChannels).distinctBy { it.key }
        if (ids.size > 1) sorted(combined) else combined
    }
''' + text[end:]
    anchor = '    groups += orderedRegular'
    text = text.replace(anchor, native + '''    groups += orderedRegular
    val guideIndex = com.nuvio.tv.petalix.PetalixGuideGroupIndex(groups, ::nativeChannels)
    groups.clear()
    groups.addAll(guideIndex.groups)
    val regularForGuide = groups.filterNot { it.special }
    val requestedGroupId = groupIdRaw?.let(guideIndex::canonicalId)''')
    text = text.replace('orderedRegular.isNotEmpty() -> orderedRegular.first().id', 'regularForGuide.isNotEmpty() -> regularForGuide.first().id')
    text = text.replace('val groupId = if (exactGroup) groupIdRaw ?: "" else groupIdRaw?.takeIf', 'val groupId = if (exactGroup) requestedGroupId ?: "" else requestedGroupId?.takeIf')
    text = text.replace('orderableGroupIds = orderedRegular.map { it.id }', 'orderableGroupIds = regularForGuide.map { it.id }')
    vm.write_text(text)
