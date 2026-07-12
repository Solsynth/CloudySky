package dev.solsynth.cloudysky.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.solsynth.cloudysky.R
import dev.solsynth.cloudysky.auth.CurrentAccount
import dev.solsynth.cloudysky.sop.SopListenerMode
import dev.solsynth.cloudysky.sop.SopListenerSnapshot
import dev.solsynth.cloudysky.sop.SopListenerStatus
import dev.solsynth.cloudysky.sop.SopRunState
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationListScreen(
    uiState: NotificationUiState,
    currentAccount: CurrentAccount?,
    sopState: SopListenerSnapshot,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSettingsClick: () -> Unit,
    onSignOut: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf false
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !uiState.isLoadingMore && uiState.hasMore && !uiState.isLoading) {
            onLoadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications)) },
                actions = {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 12.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (currentAccount != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier.size(52.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape,
                            ) {
                                if (!currentAccount.pictureUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = currentAccount.pictureUrl,
                                        contentDescription = stringResource(R.string.profile_picture_cd),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text(text = currentAccount.displayName.take(1).uppercase())
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = currentAccount.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = currentAccount.bio.ifBlank { currentAccount.language },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                if (!sopState.isIgnoringBatteryOptimizations) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.battery_opt_banner_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                text = stringResource(R.string.battery_opt_banner_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Button(
                                onClick = onOpenBatteryOptimizationSettings,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.disable_battery_optimization))
                            }
                        }
                    }
                }

                if (sopState.enabled) {
                    val statusColor = when (sopState.status) {
                        SopListenerStatus.Connected -> MaterialTheme.colorScheme.primaryContainer
                        SopListenerStatus.Connecting, SopListenerStatus.Reconnecting -> MaterialTheme.colorScheme.secondaryContainer
                        SopListenerStatus.Failed -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val modeLabel = when (sopState.mode) {
                        SopListenerMode.Stream -> stringResource(R.string.mode_stream)
                        SopListenerMode.Polling -> stringResource(R.string.mode_polling)
                        SopListenerMode.Dynamic -> stringResource(R.string.mode_dynamic)
                    }
                    val runStateLabel = when (sopState.runState) {
                        SopRunState.Idle -> stringResource(R.string.run_state_idle)
                        SopRunState.Active -> stringResource(R.string.run_state_active)
                    }
                    val statusText = when (sopState.status) {
                        SopListenerStatus.Connected -> stringResource(R.string.sop_status_streaming, modeLabel, runStateLabel)
                        SopListenerStatus.Connecting -> stringResource(R.string.sop_status_connecting)
                        SopListenerStatus.Reconnecting -> stringResource(R.string.sop_status_reconnecting)
                        SopListenerStatus.Failed -> stringResource(R.string.sop_status_failed)
                        SopListenerStatus.Registering -> stringResource(R.string.sop_status_registering)
                        SopListenerStatus.Idle -> stringResource(R.string.sop_status_idle, modeLabel, runStateLabel)
                        else -> stringResource(R.string.sop_status_mode_only, modeLabel)
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = statusColor),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier.size(8.dp),
                                shape = CircleShape,
                                color = when (sopState.status) {
                                    SopListenerStatus.Connected -> MaterialTheme.colorScheme.primary
                                    SopListenerStatus.Connecting, SopListenerStatus.Reconnecting -> MaterialTheme.colorScheme.secondary
                                    SopListenerStatus.Failed -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            ) {}
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                AnimatedContent(
                    targetState = when {
                        uiState.error != null && uiState.notifications.isEmpty() -> "error"
                        uiState.notifications.isEmpty() && !uiState.isLoading -> "empty"
                        else -> "list"
                    },
                    label = "notification-list-switch",
                    transitionSpec = {
                        (slideInHorizontally(animationSpec = tween(220)) { it } + fadeIn()) togetherWith
                            (slideOutHorizontally(animationSpec = tween(220)) { -it } + fadeOut())
                    }
                ) { state ->
                    when (state) {
                        "error" -> ErrorContent(message = uiState.error.orEmpty(), onRetry = onRefresh)
                        "empty" -> EmptyContent()
                        else -> {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(
                                    items = uiState.notifications,
                                    key = { it.id },
                                ) { notification ->
                                    NotificationItem(
                                        notification = notification,
                                        onClick = {
                                            val actionUri = notification.actionUri
                                            if (actionUri.isNotBlank()) {
                                                val uri = if (actionUri.contains("://")) actionUri else "solian://$actionUri"
                                                runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                                                }
                                            }
                                        },
                                    )
                                }

                                if (uiState.isLoadingMore) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItem(
    notification: NotificationItem,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val icon = when {
        notification.topic.contains("chat", ignoreCase = true) || notification.topic.contains("invite", ignoreCase = true) -> Icons.AutoMirrored.Filled.Chat
        notification.topic.contains("reply", ignoreCase = true) -> Icons.AutoMirrored.Filled.Comment
        notification.topic.contains("reaction", ignoreCase = true) -> Icons.Default.Favorite
        else -> Icons.Default.Notifications
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isViewed) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            },
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (notification.isViewed) 0.dp else 2.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = notification.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                if (!notification.isViewed) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge()
                }
            }
            if (notification.subtitle.isNotBlank()) {
                Text(
                    text = notification.subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (notification.content.isNotBlank()) {
                Text(
                    text = notification.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTime(context, notification.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.no_notifications_yet),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.error_with_message, message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

fun formatTime(context: Context, isoTime: String): String {
    return try {
        val zonedTime = ZonedDateTime.parse(isoTime)
        val duration = Duration.between(zonedTime, ZonedDateTime.now())

        when {
            duration.toMinutes() < 1 -> context.getString(R.string.just_now)
            duration.toMinutes() < 60 -> context.getString(R.string.time_minutes_ago, duration.toMinutes())
            duration.toHours() < 24 -> context.getString(R.string.time_hours_ago, duration.toHours())
            duration.toDays() < 7 -> context.getString(R.string.time_days_ago, duration.toDays())
            else -> zonedTime.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
        }
    } catch (e: Exception) {
        isoTime
    }
}
