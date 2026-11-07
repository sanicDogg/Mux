package com.example.mux.ui.devicestate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mux.R
import com.example.mux.ui.components.ListenerStatusHeader
import com.example.mux.ui.components.RawJsonPayload

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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ListenerStatusHeader(
            isActive = state.header.isForwardingEnabled,
            activeLabel = stringResource(R.string.device_state_listener_active),
            inactiveLabel = stringResource(R.string.device_state_listener_inactive),
            targetLabel = stringResource(R.string.forwarding_target, state.header.deviceStateEndpointUrl)
        )

        Button(
            onClick = viewModel::resend,
            enabled = !state.isSending && state.deviceState != null,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            if (state.isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.forwarding_payload))
            } else {
                Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.device_state_resend))
            }
        }

        if (state.deviceState != null) {
            RawJsonPayload(json = state.deviceStateJson ?: state.deviceState.toString())
        }
    }
}
