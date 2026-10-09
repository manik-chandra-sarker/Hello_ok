package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.ListeningStatus
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.OfflineEngineScreen
import com.example.ui.screens.RulesScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                VoiceBridgeApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceBridgeApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current

    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val voskState by viewModel.voskModelState.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Check permissions
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasMicPermission = results[Manifest.permission.RECORD_AUDIO] ?: hasMicPermission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = results[Manifest.permission.POST_NOTIFICATIONS] ?: hasNotificationPermission
        }
    }

    val requestPermissions = {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionsLauncher.launch(perms.toTypedArray())
    }

    LaunchedEffect(Unit) {
        if (!hasMicPermission || !hasNotificationPermission) {
            requestPermissions()
        }
    }

    // Support system back button to return to home tab (tab 0)
    if (selectedTab != 0) {
        BackHandler {
            selectedTab = 0
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "VoiceBridge",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(10.dp))
                        // Real-time status chip
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                serviceState.status == ListeningStatus.SPEECH_DETECTED -> MaterialTheme.colorScheme.tertiary
                                serviceState.isRunning -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (serviceState.isRunning) Color(0xFF00E676) else Color(0xFFFF5252)
                                        )
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = when (serviceState.status) {
                                        ListeningStatus.LISTENING -> "ONLINE"
                                        ListeningStatus.SPEECH_DETECTED -> "HEARING"
                                        ListeningStatus.PROCESSING -> "OFFLINE STT"
                                        ListeningStatus.SPEAKING -> "TTS ACTIVE"
                                        ListeningStatus.STOPPED -> "OFFLINE"
                                        else -> "READY"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (serviceState.isRunning)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (!hasMicPermission) {
                                requestPermissions()
                            } else {
                                viewModel.toggleService()
                            }
                        },
                        modifier = Modifier.testTag("appbar_service_action")
                    ) {
                        Icon(
                            imageVector = if (serviceState.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (serviceState.isRunning) "Stop Background Service" else "Start Background Service",
                            tint = if (serviceState.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Mic, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") },
                    modifier = Modifier.testTag("tab_dashboard")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Automation Rules") },
                    label = { Text("Rules") },
                    modifier = Modifier.testTag("tab_rules")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.CloudOff, contentDescription = "Offline Voice & TTS") },
                    label = { Text("Offline & TTS") },
                    modifier = Modifier.testTag("tab_engine")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.History, contentDescription = "History Logs") },
                    label = { Text("Logs") },
                    modifier = Modifier.testTag("tab_logs")
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> DashboardScreen(
                serviceState = serviceState,
                settings = settings,
                hasMicPermission = hasMicPermission,
                hasNotificationPermission = hasNotificationPermission,
                onRequestPermissions = requestPermissions,
                onToggleService = {
                    if (!hasMicPermission) {
                        requestPermissions()
                    } else {
                        viewModel.toggleService()
                    }
                },
                onSimulateCommand = { viewModel.simulateVoiceCommand(it) },
                onTestTts = { viewModel.testTts(it) },
                modifier = Modifier.padding(innerPadding)
            )
            1 -> RulesScreen(
                settings = settings,
                rules = rules,
                onUpdateSettings = { viewModel.updateSettings(it) },
                onSaveRule = { viewModel.saveRule(it) },
                onToggleRule = { viewModel.toggleRuleEnabled(it) },
                onDeleteRule = { viewModel.deleteRule(it) },
                modifier = Modifier.padding(innerPadding)
            )
            2 -> OfflineEngineScreen(
                settings = settings,
                voskState = voskState,
                onDownloadVoskModel = { viewModel.downloadVoskModel() },
                onUpdateSettings = { viewModel.updateSettings(it) },
                onTestTts = { viewModel.testTts(it) },
                modifier = Modifier.padding(innerPadding)
            )
            3 -> LogsScreen(
                logs = logs,
                onResendIntent = { viewModel.resendLogIntent(it) },
                onClearLogs = { viewModel.clearLogs() },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
