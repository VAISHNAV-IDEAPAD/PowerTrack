package com.powertrack.app.ui.screens

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powertrack.app.data.local.SessionManager
import com.powertrack.app.data.model.LocationEntity
import com.powertrack.app.data.repository.LocationRepository
import com.powertrack.app.service.LocationForegroundService
import com.powertrack.app.ui.theme.ErrorRed
import com.powertrack.app.ui.theme.SuccessGreen
import com.powertrack.app.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToLiveTracking: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToBattery: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToOfflineSync: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToPermissions: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionManager = remember { SessionManager(context) }
    val locationRepository = remember { LocationRepository(context) }

    val latestLocation by locationRepository.latestLocationFlow.collectAsState(initial = null)
    val pendingCount by locationRepository.pendingCountFlow.collectAsState(initial = 0)
    val isTrackingEnabled by sessionManager.trackingEnabledFlow.collectAsState()

    var isRefreshing by remember { mutableStateOf(false) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }
    var isErrorMessage by remember { mutableStateOf(false) }

    // Connectivity state check
    var isOnline by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val activeNet = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNet)
        isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    val batteryPercentage = remember { locationRepository.getBatteryPercentage() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PowerTrack Monitor") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = onNavigateToPermissions) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = "Permissions",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
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
            // Tracking Switch Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isTrackingEnabled)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
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
                            text = if (isTrackingEnabled) "Continuous Tracking ACTIVE" else "Tracking Paused",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isTrackingEnabled)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isTrackingEnabled)
                                "Foreground service active (updates every ${sessionManager.trackingIntervalSeconds}s)"
                            else
                                "Turn on tracking to transmit location to dashboard",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isTrackingEnabled,
                        onCheckedChange = { enable ->
                            if (enable) {
                                LocationForegroundService.startService(context)
                            } else {
                                LocationForegroundService.stopService(context)
                            }
                        }
                    )
                }
            }

            // System Status Row (Battery & Connectivity)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Battery Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (batteryPercentage <= 10) Icons.Default.BatteryAlert else Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                tint = if (batteryPercentage <= 10) ErrorRed else SuccessGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Battery", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$batteryPercentage%",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (batteryPercentage <= 10) ErrorRed else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (batteryPercentage <= 10) "10% TRIGGER ACTIVE" else "Threshold: 10%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (batteryPercentage <= 10) ErrorRed else MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Internet Status Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isOnline) SuccessGreen else ErrorRed)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Connectivity", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isOnline) "ONLINE" else "OFFLINE",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isOnline) SuccessGreen else ErrorRed
                        )
                        Text(
                            text = if (pendingCount > 0) "$pendingCount pending sync" else "Sync synchronized",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Latest Location Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Latest Recorded Fix",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (latestLocation != null) {
                            AccuracyBadge(accuracyMeters = latestLocation!!.accuracyMeters)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (latestLocation != null) {
                        val loc = latestLocation!!
                        Text(
                            text = "Latitude:  ${loc.latitude}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Longitude: ${loc.longitude}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Accuracy: ±${"%.1f".format(loc.accuracyMeters)} metres",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "Source: ${loc.locationSource.uppercase()} (${if (loc.isFreshLocation) "Fresh Fix" else "Cached"})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "UTC Recorded: ${loc.recordedAtUtc}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        val lastUpload = sessionManager.lastSuccessfulUploadTime
                        if (lastUpload > 0) {
                            Text(
                                text = "Last Uploaded: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastUpload))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SuccessGreen
                            )
                        }
                    } else {
                        Text(
                            text = "No location fix recorded yet. Tap Refresh Location below to obtain current GPS fix.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Manual Refresh Location Button
                    Button(
                        onClick = {
                            isRefreshing = true
                            refreshMessage = null
                            isErrorMessage = false
                            scope.launch {
                                val result = locationRepository.refreshLocationNow()
                                isRefreshing = false
                                if (result.isSuccess) {
                                    val loc = result.getOrNull()!!
                                    refreshMessage = "Fresh fix obtained! Accuracy: ±${loc.accuracyMeters.toInt()}m"
                                    isErrorMessage = false
                                } else {
                                    refreshMessage = result.exceptionOrNull()?.message ?: "Location request failed."
                                    isErrorMessage = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Obtaining GPS Fix (Timeout: 15s)...")
                        } else {
                            Icon(Icons.Default.MyLocation, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Refresh Location")
                        }
                    }

                    if (refreshMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = refreshMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isErrorMessage) ErrorRed else SuccessGreen
                        )
                    }
                }
            }

            // Navigation Hub Cards
            Text(
                text = "Application Navigation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToLiveTracking,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Navigation, contentDescription = null)
                        Text("Live Telemetry", fontSize = 11.sp)
                    }
                }
                OutlinedButton(
                    onClick = onNavigateToHistory,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, contentDescription = null)
                        Text("History", fontSize = 11.sp)
                    }
                }
                OutlinedButton(
                    onClick = onNavigateToBattery,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.BatteryFull, contentDescription = null)
                        Text("Battery Monitor", fontSize = 11.sp)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToOfflineSync,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Text("Offline Queue ($pendingCount)", fontSize = 11.sp)
                    }
                }
                OutlinedButton(
                    onClick = onNavigateToAccount,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Text("Account & Privacy", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AccuracyBadge(accuracyMeters: Float) {
    val (label, bgColor, textColor) = when {
        accuracyMeters <= 15f -> Triple("High Accuracy (±${accuracyMeters.toInt()}m)", SuccessGreen.copy(alpha = 0.15f), SuccessGreen)
        accuracyMeters <= 50f -> Triple("Moderate Accuracy (±${accuracyMeters.toInt()}m)", WarningAmber.copy(alpha = 0.15f), WarningAmber)
        else -> Triple("Low Accuracy (±${accuracyMeters.toInt()}m)", ErrorRed.copy(alpha = 0.15f), ErrorRed)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}
