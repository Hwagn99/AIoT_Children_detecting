package com.childguard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SafeGreen = Color(0xFF2E7D32)
val WatchBlue = Color(0xFF1565C0)
val WarnOrange = Color(0xFFEF6C00)
val EmergencyRed = Color(0xFFC62828)

fun levelColor(level: Int): Color = when (level) {
    0 -> SafeGreen
    1 -> WatchBlue
    2 -> WarnOrange
    else -> EmergencyRed
}

private val Light = lightColorScheme(primary = Color(0xFF1565C0), secondary = Color(0xFF00897B))
private val Dark = darkColorScheme(primary = Color(0xFF90CAF9), secondary = Color(0xFF80CBC4))

@Composable
fun ChildGuardTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
