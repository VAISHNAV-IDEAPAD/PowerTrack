package com.powertrack.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.powertrack.app.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionExplanationScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Handle results
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions & Privacy") },
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
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Transparent Tracking Philosophy",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "PowerTrack is an authorized personal device finder. It operates with full transparency and explicit user consent. We never perform stealth tracking or bypass OS protections.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Foreground Location
            PermissionDetailCard(
                title = "1. Precise Location (ACCESS_FINE_LOCATION)",
                description = "Required to read true GPS satellites and Wi-Fi/cellular triangulation with exact accuracy radius.",
                isMandatory = true
            )

            // Notifications
            PermissionDetailCard(
                title = "2. Notifications (POST_NOTIFICATIONS)",
                description = "Required by Android 13+ to display the ongoing foreground service notification and emergency 10% low-battery alerts.",
                isMandatory = true
            )

            // Background Location
            PermissionDetailCard(
                title = "3. Background Location (Android 10+)",
                description = "Allows the device to transmit location fixes while the screen is locked or another application is active. Per Android guidelines, select 'Allow all the time' in system settings.",
                isMandatory = false
            )

            // Android System Limitations
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Android System & Hardware Constraints",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Force Stop: If the app is force-stopped in Android Settings, Android disables all background workers and broadcast receivers until manually reopened.\n" +
                                "• Reboot: Tracking resumes after restart via BOOT_COMPLETED only if permissions and battery optimizations allow.\n" +
                                "• Zero Coordinates when powered off: Coordinates cannot be recorded when the device is fully shut down or battery depleted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = {
                    val permissions = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    foregroundPermissionLauncher.launch(permissions.toTypedArray())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Request App Permissions")
            }

            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Android App Settings (Background Permission)")
            }
        }
    }
}

@Composable
fun PermissionDetailCard(
    title: String,
    description: String,
    isMandatory: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = if (isMandatory) "Mandatory" else "Recommended",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isMandatory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
