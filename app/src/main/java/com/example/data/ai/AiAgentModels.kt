package com.example.data.ai

import androidx.compose.runtime.Immutable
import com.example.data.model.NotebookEntity
import com.example.data.model.PageEntity
import com.example.data.model.SubtopicEntity
import com.example.data.model.TopicEntity
import java.util.UUID

enum class LearningProgressStatus(val label: String) {
    ALREADY_STUDIED("Already Studied"),
    PARTIALLY_STUDIED("Partially Studied"),
    NOT_YET_STUDIED("Not Yet Studied"),
    NEEDS_REVISION("Needs Revision"),
    NEWLY_GENERATED("Newly Generated");

    companion object {
        fun fromRaw(raw: String?): LearningProgressStatus {
            if (raw.isNullOrBlank()) return NEWLY_GENERATED
            val normalized = raw.trim().uppercase().replace(" ", "_").replace("-", "_")
            return entries.firstOrNull {
                it.name == normalized || it.label.equals(raw.trim(), ignoreCase = true)
            } ?: when {
                normalized.contains("ALREADY") || normalized == "STUDIED" || normalized == "UNDERSTOOD" -> ALREADY_STUDIED
                normalized.contains("PARTIAL") -> PARTIALLY_STUDIED
                normalized.contains("NOT") -> NOT_YET_STUDIED
                normalized.contains("REVIS") -> NEEDS_REVISION
                else -> NEWLY_GENERATED
            }
        }
    }
}

@Immutable
data class LearningProgressRecord(
    val id: String = UUID.randomUUID().toString(),
    val notebookId: String,
    val topicId: String? = null,
    val subtopicId: String? = null,
    val conceptName: String,
    val status: LearningProgressStatus,
    val notes: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis()
)

enum class AiChatRole {
    USER,
    ASSISTANT
}

enum class ProposalStatus {
    PENDING,
    APPLIED,
    DISMISSED,
    FAILED
}

sealed interface AiProposedAction {
    val id: String
    val summary: String
    val requiresExplicitConfirmation: Boolean
    val status: ProposalStatus
    val statusNote: String

    fun withStatus(newStatus: ProposalStatus, note: String = ""): AiProposedAction

    @Immutable
    data class CreatePage(
        override val id: String = UUID.randomUUID().toString(),
        val notebookId: String,
        val topicId: String? = null,
        val subtopicId: String? = null,
        val title: String,
        val content: String,
        val isRevisionPage: Boolean = false,
        override val summary: String = "Create new page: $title",
        override val requiresExplicitConfirmation: Boolean = false,
        override val status: ProposalStatus = ProposalStatus.PENDING,
        override val statusNote: String = ""
    ) : AiProposedAction {
        override fun withStatus(newStatus: ProposalStatus, note: String) =
            copy(status = newStatus, statusNote = note)
    }

    @Immutable
    data class AppendToPage(
        override val id: String = UUID.randomUUID().toString(),
        val pageId: String,
        val pageTitle: String,
        val contentToAppend: String,
        override val summary: String = "Append notes to \"$pageTitle\"",
        override val requiresExplicitConfirmation: Boolean = false,
        override val status: ProposalStatus = ProposalStatus.PENDING,
        override val statusNote: String = ""
    ) : AiProposedAction {
        override fun withStatus(newStatus: ProposalStatus, note: String) =
            copy(status = newStatus, statusNote = note)
    }

    @Immutable
    data class OrganizePage(
        override val id: String = UUID.randomUUID().toString(),
        val pageId: String,
        val pageTitle: String,
        val originalContent: String,
        val organizedContent: String,
        override val summary: String = "Replace \"$pageTitle\" with organized version",
        override val requiresExplicitConfirmation: Boolean = true,
        override val status: ProposalStatus = ProposalStatus.PENDING,
        override val statusNote: String = ""
    ) : AiProposedAction {
        override fun withStatus(newStatus: ProposalStatus, note: String) =
            copy(status = newStatus, statusNote = note)
    }

    @Immutable
    data class RenamePage(
        override val id: String = UUID.randomUUID().toString(),
        val pageId: String,
        val oldTitle: String,
        val newTitle: String,
        override val summary: String = "Rename \"$oldTitle\" → \"$newTitle\"",
        override val requiresExplicitConfirmation: Boolean = true,
        override val status: ProposalStatus = ProposalStatus.PENDING,
        override val statusNote: String = ""
    ) : AiProposedAction {
        override fun withStatus(newStatus: ProposalStatus, note: String) =
            copy(status = newStatus, statusNote = note)
    }

    @Immutable
    data class CreateTopic(
        override val id: String = UUID.randomUUID().toString(),
        val notebookId: String,
        val name: String,
        val shortDescription: String,
        val purpose: String,
        val introduction: String,
        val starterSubtopics: List<String> = emptyList(),
        override val summary: String = "Create topic: $name",
        override val requiresExplicitConfirmation: Boolean = false,
        override val status: ProposalStatus = ProposalStatus.PENDING,
        override val statusNote: String = ""
    ) : AiProposedAction {
        override fun withStatus(newStatus: ProposalStatus, note: String) =
            copy(status = newStatus, statusNote = note)
    }

    @Immutable
    data class CreateSubtopic(
        override val id: String = UUID.randomUUID().toString(),
        val notebookId: String,
        val topicId: String,
        val name: String,
        val shortDescription: String,
        val purpose: String,
        override val summary: String = "Create subtopic: $name",
        override val requiresExplicitConfirmation: Boolean = false,
        override val status: ProposalStatus = ProposalStatus.PENDING,
        override val statusNote: String = ""
    ) : AiProposedAction {
        override fun withStatus(newStatus: ProposalStatus, note: String) =
            copy(status = newStatus, statusNote = note)
    }

    @Immutable
    data class MoveContent(
        override val id: String = UUID.randomUUID().toString(),
        val sourcePageId: String,
        val sourcePageTitle: String,
        val targetPageId: String,
        val targetPageTitle: String,
        val contentToMove: String,
        val removeFromSource: Boolean = true,
        override val summary: String = "Move content from \"$sourcePageTitle\" → \"$targetPageTitle\"",
        override val requiresExplicitConfirmation: Boolean = true,
        override val status: ProposalStatus = ProposalStatus.PENDING,
        override val statusNote: String = ""
    ) : AiProposedAction {
        override fun withStatus(newStatus: ProposalStatus, note: String) =
            copy(status = newStatus, statusNote = note)
    }
}

sealed interface AiUndoSnapshot {
    val label: String

    @Immutable
    data class RestorePage(
        val pageId: String,
        val previousTitle: String,
        val previousContent: String,
        override val label: String
    ) : AiUndoSnapshot

    @Immutable
    data class RestoreTwoPages(
        val sourcePageId: String,
        val sourcePreviousContent: String,
        val targetPageId: String,
        val targetPreviousContent: String,
        override val label: String
    ) : AiUndoSnapshot

    @Immutable
    data class DeleteCreatedPage(
        val pageId: String,
        override val label: String
    ) : AiUndoSnapshot
}

@Immutable
data class AiActionLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val succeeded: Boolean,
    val detail: String = "",
    val undoSnapshot: AiUndoSnapshot? = null,
    val undone: Boolean = false
)

@Immutable
data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: AiChatRole,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val actionLabel: String? = null,
    val proposals: List<AiProposedAction> = emptyList(),
    val actionLogs: List<AiActionLogEntry> = emptyList(),
    val isError: Boolean = false,
    val retryPrompt: String? = null,
    val modelUsed: String? = null,
    val usedFallbackModel: Boolean = false
)

@Immutable
data class AiConversationSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Conversation",
    val notebookId: String? = null,
    val notebookName: String = "",
    val topicId: String? = null,
    val topicName: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val messages: List<AiChatMessage> = emptyList()
)

data class AiContext(
    val notebook: NotebookEntity? = null,
    val topic: TopicEntity? = null,
    val subtopic: SubtopicEntity? = null,
    val currentPage: PageEntity? = null,
    val personalNotes: String = "",
    val currentPageText: String = "",
    val selectedText: String = "",
    val relatedPages: List<PageEntity> = emptyList(),
    val notebookTopics: List<TopicEntity> = emptyList(),
    val topicSubtopics: List<SubtopicEntity> = emptyList(),
    val specializedContextSummary: String = "",
    val learningProgress: List<LearningProgressRecord> = emptyList(),
    val responseLanguage: String = "MIXED"
)
