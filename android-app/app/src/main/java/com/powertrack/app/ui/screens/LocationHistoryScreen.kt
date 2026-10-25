package com.powertrack.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
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
import com.powertrack.app.data.model.LocationEntity
import com.powertrack.app.data.repository.LocationRepository
import com.powertrack.app.ui.theme.ErrorRed
import com.powertrack.app.ui.theme.SuccessGreen
import com.powertrack.app.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationHistoryScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { LocationRepository(context) }
    val historyList by repository.allLocationsFlow.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local Location History (${historyList.size})") },
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
        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No location records in Room database yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(historyList, key = { it.eventId }) { item ->
                    LocationHistoryItemCard(item)
                }
            }
        }
    }
}

@Composable
fun LocationHistoryItemCard(item: LocationEntity) {
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
                    text = "${item.latitude}, ${item.longitude}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                UploadStatusBadge(item.uploadStatus)
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Acc: ±${item.accuracyMeters.toInt()}m  •  Battery: ${item.batteryPercentage}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (item.isFreshLocation) "Fresh" else "Cached",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.isFreshLocation) SuccessGreen else WarningAmber
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Recorded: ${item.recordedAtUtc}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )

            if (!item.uploadedAtUtc.isNullOrBlank()) {
                Text(
                    text = "Uploaded: ${item.uploadedAtUtc}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SuccessGreen
                )
            }
        }
    }
}

@Composable
fun UploadStatusBadge(status: String) {
    val (label, color, icon) = when (status) {
        LocationEntity.STATUS_UPLOADED -> Triple("UPLOADED", SuccessGreen, Icons.Default.CloudDone)
        LocationEntity.STATUS_PENDING -> Triple("PENDING", WarningAmber, Icons.Default.CloudUpload)
        else -> Triple("FAILED", ErrorRed, Icons.Default.CloudUpload)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
