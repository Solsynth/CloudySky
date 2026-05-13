package dev.solsynth.cloudysky.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen(
    onBackClick: () -> Unit,
    versionName: String,
    versionCode: Int,
    buildType: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "About CloudySky")
                Text(text = "Solian client for notifications, account info, and SOP streaming.")
                Text(text = "Built with Jetpack Compose, AppAuth, Coil, and OkHttp SSE.")
                Text(text = "Version: $versionName ($versionCode)")
                Text(text = "Build: $buildType")
                Button(onClick = onBackClick) {
                    Text("Back")
                }
            }
        }
    }
}
