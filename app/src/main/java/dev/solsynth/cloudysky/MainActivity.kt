package dev.solsynth.cloudysky

import android.Manifest
import android.annotation.SuppressLint
import android.os.PowerManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.solsynth.cloudysky.auth.AuthRepository
import dev.solsynth.cloudysky.auth.AuthScreen
import dev.solsynth.cloudysky.auth.CurrentAccount
import dev.solsynth.cloudysky.notifications.NotificationController
import dev.solsynth.cloudysky.notifications.NotificationListScreen
import dev.solsynth.cloudysky.notifications.NotificationRepository
import dev.solsynth.cloudysky.settings.AboutScreen
import dev.solsynth.cloudysky.settings.SettingsScreen
import dev.solsynth.cloudysky.sop.SopLaunchCoordinator
import dev.solsynth.cloudysky.sop.SopListenerService
import dev.solsynth.cloudysky.sop.SopRepository
import dev.solsynth.cloudysky.ui.theme.CloudySkyTheme
import kotlinx.coroutines.launch
import androidx.core.net.toUri

private enum class AppScreen {
    Notifications,
    Settings,
    About,
}

class MainActivity : ComponentActivity() {
    @SuppressLint("BatteryLife")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CloudySkyTheme {
                val context = LocalContext.current
                val authRepository = remember { AuthRepository(context) }
                val notificationRepository = remember { NotificationRepository(authRepository) }
                val sopRepository = remember { SopRepository(context) }
                val sopLaunchCoordinator = remember { SopLaunchCoordinator(context) }
                val coroutineScope = rememberCoroutineScope()
                val notificationController = remember { NotificationController(notificationRepository, coroutineScope) }
                val authState by authRepository.authState.collectAsState()
                val notificationState by notificationController.uiState.collectAsState()
                val sopState by sopRepository.listenerState.collectAsState()
                var screen by remember { mutableStateOf(AppScreen.Notifications) }
                val scope = rememberCoroutineScope()
                val launcher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
                    Log.d(TAG, "auth launcher returned: hasData=${result.data != null}")
                    authRepository.handleAuthorizationResult(result.data)
                }
                val notificationPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
                    Log.d(TAG, "notification permission granted=$granted")
                }
                val batteryOptimizationLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { _ ->
                    val powerManager = context.getSystemService(PowerManager::class.java)
                    Log.d(TAG, "battery optimization granted=${powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true}")
                }
                var currentAccount by remember { mutableStateOf<CurrentAccount?>(null) }
                var loadingAccount by remember { mutableStateOf(false) }

                LaunchedEffect(authState.isAuthorized) {
                    Log.d(TAG, "auth state changed: authorized=${authState.isAuthorized}")
                    if (!authState.isAuthorized) {
                        currentAccount = null
                        notificationController.clear()
                        SopListenerService.stop(context)
                        screen = AppScreen.Notifications
                        return@LaunchedEffect
                    }

                    loadingAccount = true
                    currentAccount = authRepository.fetchCurrentAccount()
                    loadingAccount = false
                    Log.d(TAG, "account loaded: present=${currentAccount != null}")
                    notificationController.refresh()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (sopRepository.currentState().enabled) {
                        sopLaunchCoordinator.requestStart()
                        sopLaunchCoordinator.startIfPending()
                    }
                }

                LaunchedEffect(authState.isAuthorized, screen) {
                    if (authState.isAuthorized) {
                        sopLaunchCoordinator.startIfPending()
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val targetState = if (!authState.isAuthorized) "auth" else screen.name.lowercase()
                    AnimatedContent(
                        targetState = targetState,
                        label = "app-screen-switch",
                        transitionSpec = {
                            (slideInHorizontally(animationSpec = tween(220)) { it } + fadeIn()) togetherWith
                                (slideOutHorizontally(animationSpec = tween(220)) { -it } + fadeOut())
                        }
                    ) { target ->
                        when (target) {
                            "auth" -> AuthScreen(
                                isSignedIn = false,
                                currentAccount = currentAccount,
                                loadingAccount = loadingAccount,
                                onSignIn = {
                                    Log.d(TAG, "sign in clicked")
                                    launcher.launch(authRepository.createAuthorizationIntent())
                                },
                                onSignOut = authRepository::signOut,
                                modifier = Modifier.padding(innerPadding)
                            )
                            "notifications" -> NotificationListScreen(
                                uiState = notificationState,
                                currentAccount = currentAccount,
                                onRefresh = notificationController::refresh,
                                onLoadMore = notificationController::loadMore,
                                onSettingsClick = { screen = AppScreen.Settings },
                                onSignOut = authRepository::signOut,
                            )
                            "settings" -> SettingsScreen(
                                currentAccount = currentAccount,
                                isLoadingAccount = loadingAccount,
                                sopState = sopState,
                                onBackClick = { screen = AppScreen.Notifications },
                                onAboutClick = { screen = AppScreen.About },
                                onToggleSopListener = { enabled ->
                                    sopRepository.setEnabled(enabled)
                                    if (enabled && authState.isAuthorized) {
                                        sopLaunchCoordinator.requestStart()
                                        sopLaunchCoordinator.startIfPending()
                                    } else {
                                        SopListenerService.stop(context)
                                    }
                                },
                                onOpenBatteryOptimizationSettings = {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                        .setData(Uri.parse("package:${context.packageName}"))
                                    batteryOptimizationLauncher.launch(intent)
                                },
                                onLogoutClick = authRepository::signOut,
                            )
                            "about" -> AboutScreen(
                                onBackClick = { screen = AppScreen.Settings },
                                modifier = Modifier.padding(innerPadding),
                                versionName = BuildConfig.VERSION_NAME,
                                versionCode = BuildConfig.VERSION_CODE,
                                buildType = BuildConfig.BUILD_TYPE,
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "CloudySkyMain"
    }
}
