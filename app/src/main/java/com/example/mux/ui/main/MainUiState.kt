package com.example.mux.ui.main

data class MainUiState(
    val isNotificationListenerGranted: Boolean = false,
    val currentDestination: MuxDestination = MuxDestination.DASHBOARD
)
