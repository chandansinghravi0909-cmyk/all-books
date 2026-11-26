package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoCameraFront
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

object NotebookIcons {
    val allChoices: List<Pair<String, ImageVector>> = listOf(
        "public" to Icons.Filled.Public,
        "spellcheck" to Icons.Filled.Spellcheck,
        "record_voice_over" to Icons.Filled.RecordVoiceOver,
        "psychology" to Icons.Filled.Psychology,
        "fitness_center" to Icons.Filled.FitnessCenter,
        "video_camera_front" to Icons.Filled.VideoCameraFront,
        "bookmark_added" to Icons.Filled.BookmarkAdded,
        "edit_note" to Icons.Filled.EditNote,
        "checklist" to Icons.Filled.Checklist,
        "account_balance_wallet" to Icons.Filled.AccountBalanceWallet,
        "auto_stories" to Icons.Filled.AutoStories,
        "folder_special" to Icons.Filled.FolderSpecial,
        "draw" to Icons.Filled.Draw,
        "menu_book" to Icons.AutoMirrored.Filled.MenuBook,
        "science" to Icons.Filled.Science,
        "lightbulb" to Icons.Filled.Lightbulb,
        "star" to Icons.Filled.Star,
        "work" to Icons.Filled.Work,
        "article" to Icons.Filled.Article
    )

    fun resolve(key: String): ImageVector =
        allChoices.firstOrNull { it.first == key }?.second ?: Icons.AutoMirrored.Filled.MenuBook

    val coverColors: List<Pair<String, Long>> = listOf(
        "Forest" to 0xFF1B4332L,
        "Cobalt" to 0xFF1E3A8AL,
        "Teal" to 0xFF0F766EL,
        "Plum" to 0xFF6D28D9L,
        "Crimson" to 0xFFB91C1CL,
        "Terracotta" to 0xFFC2410CL,
        "Sky" to 0xFF0369A1L,
        "Indigo" to 0xFF4338CAL,
        "Emerald" to 0xFF15803DL,
        "Ochre" to 0xFF92400EL,
        "Mahogany" to 0xFF7C2D12L,
        "Slate" to 0xFF475569L
    )

    val coverStyles: List<String> = listOf("LEATHER", "LINEN", "SPIRAL", "MINIMAL", "CLASSIC")
}

private val shortDateFormat = ThreadLocal.withInitial {
    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
}

fun formatTimestampShort(timestamp: Timestamp?): String {
    if (timestamp == null) return "Just now"
    return shortDateFormat.get()?.format(timestamp.toDate()) ?: "Just now"
}
