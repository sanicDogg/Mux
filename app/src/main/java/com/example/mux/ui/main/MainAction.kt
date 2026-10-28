package com.example.mux.ui.main

sealed interface MainAction {
    data object CheckNotificationListener : MainAction
    data class Navigate(val destination: MuxDestination) : MainAction
}
