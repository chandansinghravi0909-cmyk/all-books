package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.NotebookEntity
import com.example.data.model.SubtopicEntity
import com.example.data.model.TopicEntity
import com.example.ui.viewmodel.TopicGenUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotebookEditorDialog(
    initial: NotebookEntity?,
    defaultCoverStyle: String,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        purpose: String,
        shortDescription: String,
        detailedDescription: String,
        iconKey: String,
        coverColorHex: Long,
        coverStyle: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var purpose by remember { mutableStateOf(initial?.purpose ?: "") }
    var shortDesc by remember { mutableStateOf(initial?.shortDescription ?: "") }
    var detailedDesc by remember { mutableStateOf(initial?.detailedDescription ?: "") }
    var iconKey by remember { mutableStateOf(initial?.iconKey ?: "menu_book") }
    var colorHex by remember { mutableLongStateOf(initial?.coverColorHex ?: 0xFF1B4332L) }
    var coverStyle by remember { mutableStateOf(initial?.coverStyle ?: defaultCoverStyle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Create New Notebook" else "Edit Notebook") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Notebook Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("notebook_name_input")
                )
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Purpose") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
                OutlinedTextField(
                    value = shortDesc,
                    onValueChange = { shortDesc = it },
                    label = { Text("Short Description") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
                OutlinedTextField(
                    value = detailedDesc,
                    onValueChange = { detailedDesc = it },
                    label = { Text("Detailed Description") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Text("Cover Color", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NotebookIcons.coverColors.forEach { (_, hex) ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (colorHex == hex) 3.dp else 1.dp,
                                    color = if (colorHex == hex) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { colorHex = hex }
                        )
                    }
                }

                Text("Cover Style", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NotebookIcons.coverStyles.forEach { style ->
                        FilterChip(
                            selected = coverStyle == style,
                            onClick = { coverStyle = style },
                            label = { Text(style.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }

                Text("Notebook Icon", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NotebookIcons.allChoices.forEach { (key, vec) ->
                        Surface(
                            color = if (iconKey == key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { iconKey = key }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = vec,
                                    contentDescription = key,
                                    tint = if (iconKey == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name, purpose, shortDesc, detailedDesc, iconKey, colorHex, coverStyle)
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_notebook_button")
            ) {
                Text(if (initial == null) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicCreateDialog(
    notebook: NotebookEntity,
    aiEnabled: Boolean,
    topicGenState: TopicGenUiState,
    onGenerateWithAi: (topicName: String) -> Unit,
    onResetAiGen: () -> Unit,
    onDismiss: () -> Unit,
    onCreate: (TopicEntity, starterSubtopics: List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var shortDesc by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var introduction by remember { mutableStateOf("") }
    var coversText by remember { mutableStateOf("") }
    var conceptsText by remember { mutableStateOf("") }
    var subtopicsText by remember { mutableStateOf("") }
    var relatedText by remember { mutableStateOf("") }
    var iconKey by remember { mutableStateOf(notebook.iconKey) }

    LaunchedEffect(topicGenState) {
        if (topicGenState is TopicGenUiState.Success) {
            val info = topicGenState.info
            shortDesc = info.shortDescription
            purpose = info.purpose
            introduction = info.introduction
            coversText = info.coversItems.joinToString("\n")
            conceptsText = info.importantConcepts.joinToString("\n")
            subtopicsText = info.subtopics.joinToString("\n")
            relatedText = info.relatedTopics.joinToString(", ")
        }
    }

    AlertDialog(
        onDismissRequest = {
            onResetAiGen()
            onDismiss()
        },
        title = { Text("New Topic in ${notebook.name}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Topic Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("topic_name_input")
                )

                if (aiEnabled) {
                    OutlinedButton(
                        onClick = { if (name.isNotBlank()) onGenerateWithAi(name.trim()) },
                        enabled = name.isNotBlank() && topicGenState !is TopicGenUiState.Loading,
                        modifier = Modifier.fillMaxWidth().testTag("ai_generate_topic_button")
                    ) {
                        if (topicGenState is TopicGenUiState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Generating Topic Information…")
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Optional: Generate Details with AI")
                        }
                    }
                    if (topicGenState is TopicGenUiState.Error) {
                        Text(
                            text = topicGenState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                OutlinedTextField(
                    value = shortDesc,
                    onValueChange = { shortDesc = it },
                    label = { Text("Short Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Detailed Purpose") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                OutlinedTextField(
                    value = introduction,
                    onValueChange = { introduction = it },
                    label = { Text("Introduction") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
                OutlinedTextField(
                    value = coversText,
                    onValueChange = { coversText = it },
                    label = { Text("What This Topic Covers (one per line)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
                OutlinedTextField(
                    value = conceptsText,
                    onValueChange = { conceptsText = it },
                    label = { Text("Important Concepts (one per line)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                OutlinedTextField(
                    value = subtopicsText,
                    onValueChange = { subtopicsText = it },
                    label = { Text("Starter Subtopics (one per line)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                OutlinedTextField(
                    value = relatedText,
                    onValueChange = { relatedText = it },
                    label = { Text("Related Topics (comma separated)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val splitLines = { raw: String ->
                        raw.lines().map { it.trim() }.filter { it.isNotBlank() }
                    }
                    val topic = TopicEntity(
                        notebookId = notebook.id,
                        name = name.trim(),
                        shortDescription = shortDesc.trim(),
                        purpose = purpose.trim(),
                        introduction = introduction.trim(),
                        coversItems = splitLines(coversText),
                        importantConcepts = splitLines(conceptsText),
                        relatedTopics = relatedText.split(",").map { it.trim() }.filter { it.isNotBlank() },
                        iconKey = iconKey
                    )
                    onCreate(topic, splitLines(subtopicsText))
                    onResetAiGen()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_topic_button")
            ) {
                Text("Create Topic")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onResetAiGen()
                    onDismiss()
                }
            ) { Text("Cancel") }
        }
    )
}

@Composable
fun SubtopicCreateDialog(
    notebookId: String,
    topicId: String,
    topicName: String,
    onDismiss: () -> Unit,
    onCreate: (SubtopicEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var shortDesc by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var introduction by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Subtopic in $topicName") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Subtopic Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("subtopic_name_input")
                )
                OutlinedTextField(
                    value = shortDesc,
                    onValueChange = { shortDesc = it },
                    label = { Text("Short Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Purpose") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
                OutlinedTextField(
                    value = introduction,
                    onValueChange = { introduction = it },
                    label = { Text("Detailed Explanation / Introduction") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(
                            SubtopicEntity(
                                notebookId = notebookId,
                                topicId = topicId,
                                name = name.trim(),
                                shortDescription = shortDesc.trim(),
                                purpose = purpose.trim(),
                                introduction = introduction.trim()
                            )
                        )
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_subtopic_button")
            ) {
                Text("Add Subtopic")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
