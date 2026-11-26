package com.example.data.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Per-user durable local storage for AI conversation history and learning progress records.
 * Uses atomic temp-file writes on Dispatchers.IO so data survives app refreshes and restarts.
 */
class AiAgentStorage(
    private val baseDir: File
) {
    constructor(context: Context, userId: String) : this(
        File(context.applicationContext.filesDir, "ai_agent/${userId.replace(Regex("[^a-zA-Z0-9_-]"), "_")}")
    )

    private val sessionsFile: File get() = File(baseDir, "conversations.json")
    private val progressFile: File get() = File(baseDir, "learning_progress.json")

    suspend fun loadSessions(): List<AiConversationSession> = withContext(Dispatchers.IO) {
        if (!sessionsFile.exists()) return@withContext emptyList()
        try {
            val raw = sessionsFile.readText()
            if (raw.isBlank()) return@withContext emptyList()
            val arr = JSONArray(raw)
            val result = mutableListOf<AiConversationSession>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val msgArr = obj.optJSONArray("messages") ?: JSONArray()
                val msgs = mutableListOf<AiChatMessage>()
                for (j in 0 until msgArr.length()) {
                    val m = msgArr.optJSONObject(j) ?: continue
                    val roleName = m.optString("role", AiChatRole.USER.name)
                    val role = AiChatRole.entries.firstOrNull { it.name == roleName } ?: AiChatRole.USER
                    val logsArr = m.optJSONArray("actionLogs") ?: JSONArray()
                    val logs = mutableListOf<AiActionLogEntry>()
                    for (k in 0 until logsArr.length()) {
                        val l = logsArr.optJSONObject(k) ?: continue
                        logs += AiActionLogEntry(
                            id = l.optString("id"),
                            label = l.optString("label"),
                            succeeded = l.optBoolean("succeeded", true),
                            detail = l.optString("detail"),
                            undone = l.optBoolean("undone", false)
                        )
                    }
                    msgs += AiChatMessage(
                        id = m.optString("id"),
                        role = role,
                        text = m.optString("text"),
                        timestampMillis = m.optLong("timestampMillis", 0L),
                        actionLabel = m.optString("actionLabel").takeIf { it.isNotBlank() },
                        actionLogs = logs,
                        isError = m.optBoolean("isError", false),
                        retryPrompt = m.optString("retryPrompt").takeIf { it.isNotBlank() },
                        modelUsed = m.optString("modelUsed").takeIf { it.isNotBlank() },
                        usedFallbackModel = m.optBoolean("usedFallbackModel", false)
                    )
                }
                result += AiConversationSession(
                    id = obj.optString("id"),
                    title = obj.optString("title", "Conversation"),
                    notebookId = obj.optString("notebookId").takeIf { it.isNotBlank() },
                    notebookName = obj.optString("notebookName"),
                    topicId = obj.optString("topicId").takeIf { it.isNotBlank() },
                    topicName = obj.optString("topicName").takeIf { it.isNotBlank() },
                    updatedAtMillis = obj.optLong("updatedAtMillis", 0L),
                    messages = msgs
                )
            }
            result.sortedByDescending { it.updatedAtMillis }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load AI sessions", e)
            emptyList()
        }
    }

    suspend fun saveSessions(sessions: List<AiConversationSession>) = withContext(Dispatchers.IO) {
        try {
            if (!baseDir.exists()) baseDir.mkdirs()
            val trimmed = sessions.sortedByDescending { it.updatedAtMillis }.take(30)
            val arr = JSONArray()
            trimmed.forEach { s ->
                val msgArr = JSONArray()
                s.messages.takeLast(60).forEach { m ->
                    val logsArr = JSONArray()
                    m.actionLogs.forEach { l ->
                        logsArr.put(
                            JSONObject().apply {
                                put("id", l.id)
                                put("label", l.label)
                                put("succeeded", l.succeeded)
                                put("detail", l.detail)
                                put("undone", l.undone)
                            }
                        )
                    }
                    msgArr.put(
                        JSONObject().apply {
                            put("id", m.id)
                            put("role", m.role.name)
                            put("text", m.text)
                            put("timestampMillis", m.timestampMillis)
                            put("actionLabel", m.actionLabel ?: "")
                            put("actionLogs", logsArr)
                            put("isError", m.isError)
                            put("retryPrompt", m.retryPrompt ?: "")
                            put("modelUsed", m.modelUsed ?: "")
                            put("usedFallbackModel", m.usedFallbackModel)
                        }
                    )
                }
                arr.put(
                    JSONObject().apply {
                        put("id", s.id)
                        put("title", s.title)
                        put("notebookId", s.notebookId ?: "")
                        put("notebookName", s.notebookName)
                        put("topicId", s.topicId ?: "")
                        put("topicName", s.topicName ?: "")
                        put("updatedAtMillis", s.updatedAtMillis)
                        put("messages", msgArr)
                    }
                )
            }
            atomicWrite(sessionsFile, arr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save AI sessions", e)
        }
    }

    suspend fun loadLearningProgress(): List<LearningProgressRecord> = withContext(Dispatchers.IO) {
        if (!progressFile.exists()) return@withContext emptyList()
        try {
            val raw = progressFile.readText()
            if (raw.isBlank()) return@withContext emptyList()
            val arr = JSONArray(raw)
            val result = mutableListOf<LearningProgressRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val concept = obj.optString("conceptName").trim()
                if (concept.isBlank()) continue
                result += LearningProgressRecord(
                    id = obj.optString("id"),
                    notebookId = obj.optString("notebookId"),
                    topicId = obj.optString("topicId").takeIf { it.isNotBlank() },
                    subtopicId = obj.optString("subtopicId").takeIf { it.isNotBlank() },
                    conceptName = concept,
                    status = LearningProgressStatus.fromRaw(obj.optString("status")),
                    notes = obj.optString("notes"),
                    updatedAtMillis = obj.optLong("updatedAtMillis", 0L)
                )
            }
            result.sortedByDescending { it.updatedAtMillis }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load learning progress", e)
            emptyList()
        }
    }

    suspend fun saveLearningProgress(records: List<LearningProgressRecord>) = withContext(Dispatchers.IO) {
        try {
            if (!baseDir.exists()) baseDir.mkdirs()
            val arr = JSONArray()
            records.sortedByDescending { it.updatedAtMillis }.take(300).forEach { r ->
                arr.put(
                    JSONObject().apply {
                        put("id", r.id)
                        put("notebookId", r.notebookId)
                        put("topicId", r.topicId ?: "")
                        put("subtopicId", r.subtopicId ?: "")
                        put("conceptName", r.conceptName)
                        put("status", r.status.name)
                        put("notes", r.notes)
                        put("updatedAtMillis", r.updatedAtMillis)
                    }
                )
            }
            atomicWrite(progressFile, arr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save learning progress", e)
        }
    }

    private fun atomicWrite(target: File, content: String) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(content)
        if (!temp.renameTo(target)) {
            target.writeText(content)
            temp.delete()
        }
    }

    private companion object {
        const val TAG = "AiAgentStorage"
    }
}
