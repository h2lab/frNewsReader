package org.h2lab.frnewsreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = NewsBlue,
    secondary = NewsGreen,
    background = LightBackground,
    surface = LightBackground,
    surfaceContainer = LightBackground,
    surfaceContainerLow = LightSurfaceContainerLow,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    outlineVariant = LightOutlineVariant,
)

private val DarkColorScheme = darkColorScheme(
    primary = NewsBlueDark,
    secondary = NewsGreenDark,
    background = DarkBackground,
    surface = DarkBackground,
    surfaceContainer = DarkBackground,
    surfaceContainerLow = DarkSurfaceContainerLow,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outlineVariant = DarkOutlineVariant,
)

@Composable
fun FrNewsReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
