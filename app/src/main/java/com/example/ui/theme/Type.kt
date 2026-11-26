package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val PlayfairDisplayFontFamily = FontFamily(Font(R.font.playfair_display, FontWeight.Normal))
val PlusJakartaSansFontFamily = FontFamily(Font(R.font.plus_jakarta_sans, FontWeight.Normal))
val LoraFontFamily = FontFamily(Font(R.font.lora, FontWeight.Normal))
val JetBrainsMonoFontFamily = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))
val KalamFontFamily = FontFamily(Font(R.font.kalam, FontWeight.Normal))
val CaveatFontFamily = FontFamily(Font(R.font.caveat, FontWeight.Normal))
val PatrickHandFontFamily = FontFamily(Font(R.font.patrick_hand, FontWeight.Normal))
val NotoSansDevanagariFontFamily = FontFamily(Font(R.font.noto_sans_devanagari, FontWeight.Normal))

data class FontChoice(
    val key: String,
    val displayName: String,
    val isHandwritingStyle: Boolean,
    val supportsHindiWell: Boolean,
    val family: FontFamily
)

object AppFontCatalog {
    val normalFonts = listOf(
        FontChoice("plus_jakarta_sans", "Plus Jakarta Sans", false, false, PlusJakartaSansFontFamily),
        FontChoice("lora", "Lora Book Serif", false, false, LoraFontFamily),
        FontChoice("noto_sans_devanagari", "Noto Sans (Hindi + English)", false, true, NotoSansDevanagariFontFamily),
        FontChoice("jetbrains_mono", "JetBrains Mono", false, false, JetBrainsMonoFontFamily)
    )

    val handwritingFonts = listOf(
        FontChoice("kalam", "Kalam (English + Hindi)", true, true, KalamFontFamily),
        FontChoice("patrick_hand", "Patrick Hand", true, false, PatrickHandFontFamily),
        FontChoice("caveat", "Caveat Script", true, false, CaveatFontFamily)
    )

    val allFonts = normalFonts + handwritingFonts + listOf(
        FontChoice("playfair_display", "Playfair Display", false, false, PlayfairDisplayFontFamily)
    )

    fun resolve(key: String): FontFamily =
        allFonts.firstOrNull { it.key == key }?.family ?: PlusJakartaSansFontFamily
}

fun createAppTypography(uiFontKey: String = "plus_jakarta_sans"): Typography {
    val bodyFamily = AppFontCatalog.resolve(uiFontKey)
    val displayFamily = PlayfairDisplayFontFamily

    return Typography(
        displaySmall = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp,
            lineHeight = 40.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 32.sp
        ),
        headlineSmall = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            lineHeight = 28.sp
        ),
        titleLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp
        ),
        titleMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp
        ),
        titleSmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        bodySmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp
        ),
        labelLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        labelMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp
        ),
        labelSmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 14.sp
        )
    )
}
