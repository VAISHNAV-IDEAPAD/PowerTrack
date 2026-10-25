package com.powertrack.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.powertrack.app.data.local.SessionManager
import com.powertrack.app.service.LocationForegroundService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }

    var selectedInterval by remember { mutableStateOf(sessionManager.trackingIntervalSeconds) }
    var isHighAccuracy by remember { mutableStateOf(sessionManager.isHighAccuracyEnabled) }

    val intervalOptions = listOf(
        10 to "10 seconds (High frequency)",
        30 to "30 seconds (Default - Recommended)",
        60 to "60 seconds (Balanced)",
        300 to "300 seconds (Battery Saver - 5 min)"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tracking Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Battery Impact Warning Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Battery Consumption Notice",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Aggressive tracking intervals (e.g. 10 seconds) keep the device GPS receiver active continuously and will cause significantly higher battery drainage. Intervals are treated by Android as target intervals, not absolute guarantees.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Interval Selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Location Update Interval",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    intervalOptions.forEach { (seconds, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedInterval == seconds,
                                onClick = {
                                    selectedInterval = seconds
                                    sessionManager.trackingIntervalSeconds = seconds
                                    if (sessionManager.isTrackingEnabled) {
                                        // Restart service to apply new interval
                                        LocationForegroundService.startService(context)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedInterval == seconds) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // High Accuracy Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "High Accuracy Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Uses raw satellite GPS constellations. When disabled, uses cell tower / Wi-Fi balanced mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = isHighAccuracy,
                        onCheckedChange = { checked ->
                            isHighAccuracy = checked
                            sessionManager.isHighAccuracyEnabled = checked
                            if (sessionManager.isTrackingEnabled) {
                                LocationForegroundService.startService(context)
                            }
                        }
                    )
                }
            }
        }
    }
}
