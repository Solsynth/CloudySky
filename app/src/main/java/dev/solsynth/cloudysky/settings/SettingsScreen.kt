package dev.solsynth.cloudysky.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.solsynth.cloudysky.auth.CurrentAccount
import dev.solsynth.cloudysky.sop.SopDynamicConfig
import dev.solsynth.cloudysky.sop.SopLogEntryType
import dev.solsynth.cloudysky.sop.SopListenerMode
import dev.solsynth.cloudysky.sop.SopListenerSnapshot
import dev.solsynth.cloudysky.sop.SopLogEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentAccount: CurrentAccount?,
    isLoadingAccount: Boolean,
    sopState: SopListenerSnapshot,
    logEntries: List<SopLogEntry>,
    onBackClick: () -> Unit,
    onAboutClick: () -> Unit,
    onToggleSopListener: (Boolean) -> Unit,
    onSetMode: (SopListenerMode) -> Unit,
    onSetDynamicConfig: (SopDynamicConfig) -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onLogoutClick: () -> Unit,
    onClearLog: () -> Unit,
) {
    val avatarUrl = remember(currentAccount?.pictureUrl) { currentAccount?.pictureUrl }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(colors = CardDefaults.cardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLoadingAccount) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        } else {
                            Card(
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                if (!avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = "Profile picture",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = currentAccount?.displayName ?: "Unknown account", style = MaterialTheme.typography.titleMedium)
                            Text(text = currentAccount?.bio?.ifBlank { currentAccount.name }.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }

                    currentAccount?.let {
                        InfoRow(label = "Account ID", value = it.id)
                    }
                }
            }

            Card(colors = CardDefaults.cardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Always-on SOP listener", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Status: ${sopState.status.name} | ${sopState.runState.name}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = sopState.enabled, onCheckedChange = onToggleSopListener)
                    }

                    if (sopState.enabled) {
                        Text("Connection mode", style = MaterialTheme.typography.labelMedium)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SopListenerMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = SopListenerMode.entries.size),
                                    onClick = { onSetMode(mode) },
                                    selected = sopState.mode == mode,
                                ) {
                                    Text(
                                        text = when (mode) {
                                            SopListenerMode.Stream -> "Stream"
                                            SopListenerMode.Polling -> "Poll"
                                            SopListenerMode.Dynamic -> "Dynamic"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                        Text(
                            text = when (sopState.mode) {
                                SopListenerMode.Stream -> "Real-time SSE streaming. Best latency, higher battery usage."
                                SopListenerMode.Polling -> "Periodic polling. Lowest battery usage, notifications may be delayed."
                                SopListenerMode.Dynamic -> "Smart switching. Polls when idle, streams when active. Balanced."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (sopState.mode == SopListenerMode.Polling || sopState.mode == SopListenerMode.Dynamic) {
                            val pollingMinutes = sopState.dynamicConfig.pollingIntervalMs / 60_000f
                            var sliderPosition by remember(sopState.dynamicConfig.pollingIntervalMs) {
                                mutableStateOf(
                                    when {
                                        pollingMinutes <= 2.5f -> 0f
                                        pollingMinutes <= 7.5f -> 1f
                                        else -> 2f
                                    }
                                )
                            }
                            Column {
                                Text(
                                    text = "Polling interval: ${formatInterval(sopState.dynamicConfig.pollingIntervalMs)}",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Slider(
                                    value = sliderPosition,
                                    onValueChange = { sliderPosition = it },
                                    onValueChangeFinished = {
                                        val newMs = when (sliderPosition.roundToInt()) {
                                            0 -> 1 * 60 * 1000L
                                            1 -> 5 * 60 * 1000L
                                            else -> 10 * 60 * 1000L
                                        }
                                        onSetDynamicConfig(sopState.dynamicConfig.copy(pollingIntervalMs = newMs))
                                    },
                                    valueRange = 0f..2f,
                                    steps = 1,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("1m", style = MaterialTheme.typography.labelSmall)
                                    Text("5m", style = MaterialTheme.typography.labelSmall)
                                    Text("10m", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        if (sopState.mode == SopListenerMode.Dynamic) {
                            val streamMinutes = sopState.dynamicConfig.streamTimeoutMs / 60_000f
                            var sliderPosition by remember(sopState.dynamicConfig.streamTimeoutMs) {
                                mutableStateOf(
                                    when {
                                        streamMinutes <= 7.5f -> 0f
                                        streamMinutes <= 12.5f -> 1f
                                        streamMinutes <= 22.5f -> 2f
                                        else -> 3f
                                    }
                                )
                            }
                            Column {
                                Text(
                                    text = "Stream timeout: ${formatInterval(sopState.dynamicConfig.streamTimeoutMs)}",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Slider(
                                    value = sliderPosition,
                                    onValueChange = { sliderPosition = it },
                                    onValueChangeFinished = {
                                        val newMs = when (sliderPosition.roundToInt()) {
                                            0 -> 5 * 60 * 1000L
                                            1 -> 10 * 60 * 1000L
                                            2 -> 15 * 60 * 1000L
                                            else -> 30 * 60 * 1000L
                                        }
                                        onSetDynamicConfig(sopState.dynamicConfig.copy(streamTimeoutMs = newMs))
                                    },
                                    valueRange = 0f..3f,
                                    steps = 2,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("5m", style = MaterialTheme.typography.labelSmall)
                                    Text("10m", style = MaterialTheme.typography.labelSmall)
                                    Text("15m", style = MaterialTheme.typography.labelSmall)
                                    Text("30m", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    if (!sopState.isIgnoringBatteryOptimizations) {
                        Text(
                            text = "Disable battery optimizations so the foreground listener stays connected reliably.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onOpenBatteryOptimizationSettings, modifier = Modifier.fillMaxWidth()) {
                            Text("Disable battery optimizations")
                        }
                    }

                    if (!sopState.hasNotificationPermission) {
                        Text(
                            text = "Notification permission is off, so incoming SOP events cannot be shown.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (!sopState.androidDeviceId.isNullOrBlank()) {
                        InfoRow(label = "Device ID", value = sopState.androidDeviceId.orEmpty())
                    }

                    if (!sopState.subscriptionId.isNullOrBlank()) {
                        InfoRow(label = "Subscription ID", value = sopState.subscriptionId.orEmpty())
                    }

                    sopState.error?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Card(colors = CardDefaults.cardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Activity Log", style = MaterialTheme.typography.titleMedium)
                        if (logEntries.isNotEmpty()) {
                            IconButton(onClick = onClearLog, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear log")
                            }
                        }
                    }
                    if (logEntries.isEmpty()) {
                        Text(
                            text = "No activity recorded yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        logEntries.take(10).forEach { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val (label, color) = when (entry.type) {
                                        SopLogEntryType.Notification -> entry.title to MaterialTheme.colorScheme.onSurface
                                        SopLogEntryType.ModeSwitch -> "Mode: ${entry.title}" to MaterialTheme.colorScheme.primary
                                        SopLogEntryType.Polling -> "Poll: ${entry.title}" to MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = color,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (entry.type == SopLogEntryType.Notification && entry.topic.isNotBlank()) {
                                        Text(
                                            text = entry.topic,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Text(
                                    text = formatLogTimestamp(entry.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (logEntries.size > 10) {
                            Text(
                                text = "+ ${logEntries.size - 10} more entries",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Button(onClick = onAboutClick, modifier = Modifier.fillMaxWidth()) {
                Text("About")
            }

            TextButton(onClick = onLogoutClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log out")
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

fun formatLogTimestamp(timestamp: Long): String {
    val instant = Instant.ofEpochMilli(timestamp)
    val zoned = instant.atZone(ZoneId.systemDefault())
    val now = java.time.ZonedDateTime.now()
    val duration = java.time.Duration.between(zoned, now)
    return when {
        duration.toMinutes() < 1 -> "Just now"
        duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
        duration.toHours() < 24 -> "${duration.toHours()}h ago"
        duration.toDays() < 7 -> "${duration.toDays()}d ago"
        else -> zoned.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    }
}

fun formatInterval(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
}
