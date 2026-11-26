package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.platform.ExportBackupManager
import com.example.data.model.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app metadata strings resolve cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        val dbId = context.getString(R.string.firestore_database_id)
        assertEquals("Knowledge Library", appName)
        assertTrue(dbId.isNotBlank())
    }

    @Test
    fun `seed data provisions all 13 exact notebooks and required topics`() {
        val notebooks = SeedData.defaultNotebooks()
        assertEquals(13, notebooks.size)
        assertEquals(
            listOf(
                "PERSONALLY GENERAL",
                "WORD MEANINGS",
                "HOW TO SPEAK ENGLISH",
                "SOCIAL SKILLS & OTHER SKILLS",
                "FITNESS TRACKER",
                "SOCIAL MEDIA",
                "SAVED WORK",
                "HANDWRITING",
                "TO DO",
                "FINANCE",
                "LIFE LESSONS",
                "OTHER",
                "ROUGH"
            ),
            notebooks.map { it.name }
        )

        val topics = SeedData.defaultTopics()
        val lifeLessonTopics = topics.filter { it.notebookId == "nb_11" }
        assertEquals(21, lifeLessonTopics.size)
        assertTrue(topics.any { it.notebookId == "nb_01" && it.name == "Gut Microbiome" })
        assertTrue(
            topics.any {
                it.notebookId == "nb_03" &&
                    it.name == "Sentence Formation" &&
                    it.purpose.contains("DF Sir")
            }
        )
        assertTrue(topics.any { it.notebookId == "nb_04" && it.name == "Working Memory" })
    }

    @Test
    fun `json backup export and restore parse preserves unicode Hindi and English content`() {
        val notebooks = SeedData.defaultNotebooks()
        val topics = SeedData.defaultTopics()
        val subtopics = SeedData.defaultSubtopics()
        val pages = SeedData.defaultPages()
        val vocab = SeedData.defaultVocabulary()
        val fitness = SeedData.defaultFitnessLogs()
        val todos = SeedData.defaultTodos()
        val attachments = SeedData.defaultSavedWork()

        val json = ExportBackupManager.buildBackupJsonString(
            notebooks = notebooks,
            topics = topics,
            subtopics = subtopics,
            pages = pages,
            attachments = attachments,
            fitnessLogs = fitness,
            vocabulary = vocab,
            todos = todos
        )

        val parsed = ExportBackupManager.parseBackupJson(json)
        assertEquals(notebooks.size, parsed.notebooks.size)
        assertEquals(topics.size, parsed.topics.size)
        assertEquals(subtopics.size, parsed.subtopics.size)
        assertEquals(pages.size, parsed.pages.size)
        assertFalse(parsed.vocabulary.firstOrNull()?.hindiMeaning.isNullOrBlank())
        assertTrue(parsed.pages.any { it.content.contains("आज का लक्ष्य") })
    }
}
