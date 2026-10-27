package com.example.mux.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class MuxDestination(
    val title: String,
    val icon: ImageVector
) {
    DASHBOARD("Dashboard", Icons.Rounded.Dashboard),
    NOTIFICATIONS("Notifications", Icons.Rounded.Notifications),
    WEB_SERVER("Web Server", Icons.Rounded.Dns),
    SETTINGS("Settings", Icons.Rounded.Settings)
}
