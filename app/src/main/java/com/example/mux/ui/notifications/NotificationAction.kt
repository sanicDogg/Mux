package com.example.mux.ui.notifications

import com.example.mux.data.repository.NotificationLogEntry

sealed interface NotificationAction {
    data class Search(val query: String) : NotificationAction
    data class FilterStatus(val status: NotificationStatusFilter) : NotificationAction
    data class SelectEntry(val entry: NotificationLogEntry?) : NotificationAction
    data class ResendNotification(val entry: NotificationLogEntry) : NotificationAction
    data object ClearLogs : NotificationAction
    data object ClearStatusMessage : NotificationAction
}
