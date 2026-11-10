package com.nuvio.tv.petalix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.LocalSidebarExpanded
import com.nuvio.tv.R
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.ui.components.CollectionRowSection
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.map

const val PETALIX_SERVICES_COLLECTION = "collection-streaming-services-only"
internal const val PETALIX_SERVICES_ROW = "collection_collection-streaming-services-only"

/** A calm Home: service tiles and Continue Watching, with catalogs inside each service. */
internal fun <T> petalixServicesFirst(rows: List<T>, key: (T) -> String): List<T> {
    val service = rows.filter { key(it) == PETALIX_SERVICES_ROW }
    return service + rows.filter { key(it) != PETALIX_SERVICES_ROW }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PetalixHomeDependencies {
    fun collections(): CollectionsDataStore
}

/** A usable Home while metadata is unavailable, using the same horizontal native service row. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PetalixHomeServicesFallback(
    onLiveTv: () -> Unit,
    onSettings: () -> Unit,
    onFolder: (String, String) -> Unit
) {
    val context = LocalContext.current
    val dependencies = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, PetalixHomeDependencies::class.java)
    }
    val collectionFlow = remember(dependencies) {
        dependencies.collections().collections.map<List<Collection>, List<Collection>?> { it }
    }
    val collections by collectionFlow.collectAsStateWithLifecycle(initialValue = null)
    val services = collections?.firstOrNull { it.id == PETALIX_SERVICES_COLLECTION }
        ?.takeIf { it.folders.isNotEmpty() }
    val contentFocusRequester = LocalContentFocusRequester.current
    val sidebarExpanded = LocalSidebarExpanded.current

    // The fallback may appear after the normal startup focus transfer. Only take content
    // focus while the sidebar is closed; returning from it still uses the shared requester.
    LaunchedEffect(services?.id, collections != null, sidebarExpanded) {
        if (!sidebarExpanded) {
            repeat(2) { withFrameNanos { } }
            runCatching { contentFocusRequester.requestFocus() }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(NuvioTheme.colors.Background)
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = R.drawable.app_logo_wordmark,
                contentDescription = "Petalix",
                modifier = Modifier.width(148.dp).height(44.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(24.dp))
            Text("Home", style = MaterialTheme.typography.headlineMedium)
        }

        if (services != null) {
            CollectionRowSection(
                collection = services,
                onFolderClick = onFolder,
                entryFocusRequester = contentFocusRequester
            )
        } else {
            Text(
                text = if (collections == null) "Getting your streaming services ready…"
                    else "Your streaming services are unavailable. Open Settings to check your connection.",
                modifier = Modifier.padding(horizontal = 36.dp),
                color = NuvioTheme.colors.TextSecondary,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Row(
            modifier = Modifier.padding(horizontal = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Button(
                onClick = onLiveTv,
                modifier = if (services == null) Modifier.focusRequester(contentFocusRequester) else Modifier
            ) { Text("Live TV") }
            Button(onClick = onSettings) { Text("Settings") }
        }
    }
}
