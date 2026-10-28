package com.example.mux.ui.main

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun onAction(action: MainAction) {
        when (action) {
            MainAction.CheckNotificationListener -> checkNotificationListener()
            is MainAction.Navigate -> navigateTo(action.destination)
        }
    }

    private fun checkNotificationListener() {
        val context = getApplication<Application>()
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        val isGranted = flat != null && flat.contains(context.packageName)
        
        _uiState.update { it.copy(isNotificationListenerGranted = isGranted) }
    }

    private fun navigateTo(destination: MuxDestination) {
        _uiState.update { it.copy(currentDestination = destination) }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(application) as T
        }
    }
}
