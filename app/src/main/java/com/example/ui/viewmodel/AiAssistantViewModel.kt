package com.example.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.ActionExecutionResult
import com.example.data.ai.AiActionLogEntry
import com.example.data.ai.AiAgentStorage
import com.example.data.ai.AiChatMessage
import com.example.data.ai.AiChatRole
import com.example.data.ai.AiContext
import com.example.data.ai.AiConversationSession
import com.example.data.ai.AiProposedAction
import com.example.data.ai.AiTurnEvent
import com.example.data.ai.AiUndoSnapshot
import com.example.data.ai.GeneratedTopicInfo
import com.example.data.ai.LearningProgressRecord
import com.example.data.ai.LearningProgressStatus
import com.example.data.ai.NotebookActionBridge
import com.example.data.ai.NotebookAiRepository
import com.example.data.ai.NotebookToolExecutor
import com.example.data.ai.ProposalStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

@Immutable
data class AiAgentUiState(
    val activeSession: AiConversationSession = AiConversationSession(),
    val savedSessions: List<AiConversationSession> = emptyList(),
    val learningProgress: List<LearningProgressRecord> = emptyList(),
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val streamingText: String = "",
    val errorMessage: String? = null,
    val lastFailedPrompt: String? = null,
    val lastFailedActionLabel: String = "Ask AI",
    val lastUndoSnapshot: AiUndoSnapshot? = null,
    val actionFeedback: String? = null
)

sealed interface TopicGenUiState {
    data object Idle : TopicGenUiState
    data object Loading : TopicGenUiState
    data class Success(val info: GeneratedTopicInfo) : TopicGenUiState
    data class Error(val message: String) : TopicGenUiState
}

class AiAssistantViewModel(
    private val aiRepository: NotebookAiRepository = NotebookAiRepository(),
    private val storage: AiAgentStorage? = null
) : ViewModel() {

    private val _agentState = MutableStateFlow(AiAgentUiState())
    val agentState: StateFlow<AiAgentUiState> = _agentState.asStateFlow()

    private val _topicGenState = MutableStateFlow<TopicGenUiState>(TopicGenUiState.Idle)
    val topicGenState: StateFlow<TopicGenUiState> = _topicGenState.asStateFlow()

    private var activeJob: Job? = null

    init {
        if (storage != null) {
            viewModelScope.launch {
                val loadedSessions = storage.loadSessions()
                val loadedProgress = storage.loadLearningProgress()
                _agentState.update { state ->
                    state.copy(
                        savedSessions = loadedSessions,
                        learningProgress = loadedProgress
                    )
                }
            }
        }
    }

    /**
     * Prepares the active session when the user opens the AI panel from a notebook/topic/page.
     * Reuses an existing session for the same scope if one is open, without discarding history.
     */
    fun bindToContext(context: AiContext) {
        val current = _agentState.value
        val nbId = context.notebook?.id
        val topicId = context.topic?.id
        val active = current.activeSession

        val sameScope = active.notebookId == nbId && active.topicId == topicId
        if (sameScope && (active.messages.isNotEmpty() || active.notebookId != null)) {
            return
        }

        // Preserve any non-empty current session in savedSessions before switching scope
        val updatedSaved = mergeIntoSaved(current.savedSessions, active)
        val matchingSaved = updatedSaved.firstOrNull {
            it.notebookId == nbId && it.topicId == topicId
        }
        val nextActive = matchingSaved ?: AiConversationSession(
            id = UUID.randomUUID().toString(),
            title = context.topic?.name ?: context.notebook?.name ?: "Notebook AI",
            notebookId = nbId,
            notebookName = context.notebook?.name.orEmpty(),
            topicId = topicId,
            topicName = context.topic?.name
        )

        _agentState.update {
            it.copy(
                activeSession = nextActive,
                savedSessions = updatedSaved,
                errorMessage = null,
                actionFeedback = null
            )
        }
        persistSessions(updatedSaved)
    }

    fun startNewConversation(context: AiContext) {
        activeJob?.cancel()
        val current = _agentState.value
        val archived = mergeIntoSaved(current.savedSessions, current.activeSession)
        val fresh = AiConversationSession(
            id = UUID.randomUUID().toString(),
            title = context.topic?.name ?: context.notebook?.name ?: "New Conversation",
            notebookId = context.notebook?.id,
            notebookName = context.notebook?.name.orEmpty(),
            topicId = context.topic?.id,
            topicName = context.topic?.name
        )
        _agentState.update {
            it.copy(
                activeSession = fresh,
                savedSessions = archived,
                isBusy = false,
                statusMessage = null,
                streamingText = "",
                errorMessage = null,
                lastFailedPrompt = null,
                actionFeedback = "Started a new conversation. Previous chats remain saved in History."
            )
        }
        persistSessions(archived)
    }

    fun selectSavedSession(sessionId: String) {
        activeJob?.cancel()
        val current = _agentState.value
        val archived = mergeIntoSaved(current.savedSessions, current.activeSession)
        val target = archived.firstOrNull { it.id == sessionId } ?: return
        _agentState.update {
            it.copy(
                activeSession = target,
                savedSessions = archived,
                isBusy = false,
                statusMessage = null,
                streamingText = "",
                errorMessage = null,
                lastFailedPrompt = null,
                actionFeedback = null
            )
        }
        persistSessions(archived)
    }

    fun deleteSavedSession(sessionId: String) {
        val current = _agentState.value
        val filtered = current.savedSessions.filterNot { it.id == sessionId }
        val nextActive = if (current.activeSession.id == sessionId) {
            AiConversationSession(
                notebookId = current.activeSession.notebookId,
                notebookName = current.activeSession.notebookName,
                topicId = current.activeSession.topicId,
                topicName = current.activeSession.topicName
            )
        } else {
            current.activeSession
        }
        _agentState.update {
            it.copy(
                activeSession = nextActive,
                savedSessions = filtered
            )
        }
        persistSessions(filtered)
    }

    fun sendMessage(
        userRequest: String,
        actionLabel: String = "Ask AI",
        context: AiContext,
        snapshotProvider: () -> LibrarySnapshot,
        isRetry: Boolean = false
    ) {
        val trimmed = userRequest.trim()
        if (trimmed.isBlank()) return

        // Requirement 1.7: Prevent duplicate submissions while a request is in flight
        if (_agentState.value.isBusy) {
            return
        }

        val existingSession = _agentState.value.activeSession
        val historyForModel = existingSession.messages.filter { !it.isError }

        val updatedMessages = if (isRetry && existingSession.messages.lastOrNull()?.role == AiChatRole.USER) {
            existingSession.messages
        } else {
            existingSession.messages + AiChatMessage(
                role = AiChatRole.USER,
                text = trimmed,
                actionLabel = actionLabel.takeIf { it != "Ask AI" }
            )
        }

        val sessionTitle = if (existingSession.messages.isEmpty()) {
            trimmed.take(48)
        } else {
            existingSession.title
        }

        val workingSession = existingSession.copy(
            title = sessionTitle,
            notebookId = context.notebook?.id ?: existingSession.notebookId,
            notebookName = context.notebook?.name ?: existingSession.notebookName,
            topicId = context.topic?.id ?: existingSession.topicId,
            topicName = context.topic?.name ?: existingSession.topicName,
            updatedAtMillis = System.currentTimeMillis(),
            messages = updatedMessages
        )

        _agentState.update {
            it.copy(
                activeSession = workingSession,
                isBusy = true,
                statusMessage = "Thinking with notebook context…",
                streamingText = "",
                errorMessage = null,
                lastFailedPrompt = null,
                actionFeedback = null
            )
        }

        val enrichedContext = context.copy(
            learningProgress = _agentState.value.learningProgress
                .filter { context.notebook == null || it.notebookId == context.notebook.id }
        )

        val toolExecutor = NotebookToolExecutor(
            snapshotProvider = snapshotProvider,
            progressProvider = { _agentState.value.learningProgress },
            onRecordProgress = { nbId, topicId, subId, concept, status, notes ->
                upsertLearningProgressInternal(nbId, topicId, subId, concept, status, notes)
            }
        )

        activeJob = viewModelScope.launch {
            var completedSuccessfully = false
            try {
                aiRepository.runAgentTurn(
                    userRequest = trimmed,
                    actionLabel = actionLabel,
                    history = historyForModel,
                    context = enrichedContext,
                    toolExecutor = toolExecutor
                ).collect { event ->
                    when (event) {
                        is AiTurnEvent.TextDelta -> {
                            _agentState.update { state ->
                                state.copy(
                                    streamingText = event.accumulatedText,
                                    statusMessage = null
                                )
                            }
                        }

                        is AiTurnEvent.ToolStatus -> {
                            _agentState.update { state ->
                                state.copy(statusMessage = event.statusLabel)
                            }
                        }

                        is AiTurnEvent.Completed -> {
                            completedSuccessfully = true
                            val assistantMsg = AiChatMessage(
                                role = AiChatRole.ASSISTANT,
                                text = event.finalText,
                                actionLabel = actionLabel.takeIf { it != "Ask AI" },
                                proposals = event.proposals,
                                actionLogs = event.actionLogs,
                                modelUsed = event.modelUsed,
                                usedFallbackModel = event.usedFallbackModel
                            )
                            val doneSession = _agentState.value.activeSession.copy(
                                updatedAtMillis = System.currentTimeMillis(),
                                messages = _agentState.value.activeSession.messages + assistantMsg
                            )
                            val saved = mergeIntoSaved(_agentState.value.savedSessions, doneSession)
                            _agentState.update { state ->
                                state.copy(
                                    activeSession = doneSession,
                                    savedSessions = saved,
                                    isBusy = false,
                                    statusMessage = null,
                                    streamingText = "",
                                    errorMessage = null,
                                    lastFailedPrompt = null
                                )
                            }
                            persistSessions(saved)
                        }
                    }
                }

                if (!completedSuccessfully) {
                    val partial = _agentState.value.streamingText.trim()
                    if (partial.isNotEmpty()) {
                        val assistantMsg = AiChatMessage(
                            role = AiChatRole.ASSISTANT,
                            text = partial
                        )
                        val doneSession = _agentState.value.activeSession.copy(
                            updatedAtMillis = System.currentTimeMillis(),
                            messages = _agentState.value.activeSession.messages + assistantMsg
                        )
                        val saved = mergeIntoSaved(_agentState.value.savedSessions, doneSession)
                        _agentState.update {
                            it.copy(
                                activeSession = doneSession,
                                savedSessions = saved,
                                isBusy = false,
                                statusMessage = null,
                                streamingText = ""
                            )
                        }
                        persistSessions(saved)
                    } else {
                        setErrorState(
                            "AI returned an empty response. Please tap Retry.",
                            trimmed,
                            actionLabel
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI agent turn timed out", e)
                setErrorState(
                    NotebookAiRepository.classifyError(e),
                    trimmed,
                    actionLabel
                )
            } catch (e: CancellationException) {
                // Cooperative cancellation (e.g., user tapped Stop or started New Chat)
                val partial = _agentState.value.streamingText.trim()
                if (partial.isNotEmpty() && _agentState.value.isBusy) {
                    val stoppedMsg = AiChatMessage(
                        role = AiChatRole.ASSISTANT,
                        text = "$partial\n\n_(Stopped by user)_"
                    )
                    val stoppedSession = _agentState.value.activeSession.copy(
                        updatedAtMillis = System.currentTimeMillis(),
                        messages = _agentState.value.activeSession.messages + stoppedMsg
                    )
                    val saved = mergeIntoSaved(_agentState.value.savedSessions, stoppedSession)
                    _agentState.update {
                        it.copy(
                            activeSession = stoppedSession,
                            savedSessions = saved,
                            isBusy = false,
                            statusMessage = null,
                            streamingText = ""
                        )
                    }
                    persistSessions(saved)
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI agent turn failed", e)
                setErrorState(
                    NotebookAiRepository.classifyError(e),
                    trimmed,
                    actionLabel
                )
            } finally {
                // GUARANTEE: Thinking indicator NEVER remains stuck under any code path
                _agentState.update { state ->
                    if (state.isBusy || state.statusMessage != null) {
                        state.copy(
                            isBusy = false,
                            statusMessage = null,
                            streamingText = ""
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun retryLastFailed(
        context: AiContext,
        snapshotProvider: () -> LibrarySnapshot
    ) {
        val state = _agentState.value
        val prompt = state.lastFailedPrompt ?: return
        sendMessage(
            userRequest = prompt,
            actionLabel = state.lastFailedActionLabel,
            context = context,
            snapshotProvider = snapshotProvider,
            isRetry = true
        )
    }

    fun cancelActiveRequest() {
        activeJob?.cancel()
        _agentState.update {
            it.copy(
                isBusy = false,
                statusMessage = null,
                streamingText = ""
            )
        }
    }

    fun executeProposal(
        messageId: String,
        proposal: AiProposedAction,
        bridge: NotebookActionBridge,
        snapshot: LibrarySnapshot,
        onPageMutated: (ActionExecutionResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = when (proposal) {
                is AiProposedAction.CreatePage -> bridge.createPage(proposal, snapshot)
                is AiProposedAction.AppendToPage -> bridge.appendToPage(proposal, snapshot)
                is AiProposedAction.OrganizePage -> bridge.organizePage(proposal, snapshot)
                is AiProposedAction.RenamePage -> bridge.renamePage(proposal, snapshot)
                is AiProposedAction.CreateTopic -> bridge.createTopic(proposal)
                is AiProposedAction.CreateSubtopic -> bridge.createSubtopic(proposal)
                is AiProposedAction.MoveContent -> bridge.moveContent(proposal, snapshot)
            }

            // If AI created a new page with generated notes, record as NEWLY_GENERATED (never ALREADY_STUDIED!)
            if (result.succeeded && proposal is AiProposedAction.CreatePage) {
                upsertLearningProgressInternal(
                    notebookId = proposal.notebookId,
                    topicId = proposal.topicId,
                    subtopicId = proposal.subtopicId,
                    conceptName = proposal.title,
                    status = LearningProgressStatus.NEWLY_GENERATED,
                    notes = "AI-generated page (not yet marked studied)"
                )
            }

            val nextStatus = if (result.succeeded) ProposalStatus.APPLIED else ProposalStatus.FAILED
            val logEntry = AiActionLogEntry(
                label = if (result.succeeded) result.message else "Action failed: ${proposal.summary}",
                succeeded = result.succeeded,
                detail = if (result.succeeded) "" else result.message,
                undoSnapshot = result.undoSnapshot
            )

            updateProposalInSession(
                messageId = messageId,
                proposalId = proposal.id,
                newStatus = nextStatus,
                statusNote = result.message,
                appendLog = logEntry,
                undoSnapshot = result.undoSnapshot
            )
            onPageMutated(result)
        }
    }

    fun dismissProposal(messageId: String, proposalId: String) {
        updateProposalInSession(
            messageId = messageId,
            proposalId = proposalId,
            newStatus = ProposalStatus.DISMISSED,
            statusNote = "Dismissed — no changes made.",
            appendLog = null,
            undoSnapshot = null
        )
    }

    fun undoLastAction(
        undoSnapshot: AiUndoSnapshot,
        bridge: NotebookActionBridge,
        onRestored: (ActionExecutionResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = bridge.undo(undoSnapshot)
            _agentState.update { state ->
                val updatedMsgs = state.activeSession.messages.map { msg ->
                    msg.copy(
                        actionLogs = msg.actionLogs.map { log ->
                            if (log.undoSnapshot == undoSnapshot && result.succeeded) {
                                log.copy(undone = true, detail = "Undone")
                            } else {
                                log
                            }
                        }
                    )
                }
                val updatedSession = state.activeSession.copy(messages = updatedMsgs)
                val saved = mergeIntoSaved(state.savedSessions, updatedSession)
                persistSessions(saved)
                state.copy(
                    activeSession = updatedSession,
                    savedSessions = saved,
                    lastUndoSnapshot = if (result.succeeded && state.lastUndoSnapshot == undoSnapshot) null else state.lastUndoSnapshot,
                    actionFeedback = result.message
                )
            }
            onRestored(result)
        }
    }

    fun updateLearningProgressManually(
        notebookId: String,
        topicId: String?,
        subtopicId: String?,
        conceptName: String,
        status: LearningProgressStatus,
        notes: String = ""
    ) {
        viewModelScope.launch {
            upsertLearningProgressInternal(notebookId, topicId, subtopicId, conceptName, status, notes)
            _agentState.update {
                it.copy(actionFeedback = "Marked \"$conceptName\" as ${status.label}.")
            }
        }
    }

    fun deleteLearningProgress(recordId: String) {
        viewModelScope.launch {
            val updated = _agentState.value.learningProgress.filterNot { it.id == recordId }
            _agentState.update { it.copy(learningProgress = updated) }
            storage?.saveLearningProgress(updated)
        }
    }

    fun clearActionFeedback() {
        _agentState.update { it.copy(actionFeedback = null) }
    }

    fun generateTopicMetadata(
        notebookName: String,
        notebookPurpose: String,
        topicName: String,
        languagePref: String
    ) {
        viewModelScope.launch {
            _topicGenState.value = TopicGenUiState.Loading
            try {
                val info = aiRepository.generateStructuredTopicInfo(
                    notebookName = notebookName,
                    notebookPurpose = notebookPurpose,
                    topicName = topicName,
                    languagePref = languagePref
                )
                _topicGenState.value = TopicGenUiState.Success(info)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Topic AI generation timed out", e)
                _topicGenState.value = TopicGenUiState.Error("Topic generation timed out. You can continue manually.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Topic AI generation failed", e)
                _topicGenState.value = TopicGenUiState.Error(
                    NotebookAiRepository.classifyError(e)
                )
            }
        }
    }

    fun resetTopicGenState() {
        _topicGenState.value = TopicGenUiState.Idle
    }

    private fun setErrorState(message: String, failedPrompt: String, failedAction: String) {
        _agentState.update { state ->
            state.copy(
                isBusy = false,
                statusMessage = null,
                streamingText = "",
                errorMessage = message,
                lastFailedPrompt = failedPrompt,
                lastFailedActionLabel = failedAction
            )
        }
    }

    private fun updateProposalInSession(
        messageId: String,
        proposalId: String,
        newStatus: ProposalStatus,
        statusNote: String,
        appendLog: AiActionLogEntry?,
        undoSnapshot: AiUndoSnapshot?
    ) {
        _agentState.update { state ->
            val updatedMessages = state.activeSession.messages.map { msg ->
                if (msg.id == messageId) {
                    msg.copy(
                        proposals = msg.proposals.map { p ->
                            if (p.id == proposalId) p.withStatus(newStatus, statusNote) else p
                        },
                        actionLogs = if (appendLog != null) msg.actionLogs + appendLog else msg.actionLogs
                    )
                } else {
                    msg
                }
            }
            val updatedSession = state.activeSession.copy(
                updatedAtMillis = System.currentTimeMillis(),
                messages = updatedMessages
            )
            val saved = mergeIntoSaved(state.savedSessions, updatedSession)
            persistSessions(saved)
            state.copy(
                activeSession = updatedSession,
                savedSessions = saved,
                lastUndoSnapshot = undoSnapshot ?: state.lastUndoSnapshot,
                actionFeedback = statusNote.takeIf { it.isNotBlank() }
            )
        }
    }

    private suspend fun upsertLearningProgressInternal(
        notebookId: String,
        topicId: String?,
        subtopicId: String?,
        conceptName: String,
        status: LearningProgressStatus,
        notes: String
    ): Result<LearningProgressRecord> {
        val cleanConcept = conceptName.trim()
        if (cleanConcept.isBlank()) {
            return Result.failure(IllegalArgumentException("Concept name cannot be blank."))
        }
        val currentList = _agentState.value.learningProgress
        val existing = currentList.firstOrNull {
            it.notebookId == notebookId && it.conceptName.equals(cleanConcept, ignoreCase = true)
        }
        val record = if (existing != null) {
            existing.copy(
                topicId = topicId ?: existing.topicId,
                subtopicId = subtopicId ?: existing.subtopicId,
                status = status,
                notes = notes.ifBlank { existing.notes },
                updatedAtMillis = System.currentTimeMillis()
            )
        } else {
            LearningProgressRecord(
                notebookId = notebookId,
                topicId = topicId,
                subtopicId = subtopicId,
                conceptName = cleanConcept,
                status = status,
                notes = notes,
                updatedAtMillis = System.currentTimeMillis()
            )
        }
        val updated = (listOf(record) + currentList.filterNot { it.id == record.id })
            .sortedByDescending { it.updatedAtMillis }
        _agentState.update { it.copy(learningProgress = updated) }
        storage?.saveLearningProgress(updated)
        return Result.success(record)
    }

    private fun mergeIntoSaved(
        saved: List<AiConversationSession>,
        session: AiConversationSession
    ): List<AiConversationSession> {
        if (session.messages.isEmpty()) return saved
        return (listOf(session) + saved.filterNot { it.id == session.id })
            .sortedByDescending { it.updatedAtMillis }
            .take(30)
    }

    private fun persistSessions(sessions: List<AiConversationSession>) {
        if (storage == null) return
        viewModelScope.launch {
            storage.saveSessions(sessions)
        }
    }

    private companion object {
        const val TAG = "AiAssistantVM"
    }
}
