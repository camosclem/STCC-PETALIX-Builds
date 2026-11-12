package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.petalix.PetalixManagedCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
internal class PetalixAccountViewModel @Inject constructor(
    private val coordinator: PetalixManagedCoordinator
) : ViewModel() {
    private val _account = MutableStateFlow(coordinator.accountSummary())
    private val _refresh = MutableStateFlow<String?>(null)
    val account = _account.asStateFlow()
    val refresh = _refresh.asStateFlow()

    fun readAccount() {
        _account.value = coordinator.accountSummary()
    }

    fun refreshAccount() {
        if (_refresh.value == "Refreshing…") return
        _refresh.value = "Refreshing…"
        viewModelScope.launch {
            try {
                coordinator.refresh()
                _refresh.value = "Account refreshed"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _refresh.value = "Could not refresh account"
            } finally {
                readAccount()
            }
        }
    }
}

@Composable
internal fun PetalixAccountSummary(initialFocusRequester: FocusRequester?) {
    val model: PetalixAccountViewModel = hiltViewModel()
    LaunchedEffect(model) { model.readAccount() }
    val account by model.account.collectAsStateWithLifecycle()
    val refresh by model.refresh.collectAsStateWithLifecycle()
    val expiry = account.expiry?.let { DateFormat.getDateInstance().format(Date(it)) }
    val detail = when {
        !account.activated -> "Activation required"
        expiry != null && account.active -> "Expires $expiry"
        expiry != null -> "Inactive · Expiration $expiry"
        else -> "Access inactive"
    }
    SettingsGroupCard(modifier = Modifier.fillMaxWidth(), title = account.name ?: "Petalix account", subtitle = detail) {
        SettingsActionRow(
            title = "Refresh account",
            subtitle = refresh,
            onClick = model::refreshAccount,
            modifier = if (initialFocusRequester == null) Modifier else Modifier.focusRequester(initialFocusRequester)
        )
    }
}
