package dev.jotalac.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.jotalac.R

/** DM Sans like the web client, from its variable font, at every weight the app uses. */
@OptIn(ExperimentalTextApi::class)
val DmSans = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.Black).map {
        Font(R.font.dm_sans, it, variationSettings = FontVariation.Settings(FontVariation.weight(it.weight)))
    }
)

private fun TextStyle.dmSans() = copy(fontFamily = DmSans)

/** Material's default type scale set in [DmSans]. */
val Typography = Typography().run {
    copy(
        displayLarge = displayLarge.dmSans(),
        displayMedium = displayMedium.dmSans(),
        displaySmall = displaySmall.dmSans(),
        headlineLarge = headlineLarge.dmSans(),
        headlineMedium = headlineMedium.dmSans(),
        headlineSmall = headlineSmall.dmSans(),
        titleLarge = titleLarge.dmSans(),
        titleMedium = titleMedium.dmSans(),
        titleSmall = titleSmall.dmSans(),
        bodyLarge = bodyLarge.dmSans(),
        bodyMedium = bodyMedium.dmSans(),
        bodySmall = bodySmall.dmSans(),
        labelLarge = labelLarge.dmSans(),
        labelMedium = labelMedium.dmSans(),
        labelSmall = labelSmall.dmSans(),
    )
}
