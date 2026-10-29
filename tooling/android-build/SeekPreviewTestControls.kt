package com.nuvio.tv.petalix

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.core.player.thumbnail.*
import com.nuvio.tv.data.local.CollectionsDataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ServiceTemplateTestEntryPoint { fun collections():CollectionsDataStore }

@Composable
fun SeekPreviewTestControls() {
    val context=LocalContext.current.applicationContext
    val scope=rememberCoroutineScope()
    val mode by remember(context){SeekThumbnailPreferences.modeFlow(context)}.collectAsState(initial=SeekThumbMode.OFF)
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("TEST FEATURES · Seek previews & service templates")
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Button(enabled=!busy,onClick={scope.launch {
                val next=when(mode){SeekThumbMode.OFF->SeekThumbMode.HD;SeekThumbMode.HD->SeekThumbMode.ALL;SeekThumbMode.ALL->SeekThumbMode.OFF}
                SeekThumbnailPreferences.setMode(context,next)
                SeekThumbnailPreferences.setPrepareMode(context,if(next==SeekThumbMode.ALL)ThumbPrepareMode.ALWAYS else ThumbPrepareMode.OFF)
                status=if(next==SeekThumbMode.OFF)"Seek previews off" else "Test a seekable movie or episode. Live TV playback is unchanged."
            }}) { Text("Seek previews: "+when(mode){SeekThumbMode.OFF->"Off";SeekThumbMode.HD->"HD only";SeekThumbMode.ALL->"HD + 4K"}) }
            Button(enabled=!busy,onClick={scope.launch {
                busy=true
                try {
                    val store=EntryPointAccessors.fromApplication(context,ServiceTemplateTestEntryPoint::class.java).collections()
                    val imported=store.importFromJson(context.assets.open("service-templates-test.json").bufferedReader().use{it.readText()})
                    require(imported.size==1&&imported[0].id=="collection-service-templates-test-v1")
                    val current=store.getCurrentCollections().filterNot{it.id=="collection-service-templates-test-v1"}
                    store.setCollections(current+imported)
                    status="Open Service Templates Test from Home or Collections. Your existing service tiles are preserved."
                }catch(error:Exception){status="The test collection could not be loaded. Existing collections were preserved."}finally{busy=false}
            }}) { Text("Load service templates") }
            Button(enabled=!busy,onClick={scope.launch {
                busy=true
                try { status=if(ThumbnailCache.clearAll(context))"Preview cache cleared" else "Some preview files could not be removed yet" }finally{busy=false}
            }}) { Text("Clear preview cache") }
        }
        Text(if(status.isBlank())"Opt-in TV test. Start with HD only on Fire TV or onn. Preview files are removed after playback." else status)
    }
}
