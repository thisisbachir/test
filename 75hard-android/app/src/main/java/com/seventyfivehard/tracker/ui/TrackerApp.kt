package com.seventyfivehard.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

private enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Filled.CheckCircle),
    PROGRESS("Progress", Icons.Filled.GridView),
    PHOTOS("Photos", Icons.Filled.PhotoLibrary),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
fun TrackerApp(vm: TrackerViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val message by vm.messages.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    // Which day the Today tab is showing; null = today.
    var viewedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LifecycleResumeEffect(Unit) {
        vm.refreshToday()
        onPauseOrDispose { }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val attempt = data.current
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                // Before a challenge starts there's only the start screen and settings (for restoring a backup).
                val tabs = if (attempt == null) listOf(Tab.TODAY, Tab.SETTINGS) else Tab.entries
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = {
                            if (t == Tab.TODAY && tab == Tab.TODAY) viewedDate = null
                            tab = t
                        },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        if (attempt == null) {
            if (tab == Tab.SETTINGS) SettingsScreen(vm = vm, data = data, modifier = modifier)
            else StartScreen(history = data.history, onStart = { offset, day, diet ->
                vm.startChallenge(offset, day, diet)
                tab = Tab.TODAY
                viewedDate = null
            }, modifier = modifier)
            return@Scaffold
        }
        when (tab) {
            Tab.TODAY -> TodayScreen(
                vm = vm,
                attempt = attempt,
                settings = data.settings,
                today = today,
                date = viewedDate?.let(LocalDate::parse) ?: today,
                onDateChange = { d -> viewedDate = if (d == today) null else d.toString() },
                modifier = modifier,
            )
            Tab.PROGRESS -> ProgressScreen(
                attempt = attempt,
                history = data.history,
                today = today,
                onOpenDay = { d ->
                    viewedDate = if (d == today) null else d.toString()
                    tab = Tab.TODAY
                },
                modifier = modifier,
            )
            Tab.PHOTOS -> PhotosScreen(vm = vm, attempt = attempt, modifier = modifier)
            Tab.SETTINGS -> SettingsScreen(vm = vm, data = data, modifier = modifier)
        }
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("75 HARD", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("Locked", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
            Button(onClick = onUnlock) { Text("Unlock") }
        }
    }
}
