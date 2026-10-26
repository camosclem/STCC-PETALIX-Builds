"""Reapply the reviewed customer-policy edits to the reorganized upstream UI."""
import subprocess

GUIDE='app/src/main/java/com/nuvio/tv/livetv/ui/LiveTvGuideScreen.kt'
SETTINGS='app/src/main/java/com/nuvio/tv/ui/screens/settings/LiveTvSettingsContent.kt'

def replace(text,old,new):
    if text.count(old)!=1:
        raise RuntimeError('The upstream customer-policy binding needs review: '+old[:80])
    return text.replace(old,new,1)

def legacy_custom(text,path):
    if path==GUIDE:
        text=replace(text,'action = "Press OK to open Live TV settings"','action = if(com.nuvio.tv.petalix.ManagedPolicy.state.value.hideSettings) "Contact your provider to finish setting up this TV" else "Press OK to open Live TV settings"')
        start=text.index('private fun ChannelContextMenu(')
        end=text.index('\n}\n',start)+2
        prefix,menu,suffix=text[:start],text[start:end],text[end:]
        menu=replace(menu,'    val first = remember { FocusRequester() }','    val customerPolicy by com.nuvio.tv.petalix.ManagedPolicy.state.collectAsStateWithLifecycle()\n    val first = remember { FocusRequester() }')
        menu=replace(menu,'            item { MenuItem("Add to group…", onClick = onAddToGroup) }','            if (!customerPolicy.lockSettings) {\n            item { MenuItem("Add to group…", onClick = onAddToGroup) }')
        menu=replace(menu,'            item { MenuItem("Update playlists & EPG", onClick = onRefresh) }','            }\n            item { MenuItem("Update playlists & EPG", onClick = onRefresh) }')
        menu=replace(menu,'            item { MenuItem("Live TV settings", onClick = onSettings) }','            if (!customerPolicy.hideSettings) item { MenuItem("Live TV settings", onClick = onSettings) }')
        return prefix+menu+suffix
    text=replace(text,'    val playlists by viewModel.playlists.collectAsStateWithLifecycle()','    val managementPolicy by com.nuvio.tv.petalix.ManagedPolicy.state.collectAsStateWithLifecycle()\n    val playlists by viewModel.playlists.collectAsStateWithLifecycle()')
    for key,next_section in [('drive','Playlists & Guides'),('sources','Channels')]:
        text=replace(text,'            item(key = "'+key+'") {','            if (!managementPolicy.lockSources) { item(key = "'+key+'") {')
        marker='\n            }\n\n            // ------------------------------------------------------------ '+next_section
        text=replace(text,marker,marker.replace('\n            }\n','\n            } }\n',1))
    return text

def current_custom(text,path):
    if path==SETTINGS:
        text=replace(text,'    val playlists by viewModel.playlists.collectAsStateWithLifecycle()','    val managementPolicy by com.nuvio.tv.petalix.ManagedPolicy.state.collectAsStateWithLifecycle()\n    val playlists by viewModel.playlists.collectAsStateWithLifecycle()')
        for key in ['drive','sources']:
            old='if (show(LiveTvPage.MAIN)) item(key = "'+key+'")'
            text=replace(text,old,'if (!managementPolicy.lockSources && show(LiveTvPage.MAIN)) item(key = "'+key+'")')
        return text
    text=legacy_custom(text,path)
    # Keep the new Guide heading visible with Refresh; editing still follows panel policy.
    text=replace(text,'            item { MenuHeader("Guide") }\n            }','            }\n            item { MenuHeader("Guide") }')
    return replace(text,'            item { MenuItem("Guide display (info panel, preview…)", onClick = onGuideDisplay) }','            if (!customerPolicy.lockSettings) item { MenuItem("Guide display (info panel, preview…)", onClick = onGuideDisplay) }')

def resolve(work,path):
    if path not in [GUIDE,SETTINGS]:return False
    def stage(number):
        return subprocess.run(['git','-C',str(work),'show',':'+str(number)+':'+path],check=True,capture_output=True,text=True).stdout
    base,custom=stage(1),stage(3)
    if legacy_custom(base,path).rstrip()!=custom.rstrip():
        raise RuntimeError('Unreviewed managed changes remain in '+path)
    upstream=stage(2)
    (work/path).write_text(current_custom(upstream,path))
    subprocess.run(['git','-C',str(work),'add','--',path],check=True)
    return True
