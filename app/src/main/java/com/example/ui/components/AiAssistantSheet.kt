package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.platform.TextToSpeechManager
import com.example.core.platform.VoiceInputManager
import com.example.core.platform.VoiceRecognitionState
import com.example.data.ai.AiChatMessage
import com.example.data.ai.AiChatRole
import com.example.data.ai.AiContext
import com.example.data.ai.AiProposedAction
import com.example.data.ai.AiUndoSnapshot
import com.example.data.ai.LearningProgressStatus
import com.example.ui.viewmodel.AiAgentUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiAssistantSheet(
    aiEnabled: Boolean,
    agentState: AiAgentUiState,
    context: AiContext,
    defaultVoiceLanguage: String = "en-IN",
    onSendMessage: (userPrompt: String, actionLabel: String) -> Unit,
    onRetry: () -> Unit,
    onCancelRequest: () -> Unit,
    onNewChat: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onConfirmProposal: (messageId: String, proposal: AiProposedAction) -> Unit,
    onDismissProposal: (messageId: String, proposalId: String) -> Unit,
    onUndoAction: (AiUndoSnapshot) -> Unit,
    onUpsertProgress: (concept: String, status: LearningProgressStatus, notes: String) -> Unit,
    onDeleteProgress: (String) -> Unit,
    onInsertIntoCurrentPage: ((String) -> Unit)? = null,
    onSaveAsNewPage: ((String) -> Unit)? = null,
    onSaveToTopicAiInfo: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
    onCopied: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val androidContext = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var promptText by remember { mutableStateOf("") }
    var voiceLang by remember(defaultVoiceLanguage) { mutableStateOf(defaultVoiceLanguage.ifBlank { "en-IN" }) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var voiceNotice by remember { mutableStateOf<String?>(null) }

    val voiceManager = remember(androidContext) { VoiceInputManager(androidContext) }
    val voiceState by voiceManager.state.collectAsStateWithLifecycle()

    val ttsManager = remember(androidContext) { TextToSpeechManager(androidContext) }
    val speakingMessageId by ttsManager.speakingMessageId.collectAsStateWithLifecycle()
    val ttsAvailable by ttsManager.isAvailable.collectAsStateWithLifecycle()

    DisposableEffect(voiceManager, ttsManager) {
        onDispose {
            voiceManager.stopListening()
            ttsManager.shutdown()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceNotice = null
            voiceManager.startListening(voiceLang)
        } else {
            voiceNotice = "Microphone permission denied. You can still type in English, Hindi, or Hinglish."
        }
    }

    LaunchedEffect(voiceState) {
        when (val st = voiceState) {
            is VoiceRecognitionState.Result -> {
                val spoken = st.text.trim()
                if (spoken.isNotBlank()) {
                    promptText = if (promptText.isBlank()) spoken else "${promptText.trimEnd()} $spoken"
                }
                voiceNotice = "Transcription ready — edit if needed, then tap Send."
                voiceManager.resetState()
            }
            is VoiceRecognitionState.Error -> {
                voiceNotice = st.message
                voiceManager.resetState()
            }
            else -> Unit
        }
    }

    val listState = rememberLazyListState()
    val messages = agentState.activeSession.messages

    LaunchedEffect(messages.size, agentState.streamingText.length, agentState.isBusy, agentState.errorMessage) {
        val totalItems = messages.size +
            (if (agentState.streamingText.isNotBlank()) 1 else 0) +
            (if (agentState.isBusy) 1 else 0) +
            (if (agentState.errorMessage != null) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    val quickActions = remember {
        listOf(
            "Explain This" to "Explain this topic or selected text clearly in understandable language with key concepts.",
            "Create Notes" to "Create detailed, well-structured professional study notes with headings, mechanisms, examples, key takeaways, quick revision, and missing concepts.",
            "Improve Writing" to "Improve the clarity, flow, and structure of my existing notes while preserving all my original facts.",
            "Summarize" to "Summarize what I have learned and written so far into concise structured bullet points.",
            "Organize Notes" to "Organize my current page cleanly with logical headings and prepare an organized page preview I can confirm.",
            "Find Missing Concepts" to "Review my existing notes and identify important missing concepts, gaps, and what I should explore next.",
            "Generate Examples" to "Provide 3–5 clear, practical real-world examples relevant to this notebook and topic.",
            "Create Questions" to "Create self-assessment practice questions (conceptual + application) based on my notes.",
            "Revise" to "Create quick high-yield revision notes and prepare a revision page preview if helpful.",
            "Continue Writing" to "Continue writing naturally from where my current page stops."
        )
    }

    ModalBottomSheet(
        onDismissRequest = {
            voiceManager.stopListening()
            ttsManager.stop()
            onDismiss()
        },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            // Header bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = "Notebook AI Agent",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Notebook AI Agent",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val crumb = buildString {
                        context.notebook?.let { append(it.name) }
                        context.topic?.let { append(" • ${it.name}") }
                        context.subtopic?.let { append(" • ${it.name}") }
                        context.currentPage?.let { append(" • P${it.pageNumber}") }
                    }.ifBlank { "Knowledge Library" }
                    Text(
                        text = crumb,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onNewChat,
                    modifier = Modifier.testTag("ai_new_chat_button")
                ) {
                    Icon(Icons.Filled.AddComment, contentDescription = "New conversation")
                }
                IconButton(
                    onClick = { showHistoryDialog = true },
                    modifier = Modifier.testTag("ai_history_button")
                ) {
                    Icon(Icons.Filled.History, contentDescription = "Conversation history")
                }
                IconButton(
                    onClick = { showProgressDialog = true },
                    modifier = Modifier.testTag("ai_progress_button")
                ) {
                    Icon(Icons.Filled.School, contentDescription = "Learning progress")
                }
                IconButton(
                    onClick = {
                        voiceManager.stopListening()
                        ttsManager.stop()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("ai_close_button")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close AI assistant")
                }
            }

            if (!aiEnabled) {
                Spacer(Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "AI Assistant is turned off in Settings > AI Configuration. Enable it there to use the Notebook AI Agent.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                return@Column
            }

            if (context.selectedText.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Selected text: \"${context.selectedText.take(180)}\"",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quick Actions strip (all 10 contextual actions; disabled during active request)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickActions.forEach { (label, instruction) ->
                    AssistChip(
                        onClick = {
                            if (!agentState.isBusy) {
                                val req = if (context.selectedText.isNotBlank()) {
                                    "$instruction Focus on the selected text."
                                } else {
                                    instruction
                                }
                                onSendMessage(req, label)
                            }
                        },
                        enabled = !agentState.isBusy,
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.testTag("ai_action_${label.lowercase().replace(" ", "_")}")
                    )
                }
            }

            // Feedback + Undo bar for executed actions
            if (!agentState.actionFeedback.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = agentState.actionFeedback,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f)
                        )
                        agentState.lastUndoSnapshot?.let { undoSnap ->
                            TextButton(
                                onClick = { onUndoAction(undoSnap) },
                                modifier = Modifier.testTag("ai_undo_action_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Undo")
                            }
                        }
                    }
                }
            }

            // Scrollable Conversation Thread
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty() && !agentState.isBusy && agentState.errorMessage == null) {
                    item(key = "welcome_card") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Talk naturally in English, Hindi, or Hinglish",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "• Tell me what you studied and ask me to make detailed structured notes\n" +
                                        "• Ask me to create a page, topic, subtopic, or organize your current page\n" +
                                        "• Track what you've already studied vs. what needs revision\n" +
                                        "• All page edits are previewed first and never overwrite your original notes silently.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    ChatMessageBubble(
                        message = msg,
                        isSpeaking = speakingMessageId == msg.id,
                        ttsAvailable = ttsAvailable,
                        onCopy = {
                            clipboard.setText(AnnotatedString(msg.text))
                            onCopied()
                        },
                        onToggleSpeak = { ttsManager.toggleSpeak(msg.id, msg.text) },
                        onInsertIntoCurrentPage = onInsertIntoCurrentPage?.let { cb -> { cb(msg.text) } },
                        onSaveAsNewPage = onSaveAsNewPage?.let { cb -> { cb(msg.text) } },
                        onSaveToTopicAiInfo = onSaveToTopicAiInfo?.let { cb -> { cb(msg.text) } },
                        onConfirmProposal = { proposal -> onConfirmProposal(msg.id, proposal) },
                        onDismissProposal = { proposalId -> onDismissProposal(msg.id, proposalId) },
                        onUndoAction = onUndoAction
                    )
                }

                if (agentState.streamingText.isNotBlank()) {
                    item(key = "streaming_bubble") {
                        Surface(
                            tonalElevation = 2.dp,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_streaming_bubble")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = agentState.statusMessage ?: "Writing response…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = agentState.streamingText,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                if (agentState.isBusy && agentState.streamingText.isBlank()) {
                    item(key = "thinking_indicator") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_thinking_indicator")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = agentState.statusMessage ?: "Thinking with notebook context…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = onCancelRequest,
                                    modifier = Modifier.testTag("ai_stop_button")
                                ) {
                                    Text("Stop")
                                }
                            }
                        }
                    }
                }

                if (!agentState.errorMessage.isNullOrBlank()) {
                    item(key = "error_card") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_error_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "AI Request Failed",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = agentState.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                if (!agentState.lastFailedPrompt.isNullOrBlank()) {
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = onRetry,
                                        enabled = !agentState.isBusy,
                                        modifier = Modifier.testTag("ai_retry_button")
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Voice live status / fallback notice
            val isListening = voiceState is VoiceRecognitionState.Listening ||
                voiceState is VoiceRecognitionState.Processing ||
                voiceState is VoiceRecognitionState.Partial
            if (isListening || !voiceNotice.isNullOrBlank()) {
                val statusText = when (val st = voiceState) {
                    is VoiceRecognitionState.Listening -> "Listening (${voiceLang})… speak now"
                    is VoiceRecognitionState.Processing -> "Processing speech…"
                    is VoiceRecognitionState.Partial -> "Hearing: \"${st.text}\""
                    else -> voiceNotice.orEmpty()
                }
                Surface(
                    color = if (isListening) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f)
                        )
                        if (!isListening && voiceNotice != null) {
                            IconButton(
                                onClick = { voiceNotice = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Dismiss voice notice", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // Bottom Input Dock (Voice + Editable Text + Send/Stop)
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalIconButton(
                        onClick = {
                            if (isListening) {
                                voiceManager.stopListening()
                                voiceManager.resetState()
                            } else {
                                if (!voiceManager.isAvailable()) {
                                    voiceNotice = "Speech-to-text service is not available on this device. Please type your message."
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        androidContext,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        voiceNotice = null
                                        voiceManager.startListening(voiceLang)
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            }
                        },
                        colors = if (isListening) {
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        } else {
                            IconButtonDefaults.filledTonalIconButtonColors()
                        },
                        modifier = Modifier.testTag("ai_voice_mic_button")
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = if (isListening) "Stop voice input" else "Start voice input"
                        )
                    }
                    TextButton(
                        onClick = {
                            voiceLang = when (voiceLang) {
                                "en-IN" -> "hi-IN"
                                "hi-IN" -> "en-US"
                                else -> "en-IN"
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(22.dp)
                            .testTag("ai_voice_lang_toggle")
                    ) {
                        Text(
                            text = when (voiceLang) {
                                "hi-IN" -> "हिन्दी"
                                "en-US" -> "EN-US"
                                else -> "EN/HI"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    placeholder = { Text("Ask, explain what you studied, or instruct in Hindi/English…") },
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_prompt_input")
                )

                if (agentState.isBusy) {
                    FilledIconButton(
                        onClick = onCancelRequest,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("ai_stop_fab")
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop generating")
                    }
                } else {
                    FilledIconButton(
                        onClick = {
                            val textToSend = promptText.trim()
                            if (textToSend.isNotBlank() && !agentState.isBusy) {
                                promptText = ""
                                voiceNotice = null
                                onSendMessage(textToSend, "Ask AI")
                            }
                        },
                        enabled = promptText.isNotBlank() && !agentState.isBusy,
                        modifier = Modifier.testTag("ai_send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
                    }
                }
            }
        }
    }

    if (showHistoryDialog) {
        AiHistoryDialog(
            sessions = agentState.savedSessions,
            activeSessionId = agentState.activeSession.id,
            onSelect = onSelectSession,
            onDelete = onDeleteSession,
            onDismiss = { showHistoryDialog = false }
        )
    }

    if (showProgressDialog) {
        AiLearningProgressDialog(
            notebookId = context.notebook?.id.orEmpty(),
            topicId = context.topic?.id,
            subtopicId = context.subtopic?.id,
            records = agentState.learningProgress,
            onUpsert = onUpsertProgress,
            onDelete = onDeleteProgress,
            onDismiss = { showProgressDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatMessageBubble(
    message: AiChatMessage,
    isSpeaking: Boolean,
    ttsAvailable: Boolean,
    onCopy: () -> Unit,
    onToggleSpeak: () -> Unit,
    onInsertIntoCurrentPage: (() -> Unit)?,
    onSaveAsNewPage: (() -> Unit)?,
    onSaveToTopicAiInfo: (() -> Unit)?,
    onConfirmProposal: (AiProposedAction) -> Unit,
    onDismissProposal: (String) -> Unit,
    onUndoAction: (AiUndoSnapshot) -> Unit
) {
    val isUser = message.role == AiChatRole.USER

    if (isUser) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 4.dp),
                modifier = Modifier.fillMaxWidth(0.86f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (!message.actionLabel.isNullOrBlank()) {
                        Text(
                            text = message.actionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Executed autonomous tool badges (e.g., Search, Read Page, Learning Progress)
            if (message.actionLogs.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    message.actionLogs.forEach { log ->
                        Surface(
                            color = if (log.succeeded) {
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = log.label + if (log.detail.isNotBlank()) " (${log.detail})" else "",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                if (log.undoSnapshot != null && !log.undone) {
                                    TextButton(
                                        onClick = { onUndoAction(log.undoSnapshot) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text("Undo", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Main assistant response card
            Surface(
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_assistant_message_${message.id}")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (message.usedFallbackModel && !message.modelUsed.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .testTag("ai_fallback_badge_${message.id}")
                        ) {
                            Text(
                                text = "Fallback model: ${message.modelUsed}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AssistChip(
                            onClick = onCopy,
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text("Copy", style = MaterialTheme.typography.labelSmall) }
                        )
                        if (ttsAvailable) {
                            AssistChip(
                                onClick = onToggleSpeak,
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isSpeaking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                label = { Text(if (isSpeaking) "Stop Audio" else "Listen", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("ai_tts_button_${message.id}")
                            )
                        }
                        if (onInsertIntoCurrentPage != null) {
                            AssistChip(
                                onClick = onInsertIntoCurrentPage,
                                leadingIcon = { Icon(Icons.Filled.PostAdd, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                label = { Text("Insert", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("ai_insert_button_${message.id}")
                            )
                        }
                        if (onSaveAsNewPage != null) {
                            AssistChip(
                                onClick = onSaveAsNewPage,
                                leadingIcon = { Icon(Icons.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                label = { Text("New Page", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("ai_new_page_button_${message.id}")
                            )
                        }
                        if (onSaveToTopicAiInfo != null) {
                            AssistChip(
                                onClick = onSaveToTopicAiInfo,
                                label = { Text("Save AI Info", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            // Interactive Proposal Confirmation Cards (Preview before applying!)
            message.proposals.forEach { proposal ->
                AiProposalCard(
                    proposal = proposal,
                    onConfirm = { onConfirmProposal(proposal) },
                    onDismiss = { onDismissProposal(proposal.id) }
                )
            }
        }
    }
}
