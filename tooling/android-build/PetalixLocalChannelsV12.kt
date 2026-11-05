package com.nuvio.tv.petalix.ui2

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.livetv.data.LiveTvPreferences
import com.nuvio.tv.livetv.model.LiveChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*
import javax.inject.Inject

data class NearbyStation(val call:String,val city:String,val state:String,val miles:Int)
data class LocalChannelMatch(val channel:LiveChannel,val station:NearbyStation,val market:String)
data class MarketChoice(val name:String,val miles:Int,val affiliates:Int,val matches:Int)
data class LocalLookup(val zip:String="",val location:String="",val stations:List<NearbyStation> = emptyList(),
    val loading:Boolean=false,val saving:Boolean=false,val error:String?=null,val saved:Int?=null,val groupId:String?=null,
    val savedKeys:List<String> = emptyList())

/** Station coordinates suggest nearby candidates, not DMA membership or reception rights. */
object LocalStationMatcher {
    private val callToken=Regex("(?i)(?<![A-Z0-9])[WK][A-Z]{2,4}(?:[- .]?(?:TV|DT|LD|CD|LP))?(?![A-Z])")
    private val affiliate=Regex("(?i)(?:^|[^A-Z0-9])(ABC|NBC|CBS|FOX)(?:$|[^A-Z0-9])")
    fun canonical(value:String)=value.uppercase().replace(Regex("[- .](TV|DT|LD|CD|LP)$"),"")
    fun network(channel:LiveChannel):String?=affiliate.find(listOfNotNull(channel.name,channel.tvgName).joinToString(" "))?.groupValues?.get(1)?.uppercase()
    private fun marketFromName(name:String):String?=name.split('|').dropLast(1).lastOrNull()?.trim()?.takeIf{
        it.length>2 && !it.equals("USA",true) && !it.equals("LOCAL",true)
    }
    fun playlistMarket(channel:LiveChannel):String? {
        val parts=channel.name.split('|').map{it.trim()}
        val city=marketFromName(channel.name) ?: return null
        val state=parts.getOrNull(parts.size-3)?.takeIf{it.length==2 && it.all(Char::isLetter)} ?: return null
        return "$city, ${state.uppercase()}"
    }
    fun market(channel:LiveChannel,station:NearbyStation):String="${marketFromName(channel.name) ?: station.city}, ${station.state}"
    fun matches(channels:List<LiveChannel>,stations:List<NearbyStation>):List<LocalChannelMatch> {
        val byCall=stations.associateBy{canonical(it.call)}
        return channels.mapNotNull{ch->
            val text=listOfNotNull(ch.name,ch.tvgName,ch.tvgId).joinToString(" ")
            val exact=callToken.findAll(text).map{canonical(it.value)}.firstNotNullOfOrNull{byCall[it]}
            exact?.let{LocalChannelMatch(ch,it,market(ch,it))}
        }.distinctBy{it.channel.key}.sortedWith(compareBy({it.station.miles},{it.station.call},{it.channel.name}))
    }
    /** Suggest playable playlist entries by nearby callsign or explicit market and network label. */
    fun suggestions(channels:List<LiveChannel>,matches:List<LocalChannelMatch>,market:String):List<LiveChannel> {
        val exact=matches.filter{it.market.equals(market,true)}
        val named=channels.filter{it.group.contains("local",true) && playlistMarket(it)?.equals(market,true)==true}
        val available=(exact.map{it.channel}+named).distinctBy{it.key}
        return listOf("ABC","NBC","CBS","FOX").mapNotNull { affiliateName ->
            available.asSequence().filter{network(it)==affiliateName}
                .minWithOrNull(compareBy<LiveChannel>({ ch -> exact.firstOrNull{it.channel.key==ch.key}?.station?.miles ?: Int.MAX_VALUE },{it.number}))
        }
    }
    fun miles(lat1:Double,lon1:Double,lat2:Double,lon2:Double):Int {
        val a=sin(Math.toRadians(lat2-lat1)/2).pow(2)+cos(Math.toRadians(lat1))*cos(Math.toRadians(lat2))*sin(Math.toRadians(lon2-lon1)/2).pow(2)
        return (3958.7613*2*atan2(sqrt(a.coerceIn(0.0,1.0)),sqrt((1-a).coerceIn(0.0,1.0)))).roundToInt()
    }
}
@HiltViewModel
class PetalixLocalChannelsViewModel @Inject constructor(@ApplicationContext private val context:Context,
    private val prefs:LiveTvPreferences):ViewModel() {
    private val _state=MutableStateFlow(LocalLookup())
    val state=_state.asStateFlow()
    private var job:Job?=null
    init{android.util.Log.i("PetalixLocals","petalix-locals-v12");viewModelScope.launch{
        _state.value=_state.value.copy(zip=prefs.petalixLocalZip(),savedKeys=prefs.petalixLocalKeys())
    }}
    fun lookup(zip:String) {
        if(!zip.matches(Regex("[0-9]{5}"))){_state.value=_state.value.copy(error="Enter a five-digit US ZIP code.");return}
        job?.cancel()
        _state.value=_state.value.copy(zip=zip,location="",stations=emptyList(),loading=true,error=null,
            savedKeys=if(_state.value.zip==zip)_state.value.savedKeys else emptyList())
        job=viewModelScope.launch {
            try {
                val result=withContext(Dispatchers.IO) {
                    val connection=URL("https://api.zippopotam.us/us/$zip").openConnection() as HttpURLConnection
                    connection.connectTimeout=12000;connection.readTimeout=12000
                    val json=try {
                        check(connection.responseCode==200){if(connection.responseCode==404)"ZIP code not found." else "ZIP lookup is unavailable. Please retry."}
                        connection.inputStream.bufferedReader().use{JSONObject(it.readText())}
                    }finally{connection.disconnect()}
                    val place=json.getJSONArray("places").getJSONObject(0)
                    val lat=place.getString("latitude").toDouble();val lon=place.getString("longitude").toDouble()
                    val data=context.assets.open("petalix2/local-stations.json").bufferedReader().use{JSONObject(it.readText())}.getJSONArray("stations")
                    val stations=(0 until data.length()).map{data.getJSONObject(it)}.mapNotNull{station->
                        val distance=LocalStationMatcher.miles(lat,lon,station.getDouble("lat"),station.getDouble("lon"))
                        if(distance<=90)NearbyStation(station.getString("call"),station.getString("city"),station.getString("state"),distance) else null
                    }.distinctBy{it.call}.sortedBy{it.miles}
                    LocalLookup(zip,"${place.getString("place name")}, ${place.getString("state abbreviation")}",stations)
                }
                ensureActive();_state.value=result.copy(savedKeys=if(prefs.petalixLocalZip()==zip)prefs.petalixLocalKeys() else emptyList())
            }catch(cancel:CancellationException){throw cancel}
             catch(error:Exception){_state.value=LocalLookup(zip=zip,error=error.message ?: "Local channel lookup failed. Please retry.")}
        }
    }
    fun save(zip:String,keys:List<String>,onSaved:(String)->Unit={}) {
        if(_state.value.saving)return
        if(keys.isEmpty()) {_state.value=_state.value.copy(error="Select at least one channel from your playlist before saving.");return}
        _state.value=_state.value.copy(saving=true,error=null)
        viewModelScope.launch {
            try {val group=prefs.petalixSaveLocals(zip,keys);_state.value=_state.value.copy(saving=false,saved=keys.distinct().size,groupId=group,savedKeys=prefs.petalixLocalKeys());onSaved(group)}
            catch(cancel:CancellationException){throw cancel}
            catch(error:Exception){_state.value=_state.value.copy(saving=false,error="Could not save My Locals. Please retry.")}
        }
    }
}
@Composable
fun PetalixLocalChannelsDialog(channels:List<LiveChannel>,onClose:()->Unit,onOpenGroup:(String)->Unit={},onboarding:Boolean=false,onCompleted:()->Unit={},viewModel:PetalixLocalChannelsViewModel=hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var zip by rememberSaveable{mutableStateOf("")}
    var selected by remember{mutableStateOf(setOf<String>())}
    var showAll by remember{mutableStateOf(false)}
    var market by rememberSaveable{mutableStateOf("")}
    var autoSavedZip by rememberSaveable{mutableStateOf("")}
    var stationSearch by rememberSaveable{mutableStateOf("")}
    val lookupFocus=remember{FocusRequester()}
    val keyboard=LocalSoftwareKeyboardController.current
    val focusManager=LocalFocusManager.current
    fun findLocals() {
        keyboard?.hide()
        focusManager.clearFocus()
        runCatching{lookupFocus.requestFocus()}
        if(!state.loading)viewModel.lookup(zip)
    }
    // Entering the category must not open the keyboard over the saved stations.
    LaunchedEffect(Unit){withFrameNanos{};runCatching{lookupFocus.requestFocus()}}
    LaunchedEffect(state.zip){if(zip.isBlank())zip=state.zip}
    LaunchedEffect(state.location){selected=emptySet();showAll=false;market="";stationSearch=""}
    LaunchedEffect(state.zip,channels.size) {
        if(state.zip.matches(Regex("[0-9]{5}")) && channels.isNotEmpty() && state.location.isBlank() && !state.loading && state.error==null)
            viewModel.lookup(state.zip)
    }
    val matches=remember(channels,state.stations){LocalStationMatcher.matches(channels,state.stations)}
    val markets=remember(state.stations,matches){
        val grouped=matches.groupBy{it.market}
        (grouped.keys+state.stations.map{"${it.city}, ${it.state}"}).distinctBy{it.lowercase()}.map { name ->
            val affiliateCount=grouped[name].orEmpty().mapNotNull{LocalStationMatcher.network(it.channel)}.distinct().size
            val count=grouped[name].orEmpty().size
            val distance=(grouped[name].orEmpty().map{it.station.miles}+
                state.stations.filter{"${it.city}, ${it.state}"==name}.map{it.miles}).minOrNull() ?: Int.MAX_VALUE
            MarketChoice(name,distance,affiliateCount,count)
        }.sortedWith(compareByDescending<MarketChoice>{it.affiliates}.thenByDescending{it.matches}.thenBy{it.miles}).take(12)
    }
    LaunchedEffect(state.location,markets){if(state.location.isNotBlank() && market.isBlank())market=markets.firstOrNull()?.name.orEmpty()}
    // Finding a ZIP must create a usable guide group. Save only real playlist entries,
    // and only once per lookup; changing the market chip remains an explicit edit.
    LaunchedEffect(state.zip,state.location,market,channels,matches,state.savedKeys) {
        if(state.location.isNotBlank() && market.isNotBlank() && state.savedKeys.isEmpty() &&
            autoSavedZip!=state.zip && !state.saving) {
            val initial=LocalStationMatcher.suggestions(channels,matches,market).map{it.key}
            if(initial.isNotEmpty()) {
                autoSavedZip=state.zip
                viewModel.save(state.zip,initial)
            }
        }
    }
    val marketMatches=matches.filter{market.isBlank() || it.market==market}
    LaunchedEffect(state.location,market,matches,state.savedKeys){
        if(state.location.isNotBlank() && market.isNotBlank()) {
            val savedHere=channels.filter{it.key in state.savedKeys && (
                LocalStationMatcher.playlistMarket(it)?.equals(market,true)==true ||
                    matches.any{m->m.channel.key==it.key && m.market.equals(market,true)}
            )}.map{it.key}
            selected=if(savedHere.isNotEmpty())savedHere.toSet()
                else LocalStationMatcher.suggestions(channels,matches,market).map{it.key}.toSet()
        }
    }
    val query=stationSearch.trim()
    val filteredMatches=(if(query.isBlank())marketMatches else matches).filter{
        query.isBlank() || listOf(it.station.call,it.station.city,it.station.state,it.channel.name).any{v->v.contains(query,true)}
    }
    val city=market.substringBefore(", ")
    val namedForMarket=if(city.isBlank())emptyList() else channels.filter{ch->
        (ch.name.contains(city,true) || ch.tvgName?.contains(city,true)==true) && ch.group.contains("local",true) &&
            matches.none{it.channel.key==ch.key} &&
            (query.isBlank() || ch.name.contains(query,true) || city.contains(query,true))
    }
    val choices=if(showAll)channels.filter{query.isBlank() || it.name.contains(query,true)}.sortedBy{it.name}
        else (filteredMatches.map{it.channel}+namedForMarket).distinctBy{it.key}
    val allowed=channels.map{it.key}.toSet()
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxWidth(.88f).fillMaxHeight(.88f).background(Color(0xFF081321),RoundedCornerShape(18.dp)).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Your local channels",color=Color.White,fontSize=26.sp,modifier=Modifier.weight(1f))
                PetalixAction(if(onboarding)"Skip for now" else "Close",onClick=onClose)
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(zip,{zip=it.filter(Char::isDigit).take(5)},singleLine=true,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Search),
                    keyboardActions=KeyboardActions(onSearch={findLocals()},onDone={findLocals()}),
                    label={androidx.compose.material3.Text("US ZIP code")},
                    modifier=Modifier.width(190.dp),
                    colors=androidx.compose.material3.TextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedContainerColor=Color(0xFF102238),unfocusedContainerColor=Color(0xFF102238)))
                PetalixAction(if(state.loading)"Looking up…" else "Find locals",modifier=Modifier.focusRequester(lookupFocus)){findLocals()}
                if(state.location.isNotBlank())Text(state.location,color=Color(0xFF8EBEFF),fontSize=14.sp)
            }
            if(onboarding)Text("Welcome! Enter your ZIP to find channels near you. You can change this later in Live TV.",color=Color.White,fontSize=14.sp)
            Text("Enter your ZIP, confirm the suggested ABC, NBC, CBS and FOX stations, then save. Only channels in your playlist can play.",color=Color.LightGray,fontSize=14.sp)
            state.error?.let{Text(it,color=Color(0xFFFFA8A8),fontSize=13.sp)}
            if(state.saved!=null)Text("${state.saved} playable channels added to My Locals in the Live TV guide. You can change this selection here.",color=Color(0xFF81E7BB),fontSize=14.sp)
            if(markets.isNotEmpty()) {
                Text("NEARBY MARKETS",color=Color(0xFF8EBEFF),fontSize=12.sp)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(markets,key={it.name}){entry->
                        PetalixAction("${entry.name} • ${entry.miles} mi",selected=market==entry.name){market=entry.name;showAll=false}
                    }
                }
            }
            if(state.location.isNotBlank()) {
                OutlinedTextField(stationSearch,{stationSearch=it},singleLine=true,
                    label={androidx.compose.material3.Text("Search station, city or call sign")},modifier=Modifier.fillMaxWidth(),
                    colors=androidx.compose.material3.TextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,
                        focusedContainerColor=Color(0xFF102238),unfocusedContainerColor=Color(0xFF102238)))
                Text("${choices.size} playlist channels for this market • ${selected.size} selected" +
                    if(choices.isEmpty()) " • Search or choose from your playlist below" else "",color=Color(0xFF9FB3CC),fontSize=12.sp)
            }
            if(state.location.isNotBlank())Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                PetalixAction(if(showAll)"Show market stations" else "Choose manually from playlist"){showAll=!showAll}
                PetalixAction("Select displayed"){selected=selected+choices.map{it.key}}
                PetalixAction("Clear selection"){selected=emptySet()}
            }
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                if(channels.isEmpty())item{Text("Load a playlist before adding local channels.",color=Color.LightGray)}
                else if(choices.isEmpty() && !state.loading && state.location.isNotBlank())item {
                    Text("No stations matched this market. Search by call sign or choose manually from your playlist.",color=Color.LightGray)
                }
                items(choices,key={it.key}){ch->
                    val station=matches.firstOrNull{it.channel.key==ch.key}?.station
                    PetalixTile(Modifier.fillMaxWidth().height(66.dp),onClick={
                        selected=if(ch.key in selected)selected-ch.key else selected+ch.key
                    }) {
                        Row(Modifier.fillMaxSize().padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.size(45.dp).clip(CircleShape).background(Color.White.copy(alpha=.13f)),contentAlignment=Alignment.Center) {
                                if(!ch.logo.isNullOrBlank())AsyncImage(ch.logo,null,Modifier.size(37.dp),contentScale=ContentScale.Fit)
                                else Text(station?.call?.take(4) ?: "TV",color=Color.White,fontSize=12.sp)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(ch.name,color=Color.White,fontSize=16.sp,maxLines=1)
                                Text(station?.let{"${it.call} • ${it.city}, ${it.state}"} ?: "From your playlist",color=Color(0xFF9FB3CC),fontSize=11.sp)
                            }
                            Text(if(ch.key in selected)"✓" else "+",color=if(ch.key in selected)Color(0xFF61ADFF) else Color.White,fontSize=24.sp)
                        }
                    }
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                PetalixAction(if(state.saving)"Saving…" else "Save and open My Locals • ${selected.size}"){
                    if(!state.saving) {
                        if(state.location.isBlank() || zip!=state.zip)viewModel.lookup(zip)
                        else viewModel.save(state.zip,selected.filter{it in allowed}){group->
                            keyboard?.hide()
                            onOpenGroup(group)
                        }
                    }
                }
                state.groupId?.let {group->PetalixAction("Open My Locals"){onOpenGroup(group)}}
                Text("Station metadata: FCC public mirror (2021). ZIP coordinates: Zippopotam.us.",color=Color.Gray,fontSize=10.sp)
            }
        }
    }
}
