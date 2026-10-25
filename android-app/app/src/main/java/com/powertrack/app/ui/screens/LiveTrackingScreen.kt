package com.powertrack.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.powertrack.app.data.repository.LocationRepository
import com.powertrack.app.ui.theme.SuccessGreen
import com.powertrack.app.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTrackingScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val locationRepository = remember { LocationRepository(context) }
    val latestLocation by locationRepository.latestLocationFlow.collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Location Telemetry") },
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
            if (latestLocation != null) {
                val loc = latestLocation!!

                // Accuracy Radius Visualizer Canvas
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.size(160.dp)) {
                            val center = Offset(size.width / 2, size.height / 2)
                            // Outer accuracy circle
                            drawCircle(
                                color = Color(0x331D4ED8),
                                radius = size.minDimension / 2.2f
                            )
                            drawCircle(
                                color = Color(0xFF1D4ED8),
                                radius = size.minDimension / 2.2f,
                                style = Stroke(width = 3f)
                            )
                            // Center position marker
                            drawCircle(
                                color = Color(0xFF1D4ED8),
                                radius = 10f
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "±${loc.accuracyMeters.toInt()}m",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Accuracy Confidence Radius",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                // Coordinates Data Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Geodetic Coordinates",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Latitude:", color = MaterialTheme.colorScheme.outline)
                            Text("${loc.latitude}°", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Longitude:", color = MaterialTheme.colorScheme.outline)
                            Text("${loc.longitude}°", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Altitude:", color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = if (loc.altitudeMeters != null) "${"%.1f".format(loc.altitudeMeters)} m" else "Unavailable",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Kinematic Telemetry Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Kinematic Telemetry",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ground Speed:", color = MaterialTheme.colorScheme.outline)
                            val speedMps = loc.speedMetersPerSecond
                            if (speedMps != null) {
                                val speedKmh = speedMps * 3.6f
                                Text("${"%.1f".format(speedKmh)} km/h (${"%.1f".format(speedMps)} m/s)", fontWeight = FontWeight.SemiBold)
                            } else {
                                Text("0.0 km/h (Stationary)", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Bearing / Heading:", color = MaterialTheme.colorScheme.outline)
                            val bearing = loc.bearingDegrees
                            if (bearing != null) {
                                val cardinal = getCardinalDirection(bearing)
                                Text("${bearing.toInt()}° ($cardinal)", fontWeight = FontWeight.SemiBold)
                            } else {
                                Text("Unavailable", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Provider Source:", color = MaterialTheme.colorScheme.outline)
                            Text(loc.locationSource.uppercase(), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No Live Telemetry Available",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Ensure location tracking is enabled and the device has granted location permissions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

fun getCardinalDirection(bearing: Float): String {
    val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", "N")
    val index = Math.round((bearing % 360) / 45.0).toInt()
    return directions[index]
}
