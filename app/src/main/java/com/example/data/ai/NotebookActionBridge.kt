package com.example.data.ai

import com.example.data.model.PageEntity
import com.example.data.model.SubtopicEntity
import com.example.data.model.TopicEntity
import com.example.ui.viewmodel.LibrarySnapshot

data class ActionExecutionResult(
    val succeeded: Boolean,
    val message: String,
    val undoSnapshot: AiUndoSnapshot? = null,
    val createdPageId: String? = null,
    val updatedActivePageContent: String? = null
)

/**
 * Connects AI Agent proposals to REAL persisted application mutations (Firestore via LibraryRepository).
 * Never reports success unless the actual database mutation succeeds.
 */
interface NotebookActionBridge {
    suspend fun createPage(proposal: AiProposedAction.CreatePage, snapshot: LibrarySnapshot): ActionExecutionResult
    suspend fun appendToPage(proposal: AiProposedAction.AppendToPage, snapshot: LibrarySnapshot): ActionExecutionResult
    suspend fun organizePage(proposal: AiProposedAction.OrganizePage, snapshot: LibrarySnapshot): ActionExecutionResult
    suspend fun renamePage(proposal: AiProposedAction.RenamePage, snapshot: LibrarySnapshot): ActionExecutionResult
    suspend fun createTopic(proposal: AiProposedAction.CreateTopic): ActionExecutionResult
    suspend fun createSubtopic(proposal: AiProposedAction.CreateSubtopic): ActionExecutionResult
    suspend fun moveContent(proposal: AiProposedAction.MoveContent, snapshot: LibrarySnapshot): ActionExecutionResult
    suspend fun undo(snapshot: AiUndoSnapshot): ActionExecutionResult
}

class FirestoreNotebookActionBridge(
    private val userId: String,
    private val createPageOp: suspend (PageEntity) -> Result<String>,
    private val updatePageOp: suspend (String, Map<String, Any>) -> Result<Unit>,
    private val deletePageOp: suspend (String) -> Result<Unit>,
    private val createTopicOp: suspend (TopicEntity, List<String>) -> Result<String>,
    private val createSubtopicOp: suspend (SubtopicEntity) -> Result<String>,
    private val onOpenCreatedPage: (notebookId: String, pageId: String) -> Unit = { _, _ -> }
) : NotebookActionBridge {

    override suspend fun createPage(
        proposal: AiProposedAction.CreatePage,
        snapshot: LibrarySnapshot
    ): ActionExecutionResult {
        val scopedPages = snapshot.pages.filter {
            it.notebookId == proposal.notebookId &&
                (it.topicId ?: "") == (proposal.topicId ?: "") &&
                (it.subtopicId ?: "") == (proposal.subtopicId ?: "")
        }
        val nextNum = (scopedPages.maxOfOrNull { it.pageNumber } ?: 0) + 1
        val prefs = snapshot.preferences
        val newPage = PageEntity(
            notebookId = proposal.notebookId,
            topicId = proposal.topicId,
            subtopicId = proposal.subtopicId,
            title = proposal.title.take(200),
            content = proposal.content.take(200000),
            pageNumber = nextNum,
            handwritingMode = true,
            fontKey = prefs.handwritingFontKey,
            fontSizeSp = prefs.defaultFontSizeSp,
            lineSpacingMultiplier = prefs.lineSpacingMultiplier,
            paperColorKey = prefs.paperColorKey,
            inkColorHex = 0xFF1B3F8BL
        )
        return createPageOp(newPage).fold(
            onSuccess = { createdId ->
                ActionExecutionResult(
                    succeeded = true,
                    message = "Created \"${proposal.title}\" (Page $nextNum).",
                    undoSnapshot = AiUndoSnapshot.DeleteCreatedPage(
                        pageId = createdId,
                        label = "Undo create \"${proposal.title}\""
                    ),
                    createdPageId = createdId
                )
            },
            onFailure = { err ->
                ActionExecutionResult(
                    succeeded = false,
                    message = err.localizedMessage ?: "Failed to create page in database."
                )
            }
        )
    }

    override suspend fun appendToPage(
        proposal: AiProposedAction.AppendToPage,
        snapshot: LibrarySnapshot
    ): ActionExecutionResult {
        val page = snapshot.pages.firstOrNull { it.id == proposal.pageId }
            ?: return ActionExecutionResult(false, "Target page no longer exists.")
        val previousContent = page.content
        val merged = if (previousContent.isBlank()) {
            proposal.contentToAppend
        } else {
            "${previousContent.trimEnd()}\n\n${proposal.contentToAppend}"
        }.take(200000)

        return updatePageOp(page.id, mapOf("content" to merged)).fold(
            onSuccess = {
                ActionExecutionResult(
                    succeeded = true,
                    message = "Appended notes to \"${page.title}\" (original content preserved).",
                    undoSnapshot = AiUndoSnapshot.RestorePage(
                        pageId = page.id,
                        previousTitle = page.title,
                        previousContent = previousContent,
                        label = "Undo append to \"${page.title}\""
                    ),
                    updatedActivePageContent = merged
                )
            },
            onFailure = { err ->
                ActionExecutionResult(false, err.localizedMessage ?: "Failed to append to page.")
            }
        )
    }

    override suspend fun organizePage(
        proposal: AiProposedAction.OrganizePage,
        snapshot: LibrarySnapshot
    ): ActionExecutionResult {
        val page = snapshot.pages.firstOrNull { it.id == proposal.pageId }
            ?: return ActionExecutionResult(false, "Target page no longer exists.")
        val previousContent = page.content.ifBlank { proposal.originalContent }
        val updatedContent = proposal.organizedContent.take(200000)

        return updatePageOp(page.id, mapOf("content" to updatedContent)).fold(
            onSuccess = {
                ActionExecutionResult(
                    succeeded = true,
                    message = "Organized \"${page.title}\". Tap Undo anytime to restore original text.",
                    undoSnapshot = AiUndoSnapshot.RestorePage(
                        pageId = page.id,
                        previousTitle = page.title,
                        previousContent = previousContent,
                        label = "Restore original \"${page.title}\""
                    ),
                    updatedActivePageContent = updatedContent
                )
            },
            onFailure = { err ->
                ActionExecutionResult(false, err.localizedMessage ?: "Failed to update page.")
            }
        )
    }

    override suspend fun renamePage(
        proposal: AiProposedAction.RenamePage,
        snapshot: LibrarySnapshot
    ): ActionExecutionResult {
        val page = snapshot.pages.firstOrNull { it.id == proposal.pageId }
            ?: return ActionExecutionResult(false, "Target page no longer exists.")
        val newTitle = proposal.newTitle.ifBlank { "Untitled Page" }.take(200)
        return updatePageOp(page.id, mapOf("title" to newTitle)).fold(
            onSuccess = {
                ActionExecutionResult(
                    succeeded = true,
                    message = "Renamed page to \"$newTitle\".",
                    undoSnapshot = AiUndoSnapshot.RestorePage(
                        pageId = page.id,
                        previousTitle = page.title,
                        previousContent = page.content,
                        label = "Restore title \"${page.title}\""
                    )
                )
            },
            onFailure = { err ->
                ActionExecutionResult(false, err.localizedMessage ?: "Failed to rename page.")
            }
        )
    }

    override suspend fun createTopic(proposal: AiProposedAction.CreateTopic): ActionExecutionResult {
        val entity = TopicEntity(
            notebookId = proposal.notebookId,
            name = proposal.name.take(160),
            shortDescription = proposal.shortDescription.take(500),
            purpose = proposal.purpose.take(1000),
            introduction = proposal.introduction.take(8000)
        )
        return createTopicOp(entity, proposal.starterSubtopics).fold(
            onSuccess = {
                val subNote = if (proposal.starterSubtopics.isNotEmpty()) {
                    " with ${proposal.starterSubtopics.size} subtopic(s)"
                } else ""
                ActionExecutionResult(
                    succeeded = true,
                    message = "Created topic \"${proposal.name}\"$subNote."
                )
            },
            onFailure = { err ->
                ActionExecutionResult(false, err.localizedMessage ?: "Failed to create topic.")
            }
        )
    }

    override suspend fun createSubtopic(proposal: AiProposedAction.CreateSubtopic): ActionExecutionResult {
        val entity = SubtopicEntity(
            notebookId = proposal.notebookId,
            topicId = proposal.topicId,
            name = proposal.name.take(160),
            shortDescription = proposal.shortDescription.take(500),
            purpose = proposal.purpose.take(1000)
        )
        return createSubtopicOp(entity).fold(
            onSuccess = {
                ActionExecutionResult(
                    succeeded = true,
                    message = "Created subtopic \"${proposal.name}\"."
                )
            },
            onFailure = { err ->
                ActionExecutionResult(false, err.localizedMessage ?: "Failed to create subtopic.")
            }
        )
    }

    override suspend fun moveContent(
        proposal: AiProposedAction.MoveContent,
        snapshot: LibrarySnapshot
    ): ActionExecutionResult {
        val source = snapshot.pages.firstOrNull { it.id == proposal.sourcePageId }
            ?: return ActionExecutionResult(false, "Source page not found.")
        val target = snapshot.pages.firstOrNull { it.id == proposal.targetPageId }
            ?: return ActionExecutionResult(false, "Destination page not found.")

        val targetPrev = target.content
        val sourcePrev = source.content
        val newTargetContent = if (targetPrev.isBlank()) {
            proposal.contentToMove
        } else {
            "${targetPrev.trimEnd()}\n\n${proposal.contentToMove}"
        }.take(200000)

        val targetResult = updatePageOp(target.id, mapOf("content" to newTargetContent))
        if (targetResult.isFailure) {
            return ActionExecutionResult(
                false,
                targetResult.exceptionOrNull()?.localizedMessage ?: "Failed to write to destination page."
            )
        }

        if (proposal.removeFromSource) {
            val cleanedSource = sourcePrev.replace(proposal.contentToMove, "")
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
            val srcResult = updatePageOp(source.id, mapOf("content" to cleanedSource))
            if (srcResult.isFailure) {
                return ActionExecutionResult(
                    false,
                    "Copied to \"${target.title}\", but failed to update source page: ${srcResult.exceptionOrNull()?.localizedMessage}"
                )
            }
        }

        return ActionExecutionResult(
            succeeded = true,
            message = if (proposal.removeFromSource) {
                "Moved content from \"${source.title}\" to \"${target.title}\"."
            } else {
                "Copied content from \"${source.title}\" to \"${target.title}\"."
            },
            undoSnapshot = AiUndoSnapshot.RestoreTwoPages(
                sourcePageId = source.id,
                sourcePreviousContent = sourcePrev,
                targetPageId = target.id,
                targetPreviousContent = targetPrev,
                label = "Undo move to \"${target.title}\""
            )
        )
    }

    override suspend fun undo(snapshot: AiUndoSnapshot): ActionExecutionResult = when (snapshot) {
        is AiUndoSnapshot.RestorePage -> {
            updatePageOp(
                snapshot.pageId,
                mapOf(
                    "title" to snapshot.previousTitle,
                    "content" to snapshot.previousContent
                )
            ).fold(
                onSuccess = {
                    ActionExecutionResult(
                        succeeded = true,
                        message = "Restored original page content.",
                        updatedActivePageContent = snapshot.previousContent
                    )
                },
                onFailure = { err ->
                    ActionExecutionResult(false, err.localizedMessage ?: "Undo failed.")
                }
            )
        }

        is AiUndoSnapshot.RestoreTwoPages -> {
            val r1 = updatePageOp(snapshot.sourcePageId, mapOf("content" to snapshot.sourcePreviousContent))
            val r2 = updatePageOp(snapshot.targetPageId, mapOf("content" to snapshot.targetPreviousContent))
            if (r1.isSuccess && r2.isSuccess) {
                ActionExecutionResult(true, "Restored both pages to their previous content.")
            } else {
                ActionExecutionResult(false, "Could not fully undo move operation.")
            }
        }

        is AiUndoSnapshot.DeleteCreatedPage -> {
            deletePageOp(snapshot.pageId).fold(
                onSuccess = { ActionExecutionResult(true, "Removed newly created page.") },
                onFailure = { err -> ActionExecutionResult(false, err.localizedMessage ?: "Undo delete failed.") }
            )
        }
    }
}
