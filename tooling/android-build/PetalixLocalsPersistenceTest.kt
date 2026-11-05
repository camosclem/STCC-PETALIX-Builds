package com.nuvio.tv.petalix

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PetalixLocalsPersistenceTest {
    private class Store(initial:Preferences):DataStore<Preferences> {
        override val data=MutableStateFlow(initial)
        override suspend fun updateData(transform:suspend(Preferences)->Preferences):Preferences =
            transform(data.value).also { data.value=it }
    }

    @Test fun localSelectionSurvivesPanelSettingsLockWithoutUnlockingAdminGroups()=runBlocking {
        val before=ManagedPolicy.state.value
        try {
            ManagedPolicy.state.value=CustomerPolicy()
            val groups=stringPreferencesKey("custom_groups")
            val names=booleanPreferencesKey("show_channel_names")
            val locals=stringPreferencesKey("petalix_local_group")
            val zip=stringPreferencesKey("petalix_local_zip")
            val delegate=Store(preferencesOf(groups to "admin",names to true))
            val store=ManagedStore("live_tv",delegate)
            store.updateData { it.toMutablePreferences().apply {
                this[groups]="customer edit"
                this[names]=false
                this[locals]="four selected channels"
                this[zip]="28602"
            } }
            assertEquals("admin",delegate.data.value[groups])
            assertEquals(true,delegate.data.value[names])
            assertEquals("four selected channels",delegate.data.value[locals])
            assertEquals("28602",delegate.data.value[zip])
        } finally { ManagedPolicy.state.value=before }
    }
}
