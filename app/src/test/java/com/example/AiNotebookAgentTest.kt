package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.AppCheckProviderMode
import com.example.core.resolveAppCheckMode
import com.example.data.ai.AiActionLogEntry
import com.example.data.ai.AiAgentBackend
import com.example.data.ai.AiAgentStorage
import com.example.data.ai.AiChatMessage
import com.example.data.ai.AiChatRole
import com.example.data.ai.AiContext
import com.example.data.ai.AiProposedAction
import com.example.data.ai.AiTurnEvent
import com.example.data.ai.AiUndoSnapshot
import com.example.data.ai.FirestoreNotebookActionBridge
import com.example.data.ai.GeneratedTopicInfo
import com.example.data.ai.LearningProgressRecord
import com.example.data.ai.LearningProgressStatus
import com.example.data.ai.NotebookAiRepository
import com.example.data.ai.NotebookToolExecutor
import com.example.data.ai.ProposalStatus
import com.example.data.ai.ToolInvocation
import com.example.data.model.PageEntity
import com.example.data.model.SeedData
import com.example.data.model.SubtopicEntity
import com.example.data.model.TopicEntity
import com.example.ui.viewmodel.AiAssistantViewModel
import com.example.ui.viewmodel.LibrarySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiNotebookAgentTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var appContext: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        appContext = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // 0. App Check Mode Resolution (Root Cause Fix for Emulator Hang & Phone 403)
    // -------------------------------------------------------------------------

    @Test
    fun `app check selects debug with intent token when provided`() {
        val mode = resolveAppCheckMode(
            isDebugBuild = true,
            intentDebugToken = "token-1234",
            hasVerifiedPersistedToken = false,
            isEmulator = true,
            recaptchaSiteKey = "site-key"
        )
        assertEquals(AppCheckProviderMode.DEBUG_WITH_INTENT_TOKEN, mode)
    }

    @Test
    fun `app check never installs recaptcha on emulator even without intent extra`() {
        val mode = resolveAppCheckMode(
            isDebugBuild = true,
            intentDebugToken = null,
            hasVerifiedPersistedToken = false,
            isEmulator = true,
            recaptchaSiteKey = "site-key"
        )
        assertEquals(AppCheckProviderMode.DEBUG_EMULATOR_OR_PERSISTED, mode)
    }

    @Test
    fun `app check uses recaptcha on physical phone in debug without intent token`() {
        val mode = resolveAppCheckMode(
            isDebugBuild = true,
            intentDebugToken = null,
            hasVerifiedPersistedToken = false,
            isEmulator = false,
            recaptchaSiteKey = "6LeO6CUuAAAAAESiND4mgIx-jAMOgRrPKuHtqxLZ"
        )
        assertEquals(AppCheckProviderMode.RECAPTCHA_PRODUCTION, mode)
    }

    @Test
    fun `app check uses recaptcha in release builds`() {
        val mode = resolveAppCheckMode(
            isDebugBuild = false,
            intentDebugToken = "ignored-in-release",
            hasVerifiedPersistedToken = true,
            isEmulator = false,
            recaptchaSiteKey = "site-key"
        )
        assertEquals(AppCheckProviderMode.RECAPTCHA_PRODUCTION, mode)
    }

    // -------------------------------------------------------------------------
    // 1. Simple Question + 2. Multi-turn Context Retention
    // -------------------------------------------------------------------------

    @Test
    fun `simple question streams response clears thinking indicator and retains follow-up context`() = runTest {
        val recordedHistories = mutableListOf<List<AiChatMessage>>()
        val backend = FakeAiAgentBackend { prompt, _, history, _, _ ->
            recordedHistories += history
            flow {
                emit(AiTurnEvent.TextDelta("Digestive enzymes break down food. "))
                emit(AiTurnEvent.TextDelta("Digestive enzymes break down food. Reply to: $prompt"))
                emit(
                    AiTurnEvent.Completed(
                        finalText = "Digestive enzymes break down food. Reply to: $prompt",
                        proposals = emptyList(),
                        actionLogs = emptyList()
                    )
                )
            }
        }

        val snapshot = createTestSnapshot()
        val ctx = createDigestiveContext(snapshot)
        val vm = AiAssistantViewModel(NotebookAiRepository(backend))
        vm.bindToContext(ctx)

        // Turn 1: Simple question
        vm.sendMessage("Explain the Human Digestive System simply.", "Explain This", ctx, { snapshot })
        advanceUntilIdle()

        val afterFirst = vm.agentState.value
        assertFalse("Thinking indicator must stop after completion", afterFirst.isBusy)
        assertNull("Status message must clear", afterFirst.statusMessage)
        assertNull("No error expected", afterFirst.errorMessage)
        assertEquals(2, afterFirst.activeSession.messages.size)
        assertEquals(AiChatRole.USER, afterFirst.activeSession.messages[0].role)
        assertEquals(AiChatRole.ASSISTANT, afterFirst.activeSession.messages[1].role)
        assertTrue(afterFirst.activeSession.messages[1].text.contains("Digestive enzymes"))

        // Turn 2: Follow-up question retains context
        vm.sendMessage("Now give me 3 revision questions on this.", "Create Questions", ctx, { snapshot })
        advanceUntilIdle()

        val afterSecond = vm.agentState.value
        assertFalse(afterSecond.isBusy)
        assertEquals(4, afterSecond.activeSession.messages.size)
        assertEquals("Follow-up turn must receive previous 2 messages in history", 2, recordedHistories[1].size)
        assertEquals("Explain the Human Digestive System simply.", recordedHistories[1][0].text)
    }

    // -------------------------------------------------------------------------
    // 3. Structured Study Notes + 4. Append Without Overwriting + Undo
    // -------------------------------------------------------------------------

    @Test
    fun `append proposal preserves existing page content and undo restores original content`() = runTest {
        val pages = createTestSnapshot().pages.toMutableList()
        val page0 = pages.first()
        val originalContent = page0.content
        var lastSavedContent: String? = null

        val bridge = FirestoreNotebookActionBridge(
            userId = "test-user",
            createPageOp = { Result.success("created_id") },
            updatePageOp = { pageId, fields ->
                val idx = pages.indexOfFirst { it.id == pageId }
                if (idx >= 0) {
                    val updatedText = fields["content"] as? String ?: pages[idx].content
                    lastSavedContent = updatedText
                    pages[idx] = pages[idx].copy(content = updatedText)
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalArgumentException("Page missing"))
                }
            },
            deletePageOp = { Result.success(Unit) },
            createTopicOp = { _, _ -> Result.success("t_new") },
            createSubtopicOp = { Result.success("s_new") }
        )

        val generatedNotes = """
            ## Existing Notes Summary
            - Covered mouth & stomach basics.
            
            ## Missing Concepts & Newly Generated Study Notes
            - Small Intestine (Villi & Microvilli)
            - Liver (Bile) & Pancreas (Lipase, Amylase, Trypsin)
        """.trimIndent()

        val backend = FakeAiAgentBackend { _, _, _, context, executor ->
            flow {
                val execResult = executor!!.execute(
                    call = ToolInvocation(
                        id = "call_1",
                        name = "propose_append_to_page",
                        args = mapOf(
                            "page_id" to JsonPrimitive(page0.id),
                            "content_to_append" to JsonPrimitive(generatedNotes),
                            "summary" to JsonPrimitive("Append structured Digestive System notes to ${page0.title}")
                        )
                    ),
                    context = context
                )
                emit(
                    AiTurnEvent.Completed(
                        finalText = "Here are your structured notes. Preview and confirm to append safely.",
                        proposals = listOfNotNull(execResult.proposal),
                        actionLogs = emptyList()
                    )
                )
            }
        }

        val snapshotProvider = { createTestSnapshot(pages = pages.toList()) }
        val ctx = createDigestiveContext(snapshotProvider())
        val vm = AiAssistantViewModel(NotebookAiRepository(backend))
        vm.bindToContext(ctx)

        vm.sendMessage(
            "Make professional study notes for Human Digestive System and append to my page.",
            "Create Notes",
            ctx,
            snapshotProvider
        )
        advanceUntilIdle()

        val assistantMsg = vm.agentState.value.activeSession.messages.last()
        assertEquals(1, assistantMsg.proposals.size)
        val proposal = assistantMsg.proposals.first()
        assertTrue(proposal is AiProposedAction.AppendToPage)
        assertFalse("Append should not require destructive confirmation", proposal.requiresExplicitConfirmation)

        // Confirm proposal -> executes real bridge mutation
        vm.executeProposal(assistantMsg.id, proposal, bridge, snapshotProvider())
        advanceUntilIdle()

        assertNotNull(lastSavedContent)
        assertTrue("Original user notes MUST be preserved", lastSavedContent!!.contains(originalContent))
        assertTrue("Generated notes must be appended", lastSavedContent!!.contains("Small Intestine"))
        assertEquals(ProposalStatus.APPLIED, vm.agentState.value.activeSession.messages.last().proposals.first().status)

        // Undo -> restores exact original content
        val undoSnap = vm.agentState.value.lastUndoSnapshot
        assertNotNull(undoSnap)
        vm.undoLastAction(undoSnap!!, bridge)
        advanceUntilIdle()

        assertEquals(originalContent, pages.first().content)
    }

    // -------------------------------------------------------------------------
    // 5. Create Page + Learning Progress (NEWLY_GENERATED, never ALREADY_STUDIED) + Undo
    // -------------------------------------------------------------------------

    @Test
    fun `create page action marks topic as NEWLY_GENERATED not ALREADY_STUDIED and supports undo`() = runTest {
        val pages = createTestSnapshot().pages.toMutableList()
        var deletedId: String? = null

        val bridge = FirestoreNotebookActionBridge(
            userId = "test-user",
            createPageOp = { page ->
                val createdId = "page_rev_01"
                pages.add(page.copy(id = createdId))
                Result.success(createdId)
            },
            updatePageOp = { _, _ -> Result.success(Unit) },
            deletePageOp = { id ->
                deletedId = id
                pages.removeAll { it.id == id }
                Result.success(Unit)
            },
            createTopicOp = { _, _ -> Result.success("t_new") },
            createSubtopicOp = { Result.success("s_new") }
        )

        val backend = FakeAiAgentBackend { _, _, _, context, executor ->
            flow {
                val res = executor!!.execute(
                    call = ToolInvocation(
                        id = "call_create_1",
                        name = "propose_create_page",
                        args = mapOf(
                            "title" to JsonPrimitive("Digestive System Revision Notes"),
                            "content" to JsonPrimitive("## Quick Revision\n1. Mouth -> Esophagus -> Stomach -> Intestines"),
                            "is_revision_page" to JsonPrimitive(true)
                        )
                    ),
                    context = context
                )
                emit(
                    AiTurnEvent.Completed(
                        finalText = "Prepared a new revision page for you. Tap Create Page to save it.",
                        proposals = listOfNotNull(res.proposal),
                        actionLogs = emptyList()
                    )
                )
            }
        }

        val snapshot = createTestSnapshot(pages = pages.toList())
        val ctx = createDigestiveContext(snapshot)
        val vm = AiAssistantViewModel(NotebookAiRepository(backend))
        vm.bindToContext(ctx)

        vm.sendMessage("Create a new page titled Digestive System Revision Notes.", "Revise", ctx, { snapshot })
        advanceUntilIdle()

        val msg = vm.agentState.value.activeSession.messages.last()
        val proposal = msg.proposals.first() as AiProposedAction.CreatePage
        assertEquals("Digestive System Revision Notes", proposal.title)
        assertTrue(proposal.isRevisionPage)

        vm.executeProposal(msg.id, proposal, bridge, snapshot)
        advanceUntilIdle()

        assertTrue(pages.any { it.id == "page_rev_01" && it.title == "Digestive System Revision Notes" })
        val progress = vm.agentState.value.learningProgress
        assertTrue(
            "AI-generated page must be marked NEWLY_GENERATED, never ALREADY_STUDIED",
            progress.any {
                it.conceptName == "Digestive System Revision Notes" &&
                    it.status == LearningProgressStatus.NEWLY_GENERATED
            }
        )
        assertFalse(progress.any { it.status == LearningProgressStatus.ALREADY_STUDIED })

        // Undo deletes the created page
        val undoSnap = vm.agentState.value.lastUndoSnapshot as AiUndoSnapshot.DeleteCreatedPage
        vm.undoLastAction(undoSnap, bridge)
        advanceUntilIdle()
        assertEquals("page_rev_01", deletedId)
        assertFalse(pages.any { it.id == "page_rev_01" })
    }

    // -------------------------------------------------------------------------
    // 6. Create Topic & Create Subtopic
    // -------------------------------------------------------------------------

    @Test
    fun `create topic and subtopic proposals execute real bridge operations`() = runTest {
        val createdTopics = mutableListOf<TopicEntity>()
        val createdSubtopics = mutableListOf<SubtopicEntity>()

        val bridge = FirestoreNotebookActionBridge(
            userId = "test-user",
            createPageOp = { Result.success("p1") },
            updatePageOp = { _, _ -> Result.success(Unit) },
            deletePageOp = { Result.success(Unit) },
            createTopicOp = { topic, _ ->
                createdTopics.add(topic)
                Result.success("topic_created_1")
            },
            createSubtopicOp = { sub ->
                createdSubtopics.add(sub)
                Result.success("sub_created_1")
            }
        )

        val snapshot = createTestSnapshot()
        val ctx = createDigestiveContext(snapshot)
        val executor = NotebookToolExecutor({ snapshot }, { emptyList() }) { nbId, tId, sId, concept, status, notes ->
            Result.success(LearningProgressRecord(notebookId = nbId, topicId = tId, subtopicId = sId, conceptName = concept, status = status, notes = notes))
        }

        val topicExec = executor.execute(
            call = ToolInvocation(
                id = "call_topic",
                name = "propose_create_topic",
                args = mapOf(
                    "name" to JsonPrimitive("Liver & Bile Metabolism"),
                    "short_description" to JsonPrimitive("Role of the liver in digestion and detoxification"),
                    "purpose" to JsonPrimitive("Understand bile production and nutrient processing")
                )
            ),
            context = ctx
        )
        val subExec = executor.execute(
            call = ToolInvocation(
                id = "call_subtopic",
                name = "propose_create_subtopic",
                args = mapOf(
                    "name" to JsonPrimitive("Enzymatic Breakdown in Duodenum"),
                    "short_description" to JsonPrimitive("Pancreatic enzymes and bile emulsification")
                )
            ),
            context = ctx
        )

        val topicResult = bridge.createTopic(topicExec.proposal as AiProposedAction.CreateTopic)
        val subResult = bridge.createSubtopic(subExec.proposal as AiProposedAction.CreateSubtopic)

        assertTrue(topicResult.succeeded)
        assertTrue(subResult.succeeded)
        assertEquals("Liver & Bile Metabolism", createdTopics.single().name)
        assertEquals("Enzymatic Breakdown in Duodenum", createdSubtopics.single().name)
    }

    // -------------------------------------------------------------------------
    // 7. Hindi, English, and Hinglish + 8. Autonomous Read/Progress Tools
    // -------------------------------------------------------------------------

    @Test
    fun `hindi hinglish and english queries work with read tools and progress tracking`() = runTest {
        val snapshot = createTestSnapshot()
        val ctx = createDigestiveContext(snapshot)

        val backend = FakeAiAgentBackend { prompt, _, _, context, executor ->
            flow {
                val searchRes = executor!!.execute(
                    call = ToolInvocation(
                        id = "call_search",
                        name = "search_notebook_content",
                        args = mapOf("query" to JsonPrimitive("Stomach"))
                    ),
                    context = context
                )
                assertTrue(searchRes.responseJson.toString().contains("Stomach"))

                val readRes = executor.execute(
                    call = ToolInvocation(
                        id = "call_read",
                        name = "read_notebook_page",
                        args = mapOf("page_id" to JsonPrimitive("page_dig_1"))
                    ),
                    context = context
                )
                assertTrue(readRes.responseJson.toString().contains("HCl"))

                executor.execute(
                    call = ToolInvocation(
                        id = "call_prog",
                        name = "update_learning_progress",
                        args = mapOf(
                            "concept_name" to JsonPrimitive("Stomach Digestion"),
                            "status" to JsonPrimitive("ALREADY_STUDIED"),
                            "notes" to JsonPrimitive("User confirmed they studied HCl and pepsin.")
                        )
                    ),
                    context = context
                )

                val reply = "बिल्कुल! आपने Stomach (HCl और Pepsin) अच्छे से पढ़ लिया है। Ab next Small Intestine aur absorption padhte hain. ($prompt)"
                emit(AiTurnEvent.TextDelta(reply))
                emit(
                    AiTurnEvent.Completed(
                        finalText = reply,
                        proposals = emptyList(),
                        actionLogs = listOf(AiActionLogEntry(label = "Updated progress: Stomach Digestion", succeeded = true))
                    )
                )
            }
        }

        val vm = AiAssistantViewModel(NotebookAiRepository(backend))
        vm.bindToContext(ctx)

        vm.sendMessage(
            "मैंने stomach और pepsin पढ़ लिया है, ab aage kya padhna chahiye?",
            "Ask AI",
            ctx,
            { snapshot }
        )
        advanceUntilIdle()

        val state = vm.agentState.value
        assertFalse(state.isBusy)
        assertTrue(state.activeSession.messages.last().text.contains("बिल्कुल!"))
        assertTrue(
            state.learningProgress.any {
                it.conceptName == "Stomach Digestion" && it.status == LearningProgressStatus.ALREADY_STUDIED
            }
        )
    }

    // -------------------------------------------------------------------------
    // 9. Long Streaming Response + 10. Error Handling, Duplicate Guard & Retry
    // -------------------------------------------------------------------------

    @Test
    fun `error classification handles network timeout rate limit and app check clearly`() {
        assertTrue(
            NotebookAiRepository.classifyError(UnknownHostException("host")).contains("No internet", ignoreCase = true)
        )
        assertTrue(
            NotebookAiRepository.classifyError(SocketTimeoutException("timeout")).contains("timed out", ignoreCase = true)
        )
        assertTrue(
            NotebookAiRepository.classifyError(RuntimeException("HTTP 429 RESOURCE_EXHAUSTED")).contains("rate limit", ignoreCase = true)
        )
        assertTrue(
            NotebookAiRepository.classifyError(RuntimeException("HTTP 403 Firebase App Check token is invalid")).contains("App Check", ignoreCase = true)
        )
    }

    @Test
    fun `failure clears thinking indicator prevents duplicate requests and retry recovers cleanly`() = runTest {
        var attempt = 0
        val backend = FakeAiAgentBackend { _, _, _, _, _ ->
            attempt++
            flow {
                delay(50)
                if (attempt == 1) {
                    throw IOException("Network dropped mid-request")
                } else {
                    val longBody = (1..20).joinToString("\n") { i -> "Section $i: Detailed clinical note on GI tract motility." }
                    emit(AiTurnEvent.TextDelta(longBody.take(120)))
                    emit(AiTurnEvent.TextDelta(longBody))
                    emit(AiTurnEvent.Completed(longBody, emptyList(), emptyList()))
                }
            }
        }

        val snapshot = createTestSnapshot()
        val ctx = createDigestiveContext(snapshot)
        val vm = AiAssistantViewModel(NotebookAiRepository(backend))
        vm.bindToContext(ctx)

        // Fire message + immediate duplicate tap while busy -> only 1 backend call should execute!
        vm.sendMessage("Give me full long notes on GI motility.", "Create Notes", ctx, { snapshot })
        vm.sendMessage("Duplicate tap while busy", "Create Notes", ctx, { snapshot })
        advanceUntilIdle()

        val failedState = vm.agentState.value
        assertEquals("Duplicate submission must be ignored while busy", 1, attempt)
        assertFalse("Thinking indicator MUST clear on failure", failedState.isBusy)
        assertNull(failedState.statusMessage)
        assertNotNull("Clear error message must be shown", failedState.errorMessage)
        assertEquals("Give me full long notes on GI motility.", failedState.lastFailedPrompt)

        // Retry recovers cleanly with full long response
        vm.retryLastFailed(ctx, { snapshot })
        advanceUntilIdle()

        val recoveredState = vm.agentState.value
        assertEquals(2, attempt)
        assertFalse(recoveredState.isBusy)
        assertNull(recoveredState.errorMessage)
        assertNull(recoveredState.lastFailedPrompt)
        assertEquals(
            "Retry must not duplicate the user prompt message",
            2,
            recoveredState.activeSession.messages.size
        )
        assertTrue(recoveredState.activeSession.messages.last().text.contains("Section 20"))
    }

    // -------------------------------------------------------------------------
    // 11. Durable Persistence Across Restart & New Chat Preserving History
    // -------------------------------------------------------------------------

    @Test
    fun `new chat preserves previous conversation and storage persists sessions and progress`() = runTest {
        val uid = "user_${UUID.randomUUID().toString().take(8)}"
        val storage = AiAgentStorage(appContext, uid)
        val backend = FakeAiAgentBackend { prompt, _, _, _, _ ->
            flow {
                emit(AiTurnEvent.Completed("Answer to $prompt", emptyList(), emptyList()))
            }
        }

        val snapshot = createTestSnapshot()
        val ctx = createDigestiveContext(snapshot)
        val vm1 = AiAssistantViewModel(NotebookAiRepository(backend), storage)
        advanceUntilIdle()
        vm1.bindToContext(ctx)

        vm1.sendMessage("First chat about nutrition", "Ask AI", ctx, { snapshot })
        advanceUntilIdle()
        val firstSessionId = vm1.agentState.value.activeSession.id

        // Start a New Chat -> old conversation MUST remain in savedSessions!
        vm1.startNewConversation(ctx)
        advanceUntilIdle()
        assertTrue(vm1.agentState.value.activeSession.messages.isEmpty())
        assertTrue(vm1.agentState.value.savedSessions.any { it.id == firstSessionId && it.messages.size == 2 })

        // Update progress & verify durable reload in a brand-new ViewModel instance
        vm1.updateLearningProgressManually(
            notebookId = "nb_01",
            topicId = "topic_dig",
            subtopicId = null,
            conceptName = "Gut Microbiome",
            status = LearningProgressStatus.NEEDS_REVISION,
            notes = "Revise prebiotics vs probiotics"
        )
        advanceUntilIdle()

        // Allow IO write to flush by loading directly via storage
        val reloadedSessions = storage.loadSessions()
        val reloadedProgress = storage.loadLearningProgress()
        assertTrue(reloadedSessions.any { it.id == firstSessionId })
        assertTrue(
            reloadedProgress.any {
                it.conceptName == "Gut Microbiome" && it.status == LearningProgressStatus.NEEDS_REVISION
            }
        )
    }

    // -------------------------------------------------------------------------
    // 12. Destructive Action Safety, Truthful Failure Reporting, & Move Undo
    // -------------------------------------------------------------------------

    @Test
    fun `destructive organize and move actions enforce confirmation report real failures truthfully and support undo`() = runTest {
        val pages = createTestSnapshot().pages.toMutableList()
        var failNextWrite = true

        val bridge = FirestoreNotebookActionBridge(
            userId = "test-user",
            createPageOp = { Result.success("p_new") },
            updatePageOp = { pageId, fields ->
                if (failNextWrite) {
                    Result.failure(RuntimeException("Firestore permission denied"))
                } else {
                    val idx = pages.indexOfFirst { it.id == pageId }
                    pages[idx] = pages[idx].copy(content = fields["content"] as String)
                    Result.success(Unit)
                }
            },
            deletePageOp = { Result.success(Unit) },
            createTopicOp = { _, _ -> Result.success("t") },
            createSubtopicOp = { Result.success("s") }
        )

        val snapshotProvider = { createTestSnapshot(pages = pages.toList()) }
        val ctx = createDigestiveContext(snapshotProvider())
        val executor = NotebookToolExecutor(snapshotProvider, { emptyList() }) { nbId, tId, sId, concept, status, notes ->
            Result.success(LearningProgressRecord(notebookId = nbId, topicId = tId, subtopicId = sId, conceptName = concept, status = status, notes = notes))
        }

        val orgTool = executor.execute(
            call = ToolInvocation(
                id = "call_org",
                name = "propose_organize_page",
                args = mapOf(
                    "page_id" to JsonPrimitive("page_dig_1"),
                    "organized_content" to JsonPrimitive("# Clean Organized Notes\n- Mouth\n- Stomach (HCl + Pepsin)")
                )
            ),
            context = ctx
        )
        val orgProposal = orgTool.proposal as AiProposedAction.OrganizePage
        assertTrue("Organize/overwrite MUST require explicit confirmation", orgProposal.requiresExplicitConfirmation)

        val backend = FakeAiAgentBackend { _, _, _, _, _ ->
            flow {
                emit(AiTurnEvent.Completed("Ready to organize your page.", listOf(orgProposal), emptyList()))
            }
        }
        val vm = AiAssistantViewModel(NotebookAiRepository(backend))
        vm.bindToContext(ctx)
        vm.sendMessage("Organize this page cleanly.", "Organize Notes", ctx, snapshotProvider)
        advanceUntilIdle()

        val msgId = vm.agentState.value.activeSession.messages.last().id

        // 1) When Firestore fails, ViewModel MUST report FAILED and NEVER pretend success
        vm.executeProposal(msgId, orgProposal, bridge, snapshotProvider())
        advanceUntilIdle()
        assertEquals(ProposalStatus.FAILED, vm.agentState.value.activeSession.messages.last().proposals.first().status)
        assertTrue(vm.agentState.value.activeSession.messages.last().actionLogs.last().detail.contains("permission denied"))

        // 2) Now succeed & test MoveContent + Undo restoring BOTH pages
        failNextWrite = false
        val moveTool = executor.execute(
            call = ToolInvocation(
                id = "call_move",
                name = "propose_move_content",
                args = mapOf(
                    "source_page_id" to JsonPrimitive("page_dig_1"),
                    "target_page_id" to JsonPrimitive("page_dig_2"),
                    "content_to_move" to JsonPrimitive("Stomach secretes HCl and pepsin.")
                )
            ),
            context = ctx
        )
        val moveProposal = moveTool.proposal as AiProposedAction.MoveContent
        assertTrue(moveProposal.requiresExplicitConfirmation)

        val origSrc = pages[0].content
        val origDst = pages[1].content
        val moveRes = bridge.moveContent(moveProposal, snapshotProvider())
        assertTrue(moveRes.succeeded)
        assertFalse(pages[0].content.contains("Stomach secretes HCl and pepsin."))
        assertTrue(pages[1].content.contains("Stomach secretes HCl and pepsin."))

        val undoRes = bridge.undo(moveRes.undoSnapshot!!)
        assertTrue(undoRes.succeeded)
        assertEquals(origSrc, pages[0].content)
        assertEquals(origDst, pages[1].content)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private class FakeAiAgentBackend(
        private val handler: (String, String, List<AiChatMessage>, AiContext, NotebookToolExecutor?) -> Flow<AiTurnEvent>
    ) : AiAgentBackend {
        override fun runAgentTurn(
            userRequest: String,
            actionLabel: String,
            history: List<AiChatMessage>,
            context: AiContext,
            toolExecutor: NotebookToolExecutor?
        ): Flow<AiTurnEvent> = handler(userRequest, actionLabel, history, context, toolExecutor)

        override suspend fun generateStructuredTopicInfo(
            notebookName: String,
            notebookPurpose: String,
            topicName: String,
            languagePref: String
        ): GeneratedTopicInfo = GeneratedTopicInfo(
            shortDescription = "Short desc",
            purpose = "Purpose",
            introduction = "Intro",
            coversItems = listOf("Basics"),
            importantConcepts = listOf("Core"),
            subtopics = listOf("Part 1"),
            relatedTopics = listOf("Nutrition")
        )
    }

    private fun createTestSnapshot(
        pages: List<PageEntity> = listOf(
            PageEntity(
                id = "page_dig_1",
                notebookId = "nb_01",
                topicId = "topic_dig",
                title = "Page 1 - Basics",
                content = "Mouth starts mechanical digestion. Stomach secretes HCl and pepsin.",
                pageNumber = 1
            ),
            PageEntity(
                id = "page_dig_2",
                notebookId = "nb_01",
                topicId = "topic_dig",
                title = "Page 2 - Enzymes",
                content = "Enzyme summary notes:",
                pageNumber = 2
            )
        )
    ): LibrarySnapshot {
        val notebooks = SeedData.defaultNotebooks()
        val topic = TopicEntity(
            id = "topic_dig",
            notebookId = "nb_01",
            name = "Human Digestive System",
            shortDescription = "How the GI tract digests and absorbs nutrients",
            purpose = "Master human digestive anatomy and physiology"
        )
        return LibrarySnapshot(
            notebooks = notebooks,
            topics = listOf(topic),
            subtopics = emptyList(),
            pages = pages
        )
    }

    private fun createDigestiveContext(snapshot: LibrarySnapshot): AiContext {
        val nb = snapshot.notebooks.first { it.id == "nb_01" }
        val topic = snapshot.topics.first { it.id == "topic_dig" }
        val page = snapshot.pages.first()
        return AiContext(
            notebook = nb,
            topic = topic,
            currentPage = page,
            currentPageText = page.content,
            relatedPages = snapshot.pages.drop(1),
            notebookTopics = snapshot.topics
        )
    }
}
