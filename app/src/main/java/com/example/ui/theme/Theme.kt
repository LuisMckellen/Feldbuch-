package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FeldbuchLightColorScheme =
  lightColorScheme(
    primary = PineInk,
    onPrimary = PaperElevated,
    primaryContainer = PaperLinen,
    onPrimaryContainer = PineInk,
    secondary = Terracotta,
    onSecondary = Color.White,
    secondaryContainer = TerracottaContainer,
    onSecondaryContainer = OnTerracottaContainer,
    tertiary = OliveBrass,
    onTertiary = Color.White,
    tertiaryContainer = OliveBrassContainer,
    onTertiaryContainer = OnOliveBrassContainer,
    background = PaperCream,
    onBackground = PineInk,
    surface = PaperCream,
    onSurface = PineInk,
    surfaceVariant = PaperLinen,
    onSurfaceVariant = PineInkMuted,
    surfaceContainerLowest = PaperElevated,
    surfaceContainerLow = PaperCream,
    surfaceContainer = PaperLinen,
    surfaceContainerHigh = Color(0xFFE7DFCF),
    outline = PaperBorder,
    outlineVariant = Color(0xFFE5DDD0),
    error = RustError,
    onError = Color.White,
    errorContainer = RustErrorContainer,
    onErrorContainer = Color(0xFF5D140E),
  )

private val FeldbuchDarkColorScheme =
  darkColorScheme(
    primary = TerracottaBright,
    onPrimary = Color(0xFF281007),
    primaryContainer = TerracottaDarkContainer,
    onPrimaryContainer = TerracottaLightText,
    secondary = SageBright,
    onSecondary = Color(0xFF14210F),
    secondaryContainer = SageDarkContainer,
    onSecondaryContainer = SageLightText,
    tertiary = TerracottaBright,
    onTertiary = Color(0xFF281007),
    tertiaryContainer = NightElevated,
    onTertiaryContainer = ParchmentText,
    background = NightSlate,
    onBackground = ParchmentText,
    surface = NightSlate,
    onSurface = ParchmentText,
    surfaceVariant = NightCard,
    onSurfaceVariant = ParchmentMuted,
    surfaceContainerLowest = Color(0xFF0C110F),
    surfaceContainerLow = NightSlate,
    surfaceContainer = NightCard,
    surfaceContainerHigh = NightElevated,
    outline = NightBorder,
    outlineVariant = Color(0xFF25302B),
    error = RustBright,
    onError = Color(0xFF3E0B07),
    errorContainer = RustDarkContainer,
    onErrorContainer = Color(0xFFFFD6D1),
  )

val FeldbuchShapes =
  Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
  )

@Composable
fun FeldbuchTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) FeldbuchDarkColorScheme else FeldbuchLightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = FeldbuchTypography,
    shapes = FeldbuchShapes,
    content = content,
  )
}
