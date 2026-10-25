package com.powertrack.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.powertrack.app.data.local.SessionManager
import com.powertrack.app.data.repository.LocationRepository
import com.powertrack.app.ui.theme.ErrorRed
import com.powertrack.app.ui.theme.SuccessGreen
import com.powertrack.app.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryStatusScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionManager = remember { SessionManager(context) }
    val repository = remember { LocationRepository(context) }

    val currentBattery = remember { repository.getBatteryPercentage() }
    val isArmed = sessionManager.isLowBatteryTriggerArmed
    val lastTriggerTime = sessionManager.lastLowBatteryTriggerTime

    var isSimulating by remember { mutableStateOf(false) }
    var simulationMsg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Battery & Emergency Trigger") },
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
            // Main Battery Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Current Battery Level",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$currentBattery%",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (currentBattery <= 10) ErrorRed else SuccessGreen
                    )
                    LinearProgressIndicator(
                        progress = { currentBattery / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = if (currentBattery <= 10) ErrorRed else SuccessGreen,
                    )
                }
            }

            // Low Battery Trigger Logic Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.BatteryAlert,
                            contentDescription = null,
                            tint = if (isArmed) WarningAmber else SuccessGreen
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "10% Emergency Trigger State",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Trigger State:")
                        Text(
                            text = if (isArmed) "ARMED (Waiting for 10%)" else "TRIGGERED / DISARMED",
                            fontWeight = FontWeight.Bold,
                            color = if (isArmed) SuccessGreen else WarningAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Activation Threshold:")
                        Text("10% Battery Level", fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Re-arm Reset Threshold:")
                        Text("15% (Upon Charging)", fontWeight = FontWeight.SemiBold)
                    }

                    if (lastTriggerTime > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Last Activation:")
                            Text(
                                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(lastTriggerTime)),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Hardware Limitation Notice Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Android OS Hardware Limitation Notice",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Android may shut down or enter hard power-off before a final high-accuracy location can be acquired or transmitted. PowerTrack requests a 15s high-accuracy fix and persists locally in Room, but successful cloud upload cannot be guaranteed if the battery exhausts completely.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Test Simulation Button
            OutlinedButton(
                onClick = {
                    isSimulating = true
                    simulationMsg = null
                    scope.launch {
                        val loc = repository.obtainLocation(15000L)
                        if (loc != null) {
                            val entityWith10 = loc.copy(batteryPercentage = 10)
                            repository.recordAndUploadLocation(entityWith10)
                            simulationMsg = "10% trigger simulated! Recorded with 10% battery at ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}"
                        } else {
                            simulationMsg = "Could not obtain location for simulation."
                        }
                        isSimulating = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSimulating
            ) {
                if (isSimulating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Simulating 10% Trigger...")
                } else {
                    Text("Simulate 10% Low-Battery Trigger")
                }
            }

            if (simulationMsg != null) {
                Text(
                    text = simulationMsg ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = SuccessGreen
                )
            }
        }
    }
}
