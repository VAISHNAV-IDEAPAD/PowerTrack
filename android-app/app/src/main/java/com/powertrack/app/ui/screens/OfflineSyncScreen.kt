package com.powertrack.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.powertrack.app.data.repository.LocationRepository
import com.powertrack.app.ui.theme.ErrorRed
import com.powertrack.app.ui.theme.SuccessGreen
import com.powertrack.app.ui.theme.WarningAmber
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineSyncScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { LocationRepository(context) }
    val pendingCount by repository.pendingCountFlow.collectAsState(initial = 0)

    var isSyncing by remember { mutableStateOf(false) }
    var syncResultMsg by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline Queue & Synchronization") },
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
                            text = "Pending Upload Queue",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            if (pendingCount > 0) Icons.Default.Sync else Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = if (pendingCount > 0) WarningAmber else SuccessGreen
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "$pendingCount",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (pendingCount > 0) WarningAmber else SuccessGreen
                    )
                    Text(
                        text = if (pendingCount > 0)
                            "Local records pending cloud transmission"
                        else
                            "All recorded locations are fully synchronized with server",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // WorkManager explanation card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "How Offline Sync Operates",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. Every location is committed to local SQLite (Room) first with status PENDING.\n" +
                                "2. If internet connectivity drops, records are preserved safely on-device without data loss.\n" +
                                "3. Android WorkManager detects network restoration and uploads records in chronological order.\n" +
                                "4. Records are marked UPLOADED only after receiving HTTP 201 confirmation from the backend.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Button(
                onClick = {
                    isSyncing = true
                    syncResultMsg = null
                    isError = false
                    scope.launch {
                        try {
                            val uploaded = repository.uploadPendingLocations()
                            isSyncing = false
                            if (uploaded > 0) {
                                syncResultMsg = "Successfully synchronized $uploaded pending location records!"
                                isError = false
                            } else if (pendingCount == 0) {
                                syncResultMsg = "No pending records to sync."
                                isError = false
                            } else {
                                syncResultMsg = "Sync failed. Check network connection or server URL."
                                isError = true
                            }
                        } catch (e: Exception) {
                            isSyncing = false
                            syncResultMsg = "Sync error: ${e.message}"
                            isError = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSyncing
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Synchronizing Queue...")
                } else {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Force Sync Now")
                }
            }

            if (syncResultMsg != null) {
                Text(
                    text = syncResultMsg ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) ErrorRed else SuccessGreen
                )
            }
        }
    }
}
