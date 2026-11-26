package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class AccentPalette(
    val key: String,
    val label: String,
    val primaryLight: Color,
    val onPrimaryLight: Color,
    val primaryContainerLight: Color,
    val onPrimaryContainerLight: Color,
    val secondaryLight: Color,
    val primaryDark: Color,
    val onPrimaryDark: Color,
    val primaryContainerDark: Color,
    val onPrimaryContainerDark: Color
) {
    FOREST(
        key = "FOREST",
        label = "Library Forest",
        primaryLight = Color(0xFF1B4332),
        onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFD8F3DC),
        onPrimaryContainerLight = Color(0xFF081C15),
        secondaryLight = Color(0xFF8C5A2B),
        primaryDark = Color(0xFF74C69D),
        onPrimaryDark = Color(0xFF081C15),
        primaryContainerDark = Color(0xFF1B4332),
        onPrimaryContainerDark = Color(0xFFD8F3DC)
    ),
    COBALT(
        key = "COBALT",
        label = "Ink Cobalt",
        primaryLight = Color(0xFF1E3A8A),
        onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFDBEAFE),
        onPrimaryContainerLight = Color(0xFF172554),
        secondaryLight = Color(0xFF0F766E),
        primaryDark = Color(0xFF93C5FD),
        onPrimaryDark = Color(0xFF172554),
        primaryContainerDark = Color(0xFF1E3A8A),
        onPrimaryContainerDark = Color(0xFFDBEAFE)
    ),
    TERRACOTTA(
        key = "TERRACOTTA",
        label = "Terracotta Cloth",
        primaryLight = Color(0xFF9A3412),
        onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFFFEDD5),
        onPrimaryContainerLight = Color(0xFF431407),
        secondaryLight = Color(0xFF4D7C0F),
        primaryDark = Color(0xFFFDBA74),
        onPrimaryDark = Color(0xFF431407),
        primaryContainerDark = Color(0xFF7C2D12),
        onPrimaryContainerDark = Color(0xFFFFEDD5)
    ),
    AMBER(
        key = "AMBER",
        label = "Warm Ochre",
        primaryLight = Color(0xFF92400E),
        onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFFEF3C7),
        onPrimaryContainerLight = Color(0xFF451A03),
        secondaryLight = Color(0xFF1E40AF),
        primaryDark = Color(0xFFFCD34D),
        onPrimaryDark = Color(0xFF451A03),
        primaryContainerDark = Color(0xFF78350F),
        onPrimaryContainerDark = Color(0xFFFEF3C7)
    ),
    PLUM(
        key = "PLUM",
        label = "Royal Plum",
        primaryLight = Color(0xFF5B21B6),
        onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFEDE9FE),
        onPrimaryContainerLight = Color(0xFF2E1065),
        secondaryLight = Color(0xFF0E7490),
        primaryDark = Color(0xFFC4B5FD),
        onPrimaryDark = Color(0xFF2E1065),
        primaryContainerDark = Color(0xFF4C1D95),
        onPrimaryContainerDark = Color(0xFFEDE9FE)
    ),
    SLATE(
        key = "SLATE",
        label = "Editorial Slate",
        primaryLight = Color(0xFF334155),
        onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFE2E8F0),
        onPrimaryContainerLight = Color(0xFF0F172A),
        secondaryLight = Color(0xFFB45309),
        primaryDark = Color(0xFFCBD5E1),
        onPrimaryDark = Color(0xFF0F172A),
        primaryContainerDark = Color(0xFF1E293B),
        onPrimaryContainerDark = Color(0xFFE2E8F0)
    );

    companion object {
        fun fromKey(key: String): AccentPalette =
            entries.firstOrNull { it.key == key } ?: FOREST
    }
}

enum class PaperTone(
    val key: String,
    val label: String,
    val backgroundColor: Color,
    val marginLineColor: Color,
    val defaultInkColor: Color
) {
    IVORY("IVORY", "Warm Ivory", Color(0xFFFDFBF5), Color(0xFFD64545), Color(0xFF1C2430)),
    CREAM("CREAM", "Classic Cream", Color(0xFFFAF3E0), Color(0xFFC94A4A), Color(0xFF231F20)),
    WHITE("WHITE", "Crisp White", Color(0xFFFFFFFF), Color(0xFFE05252), Color(0xFF111827)),
    SEPIA("SEPIA", "Vintage Sepia", Color(0xFFF3E9D2), Color(0xFFB84A39), Color(0xFF2E2218)),
    MINT("MINT", "Eye-Care Mint", Color(0xFFF1F8F4), Color(0xFFD15B5B), Color(0xFF14281D));

    companion object {
        fun fromKey(key: String): PaperTone =
            entries.firstOrNull { it.key == key } ?: IVORY
    }
}

enum class RuledLineTone(
    val key: String,
    val label: String,
    val lineColor: Color
) {
    BLUE("BLUE", "Classic School Blue", Color(0xFF93C5FD)),
    TEAL("TEAL", "Soft Teal", Color(0xFF81C7C0)),
    SLATE("SLATE", "Subtle Slate", Color(0xFFCBD5E1)),
    SEPIA("SEPIA", "Warm Sepia", Color(0xFFD6BFA3));

    companion object {
        fun fromKey(key: String): RuledLineTone =
            entries.firstOrNull { it.key == key } ?: BLUE
    }
}

object InkSwatches {
    val options = listOf(
        "Ballpoint Blue" to 0xFF1B3F8BL,
        "Pen Black" to 0xFF1C2430L,
        "Royal Indigo" to 0xFF312E81L,
        "Forest Ink" to 0xFF14532DL,
        "Burgundy" to 0xFF881337L
    )
}

val WarmBackgroundLight = Color(0xFFF8F6F1)
val WarmSurfaceLight = Color(0xFFFFFFFF)
val WarmSurfaceVariantLight = Color(0xFFEEEAE0)
val WarmOnSurfaceLight = Color(0xFF1A1C19)
val WarmOnSurfaceVariantLight = Color(0xFF52564F)

val WarmBackgroundDark = Color(0xFF121412)
val WarmSurfaceDark = Color(0xFF1B1E1B)
val WarmSurfaceVariantDark = Color(0xFF272B27)
val WarmOnSurfaceDark = Color(0xFFEDEBE4)
val WarmOnSurfaceVariantDark = Color(0xFFB7B9B0)
