package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.NotebookEntity
import com.example.data.model.PageEntity
import com.example.data.model.SubtopicEntity
import com.example.data.model.TopicEntity
import com.example.ui.viewmodel.LibrarySnapshot

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubtopicInfoScreen(
    notebook: NotebookEntity,
    topic: TopicEntity,
    subtopic: SubtopicEntity,
    snapshot: LibrarySnapshot,
    onBack: () -> Unit,
    onOpenPage: (PageEntity) -> Unit,
    onCreateNewPage: () -> Unit,
    onDeletePage: (PageEntity) -> Unit,
    onStartWriting: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    onUpdateSubtopic: (
        name: String,
        shortDesc: String,
        purpose: String,
        intro: String,
        personalNotes: String
    ) -> Unit
) {
    val subPages = remember(snapshot.pages, subtopic.id) {
        snapshot.pages.filter { it.subtopicId == subtopic.id }
    }
    var showEdit by remember { mutableStateOf(false) }
    var pageToDelete by remember { mutableStateOf<PageEntity?>(null) }
    val accent = Color(notebook.coverColorHex)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_to_topic")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Topic")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Home / ${notebook.name} / ${topic.name} / ${subtopic.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtopic.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAiAssistant) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = "AI Assistant")
                    }
                    IconButton(onClick = { showEdit = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit subtopic")
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
                    OutlinedButton(onClick = onCreateNewPage, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New Page")
                    }
                    Button(
                        onClick = onStartWriting,
                        modifier = Modifier.weight(1f).testTag("subtopic_start_writing_button")
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.09f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(subtopic.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent)
                        if (subtopic.shortDescription.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(subtopic.shortDescription, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (subtopic.purpose.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text("PURPOSE", style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
                            Text(subtopic.purpose, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (subtopic.introduction.isNotBlank()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Explanation & Introduction", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(subtopic.introduction, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (subtopic.coversItems.isNotEmpty() || subtopic.importantConcepts.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (subtopic.coversItems.isNotEmpty()) {
                                Text("Covers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    subtopic.coversItems.forEach { item ->
                                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                                            Text(item, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }
                                }
                            }
                            if (subtopic.importantConcepts.isNotEmpty()) {
                                Text("Key Concepts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                subtopic.importantConcepts.forEach { c ->
                                    Text("• $c", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("My Personal Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { showEdit = true }) { Text("Edit") }
                        }
                        Text(
                            text = subtopic.personalNotes.ifBlank { "No personal notes added for this subtopic yet." },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (subtopic.aiGeneratedInfo.isNotBlank()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("AI-Generated Reference", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                            Text(subtopic.aiGeneratedInfo, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Subtopic Writing Pages (${subPages.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (subPages.isEmpty()) {
                            Text("Tap \"Start Writing\" to create the first A4 ruled page for ${subtopic.name}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            subPages.forEach { page ->
                                Surface(
                                    onClick = { onOpenPage(page) },
                                    tonalElevation = 2.dp,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Description, contentDescription = null, tint = accent)
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Page ${page.pageNumber}: ${page.title}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(page.content.ifBlank { "Blank page" }, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                        IconButton(
                                            onClick = { pageToDelete = page },
                                            modifier = Modifier.testTag("delete_subtopic_page_${page.id}")
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

    if (showEdit) {
        var name by remember { mutableStateOf(subtopic.name) }
        var shortDesc by remember { mutableStateOf(subtopic.shortDescription) }
        var purpose by remember { mutableStateOf(subtopic.purpose) }
        var intro by remember { mutableStateOf(subtopic.introduction) }
        var notes by remember { mutableStateOf(subtopic.personalNotes) }

        AlertDialog(
            onDismissRequest = { showEdit = false },
            title = { Text("Edit Subtopic") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name *") })
                    OutlinedTextField(value = shortDesc, onValueChange = { shortDesc = it }, label = { Text("Short Description") })
                    OutlinedTextField(value = purpose, onValueChange = { purpose = it }, label = { Text("Purpose") }, maxLines = 2)
                    OutlinedTextField(value = intro, onValueChange = { intro = it }, label = { Text("Introduction") }, maxLines = 4)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("My Personal Notes") }, maxLines = 4)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateSubtopic(name, shortDesc, purpose, intro, notes)
                        showEdit = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEdit = false }) { Text("Cancel") } }
        )
    }

    pageToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pageToDelete = null },
            title = { Text("Delete Page ${target.pageNumber}?") },
            text = { Text("Delete \"${target.title}\"? Remaining pages in ${subtopic.name} will be renumbered automatically.") },
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
