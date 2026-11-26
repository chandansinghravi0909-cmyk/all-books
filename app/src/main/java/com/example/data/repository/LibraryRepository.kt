package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.AppPreferencesEntity
import com.example.data.model.AttachmentEntity
import com.example.data.model.FitnessLogEntity
import com.example.data.model.NotebookEntity
import com.example.data.model.PageEntity
import com.example.data.model.SeedData
import com.example.data.model.SubtopicEntity
import com.example.data.model.TodoTaskEntity
import com.example.data.model.TopicEntity
import com.example.data.model.VocabularyEntryEntity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class LibraryRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun userRoot(userId: String) = db.collection("users").document(userId)

    private fun requireSignedInUser(expectedUserId: String): String {
        val current = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(current == expectedUserId) { "Active user changed; refusing cross-user operation." }
        return current
    }

    private inline fun <reified T> observeUserCollection(
        userId: String,
        collectionName: String
    ): Flow<List<T>> {
        val path = "users/$userId/$collectionName"
        return userRoot(userId).collection(collectionName)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    T::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .flowOn(Dispatchers.Default)
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                val toLog = fsError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeNotebooks(userId: String): Flow<List<NotebookEntity>> =
        observeUserCollection<NotebookEntity>(userId, "notebooks")
            .map { list -> list.sortedWith(compareBy<NotebookEntity> { it.sortOrder }.thenBy { it.notebookNumber }) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observeTopics(userId: String): Flow<List<TopicEntity>> =
        observeUserCollection<TopicEntity>(userId, "topics")
            .map { list -> list.sortedBy { it.sortOrder } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observeSubtopics(userId: String): Flow<List<SubtopicEntity>> =
        observeUserCollection<SubtopicEntity>(userId, "subtopics")
            .map { list -> list.sortedBy { it.sortOrder } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observePages(userId: String): Flow<List<PageEntity>> =
        observeUserCollection<PageEntity>(userId, "pages")
            .map { list -> list.sortedBy { it.pageNumber } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observeAttachments(userId: String): Flow<List<AttachmentEntity>> =
        observeUserCollection<AttachmentEntity>(userId, "attachments")
            .map { list -> list.sortedByDescending { it.updatedAt?.seconds ?: 0L } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observeFitnessLogs(userId: String): Flow<List<FitnessLogEntity>> =
        observeUserCollection<FitnessLogEntity>(userId, "fitnessLogs")
            .map { list -> list.sortedByDescending { it.workoutDateMillis } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observeVocabulary(userId: String): Flow<List<VocabularyEntryEntity>> =
        observeUserCollection<VocabularyEntryEntity>(userId, "vocabulary")
            .map { list -> list.sortedBy { it.word.lowercase() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observeTodos(userId: String): Flow<List<TodoTaskEntity>> =
        observeUserCollection<TodoTaskEntity>(userId, "todos")
            .map { list -> list.sortedWith(compareBy<TodoTaskEntity> { it.completed }.thenByDescending { it.updatedAt?.seconds ?: 0L }) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    fun observePreferences(userId: String): Flow<AppPreferencesEntity> {
        val path = "users/$userId/preferences/user_settings"
        return userRoot(userId).collection("preferences").document("user_settings")
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        AppPreferencesEntity::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: AppPreferencesEntity(userId = userId)
                } else {
                    AppPreferencesEntity(userId = userId)
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                val toLog = fsError ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.GET, path)
                }
                throw error
            }
    }

    suspend fun ensureInitialSeed(userId: String): Result<Unit> {
        return try {
            val uid = requireSignedInUser(userId)
            val root = userRoot(uid)
            val existingNotebooks = root.collection("notebooks").limit(1).get().await()
            if (!existingNotebooks.isEmpty) {
                return Result.success(Unit)
            }

            val operations = withContext(Dispatchers.Default) {
                val list = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()

                SeedData.defaultNotebooks().forEach { nb ->
                    list += root.collection("notebooks").document(nb.id) to nb.toCreateMap(uid)
                }
                SeedData.defaultTopics().forEach { topic ->
                    list += root.collection("topics").document(topic.id) to topic.toCreateMap(uid)
                }
                SeedData.defaultSubtopics().forEach { sub ->
                    list += root.collection("subtopics").document(sub.id) to sub.toCreateMap(uid)
                }
                SeedData.defaultPages().forEach { page ->
                    list += root.collection("pages").document(page.id) to page.toCreateMap(uid)
                }
                SeedData.defaultVocabulary().forEach { voc ->
                    list += root.collection("vocabulary").document(voc.id) to voc.toCreateMap(uid)
                }
                SeedData.defaultFitnessLogs().forEach { fit ->
                    list += root.collection("fitnessLogs").document(fit.id) to fit.toCreateMap(uid)
                }
                SeedData.defaultTodos().forEach { todo ->
                    list += root.collection("todos").document(todo.id) to todo.toCreateMap(uid)
                }
                SeedData.defaultSavedWork().forEach { att ->
                    list += root.collection("attachments").document(att.id) to att.toCreateMap(uid)
                }
                val prefs = AppPreferencesEntity(userId = uid)
                list += root.collection("preferences").document("user_settings") to prefs.toCreateMap(uid)
                list
            }

        operations.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, map) -> batch.set(ref, map) }
            batch.commit().await()
        }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$userId/seed")
            Result.failure(e)
        }
    }

    private fun newId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(16)}"

    // --- NOTEBOOKS ---

    suspend fun createNotebook(userId: String, notebook: NotebookEntity): Result<String> {
        val docId = notebook.id.ifBlank { newId("nb") }
        val path = "users/$userId/notebooks/$docId"
        return try {
            val uid = requireSignedInUser(userId)
            require(notebook.name.isNotBlank()) { "Notebook name is required." }
            userRoot(uid).collection("notebooks").document(docId)
                .set(notebook.copy(id = docId).toCreateMap(uid))
                .await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateNotebook(
        userId: String,
        notebookId: String,
        fields: Map<String, Any>
    ): Result<Unit> {
        val path = "users/$userId/notebooks/$notebookId"
        return try {
            val uid = requireSignedInUser(userId)
            require(notebookId.isNotBlank())
            val payload = fields.toMutableMap().apply {
                this["updatedAt"] = FieldValue.serverTimestamp()
            }
            userRoot(uid).collection("notebooks").document(notebookId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteNotebook(userId: String, notebookId: String): Result<Unit> {
        val path = "users/$userId/notebooks/$notebookId"
        return try {
            val uid = requireSignedInUser(userId)
            userRoot(uid).collection("notebooks").document(notebookId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- TOPICS ---

    suspend fun createTopic(userId: String, topic: TopicEntity): Result<String> {
        val docId = topic.id.ifBlank { newId("topic") }
        val path = "users/$userId/topics/$docId"
        return try {
            val uid = requireSignedInUser(userId)
            require(topic.name.isNotBlank()) { "Topic name is required." }
            userRoot(uid).collection("topics").document(docId)
                .set(topic.copy(id = docId).toCreateMap(uid))
                .await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTopic(
        userId: String,
        topicId: String,
        fields: Map<String, Any>
    ): Result<Unit> {
        val path = "users/$userId/topics/$topicId"
        return try {
            val uid = requireSignedInUser(userId)
            val payload = fields.toMutableMap().apply {
                this["updatedAt"] = FieldValue.serverTimestamp()
            }
            userRoot(uid).collection("topics").document(topicId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteTopic(userId: String, topicId: String): Result<Unit> {
        val path = "users/$userId/topics/$topicId"
        return try {
            val uid = requireSignedInUser(userId)
            userRoot(uid).collection("topics").document(topicId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- SUBTOPICS ---

    suspend fun createSubtopic(userId: String, subtopic: SubtopicEntity): Result<String> {
        val docId = subtopic.id.ifBlank { newId("sub") }
        val path = "users/$userId/subtopics/$docId"
        return try {
            val uid = requireSignedInUser(userId)
            require(subtopic.name.isNotBlank()) { "Subtopic name is required." }
            userRoot(uid).collection("subtopics").document(docId)
                .set(subtopic.copy(id = docId).toCreateMap(uid))
                .await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSubtopic(
        userId: String,
        subtopicId: String,
        fields: Map<String, Any>
    ): Result<Unit> {
        val path = "users/$userId/subtopics/$subtopicId"
        return try {
            val uid = requireSignedInUser(userId)
            val payload = fields.toMutableMap().apply {
                this["updatedAt"] = FieldValue.serverTimestamp()
            }
            userRoot(uid).collection("subtopics").document(subtopicId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSubtopic(userId: String, subtopicId: String): Result<Unit> {
        val path = "users/$userId/subtopics/$subtopicId"
        return try {
            val uid = requireSignedInUser(userId)
            userRoot(uid).collection("subtopics").document(subtopicId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- PAGES ---

    suspend fun createPage(userId: String, page: PageEntity): Result<String> {
        val docId = page.id.ifBlank { newId("page") }
        val path = "users/$userId/pages/$docId"
        return try {
            val uid = requireSignedInUser(userId)
            userRoot(uid).collection("pages").document(docId)
                .set(page.copy(id = docId).toCreateMap(uid))
                .await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePage(
        userId: String,
        pageId: String,
        fields: Map<String, Any>
    ): Result<Unit> {
        val path = "users/$userId/pages/$pageId"
        return try {
            val uid = requireSignedInUser(userId)
            val payload = fields.toMutableMap().apply {
                this["updatedAt"] = FieldValue.serverTimestamp()
            }
            userRoot(uid).collection("pages").document(pageId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePage(userId: String, pageId: String): Result<Unit> {
        val path = "users/$userId/pages/$pageId"
        return try {
            val uid = requireSignedInUser(userId)
            userRoot(uid).collection("pages").document(pageId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- ATTACHMENTS (Saved Work & Images) ---

    suspend fun createAttachment(userId: String, attachment: AttachmentEntity): Result<String> {
        val docId = attachment.id.ifBlank { newId("att") }
        val path = "users/$userId/attachments/$docId"
        return try {
            val uid = requireSignedInUser(userId)
            userRoot(uid).collection("attachments").document(docId)
                .set(attachment.copy(id = docId).toCreateMap(uid))
                .await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateAttachment(
        userId: String,
        attachmentId: String,
        fields: Map<String, Any>
    ): Result<Unit> {
        val path = "users/$userId/attachments/$attachmentId"
        return try {
            val uid = requireSignedInUser(userId)
            val payload = fields.toMutableMap().apply {
                this["updatedAt"] = FieldValue.serverTimestamp()
            }
            userRoot(uid).collection("attachments").document(attachmentId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAttachment(userId: String, attachmentId: String): Result<Unit> {
        val path = "users/$userId/attachments/$attachmentId"
        return try {
            val uid = requireSignedInUser(userId)
            userRoot(uid).collection("attachments").document(attachmentId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- FITNESS LOGS ---

    suspend fun createFitnessLog(userId: String, log: FitnessLogEntity): Result<String> {
        val docId = log.id.ifBlank { newId("fit") }
        val path = "users/$userId/fitnessLogs/$docId"
        return try {
            val uid = requireSignedInUser(userId)
            require(log.exerciseName.isNotBlank())
            userRoot(uid).collection("fitnessLogs").document(docId)
                .set(log.copy(id = docId).toCreateMap(uid))
                .await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteFitnessLog(userId: String, logId: String): Result<Unit> {
        val path = "users/$userId/fitnessLogs/$logId"
        return try {
            val uid = requireSignedInUser(userId)
            userRoot(uid).collection("fitnessLogs").document(logId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- VOCABULARY ---

    suspend fun createVocabulary(userId: String, entry: VocabularyEntryEntity): Result<String> {
        val docId = entry.id.ifBlank { newId("voc") }
        val path = "users/$userId/vocabulary/$docId"
        return try {
            val uid = requireSignedInUser(userId)
            require(entry.word.isNotBlank())
            userRoot(uid).collection("vocabulary").document(docId)
                .set(entry.copy(id = docId).toCreateMap(uid))
                .await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateVocabulary(
        userId: String,
        entryId: String,
        fields: Map<String, Any>
    ): Result<Unit> {
        val path = "users/$userId/vocabulary/$entryId"
        return try {
            val uid = requireSignedInUser(userId)
            val payload = fields.toMutableMap().apply {
                this["updatedAt"] = FieldValue.serverTimestamp()
            }
            userRoot(uid).collection("vocabulary").document(entryId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteVocabulary(userId: String, entryId: String): Result<Unit> {
        val path = "users/$userId/vocabulary/$entryId"
        return try {
            val uid = requireSignedInUser(userId)
            userRoot(uid).collection("vocabulary").document(entryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- TODOS ---

    suspend fun createTodo(userId: String, todo: TodoTaskEntity): Result<String> {
        val docId = todo.id.ifBlank { newId("todo") }
        val path = "users/$userId/todos/$docId"
        return try {
            val uid = requireSignedInUser(userId)
            require(todo.title.isNotBlank())
            userRoot(uid).collection("todos").document(docId)
                .set(todo.copy(id = docId).toCreateMap(uid))
                .await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTodo(
        userId: String,
        todoId: String,
        fields: Map<String, Any>
    ): Result<Unit> {
        val path = "users/$userId/todos/$todoId"
        return try {
            val uid = requireSignedInUser(userId)
            val payload = fields.toMutableMap().apply {
                this["updatedAt"] = FieldValue.serverTimestamp()
            }
            userRoot(uid).collection("todos").document(todoId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteTodo(userId: String, todoId: String): Result<Unit> {
        val path = "users/$userId/todos/$todoId"
        return try {
            val uid = requireSignedInUser(userId)
            userRoot(uid).collection("todos").document(todoId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- PREFERENCES ---

    suspend fun savePreferences(userId: String, prefs: AppPreferencesEntity): Result<Unit> {
        val path = "users/$userId/preferences/user_settings"
        return try {
            val uid = requireSignedInUser(userId)
            val docRef = userRoot(uid).collection("preferences").document("user_settings")
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updateMap = mapOf(
                    "customAppName" to prefs.customAppName.ifBlank { "Knowledge Library" }.take(40),
                    "themeMode" to prefs.themeMode,
                    "accentColorKey" to prefs.accentColorKey,
                    "uiFontKey" to prefs.uiFontKey,
                    "writingFontKey" to prefs.writingFontKey,
                    "handwritingFontKey" to prefs.handwritingFontKey,
                    "defaultFontSizeSp" to prefs.defaultFontSizeSp.coerceIn(12, 32),
                    "paperColorKey" to prefs.paperColorKey,
                    "ruledLineColorKey" to prefs.ruledLineColorKey,
                    "lineSpacingMultiplier" to prefs.lineSpacingMultiplier.coerceIn(1.0, 3.0),
                    "defaultCoverStyle" to prefs.defaultCoverStyle,
                    "dashboardLayout" to prefs.dashboardLayout,
                    "showArchivedOnDashboard" to prefs.showArchivedOnDashboard,
                    "preferredLanguage" to prefs.preferredLanguage,
                    "voiceInputLanguage" to prefs.voiceInputLanguage,
                    "aiEnabled" to prefs.aiEnabled,
                    "aiResponseLanguage" to prefs.aiResponseLanguage,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updateMap).await()
            } else {
                docRef.set(prefs.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- BATCH RESTORE (Non-destructive) ---

    suspend fun restoreBackup(
        userId: String,
        notebooks: List<NotebookEntity>,
        topics: List<TopicEntity>,
        subtopics: List<SubtopicEntity>,
        pages: List<PageEntity>,
        attachments: List<AttachmentEntity>,
        fitnessLogs: List<FitnessLogEntity>,
        vocabulary: List<VocabularyEntryEntity>,
        todos: List<TodoTaskEntity>,
        existingNotebookIds: Set<String>,
        existingTopicIds: Set<String>,
        existingSubtopicIds: Set<String>,
        existingPageIds: Set<String>
    ): Result<Int> = try {
        val uid = requireSignedInUser(userId)
        val root = userRoot(uid)
        val ops = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()

        // Non-destructive merge: skip records whose IDs already exist, or create new IDs for colliding pages
        val nbIdMap = mutableMapOf<String, String>()
        notebooks.forEach { nb ->
            if (nb.id in existingNotebookIds) {
                nbIdMap[nb.id] = nb.id
            } else {
                val targetId = nb.id.ifBlank { newId("nb") }
                nbIdMap[nb.id] = targetId
                ops += root.collection("notebooks").document(targetId) to nb.copy(id = targetId).toCreateMap(uid)
            }
        }

        val topicIdMap = mutableMapOf<String, String>()
        topics.forEach { topic ->
            val mappedNb = nbIdMap[topic.notebookId] ?: topic.notebookId
            if (topic.id in existingTopicIds) {
                topicIdMap[topic.id] = topic.id
            } else {
                val targetId = topic.id.ifBlank { newId("topic") }
                topicIdMap[topic.id] = targetId
                ops += root.collection("topics").document(targetId) to topic.copy(
                    id = targetId,
                    notebookId = mappedNb
                ).toCreateMap(uid)
            }
        }

        val subIdMap = mutableMapOf<String, String>()
        subtopics.forEach { sub ->
            val mappedNb = nbIdMap[sub.notebookId] ?: sub.notebookId
            val mappedTopic = topicIdMap[sub.topicId] ?: sub.topicId
            if (sub.id in existingSubtopicIds) {
                subIdMap[sub.id] = sub.id
            } else {
                val targetId = sub.id.ifBlank { newId("sub") }
                subIdMap[sub.id] = targetId
                ops += root.collection("subtopics").document(targetId) to sub.copy(
                    id = targetId,
                    notebookId = mappedNb,
                    topicId = mappedTopic
                ).toCreateMap(uid)
            }
        }

        pages.forEach { page ->
            val mappedNb = nbIdMap[page.notebookId] ?: page.notebookId
            val mappedTopic = page.topicId?.let { topicIdMap[it] ?: it }
            val mappedSub = page.subtopicId?.let { subIdMap[it] ?: it }
            val targetId = if (page.id.isBlank() || page.id in existingPageIds) newId("page_restored") else page.id
            val title = if (page.id in existingPageIds) "${page.title} (Restored)" else page.title
            ops += root.collection("pages").document(targetId) to page.copy(
                id = targetId,
                notebookId = mappedNb,
                topicId = mappedTopic,
                subtopicId = mappedSub,
                title = title
            ).toCreateMap(uid)
        }

        attachments.forEach { att ->
            val targetId = newId("att_restored")
            ops += root.collection("attachments").document(targetId) to att.copy(id = targetId).toCreateMap(uid)
        }
        fitnessLogs.forEach { fit ->
            val targetId = newId("fit_restored")
            ops += root.collection("fitnessLogs").document(targetId) to fit.copy(id = targetId).toCreateMap(uid)
        }
        vocabulary.forEach { voc ->
            val targetId = newId("voc_restored")
            ops += root.collection("vocabulary").document(targetId) to voc.copy(id = targetId).toCreateMap(uid)
        }
        todos.forEach { todo ->
            val targetId = newId("todo_restored")
            ops += root.collection("todos").document(targetId) to todo.copy(id = targetId).toCreateMap(uid)
        }

        ops.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, map) -> batch.set(ref, map) }
            batch.commit().await()
        }
        Result.success(ops.size)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "users/$userId/restore")
        Result.failure(e)
    }
}
