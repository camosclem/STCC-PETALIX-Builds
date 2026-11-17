package com.nuvio.tv.petalix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import dagger.hilt.android.EntryPointAccessors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Singleton
class PetalixHomePreferences @Inject constructor(
    factory: ProfileDataStoreFactory, profiles: ProfileManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val layout: StateFlow<PetalixHomeLayout> = profiles.activeProfileId.flatMapLatest { id ->
        factory.get(id, "layout_settings").data.map { prefs ->
            PetalixHomeLayout.from(prefs.asMap().mapKeys { it.key.name })
        }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, PetalixHomeLayout())
}

@Composable
internal fun petalixHomeLayout(): PetalixHomeLayout {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) {
        EntryPointAccessors.fromApplication(context, PetalixHomeDependencies::class.java).homePreferences()
    }
    val layout by preferences.layout.collectAsStateWithLifecycle()
    return layout
}
