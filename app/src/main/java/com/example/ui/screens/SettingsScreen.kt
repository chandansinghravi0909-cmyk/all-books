package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.platform.ExportBackupManager
import com.example.data.model.AppPreferencesEntity
import com.example.data.model.NotebookEntity
import com.example.data.model.SubtopicEntity
import com.example.data.model.TopicEntity
import com.example.ui.components.NotebookEditorDialog
import com.example.ui.components.NotebookIcons
import com.example.ui.components.SubtopicCreateDialog
import com.example.ui.components.TopicCreateDialog
import com.example.ui.theme.AccentPalette
import com.example.ui.theme.AppFontCatalog
import com.example.ui.theme.PaperTone
import com.example.ui.theme.RuledLineTone
import com.example.ui.viewmodel.LibrarySnapshot
import com.example.ui.viewmodel.TopicGenUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    snapshot: LibrarySnapshot,
    userEmail: String?,
    userId: String,
    topicGenState: TopicGenUiState,
    onBack: () -> Unit,
    onSavePreferences: (AppPreferencesEntity) -> Unit,
    onCreateNotebook: (
        name: String,
        purpose: String,
        shortDesc: String,
        detailedDesc: String,
        iconKey: String,
        colorHex: Long,
        coverStyle: String
    ) -> Unit,
    onUpdateNotebook: (
        notebookId: String,
        name: String,
        purpose: String,
        shortDesc: String,
        detailedDesc: String,
        iconKey: String,
        colorHex: Long,
        coverStyle: String
    ) -> Unit,
    onTogglePinNotebook: (NotebookEntity) -> Unit,
    onToggleHideNotebook: (NotebookEntity) -> Unit,
    onToggleArchiveNotebook: (NotebookEntity) -> Unit,
    onSwapNotebookOrder: (NotebookEntity, NotebookEntity) -> Unit,
    onDeleteNotebook: (String) -> Unit,
    onCreateTopic: (TopicEntity, List<String>) -> Unit,
    onGenerateTopicWithAi: (notebookName: String, notebookPurpose: String, topicName: String) -> Unit,
    onResetTopicAiGen: () -> Unit,
    onDeleteTopic: (String) -> Unit,
    onCreateSubtopic: (SubtopicEntity) -> Unit,
    onDeleteSubtopic: (String) -> Unit,
    onRestoreFromJson: (String) -> Unit,
    onPostMessage: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val prefs = snapshot.preferences

    var showCreateNotebook by remember { mutableStateOf(false) }
    var editingNotebook by remember { mutableStateOf<NotebookEntity?>(null) }
    var deleteCandidateNotebook by remember { mutableStateOf<NotebookEntity?>(null) }

    var selectedTopicNotebookId by remember(snapshot.notebooks) {
        mutableStateOf(snapshot.notebooks.firstOrNull()?.id ?: "")
    }
    var showNotebookPicker by remember { mutableStateOf(false) }
    var showCreateTopicDialog by remember { mutableStateOf(false) }
    var addSubtopicParent by remember { mutableStateOf<TopicEntity?>(null) }
    var deleteCandidateTopic by remember { mutableStateOf<TopicEntity?>(null) }

    var appNameDraft by remember(prefs.customAppName) { mutableStateOf(prefs.customAppName) }
    var selectedExportNotebook by remember(snapshot.notebooks) {
        mutableStateOf(snapshot.notebooks.firstOrNull())
    }
    var showExportNotebookMenu by remember { mutableStateOf(false) }

    // JSON Restore Document Picker
    val jsonRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!content.isNullOrBlank()) {
                    onRestoreFromJson(content)
                } else {
                    onPostMessage("Selected backup file is empty.")
                }
            } catch (e: Exception) {
                onPostMessage("Unable to read backup file: ${e.localizedMessage}")
            }
        }
    }

    // Save JSON Backup directly to user-picked location
    val jsonCreateDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = ExportBackupManager.buildBackupJsonString(
                    notebooks = snapshot.notebooks,
                    topics = snapshot.topics,
                    subtopics = snapshot.subtopics,
                    pages = snapshot.pages,
                    attachments = snapshot.attachments,
                    fitnessLogs = snapshot.fitnessLogs,
                    vocabulary = snapshot.vocabulary,
                    todos = snapshot.todos
                )
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                onPostMessage("Full JSON backup saved successfully.")
            } catch (e: Exception) {
                onPostMessage("Failed to save JSON backup: ${e.localizedMessage}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Dashboard")
                    }
                },
                title = {
                    Column {
                        Text("Settings & Customization", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Personalize notebooks, themes, writing paper, voice, AI & backups",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Notebook Management
            item {
                SettingsSectionCard(
                    number = "1",
                    title = "Notebook Management",
                    subtitle = "Create, rename, change icons & covers, reorder, pin, hide, archive, or delete notebooks."
                ) {
                    Button(
                        onClick = { showCreateNotebook = true },
                        modifier = Modifier.fillMaxWidth().testTag("settings_add_notebook_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create New Custom Notebook")
                    }

                    Spacer(Modifier.height(10.dp))

                    snapshot.notebooks.forEachIndexed { idx, nb ->
                        val nbTopicCount = snapshot.topics.count { it.notebookId == nb.id }
                        val nbPageCount = snapshot.pages.count { it.notebookId == nb.id }
                        Surface(
                            tonalElevation = 1.dp,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(Color(nb.coverColorHex))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = NotebookIcons.resolve(nb.iconKey),
                                        contentDescription = null,
                                        tint = Color(nb.coverColorHex),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${nb.notebookNumber.toString().padStart(2, '0')} • ${nb.name}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "$nbTopicCount topics • $nbPageCount pages" +
                                                (if (nb.archived) " • Archived" else "") +
                                                (if (nb.hidden) " • Hidden" else ""),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { editingNotebook = nb }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Rename or customize notebook")
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { if (idx > 0) onSwapNotebookOrder(nb, snapshot.notebooks[idx - 1]) },
                                        enabled = idx > 0
                                    ) {
                                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            if (idx < snapshot.notebooks.lastIndex) {
                                                onSwapNotebookOrder(nb, snapshot.notebooks[idx + 1])
                                            }
                                        },
                                        enabled = idx < snapshot.notebooks.lastIndex
                                    ) {
                                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { onTogglePinNotebook(nb) }) {
                                        Icon(
                                            imageVector = if (nb.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                            contentDescription = if (nb.pinned) "Unpin" else "Pin",
                                            tint = if (nb.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(onClick = { onToggleHideNotebook(nb) }) {
                                        Icon(
                                            imageVector = if (nb.hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = if (nb.hidden) "Show on dashboard" else "Hide from dashboard",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(onClick = { onToggleArchiveNotebook(nb) }) {
                                        Icon(
                                            imageVector = if (nb.archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                            contentDescription = if (nb.archived) "Restore notebook" else "Archive notebook",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(onClick = { deleteCandidateNotebook = nb }) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Delete notebook",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Topic & Category Management
            item {
                val activeNb = snapshot.notebooks.firstOrNull { it.id == selectedTopicNotebookId }
                val nbTopics = snapshot.topics.filter { it.notebookId == selectedTopicNotebookId }

                SettingsSectionCard(
                    number = "2",
                    title = "Topic & Category Management",
                    subtitle = "Add or remove topics and subtopics inside any notebook."
                ) {
                    Box {
                        OutlinedButton(
                            onClick = { showNotebookPicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(activeNb?.let { "Notebook ${it.notebookNumber}: ${it.name}" } ?: "Select Notebook")
                        }
                        DropdownMenu(expanded = showNotebookPicker, onDismissRequest = { showNotebookPicker = false }) {
                            snapshot.notebooks.forEach { nb ->
                                DropdownMenuItem(
                                    text = { Text("${nb.notebookNumber}. ${nb.name}") },
                                    onClick = {
                                        selectedTopicNotebookId = nb.id
                                        showNotebookPicker = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (activeNb != null) {
                        OutlinedButton(
                            onClick = { showCreateTopicDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Topic to ${activeNb.name}")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (nbTopics.isEmpty()) {
                        Text(
                            text = "No topics in this notebook yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        nbTopics.forEach { topic ->
                            val subs = snapshot.subtopics.filter { it.topicId == topic.id }
                            Surface(
                                tonalElevation = 1.dp,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(topic.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "${subs.size} subtopics",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        TextButton(onClick = { addSubtopicParent = topic }) {
                                            Text("+ Subtopic")
                                        }
                                        IconButton(onClick = { deleteCandidateTopic = topic }) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "Delete topic",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    if (subs.isNotEmpty()) {
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            subs.forEach { sub ->
                                                FilterChip(
                                                    selected = false,
                                                    onClick = { onDeleteSubtopic(sub.id) },
                                                    label = { Text("${sub.name} ✕", style = MaterialTheme.typography.labelSmall) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Appearance & Themes
            item {
                SettingsSectionCard(
                    number = "3",
                    title = "Appearance & Themes",
                    subtitle = "Customize dashboard title, Light/Dark mode, accent color, and UI typography."
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = appNameDraft,
                            onValueChange = { appNameDraft = it },
                            label = { Text("Custom Dashboard App Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("settings_app_name_input")
                        )
                        Button(
                            onClick = {
                                onSavePreferences(prefs.copy(customAppName = appNameDraft.ifBlank { "Knowledge Library" }))
                            },
                            modifier = Modifier.testTag("settings_save_app_name_button")
                        ) {
                            Text("Save")
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("Theme Mode", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark").forEach { (key, label) ->
                            FilterChip(
                                selected = prefs.themeMode == key,
                                onClick = { onSavePreferences(prefs.copy(themeMode = key)) },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("Accent Palette", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AccentPalette.entries.forEach { pal ->
                            val selected = prefs.accentColorKey == pal.key
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    onSavePreferences(prefs.copy(accentColorKey = pal.key))
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(pal.primaryLight)
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                )
                                Text(pal.label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("UI Font", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppFontCatalog.normalFonts.forEach { font ->
                            FilterChip(
                                selected = prefs.uiFontKey == font.key,
                                onClick = { onSavePreferences(prefs.copy(uiFontKey = font.key)) },
                                label = { Text(font.displayName) }
                            )
                        }
                    }
                }
            }

            // 4. Writing & Paper Settings
            item {
                SettingsSectionCard(
                    number = "4",
                    title = "Writing & Paper Settings",
                    subtitle = "Default fonts, size, ruled paper shade, ruled line tint, and line spacing for new pages."
                ) {
                    Text("Default Normal Writing Font", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppFontCatalog.normalFonts.forEach { font ->
                            FilterChip(
                                selected = prefs.writingFontKey == font.key,
                                onClick = { onSavePreferences(prefs.copy(writingFontKey = font.key)) },
                                label = { Text(font.displayName) }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Default Handwriting-Style Font", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppFontCatalog.handwritingFonts.forEach { font ->
                            FilterChip(
                                selected = prefs.handwritingFontKey == font.key,
                                onClick = { onSavePreferences(prefs.copy(handwritingFontKey = font.key)) },
                                label = { Text(font.displayName) }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Default Font Size: ${prefs.defaultFontSizeSp} sp", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = prefs.defaultFontSizeSp.toFloat(),
                        onValueChange = { value ->
                            onSavePreferences(prefs.copy(defaultFontSizeSp = value.roundToInt().coerceIn(12, 30)))
                        },
                        valueRange = 12f..30f
                    )

                    Text("Default Paper Background", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaperTone.entries.forEach { tone ->
                            FilterChip(
                                selected = prefs.paperColorKey == tone.key,
                                onClick = { onSavePreferences(prefs.copy(paperColorKey = tone.key)) },
                                label = { Text(tone.label) }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Ruled Line Color", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RuledLineTone.entries.forEach { line ->
                            FilterChip(
                                selected = prefs.ruledLineColorKey == line.key,
                                onClick = { onSavePreferences(prefs.copy(ruledLineColorKey = line.key)) },
                                label = { Text(line.label) }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Default Line Spacing", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1.35 to "Compact", 1.55 to "Standard Ruled", 1.85 to "Wide").forEach { (spacing, label) ->
                            FilterChip(
                                selected = kotlin.math.abs(prefs.lineSpacingMultiplier - spacing) < 0.08,
                                onClick = { onSavePreferences(prefs.copy(lineSpacingMultiplier = spacing)) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // 5. Language & Voice Settings
            item {
                SettingsSectionCard(
                    number = "5",
                    title = "Language & Voice Settings",
                    subtitle = "Support English, Hindi (हिन्दी), and mixed Hinglish notes and speech dictation."
                ) {
                    Text("Preferred Content Language", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "ENGLISH" to "English",
                            "HINDI" to "Hindi (हिन्दी)",
                            "MIXED" to "Mixed (Hindi + English)"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = prefs.preferredLanguage == key,
                                onClick = { onSavePreferences(prefs.copy(preferredLanguage = key)) },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Default Voice-to-Text Language", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "en-IN" to "English (India)",
                            "en-US" to "English (US)",
                            "hi-IN" to "Hindi (हिन्दी)"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = prefs.voiceInputLanguage == key,
                                onClick = { onSavePreferences(prefs.copy(voiceInputLanguage = key)) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // 6. AI Configuration
            item {
                SettingsSectionCard(
                    number = "6",
                    title = "AI Configuration",
                    subtitle = "Powered securely via Firebase AI Logic + App Check (no manual API key required)."
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Gemini AI Assistant", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Context-aware explanations, summaries, and topic generation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.aiEnabled,
                            onCheckedChange = { onSavePreferences(prefs.copy(aiEnabled = it)) },
                            modifier = Modifier.testTag("settings_ai_enabled_switch")
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("AI Response Language", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "ENGLISH" to "English",
                            "HINDI" to "Hindi",
                            "MIXED" to "Match User / Bilingual"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = prefs.aiResponseLanguage == key,
                                onClick = { onSavePreferences(prefs.copy(aiResponseLanguage = key)) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // 7. Storage & Backup
            item {
                SettingsSectionCard(
                    number = "7",
                    title = "Storage, Cloud Sync & Account",
                    subtitle = "Cloud Firestore persistence with offline cache isolated to your Google Account."
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(userEmail ?: "Signed in with Google", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Account ID: ${userId.take(12)}… • ${snapshot.notebooks.size} notebooks • ${snapshot.pages.size} pages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                val file = ExportBackupManager.exportFullLibraryJson(
                                    context = context,
                                    notebooks = snapshot.notebooks,
                                    topics = snapshot.topics,
                                    subtopics = snapshot.subtopics,
                                    pages = snapshot.pages,
                                    attachments = snapshot.attachments,
                                    fitnessLogs = snapshot.fitnessLogs,
                                    vocabulary = snapshot.vocabulary,
                                    todos = snapshot.todos
                                )
                                ExportBackupManager.shareFile(context, file, "application/json")
                            },
                            modifier = Modifier.weight(1f).testTag("settings_share_backup_button")
                        ) {
                            Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share Backup")
                        }
                        OutlinedButton(
                            onClick = {
                                jsonCreateDocLauncher.launch("knowledge_library_backup.json")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Backup File")
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onSignOut,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().testTag("settings_sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }

            // 8. Data Export & Restore
            item {
                SettingsSectionCard(
                    number = "8",
                    title = "Data Export & Restore",
                    subtitle = "Export any notebook as PDF or Markdown, or safely restore from a JSON backup."
                ) {
                    Box {
                        OutlinedButton(
                            onClick = { showExportNotebookMenu = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedExportNotebook?.let { "Export Notebook: ${it.name}" } ?: "Select Notebook")
                        }
                        DropdownMenu(expanded = showExportNotebookMenu, onDismissRequest = { showExportNotebookMenu = false }) {
                            snapshot.notebooks.forEach { nb ->
                                DropdownMenuItem(
                                    text = { Text("${nb.notebookNumber}. ${nb.name}") },
                                    onClick = {
                                        selectedExportNotebook = nb
                                        showExportNotebookMenu = false
                                    }
                                )
                            }
                        }
                    }

                    selectedExportNotebook?.let { nb ->
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    val file = ExportBackupManager.exportNotebookAsPdf(
                                        context = context,
                                        notebook = nb,
                                        topics = snapshot.topics.filter { it.notebookId == nb.id },
                                        pages = snapshot.pages.filter { it.notebookId == nb.id }
                                    )
                                    ExportBackupManager.shareFile(context, file, "application/pdf")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Export PDF")
                            }
                            OutlinedButton(
                                onClick = {
                                    val file = ExportBackupManager.exportNotebookAsMarkdown(
                                        context = context,
                                        notebook = nb,
                                        topics = snapshot.topics.filter { it.notebookId == nb.id },
                                        subtopics = snapshot.subtopics.filter { it.notebookId == nb.id },
                                        pages = snapshot.pages.filter { it.notebookId == nb.id }
                                    )
                                    ExportBackupManager.shareFile(context, file, "text/markdown")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Export Markdown")
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    OutlinedButton(
                        onClick = { jsonRestoreLauncher.launch(arrayOf("application/json", "text/plain")) },
                        modifier = Modifier.fillMaxWidth().testTag("settings_restore_json_button")
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Restore from JSON Backup (Non-Destructive)")
                    }
                    Text(
                        text = "Restoring merges new notebooks, topics, and pages without overwriting existing items.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // 9. App Preferences
            item {
                SettingsSectionCard(
                    number = "9",
                    title = "App Preferences",
                    subtitle = "Dashboard layout, default cover style, and archive visibility."
                ) {
                    Text("Dashboard Layout", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("GRID" to "Shelf Grid", "LIST" to "Compact List").forEach { (key, label) ->
                            FilterChip(
                                selected = prefs.dashboardLayout == key,
                                onClick = { onSavePreferences(prefs.copy(dashboardLayout = key)) },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Default New Notebook Cover Style", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NotebookIcons.coverStyles.forEach { style ->
                            FilterChip(
                                selected = prefs.defaultCoverStyle == style,
                                onClick = { onSavePreferences(prefs.copy(defaultCoverStyle = style)) },
                                label = { Text(style.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show Archived Notebooks on Dashboard", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Include archived notebooks in the main shelf.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.showArchivedOnDashboard,
                            onCheckedChange = { onSavePreferences(prefs.copy(showArchivedOnDashboard = it)) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateNotebook) {
        NotebookEditorDialog(
            initial = null,
            defaultCoverStyle = prefs.defaultCoverStyle,
            onDismiss = { showCreateNotebook = false },
            onSave = { name, purpose, shortDesc, detailedDesc, iconKey, colorHex, coverStyle ->
                onCreateNotebook(name, purpose, shortDesc, detailedDesc, iconKey, colorHex, coverStyle)
                showCreateNotebook = false
            }
        )
    }

    editingNotebook?.let { target ->
        NotebookEditorDialog(
            initial = target,
            defaultCoverStyle = prefs.defaultCoverStyle,
            onDismiss = { editingNotebook = null },
            onSave = { name, purpose, shortDesc, detailedDesc, iconKey, colorHex, coverStyle ->
                onUpdateNotebook(target.id, name, purpose, shortDesc, detailedDesc, iconKey, colorHex, coverStyle)
                editingNotebook = null
            }
        )
    }

    deleteCandidateNotebook?.let { target ->
        val tCount = snapshot.topics.count { it.notebookId == target.id }
        val pCount = snapshot.pages.count { it.notebookId == target.id }
        AlertDialog(
            onDismissRequest = { deleteCandidateNotebook = null },
            title = { Text("Delete \"${target.name}\"?") },
            text = {
                Text(
                    "Warning: This notebook currently has $tCount topics and $pCount pages. " +
                        if (target.systemDefault) {
                            "It is one of your core 13 library notebooks. Consider archiving instead of permanently deleting."
                        } else {
                            "Deleting will permanently remove this notebook."
                        }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteNotebook(target.id)
                        deleteCandidateNotebook = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                Row {
                    if (!target.archived) {
                        TextButton(
                            onClick = {
                                onToggleArchiveNotebook(target)
                                deleteCandidateNotebook = null
                            }
                        ) {
                            Text("Archive Instead")
                        }
                    }
                    TextButton(onClick = { deleteCandidateNotebook = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (showCreateTopicDialog) {
        val activeNb = snapshot.notebooks.firstOrNull { it.id == selectedTopicNotebookId }
        if (activeNb != null) {
            TopicCreateDialog(
                notebook = activeNb,
                aiEnabled = prefs.aiEnabled,
                topicGenState = topicGenState,
                onGenerateWithAi = { topicName ->
                    onGenerateTopicWithAi(activeNb.name, activeNb.purpose, topicName)
                },
                onResetAiGen = onResetTopicAiGen,
                onDismiss = { showCreateTopicDialog = false },
                onCreate = { topic, starterSubs ->
                    onCreateTopic(topic, starterSubs)
                    showCreateTopicDialog = false
                }
            )
        }
    }

    addSubtopicParent?.let { parent ->
        SubtopicCreateDialog(
            notebookId = parent.notebookId,
            topicId = parent.id,
            topicName = parent.name,
            onDismiss = { addSubtopicParent = null },
            onCreate = { sub ->
                onCreateSubtopic(sub)
                addSubtopicParent = null
            }
        )
    }

    deleteCandidateTopic?.let { topic ->
        AlertDialog(
            onDismissRequest = { deleteCandidateTopic = null },
            title = { Text("Delete Topic \"${topic.name}\"?") },
            text = { Text("This removes the topic from the directory. Associated pages remain in your library.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTopic(topic.id)
                        deleteCandidateTopic = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidateTopic = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    number: String,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = number,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
