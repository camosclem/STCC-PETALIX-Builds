from home_visual_owner_patch import replace


def apply(root):
    source = root / 'app/src/main/java/com/nuvio/tv'
    hero = source / 'ui/screens/home/ModernHomeContent.kt'
    s = hero.read_text()
    start = s.index('            val petalixBackgroundItems = remember(carouselRows)')
    end = s.index('            val resolvedHeroState = remember(', start)
    s = s[:start] + s[end:]
    s = s.replace('remember(petalixServiceFocused, petalixBackgroundItems, petalixBackgroundIndex, ', 'remember(', 1)
    start = s.index('                    val focusedItem = activeCarouselItemState.value', start)
    end = s.index('                    } else focusedItem', start) + len('                    } else focusedItem')
    hero.write_text(s[:start] + '                    val activeCarouselItem = activeCarouselItemState.value' + s[end:])

    models = source / 'ui/screens/home/ModernHomeModels.kt'
    replace(models, '''    // Collection folders define their own tile shape — never override with''', '''    val folder = payload as? ModernPayload.CollectionFolder
    if (folder?.collectionId == com.nuvio.tv.petalix.PETALIX_SERVICES_COLLECTION) {
        val width = landscapeCardWidth * 1.18f
        return ModernCatalogCardMetrics(width = width, height = width / 2.55f)
    }
    // Collection folders define their own tile shape — never override with''')
    row = source / 'ui/screens/home/ModernHomeRowsList.kt'
    replace(row, '''                        activeRowKey.value == firstRowKey &&
                        !(liveTvRowAbove''', '''                        activeRowKey.value == firstRowKey &&
                        firstRowKey != com.nuvio.tv.petalix.PETALIX_SERVICES_ROW &&
                        !(liveTvRowAbove''')

    live = source / 'livetv/ui/LiveTvGuideScreen.kt'
    replace(live, '''                            if (settings.showChannelNumbers) append("${channel.number}  ")
                            append(channel.name)
                            playlistName?.let { append("  ·  $it") }
''', '''                            if (settings.showChannelNumbers) append(channel.number)
                            if (GuideInfoPart.TITLE in hid || !com.nuvio.tv.petalix.isRepeatedGuideTitle(channel.name, block?.program?.title)) {
                                if (isNotEmpty()) append("  ")
                                append(channel.name)
                            }
                            playlistName?.let {
                                if (isNotEmpty()) append("  ·  ")
                                append(it)
                            }
''')
    panel = source / 'livetv/ui/LiveTvPlayerPanel.kt'
    replace(panel, '''                                LiveText(channel.name, size = 13.sp, color = colors.TextSecondary, maxLines = 1, modifier = Modifier.widthIn(max = 320.dp))
                                Spacer(Modifier.width(10.dp))''',
            '''                                if (!com.nuvio.tv.petalix.isRepeatedGuideTitle(channel.name, title)) {
                                    LiveText(channel.name, size = 13.sp, color = colors.TextSecondary, maxLines = 1, modifier = Modifier.widthIn(max = 320.dp))
                                    Spacer(Modifier.width(10.dp))
                                }''')
