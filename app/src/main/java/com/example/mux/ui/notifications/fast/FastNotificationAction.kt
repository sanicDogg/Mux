package com.example.mux.ui.notifications.fast

import com.example.mux.ui.notifications.NotificationStatusFilter

/**
 * Action contract for the fast notifications screen.
 *
 * Identity is carried as the notification domain ID ([String]) everywhere, never as a
 * log entry object or list position (per ui/ai/GEMINI.md).
 */
sealed interface FastNotificationAction {
    data class Search(val query: String) : FastNotificationAction
    data class FilterStatus(val status: NotificationStatusFilter) : FastNotificationAction
    data class SelectEntry(val id: String?) : FastNotificationAction
    data class ResendNotification(val id: String) : FastNotificationAction
    data object ClearLogs : FastNotificationAction
    data object ClearStatusMessage : FastNotificationAction
    data object FetchActiveNotifications : FastNotificationAction
}
