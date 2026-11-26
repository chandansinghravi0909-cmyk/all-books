package com.example.data.ai

import com.example.ui.viewmodel.LibrarySnapshot
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object NotebookAiTools {

    const val FN_SEARCH_NOTEBOOK = "search_notebook_content"
    const val FN_READ_PAGE = "read_notebook_page"
    const val FN_GET_LEARNING_PROGRESS = "get_learning_progress"
    const val FN_UPDATE_LEARNING_PROGRESS = "update_learning_progress"
    const val FN_PROPOSE_CREATE_PAGE = "propose_create_page"
    const val FN_PROPOSE_APPEND_TO_PAGE = "propose_append_to_page"
    const val FN_PROPOSE_ORGANIZE_PAGE = "propose_organize_page"
    const val FN_PROPOSE_RENAME_PAGE = "propose_rename_page"
    const val FN_PROPOSE_CREATE_TOPIC = "propose_create_topic"
    const val FN_PROPOSE_CREATE_SUBTOPIC = "propose_create_subtopic"
    const val FN_PROPOSE_MOVE_CONTENT = "propose_move_content"

    val declarations: List<FunctionDeclaration> = listOf(
        FunctionDeclaration(
            name = FN_SEARCH_NOTEBOOK,
            description = "Search the user's real notebook pages, topics, subtopics, vocabulary, or fitness logs for a keyword or concept. Use this before summarizing, comparing, or finding duplicates/missing concepts.",
            parameters = mapOf(
                "query" to Schema.string("Search keywords or concept name in English, Hindi, or Hinglish."),
                "scope" to Schema.string("Either 'CURRENT_NOTEBOOK' or 'ALL_NOTEBOOKS'.")
            ),
            optionalParameters = listOf("scope")
        ),
        FunctionDeclaration(
            name = FN_READ_PAGE,
            description = "Read the full text content of a specific page in the user's notebook by page ID or page title.",
            parameters = mapOf(
                "page_identifier" to Schema.string("The exact page ID or title (or 'CURRENT' for the currently open page).")
            )
        ),
        FunctionDeclaration(
            name = FN_GET_LEARNING_PROGRESS,
            description = "Check the user's tracked learning progress records (Already Studied, Partially Studied, Not Yet Studied, Needs Revision, Newly Generated).",
            parameters = mapOf(
                "filter_keyword" to Schema.string("Optional topic or concept keyword to filter progress records.")
            ),
            optionalParameters = listOf("filter_keyword")
        ),
        FunctionDeclaration(
            name = FN_UPDATE_LEARNING_PROGRESS,
            description = "Update the user's learning progress status for a concept or topic. CRITICAL: Only use ALREADY_STUDIED or PARTIALLY_STUDIED when the user explicitly states they studied/explored/understood it. Mark AI-generated notes as NEWLY_GENERATED unless the user already studied them.",
            parameters = mapOf(
                "concept_name" to Schema.string("Name of the concept, subtopic, or topic."),
                "status" to Schema.string("One of: ALREADY_STUDIED, PARTIALLY_STUDIED, NOT_YET_STUDIED, NEEDS_REVISION, NEWLY_GENERATED."),
                "notes" to Schema.string("Brief note on what the user studied or wants to revise.")
            ),
            optionalParameters = listOf("notes")
        ),
        FunctionDeclaration(
            name = FN_PROPOSE_CREATE_PAGE,
            description = "Prepare a previewed proposal to create a new notebook page (or revision page) with structured notes. The user will review and confirm before saving.",
            parameters = mapOf(
                "title" to Schema.string("Clear, concise title for the new page."),
                "content" to Schema.string("Complete structured notebook content ready to write on the page."),
                "is_revision_page" to Schema.boolean("True if this is specifically a quick-revision or exam-revision sheet.")
            ),
            optionalParameters = listOf("is_revision_page")
        ),
        FunctionDeclaration(
            name = FN_PROPOSE_APPEND_TO_PAGE,
            description = "Prepare a previewed proposal to append new notes to the end of an existing page without overwriting or deleting existing content.",
            parameters = mapOf(
                "content_to_append" to Schema.string("The well-formatted notes to append."),
                "page_identifier" to Schema.string("Target page ID or title, or 'CURRENT' for the open page.")
            ),
            optionalParameters = listOf("page_identifier")
        ),
        FunctionDeclaration(
            name = FN_PROPOSE_ORGANIZE_PAGE,
            description = "Prepare a confirmed proposal to reorganize an existing page cleanly while preserving all original user facts and ideas. Requires explicit user confirmation and supports Undo.",
            parameters = mapOf(
                "organized_content" to Schema.string("The full reorganized version of the page preserving all user facts."),
                "summary" to Schema.string("Short summary of how the page was organized."),
                "page_identifier" to Schema.string("Target page ID or title, or 'CURRENT' for the open page.")
            ),
            optionalParameters = listOf("page_identifier")
        ),
        FunctionDeclaration(
            name = FN_PROPOSE_RENAME_PAGE,
            description = "Prepare a confirmed proposal to rename an existing notebook page.",
            parameters = mapOf(
                "new_title" to Schema.string("The proposed new title for the page."),
                "page_identifier" to Schema.string("Target page ID or title, or 'CURRENT' for the open page.")
            ),
            optionalParameters = listOf("page_identifier")
        ),
        FunctionDeclaration(
            name = FN_PROPOSE_CREATE_TOPIC,
            description = "Prepare a previewed proposal to create a new topic inside the current notebook, optionally with starter subtopics.",
            parameters = mapOf(
                "name" to Schema.string("Topic title."),
                "short_description" to Schema.string("One-line topic summary."),
                "purpose" to Schema.string("Why this topic matters."),
                "introduction" to Schema.string("2-4 sentence clear introduction."),
                "subtopics" to Schema.array(Schema.string(), "Optional starter subtopic names.")
            ),
            optionalParameters = listOf("introduction", "subtopics")
        ),
        FunctionDeclaration(
            name = FN_PROPOSE_CREATE_SUBTOPIC,
            description = "Prepare a previewed proposal to create a new subtopic under the current topic.",
            parameters = mapOf(
                "name" to Schema.string("Subtopic title."),
                "short_description" to Schema.string("One-line subtopic summary."),
                "purpose" to Schema.string("Focus or learning goal of this subtopic.")
            )
        ),
        FunctionDeclaration(
            name = FN_PROPOSE_MOVE_CONTENT,
            description = "Prepare a confirmed proposal to move a section of text from one page to another page. Requires explicit user confirmation and supports Undo.",
            parameters = mapOf(
                "source_page_identifier" to Schema.string("Source page ID or title (or 'CURRENT')."),
                "target_page_identifier" to Schema.string("Destination page ID or title."),
                "content_to_move" to Schema.string("Exact text excerpt to move."),
                "remove_from_source" to Schema.boolean("True to cut from source after copying to destination; false to copy only.")
            ),
            optionalParameters = listOf("remove_from_source")
        )
    )

    val tool: Tool = Tool.functionDeclarations(declarations)
}

data class ToolInvocation(
    val id: String?,
    val name: String,
    val args: Map<String, JsonElement>
)

data class ToolExecutionOutput(
    val responseJson: JsonObject,
    val proposal: AiProposedAction? = null,
    val logEntry: AiActionLogEntry? = null
)

class NotebookToolExecutor(
    private val snapshotProvider: () -> LibrarySnapshot,
    private val progressProvider: () -> List<LearningProgressRecord>,
    private val onRecordProgress: suspend (
        notebookId: String,
        topicId: String?,
        subtopicId: String?,
        conceptName: String,
        status: LearningProgressStatus,
        notes: String
    ) -> Result<LearningProgressRecord>
) {

    suspend fun execute(call: ToolInvocation, context: AiContext): ToolExecutionOutput {
        val snapshot = snapshotProvider()
        return when (call.name) {
            NotebookAiTools.FN_SEARCH_NOTEBOOK -> executeSearch(call.args, context, snapshot)
            NotebookAiTools.FN_READ_PAGE -> executeReadPage(call.args, context, snapshot)
            NotebookAiTools.FN_GET_LEARNING_PROGRESS -> executeGetProgress(call.args, context)
            NotebookAiTools.FN_UPDATE_LEARNING_PROGRESS -> executeUpdateProgress(call.args, context)
            NotebookAiTools.FN_PROPOSE_CREATE_PAGE -> buildCreatePageProposal(call.args, context)
            NotebookAiTools.FN_PROPOSE_APPEND_TO_PAGE -> buildAppendProposal(call.args, context, snapshot)
            NotebookAiTools.FN_PROPOSE_ORGANIZE_PAGE -> buildOrganizeProposal(call.args, context, snapshot)
            NotebookAiTools.FN_PROPOSE_RENAME_PAGE -> buildRenameProposal(call.args, context, snapshot)
            NotebookAiTools.FN_PROPOSE_CREATE_TOPIC -> buildCreateTopicProposal(call.args, context)
            NotebookAiTools.FN_PROPOSE_CREATE_SUBTOPIC -> buildCreateSubtopicProposal(call.args, context)
            NotebookAiTools.FN_PROPOSE_MOVE_CONTENT -> buildMoveContentProposal(call.args, context, snapshot)
            else -> ToolExecutionOutput(
                responseJson = jsonObj(
                    "status" to "error",
                    "message" to "Unknown tool: ${call.name}"
                )
            )
        }
    }

    private fun executeSearch(
        args: Map<String, JsonElement>,
        context: AiContext,
        snapshot: LibrarySnapshot
    ): ToolExecutionOutput {
        val query = args.str("query").trim()
        val scope = args.str("scope").uppercase().ifBlank { "CURRENT_NOTEBOOK" }
        val nbId = context.notebook?.id
        val q = query.lowercase()

        if (q.isBlank()) {
            return ToolExecutionOutput(
                responseJson = jsonObj("status" to "error", "message" to "Search query cannot be empty.")
            )
        }

        val matchingPages = snapshot.pages
            .filter { scope == "ALL_NOTEBOOKS" || nbId == null || it.notebookId == nbId }
            .filter { it.title.lowercase().contains(q) || it.content.lowercase().contains(q) }
            .take(8)
            .map { p ->
                JsonObject(
                    mapOf(
                        "type" to JsonPrimitive("page"),
                        "id" to JsonPrimitive(p.id),
                        "title" to JsonPrimitive(p.title),
                        "pageNumber" to JsonPrimitive(p.pageNumber),
                        "snippet" to JsonPrimitive(extractSnippet(p.content, q))
                    )
                )
            }

        val matchingTopics = snapshot.topics
            .filter { scope == "ALL_NOTEBOOKS" || nbId == null || it.notebookId == nbId }
            .filter {
                it.name.lowercase().contains(q) ||
                    it.shortDescription.lowercase().contains(q) ||
                    it.personalNotes.lowercase().contains(q)
            }
            .take(6)
            .map { t ->
                JsonObject(
                    mapOf(
                        "type" to JsonPrimitive("topic"),
                        "id" to JsonPrimitive(t.id),
                        "name" to JsonPrimitive(t.name),
                        "summary" to JsonPrimitive(t.shortDescription.ifBlank { t.purpose })
                    )
                )
            }

        val matchingSubtopics = snapshot.subtopics
            .filter { scope == "ALL_NOTEBOOKS" || nbId == null || it.notebookId == nbId }
            .filter { it.name.lowercase().contains(q) || it.personalNotes.lowercase().contains(q) }
            .take(6)
            .map { s ->
                JsonObject(
                    mapOf(
                        "type" to JsonPrimitive("subtopic"),
                        "id" to JsonPrimitive(s.id),
                        "name" to JsonPrimitive(s.name),
                        "summary" to JsonPrimitive(s.shortDescription)
                    )
                )
            }

        val total = matchingPages.size + matchingTopics.size + matchingSubtopics.size
        return ToolExecutionOutput(
            responseJson = JsonObject(
                mapOf(
                    "status" to JsonPrimitive("ok"),
                    "query" to JsonPrimitive(query),
                    "totalMatches" to JsonPrimitive(total),
                    "pages" to JsonArray(matchingPages),
                    "topics" to JsonArray(matchingTopics),
                    "subtopics" to JsonArray(matchingSubtopics)
                )
            ),
            logEntry = AiActionLogEntry(
                label = "Searched notebook: \"$query\"",
                succeeded = true,
                detail = "$total match(es) found"
            )
        )
    }

    private fun executeReadPage(
        args: Map<String, JsonElement>,
        context: AiContext,
        snapshot: LibrarySnapshot
    ): ToolExecutionOutput {
        val identifier = args.str("page_identifier").trim()
        val page = resolvePage(identifier, context, snapshot)
            ?: return ToolExecutionOutput(
                responseJson = jsonObj(
                    "status" to "not_found",
                    "message" to "No page matched \"$identifier\"."
                )
            )
        val liveContent = if (context.currentPage?.id == page.id && context.currentPageText.isNotBlank()) {
            context.currentPageText
        } else {
            page.content
        }
        return ToolExecutionOutput(
            responseJson = JsonObject(
                mapOf(
                    "status" to JsonPrimitive("ok"),
                    "pageId" to JsonPrimitive(page.id),
                    "title" to JsonPrimitive(page.title),
                    "pageNumber" to JsonPrimitive(page.pageNumber),
                    "content" to JsonPrimitive(liveContent.take(6000))
                )
            ),
            logEntry = AiActionLogEntry(
                label = "Read page: ${page.title}",
                succeeded = true,
                detail = "${liveContent.length} characters"
            )
        )
    }

    private fun executeGetProgress(
        args: Map<String, JsonElement>,
        context: AiContext
    ): ToolExecutionOutput {
        val filter = args.str("filter_keyword").trim().lowercase()
        val nbId = context.notebook?.id
        val items = progressProvider()
            .filter { nbId == null || it.notebookId == nbId }
            .filter { filter.isBlank() || it.conceptName.lowercase().contains(filter) || it.notes.lowercase().contains(filter) }
            .take(25)
            .map { r ->
                JsonObject(
                    mapOf(
                        "concept" to JsonPrimitive(r.conceptName),
                        "status" to JsonPrimitive(r.status.label),
                        "notes" to JsonPrimitive(r.notes)
                    )
                )
            }
        return ToolExecutionOutput(
            responseJson = JsonObject(
                mapOf(
                    "status" to JsonPrimitive("ok"),
                    "count" to JsonPrimitive(items.size),
                    "records" to JsonArray(items)
                )
            )
        )
    }

    private suspend fun executeUpdateProgress(
        args: Map<String, JsonElement>,
        context: AiContext
    ): ToolExecutionOutput {
        val concept = args.str("concept_name").trim()
        val status = LearningProgressStatus.fromRaw(args.str("status"))
        val notes = args.str("notes").trim()
        val nbId = context.notebook?.id ?: "general"
        if (concept.isBlank()) {
            return ToolExecutionOutput(
                responseJson = jsonObj("status" to "error", "message" to "concept_name is required.")
            )
        }
        val result = onRecordProgress(
            nbId,
            context.topic?.id,
            context.subtopic?.id,
            concept,
            status,
            notes
        )
        return if (result.isSuccess) {
            ToolExecutionOutput(
                responseJson = jsonObj(
                    "status" to "saved",
                    "concept" to concept,
                    "progressStatus" to status.label
                ),
                logEntry = AiActionLogEntry(
                    label = "Progress: $concept → ${status.label}",
                    succeeded = true,
                    detail = notes
                )
            )
        } else {
            ToolExecutionOutput(
                responseJson = jsonObj(
                    "status" to "error",
                    "message" to (result.exceptionOrNull()?.localizedMessage ?: "Could not save progress.")
                ),
                logEntry = AiActionLogEntry(
                    label = "Failed to update progress: $concept",
                    succeeded = false
                )
            )
        }
    }

    private fun buildCreatePageProposal(
        args: Map<String, JsonElement>,
        context: AiContext
    ): ToolExecutionOutput {
        val nbId = context.notebook?.id
            ?: return ToolExecutionOutput(
                responseJson = jsonObj("status" to "error", "message" to "No active notebook is open to create a page.")
            )
        val title = args.str("title").trim().ifBlank { "AI Study Notes" }.take(180)
        val content = args.str("content").trim()
        val isRev = args.bool("is_revision_page")
        if (content.isBlank()) {
            return ToolExecutionOutput(
                responseJson = jsonObj("status" to "error", "message" to "Page content cannot be empty.")
            )
        }
        val proposal = AiProposedAction.CreatePage(
            notebookId = nbId,
            topicId = context.topic?.id,
            subtopicId = context.subtopic?.id,
            title = title,
            content = content,
            isRevisionPage = isRev,
            summary = if (isRev) "Create revision page: \"$title\"" else "Create new page: \"$title\""
        )
        return ToolExecutionOutput(
            responseJson = jsonObj(
                "status" to "pending_user_confirmation",
                "message" to "Preview card prepared for user confirmation. Do NOT claim the page is already saved until the user taps Confirm."
            ),
            proposal = proposal
        )
    }

    private fun buildAppendProposal(
        args: Map<String, JsonElement>,
        context: AiContext,
        snapshot: LibrarySnapshot
    ): ToolExecutionOutput {
        val target = resolvePage(args.str("page_identifier").ifBlank { args.str("page_id") }, context, snapshot)
            ?: return ToolExecutionOutput(
                responseJson = jsonObj(
                    "status" to "not_found",
                    "message" to "No active or matching page found to append to. Suggest creating a new page instead."
                )
            )
        val content = args.str("content_to_append").trim()
        if (content.isBlank()) {
            return ToolExecutionOutput(
                responseJson = jsonObj("status" to "error", "message" to "content_to_append is empty.")
            )
        }
        val proposal = AiProposedAction.AppendToPage(
            pageId = target.id,
            pageTitle = target.title,
            contentToAppend = content,
            summary = "Append notes to \"${target.title}\" (preserves existing notes)"
        )
        return ToolExecutionOutput(
            responseJson = jsonObj(
                "status" to "pending_user_confirmation",
                "message" to "Append preview card created for \"${target.title}\". Inform the user they can preview and tap Append."
            ),
            proposal = proposal
        )
    }

    private fun buildOrganizeProposal(
        args: Map<String, JsonElement>,
        context: AiContext,
        snapshot: LibrarySnapshot
    ): ToolExecutionOutput {
        val target = resolvePage(args.str("page_identifier").ifBlank { args.str("page_id") }, context, snapshot)
            ?: return ToolExecutionOutput(
                responseJson = jsonObj("status" to "not_found", "message" to "No page found to organize.")
            )
        val organized = args.str("organized_content").trim()
        if (organized.isBlank()) {
            return ToolExecutionOutput(
                responseJson = jsonObj("status" to "error", "message" to "organized_content cannot be empty.")
            )
        }
        val original = if (context.currentPage?.id == target.id && context.currentPageText.isNotBlank()) {
            context.currentPageText
        } else {
            target.content
        }
        val summary = args.str("summary").trim().ifBlank { "Organize \"${target.title}\" into structured notes" }
        val proposal = AiProposedAction.OrganizePage(
            pageId = target.id,
            pageTitle = target.title,
            originalContent = original,
            organizedContent = organized,
            summary = summary
        )
        return ToolExecutionOutput(
            responseJson = jsonObj(
                "status" to "pending_user_confirmation",
                "message" to "Organized preview ready with Undo support. User must explicitly confirm before replacing \"${target.title}\"."
            ),
            proposal = proposal
        )
    }

    private fun buildRenameProposal(
        args: Map<String, JsonElement>,
        context: AiContext,
        snapshot: LibrarySnapshot
    ): ToolExecutionOutput {
        val target = resolvePage(args.str("page_identifier"), context, snapshot)
            ?: return ToolExecutionOutput(
                responseJson = jsonObj("status" to "not_found", "message" to "No page found to rename.")
            )
        val newTitle = args.str("new_title").trim().take(180)
        if (newTitle.isBlank()) {
            return ToolExecutionOutput(
                responseJson = jsonObj("status" to "error", "message" to "new_title cannot be empty.")
            )
        }
        val proposal = AiProposedAction.RenamePage(
            pageId = target.id,
            oldTitle = target.title,
            newTitle = newTitle
        )
        return ToolExecutionOutput(
            responseJson = jsonObj(
                "status" to "pending_user_confirmation",
                "message" to "Rename confirmation card prepared (\"${target.title}\" -> \"$newTitle\")."
            ),
            proposal = proposal
        )
    }

    private fun buildCreateTopicProposal(
        args: Map<String, JsonElement>,
        context: AiContext
    ): ToolExecutionOutput {
        val nbId = context.notebook?.id
            ?: return ToolExecutionOutput(
                responseJson = jsonObj("status" to "error", "message" to "No active notebook is open.")
            )
        val name = args.str("name").trim().take(150)
        if (name.isBlank()) {
            return ToolExecutionOutput(
                responseJson = jsonObj("status" to "error", "message" to "Topic name is required.")
            )
        }
        val proposal = AiProposedAction.CreateTopic(
            notebookId = nbId,
            name = name,
            shortDescription = args.str("short_description").trim().take(480),
            purpose = args.str("purpose").trim().take(900),
            introduction = args.str("introduction").trim().take(4000),
            starterSubtopics = args.strList("subtopics").take(10)
        )
        return ToolExecutionOutput(
            responseJson = jsonObj(
                "status" to "pending_user_confirmation",
                "message" to "Topic creation preview prepared for \"$name\". User must tap Confirm to create it."
            ),
            proposal = proposal
        )
    }

    private fun buildCreateSubtopicProposal(
        args: Map<String, JsonElement>,
        context: AiContext
    ): ToolExecutionOutput {
        val nbId = context.notebook?.id
        val topicId = context.topic?.id
        if (nbId.isNullOrBlank() || topicId.isNullOrBlank()) {
            return ToolExecutionOutput(
                responseJson = jsonObj("status" to "error", "message" to "Open a topic first before creating a subtopic.")
            )
        }
        val name = args.str("name").trim().take(150)
        if (name.isBlank()) {
            return ToolExecutionOutput(
                responseJson = jsonObj("status" to "error", "message" to "Subtopic name is required.")
            )
        }
        val proposal = AiProposedAction.CreateSubtopic(
            notebookId = nbId,
            topicId = topicId,
            name = name,
            shortDescription = args.str("short_description").trim().take(480),
            purpose = args.str("purpose").trim().take(900)
        )
        return ToolExecutionOutput(
            responseJson = jsonObj(
                "status" to "pending_user_confirmation",
                "message" to "Subtopic creation preview prepared for \"$name\"."
            ),
            proposal = proposal
        )
    }

    private fun buildMoveContentProposal(
        args: Map<String, JsonElement>,
        context: AiContext,
        snapshot: LibrarySnapshot
    ): ToolExecutionOutput {
        val source = resolvePage(
            args.str("source_page_identifier").ifBlank { args.str("source_page_id") },
            context,
            snapshot
        ) ?: return ToolExecutionOutput(
            responseJson = jsonObj("status" to "not_found", "message" to "Source page not found.")
        )
        val target = resolvePage(
            args.str("target_page_identifier").ifBlank { args.str("target_page_id") },
            context,
            snapshot
        ) ?: return ToolExecutionOutput(
            responseJson = jsonObj("status" to "not_found", "message" to "Target page not found.")
        )
        if (source.id == target.id) {
            return ToolExecutionOutput(
                responseJson = jsonObj("status" to "error", "message" to "Source and target pages are identical.")
            )
        }
        val contentToMove = args.str("content_to_move").trim()
        if (contentToMove.isBlank()) {
            return ToolExecutionOutput(
                responseJson = jsonObj("status" to "error", "message" to "content_to_move is required.")
            )
        }
        val removeFromSource = args["remove_from_source"]?.jsonPrimitive?.booleanOrNull ?: true
        val proposal = AiProposedAction.MoveContent(
            sourcePageId = source.id,
            sourcePageTitle = source.title,
            targetPageId = target.id,
            targetPageTitle = target.title,
            contentToMove = contentToMove,
            removeFromSource = removeFromSource,
            summary = if (removeFromSource) {
                "Move section from \"${source.title}\" → \"${target.title}\""
            } else {
                "Copy section from \"${source.title}\" → \"${target.title}\""
            }
        )
        return ToolExecutionOutput(
            responseJson = jsonObj(
                "status" to "pending_user_confirmation",
                "message" to "Move confirmation preview prepared. User must confirm before modifying pages."
            ),
            proposal = proposal
        )
    }

    private fun resolvePage(
        identifier: String,
        context: AiContext,
        snapshot: LibrarySnapshot
    ) = when {
        identifier.isBlank() || identifier.equals("CURRENT", ignoreCase = true) -> context.currentPage
        else -> {
            val nbPages = context.notebook?.id?.let { nbId ->
                snapshot.pages.filter { it.notebookId == nbId }
            } ?: snapshot.pages
            nbPages.firstOrNull { it.id == identifier }
                ?: nbPages.firstOrNull { it.title.equals(identifier, ignoreCase = true) }
                ?: nbPages.firstOrNull { it.title.contains(identifier, ignoreCase = true) }
                ?: snapshot.pages.firstOrNull { it.id == identifier }
        }
    }

    private fun extractSnippet(content: String, q: String): String {
        val idx = content.lowercase().indexOf(q)
        if (idx < 0) return content.take(180)
        val start = (idx - 60).coerceAtLeast(0)
        val end = (idx + 140).coerceAtMost(content.length)
        return content.substring(start, end).replace("\n", " ").trim()
    }

    private fun Map<String, JsonElement>.str(key: String): String =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull().orEmpty()

    private fun Map<String, JsonElement>.bool(key: String): Boolean =
        runCatching { this[key]?.jsonPrimitive?.booleanOrNull }.getOrNull() ?: false

    private fun Map<String, JsonElement>.strList(key: String): List<String> {
        val el = this[key] as? JsonArray ?: return emptyList()
        return el.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull()?.trim() }
            .filter { it.isNotBlank() }
    }

    private fun jsonObj(vararg pairs: Pair<String, String>): JsonObject =
        JsonObject(pairs.associate { (k, v) -> k to JsonPrimitive(v) })
}
