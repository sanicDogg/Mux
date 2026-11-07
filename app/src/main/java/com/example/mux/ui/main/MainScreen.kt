package com.example.mux.ui.main

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowWidthSizeClass
import com.example.mux.R
import com.example.mux.ui.dashboard.DashboardScreen
import com.example.mux.ui.dashboard.DashboardViewModel
import com.example.mux.ui.devicestate.DeviceStateScreen
import com.example.mux.ui.devicestate.DeviceStateViewModel
import com.example.mux.ui.notifications.fast.FastNotificationScreen
import com.example.mux.ui.notifications.fast.FastNotificationViewModel
import com.example.mux.ui.settings.SettingsScreen
import com.example.mux.ui.settings.SettingsViewModel
import com.example.mux.ui.webserver.NginxConfigViewModel
import com.example.mux.ui.webserver.WebServerConfigScreen
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val isNotificationListenerGranted = uiState.isNotificationListenerGranted
    val currentDestination = uiState.currentDestination

    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    val isCompact = windowAdaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT

    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val destinations = remember { MuxDestination.entries }
    
    val onOpenMenu: () -> Unit = remember { { scope.launch { drawerState.open() } } }
    val onNavigate: (MuxDestination) -> Unit = remember(onAction) { { dest -> onAction(MainAction.Navigate(dest)) } }

    LaunchedEffect(currentDestination) {
        onAction(MainAction.CheckNotificationListener)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onAction(MainAction.CheckNotificationListener)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isCompact,
        drawerContent = {
            if (isCompact) {
                AppDrawerContent(
                    destinations = destinations,
                    currentDestination = currentDestination,
                    onNavigate = { dest ->
                        onAction(MainAction.Navigate(dest))
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Row(modifier = modifier.fillMaxSize()) {
            if (!isCompact) {
                AppNavigationRail(
                    destinations = destinations,
                    currentDestination = currentDestination,
                    onNavigate = { dest -> onAction(MainAction.Navigate(dest)) }
                )
            }
            AppScaffold(
                destinations = destinations,
                destination = currentDestination,
                isCompact = isCompact,
                snackbarHostState = snackbarHostState,
                isNotificationListenerGranted = isNotificationListenerGranted,
                onOpenMenu = onOpenMenu,
                onNavigate = onNavigate,
                modifier = if (isCompact) Modifier else Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(
    destinations: List<MuxDestination>,
    destination: MuxDestination,
    isCompact: Boolean,
    snackbarHostState: SnackbarHostState,
    isNotificationListenerGranted: Boolean,
    onOpenMenu: () -> Unit,
    onNavigate: (MuxDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTitle = stringResource(destination.titleRes)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = currentTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (isCompact) {
                        IconButton(onClick = onOpenMenu) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = stringResource(R.string.acc_open_menu)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isCompact) {
                AppNavigationBar(
                    destinations = destinations,
                    currentDestination = destination,
                    onNavigate = onNavigate
                )
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = destination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
            label = "Screen Transition"
        ) { dest ->
            MuxScreenContent(
                destination = dest,
                isNotificationListenerGranted = isNotificationListenerGranted,
                snackbarHostState = snackbarHostState,
                onNavigate = onNavigate
            )
        }
    }
}

@Composable
private fun MuxScreenContent(
    destination: MuxDestination,
    isNotificationListenerGranted: Boolean,
    snackbarHostState: SnackbarHostState,
    onNavigate: (MuxDestination) -> Unit,
) {
    when (destination) {
        MuxDestination.DASHBOARD -> DashboardScreen(
            viewModel = viewModel(factory = DashboardViewModel.Factory()),
            snackbarHostState = snackbarHostState
        )
        MuxDestination.NOTIFICATIONS_FAST -> FastNotificationScreen(
            viewModel = viewModel(factory = FastNotificationViewModel.Factory()),
            isNotificationListenerGranted = isNotificationListenerGranted,
            snackbarHostState = snackbarHostState
        )
        MuxDestination.WEB_SERVER -> WebServerConfigScreen(
            viewModel = viewModel(factory = NginxConfigViewModel.Factory()),
            snackbarHostState = snackbarHostState
        )
        MuxDestination.DEVICE_STATE -> DeviceStateScreen(
            viewModel = viewModel(factory = DeviceStateViewModel.Factory()),
            snackbarHostState = snackbarHostState
        )
        MuxDestination.SETTINGS -> SettingsScreen(
            viewModel = viewModel(factory = SettingsViewModel.Factory()),
            onOpenDeviceState = { onNavigate(MuxDestination.DEVICE_STATE) },
            snackbarHostState = snackbarHostState
        )
    }
}

@Composable
private fun AppDrawerContent(
    destinations: List<MuxDestination>,
    currentDestination: MuxDestination,
    onNavigate: (MuxDestination) -> Unit
) {
    ModalDrawerSheet {
        Text(
            text = stringResource(R.string.mux_control_hub),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        destinations.forEach { destination ->
            val labelStr = stringResource(destination.titleRes)
            NavigationDrawerItem(
                icon = { 
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = null // Null because label is announced
                    ) 
                },
                label = { Text(labelStr) },
                selected = currentDestination == destination,
                onClick = { onNavigate(destination) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun AppNavigationBar(
    destinations: List<MuxDestination>,
    currentDestination: MuxDestination,
    onNavigate: (MuxDestination) -> Unit
) {
    NavigationBar {
        destinations.forEach { destination ->
            val labelStr = stringResource(destination.titleRes)
            NavigationBarItem(
                selected = currentDestination == destination,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = null // Null because label is announced
                    )
                },
                label = { Text(labelStr) }
            )
        }
    }
}

@Composable
private fun AppNavigationRail(
    destinations: List<MuxDestination>,
    currentDestination: MuxDestination,
    onNavigate: (MuxDestination) -> Unit
) {
    NavigationRail(
        header = {
            Text(
                text = stringResource(R.string.mux_short_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    ) {
        destinations.forEach { destination ->
            val labelStr = stringResource(destination.titleRes)
            NavigationRailItem(
                selected = currentDestination == destination,
                onClick = { onNavigate(destination) },
                icon = { 
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = null // Null because label provides context
                    ) 
                },
                label = { Text(labelStr) }
            )
        }
    }
}
