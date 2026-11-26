package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.platform.ExportBackupManager
import com.example.core.platform.VoiceInputManager
import com.example.core.platform.VoiceRecognitionState
import com.example.data.model.AppPreferencesEntity
import com.example.data.model.NotebookEntity
import com.example.data.model.PageEntity
import com.example.data.model.SaveStatus
import com.example.data.model.SubtopicEntity
import com.example.data.model.TopicEntity
import com.example.ui.components.EditorFormattingSheet
import com.example.ui.components.PageManagerSheet
import com.example.ui.theme.AppFontCatalog
import com.example.ui.theme.PaperTone
import com.example.ui.theme.RuledLineTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val LINES_PER_A4_SHEET = 26

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysicalNotebookEditorScreen(
    notebook: NotebookEntity,
    topic: TopicEntity?,
    subtopic: SubtopicEntity?,
    pagesInScope: List<PageEntity>,
    activePage: PageEntity,
    preferences: AppPreferencesEntity,
    pendingAiInsertion: String?,
    onClearPendingAiInsertion: () -> Unit,
    onSelectPage: (PageEntity) -> Unit,
    onCreateNewPage: () -> Unit,
    onRenamePage: (PageEntity, String) -> Unit,
    onDuplicatePage: (PageEntity) -> Unit,
    onSwapPageOrder: (PageEntity, PageEntity) -> Unit,
    onDeletePage: (PageEntity) -> Unit,
    onPersistPageFields: suspend (String, Map<String, Any>) -> Result<Unit>,
    onOpenAiWithContext: (currentContent: String, selectedText: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val voiceManager = remember(context) { VoiceInputManager(context) }
    val voiceState by voiceManager.state.collectAsStateWithLifecycle()
    var voiceLang by remember(preferences.voiceInputLanguage) { mutableStateOf(preferences.voiceInputLanguage) }
    var showVoiceLangMenu by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceManager.startListening(voiceLang)
        }
    }

    DisposableEffect(Unit) {
        onDispose { voiceManager.stopListening() }
    }

    // Editor state keyed by activePage.id
    var titleText by remember(activePage.id) { mutableStateOf(activePage.title) }
    var textFieldValue by remember(activePage.id) {
        mutableStateOf(
            TextFieldValue(
                text = activePage.content,
                selection = TextRange(activePage.content.length)
            )
        )
    }
    var handwritingMode by remember(activePage.id) { mutableStateOf(activePage.handwritingMode) }
    var fontKey by remember(activePage.id) { mutableStateOf(activePage.fontKey) }
    var fontSizeSp by remember(activePage.id) { mutableIntStateOf(activePage.fontSizeSp) }
    var inkColorHex by remember(activePage.id) { mutableLongStateOf(activePage.inkColorHex) }
    var lineSpacing by remember(activePage.id) { mutableDoubleStateOf(activePage.lineSpacingMultiplier) }
    var textAlignment by remember(activePage.id) { mutableStateOf(activePage.textAlignment) }
    var isBold by remember(activePage.id) { mutableStateOf(activePage.bold) }
    var isItalic by remember(activePage.id) { mutableStateOf(activePage.italic) }
    var paperColorKey by remember(activePage.id) { mutableStateOf(activePage.paperColorKey) }

    var saveStatus by remember(activePage.id) { mutableStateOf(SaveStatus.SAVED) }
    var isDirty by remember(activePage.id) { mutableStateOf(false) }
    var focusMode by remember { mutableStateOf(false) }

    val undoStack = remember(activePage.id) { mutableStateListOf<String>() }
    val redoStack = remember(activePage.id) { mutableStateListOf<String>() }
    var lastUndoPushTime by remember(activePage.id) { mutableLongStateOf(0L) }
    var lastUndoTextLen by remember(activePage.id) { mutableIntStateOf(activePage.content.length) }

    var showFormattingSheet by remember { mutableStateOf(false) }
    var showPageManager by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun pushUndoSnapshot(previous: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val lenDiff = abs(previous.length - lastUndoTextLen)
        if (!force && lenDiff < 14 && (now - lastUndoPushTime) < 750L) return
        if (undoStack.lastOrNull() != previous) {
            undoStack.add(previous)
            if (undoStack.size > 30) undoStack.removeAt(0)
            redoStack.clear()
            lastUndoPushTime = now
            lastUndoTextLen = previous.length
        }
    }

    fun insertTextAtCursor(insertion: String, ensureLeadingSpace: Boolean = true) {
        if (insertion.isBlank()) return
        pushUndoSnapshot(textFieldValue.text, force = true)
        val sel = textFieldValue.selection
        val start = minOf(sel.start, sel.end).coerceIn(0, textFieldValue.text.length)
        val end = maxOf(sel.start, sel.end).coerceIn(start, textFieldValue.text.length)
        val needsSpaceBefore = ensureLeadingSpace && start > 0 && !textFieldValue.text[start - 1].isWhitespace()
        val toInsert = if (needsSpaceBefore) " $insertion" else insertion
        val updated = textFieldValue.text.substring(0, start) + toInsert + textFieldValue.text.substring(end)
        val newCursor = start + toInsert.length
        textFieldValue = TextFieldValue(text = updated, selection = TextRange(newCursor))
        isDirty = true
    }

    // Consume AI insertion when user taps "Insert into Page"
    LaunchedEffect(pendingAiInsertion) {
        if (!pendingAiInsertion.isNullOrBlank()) {
            insertTextAtCursor("\n\n$pendingAiInsertion", ensureLeadingSpace = false)
            onClearPendingAiInsertion()
        }
    }

    // Consume Voice-to-Text final transcript
    LaunchedEffect(voiceState) {
        if (voiceState is VoiceRecognitionState.Result) {
            val spoken = (voiceState as VoiceRecognitionState.Result).text
            insertTextAtCursor(spoken)
            voiceManager.resetState()
        }
    }

    fun buildSaveMap(): Map<String, Any> = mapOf(
        "title" to titleText.ifBlank { "Page ${activePage.pageNumber}" }.take(200),
        "content" to textFieldValue.text.take(200000),
        "handwritingMode" to handwritingMode,
        "fontKey" to fontKey,
        "fontSizeSp" to fontSizeSp.coerceIn(10, 36),
        "inkColorHex" to inkColorHex,
        "lineSpacingMultiplier" to lineSpacing.coerceIn(1.0, 3.0),
        "textAlignment" to textAlignment,
        "bold" to isBold,
        "italic" to isItalic,
        "paperColorKey" to paperColorKey
    )

    // Smooth debounced auto-save (1.4s pause while typing avoids Firestore thrashing on phone)
    LaunchedEffect(
        activePage.id, titleText, textFieldValue.text, handwritingMode, fontKey,
        fontSizeSp, inkColorHex, lineSpacing, textAlignment, isBold, isItalic, paperColorKey, isDirty
    ) {
        if (!isDirty) return@LaunchedEffect
        saveStatus = SaveStatus.SAVING
        delay(1400L)
        val result = onPersistPageFields(activePage.id, buildSaveMap())
        saveStatus = if (result.isSuccess) {
            isDirty = false
            SaveStatus.SAVED
        } else {
            val msg = result.exceptionOrNull()?.message.orEmpty()
            if (msg.contains("offline", ignoreCase = true) || msg.contains("unavailable", ignoreCase = true)) {
                SaveStatus.OFFLINE_PENDING
            } else {
                SaveStatus.ERROR
            }
        }
    }

    val currentIndex = remember(pagesInScope, activePage.id) {
        pagesInScope.indexOfFirst { it.id == activePage.id }.coerceAtLeast(0)
    }
    val paperTone = remember(paperColorKey) { PaperTone.fromKey(paperColorKey) }
    val ruledTone = remember(preferences.ruledLineColorKey) { RuledLineTone.fromKey(preferences.ruledLineColorKey) }
    val selectedFontFamily = remember(fontKey) { AppFontCatalog.resolve(fontKey) }

    val lineHeightSp = (fontSizeSp * lineSpacing).sp
    val lineHeightPx = with(density) { lineHeightSp.toPx() }.coerceAtLeast(22f)
    val paperTopPaddingDp = 14.dp
    val paperTopPaddingPx = with(density) { paperTopPaddingDp.toPx() }
    val baselineNudgePx = with(density) { 2.dp.toPx() }
    val marginLineOffsetDp = 40.dp
    val textLeftPaddingDp = 50.dp

    val selectedText = remember(textFieldValue.selection, textFieldValue.text) {
        val s = textFieldValue.selection
        if (s.collapsed) "" else textFieldValue.text.substring(
            minOf(s.start, s.end).coerceIn(0, textFieldValue.text.length),
            maxOf(s.start, s.end).coerceIn(0, textFieldValue.text.length)
        )
    }

    val estimatedLineCount = remember(textFieldValue.text) {
        textFieldValue.text.lines().sumOf { line -> (line.length / 40) + 1 }
    }

    Scaffold(
        topBar = {
            if (!focusMode) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (isDirty) onPersistPageFields(activePage.id, buildSaveMap())
                                    onBack()
                                }
                            },
                            modifier = Modifier.testTag("editor_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        Column {
                            val crumb = buildString {
                                append(notebook.name)
                                topic?.let { append(" / ${it.name}") }
                                subtopic?.let { append(" / ${it.name}") }
                            }
                            Text(
                                text = crumb,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Page ${activePage.pageNumber}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = when (saveStatus) {
                                        SaveStatus.SAVED -> MaterialTheme.colorScheme.primaryContainer
                                        SaveStatus.SAVING -> MaterialTheme.colorScheme.secondaryContainer
                                        SaveStatus.OFFLINE_PENDING -> MaterialTheme.colorScheme.tertiaryContainer
                                        SaveStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = saveStatus.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .testTag("save_status_badge")
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { focusMode = true },
                            modifier = Modifier.testTag("editor_focus_mode_button")
                        ) {
                            Icon(Icons.Filled.OpenInFull, contentDescription = "Distraction-free focus mode")
                        }
                        IconButton(
                            onClick = { onOpenAiWithContext(textFieldValue.text, selectedText) },
                            modifier = Modifier.testTag("editor_ai_button")
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "Ask AI Assistant")
                        }
                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.testTag("editor_more_menu_button")
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Page & export options")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Add New Page") },
                                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        scope.launch {
                                            if (isDirty) onPersistPageFields(activePage.id, buildSaveMap())
                                            onCreateNewPage()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Insert Visual Page Break") },
                                    leadingIcon = { Icon(Icons.Filled.HorizontalRule, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        insertTextAtCursor(
                                            "\n\n────────── New Page Break ──────────\n\n",
                                            ensureLeadingSpace = false
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Manage All Pages (${pagesInScope.size})") },
                                    leadingIcon = { Icon(Icons.Filled.Layers, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showPageManager = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Duplicate This Page") },
                                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        scope.launch {
                                            if (isDirty) onPersistPageFields(activePage.id, buildSaveMap())
                                            onDuplicatePage(activePage.copy(title = titleText, content = textFieldValue.text))
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete This Page", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showDeleteConfirm = true
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Export PDF (A4 Ruled)") },
                                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        val pageCopy = activePage.copy(title = titleText, content = textFieldValue.text)
                                        scope.launch {
                                            val file = withContext(Dispatchers.IO) {
                                                ExportBackupManager.exportPageAsPdf(context, notebook.name, pageCopy)
                                            }
                                            ExportBackupManager.shareFile(context, file, "application/pdf")
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export Markdown (.md)") },
                                    onClick = {
                                        showOverflowMenu = false
                                        val pageCopy = activePage.copy(title = titleText, content = textFieldValue.text)
                                        scope.launch {
                                            val file = withContext(Dispatchers.IO) {
                                                ExportBackupManager.exportPageAsMarkdown(context, notebook.name, topic?.name, pageCopy)
                                            }
                                            ExportBackupManager.shareFile(context, file, "text/markdown")
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export Plain Text (.txt)") },
                                    onClick = {
                                        showOverflowMenu = false
                                        val pageCopy = activePage.copy(title = titleText, content = textFieldValue.text)
                                        scope.launch {
                                            val file = withContext(Dispatchers.IO) {
                                                ExportBackupManager.exportPageAsText(context, notebook.name, pageCopy)
                                            }
                                            ExportBackupManager.shareFile(context, file, "text/plain")
                                        }
                                    }
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!focusMode) {
                Surface(
                    tonalElevation = 3.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Column {
                        // Voice feedback banner when listening / processing / error
                        if (voiceState !is VoiceRecognitionState.Idle) {
                            Surface(
                                color = if (voiceState is VoiceRecognitionState.Error) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val statusMsg = when (val s = voiceState) {
                                        is VoiceRecognitionState.Listening -> "Listening ($voiceLang)… Speak now"
                                        is VoiceRecognitionState.Processing -> "Processing speech…"
                                        is VoiceRecognitionState.Partial -> "Hearing: ${s.text}"
                                        is VoiceRecognitionState.Error -> s.message
                                        else -> ""
                                    }
                                    Text(
                                        text = statusMsg,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = {
                                            voiceManager.stopListening()
                                            voiceManager.resetState()
                                        }
                                    ) {
                                        Text(if (voiceState is VoiceRecognitionState.Error) "Dismiss" else "Stop")
                                    }
                                }
                            }
                        }

                        // Clutter-free compact bottom dock
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left: Pen & Paper Style + Voice Input
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { showFormattingSheet = true },
                                    modifier = Modifier.testTag("editor_format_button")
                                ) {
                                    Icon(Icons.Filled.FormatSize, contentDescription = "Pen & Paper Style")
                                }

                                Box {
                                    TextButton(
                                        onClick = { showVoiceLangMenu = true },
                                        modifier = Modifier.testTag("voice_language_chip")
                                    ) {
                                        Text(
                                            text = when (voiceLang) {
                                                "hi-IN" -> "HI"
                                                "en-US" -> "EN"
                                                else -> "EN-IN"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showVoiceLangMenu,
                                        onDismissRequest = { showVoiceLangMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("English (India)") },
                                            onClick = { voiceLang = "en-IN"; showVoiceLangMenu = false }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("English (US)") },
                                            onClick = { voiceLang = "en-US"; showVoiceLangMenu = false }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Hindi (हिन्दी)") },
                                            onClick = { voiceLang = "hi-IN"; showVoiceLangMenu = false }
                                        )
                                    }
                                }

                                val isRecording = voiceState is VoiceRecognitionState.Listening ||
                                    voiceState is VoiceRecognitionState.Partial
                                IconButton(
                                    onClick = {
                                        if (isRecording) {
                                            voiceManager.stopListening()
                                            voiceManager.resetState()
                                        } else {
                                            val granted = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) {
                                                voiceManager.startListening(voiceLang)
                                            } else {
                                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("editor_mic_button")
                                ) {
                                    Icon(
                                        imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                                        contentDescription = if (isRecording) "Stop voice input" else "Start voice-to-text",
                                        tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Center: Clean Page Flipper (Prev / X of Y / Next)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (currentIndex > 0) {
                                            scope.launch {
                                                if (isDirty) onPersistPageFields(activePage.id, buildSaveMap())
                                                onSelectPage(pagesInScope[currentIndex - 1])
                                            }
                                        }
                                    },
                                    enabled = currentIndex > 0
                                ) {
                                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous page")
                                }
                                Surface(
                                    onClick = { showPageManager = true },
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("editor_pages_button")
                                ) {
                                    Text(
                                        text = "${currentIndex + 1} / ${pagesInScope.size.coerceAtLeast(1)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (currentIndex < pagesInScope.lastIndex) {
                                            scope.launch {
                                                if (isDirty) onPersistPageFields(activePage.id, buildSaveMap())
                                                onSelectPage(pagesInScope[currentIndex + 1])
                                            }
                                        }
                                    },
                                    enabled = currentIndex < pagesInScope.lastIndex
                                ) {
                                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next page")
                                }
                            }

                            // Right: Add Page + Delete Page directly accessible
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            if (isDirty) onPersistPageFields(activePage.id, buildSaveMap())
                                            onCreateNewPage()
                                        }
                                    },
                                    modifier = Modifier.testTag("editor_new_page_button")
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Add new page", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(
                                    onClick = { showDeleteConfirm = true },
                                    modifier = Modifier.testTag("editor_delete_page_button")
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete current page",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .then(if (focusMode) Modifier.statusBarsPadding().navigationBarsPadding().imePadding() else Modifier)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.TopCenter
        ) {
            val minSheetHeight = (maxHeight - 20.dp).coerceAtLeast(500.dp)
            val verticalScroll = rememberScrollState()

            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(horizontal = if (focusMode) 4.dp else 10.dp, vertical = 8.dp)
                    .shadow(4.dp, RoundedCornerShape(6.dp))
                    .background(paperTone.backgroundColor, RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFFD6D3CD), RoundedCornerShape(6.dp))
                    .verticalScroll(verticalScroll)
            ) {
                // Clean Paper Header Strip: Page Title + 1-tap Pen/Print switch + unobtrusive Undo/Redo when active
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = textLeftPaddingDp, end = 12.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = titleText,
                        onValueChange = {
                            titleText = it
                            isDirty = true
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = selectedFontFamily,
                            fontSize = if (handwritingMode) 20.sp else 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(inkColorHex),
                            letterSpacing = if (handwritingMode) 0.35.sp else 0.sp
                        ),
                        cursorBrush = SolidColor(Color(inkColorHex)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("editor_page_title_input"),
                        decorationBox = { inner ->
                            if (titleText.isEmpty()) {
                                Text("Page Title…", color = Color(0xFF94A3B8), fontSize = 17.sp)
                            }
                            inner()
                        }
                    )

                    if (undoStack.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                if (undoStack.isNotEmpty()) {
                                    redoStack.add(textFieldValue.text)
                                    val prev = undoStack.removeAt(undoStack.lastIndex)
                                    textFieldValue = TextFieldValue(prev, TextRange(prev.length))
                                    lastUndoTextLen = prev.length
                                    isDirty = true
                                }
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("editor_undo_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (redoStack.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                if (redoStack.isNotEmpty()) {
                                    undoStack.add(textFieldValue.text)
                                    val next = redoStack.removeAt(redoStack.lastIndex)
                                    textFieldValue = TextFieldValue(next, TextRange(next.length))
                                    lastUndoTextLen = next.length
                                    isDirty = true
                                }
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("editor_redo_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo",
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(4.dp))

                    // 1-tap Human Pen vs Print toggle directly on the sheet
                    Surface(
                        onClick = {
                            val nextHw = !handwritingMode
                            handwritingMode = nextHw
                            fontKey = if (nextHw) preferences.handwritingFontKey else preferences.writingFontKey
                            if (nextHw && inkColorHex == 0xFF1C2430L) {
                                inkColorHex = 0xFF1B3F8BL // Classic ballpoint blue ink for pen feel
                            }
                            isDirty = true
                        },
                        color = if (handwritingMode) Color(0xFFE0ECFF) else Color(0xFFECEAE4),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("quick_pen_mode_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = null,
                                tint = if (handwritingMode) Color(0xFF1B3F8B) else Color(0xFF475569),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (handwritingMode) "Pen" else "Print",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (handwritingMode) Color(0xFF1B3F8B) else Color(0xFF475569),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Ruled A4 Writing Body with cached Canvas drawing and natural sheet page breaks
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minSheetHeight)
                        .drawWithCache {
                            val marginX = marginLineOffsetDp.toPx()
                            val doubleMarginOffset = 3.dp.toPx()
                            val topRuleY = 1.dp.toPx()
                            val marginStroke = 1.3.dp.toPx()
                            val thinStroke = 0.75.dp.toPx()
                            val ruleStroke = 0.85.dp.toPx()
                            val pageBreakStroke = 1.2.dp.toPx()
                            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                            val primaryMargin = paperTone.marginLineColor.copy(alpha = 0.75f)
                            val secondaryMargin = paperTone.marginLineColor.copy(alpha = 0.34f)
                            val headerLine = paperTone.marginLineColor.copy(alpha = 0.45f)
                            val ruleColor = ruledTone.lineColor.copy(alpha = 0.58f)
                            val breakColor = Color(0xFF94A3B8).copy(alpha = 0.75f)
                            val step = lineHeightPx

                            onDrawBehind {
                                // Double-line physical notebook red margin
                                drawLine(
                                    color = primaryMargin,
                                    start = Offset(marginX, 0f),
                                    end = Offset(marginX, size.height),
                                    strokeWidth = marginStroke
                                )
                                drawLine(
                                    color = secondaryMargin,
                                    start = Offset(marginX + doubleMarginOffset, 0f),
                                    end = Offset(marginX + doubleMarginOffset, size.height),
                                    strokeWidth = thinStroke
                                )
                                // Header rule
                                drawLine(
                                    color = headerLine,
                                    start = Offset(0f, topRuleY),
                                    end = Offset(size.width, topRuleY),
                                    strokeWidth = 1.dp.toPx()
                                )

                                // Horizontal ruled lines synchronized to text baseline + automatic A4 sheet break creases
                                var y = paperTopPaddingPx + step - baselineNudgePx
                                var lineNumber = 1
                                while (y < size.height) {
                                    if (lineNumber > 0 && lineNumber % LINES_PER_A4_SHEET == 0) {
                                        // Realistic physical A4 sheet page-break crease
                                        drawLine(
                                            color = breakColor,
                                            start = Offset(0f, y),
                                            end = Offset(size.width, y),
                                            strokeWidth = pageBreakStroke,
                                            pathEffect = dashEffect
                                        )
                                    } else {
                                        drawLine(
                                            color = ruleColor,
                                            start = Offset(0f, y),
                                            end = Offset(size.width, y),
                                            strokeWidth = ruleStroke
                                        )
                                    }
                                    y += step
                                    lineNumber++
                                }
                            }
                        }
                        .padding(
                            start = textLeftPaddingDp,
                            end = 14.dp,
                            top = paperTopPaddingDp,
                            bottom = 36.dp
                        )
                ) {
                    val align = when (textAlignment) {
                        "CENTER" -> TextAlign.Center
                        "END" -> TextAlign.End
                        else -> TextAlign.Start
                    }
                    val bodyStyle = remember(
                        selectedFontFamily, fontSizeSp, lineHeightSp, inkColorHex,
                        isBold, isItalic, align, handwritingMode
                    ) {
                        TextStyle(
                            fontFamily = selectedFontFamily,
                            fontSize = fontSizeSp.sp,
                            lineHeight = lineHeightSp,
                            color = Color(inkColorHex),
                            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                            letterSpacing = if (handwritingMode) 0.35.sp else 0.sp,
                            textAlign = align,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Bottom,
                                trim = LineHeightStyle.Trim.None
                            )
                        )
                    }
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { next ->
                            if (next.text != textFieldValue.text) {
                                val newlineAdded = next.text.length > textFieldValue.text.length &&
                                    next.text.endsWith('\n')
                                pushUndoSnapshot(textFieldValue.text, force = newlineAdded)
                                isDirty = true
                            }
                            textFieldValue = next
                        },
                        textStyle = bodyStyle,
                        cursorBrush = SolidColor(Color(inkColorHex)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minSheetHeight - 64.dp)
                            .testTag("editor_body_input"),
                        decorationBox = { inner ->
                            if (textFieldValue.text.isEmpty()) {
                                Text(
                                    text = "Start writing in English, हिन्दी, or Hinglish…",
                                    style = bodyStyle.copy(color = Color(0xFF94A3B8))
                                )
                            }
                            inner()
                        }
                    )
                }

                // Natural Next-Page Break Callout at bottom of sheet
                if (!focusMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (estimatedLineCount >= LINES_PER_A4_SHEET) {
                                "Sheet full • Page ${activePage.pageNumber}"
                            } else {
                                "${notebook.name} • Page ${activePage.pageNumber}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    if (isDirty) onPersistPageFields(activePage.id, buildSaveMap())
                                    onCreateNewPage()
                                }
                            },
                            modifier = Modifier.testTag("sheet_bottom_next_page_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New Page", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Floating Focus-Mode exit pill
            if (focusMode) {
                FilledTonalIconButton(
                    onClick = { focusMode = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(CircleShape)
                        .testTag("exit_focus_mode_button")
                ) {
                    Icon(Icons.Filled.CloseFullscreen, contentDescription = "Exit focus mode")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Page ${activePage.pageNumber}?") },
            text = {
                Text(
                    if (pagesInScope.size <= 1) {
                        "Delete \"${titleText.ifBlank { activePage.title }}\"? This is the last page in this section."
                    } else {
                        "Delete \"${titleText.ifBlank { activePage.title }}\"? Remaining pages will be renumbered cleanly."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        isDirty = false
                        onDeletePage(activePage)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_current_page")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFormattingSheet) {
        EditorFormattingSheet(
            handwritingMode = handwritingMode,
            fontKey = fontKey,
            fontSizeSp = fontSizeSp,
            inkColorHex = inkColorHex,
            lineSpacing = lineSpacing,
            textAlignment = textAlignment,
            bold = isBold,
            italic = isItalic,
            paperColorKey = paperColorKey,
            onToggleHandwritingMode = { hw ->
                handwritingMode = hw
                fontKey = if (hw) preferences.handwritingFontKey else preferences.writingFontKey
                isDirty = true
            },
            onSelectFont = { fontKey = it; isDirty = true },
            onChangeFontSize = { fontSizeSp = it; isDirty = true },
            onSelectInkColor = { inkColorHex = it; isDirty = true },
            onSelectLineSpacing = { lineSpacing = it; isDirty = true },
            onSelectAlignment = { textAlignment = it; isDirty = true },
            onToggleBold = { isBold = !isBold; isDirty = true },
            onToggleItalic = { isItalic = !isItalic; isDirty = true },
            onSelectPaperTone = { paperColorKey = it; isDirty = true },
            onDismiss = { showFormattingSheet = false }
        )
    }

    if (showPageManager) {
        PageManagerSheet(
            pages = pagesInScope,
            currentPageId = activePage.id,
            onSelectPage = { target ->
                scope.launch {
                    if (isDirty) onPersistPageFields(activePage.id, buildSaveMap())
                    onSelectPage(target)
                }
            },
            onCreateNewPage = {
                scope.launch {
                    if (isDirty) onPersistPageFields(activePage.id, buildSaveMap())
                    onCreateNewPage()
                }
            },
            onRenamePage = onRenamePage,
            onDuplicatePage = { page ->
                scope.launch {
                    if (isDirty && page.id == activePage.id) onPersistPageFields(activePage.id, buildSaveMap())
                    onDuplicatePage(page)
                }
            },
            onSwapPageOrder = onSwapPageOrder,
            onDeletePage = { page ->
                if (page.id == activePage.id) isDirty = false
                onDeletePage(page)
            },
            onDismiss = { showPageManager = false }
        )
    }
}
