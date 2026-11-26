package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KnowledgeLibraryTheme(
    themeMode: String = "SYSTEM",
    accentColorKey: String = "FOREST",
    uiFontKey: String = "plus_jakarta_sans",
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> systemDark
    }

    val colorScheme = remember(darkTheme, accentColorKey) {
        val palette = AccentPalette.fromKey(accentColorKey)
        if (darkTheme) {
            darkColorScheme(
                primary = palette.primaryDark,
                onPrimary = palette.onPrimaryDark,
                primaryContainer = palette.primaryContainerDark,
                onPrimaryContainer = palette.onPrimaryContainerDark,
                secondary = palette.secondaryLight,
                background = WarmBackgroundDark,
                onBackground = WarmOnSurfaceDark,
                surface = WarmSurfaceDark,
                onSurface = WarmOnSurfaceDark,
                surfaceVariant = WarmSurfaceVariantDark,
                onSurfaceVariant = WarmOnSurfaceVariantDark
            )
        } else {
            lightColorScheme(
                primary = palette.primaryLight,
                onPrimary = palette.onPrimaryLight,
                primaryContainer = palette.primaryContainerLight,
                onPrimaryContainer = palette.onPrimaryContainerLight,
                secondary = palette.secondaryLight,
                background = WarmBackgroundLight,
                onBackground = WarmOnSurfaceLight,
                surface = WarmSurfaceLight,
                onSurface = WarmOnSurfaceLight,
                surfaceVariant = WarmSurfaceVariantLight,
                onSurfaceVariant = WarmOnSurfaceVariantLight
            )
        }
    }

    val typography = remember(uiFontKey) {
        createAppTypography(uiFontKey)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = AppShapes,
        content = content
    )
}
