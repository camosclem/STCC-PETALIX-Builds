package com.nuvio.tv.petalix

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.MainActivity
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PetalixLocalsActivity : ComponentActivity() {
    @Inject lateinit var manager: PetalixManagedCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val setup = intent.getBooleanExtra("setup", false)
        setContent {
            NuvioTheme {
                LocalsSetup(manager, setup) {
                    if (setup) {
                        manager.dismissLocalsPrompt()
                        startActivity(Intent(this, MainActivity::class.java).putExtra("skipLocalsOnce", true))
                    }
                    finish()
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LocalsSetup(manager: PetalixManagedCoordinator, setup: Boolean, onDone: () -> Unit) {
    var zip by rememberSaveable { mutableStateOf(manager.localsZip) }
    var result by remember { mutableStateOf<PetalixLocalStations.Result?>(null) }
    var matchedZip by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var showAll by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val zipFocus = remember { FocusRequester() }
    val colors = NuvioTheme.colors
    BackHandler { if (!saving) onDone() }
    LaunchedEffect(Unit) { zipFocus.requestFocus() }

    fun findStations() {
        if (busy || !zip.matches(Regex("[0-9]{5}"))) return
        val requestedZip = zip
        busy = true
        message = "Finding available local stations…"
        scope.launch {
            try {
                val found = manager.lookupLocals(requestedZip)
                val prior = if (requestedZip == manager.localsZip) manager.localKeys() else emptyList()
                val availableKeys = found.available.map { it.key }.toSet()
                selected = prior.filter { it in availableKeys }.toSet().ifEmpty {
                    found.nearby.map { it.key }.toSet()
                }
                matchedZip = requestedZip
                result = found
                showAll = found.nearby.isEmpty()
                message = when {
                    found.available.isEmpty() -> "Your playlist has no local stations available yet. Let it finish loading, then try again."
                    found.nearby.isEmpty() -> "No nearby matches were found. Choose your stations from the available list below."
                    else -> null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: "Couldn’t find stations. Please try again."
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(colors.Background).padding(horizontal = 44.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Choose your local channels", color = colors.TextPrimary, fontSize = 26.sp)
        Text("Enter your ZIP code. We’ll suggest stations available through your service.",
            color = colors.TextSecondary, fontSize = 15.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = zip,
                onValueChange = { value ->
                    val next = value.filter { it in '0'..'9' }.take(5)
                    if (next != zip) {
                        zip = next
                        result = null
                        matchedZip = null
                        selected = emptySet()
                        message = null
                    }
                },
                enabled = !busy,
                label = { androidx.compose.material3.Text("ZIP code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.TextPrimary, unfocusedTextColor = colors.TextPrimary,
                    focusedLabelColor = colors.TextPrimary, unfocusedLabelColor = colors.TextSecondary,
                    cursorColor = colors.TextPrimary, focusedBorderColor = colors.FocusRing
                ),
                modifier = Modifier.width(200.dp).focusRequester(zipFocus)
            )
            Button(onClick = ::findStations, enabled = !busy && zip.length == 5) {
                Text(if (busy && !saving) "Finding stations…" else "Find my channels")
            }
        }
        result?.let { found ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(found.location, color = colors.TextPrimary, fontSize = 20.sp)
                    Text("${selected.size} selected · You can adjust the selection below",
                        color = colors.TextSecondary, fontSize = 13.sp)
                }
                if (found.available.isNotEmpty()) {
                    Button(onClick = { showAll = !showAll }, enabled = !busy) {
                        Text(if (showAll) "Nearby stations" else "Browse all locals")
                    }
                }
            }
        }
        message?.let { Text(it, color = colors.TextSecondary, fontSize = 14.sp) }
        val stations = result?.let { if (showAll) it.available else it.nearby }.orEmpty()
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(stations, key = { it.key }) { channel ->
                val checked = channel.key in selected
                Button(
                    onClick = { selected = if (checked) selected - channel.key else selected + channel.key },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AsyncImage(model = channel.logo, contentDescription = null,
                            modifier = Modifier.size(width = 64.dp, height = 36.dp))
                        Text(channel.name, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(if (checked) "✓ Selected" else "Select")
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    val code = matchedZip ?: return@Button
                    busy = true
                    saving = true
                    message = null
                    scope.launch {
                        try {
                            manager.saveLocals(code, selected.toList())
                            onDone()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            message = e.message ?: "Couldn’t save your stations. Please try again."
                            busy = false
                            saving = false
                        }
                    }
                },
                enabled = !busy && matchedZip == zip && selected.isNotEmpty(),
            ) { Text(if (saving) "Saving…" else "Use these channels") }
            Button(onClick = onDone, enabled = !saving) { Text(if (setup) "Choose later" else "Back") }
            Text("Change this later in Settings → Live TV → Local channels.", color = colors.TextSecondary, fontSize = 12.sp)
        }
    }
}
