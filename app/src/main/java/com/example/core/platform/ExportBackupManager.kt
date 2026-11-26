package com.example.core.platform

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.model.AttachmentEntity
import com.example.data.model.FitnessLogEntity
import com.example.data.model.NotebookEntity
import com.example.data.model.PageEntity
import com.example.data.model.SubtopicEntity
import com.example.data.model.TodoTaskEntity
import com.example.data.model.TopicEntity
import com.example.data.model.VocabularyEntryEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ParsedBackupData(
    val notebooks: List<NotebookEntity>,
    val topics: List<TopicEntity>,
    val subtopics: List<SubtopicEntity>,
    val pages: List<PageEntity>,
    val attachments: List<AttachmentEntity>,
    val fitnessLogs: List<FitnessLogEntity>,
    val vocabulary: List<VocabularyEntryEntity>,
    val todos: List<TodoTaskEntity>
)

object ExportBackupManager {

    private fun exportsDir(context: Context): File {
        val dir = File(context.filesDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun safeName(raw: String): String =
        raw.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "export" }
            .take(48)

    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Export ${file.name}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun exportPageAsText(context: Context, notebookName: String, page: PageEntity): File {
        val file = File(exportsDir(context), "${safeName(page.title)}_p${page.pageNumber}.txt")
        val content = buildString {
            appendLine("$notebookName — Page ${page.pageNumber}: ${page.title}")
            appendLine("=".repeat(50))
            appendLine()
            appendLine(page.content)
        }
        file.writeText(content)
        return file
    }

    fun exportPageAsMarkdown(context: Context, notebookName: String, topicName: String?, page: PageEntity): File {
        val file = File(exportsDir(context), "${safeName(page.title)}_p${page.pageNumber}.md")
        val content = buildString {
            appendLine("# ${page.title}")
            appendLine()
            appendLine("**Notebook:** $notebookName  ")
            if (!topicName.isNullOrBlank()) appendLine("**Topic:** $topicName  ")
            appendLine("**Page:** ${page.pageNumber}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine(page.content)
        }
        file.writeText(content)
        return file
    }

    fun exportPageAsPdf(context: Context, notebookName: String, page: PageEntity): File {
        val file = File(exportsDir(context), "${safeName(page.title)}_p${page.pageNumber}.pdf")
        writeA4RuledPdf(
            file = file,
            sections = listOf(
                "$notebookName • Page ${page.pageNumber}: ${page.title}" to page.content
            )
        )
        return file
    }

    fun exportTopicAsMarkdown(
        context: Context,
        notebookName: String,
        topic: TopicEntity,
        subtopics: List<SubtopicEntity>,
        pages: List<PageEntity>
    ): File {
        val file = File(exportsDir(context), "${safeName(topic.name)}.md")
        val content = buildString {
            appendLine("# ${topic.name}")
            appendLine("**Notebook:** $notebookName")
            appendLine()
            if (topic.purpose.isNotBlank()) appendLine("## Purpose\n${topic.purpose}\n")
            if (topic.introduction.isNotBlank()) appendLine("## Introduction\n${topic.introduction}\n")
            if (topic.coversItems.isNotEmpty()) {
                appendLine("## What This Topic Covers")
                topic.coversItems.forEach { appendLine("- $it") }
                appendLine()
            }
            if (topic.importantConcepts.isNotEmpty()) {
                appendLine("## Important Concepts")
                topic.importantConcepts.forEach { appendLine("- $it") }
                appendLine()
            }
            if (topic.personalNotes.isNotBlank()) {
                appendLine("## My Personal Notes\n${topic.personalNotes}\n")
            }
            if (subtopics.isNotEmpty()) {
                appendLine("## Subtopics")
                subtopics.forEach { sub ->
                    appendLine("### ${sub.name}")
                    if (sub.purpose.isNotBlank()) appendLine("${sub.purpose}\n")
                    if (sub.personalNotes.isNotBlank()) appendLine("**Notes:** ${sub.personalNotes}\n")
                }
            }
            if (pages.isNotEmpty()) {
                appendLine("## Writing Pages")
                pages.forEach { p ->
                    appendLine("### Page ${p.pageNumber}: ${p.title}")
                    appendLine(p.content)
                    appendLine()
                }
            }
        }
        file.writeText(content)
        return file
    }

    fun exportNotebookAsPdf(
        context: Context,
        notebook: NotebookEntity,
        topics: List<TopicEntity>,
        pages: List<PageEntity>
    ): File {
        val file = File(exportsDir(context), "notebook_${notebook.notebookNumber}_${safeName(notebook.name)}.pdf")
        val sections = mutableListOf<Pair<String, String>>()
        sections += "NOTEBOOK ${notebook.notebookNumber} — ${notebook.name}" to
            "Purpose: ${notebook.purpose}\n\n${notebook.detailedDescription}"
        topics.forEach { topic ->
            val topicPages = pages.filter { it.topicId == topic.id }
            val body = buildString {
                if (topic.purpose.isNotBlank()) appendLine("Purpose: ${topic.purpose}")
                if (topic.introduction.isNotBlank()) appendLine("\n${topic.introduction}")
                if (topic.personalNotes.isNotBlank()) appendLine("\nPersonal Notes:\n${topic.personalNotes}")
                topicPages.forEach { p ->
                    appendLine("\n--- Page ${p.pageNumber}: ${p.title} ---\n${p.content}")
                }
            }
            sections += "Topic: ${topic.name}" to body
        }
        val loosePages = pages.filter { it.topicId.isNullOrBlank() }
        loosePages.forEach { p ->
            sections += "Page ${p.pageNumber}: ${p.title}" to p.content
        }
        writeA4RuledPdf(file, sections)
        return file
    }

    fun exportNotebookAsMarkdown(
        context: Context,
        notebook: NotebookEntity,
        topics: List<TopicEntity>,
        subtopics: List<SubtopicEntity>,
        pages: List<PageEntity>
    ): File {
        val file = File(exportsDir(context), "notebook_${notebook.notebookNumber}_${safeName(notebook.name)}.md")
        val content = buildString {
            appendLine("# Notebook ${notebook.notebookNumber}: ${notebook.name}")
            appendLine("**Purpose:** ${notebook.purpose}")
            appendLine()
            appendLine(notebook.detailedDescription)
            appendLine()
            topics.forEach { t ->
                appendLine("## ${t.name}")
                if (t.purpose.isNotBlank()) appendLine("*${t.purpose}*\n")
                if (t.introduction.isNotBlank()) appendLine("${t.introduction}\n")
                if (t.personalNotes.isNotBlank()) appendLine("### Personal Notes\n${t.personalNotes}\n")
                subtopics.filter { it.topicId == t.id }.forEach { s ->
                    appendLine("### Subtopic: ${s.name}\n${s.introduction}\n")
                }
                pages.filter { it.topicId == t.id }.forEach { p ->
                    appendLine("#### Page ${p.pageNumber}: ${p.title}\n${p.content}\n")
                }
            }
            val generalPages = pages.filter { it.topicId.isNullOrBlank() }
            if (generalPages.isNotEmpty()) {
                appendLine("## General Pages")
                generalPages.forEach { p ->
                    appendLine("### Page ${p.pageNumber}: ${p.title}\n${p.content}\n")
                }
            }
        }
        file.writeText(content)
        return file
    }

    fun exportFullLibraryJson(
        context: Context,
        notebooks: List<NotebookEntity>,
        topics: List<TopicEntity>,
        subtopics: List<SubtopicEntity>,
        pages: List<PageEntity>,
        attachments: List<AttachmentEntity>,
        fitnessLogs: List<FitnessLogEntity>,
        vocabulary: List<VocabularyEntryEntity>,
        todos: List<TodoTaskEntity>
    ): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val file = File(exportsDir(context), "knowledge_library_backup_$stamp.json")
        file.writeText(
            buildBackupJsonString(
                notebooks, topics, subtopics, pages, attachments, fitnessLogs, vocabulary, todos
            )
        )
        return file
    }

    fun buildBackupJsonString(
        notebooks: List<NotebookEntity>,
        topics: List<TopicEntity>,
        subtopics: List<SubtopicEntity>,
        pages: List<PageEntity>,
        attachments: List<AttachmentEntity>,
        fitnessLogs: List<FitnessLogEntity>,
        vocabulary: List<VocabularyEntryEntity>,
        todos: List<TodoTaskEntity>
    ): String {
        val root = JSONObject()
        root.put("backupVersion", 1)
        root.put("exportedAtMillis", System.currentTimeMillis())

        root.put("notebooks", JSONArray().apply {
            notebooks.forEach { nb ->
                put(JSONObject().apply {
                    put("id", nb.id)
                    put("notebookNumber", nb.notebookNumber)
                    put("name", nb.name)
                    put("purpose", nb.purpose)
                    put("shortDescription", nb.shortDescription)
                    put("detailedDescription", nb.detailedDescription)
                    put("iconKey", nb.iconKey)
                    put("coverColorHex", nb.coverColorHex)
                    put("coverStyle", nb.coverStyle)
                    put("sortOrder", nb.sortOrder)
                    put("pinned", nb.pinned)
                    put("archived", nb.archived)
                    put("hidden", nb.hidden)
                    put("systemDefault", nb.systemDefault)
                    put("notebookType", nb.notebookType)
                })
            }
        })

        root.put("topics", JSONArray().apply {
            topics.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("notebookId", t.notebookId)
                    put("name", t.name)
                    put("shortDescription", t.shortDescription)
                    put("purpose", t.purpose)
                    put("introduction", t.introduction)
                    put("coversItems", JSONArray(t.coversItems))
                    put("importantConcepts", JSONArray(t.importantConcepts))
                    put("relatedTopics", JSONArray(t.relatedTopics))
                    put("personalNotes", t.personalNotes)
                    put("personalObservations", t.personalObservations)
                    put("personalExamples", t.personalExamples)
                    put("aiGeneratedInfo", t.aiGeneratedInfo)
                    put("referenceLinks", JSONArray(t.referenceLinks))
                    put("iconKey", t.iconKey)
                    put("sortOrder", t.sortOrder)
                })
            }
        })

        root.put("subtopics", JSONArray().apply {
            subtopics.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("notebookId", s.notebookId)
                    put("topicId", s.topicId)
                    put("name", s.name)
                    put("shortDescription", s.shortDescription)
                    put("purpose", s.purpose)
                    put("introduction", s.introduction)
                    put("coversItems", JSONArray(s.coversItems))
                    put("importantConcepts", JSONArray(s.importantConcepts))
                    put("relatedTopics", JSONArray(s.relatedTopics))
                    put("personalNotes", s.personalNotes)
                    put("aiGeneratedInfo", s.aiGeneratedInfo)
                    put("sortOrder", s.sortOrder)
                })
            }
        })

        root.put("pages", JSONArray().apply {
            pages.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("notebookId", p.notebookId)
                    put("topicId", p.topicId ?: "")
                    put("subtopicId", p.subtopicId ?: "")
                    put("title", p.title)
                    put("content", p.content)
                    put("pageNumber", p.pageNumber)
                    put("handwritingMode", p.handwritingMode)
                    put("fontKey", p.fontKey)
                    put("fontSizeSp", p.fontSizeSp)
                    put("inkColorHex", p.inkColorHex)
                    put("lineSpacingMultiplier", p.lineSpacingMultiplier)
                    put("textAlignment", p.textAlignment)
                    put("bold", p.bold)
                    put("italic", p.italic)
                    put("paperColorKey", p.paperColorKey)
                    put("aiNotes", p.aiNotes)
                })
            }
        })

        root.put("attachments", JSONArray().apply {
            attachments.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id)
                    put("notebookId", a.notebookId)
                    put("fileName", a.fileName)
                    put("fileType", a.fileType)
                    put("localUriOrPath", a.localUriOrPath)
                    put("caption", a.caption)
                    put("sourceLabel", a.sourceLabel)
                    put("status", a.status)
                    put("priority", a.priority)
                })
            }
        })

        root.put("fitnessLogs", JSONArray().apply {
            fitnessLogs.forEach { f ->
                put(JSONObject().apply {
                    put("id", f.id)
                    put("notebookId", f.notebookId)
                    put("exerciseName", f.exerciseName)
                    put("muscleGroup", f.muscleGroup)
                    put("sets", f.sets)
                    put("reps", f.reps)
                    put("weightKg", f.weightKg)
                    put("rir", f.rir)
                    put("rpe", f.rpe)
                    put("personalRecord", f.personalRecord)
                    put("notes", f.notes)
                    put("workoutDateMillis", f.workoutDateMillis)
                })
            }
        })

        root.put("vocabulary", JSONArray().apply {
            vocabulary.forEach { v ->
                put(JSONObject().apply {
                    put("id", v.id)
                    put("notebookId", v.notebookId)
                    put("word", v.word)
                    put("pronunciation", v.pronunciation)
                    put("meaning", v.meaning)
                    put("hindiMeaning", v.hindiMeaning)
                    put("synonyms", JSONArray(v.synonyms))
                    put("antonyms", JSONArray(v.antonyms))
                    put("exampleSentences", JSONArray(v.exampleSentences))
                    put("category", v.category)
                    put("mastered", v.mastered)
                })
            }
        })

        root.put("todos", JSONArray().apply {
            todos.forEach { td ->
                put(JSONObject().apply {
                    put("id", td.id)
                    put("notebookId", td.notebookId)
                    put("title", td.title)
                    put("description", td.description)
                    put("category", td.category)
                    put("priority", td.priority)
                    put("completed", td.completed)
                    if (td.dueDateMillis != null) put("dueDateMillis", td.dueDateMillis)
                })
            }
        })

        return root.toString(2)
    }

    fun parseBackupJson(jsonText: String): ParsedBackupData {
        val root = JSONObject(jsonText)

        fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
        }

        val notebooks = mutableListOf<NotebookEntity>()
        val nbArr = root.optJSONArray("notebooks") ?: JSONArray()
        for (i in 0 until nbArr.length()) {
            val o = nbArr.getJSONObject(i)
            notebooks += NotebookEntity(
                id = o.optString("id"),
                notebookNumber = o.optInt("notebookNumber", i + 1),
                name = o.optString("name", "Restored Notebook"),
                purpose = o.optString("purpose"),
                shortDescription = o.optString("shortDescription"),
                detailedDescription = o.optString("detailedDescription"),
                iconKey = o.optString("iconKey", "menu_book"),
                coverColorHex = o.optLong("coverColorHex", 0xFF1B4332L),
                coverStyle = o.optString("coverStyle", "LEATHER"),
                sortOrder = o.optInt("sortOrder", i),
                pinned = o.optBoolean("pinned", false),
                archived = o.optBoolean("archived", false),
                hidden = o.optBoolean("hidden", false),
                systemDefault = o.optBoolean("systemDefault", false),
                notebookType = o.optString("notebookType", "CUSTOM")
            )
        }

        val topics = mutableListOf<TopicEntity>()
        val tArr = root.optJSONArray("topics") ?: JSONArray()
        for (i in 0 until tArr.length()) {
            val o = tArr.getJSONObject(i)
            topics += TopicEntity(
                id = o.optString("id"),
                notebookId = o.optString("notebookId"),
                name = o.optString("name", "Restored Topic"),
                shortDescription = o.optString("shortDescription"),
                purpose = o.optString("purpose"),
                introduction = o.optString("introduction"),
                coversItems = o.optJSONArray("coversItems").toStringList(),
                importantConcepts = o.optJSONArray("importantConcepts").toStringList(),
                relatedTopics = o.optJSONArray("relatedTopics").toStringList(),
                personalNotes = o.optString("personalNotes"),
                personalObservations = o.optString("personalObservations"),
                personalExamples = o.optString("personalExamples"),
                aiGeneratedInfo = o.optString("aiGeneratedInfo"),
                referenceLinks = o.optJSONArray("referenceLinks").toStringList(),
                iconKey = o.optString("iconKey", "article"),
                sortOrder = o.optInt("sortOrder", i)
            )
        }

        val subtopics = mutableListOf<SubtopicEntity>()
        val sArr = root.optJSONArray("subtopics") ?: JSONArray()
        for (i in 0 until sArr.length()) {
            val o = sArr.getJSONObject(i)
            subtopics += SubtopicEntity(
                id = o.optString("id"),
                notebookId = o.optString("notebookId"),
                topicId = o.optString("topicId"),
                name = o.optString("name", "Restored Subtopic"),
                shortDescription = o.optString("shortDescription"),
                purpose = o.optString("purpose"),
                introduction = o.optString("introduction"),
                coversItems = o.optJSONArray("coversItems").toStringList(),
                importantConcepts = o.optJSONArray("importantConcepts").toStringList(),
                relatedTopics = o.optJSONArray("relatedTopics").toStringList(),
                personalNotes = o.optString("personalNotes"),
                aiGeneratedInfo = o.optString("aiGeneratedInfo"),
                sortOrder = o.optInt("sortOrder", i)
            )
        }

        val pages = mutableListOf<PageEntity>()
        val pArr = root.optJSONArray("pages") ?: JSONArray()
        for (i in 0 until pArr.length()) {
            val o = pArr.getJSONObject(i)
            pages += PageEntity(
                id = o.optString("id"),
                notebookId = o.optString("notebookId"),
                topicId = o.optString("topicId").takeIf { it.isNotBlank() },
                subtopicId = o.optString("subtopicId").takeIf { it.isNotBlank() },
                title = o.optString("title", "Restored Page"),
                content = o.optString("content"),
                pageNumber = o.optInt("pageNumber", 1),
                handwritingMode = o.optBoolean("handwritingMode", false),
                fontKey = o.optString("fontKey", "lora"),
                fontSizeSp = o.optInt("fontSizeSp", 17),
                inkColorHex = o.optLong("inkColorHex", 0xFF1C2430L),
                lineSpacingMultiplier = o.optDouble("lineSpacingMultiplier", 1.5),
                textAlignment = o.optString("textAlignment", "START"),
                bold = o.optBoolean("bold", false),
                italic = o.optBoolean("italic", false),
                paperColorKey = o.optString("paperColorKey", "IVORY"),
                aiNotes = o.optString("aiNotes")
            )
        }

        val attachments = mutableListOf<AttachmentEntity>()
        val aArr = root.optJSONArray("attachments") ?: JSONArray()
        for (i in 0 until aArr.length()) {
            val o = aArr.getJSONObject(i)
            attachments += AttachmentEntity(
                id = o.optString("id"),
                notebookId = o.optString("notebookId", "nb_07"),
                fileName = o.optString("fileName", "Restored Item"),
                fileType = o.optString("fileType", "DOCUMENT"),
                localUriOrPath = o.optString("localUriOrPath"),
                caption = o.optString("caption"),
                sourceLabel = o.optString("sourceLabel", "Saved Messages"),
                status = o.optString("status", "SAVED"),
                priority = o.optString("priority", "MEDIUM")
            )
        }

        val fitnessLogs = mutableListOf<FitnessLogEntity>()
        val fArr = root.optJSONArray("fitnessLogs") ?: JSONArray()
        for (i in 0 until fArr.length()) {
            val o = fArr.getJSONObject(i)
            fitnessLogs += FitnessLogEntity(
                id = o.optString("id"),
                notebookId = o.optString("notebookId", "nb_05"),
                exerciseName = o.optString("exerciseName", "Exercise"),
                muscleGroup = o.optString("muscleGroup"),
                sets = o.optInt("sets", 3),
                reps = o.optString("reps", "10"),
                weightKg = o.optDouble("weightKg", 0.0),
                rir = o.optInt("rir", 2),
                rpe = o.optDouble("rpe", 8.0),
                personalRecord = o.optBoolean("personalRecord", false),
                notes = o.optString("notes"),
                workoutDateMillis = o.optLong("workoutDateMillis", System.currentTimeMillis())
            )
        }

        val vocabulary = mutableListOf<VocabularyEntryEntity>()
        val vArr = root.optJSONArray("vocabulary") ?: JSONArray()
        for (i in 0 until vArr.length()) {
            val o = vArr.getJSONObject(i)
            vocabulary += VocabularyEntryEntity(
                id = o.optString("id"),
                notebookId = o.optString("notebookId", "nb_02"),
                word = o.optString("word", "Word"),
                pronunciation = o.optString("pronunciation"),
                meaning = o.optString("meaning"),
                hindiMeaning = o.optString("hindiMeaning"),
                synonyms = o.optJSONArray("synonyms").toStringList(),
                antonyms = o.optJSONArray("antonyms").toStringList(),
                exampleSentences = o.optJSONArray("exampleSentences").toStringList(),
                category = o.optString("category", "Daily Use English Words"),
                mastered = o.optBoolean("mastered", false)
            )
        }

        val todos = mutableListOf<TodoTaskEntity>()
        val tdArr = root.optJSONArray("todos") ?: JSONArray()
        for (i in 0 until tdArr.length()) {
            val o = tdArr.getJSONObject(i)
            todos += TodoTaskEntity(
                id = o.optString("id"),
                notebookId = o.optString("notebookId", "nb_09"),
                title = o.optString("title", "Task"),
                description = o.optString("description"),
                category = o.optString("category", "TODAY"),
                priority = o.optString("priority", "MEDIUM"),
                completed = o.optBoolean("completed", false),
                dueDateMillis = if (o.has("dueDateMillis")) o.optLong("dueDateMillis") else null
            )
        }

        return ParsedBackupData(
            notebooks = notebooks,
            topics = topics,
            subtopics = subtopics,
            pages = pages,
            attachments = attachments,
            fitnessLogs = fitnessLogs,
            vocabulary = vocabulary,
            todos = todos
        )
    }

    fun copyAttachmentToInternalStorage(context: Context, uri: Uri, prefix: String = "img"): String {
        val dir = File(context.filesDir, "attachments")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file.absolutePath
    }

    private fun writeA4RuledPdf(file: File, sections: List<Pair<String, String>>) {
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val marginLeft = 64f
        val marginRight = 32f
        val topY = 56f
        val lineStep = 22f
        val maxContentWidth = (pageWidth - marginLeft - marginRight).toInt()

        val paperPaint = Paint().apply { color = Color.rgb(253, 251, 245) }
        val ruledPaint = Paint().apply {
            color = Color.rgb(186, 214, 247)
            strokeWidth = 0.8f
        }
        val marginPaint = Paint().apply {
            color = Color.rgb(214, 69, 69)
            strokeWidth = 1.2f
        }
        val headerPaint = TextPaint().apply {
            color = Color.rgb(27, 67, 50)
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val bodyPaint = TextPaint().apply {
            color = Color.rgb(28, 36, 48)
            textSize = 11.5f
            isAntiAlias = true
        }

        var pageNumber = 1

        fun startRuledPage(): PdfDocument.Page {
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create()
            val page = pdf.startPage(info)
            val canvas = page.canvas
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), paperPaint)
            var y = topY + lineStep
            while (y < pageHeight - 40f) {
                canvas.drawLine(0f, y, pageWidth.toFloat(), y, ruledPaint)
                y += lineStep
            }
            canvas.drawLine(marginLeft - 10f, 0f, marginLeft - 10f, pageHeight.toFloat(), marginPaint)
            return page
        }

        sections.forEach { (title, body) ->
            var page = startRuledPage()
            var canvas = page.canvas
            canvas.drawText(title.take(70), marginLeft, topY - 10f, headerPaint)

            val layout = StaticLayout.Builder.obtain(
                body.ifBlank { "(Empty page)" },
                0,
                body.ifBlank { "(Empty page)" }.length,
                bodyPaint,
                maxContentWidth
            )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(lineStep - 14f, 1.0f)
                .build()

            var currentY = topY + 6f
            for (line in 0 until layout.lineCount) {
                if (currentY > pageHeight - 48f) {
                    pdf.finishPage(page)
                    page = startRuledPage()
                    canvas = page.canvas
                    currentY = topY + 6f
                }
                val start = layout.getLineStart(line)
                val end = layout.getLineEnd(line)
                val lineText = layout.text.subSequence(start, end).toString().trimEnd('\n')
                canvas.drawText(lineText, marginLeft, currentY + 12f, bodyPaint)
                currentY += lineStep
            }
            pdf.finishPage(page)
        }

        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
    }
}
