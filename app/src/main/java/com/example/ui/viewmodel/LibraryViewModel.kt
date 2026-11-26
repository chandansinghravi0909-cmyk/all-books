package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.platform.ExportBackupManager
import com.example.data.model.AppPreferencesEntity
import com.example.data.model.AttachmentEntity
import com.example.data.model.FitnessLogEntity
import com.example.data.model.GlobalSearchResult
import com.example.data.model.NotebookEntity
import com.example.data.model.PageEntity
import com.example.data.model.SearchResultKind
import com.example.data.model.SubtopicEntity
import com.example.data.model.TodoTaskEntity
import com.example.data.model.TopicEntity
import com.example.data.model.UiState
import com.example.data.model.VocabularyEntryEntity
import com.example.data.repository.LibraryRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class LibrarySnapshot(
    val notebooks: List<NotebookEntity> = emptyList(),
    val topics: List<TopicEntity> = emptyList(),
    val subtopics: List<SubtopicEntity> = emptyList(),
    val pages: List<PageEntity> = emptyList(),
    val attachments: List<AttachmentEntity> = emptyList(),
    val fitnessLogs: List<FitnessLogEntity> = emptyList(),
    val vocabulary: List<VocabularyEntryEntity> = emptyList(),
    val todos: List<TodoTaskEntity> = emptyList(),
    val preferences: AppPreferencesEntity = AppPreferencesEntity()
)

class LibraryViewModel(
    private val repository: LibraryRepository,
    val currentUserId: String
) : ViewModel() {

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val coreCollectionsFlow = combine(
        repository.observeNotebooks(currentUserId),
        repository.observeTopics(currentUserId),
        repository.observeSubtopics(currentUserId),
        repository.observePages(currentUserId),
        repository.observePreferences(currentUserId)
    ) { notebooks, topics, subtopics, pages, prefs ->
        CoreBundle(notebooks, topics, subtopics, pages, prefs)
    }

    private val specializedCollectionsFlow = combine(
        repository.observeAttachments(currentUserId),
        repository.observeFitnessLogs(currentUserId),
        repository.observeVocabulary(currentUserId),
        repository.observeTodos(currentUserId)
    ) { attachments, fitness, vocab, todos ->
        SpecializedBundle(attachments, fitness, vocab, todos)
    }

    val libraryState: StateFlow<UiState<LibrarySnapshot>> = combine(
        coreCollectionsFlow,
        specializedCollectionsFlow
    ) { core, spec ->
        LibrarySnapshot(
            notebooks = core.notebooks,
            topics = core.topics,
            subtopics = core.subtopics,
            pages = core.pages,
            attachments = spec.attachments,
            fitnessLogs = spec.fitness,
            vocabulary = spec.vocab,
            todos = spec.todos,
            preferences = core.prefs
        )
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .map<LibrarySnapshot, UiState<LibrarySnapshot>> { UiState.Success(it) }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.e(TAG, "Error observing library", root)
            emit(UiState.Error(root.message ?: "Failed to load Knowledge Library"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    init {
        viewModelScope.launch {
            repository.ensureInitialSeed(currentUserId).onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Unable to initialize starter notebooks."
            }
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun postMessage(msg: String) {
        _statusBanner.value = msg
    }

    fun searchGlobally(query: String, snapshot: LibrarySnapshot): List<GlobalSearchResult> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return emptyList()
        val nbMap = snapshot.notebooks.associateBy { it.id }
        val topicMap = snapshot.topics.associateBy { it.id }
        val results = mutableListOf<GlobalSearchResult>()

        snapshot.notebooks.forEach { nb ->
            if (nb.name.lowercase().contains(q) ||
                nb.purpose.lowercase().contains(q) ||
                nb.shortDescription.lowercase().contains(q) ||
                nb.detailedDescription.lowercase().contains(q)
            ) {
                results += GlobalSearchResult(
                    id = "nb_${nb.id}",
                    kind = SearchResultKind.NOTEBOOK,
                    title = "Notebook ${nb.notebookNumber}: ${nb.name}",
                    snippet = nb.purpose.ifBlank { nb.shortDescription },
                    notebookId = nb.id,
                    notebookName = nb.name
                )
            }
        }

        snapshot.topics.forEach { t ->
            val nbName = nbMap[t.notebookId]?.name ?: "Notebook"
            if (t.name.lowercase().contains(q) ||
                t.shortDescription.lowercase().contains(q) ||
                t.purpose.lowercase().contains(q) ||
                t.introduction.lowercase().contains(q) ||
                t.personalNotes.lowercase().contains(q)
            ) {
                results += GlobalSearchResult(
                    id = "topic_${t.id}",
                    kind = SearchResultKind.TOPIC,
                    title = t.name,
                    snippet = t.shortDescription.ifBlank { t.purpose },
                    notebookId = t.notebookId,
                    notebookName = nbName,
                    topicId = t.id,
                    topicName = t.name
                )
            }
        }

        snapshot.subtopics.forEach { s ->
            val nbName = nbMap[s.notebookId]?.name ?: "Notebook"
            val tName = topicMap[s.topicId]?.name
            if (s.name.lowercase().contains(q) ||
                s.shortDescription.lowercase().contains(q) ||
                s.introduction.lowercase().contains(q) ||
                s.personalNotes.lowercase().contains(q)
            ) {
                results += GlobalSearchResult(
                    id = "sub_${s.id}",
                    kind = SearchResultKind.SUBTOPIC,
                    title = s.name,
                    snippet = s.shortDescription.ifBlank { s.purpose },
                    notebookId = s.notebookId,
                    notebookName = nbName,
                    topicId = s.topicId,
                    topicName = tName,
                    subtopicId = s.id
                )
            }
        }

        snapshot.pages.forEach { p ->
            val nbName = nbMap[p.notebookId]?.name ?: "Notebook"
            val tName = p.topicId?.let { topicMap[it]?.name }
            if (p.title.lowercase().contains(q) || p.content.lowercase().contains(q)) {
                results += GlobalSearchResult(
                    id = "page_${p.id}",
                    kind = SearchResultKind.PAGE,
                    title = "Page ${p.pageNumber}: ${p.title}",
                    snippet = p.content.lineSequence().firstOrNull { it.lowercase().contains(q) }
                        ?: p.content.take(100),
                    notebookId = p.notebookId,
                    notebookName = nbName,
                    topicId = p.topicId,
                    topicName = tName,
                    subtopicId = p.subtopicId,
                    pageId = p.id
                )
            }
        }

        snapshot.vocabulary.forEach { v ->
            val nbName = nbMap[v.notebookId]?.name ?: "WORD MEANINGS"
            if (v.word.lowercase().contains(q) ||
                v.meaning.lowercase().contains(q) ||
                v.hindiMeaning.lowercase().contains(q)
            ) {
                results += GlobalSearchResult(
                    id = "voc_${v.id}",
                    kind = SearchResultKind.VOCABULARY,
                    title = "${v.word} ${v.pronunciation}".trim(),
                    snippet = "${v.meaning} • ${v.hindiMeaning}".trim(' ', '•'),
                    notebookId = v.notebookId,
                    notebookName = nbName
                )
            }
        }

        snapshot.attachments.forEach { a ->
            val nbName = nbMap[a.notebookId]?.name ?: "SAVED WORK"
            if (a.fileName.lowercase().contains(q) ||
                a.caption.lowercase().contains(q) ||
                a.sourceLabel.lowercase().contains(q)
            ) {
                results += GlobalSearchResult(
                    id = "att_${a.id}",
                    kind = SearchResultKind.SAVED_WORK,
                    title = a.fileName,
                    snippet = "${a.sourceLabel} • ${a.caption}".trim(' ', '•'),
                    notebookId = a.notebookId,
                    notebookName = nbName
                )
            }
        }

        return results.take(50)
    }

    // --- Notebook Actions ---

    fun markNotebookOpened(notebookId: String) {
        viewModelScope.launch {
            repository.updateNotebook(
                currentUserId,
                notebookId,
                mapOf("lastOpenedAt" to FieldValue.serverTimestamp())
            )
        }
    }

    fun createNotebook(
        name: String,
        purpose: String,
        shortDescription: String,
        detailedDescription: String,
        iconKey: String,
        coverColorHex: Long,
        coverStyle: String,
        existingCount: Int,
        onCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val entity = NotebookEntity(
                notebookNumber = existingCount + 1,
                name = name.trim(),
                purpose = purpose.trim(),
                shortDescription = shortDescription.trim(),
                detailedDescription = detailedDescription.trim(),
                iconKey = iconKey,
                coverColorHex = coverColorHex,
                coverStyle = coverStyle,
                sortOrder = existingCount + 1,
                pinned = false,
                systemDefault = false,
                notebookType = "CUSTOM"
            )
            repository.createNotebook(currentUserId, entity)
                .onSuccess {
                    _statusBanner.value = "Notebook \"$name\" created."
                    onCreated(it)
                }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to create notebook." }
        }
    }

    fun updateNotebookDetails(
        notebookId: String,
        name: String,
        purpose: String,
        shortDescription: String,
        detailedDescription: String,
        iconKey: String,
        coverColorHex: Long,
        coverStyle: String
    ) {
        viewModelScope.launch {
            repository.updateNotebook(
                currentUserId,
                notebookId,
                mapOf(
                    "name" to name.trim().take(120),
                    "purpose" to purpose.trim().take(500),
                    "shortDescription" to shortDescription.trim().take(500),
                    "detailedDescription" to detailedDescription.trim().take(4000),
                    "iconKey" to iconKey,
                    "coverColorHex" to coverColorHex,
                    "coverStyle" to coverStyle
                )
            ).onSuccess {
                _statusBanner.value = "Notebook updated."
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to update notebook."
            }
        }
    }

    fun togglePinNotebook(notebook: NotebookEntity) {
        viewModelScope.launch {
            repository.updateNotebook(
                currentUserId,
                notebook.id,
                mapOf("pinned" to !notebook.pinned)
            )
        }
    }

    fun toggleArchiveNotebook(notebook: NotebookEntity) {
        viewModelScope.launch {
            repository.updateNotebook(
                currentUserId,
                notebook.id,
                mapOf("archived" to !notebook.archived)
            ).onSuccess {
                _statusBanner.value = if (!notebook.archived) "Notebook archived." else "Notebook restored."
            }
        }
    }

    fun toggleHideNotebook(notebook: NotebookEntity) {
        viewModelScope.launch {
            repository.updateNotebook(
                currentUserId,
                notebook.id,
                mapOf("hidden" to !notebook.hidden)
            )
        }
    }

    fun swapNotebookOrder(first: NotebookEntity, second: NotebookEntity) {
        viewModelScope.launch {
            repository.updateNotebook(currentUserId, first.id, mapOf("sortOrder" to second.sortOrder))
            repository.updateNotebook(currentUserId, second.id, mapOf("sortOrder" to first.sortOrder))
        }
    }

    fun deleteNotebook(notebookId: String) {
        viewModelScope.launch {
            repository.deleteNotebook(currentUserId, notebookId)
                .onSuccess { _statusBanner.value = "Notebook deleted." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to delete notebook." }
        }
    }

    // --- Topic Actions ---

    fun createTopic(
        topic: TopicEntity,
        starterSubtopicNames: List<String> = emptyList(),
        onCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.createTopic(currentUserId, topic)
                .onSuccess { topicId ->
                    starterSubtopicNames.filter { it.isNotBlank() }.forEachIndexed { idx, subName ->
                        repository.createSubtopic(
                            currentUserId,
                            SubtopicEntity(
                                notebookId = topic.notebookId,
                                topicId = topicId,
                                name = subName.trim(),
                                shortDescription = "Subtopic under ${topic.name}",
                                purpose = "Explore $subName in depth.",
                                sortOrder = idx
                            )
                        )
                    }
                    _statusBanner.value = "Topic \"${topic.name}\" created."
                    onCreated(topicId)
                }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to create topic." }
        }
    }

    fun updateTopicDefaultInfo(
        topicId: String,
        name: String,
        shortDescription: String,
        purpose: String,
        introduction: String,
        coversItems: List<String>,
        importantConcepts: List<String>,
        relatedTopics: List<String>
    ) {
        viewModelScope.launch {
            repository.updateTopic(
                currentUserId,
                topicId,
                mapOf(
                    "name" to name.trim().take(160),
                    "shortDescription" to shortDescription.trim().take(500),
                    "purpose" to purpose.trim().take(1000),
                    "introduction" to introduction.trim().take(8000),
                    "coversItems" to coversItems.take(60),
                    "importantConcepts" to importantConcepts.take(60),
                    "relatedTopics" to relatedTopics.take(40)
                )
            ).onSuccess {
                _statusBanner.value = "Topic information saved (personal notes preserved)."
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to update topic."
            }
        }
    }

    fun updateTopicPersonalSection(
        topicId: String,
        personalNotes: String,
        personalObservations: String,
        personalExamples: String,
        referenceLinks: List<String>
    ) {
        viewModelScope.launch {
            repository.updateTopic(
                currentUserId,
                topicId,
                mapOf(
                    "personalNotes" to personalNotes.take(50000),
                    "personalObservations" to personalObservations.take(50000),
                    "personalExamples" to personalExamples.take(50000),
                    "referenceLinks" to referenceLinks.take(30)
                )
            ).onSuccess {
                _statusBanner.value = "Personal notes saved."
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to save personal notes."
            }
        }
    }

    fun saveAiInfoToTopic(topicId: String, aiText: String) {
        viewModelScope.launch {
            repository.updateTopic(
                currentUserId,
                topicId,
                mapOf("aiGeneratedInfo" to aiText.take(50000))
            ).onSuccess {
                _statusBanner.value = "Saved to AI Information section."
            }
        }
    }

    fun swapTopicOrder(first: TopicEntity, second: TopicEntity) {
        viewModelScope.launch {
            repository.updateTopic(currentUserId, first.id, mapOf("sortOrder" to second.sortOrder))
            repository.updateTopic(currentUserId, second.id, mapOf("sortOrder" to first.sortOrder))
        }
    }

    fun deleteTopic(topicId: String) {
        viewModelScope.launch {
            repository.deleteTopic(currentUserId, topicId)
                .onSuccess { _statusBanner.value = "Topic deleted." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to delete topic." }
        }
    }

    // --- Subtopic Actions ---

    fun createSubtopic(subtopic: SubtopicEntity, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            repository.createSubtopic(currentUserId, subtopic)
                .onSuccess {
                    _statusBanner.value = "Subtopic \"${subtopic.name}\" created."
                    onCreated(it)
                }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to create subtopic." }
        }
    }

    fun updateSubtopicInfo(
        subtopicId: String,
        name: String,
        shortDescription: String,
        purpose: String,
        introduction: String,
        personalNotes: String
    ) {
        viewModelScope.launch {
            repository.updateSubtopic(
                currentUserId,
                subtopicId,
                mapOf(
                    "name" to name.trim().take(160),
                    "shortDescription" to shortDescription.trim().take(500),
                    "purpose" to purpose.trim().take(1000),
                    "introduction" to introduction.trim().take(8000),
                    "personalNotes" to personalNotes.take(50000)
                )
            ).onSuccess { _statusBanner.value = "Subtopic updated." }
        }
    }

    fun saveAiInfoToSubtopic(subtopicId: String, aiText: String) {
        viewModelScope.launch {
            repository.updateSubtopic(
                currentUserId,
                subtopicId,
                mapOf("aiGeneratedInfo" to aiText.take(50000))
            ).onSuccess { _statusBanner.value = "Saved to Subtopic AI Information." }
        }
    }

    fun deleteSubtopic(subtopicId: String) {
        viewModelScope.launch {
            repository.deleteSubtopic(currentUserId, subtopicId)
                .onSuccess { _statusBanner.value = "Subtopic deleted." }
        }
    }

    // --- Page Actions ---

    fun createPage(page: PageEntity, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            repository.createPage(currentUserId, page)
                .onSuccess { onCreated(it) }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to create page." }
        }
    }

    suspend fun savePageFieldsSuspend(pageId: String, fields: Map<String, Any>): Result<Unit> =
        repository.updatePage(currentUserId, pageId, fields)

    suspend fun createPageSuspend(page: PageEntity): Result<String> =
        repository.createPage(currentUserId, page)

    suspend fun deletePageSuspend(pageId: String): Result<Unit> =
        repository.deletePage(currentUserId, pageId)

    suspend fun createTopicSuspend(
        topic: TopicEntity,
        starterSubtopicNames: List<String> = emptyList()
    ): Result<String> {
        val result = repository.createTopic(currentUserId, topic)
        result.onSuccess { topicId ->
            starterSubtopicNames.filter { it.isNotBlank() }.forEachIndexed { idx, subName ->
                repository.createSubtopic(
                    currentUserId,
                    SubtopicEntity(
                        notebookId = topic.notebookId,
                        topicId = topicId,
                        name = subName.trim(),
                        shortDescription = "Subtopic under ${topic.name}",
                        purpose = "Explore $subName in depth.",
                        sortOrder = idx
                    )
                )
            }
        }
        return result
    }

    suspend fun createSubtopicSuspend(subtopic: SubtopicEntity): Result<String> =
        repository.createSubtopic(currentUserId, subtopic)

    fun renamePage(pageId: String, newTitle: String) {
        viewModelScope.launch {
            repository.updatePage(
                currentUserId,
                pageId,
                mapOf("title" to newTitle.ifBlank { "Untitled Page" }.take(200))
            )
        }
    }

    fun duplicatePage(page: PageEntity, nextNumber: Int, onCreated: (String) -> Unit = {}) {
        val copy = page.copy(
            id = "",
            title = "${page.title} (Copy)".take(200),
            pageNumber = nextNumber
        )
        createPage(copy, onCreated)
    }

    fun swapPageOrder(first: PageEntity, second: PageEntity) {
        viewModelScope.launch {
            repository.updatePage(currentUserId, first.id, mapOf("pageNumber" to second.pageNumber))
            repository.updatePage(currentUserId, second.id, mapOf("pageNumber" to first.pageNumber))
        }
    }

    fun deletePage(pageId: String) {
        viewModelScope.launch {
            repository.deletePage(currentUserId, pageId)
                .onSuccess { _statusBanner.value = "Page deleted." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to delete page." }
        }
    }

    fun deletePageAndRenumber(target: PageEntity, scopePages: List<PageEntity>) {
        viewModelScope.launch {
            repository.deletePage(currentUserId, target.id)
                .onSuccess {
                    val remaining = scopePages
                        .filter { it.id != target.id }
                        .sortedBy { it.pageNumber }
                    remaining.forEachIndexed { idx, page ->
                        val expected = idx + 1
                        if (page.pageNumber != expected) {
                            val updatedFields = mutableMapOf<String, Any>("pageNumber" to expected)
                            if (page.title == "Page ${page.pageNumber}") {
                                updatedFields["title"] = "Page $expected"
                            }
                            repository.updatePage(currentUserId, page.id, updatedFields)
                        }
                    }
                    _statusBanner.value = "Page deleted."
                }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to delete page." }
        }
    }

    // --- Specialized Actions ---

    fun createAttachment(attachment: AttachmentEntity) {
        viewModelScope.launch {
            repository.createAttachment(currentUserId, attachment)
                .onSuccess { _statusBanner.value = "Saved item added." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to save item." }
        }
    }

    fun updateAttachmentStatus(attachmentId: String, status: String) {
        viewModelScope.launch {
            repository.updateAttachment(currentUserId, attachmentId, mapOf("status" to status))
        }
    }

    fun deleteAttachment(attachmentId: String) {
        viewModelScope.launch {
            repository.deleteAttachment(currentUserId, attachmentId)
        }
    }

    fun createFitnessLog(log: FitnessLogEntity) {
        viewModelScope.launch {
            repository.createFitnessLog(currentUserId, log)
                .onSuccess { _statusBanner.value = "Workout set logged." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to log workout." }
        }
    }

    fun deleteFitnessLog(logId: String) {
        viewModelScope.launch {
            repository.deleteFitnessLog(currentUserId, logId)
        }
    }

    fun createVocabulary(entry: VocabularyEntryEntity) {
        viewModelScope.launch {
            repository.createVocabulary(currentUserId, entry)
                .onSuccess { _statusBanner.value = "Word \"${entry.word}\" saved." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to save word." }
        }
    }

    fun toggleVocabularyMastered(entry: VocabularyEntryEntity) {
        viewModelScope.launch {
            repository.updateVocabulary(currentUserId, entry.id, mapOf("mastered" to !entry.mastered))
        }
    }

    fun deleteVocabulary(entryId: String) {
        viewModelScope.launch {
            repository.deleteVocabulary(currentUserId, entryId)
        }
    }

    fun createTodo(todo: TodoTaskEntity) {
        viewModelScope.launch {
            repository.createTodo(currentUserId, todo)
                .onSuccess { _statusBanner.value = "Task added." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to add task." }
        }
    }

    fun toggleTodoCompleted(todo: TodoTaskEntity) {
        viewModelScope.launch {
            repository.updateTodo(currentUserId, todo.id, mapOf("completed" to !todo.completed))
        }
    }

    fun deleteTodo(todoId: String) {
        viewModelScope.launch {
            repository.deleteTodo(currentUserId, todoId)
        }
    }

    // --- Preferences & Backup Restore ---

    fun savePreferences(prefs: AppPreferencesEntity) {
        viewModelScope.launch {
            repository.savePreferences(currentUserId, prefs)
                .onSuccess { _statusBanner.value = "Settings saved permanently." }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to save settings." }
        }
    }

    fun restoreFromJson(jsonText: String, snapshot: LibrarySnapshot) {
        viewModelScope.launch {
            try {
                val parsed = ExportBackupManager.parseBackupJson(jsonText)
                repository.restoreBackup(
                    userId = currentUserId,
                    notebooks = parsed.notebooks,
                    topics = parsed.topics,
                    subtopics = parsed.subtopics,
                    pages = parsed.pages,
                    attachments = parsed.attachments,
                    fitnessLogs = parsed.fitnessLogs,
                    vocabulary = parsed.vocabulary,
                    todos = parsed.todos,
                    existingNotebookIds = snapshot.notebooks.map { it.id }.toSet(),
                    existingTopicIds = snapshot.topics.map { it.id }.toSet(),
                    existingSubtopicIds = snapshot.subtopics.map { it.id }.toSet(),
                    existingPageIds = snapshot.pages.map { it.id }.toSet()
                ).onSuccess { count ->
                    _statusBanner.value = "Restored $count items safely without overwriting existing notebooks or topics."
                }.onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Restore failed."
                }
            } catch (e: Exception) {
                _statusBanner.value = "Invalid JSON backup file: ${e.localizedMessage}"
            }
        }
    }

    private data class CoreBundle(
        val notebooks: List<NotebookEntity>,
        val topics: List<TopicEntity>,
        val subtopics: List<SubtopicEntity>,
        val pages: List<PageEntity>,
        val prefs: AppPreferencesEntity
    )

    private data class SpecializedBundle(
        val attachments: List<AttachmentEntity>,
        val fitness: List<FitnessLogEntity>,
        val vocab: List<VocabularyEntryEntity>,
        val todos: List<TodoTaskEntity>
    )

    private companion object {
        const val TAG = "LibraryVM"
    }
}
