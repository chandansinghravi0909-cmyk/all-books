package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.platform.ExportBackupManager
import com.example.data.model.AttachmentEntity
import com.example.data.model.NotebookEntity
import com.example.data.model.PageEntity
import com.example.data.model.SubtopicEntity
import com.example.data.model.TopicEntity
import com.example.ui.components.SubtopicCreateDialog
import com.example.ui.viewmodel.LibrarySnapshot
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TopicInfoScreen(
    notebook: NotebookEntity,
    topic: TopicEntity,
    snapshot: LibrarySnapshot,
    onBack: () -> Unit,
    onBackToDashboard: () -> Unit,
    onOpenSubtopic: (SubtopicEntity) -> Unit,
    onOpenRelatedTopic: (String) -> Unit,
    onOpenPage: (PageEntity) -> Unit,
    onCreateNewPage: () -> Unit,
    onDeletePage: (PageEntity) -> Unit,
    onStartWriting: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    onUpdateDefaultInfo: (
        name: String,
        shortDesc: String,
        purpose: String,
        intro: String,
        covers: List<String>,
        concepts: List<String>,
        related: List<String>
    ) -> Unit,
    onUpdatePersonalSection: (
        personalNotes: String,
        observations: String,
        examples: String,
        links: List<String>
    ) -> Unit,
    onCreateSubtopic: (SubtopicEntity) -> Unit,
    onAddAttachment: (AttachmentEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val subtopics = remember(snapshot.subtopics, topic.id) {
        snapshot.subtopics.filter { it.topicId == topic.id }
    }
    val topicPages = remember(snapshot.pages, topic.id) {
        snapshot.pages.filter { it.topicId == topic.id && it.subtopicId.isNullOrBlank() }
    }
    val topicAttachments = remember(snapshot.attachments, topic.id) {
        snapshot.attachments.filter { it.topicId == topic.id }
    }

    var infoSectionTab by remember { mutableIntStateOf(0) } // 0=Topic Info, 1=My Personal Notes, 2=AI Info
    var introExpanded by remember { mutableStateOf(true) }
    var coversExpanded by remember { mutableStateOf(true) }
    var showEditDefault by remember { mutableStateOf(false) }
    var showEditPersonal by remember { mutableStateOf(false) }
    var showAddSubtopic by remember { mutableStateOf(false) }
    var pageToDelete by remember { mutableStateOf<PageEntity?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) {
                    ExportBackupManager.copyAttachmentToInternalStorage(context, uri, "topic_${topic.id}")
                }
                onAddAttachment(
                    AttachmentEntity(
                        notebookId = notebook.id,
                        topicId = topic.id,
                        fileName = File(path).name,
                        fileType = "IMAGE",
                        localUriOrPath = path,
                        caption = "Attached to ${topic.name}",
                        sourceLabel = topic.name
                    )
                )
            }
        }
    }

    val accent = Color(notebook.coverColorHex)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_to_notebook")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Home",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onBackToDashboard() }
                            )
                            Text(
                                text = " / ${notebook.name} / ${topic.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = topic.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAiAssistant, modifier = Modifier.testTag("topic_ai_button")) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "AI Assistant")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                ExportBackupManager.exportTopicAsMarkdown(
                                    context, notebook.name, topic, subtopics, topicPages
                                )
                            }
                            ExportBackupManager.shareFile(context, file, "text/markdown")
                        }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Export topic")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCreateNewPage,
                        modifier = Modifier.weight(1f).testTag("topic_create_page_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New Page")
                    }
                    Button(
                        onClick = onStartWriting,
                        modifier = Modifier.weight(1f).testTag("topic_start_writing_button")
                    ) {
                        Icon(Icons.Filled.Create, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Start Writing")
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1 & 2. Topic Title + Purpose
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.09f)),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = topic.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showEditDefault = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit topic information")
                            }
                        }
                        if (topic.shortDescription.isNotBlank()) {
                            Text(
                                text = topic.shortDescription,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            text = "PURPOSE",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = topic.purpose.ifBlank { "Define why this topic matters to you." },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Separation Tabs: Default Info | My Personal Notes | AI Info
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = infoSectionTab == 0,
                        onClick = { infoSectionTab = 0 },
                        label = { Text("Topic Info") }
                    )
                    FilterChip(
                        selected = infoSectionTab == 1,
                        onClick = { infoSectionTab = 1 },
                        label = { Text("My Notes") },
                        modifier = Modifier.testTag("tab_personal_notes")
                    )
                    FilterChip(
                        selected = infoSectionTab == 2,
                        onClick = { infoSectionTab = 2 },
                        label = { Text("AI Info") },
                        modifier = Modifier.testTag("tab_ai_info")
                    )
                }
            }

            when (infoSectionTab) {
                0 -> {
                    // 3. Detailed Introduction (Expandable)
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { introExpanded = !introExpanded },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Detailed Introduction",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = if (introExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = "Toggle introduction"
                                    )
                                }
                                AnimatedVisibility(visible = introExpanded) {
                                    Text(
                                        text = topic.introduction.ifBlank { "Tap edit to add a detailed introduction." },
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 4. What This Topic Covers
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { coversExpanded = !coversExpanded },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "What This Topic Covers (${topic.coversItems.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = if (coversExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = "Toggle coverage"
                                    )
                                }
                                AnimatedVisibility(visible = coversExpanded) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 10.dp)
                                    ) {
                                        topic.coversItems.forEach { item ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Text(
                                                    text = item,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 5. Important Concepts
                    if (topic.importantConcepts.isNotEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Important Concepts",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    topic.importantConcepts.forEachIndexed { idx, concept ->
                                        Surface(
                                            tonalElevation = 1.dp,
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "${idx + 1}. $concept",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 6. Subtopics + 9. Add New Subtopic
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Subtopics (${subtopics.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilledTonalButton(
                                        onClick = { showAddSubtopic = true },
                                        modifier = Modifier.testTag("add_subtopic_button")
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Add Subtopic")
                                    }
                                }
                                if (subtopics.isEmpty()) {
                                    Text(
                                        text = "No subtopics yet. Add subtopics to break this subject into focused information pages.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    subtopics.forEach { sub ->
                                        Surface(
                                            onClick = { onOpenSubtopic(sub) },
                                            tonalElevation = 2.dp,
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("subtopic_card_${sub.id}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(sub.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                    if (sub.shortDescription.isNotBlank()) {
                                                        Text(
                                                            text = sub.shortDescription,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 2
                                                        )
                                                    }
                                                }
                                                Icon(
                                                    Icons.AutoMirrored.Filled.ArrowForward,
                                                    contentDescription = "Open subtopic",
                                                    tint = accent
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 7. Related Topics
                    if (topic.relatedTopics.isNotEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Related Topics",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        topic.relatedTopics.forEach { rel ->
                                            AssistChip(
                                                onClick = { onOpenRelatedTopic(rel) },
                                                label = { Text(rel) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // My Personal Notes, Observations, Examples, Links & Images
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "My Personal Information",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = { showEditPersonal = true },
                                        modifier = Modifier.testTag("edit_personal_info_button")
                                    ) {
                                        Text("Edit My Info")
                                    }
                                }
                                Text(
                                    text = "Stored separately from default topic descriptions and AI output.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                PersonalBlock("My Notes", topic.personalNotes)
                                PersonalBlock("Personal Observations", topic.personalObservations)
                                PersonalBlock("Examples", topic.personalExamples)

                                if (topic.referenceLinks.isNotEmpty()) {
                                    Text("Reference Links", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    topic.referenceLinks.forEach { link ->
                                        Text("• $link", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        photoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Image, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Attach Image to Topic")
                                }

                                topicAttachments.forEach { att ->
                                    if (att.localUriOrPath.isNotBlank() && File(att.localUriOrPath).exists()) {
                                        AsyncImage(
                                            model = File(att.localUriOrPath),
                                            contentDescription = att.fileName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(150.dp)
                                                .clip(MaterialTheme.shapes.small)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // AI-Generated Information (Strictly separate)
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "AI-Generated Reference",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilledTonalButton(onClick = onOpenAiAssistant) {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Ask AI")
                                    }
                                }
                                Text(
                                    text = topic.aiGeneratedInfo.ifBlank {
                                        "No AI reference saved here yet. Tap \"Ask AI\" and choose \"Save to AI Info Section\" to store AI explanations without altering your personal notes."
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            // 8. My Notes and Pages
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "My Writing Pages (${topicPages.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (topicPages.isEmpty()) {
                            Text(
                                text = "No writing pages inside ${topic.name} yet. Tap \"Start Writing\" below to open an A4 ruled page.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            topicPages.forEach { page ->
                                Surface(
                                    onClick = { onOpenPage(page) },
                                    tonalElevation = 2.dp,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Description, contentDescription = null, tint = accent)
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Page ${page.pageNumber}: ${page.title}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = page.content.ifBlank { "Blank page" },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = { pageToDelete = page },
                                            modifier = Modifier.testTag("delete_topic_page_${page.id}")
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
        }
    }

    if (showEditDefault) {
        var name by remember { mutableStateOf(topic.name) }
        var shortDesc by remember { mutableStateOf(topic.shortDescription) }
        var purpose by remember { mutableStateOf(topic.purpose) }
        var intro by remember { mutableStateOf(topic.introduction) }
        var covers by remember { mutableStateOf(topic.coversItems.joinToString("\n")) }
        var concepts by remember { mutableStateOf(topic.importantConcepts.joinToString("\n")) }
        var related by remember { mutableStateOf(topic.relatedTopics.joinToString(", ")) }

        AlertDialog(
            onDismissRequest = { showEditDefault = false },
            title = { Text("Edit Topic Information") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Title *") })
                    OutlinedTextField(value = shortDesc, onValueChange = { shortDesc = it }, label = { Text("Short Description") })
                    OutlinedTextField(value = purpose, onValueChange = { purpose = it }, label = { Text("Purpose") }, maxLines = 3)
                    OutlinedTextField(value = intro, onValueChange = { intro = it }, label = { Text("Introduction") }, maxLines = 4)
                    OutlinedTextField(value = covers, onValueChange = { covers = it }, label = { Text("What This Covers (one per line)") }, maxLines = 4)
                    OutlinedTextField(value = concepts, onValueChange = { concepts = it }, label = { Text("Important Concepts (one per line)") }, maxLines = 3)
                    OutlinedTextField(value = related, onValueChange = { related = it }, label = { Text("Related Topics (comma separated)") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateDefaultInfo(
                            name,
                            shortDesc,
                            purpose,
                            intro,
                            covers.lines().map { it.trim() }.filter { it.isNotBlank() },
                            concepts.lines().map { it.trim() }.filter { it.isNotBlank() },
                            related.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        )
                        showEditDefault = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditDefault = false }) { Text("Cancel") } }
        )
    }

    if (showEditPersonal) {
        var notes by remember { mutableStateOf(topic.personalNotes) }
        var obs by remember { mutableStateOf(topic.personalObservations) }
        var ex by remember { mutableStateOf(topic.personalExamples) }
        var links by remember { mutableStateOf(topic.referenceLinks.joinToString("\n")) }

        AlertDialog(
            onDismissRequest = { showEditPersonal = false },
            title = { Text("Add Personal Information") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("My Explanations & Notes") }, maxLines = 4)
                    OutlinedTextField(value = obs, onValueChange = { obs = it }, label = { Text("Personal Observations") }, maxLines = 3)
                    OutlinedTextField(value = ex, onValueChange = { ex = it }, label = { Text("My Examples") }, maxLines = 3)
                    OutlinedTextField(value = links, onValueChange = { links = it }, label = { Text("Reference Links (one per line)") }, maxLines = 3)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdatePersonalSection(
                            notes,
                            obs,
                            ex,
                            links.lines().map { it.trim() }.filter { it.isNotBlank() }
                        )
                        showEditPersonal = false
                    }
                ) { Text("Save Personal Info") }
            },
            dismissButton = { TextButton(onClick = { showEditPersonal = false }) { Text("Cancel") } }
        )
    }

    if (showAddSubtopic) {
        SubtopicCreateDialog(
            notebookId = notebook.id,
            topicId = topic.id,
            topicName = topic.name,
            onDismiss = { showAddSubtopic = false },
            onCreate = {
                onCreateSubtopic(it)
                showAddSubtopic = false
            }
        )
    }

    pageToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pageToDelete = null },
            title = { Text("Delete Page ${target.pageNumber}?") },
            text = { Text("Delete \"${target.title}\"? Remaining pages in ${topic.name} will be renumbered automatically.") },
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
private fun PersonalBlock(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(
            text = body.ifBlank { "Not added yet." },
            style = MaterialTheme.typography.bodyMedium,
            color = if (body.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
    }
}
