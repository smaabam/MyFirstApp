package com.elaalyawm.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF0F0F0F)
val Surface = Color(0xFF1A1A1A)
val Emerald = Color(0xFF00C896)
val OnSurface = Color(0xFFE8E8E8)
val Secondary = Color(0xFF888888)
val EmptyCell = Color(0xFF2A2A2A)
val Delayed = Color(0xFFE07B39)
val Qadaa = Color(0xFF4A4A4A)

private val Colors = darkColorScheme(
    primary = Emerald, onPrimary = Background, secondary = Emerald,
    background = Background, onBackground = OnSurface,
    surface = Surface, onSurface = OnSurface, surfaceVariant = EmptyCell, onSurfaceVariant = Secondary,
    error = Delayed
)

@Composable fun ElaTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Colors, content = content)

