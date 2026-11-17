"""Keep alternate and event feeds in the repository while hiding them from guide browsing."""
from pathlib import Path
from shutil import copy2
from home_visual_owner_patch import replace

HERE = Path(__file__).resolve().parent


def apply(root):
    source = root / 'app/src/main/java/com/nuvio/tv'
    copy2(HERE / 'PetalixGuideFeedPolicy.kt', source / 'petalix/PetalixGuideFeedPolicy.kt')
    copy2(HERE / 'PetalixGuideFeedPolicyTest.kt',
          root / 'app/src/test/java/com/nuvio/tv/petalix/PetalixGuideFeedPolicyTest.kt')

    parser = source / 'livetv/data/M3uParser.kt'
    replace(parser, '    val tvgShiftMinutes: Int = 0\n)', '''    val tvgShiftMinutes: Int = 0,
    val petalixGuideHidden: Boolean = false
)''')
    replace(parser, '            catchup = catchup,\n            // Hours', '''            catchup = catchup,
            petalixGuideHidden = attrs["petalix-guide-hidden"].equals("true", ignoreCase = true),
            // Hours''')
    replace(source / 'livetv/model/LiveTvModels.kt', '    val tvgShiftMinutes: Int = 0\n)', '''    val tvgShiftMinutes: Int = 0,
    /** Presentation only. Still available to backup playback and the Sports Hub. */
    val petalixGuideHidden: Boolean = false
)''')

    repository = source / 'livetv/data/LiveTvRepository.kt'
    replace(repository, '                            attr("tvg-chno", f["num"])', '''                            attr("tvg-chno", f["num"])
                            attr("petalix-guide-hidden", f["petalix_guide_hidden"])''')
    replace(repository, '                    tvgShiftMinutes = e.tvgShiftMinutes\n', '''                    tvgShiftMinutes = e.tvgShiftMinutes,
                    petalixGuideHidden = pl.id == "petalix-managed" && e.petalixGuideHidden
''')

    viewmodel = source / 'livetv/ui/LiveTvViewModel.kt'
    replace(viewmodel, '''        val guideChannels = channels.filterNot {
            com.nuvio.tv.petalix.hideSportsEventInGuide(it.group, it.name)
        }
        buildUi(guideChannels, user, s, groupIdRaw, query, hasSrc)''', '''        buildUi(channels, user, s, groupIdRaw, query, hasSrc)''')
    replace(viewmodel, '    val notHidden = channels.filter { it.key !in user.hiddenChannels }', '''    val customSaved = user.customGroups.firstOrNull { it.id == groupIdRaw }?.channelKeys.orEmpty().toSet()
    val notHidden = channels.filter { channel ->
        val savedChoice = groupIdRaw == ChannelGroup.FAVORITES && channel.key in user.favorites ||
            groupIdRaw == ChannelGroup.RECENT && channel.key in user.recent || channel.key in customSaved
        channel.key !in user.hiddenChannels &&
            !com.nuvio.tv.petalix.hideSportsEventInGuide(channel.group, channel.name) &&
            com.nuvio.tv.petalix.petalixShowGuideFeed(channel.petalixGuideHidden, savedChoice)
    }''')

    coordinator = source / 'petalix/PetalixManagedCoordinator.kt'
    replace(coordinator, '"favorites-backup-v1"', '"favorites-backup-v1", "guide-only-feeds-v1"')
    replace(coordinator, '        if (changed) repository.reloadAfterSync(setOf("petalix-managed"))', '''        val guidePoolRevision = doc.optJSONObject("nativeConfig")?.optString("guidePoolRevision").orEmpty()
        val guidePoolKey = "guidePoolRevision:${profiles.activeProfileId.value}"
        val refreshGuidePool = guidePoolRevision.isNotBlank() &&
            guidePoolRevision != prefs.getString(guidePoolKey, "")
        if (changed || refreshGuidePool) {
            repository.reloadAfterSync(setOf("petalix-managed"))
            if (guidePoolRevision.isNotBlank()) {
                prefs.edit().putString(guidePoolKey, guidePoolRevision).apply()
            }
        }''')
