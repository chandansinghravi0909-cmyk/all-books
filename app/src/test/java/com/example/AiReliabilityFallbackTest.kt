package com.example

import com.example.data.ai.AiChatMessage
import com.example.data.ai.AiChatRole
import com.example.data.ai.AiContext
import com.example.data.ai.AiErrorClassifier
import com.example.data.ai.AiErrorKind
import com.example.data.ai.AiModelConfig
import com.example.data.ai.AiTurnEvent
import com.example.data.ai.ClassifiedAiException
import com.example.data.ai.FirebaseAiAgentBackend
import com.example.data.ai.LearningProgressRecord
import com.example.data.ai.ModelCallTransport
import com.example.data.ai.ModelChatSession
import com.example.data.ai.ModelHealthTracker
import com.example.data.ai.ModelStreamChunk
import com.example.data.ai.NetworkConnectivityChecker
import com.example.data.ai.NotebookAiRepository
import com.example.data.ai.NotebookToolExecutor
import com.example.data.ai.ToolInvocation
import com.example.data.model.PageEntity
import com.example.data.model.SeedData
import com.example.data.model.TopicEntity
import com.example.ui.viewmodel.AiAssistantViewModel
import com.example.ui.viewmodel.LibrarySnapshot
import com.google.firebase.ai.type.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
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
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiReliabilityFallbackTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fastTestConfig = AiModelConfig(
        primaryModelId = "gemini-3.8-flash",
        secondaryModelId = "gemini-3.5-flash-lite",
        primaryMaxRetries = 1,
        secondaryMaxRetries = 1,
        firstTokenTimeoutMs = 400L,
        interChunkStallTimeoutMs = 400L,
        singleAttemptTimeoutMs = 4_000L,
        totalTurnTimeoutMs = 10_000L,
        baseBackoffMs = 30L,
        maxBackoffMs = 120L,
        primaryOverloadCooldownMs = 60_000L
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // Test 1: Basic Response ("Hello") uses fast-path without heavy tools
    // -------------------------------------------------------------------------

    @Test
    fun `basic Hello message uses fast path and streams clean response from primary model`() = runTest {
        val calls = mutableListOf<Pair<String, Boolean>>()
        val transport = FakeTransport { modelId, includeTools, _ ->
            calls += modelId to includeTools
            FakeSession(
                streamHandler = {
                    flow {
                        emit(ModelStreamChunk(text = "Hello! "))
                        emit(ModelStreamChunk(text = "How can I help with your PERSONALLY GENERAL notebook today?"))
                    }
                }
            )
        }

        val backend = FirebaseAiAgentBackend(config = fastTestConfig, transport = transport)
        val snapshot = testSnapshot()
        val ctx = testContext(snapshot)
        val events = backend.runAgentTurn(
            userRequest = "Hello",
            actionLabel = "Ask AI",
            history = emptyList(),
            context = ctx,
            toolExecutor = testToolExecutor(snapshot)
        ).toList()

        assertEquals(1, calls.size)
        assertEquals("gemini-3.8-flash", calls[0].first)
        assertFalse("Simple greeting should skip 11 heavy tool schemas for low latency", calls[0].second)

        val completed = events.filterIsInstance<AiTurnEvent.Completed>().single()
        assertEquals("gemini-3.8-flash", completed.modelUsed)
        assertFalse(completed.usedFallbackModel)
        assertTrue(completed.finalText.contains("PERSONALLY GENERAL"))
    }

    // -------------------------------------------------------------------------
    // Test 2: Notebook Context ("notebook mein kya-kya add ho gaya hai?")
    // -------------------------------------------------------------------------

    @Test
    fun `hinglish notebook query attaches tools and reads notebook context`() = runTest {
        var receivedTools = false
        var sawFunctionResponseRound = false

        val transport = FakeTransport { _, includeTools, _ ->
            receivedTools = includeTools
            var round = 0
            FakeSession(
                streamHandler = {
                    round++
                    flow {
                        if (round == 1) {
                            emit(
                                ModelStreamChunk(
                                    functionCalls = listOf(
                                        ToolInvocation(
                                            id = "fc_1",
                                            name = "search_notebook_content",
                                            args = mapOf("query" to JsonPrimitive("Digestive"))
                                        )
                                    )
                                )
                            )
                        } else {
                            sawFunctionResponseRound = true
                            emit(
                                ModelStreamChunk(
                                    text = "Aapki PERSONALLY GENERAL notebook mein abhi Human Digestive System topic aur Page 1 (Basics) add ho chuke hain."
                                )
                            )
                        }
                    }
                }
            )
        }

        val backend = FirebaseAiAgentBackend(config = fastTestConfig, transport = transport)
        val snapshot = testSnapshot()
        val ctx = testContext(snapshot)
        val events = backend.runAgentTurn(
            userRequest = "notebook mein kya-kya add ho gaya hai?",
            actionLabel = "Ask AI",
            history = emptyList(),
            context = ctx,
            toolExecutor = testToolExecutor(snapshot)
        ).toList()

        assertTrue("Notebook query MUST attach tools", receivedTools)
        assertTrue("Tool response must feed back into second model round", sawFunctionResponseRound)
        val completed = events.filterIsInstance<AiTurnEvent.Completed>().single()
        assertTrue(completed.finalText.contains("Human Digestive System"))
    }

    // -------------------------------------------------------------------------
    // Test 4: Primary HTTP 503 Overload -> Controlled Retry -> Automatic Fallback
    //         to gemini-3.5-flash-lite + Primary Cooldown on Subsequent Turn
    // -------------------------------------------------------------------------

    @Test
    fun `primary model 503 high demand retries once then falls back to gemini-3_5-flash-lite and cools down primary`() = runTest {
        var nowMs = 1_000_000L
        val healthTracker = ModelHealthTracker(clockMs = { nowMs })
        val attemptedModels = mutableListOf<String>()

        val transport = FakeTransport { modelId, _, _ ->
            attemptedModels += modelId
            FakeSession(
                streamHandler = {
                    flow {
                        if (modelId == "gemini-3.8-flash") {
                            throw RuntimeException(
                                "HTTP 503: This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later."
                            )
                        } else {
                            emit(ModelStreamChunk(text = "## Human Digestive System\n1. Mouth\n2. Esophagus\n3. Stomach\n4. Small Intestine"))
                        }
                    }
                }
            )
        }

        val backend = FirebaseAiAgentBackend(
            config = fastTestConfig,
            transport = transport,
            healthTracker = healthTracker
        )
        val snapshot = testSnapshot()
        val ctx = testContext(snapshot)

        // Turn 1: Primary fails twice (initial + 1 controlled retry) -> automatically switches to secondary!
        val events1 = backend.runAgentTurn(
            userRequest = "Create detailed notes on the human digestive system.",
            actionLabel = "Create Notes",
            history = emptyList(),
            context = ctx,
            toolExecutor = testToolExecutor(snapshot)
        ).toList()

        assertEquals(
            listOf("gemini-3.8-flash", "gemini-3.8-flash", "gemini-3.5-flash-lite"),
            attemptedModels
        )
        assertTrue(
            "UI status should announce switching to fallback model",
            events1.filterIsInstance<AiTurnEvent.ToolStatus>().any { it.statusLabel.contains("gemini-3.5-flash-lite") }
        )
        val completed1 = events1.filterIsInstance<AiTurnEvent.Completed>().single()
        assertEquals("gemini-3.5-flash-lite", completed1.modelUsed)
        assertTrue(completed1.usedFallbackModel)
        assertTrue(completed1.finalText.contains("Human Digestive System"))

        // Turn 2 (10 seconds later, still inside 60s cooldown): routes IMMEDIATELY to secondary without wasting time on overloaded primary!
        attemptedModels.clear()
        nowMs += 10_000L
        val events2 = backend.runAgentTurn(
            userRequest = "Summarize stomach enzymes.",
            actionLabel = "Summarize",
            history = listOf(
                AiChatMessage(role = AiChatRole.USER, text = "Create detailed notes on the human digestive system."),
                AiChatMessage(role = AiChatRole.ASSISTANT, text = completed1.finalText)
            ),
            context = ctx,
            toolExecutor = testToolExecutor(snapshot)
        ).toList()

        assertEquals(listOf("gemini-3.5-flash-lite"), attemptedModels)
        val completed2 = events2.filterIsInstance<AiTurnEvent.Completed>().single()
        assertTrue(completed2.usedFallbackModel)

        // Turn 3 (after cooldown expires): probes primary again
        attemptedModels.clear()
        nowMs += 65_000L
        assertEquals(
            listOf("gemini-3.8-flash", "gemini-3.5-flash-lite"),
            backend.resolveCandidateModels()
        )
    }

    // -------------------------------------------------------------------------
    // Test 5: Real Coroutine Stream-Stall Watchdog Interrupts Hung Primary Stream
    // -------------------------------------------------------------------------

    @Test
    fun `stalled primary stream with zero chunks is interrupted by watchdog and falls back to secondary`() = runTest {
        val attemptedModels = mutableListOf<String>()
        val transport = FakeTransport { modelId, _, _ ->
            attemptedModels += modelId
            FakeSession(
                streamHandler = {
                    flow {
                        if (modelId == "gemini-3.8-flash") {
                            // Simulates hung TCP/SSE connection that never emits a single chunk
                            delay(60_000L)
                        } else {
                            emit(ModelStreamChunk(text = "Recovered promptly via gemini-3.5-flash-lite."))
                        }
                    }
                }
            )
        }

        val backend = FirebaseAiAgentBackend(config = fastTestConfig, transport = transport)
        val snapshot = testSnapshot()
        val ctx = testContext(snapshot)
        val events = backend.runAgentTurn(
            userRequest = "Explain gut microbiome",
            actionLabel = "Explain This",
            history = emptyList(),
            context = ctx,
            toolExecutor = testToolExecutor(snapshot)
        ).toList()

        assertEquals(listOf("gemini-3.8-flash", "gemini-3.5-flash-lite"), attemptedModels)
        val completed = events.filterIsInstance<AiTurnEvent.Completed>().single()
        assertEquals("gemini-3.5-flash-lite", completed.modelUsed)
        assertTrue(completed.usedFallbackModel)
    }

    // -------------------------------------------------------------------------
    // Test 6: All 10 Error Classes & Non-Fallback Guards (403, 401, Quota, 400, Offline)
    // -------------------------------------------------------------------------

    @Test
    fun `error classifier accurately distinguishes all 10 failure categories and Retry-After`() {
        assertEquals(
            AiErrorKind.HTTP_503_OVERLOAD,
            AiErrorClassifier.classify(RuntimeException("This model is currently experiencing high demand.")).kind
        )
        val rateLimit = AiErrorClassifier.classify(RuntimeException("HTTP 429 Too Many Requests. Retry-After: 3s"))
        assertEquals(AiErrorKind.HTTP_429_RATE_LIMIT, rateLimit.kind)
        assertEquals(3000L, rateLimit.retryAfterMs)
        assertTrue(rateLimit.allowsModelFallback)

        val quota = AiErrorClassifier.classify(RuntimeException("429 You exceeded your current quota, please check billing"))
        assertEquals(AiErrorKind.QUOTA_EXHAUSTED, quota.kind)
        assertFalse("Quota exhaustion must NOT retry endlessly", quota.retryableOnSameModel)
        assertFalse("Quota exhaustion must NOT switch models", quota.allowsModelFallback)

        assertEquals(
            AiErrorKind.HTTP_500_SERVER_ERROR,
            AiErrorClassifier.classify(RuntimeException("HTTP 500 Internal Server Error")).kind
        )
        val appCheck = AiErrorClassifier.classify(RuntimeException("HTTP 403 Firebase App Check token is invalid"))
        assertEquals(AiErrorKind.HTTP_403_AUTH_OR_APPCHECK, appCheck.kind)
        assertFalse("403 App Check error must NOT mask failure by switching models", appCheck.allowsModelFallback)

        val auth401 = AiErrorClassifier.classify(RuntimeException("HTTP 401 UNAUTHENTICATED"))
        assertEquals(AiErrorKind.HTTP_401_UNAUTHORIZED, auth401.kind)
        assertFalse(auth401.allowsModelFallback)

        val invalidModel = AiErrorClassifier.classify(RuntimeException("HTTP 404 model not found"))
        assertEquals(AiErrorKind.INVALID_MODEL, invalidModel.kind)
        assertTrue("Invalid primary model SHOULD fall back to secondary", invalidModel.allowsModelFallback)

        val invalidReq = AiErrorClassifier.classify(RuntimeException("HTTP 400 INVALID_ARGUMENT token count exceeds limit"))
        assertEquals(AiErrorKind.INVALID_REQUEST, invalidReq.kind)
        assertFalse(invalidReq.allowsModelFallback)

        assertEquals(
            AiErrorKind.NETWORK_UNAVAILABLE,
            AiErrorClassifier.classify(UnknownHostException("generativelanguage.googleapis.com")).kind
        )
        assertEquals(
            AiErrorKind.TIMEOUT,
            AiErrorClassifier.classify(SocketTimeoutException("read timed out")).kind
        )

        // Exponential backoff with jitter and Retry-After respect
        val retryAfterDelay = AiErrorClassifier.computeBackoffDelayMs(0, fastTestConfig, retryAfterMs = 2_000L)
        assertTrue(retryAfterDelay >= 300L)
    }

    @Test
    fun `app check 403 and quota exhaustion fail immediately without switching models or looping`() = runTest {
        var calls = 0
        val transport403 = FakeTransport { _, _, _ ->
            calls++
            FakeSession(streamHandler = {
                flow { throw RuntimeException("HTTP 403 Firebase App Check token is invalid.") }
            })
        }

        val backend = FirebaseAiAgentBackend(config = fastTestConfig, transport = transport403)
        val snapshot = testSnapshot()
        val ctx = testContext(snapshot)
        var caught: ClassifiedAiException? = null
        try {
            backend.runAgentTurn("Hello", "Ask AI", emptyList(), ctx, null).toList()
        } catch (e: ClassifiedAiException) {
            caught = e
        }
        assertEquals("403 must NOT retry or switch models", 1, calls)
        assertEquals(AiErrorKind.HTTP_403_AUTH_OR_APPCHECK, caught?.classified?.kind)
    }

    @Test
    fun `offline connectivity check fails immediately without invoking Gemini transport`() = runTest {
        var transportCalled = false
        val offlineChecker = object : NetworkConnectivityChecker {
            override fun isNetworkAvailable(): Boolean = false
        }
        val transport = FakeTransport { _, _, _ ->
            transportCalled = true
            FakeSession(streamHandler = { flow { emit(ModelStreamChunk(text = "should not run")) } })
        }

        val backend = FirebaseAiAgentBackend(
            config = fastTestConfig,
            transport = transport,
            connectivityChecker = offlineChecker,
            ioDispatcher = testDispatcher
        )
        val vm = AiAssistantViewModel(NotebookAiRepository(backend))
        val snapshot = testSnapshot()
        val ctx = testContext(snapshot)
        vm.bindToContext(ctx)

        vm.sendMessage("Hello", "Ask AI", ctx, { snapshot })
        advanceUntilIdle()

        assertFalse("Offline check must short-circuit before network call", transportCalled)
        assertFalse(vm.agentState.value.isBusy)
        assertTrue(vm.agentState.value.errorMessage.orEmpty().contains("No internet", ignoreCase = true))
        assertEquals("Hello", vm.agentState.value.lastFailedPrompt)
    }

    // -------------------------------------------------------------------------
    // Test 8: Structured Topic Generation Also Falls Back on HTTP 503 Overload
    // -------------------------------------------------------------------------

    @Test
    fun `structured topic info falls back to secondary model when primary is overloaded`() = runTest {
        val modelsTried = mutableListOf<String>()
        val transport = FakeTransport(
            chatFactory = { _, _, _ -> FakeSession(streamHandler = { flow {} }) },
            topicJsonHandler = { modelId, _ ->
                modelsTried += modelId
                if (modelId == "gemini-3.8-flash") {
                    throw RuntimeException("503 Service Unavailable: high demand")
                }
                """
                {
                  "shortDescription": "Study of digestive organs and enzymes",
                  "purpose": "Understand nutrient breakdown and absorption",
                  "introduction": "The human digestive system converts food into absorbable nutrients.",
                  "coversItems": ["Mouth", "Stomach", "Small Intestine"],
                  "importantConcepts": ["Peristalsis", "Enzymatic hydrolysis"],
                  "subtopics": ["Gastric Digestion", "Intestinal Absorption"],
                  "relatedTopics": ["Nutrition", "Gut Microbiome"]
                }
                """.trimIndent()
            }
        )

        val repo = NotebookAiRepository(FirebaseAiAgentBackend(config = fastTestConfig, transport = transport))
        val info = repo.generateStructuredTopicInfo("PERSONALLY GENERAL", "Personal encyclopedia", "Human Digestive System", "MIXED")

        assertEquals(listOf("gemini-3.8-flash", "gemini-3.5-flash-lite"), modelsTried)
        assertEquals("Study of digestive organs and enzymes", info.shortDescription)
        assertTrue(info.subtopics.contains("Gastric Digestion"))
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private class FakeTransport(
        private val topicJsonHandler: suspend (String, String) -> String = { _, _ -> "{}" },
        private val chatFactory: (String, Boolean, List<Content>) -> ModelChatSession
    ) : ModelCallTransport {
        override fun startChatSession(
            modelId: String,
            includeTools: Boolean,
            history: List<Content>
        ): ModelChatSession = chatFactory(modelId, includeTools, history)

        override suspend fun generateStructuredTopicJson(modelId: String, prompt: String): String =
            topicJsonHandler(modelId, prompt)
    }

    private class FakeSession(
        private val streamHandler: (Content) -> Flow<ModelStreamChunk>,
        private val unaryHandler: suspend (Content) -> ModelStreamChunk = { ModelStreamChunk() }
    ) : ModelChatSession {
        override fun sendMessageStream(input: Content): Flow<ModelStreamChunk> = streamHandler(input)
        override suspend fun sendMessageUnary(input: Content): ModelStreamChunk = unaryHandler(input)
    }

    private fun testSnapshot(): LibrarySnapshot {
        val notebooks = SeedData.defaultNotebooks()
        val topic = TopicEntity(
            id = "topic_dig",
            notebookId = "nb_01",
            name = "Human Digestive System",
            shortDescription = "GI tract anatomy and digestion",
            purpose = "Understand digestion and nutrient absorption"
        )
        val page = PageEntity(
            id = "page_1",
            notebookId = "nb_01",
            topicId = "topic_dig",
            title = "Page 1 - Basics",
            content = "Mouth chews food. Stomach uses HCl and pepsin.",
            pageNumber = 1
        )
        return LibrarySnapshot(
            notebooks = notebooks,
            topics = listOf(topic),
            subtopics = emptyList(),
            pages = listOf(page)
        )
    }

    private fun testContext(snapshot: LibrarySnapshot): AiContext {
        val nb = snapshot.notebooks.first { it.id == "nb_01" }
        val topic = snapshot.topics.first()
        val page = snapshot.pages.first()
        return AiContext(
            notebook = nb,
            topic = topic,
            currentPage = page,
            currentPageText = page.content,
            notebookTopics = snapshot.topics
        )
    }

    private fun testToolExecutor(snapshot: LibrarySnapshot): NotebookToolExecutor =
        NotebookToolExecutor({ snapshot }, { emptyList() }) { nbId, tId, sId, concept, status, notes ->
            Result.success(
                LearningProgressRecord(
                    notebookId = nbId,
                    topicId = tId,
                    subtopicId = sId,
                    conceptName = concept,
                    status = status,
                    notes = notes
                )
            )
        }
}
