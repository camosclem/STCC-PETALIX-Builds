"""Petalix-only follow-up: accessible locals, service identity, and guide focus."""
from pathlib import Path
import re

def replace_one(text, old, new, label):
    if text.count(old) != 1:
        raise RuntimeError("Petalix user fixes anchor changed: " + label)
    return text.replace(old, new)

def apply(work):
    root=Path(work)/"app/src/main/java/com/nuvio/tv"
    ui=root/"petalix/ui2"
    p=ui/"Petalix2Home.kt";s=p.read_text()
    s=replace_one(s,'"Local TV","Lifestyle"','"Local News","Lifestyle"',"home local category")
    s=replace_one(s,'"Local TV" to listOf("local","locals")','"Local News" to listOf("local","locals","news")',"category terms")
    a=s.index('@Composable\nfun ServiceArtwork(')
    b=s.index('\n@Composable\nfun PetalixReferenceLogo',a)
    s=s[:a]+'''@Composable
fun ServiceArtwork(id:String,modifier:Modifier=Modifier) {
    val logo=ArvioServiceMedia.logo(id)
    if(logo!=null) {
        AsyncImage(logo,id,modifier.padding(10.dp),contentScale=ContentScale.Fit)
    } else {
        Box(modifier,contentAlignment=Alignment.Center) {
            Text(if(id=="apple")"Apple TV" else id.replaceFirstChar{it.uppercase()},color=Color.White,
                fontWeight=FontWeight.Bold,fontSize=24.sp,maxLines=1)
        }
    }
}
'''+s[b:]
    p.write_text(s)
    p=ui/"Petalix2Services.kt";s=p.read_text()
    s=replace_one(s,'PetalixService("apple","Apple TV+"','PetalixService("apple","Apple TV"',"current Apple TV name")
    # The saved service catalog still uses its legacy folder key. Keep that lookup
    # until the catalog is migrated; only the customer-facing name changes here.
    p.write_text(s)
    p=ui/"PetalixServiceLayouts.kt";s=p.read_text()
    s=replace_one(s,'android.util.Log.i("PetalixServices","petalix-service-experience-v4")',
        'android.util.Log.i("PetalixServices","petalix-service-experience-v4"); android.util.Log.i("PetalixServices","petalix-user-fixes-v9")',
        'verified build marker')
    s=replace_one(s,'''            if(!search && state.more && !state.loading) PetalixAction("Load more ${service.name}",accent=service.accent,
                modifier=Modifier.padding(start=28.dp,top=4.dp,bottom=4.dp)){viewModel.load(true)}
''','',"remove nonfunctional more control")
    s=s.replace('eyebrow="APPLE TV+"','eyebrow="APPLE TV"')
    s=replace_one(s,'''    Crossfade(p.hero?.backdropUrl,modifier=modifier,label="${p.service.id}FeatureArtwork") { art ->
        AsyncImage(art,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
    }''','''    Box(modifier.background(p.service.background)) {
        Crossfade(p.hero?.backdropUrl,label="${p.service.id}FeatureArtwork") { art ->
            AsyncImage(art,null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
        }
    }''',"show entire feature art")
    p.write_text(s)
    p=ui/"PetalixLocalChannels.kt";s=p.read_text()
    s=s.replace("My Locals","Local News")
    s=replace_one(s,
        '(ch.name.contains(city,true) || ch.tvgName?.contains(city,true)==true) &&',
        '(ch.name.contains(city,true) || ch.tvgName?.contains(city,true)==true || ch.group.contains("local",true) || ch.group.contains("news",true)) &&',
        "playlist local groups")
    p.write_text(s)
    p=root/"livetv/data/LiveTvPreferences.kt";s=p.read_text()
    s=replace_one(s,'it.name.equals("My Locals",true)}',
        'it.name.equals("My Locals",true) || it.name.equals("Local News",true)}',"migrate old local group")
    s=replace_one(s,'CustomGroup(id,"My Locals",','CustomGroup(id,"Local News",',"rename local group")
    p.write_text(s)
    p=root/"livetv/ui/LiveTvGuideScreen.kt";s=p.read_text()
    s=replace_one(s,
        'val matches=current.groups.firstOrNull { it.id=="__petalix__:"+requested }',
        'val matches=current.groups.firstOrNull { if(requested=="Local News") it.id==ChannelGroup.CUSTOM_PREFIX+"petalix_locals" || it.title.equals("Local News",true) || it.title.equals("My Locals",true) else it.id=="__petalix__:"+requested }',
        "home local launch")
    s=replace_one(s,'if(matches!=null)viewModel.selectGroup(matches.id)',
        'if(matches!=null && (requested!="Local News" || matches.count>0))viewModel.selectGroup(matches.id) else if(requested=="Local News")petalixLocalsOpen=true',
        "missing locals setup")
    s=s.replace('group.title.equals("My Locals",ignoreCase=true)',
        '(group.title.equals("My Locals",ignoreCase=true) || group.title.equals("Local News",ignoreCase=true))')
    s=re.sub(r'(?<![A-Za-z])g\.title\.equals\("My Locals",ignoreCase=true\)',
        '(g.title.equals("My Locals",ignoreCase=true) || g.title.equals("Local News",ignoreCase=true))',s)
    s=replace_one(s,'import androidx.compose.foundation.lazy.rememberLazyListState',
        'import androidx.compose.foundation.lazy.rememberLazyListState\nimport androidx.compose.ui.focus.focusRestorer',
        'focus restoration import')
    s=replace_one(s,'                    onGroupMenu = { g ->','                    onEditLocals = { petalixLocalsOpen=true },\n                    onGroupMenu = { g ->',"guide locals action")
    s=replace_one(s,'    onGroupMenu: (ChannelGroup) -> Unit,\n    onRight: () -> Unit,',
        '    onGroupMenu: (ChannelGroup) -> Unit,\n    onEditLocals: () -> Unit,\n    onRight: () -> Unit,',"guide rail action signature")
    s=replace_one(s,'        // Search sits above the groups',
        '''        GroupSearchButton(modifier=Modifier.padding(bottom=4.dp),onClick=onEditLocals,label="Edit Local News")
        // Search sits above the groups''',"persistent local button")
    s=replace_one(s,'private fun GroupSearchButton(modifier: Modifier, onClick: () -> Unit) {',
        'private fun GroupSearchButton(modifier: Modifier, onClick: () -> Unit, label:String="Search") {',"rail button label")
    s=replace_one(s,'LiveText("Search", color = colors.text, size = gsp(13f), weight = FontWeight.Medium)',
        'LiveText(label, color = colors.text, size = gsp(13f), weight = FontWeight.Medium)',"rail button text")
    # Focus remains on the category being scrolled; OK chooses it. No delayed group
    # switch to replace the list under the remote's current focused item.
    a=s.index('    // Moving through the group column shows that group straight away')
    b=s.index('    // Opening Live TV (or coming back from full screen):',a)
    s=s[:a]+s[b:]
    s=replace_one(s,
        'val index=ui.groups.indexOfFirst { it.id==ui.selectedGroupId }.coerceAtLeast(0)',
        'val index=groupListIndex(ui.groups,ui.selectedGroupId,settings.groupPlaylistHeadings,collapsedPlaylists).coerceAtLeast(0)',
        "focus actual group row")
    s=replace_one(s,'modifier = Modifier.fillMaxSize(),\n            // Each channel row has 2dp above its tile',
        'modifier = Modifier.fillMaxSize().focusRestorer(),\n            // Each channel row has 2dp above its tile',
        "restore rail focus")
    s=replace_one(s,'''        coil3.compose.AsyncImage(model=headerPoster ?: headerBlock?.program?.icon,contentDescription=null,
            modifier=Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop,alpha=.50f)
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(Color(0x55101D32),Color(0x77081020)))))''',
        '''        val guideTint by androidx.compose.animation.animateColorAsState(
            if(ui.selectedGroupId.contains("sport",true))Color(0xFF103D4A)
            else if(ui.selectedGroupId.contains("news",true))Color(0xFF173B63)
            else Color(0xFF253D61),label="guideAmbient")
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(guideTint.copy(alpha=.65f),Color(0xCC081020)))))''',
        "replace still guide poster")
    p.write_text(s)
