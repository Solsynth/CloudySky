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
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
import dev.solsynth.cloudysky.sop.SopNotificationLogger
import dev.solsynth.cloudysky.sop.SopRepository
import dev.solsynth.cloudysky.ui.theme.CloudySkyTheme

private object Routes {
    const val AUTH = "auth"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
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
                val navController = rememberNavController()
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
                val sopNotificationLogger = remember { SopNotificationLogger(context) }
                var logEntries by remember { mutableStateOf(sopNotificationLogger.getEntries()) }

                val performLogout: () -> Unit = {
                    coroutineScope.launch {
                        SopListenerService.stop(context)
                        sopRepository.deleteSubscriptionAndClear()
                        authRepository.signOut()
                    }
                }

                LaunchedEffect(authState.isAuthorized) {
                    Log.d(TAG, "auth state changed: authorized=${authState.isAuthorized}")
                    if (!authState.isAuthorized) {
                        currentAccount = null
                        notificationController.clear()
                        SopListenerService.stop(context)
                        navController.navigate(Routes.AUTH) {
                            popUpTo(0) { inclusive = true }
                        }
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

                LaunchedEffect(authState.isAuthorized) {
                    if (authState.isAuthorized) {
                        navController.navigate(Routes.NOTIFICATIONS) {
                            popUpTo(Routes.AUTH) { inclusive = true }
                        }
                        sopLaunchCoordinator.startIfPending()
                    }
                }

                val startDestination = if (authState.isAuthorized) Routes.NOTIFICATIONS else Routes.AUTH

                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(220)
                        ) + fadeIn(animationSpec = tween(220))
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(220)
                        ) + fadeOut(animationSpec = tween(220))
                    },
                    popEnterTransition = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(220)
                        ) + fadeIn(animationSpec = tween(220))
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(220)
                        ) + fadeOut(animationSpec = tween(220))
                    },
                ) {
                    composable(Routes.AUTH) {
                        AuthScreen(
                            isSignedIn = false,
                            currentAccount = currentAccount,
                            loadingAccount = loadingAccount,
                            onSignIn = {
                                Log.d(TAG, "sign in clicked")
                                launcher.launch(authRepository.createAuthorizationIntent())
                            },
                            onSignOut = performLogout,
                        )
                    }

                    composable(Routes.NOTIFICATIONS) {
                        NotificationListScreen(
                            uiState = notificationState,
                            currentAccount = currentAccount,
                            sopState = sopState,
                            onRefresh = notificationController::refresh,
                            onLoadMore = notificationController::loadMore,
                            onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                            onSignOut = performLogout,
                            onOpenBatteryOptimizationSettings = {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                    .setData(Uri.parse("package:${context.packageName}"))
                                batteryOptimizationLauncher.launch(intent)
                            },
                        )
                    }

                    composable(Routes.SETTINGS) {
                        LaunchedEffect(Unit) {
                            logEntries = sopNotificationLogger.getEntries()
                        }
                        SettingsScreen(
                            currentAccount = currentAccount,
                            isLoadingAccount = loadingAccount,
                            sopState = sopState,
                            onBackClick = { navController.popBackStack() },
                            onAboutClick = { navController.navigate(Routes.ABOUT) },
                            onToggleSopListener = { enabled ->
                                sopRepository.setEnabled(enabled)
                                if (enabled && authState.isAuthorized) {
                                    sopLaunchCoordinator.requestStart()
                                    sopLaunchCoordinator.startIfPending()
                                } else {
                                    SopListenerService.stop(context)
                                }
                            },
                            onSetMode = { mode ->
                                sopRepository.setMode(mode)
                                SopListenerService.stop(context)
                                if (sopState.enabled && authState.isAuthorized) {
                                    sopLaunchCoordinator.requestStart()
                                    sopLaunchCoordinator.startIfPending()
                                }
                            },
                            onSetDynamicConfig = sopRepository::setDynamicConfig,
                            onSetAutoStartOnBoot = sopRepository::setAutoStartOnBoot,
                            onSetSilentMode = { enabled ->
                                sopRepository.setSilentMode(enabled)
                                SopListenerService.updateNotification(context)
                            },
                            onOpenBatteryOptimizationSettings = {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                    .setData(Uri.parse("package:${context.packageName}"))
                                batteryOptimizationLauncher.launch(intent)
                            },
                            onLogoutClick = performLogout,
                            logEntries = logEntries,
                            onClearLog = {
                                sopNotificationLogger.clearLog()
                                logEntries = emptyList()
                            },
                        )
                    }

                    composable(Routes.ABOUT) {
                        AboutScreen(
                            onBackClick = { navController.popBackStack() },
                            versionName = BuildConfig.VERSION_NAME,
                            versionCode = BuildConfig.VERSION_CODE,
                            buildType = BuildConfig.BUILD_TYPE,
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "CloudySkyMain"
    }
}
