"""Use the pinned native guide views; preserve logo data and cache guide-only policy work."""
from pathlib import Path
from shutil import copy2
import subprocess
from home_visual_owner_patch import replace
HERE = Path(__file__).resolve().parent
PIN = '2d898cd8be38cd81a087604cd9dc974a06cf9a00'
NATIVE_GUIDE_FILES = ('LiveTvGuideScreen.kt', 'GuideRowStrip.kt', 'LiveTvPlayerPanel.kt')

def stock(root, relative):
    return subprocess.check_output(['git', 'show', f'{PIN}:{relative}'], cwd=root).decode()

def apply(root):
    prefix = 'app/src/main/java/com/nuvio/tv/'
    for name in NATIVE_GUIDE_FILES:
        relative = prefix + 'livetv/ui/' + name
        (root / relative).write_text(stock(root, relative))
    # Keep only the upgraded logo loader in the otherwise exact native components.
    relative = prefix + 'livetv/ui/LiveTvComponents.kt'
    path = root / relative
    current = path.read_text()
    native = stock(root, relative)
    def logo_span(text):
        start = text.index('internal fun ChannelLogo(')
        end = text.index('\n/**\n * Logo links', start)
        return start, end
    a, b = logo_span(current)
    c, d = logo_span(native)
    path.write_text(native[:c] + current[a:b] + native[d:])
    copy2(HERE / 'PetalixGuideCache.kt', root / prefix / 'petalix/PetalixGuideCache.kt')
    guide = root / prefix / 'livetv/ui/LiveTvViewModel.kt'
    replace(guide, '''    val customSaved = user.customGroups.firstOrNull { it.id == groupIdRaw }?.channelKeys.orEmpty().toSet()
    val guideChannels = channels.filter { channel ->
        val savedChoice = groupIdRaw == ChannelGroup.FAVORITES && channel.key in user.favorites ||
            groupIdRaw == ChannelGroup.RECENT && channel.key in user.recent || channel.key in customSaved
        com.nuvio.tv.petalix.petalixGuideChannelAllowed(channel, savedChoice)
    }''', '''    val savedKeys = when (groupIdRaw) {
        ChannelGroup.FAVORITES -> user.favorites.toSet()
        ChannelGroup.RECENT -> user.recent.toSet()
        else -> user.customGroups.firstOrNull { it.id == groupIdRaw }?.channelKeys.orEmpty().toSet()
    }
    val guideChannels = com.nuvio.tv.petalix.petalixGuideCache.channels(channels, savedKeys)''')
    guide.write_text(guide.read_text().replace('com.nuvio.tv.petalix.petalixGuidePresentation(visibility.',
        'com.nuvio.tv.petalix.petalixGuideCache.presentation(visibility.'))
    # Enrichment must not erase the honest TMDB score delivered by a chart item.
    home = root / prefix / 'ui/screens/home/ModernHomeContent.kt'
    replace(home, 'mdbListRatings = enrichedItem.mdbListRatings,', '''mdbListRatings = enrichedItem.mdbListRatings?.let { ratings ->
                                ratings.copy(tmdb = ratings.tmdb ?: activeCarouselItem?.heroPreview?.mdbListRatings?.tmdb)
                            } ?: activeCarouselItem?.heroPreview?.mdbListRatings,''')

    rows = root / prefix / 'ui/screens/home/ModernHomeRowsList.kt'
    text = rows.read_text()
    text = text.replace('LiveTvHomeRow(hideUnlessFocused = true,',
                        'LiveTvHomeRow(persistent = true, hideUnlessFocused = false,')
    rows.write_text(text)
