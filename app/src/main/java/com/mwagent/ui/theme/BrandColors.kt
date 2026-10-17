package com.mwagent.ui.theme

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object DynamicColorSupport {
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    val isAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

object BrandColors {
    val dark: ColorScheme = darkColorScheme(
        primary = Color(0xFFB0C6FF),
        onPrimary = Color(0xFF152E60),
        primaryContainer = Color(0xFF2D4578),
        onPrimaryContainer = Color(0xFFD9E2FF),
        secondaryContainer = Color(0xFF3F4759),
        onSecondaryContainer = Color(0xFFDBE2F9),
        surface = Color(0xFF121318),
        surfaceContainer = Color(0xFF1E1F25),
        surfaceContainerHigh = Color(0xFF282A2F),
        surfaceContainerHighest = Color(0xFF33353A),
    )

    val light: ColorScheme = lightColorScheme(
        primary = Color(0xFF455D93),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD9E2FF),
        onPrimaryContainer = Color(0xFF2D4578),
        secondaryContainer = Color(0xFFDBE2F9),
        onSecondaryContainer = Color(0xFF3F4759),
        surface = Color(0xFFFAF8FF),
        surfaceContainer = Color(0xFFEEEDF4),
        surfaceContainerHigh = Color(0xFFE8E7EF),
        surfaceContainerHighest = Color(0xFFE2E2E9),
    )
}
