package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class SaveStatus(val label: String) {
    SAVED("Saved"),
    SAVING("Saving…"),
    OFFLINE_PENDING("Offline / Sync Pending"),
    ERROR("Save Error")
}

@Keep
@IgnoreExtraProperties
data class NotebookEntity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val notebookNumber: Int = 1,
    val name: String = "",
    val purpose: String = "",
    val shortDescription: String = "",
    val detailedDescription: String = "",
    val iconKey: String = "menu_book",
    val coverColorHex: Long = 0xFF1B4332L,
    val coverStyle: String = "LEATHER",
    val sortOrder: Int = 0,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val hidden: Boolean = false,
    val systemDefault: Boolean = false,
    val notebookType: String = "CUSTOM",
    val lastOpenedAt: Timestamp? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "notebookNumber" to notebookNumber,
        "name" to name.take(120),
        "purpose" to purpose.take(500),
        "shortDescription" to shortDescription.take(500),
        "detailedDescription" to detailedDescription.take(4000),
        "iconKey" to iconKey,
        "coverColorHex" to coverColorHex,
        "coverStyle" to coverStyle,
        "sortOrder" to sortOrder,
        "pinned" to pinned,
        "archived" to archived,
        "hidden" to hidden,
        "systemDefault" to systemDefault,
        "notebookType" to notebookType,
        "lastOpenedAt" to lastOpenedAt,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class TopicEntity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val notebookId: String = "",
    val name: String = "",
    val shortDescription: String = "",
    val purpose: String = "",
    val introduction: String = "",
    val coversItems: List<String> = emptyList(),
    val importantConcepts: List<String> = emptyList(),
    val relatedTopics: List<String> = emptyList(),
    val personalNotes: String = "",
    val personalObservations: String = "",
    val personalExamples: String = "",
    val aiGeneratedInfo: String = "",
    val referenceLinks: List<String> = emptyList(),
    val iconKey: String = "article",
    val sortOrder: Int = 0,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "notebookId" to notebookId,
        "name" to name.take(160),
        "shortDescription" to shortDescription.take(500),
        "purpose" to purpose.take(1000),
        "introduction" to introduction.take(8000),
        "coversItems" to coversItems.take(60),
        "importantConcepts" to importantConcepts.take(60),
        "relatedTopics" to relatedTopics.take(40),
        "personalNotes" to personalNotes.take(50000),
        "personalObservations" to personalObservations.take(50000),
        "personalExamples" to personalExamples.take(50000),
        "aiGeneratedInfo" to aiGeneratedInfo.take(50000),
        "referenceLinks" to referenceLinks.take(30),
        "iconKey" to iconKey,
        "sortOrder" to sortOrder,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class SubtopicEntity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val notebookId: String = "",
    val topicId: String = "",
    val name: String = "",
    val shortDescription: String = "",
    val purpose: String = "",
    val introduction: String = "",
    val coversItems: List<String> = emptyList(),
    val importantConcepts: List<String> = emptyList(),
    val relatedTopics: List<String> = emptyList(),
    val personalNotes: String = "",
    val aiGeneratedInfo: String = "",
    val sortOrder: Int = 0,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "notebookId" to notebookId,
        "topicId" to topicId,
        "name" to name.take(160),
        "shortDescription" to shortDescription.take(500),
        "purpose" to purpose.take(1000),
        "introduction" to introduction.take(8000),
        "coversItems" to coversItems.take(60),
        "importantConcepts" to importantConcepts.take(60),
        "relatedTopics" to relatedTopics.take(40),
        "personalNotes" to personalNotes.take(50000),
        "aiGeneratedInfo" to aiGeneratedInfo.take(50000),
        "sortOrder" to sortOrder,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class PageEntity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val notebookId: String = "",
    val topicId: String? = null,
    val subtopicId: String? = null,
    val title: String = "Page 1",
    val content: String = "",
    val pageNumber: Int = 1,
    val handwritingMode: Boolean = false,
    val fontKey: String = "lora",
    val fontSizeSp: Int = 17,
    val inkColorHex: Long = 0xFF1C2430L,
    val lineSpacingMultiplier: Double = 1.5,
    val textAlignment: String = "START",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val paperColorKey: String = "IVORY",
    val aiNotes: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "notebookId" to notebookId,
        "topicId" to topicId?.takeIf { it.isNotBlank() },
        "subtopicId" to subtopicId?.takeIf { it.isNotBlank() },
        "title" to title.ifBlank { "Untitled Page" }.take(200),
        "content" to content.take(200000),
        "pageNumber" to pageNumber.coerceIn(1, 99999),
        "handwritingMode" to handwritingMode,
        "fontKey" to fontKey,
        "fontSizeSp" to fontSizeSp.coerceIn(10, 36),
        "inkColorHex" to inkColorHex,
        "lineSpacingMultiplier" to lineSpacingMultiplier.coerceIn(1.0, 3.0),
        "textAlignment" to textAlignment,
        "bold" to bold,
        "italic" to italic,
        "paperColorKey" to paperColorKey,
        "aiNotes" to aiNotes.take(50000),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class AttachmentEntity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val notebookId: String = "",
    val topicId: String? = null,
    val pageId: String? = null,
    val fileName: String = "",
    val fileType: String = "IMAGE",
    val localUriOrPath: String = "",
    val caption: String = "",
    val sourceLabel: String = "Saved Messages",
    val status: String = "SAVED",
    val priority: String = "MEDIUM",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "notebookId" to notebookId,
        "topicId" to topicId?.takeIf { it.isNotBlank() },
        "pageId" to pageId?.takeIf { it.isNotBlank() },
        "fileName" to fileName.ifBlank { "attachment" }.take(255),
        "fileType" to fileType,
        "localUriOrPath" to localUriOrPath.take(2048),
        "caption" to caption.take(2000),
        "sourceLabel" to sourceLabel.take(120),
        "status" to status,
        "priority" to priority,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class FitnessLogEntity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val notebookId: String = "nb_05",
    val exerciseName: String = "",
    val muscleGroup: String = "",
    val sets: Int = 3,
    val reps: String = "10, 10, 10",
    val weightKg: Double = 0.0,
    val rir: Int = 2,
    val rpe: Double = 8.0,
    val personalRecord: Boolean = false,
    val notes: String = "",
    val workoutDateMillis: Long = System.currentTimeMillis(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "notebookId" to notebookId,
        "exerciseName" to exerciseName.take(160),
        "muscleGroup" to muscleGroup.take(100),
        "sets" to sets.coerceIn(1, 100),
        "reps" to reps.ifBlank { "1" }.take(100),
        "weightKg" to weightKg.coerceIn(0.0, 2000.0),
        "rir" to rir.coerceIn(0, 10),
        "rpe" to rpe.coerceIn(1.0, 10.0),
        "personalRecord" to personalRecord,
        "notes" to notes.take(2000),
        "workoutDateMillis" to workoutDateMillis.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class VocabularyEntryEntity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val notebookId: String = "nb_02",
    val word: String = "",
    val pronunciation: String = "",
    val meaning: String = "",
    val hindiMeaning: String = "",
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val exampleSentences: List<String> = emptyList(),
    val category: String = "Daily Use English Words",
    val mastered: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "notebookId" to notebookId,
        "word" to word.take(120),
        "pronunciation" to pronunciation.take(120),
        "meaning" to meaning.take(2000),
        "hindiMeaning" to hindiMeaning.take(1000),
        "synonyms" to synonyms.take(25),
        "antonyms" to antonyms.take(25),
        "exampleSentences" to exampleSentences.take(20),
        "category" to category.take(120),
        "mastered" to mastered,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class TodoTaskEntity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val notebookId: String = "nb_09",
    val title: String = "",
    val description: String = "",
    val category: String = "TODAY",
    val priority: String = "MEDIUM",
    val completed: Boolean = false,
    val dueDateMillis: Long? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "notebookId" to notebookId,
        "title" to title.take(300),
        "description" to description.take(4000),
        "category" to category,
        "priority" to priority,
        "completed" to completed,
        "dueDateMillis" to dueDateMillis?.takeIf { it >= 0L },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@Keep
@IgnoreExtraProperties
data class AppPreferencesEntity(
    @DocumentId val id: String = "user_settings",
    val userId: String = "",
    val customAppName: String = "Knowledge Library",
    val themeMode: String = "SYSTEM",
    val accentColorKey: String = "FOREST",
    val uiFontKey: String = "plus_jakarta_sans",
    val writingFontKey: String = "lora",
    val handwritingFontKey: String = "kalam",
    val defaultFontSizeSp: Int = 17,
    val paperColorKey: String = "IVORY",
    val ruledLineColorKey: String = "BLUE",
    val lineSpacingMultiplier: Double = 1.5,
    val defaultCoverStyle: String = "LEATHER",
    val dashboardLayout: String = "GRID",
    val showArchivedOnDashboard: Boolean = false,
    val preferredLanguage: String = "MIXED",
    val voiceInputLanguage: String = "en-IN",
    val aiEnabled: Boolean = true,
    val aiResponseLanguage: String = "MIXED",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "customAppName" to customAppName.ifBlank { "Knowledge Library" }.take(40),
        "themeMode" to themeMode,
        "accentColorKey" to accentColorKey,
        "uiFontKey" to uiFontKey,
        "writingFontKey" to writingFontKey,
        "handwritingFontKey" to handwritingFontKey,
        "defaultFontSizeSp" to defaultFontSizeSp.coerceIn(12, 32),
        "paperColorKey" to paperColorKey,
        "ruledLineColorKey" to ruledLineColorKey,
        "lineSpacingMultiplier" to lineSpacingMultiplier.coerceIn(1.0, 3.0),
        "defaultCoverStyle" to defaultCoverStyle,
        "dashboardLayout" to dashboardLayout,
        "showArchivedOnDashboard" to showArchivedOnDashboard,
        "preferredLanguage" to preferredLanguage,
        "voiceInputLanguage" to voiceInputLanguage,
        "aiEnabled" to aiEnabled,
        "aiResponseLanguage" to aiResponseLanguage,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

data class GlobalSearchResult(
    val id: String,
    val kind: SearchResultKind,
    val title: String,
    val snippet: String,
    val notebookId: String,
    val notebookName: String,
    val topicId: String? = null,
    val topicName: String? = null,
    val subtopicId: String? = null,
    val pageId: String? = null
)

enum class SearchResultKind(val label: String) {
    NOTEBOOK("Notebook"),
    TOPIC("Topic"),
    SUBTOPIC("Subtopic"),
    PAGE("Page"),
    VOCABULARY("Vocabulary"),
    SAVED_WORK("Saved Work")
}
