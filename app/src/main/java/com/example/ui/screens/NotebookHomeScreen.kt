package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.platform.ExportBackupManager
import com.example.data.model.AttachmentEntity
import com.example.data.model.FitnessLogEntity
import com.example.data.model.NotebookEntity
import com.example.data.model.PageEntity
import com.example.data.model.SubtopicEntity
import com.example.data.model.TodoTaskEntity
import com.example.data.model.TopicEntity
import com.example.data.model.VocabularyEntryEntity
import com.example.ui.components.FitnessTrackerSection
import com.example.ui.components.HandwritingPracticeSection
import com.example.ui.components.NotebookEditorDialog
import com.example.ui.components.NotebookIcons
import com.example.ui.components.SavedWorkSection
import com.example.ui.components.TodoTrackerSection
import com.example.ui.components.TopicCreateDialog
import com.example.ui.components.VocabularySection
import com.example.ui.components.formatTimestampShort
import com.example.ui.viewmodel.LibrarySnapshot
import com.example.ui.viewmodel.TopicGenUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookHomeScreen(
    notebook: NotebookEntity,
    snapshot: LibrarySnapshot,
    topicGenState: TopicGenUiState,
    onBack: () -> Unit,
    onOpenTopic: (TopicEntity) -> Unit,
    onOpenPage: (PageEntity) -> Unit,
    onDeletePage: (PageEntity) -> Unit,
    onStartQuickWriting: (handwritingDefault: Boolean) -> Unit,
    onOpenAiAssistant: () -> Unit,
    onCreateTopic: (TopicEntity, List<String>) -> Unit,
    onGenerateTopicWithAi: (String) -> Unit,
    onResetTopicAiGen: () -> Unit,
    onSwapTopicOrder: (TopicEntity, TopicEntity) -> Unit,
    onDeleteTopic: (String) -> Unit,
    onUpdateNotebook: (
        name: String,
        purpose: String,
        shortDesc: String,
        detailedDesc: String,
        iconKey: String,
        colorHex: Long,
        coverStyle: String
    ) -> Unit,
    onAddFitnessLog: (FitnessLogEntity) -> Unit,
    onDeleteFitnessLog: (String) -> Unit,
    onAddVocabulary: (VocabularyEntryEntity) -> Unit,
    onToggleVocabularyMastered: (VocabularyEntryEntity) -> Unit,
    onDeleteVocabulary: (String) -> Unit,
    onAddTodo: (TodoTaskEntity) -> Unit,
    onToggleTodoCompleted: (TodoTaskEntity) -> Unit,
    onDeleteTodo: (String) -> Unit,
    onAddAttachment: (AttachmentEntity) -> Unit,
    onUpdateAttachmentStatus: (String, String) -> Unit,
    onDeleteAttachment: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchInNotebook by remember { mutableStateOf("") }
    var showCreateTopic by remember { mutableStateOf(false) }
    var showEditNotebook by remember { mutableStateOf(false) }
    var topicToDelete by remember { mutableStateOf<TopicEntity?>(null) }
    var pageToDelete by remember { mutableStateOf<PageEntity?>(null) }
    var showExportMenu by remember { mutableStateOf(false) }

    val hasSpecializedTool = notebook.notebookType in setOf(
        "FITNESS", "VOCABULARY", "TODO", "SAVED_WORK", "HANDWRITING"
    )
    var activeTab by remember(notebook.id) {
        mutableIntStateOf(
            when (notebook.notebookType) {
                "FITNESS", "TODO", "SAVED_WORK" -> 1
                "ROUGH" -> 2
                else -> 0
            }
        )
    }

    val nbTopics = remember(snapshot.topics, notebook.id, searchInNotebook) {
        val q = searchInNotebook.trim().lowercase()
        snapshot.topics.filter { it.notebookId == notebook.id }
            .filter { t ->
                q.isBlank() || t.name.lowercase().contains(q) ||
                    t.shortDescription.lowercase().contains(q) ||
                    t.purpose.lowercase().contains(q)
            }
    }

    val nbSubtopics = remember(snapshot.subtopics, notebook.id) {
        snapshot.subtopics.filter { it.notebookId == notebook.id }
    }

    val nbPages = remember(snapshot.pages, notebook.id) {
        snapshot.pages.filter { it.notebookId == notebook.id }
    }

    val generalPages = remember(nbPages, searchInNotebook) {
        val q = searchInNotebook.trim().lowercase()
        nbPages.filter { it.topicId.isNullOrBlank() }
            .filter { p -> q.isBlank() || p.title.lowercase().contains(q) || p.content.lowercase().contains(q) }
    }

    val subCountByTopic = remember(nbSubtopics) {
        nbSubtopics.groupingBy { it.topicId }.eachCount()
    }
    val pageCountByTopic = remember(nbPages) {
        nbPages.filter { !it.topicId.isNullOrBlank() }.groupingBy { it.topicId!! }.eachCount()
    }

    val coverColor = Color(notebook.coverColorHex)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_to_dashboard")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Dashboard")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Home / ${notebook.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "NB ${notebook.notebookNumber.toString().padStart(2, '0')} — ${notebook.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAiAssistant, modifier = Modifier.testTag("notebook_ai_button")) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "Ask AI about this notebook")
                    }
                    IconButton(onClick = { showEditNotebook = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit notebook details")
                    }
                    IconButton(onClick = { showExportMenu = true }) {
                        Icon(Icons.Filled.Share, contentDescription = "Export notebook")
                    }
                    DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Export Notebook as PDF") },
                            onClick = {
                                showExportMenu = false
                                scope.launch {
                                    val file = withContext(Dispatchers.IO) {
                                        ExportBackupManager.exportNotebookAsPdf(context, notebook, nbTopics, nbPages)
                                    }
                                    ExportBackupManager.shareFile(context, file, "application/pdf")
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export Notebook as Markdown") },
                            onClick = {
                                showExportMenu = false
                                scope.launch {
                                    val file = withContext(Dispatchers.IO) {
                                        ExportBackupManager.exportNotebookAsMarkdown(
                                            context, notebook, nbTopics, nbSubtopics, nbPages
                                        )
                                    }
                                    ExportBackupManager.shareFile(context, file, "text/markdown")
                                }
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (activeTab == 2 || notebook.notebookType == "ROUGH") {
                        onStartQuickWriting(notebook.notebookType == "HANDWRITING")
                    } else {
                        showCreateTopic = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (activeTab == 2 || notebook.notebookType == "ROUGH") Icons.Filled.Create else Icons.Filled.Add,
                        contentDescription = null
                    )
                },
                text = {
                    Text(if (activeTab == 2 || notebook.notebookType == "ROUGH") "Start Writing" else "New Topic")
                },
                modifier = Modifier.testTag("notebook_primary_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = coverColor.copy(alpha = 0.1f)),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = coverColor,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = NotebookIcons.resolve(notebook.iconKey),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "NOTEBOOK ${notebook.notebookNumber.toString().padStart(2, '0')} — ${notebook.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = coverColor
                                )
                                Text(
                                    text = "Updated ${formatTimestampShort(notebook.updatedAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (notebook.purpose.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Purpose",
                                style = MaterialTheme.typography.labelMedium,
                                color = coverColor,
                                fontWeight = FontWeight.Bold
                            )
                            Text(notebook.purpose, style = MaterialTheme.typography.bodyMedium)
                        }

                        if (notebook.detailedDescription.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = notebook.detailedDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MetricPill("Topics", nbTopics.size.toString())
                            MetricPill("Subtopics", nbSubtopics.size.toString())
                            MetricPill("Pages", nbPages.size.toString())
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        label = { Text("Topic Directory (${nbTopics.size})") },
                        modifier = Modifier.testTag("tab_topic_directory")
                    )
                    if (hasSpecializedTool) {
                        FilterChip(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            label = {
                                Text(
                                    when (notebook.notebookType) {
                                        "FITNESS" -> "Workout Log"
                                        "VOCABULARY" -> "Dictionary"
                                        "TODO" -> "Tasks"
                                        "SAVED_WORK" -> "Saved Inbox"
                                        "HANDWRITING" -> "Practice"
                                        else -> "Tools"
                                    }
                                )
                            },
                            modifier = Modifier.testTag("tab_specialized_tool")
                        )
                    }
                    FilterChip(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        label = { Text("Pages (${generalPages.size})") },
                        modifier = Modifier.testTag("tab_direct_pages")
                    )
                }
            }

            if (activeTab == 0 || activeTab == 2) {
                item {
                    OutlinedTextField(
                        value = searchInNotebook,
                        onValueChange = { searchInNotebook = it },
                        placeholder = { Text("Search within ${notebook.name}…") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("search_within_notebook")
                    )
                }
            }

            when (activeTab) {
                0 -> {
                    if (nbTopics.isEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (notebook.notebookType == "ROUGH" || notebook.notebookType == "OTHER") {
                                            "This notebook works great with direct pages, or you can create custom topics anytime."
                                        } else {
                                            "No topics match your filter yet. Tap \"New Topic\" to create one."
                                        },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { showCreateTopic = true }) {
                                            Text("Create Topic")
                                        }
                                        Button(onClick = { onStartQuickWriting(notebook.notebookType == "HANDWRITING") }) {
                                            Text("Start Writing Page")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        itemsIndexed(nbTopics, key = { _, t -> t.id }) { idx, topic ->
                            TopicDirectoryCard(
                                index = idx + 1,
                                topic = topic,
                                subtopicCount = subCountByTopic[topic.id] ?: 0,
                                pageCount = pageCountByTopic[topic.id] ?: 0,
                                canMoveUp = idx > 0,
                                canMoveDown = idx < nbTopics.lastIndex,
                                accentColor = coverColor,
                                onOpen = { onOpenTopic(topic) },
                                onMoveUp = { onSwapTopicOrder(topic, nbTopics[idx - 1]) },
                                onMoveDown = { onSwapTopicOrder(topic, nbTopics[idx + 1]) },
                                onDelete = { topicToDelete = topic }
                            )
                        }
                    }
                }
                1 -> {
                    item {
                        when (notebook.notebookType) {
                            "FITNESS" -> FitnessTrackerSection(
                                notebookId = notebook.id,
                                logs = snapshot.fitnessLogs.filter { it.notebookId == notebook.id },
                                onAddLog = onAddFitnessLog,
                                onDeleteLog = onDeleteFitnessLog
                            )
                            "VOCABULARY" -> VocabularySection(
                                notebookId = notebook.id,
                                entries = snapshot.vocabulary.filter { it.notebookId == notebook.id },
                                onAddWord = onAddVocabulary,
                                onToggleMastered = onToggleVocabularyMastered,
                                onDeleteWord = onDeleteVocabulary
                            )
                            "TODO" -> TodoTrackerSection(
                                notebookId = notebook.id,
                                todos = snapshot.todos.filter { it.notebookId == notebook.id },
                                onAddTodo = onAddTodo,
                                onToggleCompleted = onToggleTodoCompleted,
                                onDeleteTodo = onDeleteTodo
                            )
                            "SAVED_WORK" -> SavedWorkSection(
                                notebookId = notebook.id,
                                attachments = snapshot.attachments.filter { it.notebookId == notebook.id },
                                onAddAttachment = onAddAttachment,
                                onUpdateStatus = onUpdateAttachmentStatus,
                                onDeleteAttachment = onDeleteAttachment
                            )
                            "HANDWRITING" -> HandwritingPracticeSection(
                                onStartPracticePage = { onStartQuickWriting(true) }
                            )
                        }
                    }
                }
                2 -> {
                    item {
                        Button(
                            onClick = { onStartQuickWriting(notebook.notebookType == "HANDWRITING") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Create, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Create New A4 Writing Page")
                        }
                    }
                    itemsIndexed(generalPages, key = { _, p -> p.id }) { _, page ->
                        Card(
                            onClick = { onOpenPage(page) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Description, contentDescription = null, tint = coverColor)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Page ${page.pageNumber}: ${page.title}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = page.content.ifBlank { "Blank ruled page — tap to write" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { pageToDelete = page },
                                    modifier = Modifier.testTag("delete_general_page_${page.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete page",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateTopic) {
        TopicCreateDialog(
            notebook = notebook,
            aiEnabled = snapshot.preferences.aiEnabled,
            topicGenState = topicGenState,
            onGenerateWithAi = onGenerateTopicWithAi,
            onResetAiGen = onResetTopicAiGen,
            onDismiss = { showCreateTopic = false },
            onCreate = { topic, subNames ->
                onCreateTopic(topic, subNames)
                showCreateTopic = false
            }
        )
    }

    if (showEditNotebook) {
        NotebookEditorDialog(
            initial = notebook,
            defaultCoverStyle = snapshot.preferences.defaultCoverStyle,
            onDismiss = { showEditNotebook = false },
            onSave = { name, purpose, shortDesc, detailedDesc, iconKey, colorHex, coverStyle ->
                onUpdateNotebook(name, purpose, shortDesc, detailedDesc, iconKey, colorHex, coverStyle)
                showEditNotebook = false
            }
        )
    }

    topicToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { topicToDelete = null },
            title = { Text("Delete Topic?") },
            text = { Text("Delete \"${target.name}\"? Existing writing pages will remain accessible inside the notebook.") },
            confirmButton = {
                Button(onClick = {
                    onDeleteTopic(target.id)
                    topicToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { topicToDelete = null }) { Text("Cancel") }
            }
        )
    }

    pageToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pageToDelete = null },
            title = { Text("Delete Page ${target.pageNumber}?") },
            text = { Text("Delete \"${target.title}\"? Remaining pages will be automatically renumbered.") },
            confirmButton = {
                Button(onClick = {
                    onDeletePage(target)
                    pageToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pageToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TopicDirectoryCard(
    index: Int,
    topic: TopicEntity,
    subtopicCount: Int,
    pageCount: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    accentColor: Color,
    onOpen: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("topic_card_${topic.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = accentColor.copy(alpha = 0.14f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(36.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = topic.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (topic.shortDescription.isNotBlank()) {
                    Text(
                        text = topic.shortDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$subtopicCount subtopics • $pageCount pages",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
            }
            TextButton(onClick = onOpen) {
                Text("Open")
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Topic options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (canMoveUp) {
                    DropdownMenuItem(
                        text = { Text("Move Up") },
                        leadingIcon = { Icon(Icons.Filled.ArrowUpward, contentDescription = null) },
                        onClick = { menuOpen = false; onMoveUp() }
                    )
                }
                if (canMoveDown) {
                    DropdownMenuItem(
                        text = { Text("Move Down") },
                        leadingIcon = { Icon(Icons.Filled.ArrowDownward, contentDescription = null) },
                        onClick = { menuOpen = false; onMoveDown() }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete Topic") },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}
