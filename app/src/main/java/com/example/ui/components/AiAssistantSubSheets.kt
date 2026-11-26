package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ai.AiConversationSession
import com.example.data.ai.AiProposedAction
import com.example.data.ai.LearningProgressRecord
import com.example.data.ai.LearningProgressStatus
import com.example.data.ai.ProposalStatus

@Composable
fun AiProposalCard(
    proposal: AiProposedAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember(proposal.id) { mutableStateOf(true) }
    var showDestructiveConfirm by remember(proposal.id) { mutableStateOf(false) }

    val previewText = when (proposal) {
        is AiProposedAction.CreatePage -> proposal.content
        is AiProposedAction.AppendToPage -> proposal.contentToAppend
        is AiProposedAction.OrganizePage -> proposal.organizedContent
        is AiProposedAction.RenamePage -> "Current title: ${proposal.oldTitle}\nNew title: ${proposal.newTitle}"
        is AiProposedAction.CreateTopic -> buildString {
            appendLine("Topic: ${proposal.name}")
            if (proposal.shortDescription.isNotBlank()) appendLine("Summary: ${proposal.shortDescription}")
            if (proposal.purpose.isNotBlank()) appendLine("Purpose: ${proposal.purpose}")
            if (proposal.starterSubtopics.isNotEmpty()) {
                appendLine("Subtopics: ${proposal.starterSubtopics.joinToString(", ")}")
            }
        }.trim()
        is AiProposedAction.CreateSubtopic -> buildString {
            appendLine("Subtopic: ${proposal.name}")
            if (proposal.shortDescription.isNotBlank()) appendLine("Summary: ${proposal.shortDescription}")
            if (proposal.purpose.isNotBlank()) appendLine("Purpose: ${proposal.purpose}")
        }.trim()
        is AiProposedAction.MoveContent -> buildString {
            appendLine("From: ${proposal.sourcePageTitle}")
            appendLine("To: ${proposal.targetPageTitle}")
            appendLine(if (proposal.removeFromSource) "Mode: Cut & Move (Undo supported)" else "Mode: Copy only")
            appendLine()
            append(proposal.contentToMove)
        }.trim()
    }

    val icon = when (proposal) {
        is AiProposedAction.CreatePage -> Icons.Filled.NoteAdd
        is AiProposedAction.AppendToPage -> Icons.Filled.PostAdd
        is AiProposedAction.OrganizePage, is AiProposedAction.RenamePage -> Icons.Filled.Edit
        is AiProposedAction.CreateTopic, is AiProposedAction.CreateSubtopic -> Icons.Filled.FolderOpen
        is AiProposedAction.MoveContent -> Icons.Filled.DriveFileMove
    }

    val primaryButtonLabel = when (proposal) {
        is AiProposedAction.CreatePage -> if (proposal.isRevisionPage) "Create Revision Page" else "Create Page"
        is AiProposedAction.AppendToPage -> "Append Notes"
        is AiProposedAction.OrganizePage -> "Review & Replace"
        is AiProposedAction.RenamePage -> "Confirm Rename"
        is AiProposedAction.CreateTopic -> "Create Topic"
        is AiProposedAction.CreateSubtopic -> "Create Subtopic"
        is AiProposedAction.MoveContent -> if (proposal.removeFromSource) "Confirm Move" else "Copy Section"
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (proposal.status) {
                ProposalStatus.APPLIED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ProposalStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                ProposalStatus.DISMISSED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ProposalStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            }
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_proposal_card_${proposal.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = when (proposal.status) {
                        ProposalStatus.APPLIED -> Icons.Filled.CheckCircle
                        ProposalStatus.FAILED -> Icons.Filled.ErrorOutline
                        else -> icon
                    },
                    contentDescription = null,
                    tint = when (proposal.status) {
                        ProposalStatus.APPLIED -> MaterialTheme.colorScheme.primary
                        ProposalStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.secondary
                    },
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = proposal.summary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Hide preview" else "Show preview",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (expanded && previewText.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 170.dp)
                ) {
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp)
                    )
                }
            }

            if (proposal.statusNote.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = proposal.statusNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (proposal.status == ProposalStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }

            if (proposal.status == ProposalStatus.PENDING || proposal.status == ProposalStatus.FAILED) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (proposal.requiresExplicitConfirmation) {
                                showDestructiveConfirm = true
                            } else {
                                onConfirm()
                            }
                        },
                        modifier = Modifier.testTag("ai_proposal_confirm_${proposal.id}")
                    ) {
                        Text(if (proposal.status == ProposalStatus.FAILED) "Retry Action" else primaryButtonLabel)
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("ai_proposal_dismiss_${proposal.id}")
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    if (showDestructiveConfirm) {
        AlertDialog(
            onDismissRequest = { showDestructiveConfirm = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
            title = { Text("Confirm Notebook Change") },
            text = {
                Text(
                    "${proposal.summary}\n\nYour original content is preserved in an Undo snapshot immediately after applying."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDestructiveConfirm = false
                        onConfirm()
                    },
                    modifier = Modifier.testTag("ai_confirm_destructive_ok")
                ) {
                    Text("Yes, Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDestructiveConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AiHistoryDialog(
    sessions: List<AiConversationSession>,
    activeSessionId: String,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved AI Conversations (${sessions.size})") },
        text = {
            if (sessions.isEmpty()) {
                Text(
                    text = "No saved conversations yet. Your chats are saved automatically as soon as you send a message.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    items(sessions, key = { it.id }) { session ->
                        val isActive = session.id == activeSessionId
                        Surface(
                            onClick = {
                                onSelect(session.id)
                                onDismiss()
                            },
                            color = if (isActive) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.title.ifBlank { "Conversation" },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val scopeText = listOfNotNull(
                                        session.notebookName.takeIf { it.isNotBlank() },
                                        session.topicName?.takeIf { it.isNotBlank() }
                                    ).joinToString(" • ")
                                    Text(
                                        text = buildString {
                                            if (scopeText.isNotBlank()) append("$scopeText • ")
                                            append("${session.messages.size} msg(s)")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDelete(session.id) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete conversation",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiLearningProgressDialog(
    notebookId: String,
    topicId: String?,
    subtopicId: String?,
    records: List<LearningProgressRecord>,
    onUpsert: (concept: String, status: LearningProgressStatus, notes: String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var conceptInput by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf(LearningProgressStatus.ALREADY_STUDIED) }

    val scoped = remember(records, notebookId) {
        records.filter { it.notebookId == notebookId || notebookId.isBlank() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Learning Progress (${scoped.size})") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                OutlinedTextField(
                    value = conceptInput,
                    onValueChange = { conceptInput = it },
                    label = { Text("Add or update concept") },
                    placeholder = { Text("e.g., Digestive System, Gut Microbiome") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LearningProgressStatus.entries.forEach { st ->
                        FilterChip(
                            selected = selectedStatus == st,
                            onClick = { selectedStatus = st },
                            label = { Text(st.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Button(
                    onClick = {
                        if (conceptInput.isNotBlank()) {
                            onUpsert(conceptInput.trim(), selectedStatus, "")
                            conceptInput = ""
                        }
                    },
                    enabled = conceptInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Progress Status")
                }

                if (scoped.isEmpty()) {
                    Text(
                        text = "Tell the AI what you have studied, partially studied, or want to revise—or add concepts above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        items(scoped, key = { it.id }) { item ->
                            Surface(
                                tonalElevation = 1.dp,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.conceptName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = item.status.label + if (item.notes.isNotBlank()) " • ${item.notes}" else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDelete(item.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Remove progress entry",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
