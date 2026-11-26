package com.example.data.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class GeneratedTopicInfo(
    val shortDescription: String,
    val purpose: String,
    val introduction: String,
    val coversItems: List<String>,
    val importantConcepts: List<String>,
    val subtopics: List<String>,
    val relatedTopics: List<String>
)

sealed interface AiTurnEvent {
    data class TextDelta(val accumulatedText: String) : AiTurnEvent
    data class ToolStatus(val statusLabel: String) : AiTurnEvent
    data class Completed(
        val finalText: String,
        val proposals: List<AiProposedAction>,
        val actionLogs: List<AiActionLogEntry>,
        val modelUsed: String = AiModelConfig.DEFAULT_PRIMARY_MODEL,
        val usedFallbackModel: Boolean = false
    ) : AiTurnEvent
}

class ClassifiedAiException(
    val classified: AiClassifiedError,
    cause: Throwable? = null
) : Exception(classified.userMessage, cause)

data class ModelStreamChunk(
    val text: String = "",
    val functionCalls: List<ToolInvocation> = emptyList()
)

interface ModelChatSession {
    fun sendMessageStream(input: Content): Flow<ModelStreamChunk>
    suspend fun sendMessageUnary(input: Content): ModelStreamChunk
}

interface ModelCallTransport {
    fun startChatSession(
        modelId: String,
        includeTools: Boolean,
        history: List<Content>
    ): ModelChatSession

    suspend fun generateStructuredTopicJson(
        modelId: String,
        prompt: String
    ): String
}

interface AiAgentBackend {
    fun runAgentTurn(
        userRequest: String,
        actionLabel: String,
        history: List<AiChatMessage>,
        context: AiContext,
        toolExecutor: NotebookToolExecutor?
    ): Flow<AiTurnEvent>

    suspend fun generateStructuredTopicInfo(
        notebookName: String,
        notebookPurpose: String,
        topicName: String,
        languagePref: String
    ): GeneratedTopicInfo
}

class NotebookAiRepository(
    private val backend: AiAgentBackend = FirebaseAiAgentBackend()
) {
    constructor(
        connectivityChecker: NetworkConnectivityChecker,
        config: AiModelConfig = AiModelConfig()
    ) : this(
        backend = FirebaseAiAgentBackend(
            config = config,
            connectivityChecker = connectivityChecker
        )
    )

    fun runAgentTurn(
        userRequest: String,
        actionLabel: String,
        history: List<AiChatMessage>,
        context: AiContext,
        toolExecutor: NotebookToolExecutor? = null
    ): Flow<AiTurnEvent> = backend.runAgentTurn(
        userRequest = userRequest,
        actionLabel = actionLabel,
        history = history,
        context = context,
        toolExecutor = toolExecutor
    )

    suspend fun generateStructuredTopicInfo(
        notebookName: String,
        notebookPurpose: String,
        topicName: String,
        languagePref: String
    ): GeneratedTopicInfo = backend.generateStructuredTopicInfo(
        notebookName = notebookName,
        notebookPurpose = notebookPurpose,
        topicName = topicName,
        languagePref = languagePref
    )

    companion object {
        fun classifyError(error: Throwable): String {
            if (error is ClassifiedAiException) {
                return error.classified.userMessage
            }
            return AiErrorClassifier.classify(error).userMessage
        }

        fun classifyDetailed(error: Throwable): AiClassifiedError {
            if (error is ClassifiedAiException) {
                return error.classified
            }
            return AiErrorClassifier.classify(error)
        }
    }
}

/**
 * Default Firebase AI Logic transport lazily constructing GenerativeModel instances per model ID.
 * Never evaluates Firebase.ai eagerly during class construction.
 */
class FirebaseModelCallTransport(
    private val systemInstructionText: String
) : ModelCallTransport {

    private val modelCache = ConcurrentHashMap<String, GenerativeModel>()
    private val structuredModelCache = ConcurrentHashMap<String, GenerativeModel>()

    private val topicSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "shortDescription" to Schema.string(description = "One-line summary of the topic"),
                "purpose" to Schema.string(description = "Why studying or tracking this topic matters"),
                "introduction" to Schema.string(description = "Clear 2-3 sentence introduction"),
                "coversItems" to Schema.array(
                    items = Schema.string(),
                    description = "6 to 10 key areas covered by this topic"
                ),
                "importantConcepts" to Schema.array(
                    items = Schema.string(),
                    description = "4 to 6 essential concepts or principles"
                ),
                "subtopics" to Schema.array(
                    items = Schema.string(),
                    description = "3 to 6 suggested subtopic names"
                ),
                "relatedTopics" to Schema.array(
                    items = Schema.string(),
                    description = "3 to 5 related topic titles"
                )
            )
        )
    }

    private fun getOrCreateChatModel(modelId: String, includeTools: Boolean): GenerativeModel {
        val key = "$modelId|tools=$includeTools"
        return modelCache.getOrPut(key) {
            if (includeTools) {
                Firebase.ai.generativeModel(
                    modelName = modelId,
                    tools = listOf(NotebookAiTools.tool),
                    systemInstruction = content { text(systemInstructionText) }
                )
            } else {
                Firebase.ai.generativeModel(
                    modelName = modelId,
                    systemInstruction = content { text(systemInstructionText) }
                )
            }
        }
    }

    private fun getOrCreateStructuredModel(modelId: String): GenerativeModel {
        return structuredModelCache.getOrPut(modelId) {
            Firebase.ai.generativeModel(
                modelName = modelId,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = topicSchema
                }
            )
        }
    }

    override fun startChatSession(
        modelId: String,
        includeTools: Boolean,
        history: List<Content>
    ): ModelChatSession {
        val chat = getOrCreateChatModel(modelId, includeTools).startChat(history = history)
        return FirebaseModelChatSession(chat)
    }

    override suspend fun generateStructuredTopicJson(modelId: String, prompt: String): String {
        val response = getOrCreateStructuredModel(modelId).generateContent(prompt)
        return response.text ?: throw IllegalStateException("AI returned empty JSON.")
    }

    private class FirebaseModelChatSession(
        private val chat: Chat
    ) : ModelChatSession {
        override fun sendMessageStream(input: Content): Flow<ModelStreamChunk> = flow {
            chat.sendMessageStream(input).collect { chunk ->
                val calls = chunk.functionCalls.map { fc ->
                    ToolInvocation(id = fc.id, name = fc.name, args = fc.args)
                }
                emit(ModelStreamChunk(text = chunk.text.orEmpty(), functionCalls = calls))
            }
        }

        override suspend fun sendMessageUnary(input: Content): ModelStreamChunk {
            val resp = chat.sendMessage(input)
            val calls = resp.functionCalls.map { fc ->
                ToolInvocation(id = fc.id, name = fc.name, args = fc.args)
            }
            return ModelStreamChunk(text = resp.text.orEmpty(), functionCalls = calls)
        }
    }
}

class FirebaseAiAgentBackend(
    private val config: AiModelConfig = AiModelConfig(),
    private val transport: ModelCallTransport = FirebaseModelCallTransport(AGENT_SYSTEM_INSTRUCTION),
    private val healthTracker: ModelHealthTracker = ModelHealthTracker(),
    private val connectivityChecker: NetworkConnectivityChecker = object : NetworkConnectivityChecker {
        override fun isNetworkAvailable(): Boolean = true
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AiAgentBackend {

    override fun runAgentTurn(
        userRequest: String,
        actionLabel: String,
        history: List<AiChatMessage>,
        context: AiContext,
        toolExecutor: NotebookToolExecutor?
    ): Flow<AiTurnEvent> = flow {
        if (!connectivityChecker.isNetworkAvailable()) {
            throw ClassifiedAiException(AiErrorClassifier.classify(NoNetworkConnectionException()))
        }

        val chatHistory = buildChatHistory(history)
        val isSimpleGreeting = isFastPathGreeting(userRequest, actionLabel)
        val includeTools = toolExecutor != null && !isSimpleGreeting
        val promptMessage = buildContextualUserTurn(
            userRequest = userRequest,
            actionLabel = actionLabel,
            ctx = context,
            compactMode = isSimpleGreeting
        )

        val candidateModels = resolveCandidateModels()
        var lastClassified: AiClassifiedError? = null
        var lastThrowable: Throwable? = null
        var completedTurn = false

        withTimeout(config.totalTurnTimeoutMs) {
            for ((modelIndex, modelId) in candidateModels.withIndex()) {
                val isPrimary = modelId == config.primaryModelId
                val maxRetries = if (isPrimary) config.primaryMaxRetries else config.secondaryMaxRetries
                val hasNextModel = modelIndex < candidateModels.lastIndex

                if (modelIndex > 0) {
                    emit(AiTurnEvent.ToolStatus("Primary model busy — switching to $modelId…"))
                } else if (!isPrimary) {
                    emit(AiTurnEvent.ToolStatus("Using fast fallback model ($modelId)…"))
                }

                var shouldTryNextModel = false
                for (attempt in 0..maxRetries) {
                    var streamedAnyText = false
                    try {
                        val result = executeSingleModelTurn(
                            modelId = modelId,
                            includeTools = includeTools,
                            chatHistory = chatHistory,
                            promptMessage = promptMessage,
                            context = context,
                            toolExecutor = toolExecutor,
                            onTextDelta = { accumulated ->
                                streamedAnyText = true
                                emit(AiTurnEvent.TextDelta(accumulated))
                            },
                            onToolStatus = { label ->
                                emit(AiTurnEvent.ToolStatus(label))
                            }
                        )
                        healthTracker.markHealthy(modelId)
                        emit(
                            AiTurnEvent.Completed(
                                finalText = result.finalText,
                                proposals = result.proposals,
                                actionLogs = result.actionLogs,
                                modelUsed = modelId,
                                usedFallbackModel = modelId != config.primaryModelId
                            )
                        )
                        completedTurn = true
                        return@withTimeout
                    } catch (e: CancellationException) {
                        if (e is TimeoutCancellationException) {
                            val classified = AiErrorClassifier.classify(e)
                            lastClassified = classified
                            lastThrowable = e
                            Log.w(TAG, "Model $modelId timed out on attempt ${attempt + 1}: ${e.message}")
                            if (!streamedAnyText && hasNextModel && classified.allowsModelFallback) {
                                shouldTryNextModel = true
                                break
                            }
                            throw ClassifiedAiException(classified, e)
                        }
                        throw e
                    } catch (e: Exception) {
                        val classified = AiErrorClassifier.classify(e)
                        lastClassified = classified
                        lastThrowable = e
                        Log.w(
                            TAG,
                            "Model $modelId failed (attempt ${attempt + 1}/${maxRetries + 1}, kind=${classified.kind}): ${classified.technicalSummary}"
                        )

                        if (classified.triggersPrimaryCooldown && isPrimary) {
                            healthTracker.markOverloaded(modelId, config.primaryOverloadCooldownMs)
                        }

                        // Never retry or switch models if partial response was already rendered to the user
                        if (streamedAnyText) {
                            throw ClassifiedAiException(classified, e)
                        }

                        // Controlled retry with exponential backoff + jitter for transient overload/rate/server errors
                        if (classified.retryableOnSameModel && attempt < maxRetries) {
                            val waitMs = AiErrorClassifier.computeBackoffDelayMs(
                                attemptIndex = attempt,
                                config = config,
                                retryAfterMs = classified.retryAfterMs
                            )
                            val retryMsg = when (classified.kind) {
                                AiErrorKind.HTTP_503_OVERLOAD ->
                                    "High demand on $modelId — retrying shortly…"
                                AiErrorKind.HTTP_429_RATE_LIMIT ->
                                    "Rate limit hit — waiting briefly before retry…"
                                else ->
                                    "Temporary server issue — retrying…"
                            }
                            emit(AiTurnEvent.ToolStatus(retryMsg))
                            delay(waitMs)
                            continue
                        }

                        // Switch to verified secondary model only for overload/availability/timeout/invalid-model errors
                        if (classified.allowsModelFallback && hasNextModel) {
                            shouldTryNextModel = true
                            break
                        }

                        // Non-fallbackable error (403 App Check, 401 Auth, Quota Exhausted, 400 Invalid Request, Offline)
                        throw ClassifiedAiException(classified, e)
                    }
                }

                if (!shouldTryNextModel) {
                    break
                }
            }
        }

        if (!completedTurn) {
            val finalError = lastClassified ?: AiErrorClassifier.classify(
                lastThrowable ?: IllegalStateException("AI request failed.")
            )
            throw ClassifiedAiException(finalError, lastThrowable)
        }
    }.flowOn(ioDispatcher)

    private data class ModelTurnResult(
        val finalText: String,
        val proposals: List<AiProposedAction>,
        val actionLogs: List<AiActionLogEntry>
    )

    private suspend fun executeSingleModelTurn(
        modelId: String,
        includeTools: Boolean,
        chatHistory: List<Content>,
        promptMessage: String,
        context: AiContext,
        toolExecutor: NotebookToolExecutor?,
        onTextDelta: suspend (String) -> Unit,
        onToolStatus: suspend (String) -> Unit
    ): ModelTurnResult {
        val collectedProposals = mutableListOf<AiProposedAction>()
        val collectedLogs = mutableListOf<AiActionLogEntry>()
        val session = transport.startChatSession(
            modelId = modelId,
            includeTools = includeTools,
            history = chatHistory
        )

        val accumulated = StringBuilder()
        var pendingInput: Content = content(role = "user") { text(promptMessage) }
        var round = 0

        return withTimeout(config.singleAttemptTimeoutMs) {
            while (round < MAX_TOOL_ROUNDS) {
                round++
                val roundCalls = mutableListOf<ToolInvocation>()
                val roundText = StringBuilder()

                var emptyStreamOnly = false
                try {
                    collectStreamWithRealWatchdog(
                        streamFlow = session.sendMessageStream(pendingInput),
                        firstTokenTimeoutMs = config.firstTokenTimeoutMs,
                        interChunkStallTimeoutMs = config.interChunkStallTimeoutMs,
                        onTextChunk = { delta ->
                            roundText.append(delta)
                            if (accumulated.isNotEmpty() && round > 1 && roundText.length == delta.length) {
                                accumulated.append("\n\n")
                            }
                            accumulated.append(delta)
                            onTextDelta(accumulated.toString())
                        },
                        onFunctionCall = { call ->
                            roundCalls += call
                        }
                    )
                } catch (_: EmptyStreamException) {
                    // Stream completed cleanly with zero payload — fall back once to unary call on same session
                    emptyStreamOnly = true
                }

                if (emptyStreamOnly) {
                    val unaryChunk = withTimeout(config.firstTokenTimeoutMs) {
                        session.sendMessageUnary(pendingInput)
                    }
                    unaryChunk.functionCalls.forEach { roundCalls += it }
                    if (unaryChunk.text.isNotEmpty()) {
                        if (accumulated.isNotEmpty()) accumulated.append("\n\n")
                        accumulated.append(unaryChunk.text)
                        onTextDelta(accumulated.toString())
                    }
                }

                if (roundCalls.isEmpty() || toolExecutor == null || !includeTools) {
                    break
                }

                val responseParts = mutableListOf<FunctionResponsePart>()
                for (call in roundCalls) {
                    onToolStatus(humanToolStatus(call.name))
                    val output = toolExecutor.execute(call, context)
                    output.proposal?.let { collectedProposals += it }
                    output.logEntry?.let { collectedLogs += it }
                    responseParts += FunctionResponsePart(
                        name = call.name,
                        response = output.responseJson,
                        id = call.id
                    )
                }

                pendingInput = content(role = "function") {
                    responseParts.forEach { part(it) }
                }
            }

            val resultText = accumulated.toString().trim()
            if (resultText.isBlank() && collectedProposals.isEmpty()) {
                throw IllegalStateException("Gemini returned an empty response. Please try again.")
            }
            val finalOutput = if (resultText.isBlank() && collectedProposals.isNotEmpty()) {
                "I prepared ${collectedProposals.size} notebook action preview(s) below. Please review and confirm to apply."
            } else {
                resultText
            }
            ModelTurnResult(
                finalText = finalOutput,
                proposals = collectedProposals,
                actionLogs = collectedLogs
            )
        }
    }

    /**
     * Genuine coroutine-enforced streaming watchdog.
     * Uses a producer channel + per-receive withTimeout so a stalled first token or mid-stream hang
     * is ALWAYS interrupted even when the underlying SDK flow emits nothing.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun collectStreamWithRealWatchdog(
        streamFlow: Flow<ModelStreamChunk>,
        firstTokenTimeoutMs: Long,
        interChunkStallTimeoutMs: Long,
        onTextChunk: suspend (String) -> Unit,
        onFunctionCall: (ToolInvocation) -> Unit
    ) {
        coroutineScope {
            var streamFailure: Throwable? = null
            val chunkChannel = produce<ModelStreamChunk>(capacity = Channel.BUFFERED) {
                try {
                    streamFlow.collect { chunk ->
                        send(chunk)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    streamFailure = t
                }
            }

            var receivedAnyPayload = false
            try {
                while (true) {
                    val waitBudget = if (!receivedAnyPayload) firstTokenTimeoutMs else interChunkStallTimeoutMs
                    val result = try {
                        withTimeout(waitBudget) {
                            chunkChannel.receiveCatching()
                        }
                    } catch (t: TimeoutCancellationException) {
                        chunkChannel.cancel()
                        throw StreamStalledException(
                            if (!receivedAnyPayload) {
                                "No first token received within ${firstTokenTimeoutMs}ms."
                            } else {
                                "Streaming stalled for ${interChunkStallTimeoutMs}ms."
                            }
                        )
                    }

                    if (result.isClosed) {
                        streamFailure?.let { throw it }
                        break
                    }

                    val chunk = result.getOrNull() ?: continue
                    if (chunk.text.isNotEmpty()) {
                        receivedAnyPayload = true
                        onTextChunk(chunk.text)
                    }
                    if (chunk.functionCalls.isNotEmpty()) {
                        receivedAnyPayload = true
                        chunk.functionCalls.forEach(onFunctionCall)
                    }
                }
            } finally {
                chunkChannel.cancel()
            }

            if (!receivedAnyPayload) {
                throw EmptyStreamException("Stream completed with zero payload.")
            }
        }
    }

    override suspend fun generateStructuredTopicInfo(
        notebookName: String,
        notebookPurpose: String,
        topicName: String,
        languagePref: String
    ): GeneratedTopicInfo = withContext(ioDispatcher) {
        if (!connectivityChecker.isNetworkAvailable()) {
            throw ClassifiedAiException(AiErrorClassifier.classify(NoNetworkConnectionException()))
        }

        val prompt = """
            You are helping a user build a structured Topic Information Page inside their Personal Knowledge & Notebook Library.
            Notebook: $notebookName
            Notebook Purpose: $notebookPurpose
            Topic Title: $topicName
            Language Preference: $languagePref (keep clean English headings; if MIXED/HINDI you may add helpful clarity).
            Generate structured, accurate, practical educational metadata for this topic.
        """.trimIndent()

        val candidateModels = resolveCandidateModels()
        var lastError: Throwable? = null
        var rawJson: String? = null

        for ((index, modelId) in candidateModels.withIndex()) {
            val isPrimary = modelId == config.primaryModelId
            val hasNext = index < candidateModels.lastIndex
            try {
                rawJson = withTimeout(config.singleAttemptTimeoutMs) {
                    transport.generateStructuredTopicJson(modelId, prompt)
                }
                healthTracker.markHealthy(modelId)
                break
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException && hasNext) {
                    lastError = e
                    continue
                }
                throw e
            } catch (e: Exception) {
                lastError = e
                val classified = AiErrorClassifier.classify(e)
                if (classified.triggersPrimaryCooldown && isPrimary) {
                    healthTracker.markOverloaded(modelId, config.primaryOverloadCooldownMs)
                }
                if (classified.allowsModelFallback && hasNext) {
                    continue
                }
                throw ClassifiedAiException(classified, e)
            }
        }

        val finalJson = rawJson ?: throw ClassifiedAiException(
            AiErrorClassifier.classify(lastError ?: IllegalStateException("Topic generation failed.")),
            lastError
        )
        val json = JSONObject(finalJson)

        fun readArray(key: String): List<String> {
            val arr = json.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                arr.optString(i).takeIf { it.isNotBlank() }
            }
        }

        GeneratedTopicInfo(
            shortDescription = json.optString("shortDescription"),
            purpose = json.optString("purpose"),
            introduction = json.optString("introduction"),
            coversItems = readArray("coversItems"),
            importantConcepts = readArray("importantConcepts"),
            subtopics = readArray("subtopics"),
            relatedTopics = readArray("relatedTopics")
        )
    }

    internal fun resolveCandidateModels(): List<String> {
        val primary = config.primaryModelId.trim()
        val secondary = config.secondaryModelId.trim()
        if (secondary.isBlank() || secondary == primary) {
            return listOf(primary)
        }
        return if (healthTracker.isInCooldown(primary)) {
            listOf(secondary, primary)
        } else {
            listOf(primary, secondary)
        }
    }

    internal fun isFastPathGreeting(userRequest: String, actionLabel: String): Boolean {
        if (actionLabel.isNotBlank() && actionLabel != "Ask AI") return false
        val normalized = userRequest.trim().lowercase().replace(Regex("[!.?,]+$"), "").trim()
        if (normalized.length > 24) return false
        return normalized in FAST_PATH_GREETINGS
    }

    private fun buildChatHistory(history: List<AiChatMessage>): List<Content> {
        // Keep last 8 valid turns with compact per-turn cap to keep payloads lean and fast
        val valid = history
            .filter { !it.isError && it.text.isNotBlank() }
            .takeLast(8)
        val contents = mutableListOf<Content>()
        var lastRole: String? = null
        for (msg in valid) {
            val role = if (msg.role == AiChatRole.USER) "user" else "model"
            if (contents.isEmpty() && role != "user") continue
            if (role == lastRole) continue
            contents += content(role = role) { text(msg.text.take(1800)) }
            lastRole = role
        }
        if (contents.isNotEmpty() && contents.last().role == "user") {
            contents.removeAt(contents.lastIndex)
        }
        return contents
    }

    private fun buildContextualUserTurn(
        userRequest: String,
        actionLabel: String,
        ctx: AiContext,
        compactMode: Boolean
    ): String {
        val langInstruction = when (ctx.responseLanguage) {
            "HINDI" -> "Respond primarily in clear Hindi (Devanagari) with English technical terms where helpful. Respect the user's own language if they write in English."
            "MIXED" -> "Match the user's language naturally (English, Hindi Devanagari, or Hinglish)."
            else -> "Respond in clear English while understanding Hindi and Hinglish accurately."
        }

        if (compactMode) {
            return buildString {
                appendLine("[CONTEXT]")
                ctx.notebook?.let { appendLine("Notebook: #${it.notebookNumber} ${it.name}") }
                ctx.topic?.let { appendLine("Topic: ${it.name}") }
                appendLine("Language: $langInstruction")
                appendLine("[END CONTEXT]")
                append("User Message: ")
                append(userRequest)
            }
        }

        val nbGuidance = notebookDomainGuidance(ctx.notebook?.notebookType, ctx.notebook?.name)
        return buildString {
            appendLine("[NOTEBOOK CONTEXT — USE ONLY RELEVANT DETAILS]")
            appendLine("Domain: $nbGuidance")
            appendLine("Language: $langInstruction")
            if (actionLabel.isNotBlank() && actionLabel != "Ask AI") {
                appendLine("Action: $actionLabel")
            }
            ctx.notebook?.let {
                appendLine("Notebook: #${it.notebookNumber} ${it.name}")
                if (it.purpose.isNotBlank()) appendLine("Purpose: ${it.purpose.take(260)}")
            }
            ctx.topic?.let {
                appendLine("Topic: ${it.name}")
                if (it.purpose.isNotBlank()) appendLine("Topic Purpose: ${it.purpose.take(240)}")
                if (it.coversItems.isNotEmpty()) {
                    appendLine("Coverage: ${it.coversItems.take(10).joinToString(", ")}")
                }
                if (it.personalNotes.isNotBlank()) {
                    appendLine("User Topic Notes:\n${it.personalNotes.take(1100)}")
                }
            }
            ctx.subtopic?.let {
                appendLine("Subtopic: ${it.name} — ${it.shortDescription.take(180)}")
            }
            ctx.currentPage?.let {
                appendLine("Open Page: ID=${it.id}, Page #${it.pageNumber}, Title=\"${it.title}\"")
            }
            if (ctx.selectedText.isNotBlank()) {
                appendLine("Selected Text:\n\"\"\"${ctx.selectedText.take(1200)}\"\"\"")
            }
            if (ctx.currentPageText.isNotBlank()) {
                appendLine("Current Page Content:\n\"\"\"${ctx.currentPageText.take(2600)}\"\"\"")
            }
            if (ctx.relatedPages.isNotEmpty()) {
                appendLine("Other Pages in Scope:")
                ctx.relatedPages.take(4).forEach { p ->
                    val preview = p.content.replace("\n", " ").take(160)
                    appendLine("- [${p.id}] P${p.pageNumber} \"${p.title}\": ${preview.ifBlank { "(blank)" }}")
                }
            }
            if (ctx.notebookTopics.isNotEmpty()) {
                appendLine("Notebook Topics: ${ctx.notebookTopics.take(18).joinToString(", ") { it.name }}")
            }
            if (ctx.topicSubtopics.isNotEmpty()) {
                appendLine("Subtopics: ${ctx.topicSubtopics.take(12).joinToString(", ") { it.name }}")
            }
            if (ctx.specializedContextSummary.isNotBlank()) {
                appendLine("Notebook Data:\n${ctx.specializedContextSummary.take(1000)}")
            }
            if (ctx.learningProgress.isNotEmpty()) {
                appendLine("Learning Progress:")
                ctx.learningProgress.take(12).forEach { r ->
                    appendLine("- ${r.conceptName}: ${r.status.label}${if (r.notes.isNotBlank()) " (${r.notes.take(80)})" else ""}")
                }
            }
            appendLine("[END CONTEXT]")
            appendLine()
            append("User Message: ")
            append(userRequest)
        }
    }

    private fun notebookDomainGuidance(type: String?, name: String?): String = when (type) {
        "GENERAL" -> "Personal encyclopedia (science, biology, nutrition, physiology, tech, AI, general knowledge)."
        "VOCABULARY" -> "English vocabulary coach (definitions, pronunciation, Hindi meanings, synonyms, antonyms, usage)."
        "SPOKEN_ENGLISH" -> "Spoken English trainer (sentence formation, practical grammar, Hindi-English patterns, drills)."
        "SKILLS" -> "Social, emotional, and cognitive skills mentor (mechanisms, real scenarios, communication scripts)."
        "FITNESS" -> "Evidence-based training analyst (workout logs, sets/reps/weights, RIR/RPE, progressive overload, recovery)."
        "SOCIAL_MEDIA" -> "Creator & media strategist (hooks, editing workflows, audience psychology, content systems)."
        "SAVED_WORK" -> "Knowledge organizer (distill saved messages/resources into clear summaries and action steps)."
        "HANDWRITING" -> "Penmanship coach (letter formation, spacing checks, speed & consistency drills)."
        "TODO" -> "Execution planner (prioritize tasks into concrete steps and timelines)."
        "FINANCE" -> "Personal finance educator (clear frameworks, budgeting, taxes, inflation, investing basics)."
        "LIFE_LESSONS" -> "Reflective partner on human behavior, relationships, life stages, and practical wisdom."
        "ROUGH" -> "Quick scratchpad helper (turn rough thoughts into structured notes)."
        else -> "Context-aware assistant for notebook \"${name ?: "Custom Notebook"}\"."
    }

    private fun humanToolStatus(toolName: String): String = when (toolName) {
        NotebookAiTools.FN_SEARCH_NOTEBOOK -> "Searching your notebook…"
        NotebookAiTools.FN_READ_PAGE -> "Reading notebook page…"
        NotebookAiTools.FN_GET_LEARNING_PROGRESS -> "Checking learning progress…"
        NotebookAiTools.FN_UPDATE_LEARNING_PROGRESS -> "Updating learning progress…"
        NotebookAiTools.FN_PROPOSE_CREATE_PAGE -> "Preparing new page preview…"
        NotebookAiTools.FN_PROPOSE_APPEND_TO_PAGE -> "Preparing notes to append…"
        NotebookAiTools.FN_PROPOSE_ORGANIZE_PAGE -> "Preparing organized page preview…"
        NotebookAiTools.FN_PROPOSE_RENAME_PAGE -> "Preparing page rename…"
        NotebookAiTools.FN_PROPOSE_CREATE_TOPIC -> "Preparing new topic…"
        NotebookAiTools.FN_PROPOSE_CREATE_SUBTOPIC -> "Preparing new subtopic…"
        NotebookAiTools.FN_PROPOSE_MOVE_CONTENT -> "Preparing content move preview…"
        else -> "Working with notebook…"
    }

    private class EmptyStreamException(message: String) : Exception(message)

    private companion object {
        const val TAG = "NotebookAiRepo"
        const val MAX_TOOL_ROUNDS = 4

        val FAST_PATH_GREETINGS = setOf(
            "hi", "hello", "hey", "hii", "hiii", "hlo", "helo",
            "namaste", "namaskar", "नमस्ते", "नमस्कार", "हेलो", "हाय",
            "good morning", "good afternoon", "good evening", "good night",
            "thanks", "thank you", "ok", "okay"
        )

        val AGENT_SYSTEM_INSTRUCTION = """
            You are a genuine, context-aware Notebook AI Agent inside the user's Personal Knowledge & Notebook Library app.
            You understand natural-language instructions in English, Hindi (Devanagari), and Hinglish without needing rigid commands.

            CORE TRUTHFULNESS & QUALITY RULES:
            1. Be factually accurate, clear, and well-structured. Distinguish established facts from assumptions or uncertainty.
            2. NEVER invent research citations, papers, or statistics. NEVER claim you browsed the live internet.
            3. Clearly separate the user's EXISTING notes / ALREADY STUDIED concepts from NEWLY GENERATED explanations and MISSING concepts.
            4. Avoid superficial fluff and unnecessary repetition. Match depth to the user's request.

            PROFESSIONAL STUDY NOTES ARCHITECTURE:
            When the user describes what they studied or asks to create proper/detailed/revision notes:
            - Organize concepts in logical pedagogical order using clean Markdown headings, bullet points, key term highlights, and comparison tables where helpful.
            - Adapt naturally from: Topic Title, Introduction, Fundamental Concepts, Main Explanation, Subtopics, Important Mechanisms & Processes, Examples & Applications, Important Facts, Related Concepts, Key Takeaways, Quick Revision, Questions for Self-Assessment, and Further Learning / Important Missing Concepts (omit irrelevant headings).
            - Always list important missing concepts separately and suggest what to explore next.

            LEARNING PROGRESS DISTINCTION:
            - Maintain strict distinctions: Already Studied, Partially Studied, Not Yet Studied, Needs Revision, Newly Generated.
            - When the user explicitly tells you what they studied, partially studied, haven't studied, or want to revise, call `update_learning_progress`.
            - NEVER mark something as Already Studied just because you generated notes about it (use NEWLY_GENERATED for freshly generated material).

            REAL NOTEBOOK ACTIONS (TOOLS):
            - Call read-only tools (`search_notebook_content`, `read_notebook_page`, `get_learning_progress`) whenever you need to inspect other pages, summarize what is in the notebook, or find gaps.
            - When the user asks to create a page, create a revision page, append notes, organize an existing page, rename a page, create a topic, create a subtopic, or move content between pages, call the corresponding `propose_*` tool.
            - Because `propose_*` tools create preview/confirmation cards for safety, NEVER claim the change has already been permanently saved before the user taps Confirm.
        """.trimIndent()
    }
}
