package com.example.mux.ui.devicestate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mux.R
import com.example.mux.ui.components.DeviceStateCard

@Composable
fun DeviceStateScreen(
    viewModel: DeviceStateViewModel,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val nonePlaceholder = stringResource(R.string.none_placeholder)
    val message = when (val status = state.sendStatus) {
        DeviceStateSendStatus.Idle -> null
        DeviceStateSendStatus.Success -> stringResource(R.string.device_state_sent)
        is DeviceStateSendStatus.Failure ->
            stringResource(R.string.device_state_send_failed, status.message ?: nonePlaceholder)
    }

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState?.showSnackbar(message)
            viewModel.clearStatus()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DeviceStateCard(
            deviceState = state.deviceState,
            isSending = state.isSending,
            onSendNow = viewModel::sendNow
        )
    }
}
