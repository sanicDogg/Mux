package com.example.mux.ui.main

import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowWidthSizeClass
import com.example.mux.ui.dashboard.DashboardScreen
import com.example.mux.ui.dashboard.DashboardViewModel
import com.example.mux.ui.notifications.NotificationLogViewModel
import com.example.mux.ui.notifications.NotificationScreen
import com.example.mux.ui.settings.SettingsScreen
import com.example.mux.ui.settings.SettingsViewModel
import com.example.mux.ui.webserver.NginxConfigViewModel
import com.example.mux.ui.webserver.WebServerConfigScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAdaptiveHub(
    dashboardViewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory()),
    notificationLogViewModel: NotificationLogViewModel = viewModel(factory = NotificationLogViewModel.Factory()),
    nginxConfigViewModel: NginxConfigViewModel = viewModel(factory = NginxConfigViewModel.Factory()),
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory()),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isNotificationListenerGranted by remember { mutableStateOf(false) }

    fun checkPermission() {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        isNotificationListenerGranted = flat != null && flat.contains(context.packageName)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermission()
                settingsViewModel.checkPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var currentDestination by rememberSaveable { mutableStateOf(MuxDestination.DASHBOARD) }
    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    val isCompact = windowAdaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT

    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    if (isCompact) {
        // Phone Layout with Modal Navigation Drawer (Burger Menu)
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Text(
                        text = "MUX Control Hub",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(24.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MuxDestination.entries.forEach { destination ->
                        NavigationDrawerItem(
                            icon = { Icon(destination.icon, contentDescription = destination.title) },
                            label = { Text(destination.title) },
                            selected = currentDestination == destination,
                            onClick = {
                                currentDestination = destination
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        ) {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = currentDestination.title,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(imageVector = Icons.Rounded.Menu, contentDescription = "Open Menu")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Crossfade(targetState = currentDestination, label = "Screen Transition") { dest ->
                        MuxScreenContent(
                            destination = dest,
                            dashboardViewModel = dashboardViewModel,
                            notificationLogViewModel = notificationLogViewModel,
                            nginxConfigViewModel = nginxConfigViewModel,
                            settingsViewModel = settingsViewModel,
                            isNotificationListenerGranted = isNotificationListenerGranted,
                            snackbarHostState = snackbarHostState
                        )
                    }
                }
            }
        }
    } else {
        // Tablet / Foldable / Expanded Layout with Side Navigation Rail
        Row(modifier = modifier.fillMaxSize()) {
            NavigationRail(
                header = {
                    Text(
                        text = "MUX",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            ) {
                MuxDestination.entries.forEach { destination ->
                    NavigationRailItem(
                        selected = currentDestination == destination,
                        onClick = { currentDestination = destination },
                        icon = { Icon(imageVector = destination.icon, contentDescription = destination.title) },
                        label = { Text(destination.title) }
                    )
                }
            }

            Scaffold(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = currentDestination.title,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Crossfade(targetState = currentDestination, label = "Screen Transition") { dest ->
                        MuxScreenContent(
                            destination = dest,
                            dashboardViewModel = dashboardViewModel,
                            notificationLogViewModel = notificationLogViewModel,
                            nginxConfigViewModel = nginxConfigViewModel,
                            settingsViewModel = settingsViewModel,
                            isNotificationListenerGranted = isNotificationListenerGranted,
                            snackbarHostState = snackbarHostState
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MuxScreenContent(
    destination: MuxDestination,
    dashboardViewModel: DashboardViewModel,
    notificationLogViewModel: NotificationLogViewModel,
    nginxConfigViewModel: NginxConfigViewModel,
    settingsViewModel: SettingsViewModel,
    isNotificationListenerGranted: Boolean,
    snackbarHostState: SnackbarHostState
) {
    when (destination) {
        MuxDestination.DASHBOARD -> DashboardScreen(
            viewModel = dashboardViewModel,
            isNotificationListenerGranted = isNotificationListenerGranted,
            snackbarHostState = snackbarHostState
        )
        MuxDestination.NOTIFICATIONS -> NotificationScreen(
            viewModel = notificationLogViewModel,
            isNotificationListenerGranted = isNotificationListenerGranted,
            snackbarHostState = snackbarHostState
        )
        MuxDestination.WEB_SERVER -> WebServerConfigScreen(
            viewModel = nginxConfigViewModel,
            snackbarHostState = snackbarHostState
        )
        MuxDestination.SETTINGS -> SettingsScreen(
            viewModel = settingsViewModel,
            snackbarHostState = snackbarHostState
        )
    }
}
