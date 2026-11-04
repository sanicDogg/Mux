package com.example.mux.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mux.R
import com.example.mux.data.model.DeviceStatePayload

/**
 * A compact readout of the latest [DeviceStatePayload] plus a manual
 * "Send now" trigger. Used on the Dashboard.
 */
@Composable
fun DeviceStateCard(
    deviceState: DeviceStatePayload?,
    isSending: Boolean,
    onSendNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.device_state_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onSendNow,
                    enabled = !isSending
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.device_state_send_now))
                }
            }

            if (deviceState == null) {
                Text(
                    text = stringResource(R.string.device_state_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            val yes = stringResource(R.string.device_state_on)
            val no = stringResource(R.string.device_state_off)

            InfoLine(stringResource(R.string.device_state_battery, deviceState.batteryLevel))
            InfoLine(stringResource(R.string.device_state_signal, deviceState.signalStrength))
            InfoLine(stringResource(R.string.device_state_airplane, if (deviceState.isAirplaneModeOn) yes else no))
            InfoLine(stringResource(R.string.device_state_wifi, if (deviceState.isWifiConnected) deviceState.wifiName ?: yes else no))
            InfoLine(stringResource(R.string.device_state_cellular, if (deviceState.isCellularConnected) deviceState.cellularOperator ?: yes else no))
            InfoLine(stringResource(R.string.device_state_bluetooth, if (deviceState.isBluetoothOn) yes else no))
            InfoLine(stringResource(R.string.device_state_vpn, if (deviceState.isVpnActive) yes else no))
            InfoLine(stringResource(R.string.device_state_headphones, if (deviceState.isHeadphonesConnected) yes else no))
            InfoLine(stringResource(R.string.device_state_media, if (deviceState.isMediaPlaying) yes else no))
            InfoLine(stringResource(R.string.device_state_volume, deviceState.volumePercent))
        }
    }
}

@Composable
private fun InfoLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
