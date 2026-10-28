package com.example.mux.ui.main

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.mux.R

enum class MuxDestination(
    @StringRes val titleRes: Int,
    val icon: ImageVector
) {
    DASHBOARD(R.string.title_dashboard, Icons.Rounded.Dashboard),
    NOTIFICATIONS(R.string.title_notifications, Icons.Rounded.Notifications),
    WEB_SERVER(R.string.title_web_server, Icons.Rounded.Dns),
    SETTINGS(R.string.title_settings, Icons.Rounded.Settings)
}
