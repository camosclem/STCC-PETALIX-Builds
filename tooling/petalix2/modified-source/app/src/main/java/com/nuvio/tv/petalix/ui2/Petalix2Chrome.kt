package com.nuvio.tv.petalix.ui2

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.LocalOpenSidebar
import com.nuvio.tv.LocalSidebarExpanded
import com.nuvio.tv.R
import com.nuvio.tv.ui.navigation.NuvioNavHost
import com.nuvio.tv.ui.navigation.Screen

val PetalixBlue = Color(0xFF087CFF)
val PetalixBackground = Color(0xFF020912)
class PetalixChromeState {
    var visible by mutableStateOf(true)
    var hasFocus by mutableStateOf(false)
    val navigationFocus = FocusRequester()
    fun open() { visible = true }
}
val LocalPetalixChrome = staticCompositionLocalOf { PetalixChromeState() }

data class PetalixTab(val label:String, val route:String, val icon:ImageVector)
val petalixTabs = listOf(
    PetalixTab("Home", "home", Icons.Default.Home),
    PetalixTab("Live TV", "live_tv", Icons.Default.LiveTv),
    PetalixTab("Movies", "petalix_movies", Icons.Default.Movie),
    PetalixTab("TV Shows", "petalix_series", Icons.Default.Tv),
    PetalixTab("Search", "search", Icons.Default.Search),
    PetalixTab("Settings", "settings", Icons.Default.Settings)
)

@Composable
fun Petalix2Scaffold(navController:NavHostController, startDestination:String, currentRoute:String?, onExitApp:()->Unit) {
    val chrome = remember { PetalixChromeState() }
    val contentFocus = remember { FocusRequester() }
    val contentScroll = remember { object:NestedScrollConnection {
        override fun onPostScroll(consumed:Offset,available:Offset,source:NestedScrollSource):Offset {
            if(consumed.y < -4f)chrome.visible=false
            if(consumed.y > 4f)chrome.visible=true
            return Offset.Zero
        }
    } }
    val root = currentRoute in petalixTabs.map { it.route } || currentRoute in listOf("on_demand", "library", "discover", "sports", "recordings")
    val fullScreen by com.nuvio.tv.livetv.ui.LiveTvFullscreen.active.collectAsState()
    val showNavigation = root && !fullScreen
    LaunchedEffect(currentRoute) { chrome.visible=true; chrome.hasFocus=false }
    LaunchedEffect(chrome.visible,chrome.hasFocus) {
        if (chrome.visible && chrome.hasFocus) {
            withFrameNanos { }; runCatching { chrome.navigationFocus.requestFocus() }
        }
    }
    BackHandler(enabled=showNavigation) {
        if (!chrome.hasFocus) { chrome.open(); chrome.hasFocus=true }
        else if(currentRoute != Screen.Home.route) navController.navigate(Screen.Home.route) { popUpTo(navController.graph.startDestinationId) { saveState=true }; launchSingleTop=true; restoreState=true }
        else onExitApp()
    }
    CompositionLocalProvider(LocalPetalixChrome provides chrome, LocalSidebarExpanded provides false,
        LocalContentFocusRequester provides contentFocus,
        LocalOpenSidebar provides { chrome.open(); chrome.hasFocus=true }) {
        Column(Modifier.fillMaxSize().background(PetalixBackground)) {
            AnimatedVisibility(visible=showNavigation && chrome.visible) {
                Row(Modifier.fillMaxWidth().height(56.dp).background(Brush.verticalGradient(listOf(Color(0xFF061226),PetalixBackground))).padding(horizontal=24.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    PetalixReferenceLogo(Modifier.width(182.dp).height(44.dp))
                    Spacer(Modifier.width(20.dp))
                    petalixTabs.filter { it.route!="settings" || !com.nuvio.tv.petalix.ManagedPolicy.state.value.hideSettings }.forEach { tab ->
                        val selected = currentRoute==tab.route || (currentRoute=="on_demand" && tab.route=="petalix_movies") || (currentRoute !in petalixTabs.map { it.route } && currentRoute!="on_demand" && tab.route=="home")
                        PetalixAction(tab.label,tab.icon,selected,
                            Modifier.then(if(selected)Modifier.focusRequester(chrome.navigationFocus) else Modifier)
                                .onFocusChanged { if(it.isFocused) chrome.hasFocus=true }
                                .onPreviewKeyEvent { if(it.type==KeyEventType.KeyDown && it.key==Key.DirectionDown) { chrome.hasFocus=false;runCatching { contentFocus.requestFocus() }.isSuccess } else false }
                        ) { navController.navigate(tab.route) { popUpTo(navController.graph.startDestinationId) { saveState=true };launchSingleTop=true;restoreState=true } }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().nestedScroll(contentScroll).focusRequester(contentFocus).onFocusChanged { if(it.hasFocus) chrome.hasFocus=false }.onKeyEvent {
                if(showNavigation && it.type==KeyEventType.KeyDown && it.key==Key.DirectionUp) { chrome.open();chrome.hasFocus=true;true } else false
            }) { NuvioNavHost(navController=navController,startDestination=startDestination,hideBuiltInHeaders=true) }
        }
    }
}

@Composable
fun PetalixAction(label:String,icon:ImageVector?=null,selected:Boolean=false,modifier:Modifier=Modifier,accent:Color=PetalixBlue,onClick:()->Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(modifier.onFocusChanged { focused=it.isFocused }.background(if(focused || selected) accent.copy(alpha=0.28f) else Color.Transparent,RoundedCornerShape(18.dp))
        .border(if(focused)2.dp else 1.dp,if(focused)accent else if(selected)accent.copy(alpha=.7f) else Color.Transparent,RoundedCornerShape(18.dp))
        .clickable(onClick=onClick).padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        if(icon!=null)Icon(icon,null,Modifier.size(18.dp),tint=Color.White)
        Text(label,color=Color.White,fontSize=13.sp,maxLines=1)
    }
}
