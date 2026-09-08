package com.hitrocker.game2048.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Orange40,
    background = Beige90,
    surface = Beige90,
    onBackground = Brown40,
    onSurface = Brown40
)

/**
 * App-wide Material 3 theme. The classic 2048 look is always the light/beige palette, regardless of
 * the system dark-mode setting, so the recognizable tile colors stay consistent.
 */
@Composable
fun Game2048Theme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content
    )
}
