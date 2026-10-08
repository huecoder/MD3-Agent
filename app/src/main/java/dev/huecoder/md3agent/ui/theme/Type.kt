package dev.huecoder.md3agent.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.huecoder.md3agent.R

private val GoogleSans = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(
            resId = R.font.google_sans,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

private fun TextStyle.inGoogleSans() = copy(fontFamily = GoogleSans)

val AssistantTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.inGoogleSans(),
        displayMedium = displayMedium.inGoogleSans(),
        displaySmall = displaySmall.inGoogleSans(),
        headlineLarge = headlineLarge.inGoogleSans(),
        headlineMedium = headlineMedium.inGoogleSans(),
        headlineSmall = headlineSmall.inGoogleSans(),
        titleLarge = titleLarge.inGoogleSans(),
        titleMedium = titleMedium.inGoogleSans(),
        titleSmall = titleSmall.inGoogleSans(),
        bodyLarge = bodyLarge.inGoogleSans(),
        bodyMedium = bodyMedium.inGoogleSans(),
        bodySmall = bodySmall.inGoogleSans(),
        labelLarge = labelLarge.inGoogleSans(),
        labelMedium = labelMedium.inGoogleSans(),
        labelSmall = labelSmall.inGoogleSans(),
        displayLargeEmphasized = displayLargeEmphasized.inGoogleSans(),
        displayMediumEmphasized = displayMediumEmphasized.inGoogleSans(),
        displaySmallEmphasized = displaySmallEmphasized.inGoogleSans(),
        headlineLargeEmphasized = headlineLargeEmphasized.inGoogleSans(),
        headlineMediumEmphasized = headlineMediumEmphasized.inGoogleSans(),
        headlineSmallEmphasized = headlineSmallEmphasized.inGoogleSans(),
        titleLargeEmphasized = titleLargeEmphasized.inGoogleSans(),
        titleMediumEmphasized = titleMediumEmphasized.inGoogleSans(),
        titleSmallEmphasized = titleSmallEmphasized.inGoogleSans(),
        bodyLargeEmphasized = bodyLargeEmphasized.inGoogleSans(),
        bodyMediumEmphasized = bodyMediumEmphasized.inGoogleSans(),
        bodySmallEmphasized = bodySmallEmphasized.inGoogleSans(),
        labelLargeEmphasized = labelLargeEmphasized.inGoogleSans(),
        labelMediumEmphasized = labelMediumEmphasized.inGoogleSans(),
        labelSmallEmphasized = labelSmallEmphasized.inGoogleSans(),
    )
}

val Typography.heroTitle: TextStyle
    get() = displaySmallEmphasized.copy(fontWeight = FontWeight.Bold)
