package dev.solsynth.cloudysky.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.solsynth.cloudysky.R
import dev.solsynth.cloudysky.api.GitHubApi
import dev.solsynth.cloudysky.api.GitHubRelease
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBackClick: () -> Unit,
    versionName: String,
    versionCode: Int,
    buildType: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val gitHubApi = remember { GitHubApi() }

    var isCheckingForUpdates by remember { mutableStateOf(false) }
    var latestRelease by remember { mutableStateOf<GitHubRelease?>(null) }
    var updateAvailable by remember { mutableStateOf(false) }
    var checkError by remember { mutableStateOf(false) }

    fun checkForUpdates() {
        coroutineScope.launch {
            isCheckingForUpdates = true
            checkError = false
            latestRelease = null
            updateAvailable = false

            val release = gitHubApi.getLatestRelease()
            isCheckingForUpdates = false

            if (release != null) {
                latestRelease = release
                val latestTag = release.tagName.removePrefix("v").substringBefore("+")
                val currentVersion = versionName.removePrefix("v").substringBefore("+")
                updateAvailable = latestTag != currentVersion
            } else {
                checkError = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = "CloudySky icon",
                modifier = Modifier.size(96.dp),
            )

            Text(
                text = "CloudySky",
                style = MaterialTheme.typography.headlineMedium,
            )

            Text(
                text = "Official Solar Network Android push notification companion app",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "App Info",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    InfoRow(label = "Version", value = "$versionName ($versionCode)")
                    InfoRow(label = "Build", value = buildType)
                }
            }

            Card(
                colors = CardDefaults.cardColors(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Updates",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    when {
                        isCheckingForUpdates -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text(
                                    text = "Checking for updates...",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        updateAvailable && latestRelease != null -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Update,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "Update available: ${latestRelease!!.tagName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = latestRelease!!.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(latestRelease!!.htmlUrl))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Download Update")
                            }
                        }
                        checkError -> {
                            Text(
                                text = "Failed to check for updates",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            OutlinedButton(
                                onClick = { checkForUpdates() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Retry")
                            }
                        }
                        latestRelease != null -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "You're up to date!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            OutlinedButton(
                                onClick = { checkForUpdates() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Check Again")
                            }
                        }
                        else -> {
                            OutlinedButton(
                                onClick = { checkForUpdates() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Check for Updates")
                            }
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Made by the Solar Network Team with ❤️",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Open-sourced under APGL v3 license",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "github.com/Solsynth/CloudySky",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
