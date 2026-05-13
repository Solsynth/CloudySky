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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.solsynth.cloudysky.auth.CurrentAccount
import dev.solsynth.cloudysky.sop.SopListenerSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentAccount: CurrentAccount?,
    isLoadingAccount: Boolean,
    sopState: SopListenerSnapshot,
    onBackClick: () -> Unit,
    onAboutClick: () -> Unit,
    onToggleSopListener: (Boolean) -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onLogoutClick: () -> Unit,
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
                                text = "Status: ${sopState.status.name}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = sopState.enabled, onCheckedChange = onToggleSopListener)
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

                    if (!sopState.deviceId.isNullOrBlank()) {
                        InfoRow(label = "Device ID", value = sopState.deviceId.orEmpty())
                    }

                    if (!sopState.subscriptionId.isNullOrBlank()) {
                        InfoRow(label = "Subscription ID", value = sopState.subscriptionId.orEmpty())
                    }

                    sopState.error?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error)
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
private fun InfoRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
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
