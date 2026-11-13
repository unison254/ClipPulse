package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ClipPulseColorScheme = darkColorScheme(
    primary = ElectricCoral,
    onPrimary = TextPrimary,
    primaryContainer = ElectricCoralSoft.copy(alpha = 0.2f),
    onPrimaryContainer = ElectricCoralSoft,
    secondary = ElectricCyan,
    onSecondary = StudioObsidian,
    secondaryContainer = ElectricCyan.copy(alpha = 0.16f),
    onSecondaryContainer = ElectricCyan,
    tertiary = NeonLime,
    onTertiary = StudioObsidian,
    background = StudioObsidian,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = StudioObsidian,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceHighlight,
    outline = StudioOutline,
    outlineVariant = StudioOutline.copy(alpha = 0.6f),
    error = ErrorRed,
    onError = StudioObsidian
)

private val ClipPulseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ClipPulseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ClipPulseColorScheme,
        typography = ClipPulseTypography,
        shapes = ClipPulseShapes,
        content = content
    )
}
