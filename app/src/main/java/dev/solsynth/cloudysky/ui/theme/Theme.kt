package dev.solsynth.cloudysky.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = SkyPrimaryDark,
    onPrimary = SkyOnPrimaryDark,
    primaryContainer = SkyPrimaryContainerDark,
    onPrimaryContainer = SkyOnPrimaryContainerDark,
    secondary = SkySecondaryDark,
    onSecondary = SkyOnSecondaryDark,
    secondaryContainer = SkySecondaryContainerDark,
    onSecondaryContainer = SkyOnSecondaryContainerDark,
    tertiary = SkyTertiaryDark,
    onTertiary = SkyOnTertiaryDark,
    tertiaryContainer = SkyTertiaryContainerDark,
    onTertiaryContainer = SkyOnTertiaryContainerDark,
    error = SkyErrorDark,
    onError = SkyOnErrorDark,
    errorContainer = SkyErrorContainerDark,
    onErrorContainer = SkyOnErrorContainerDark,
    background = SkyBackgroundDark,
    onBackground = SkyOnBackgroundDark,
    surface = SkySurfaceDark,
    onSurface = SkyOnSurfaceDark,
    surfaceVariant = SkySurfaceVariantDark,
    onSurfaceVariant = SkyOnSurfaceVariantDark,
    outline = SkyOutlineDark,
    outlineVariant = SkyOutlineVariantDark,
    inverseSurface = SkyInverseSurfaceDark,
    inverseOnSurface = SkyInverseOnSurfaceDark,
    inversePrimary = SkyInversePrimaryDark,
    surfaceDim = SkySurfaceDimDark,
    surfaceBright = SkySurfaceBrightDark,
    surfaceContainerLowest = SkySurfaceContainerLowestDark,
    surfaceContainerLow = SkySurfaceContainerLowDark,
    surfaceContainer = SkySurfaceContainerDark,
    surfaceContainerHigh = SkySurfaceContainerHighDark,
    surfaceContainerHighest = SkySurfaceContainerHighestDark,
    scrim = Color.Black,
)

private val LightColorScheme = lightColorScheme(
    primary = SkyPrimaryLight,
    onPrimary = SkyOnPrimaryLight,
    primaryContainer = SkyPrimaryContainerLight,
    onPrimaryContainer = SkyOnPrimaryContainerLight,
    secondary = SkySecondaryLight,
    onSecondary = SkyOnSecondaryLight,
    secondaryContainer = SkySecondaryContainerLight,
    onSecondaryContainer = SkyOnSecondaryContainerLight,
    tertiary = SkyTertiaryLight,
    onTertiary = SkyOnTertiaryLight,
    tertiaryContainer = SkyTertiaryContainerLight,
    onTertiaryContainer = SkyOnTertiaryContainerLight,
    error = SkyErrorLight,
    onError = SkyOnErrorLight,
    errorContainer = SkyErrorContainerLight,
    onErrorContainer = SkyOnErrorContainerLight,
    background = SkyBackgroundLight,
    onBackground = SkyOnBackgroundLight,
    surface = SkySurfaceLight,
    onSurface = SkyOnSurfaceLight,
    surfaceVariant = SkySurfaceVariantLight,
    onSurfaceVariant = SkyOnSurfaceVariantLight,
    outline = SkyOutlineLight,
    outlineVariant = SkyOutlineVariantLight,
    inverseSurface = SkyInverseSurfaceLight,
    inverseOnSurface = SkyInverseOnSurfaceLight,
    inversePrimary = SkyInversePrimaryLight,
    surfaceDim = SkySurfaceDimLight,
    surfaceBright = SkySurfaceBrightLight,
    surfaceContainerLowest = SkySurfaceContainerLowestLight,
    surfaceContainerLow = SkySurfaceContainerLowLight,
    surfaceContainer = SkySurfaceContainerLight,
    surfaceContainerHigh = SkySurfaceContainerHighLight,
    surfaceContainerHighest = SkySurfaceContainerHighestLight,
    scrim = Color.Black,
)

@Composable
fun CloudySkyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
