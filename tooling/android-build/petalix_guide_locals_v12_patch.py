"""Keep EPG text inside its row, expose saved locals, and stabilize group travel."""
from pathlib import Path
import shutil


def one(text, old, new, label):
    if text.count(old) != 1:
        raise RuntimeError("Petalix v12 anchor changed: " + label)
    return text.replace(old, new)


def apply(work):
    root = Path(work) / "app/src/main/java/com/nuvio/tv"
    ui = root / "petalix/ui2"
    shutil.copyfile(Path(__file__).with_name("PetalixLocalChannelsV12.kt"), ui / "PetalixLocalChannels.kt")

    p = ui / "Petalix2Home.kt"
    s = p.read_text()
    # Keep the existing category route and restoration key for installed customers.
    s = one(s, 'Text(name,color=Color.White,fontSize=13.sp,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.BottomCenter).padding(8.dp))',
        'Text(if(name=="Local News")"My Locals" else name,color=Color.White,fontSize=13.sp,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.BottomCenter).padding(8.dp))', "My Locals home thumbnail label")
    p.write_text(s)

    # custom_groups is panel locked. Locals are personal selections, not an admin
    # settings edit: keep them in a dedicated per-profile key and merge for display.
    p = root / "livetv/data/LiveTvPreferences.kt"
    s = p.read_text()
    anchor = '    suspend fun petalixLocalZip():String = store.data.first()[my(stringPreferencesKey("petalix_local_zip"))].orEmpty()'
    s = one(s, anchor, anchor + '''
    private fun petalixGroups(p:Preferences,id:Int):List<CustomGroup> {
        val regular=decodeCustomGroups(profileView(p,id)[Keys.customGroups])
        val local=decodeCustomGroups(p[my(stringPreferencesKey("petalix_local_group"),id)]).firstOrNull()
            ?: return regular
        return regular.filterNot { it.id==local.id || it.name.equals("Local News",true) || it.name.equals("My Locals",true) } + local
    }
''', "personal locals storage")
    s = one(s, 'val groups=decodeCustomGroups(store.data.first()[my(Keys.customGroups,currentProfile)])',
        'val groups=petalixGroups(store.data.first(),currentProfile)', "read personal locals")
    s = one(s, '        val groupsKey=my(Keys.customGroups,profile)',
        '        val localsKey=my(stringPreferencesKey("petalix_local_group"),profile)', "personal write key")
    s = one(s, '            val groups=decodeCustomGroups(p[groupsKey])',
        '            val groups=petalixGroups(p,profile)', "migrate existing locals")
    s = one(s, '            p[groupsKey]=encodeCustomGroups(if(existing==null)groups+group else groups.map{if(it.id==id)group else it})',
        '            p[localsKey]=encodeCustomGroups(listOf(group))', "save without altering panel locked groups")
    s = one(s, 'CustomGroup(id,"Local News",', 'CustomGroup(id,"My Locals",', "saved locals category name")
    s = one(s, '''        return id
    }

    suspend fun createCustomGroup''', '''        check(petalixLocalKeys().containsAll(keys)) { "Local channels were not saved" }
        return id
    }

    suspend fun createCustomGroup''', "verify persistence before success")
    s = one(s, '            customGroups = decodeCustomGroups(p[Keys.customGroups]),',
        '            customGroups = petalixGroups(p0,id),', "expose personal locals in guide state")
    s = one(s, '            decodeCustomGroups(v[Keys.customGroups]).forEach { g -> if (g.id !in hiddenGroups) kept += g.channelKeys }',
        '            petalixGroups(p,id).forEach { g -> if (g.id !in hiddenGroups) kept += g.channelKeys }', "keep local guide data loaded")
    p.write_text(s)

    tests = Path(work) / "app/src/test/java/com/nuvio/tv/petalix"
    tests.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(Path(__file__).with_name("PetalixLocalsPersistenceTest.kt"), tests / "PetalixLocalsPersistenceTest.kt")

    p = root / "livetv/ui/GuideRowStrip.kt"
    s = p.read_text()
    s = one(s, "import androidx.compose.foundation.Canvas", "import androidx.compose.foundation.Canvas\nimport androidx.compose.ui.draw.clipToBounds", "clip canvas import")
    s = one(s, "Canvas(Modifier.fillMaxSize()) {", "Canvas(Modifier.fillMaxSize().clipToBounds()) {", "clip row canvas")
    s = one(s, '''            if (inner > 8) drawContent(
                item, left + pad, inner, h, measurer, styles, gap,
                dotSize.toPx(), iconSize.toPx(), bell, replay, secondary, onSecondary
            )
            item.progress?.let { f -> drawProgress(left, w, h, r, stroke, f, secondary) }''',
        '''            val tile = Path().apply { addRoundRect(RoundRect(left, 0f, left + w, h, CornerRadius(r, r))) }
            clipPath(tile) {
                if (inner > 8) drawContent(
                    item, left + pad, inner, h, measurer, styles, gap,
                    dotSize.toPx(), iconSize.toPx(), bell, replay, secondary, onSecondary
                )
                item.progress?.let { f -> drawProgress(left, w, h, r, stroke, f, secondary) }
            }''', "keep titles inside their program cells")
    p.write_text(s)

    p = root / "livetv/ui/LiveTvGuideScreen.kt"
    s = p.read_text()
    s = one(s, "import androidx.compose.ui.draw.clip\n", "import androidx.compose.ui.draw.clip\nimport androidx.compose.ui.draw.clipToBounds\n", "clip grid import")
    s = one(s, 'label="Edit Local News"', 'label="Edit My Locals"', "locals side blade editing label")
    s = one(s, "ProgramArea(programAreaWidth, Modifier.weight(1f).fillMaxHeight())", "ProgramArea(programAreaWidth, Modifier.weight(1f).fillMaxHeight().clipToBounds())", "clip guide viewport")
    s = one(s, "val gapFill = guideSurface().copy(alpha = 0.55f)", "val gapFill = Color(0xF223344C)", "solid gap surface")
    s = one(s, "val idleFill = Color(0x6634425C)", "val idleFill = Color(0xF223344C)", "solid program surface")
    s = one(s, "Color(0xAA5B8CC1) else Color(0x77465F7D)", "Color(0xF05B8CC1) else Color(0xF223344C)", "opaque focused program")
    s = one(s, '''            if (index !in groupsListState.layoutInfo.visibleItemsInfo.map { it.index })
                runCatching { groupsListState.scrollToItem((index - 3).coerceAtLeast(0)) }''',
        '''            if (index !in groupsListState.layoutInfo.visibleItemsInfo.map { it.index })
                runCatching { groupsListState.scrollToItem(index) }''', "bring selected group into view")
    s = one(s, '''                runCatching { groupsListState.scrollToItem((index - 3).coerceAtLeast(0)) }
            }
        }
    }

    LaunchedEffect(windowStart''',
        '''                runCatching { groupsListState.scrollToItem(index) }
            }
        }
    }

    LaunchedEffect(windowStart''', "retry visible group focus")
    s = one(s, '''                    onRight = {
                        column = GuideColumn.CHANNEL
                        groupsOpen = false
                        nameFocus = settings.browseByChannelName
                        runCatching { gridFocus.requestFocus() }
                    }''',
        '''                    onRight = {
                        val target = ui.groups.firstOrNull { it.id == pendingGroupId }
                        if (target?.id == ChannelGroup.CUSTOM_PREFIX + "petalix_locals" && target.count == 0) {
                            petalixLocalsOpen = true
                        } else if (target?.locked == true) {
                            pinGroup = target
                            pinWrong = false
                        } else {
                            if (target != null && target.id != ChannelGroup.SEARCH && target.id != ui.selectedGroupId) {
                                viewModel.selectGroup(target.id)
                                row = 0
                            }
                            column = GuideColumn.CHANNEL
                            groupsOpen = false
                            nameFocus = settings.browseByChannelName
                            scope.launch {
                                androidx.compose.runtime.withFrameNanos { }
                                runCatching { gridFocus.requestFocus() }
                            }
                        }
                    }''', "right enters focused group")
    s = one(s, '"petalix-locals-guide-v11"', '"petalix-locals-guide-v11"); android.util.Log.i("PetalixGuide","petalix-guide-locals-v12"', "v12 marker")
    p.write_text(s)

    p = root / "livetv/ui/LiveTvViewModel.kt"
    s = p.read_text()
    s = one(s, '''        localGroup?.let { groups += it }
        val petalixCategories''',
        '''        // Keep My Locals visible even before setup, so it can be opened or edited later.
        groups += if (localGroup != null) localGroup.copy(title = "My Locals")
            else ChannelGroup(ChannelGroup.CUSTOM_PREFIX + "petalix_locals", "My Locals", 0)
        val petalixCategories''', "persistent My Locals entry")
    p.write_text(s)
