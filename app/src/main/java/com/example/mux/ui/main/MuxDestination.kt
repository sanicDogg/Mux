package com.example.mux.ui.main

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.mux.R

enum class MuxDestination(
    @StringRes val titleRes: Int,
    val icon: ImageVector
) {
    DASHBOARD(R.string.title_dashboard, Icons.Rounded.Dashboard),
    NOTIFICATIONS_FAST(R.string.title_notifications_fast, Icons.Rounded.Speed),
    WEB_SERVER(R.string.title_web_server, Icons.Rounded.Dns),
    DEVICE_STATE(R.string.title_device_state, Icons.Rounded.Devices),
    SETTINGS(R.string.title_settings, Icons.Rounded.Settings)
}
