package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.platform.ExportBackupManager
import com.example.data.model.AttachmentEntity
import com.example.data.model.FitnessLogEntity
import com.example.data.model.TodoTaskEntity
import com.example.data.model.VocabularyEntryEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FitnessTrackerSection(
    notebookId: String,
    logs: List<FitnessLogEntity>,
    onAddLog: (FitnessLogEntity) -> Unit,
    onDeleteLog: (String) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    val prCount = logs.count { it.personalRecord }
    val totalSets = logs.sumOf { it.sets }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatMiniCard("Logged Sessions", logs.size.toString(), Modifier.weight(1f))
            StatMiniCard("Total Sets", totalSets.toString(), Modifier.weight(1f))
            StatMiniCard("Personal Records", prCount.toString(), Modifier.weight(1f))
        }

        Button(
            onClick = { showAdd = true },
            modifier = Modifier.fillMaxWidth().testTag("add_fitness_log_button")
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Log Exercise / Hypertrophy Set")
        }

        if (logs.isEmpty()) {
            EmptyToolHint("No workout sets logged yet. Track exercises, weights, reps, RIR, and PRs here.")
        } else {
            logs.forEach { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = log.exerciseName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (log.personalRecord) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Filled.EmojiEvents,
                                            contentDescription = "Personal Record",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${log.muscleGroup.ifBlank { "Strength" }} • ${
                                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(log.workoutDateMillis))
                                    }",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDeleteLog(log.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete log")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${log.sets} sets × [${log.reps}] @ ${log.weightKg} kg  •  RIR ${log.rir}  •  RPE ${log.rpe}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (log.notes.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(log.notes, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var exercise by remember { mutableStateOf("") }
        var muscle by remember { mutableStateOf("") }
        var sets by remember { mutableStateOf("3") }
        var reps by remember { mutableStateOf("10, 10, 9") }
        var weight by remember { mutableStateOf("20.0") }
        var rir by remember { mutableStateOf("2") }
        var rpe by remember { mutableStateOf("8.0") }
        var isPr by remember { mutableStateOf(false) }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Log Workout Set") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = exercise, onValueChange = { exercise = it }, label = { Text("Exercise Name *") }, singleLine = true)
                    OutlinedTextField(value = muscle, onValueChange = { muscle = it }, label = { Text("Muscle Group") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = sets, onValueChange = { sets = it }, label = { Text("Sets") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Weight kg") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    OutlinedTextField(value = reps, onValueChange = { reps = it }, label = { Text("Repetitions (e.g. 10, 10, 8)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = rir, onValueChange = { rir = it }, label = { Text("RIR (0-5)") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = rpe, onValueChange = { rpe = it }, label = { Text("RPE (1-10)") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isPr, onCheckedChange = { isPr = it })
                        Text("Mark as Personal Record (PR)")
                    }
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Performance Notes") }, maxLines = 2)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddLog(
                            FitnessLogEntity(
                                notebookId = notebookId,
                                exerciseName = exercise.trim(),
                                muscleGroup = muscle.trim(),
                                sets = sets.toIntOrNull()?.coerceIn(1, 50) ?: 3,
                                reps = reps.ifBlank { "10" },
                                weightKg = weight.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                rir = rir.toIntOrNull()?.coerceIn(0, 10) ?: 2,
                                rpe = rpe.toDoubleOrNull()?.coerceIn(1.0, 10.0) ?: 8.0,
                                personalRecord = isPr,
                                notes = notes.trim()
                            )
                        )
                        showAdd = false
                    },
                    enabled = exercise.isNotBlank()
                ) { Text("Save Log") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VocabularySection(
    notebookId: String,
    entries: List<VocabularyEntryEntity>,
    onAddWord: (VocabularyEntryEntity) -> Unit,
    onToggleMastered: (VocabularyEntryEntity) -> Unit,
    onDeleteWord: (String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }

    val filtered = remember(entries, search) {
        val q = search.trim().lowercase()
        entries.filter {
            q.isBlank() || it.word.lowercase().contains(q) ||
                it.meaning.lowercase().contains(q) ||
                it.hindiMeaning.lowercase().contains(q)
        }.sortedBy { it.word.lowercase() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search vocabulary words (English / Hindi)…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { showAdd = true },
            modifier = Modifier.fillMaxWidth().testTag("add_vocab_word_button")
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add Vocabulary Entry")
        }

        filtered.forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.word,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (item.pronunciation.isNotBlank()) {
                                Text(
                                    text = item.pronunciation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        FilterChip(
                            selected = item.mastered,
                            onClick = { onToggleMastered(item) },
                            label = { Text(if (item.mastered) "Mastered" else "Learning") }
                        )
                        IconButton(onClick = { onDeleteWord(item.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete word")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(item.meaning, style = MaterialTheme.typography.bodyMedium)
                    if (item.hindiMeaning.isNotBlank()) {
                        Text(
                            text = "Hindi: ${item.hindiMeaning}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (item.synonyms.isNotEmpty()) {
                        Text(
                            text = "Synonyms: ${item.synonyms.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.antonyms.isNotEmpty()) {
                        Text(
                            text = "Antonyms: ${item.antonyms.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.exampleSentences.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        item.exampleSentences.forEach { ex ->
                            Text("• \"$ex\"", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var word by remember { mutableStateOf("") }
        var pronunciation by remember { mutableStateOf("") }
        var meaning by remember { mutableStateOf("") }
        var hindi by remember { mutableStateOf("") }
        var synonyms by remember { mutableStateOf("") }
        var antonyms by remember { mutableStateOf("") }
        var example by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add Vocabulary Word") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = word, onValueChange = { word = it }, label = { Text("Word *") }, singleLine = true)
                    OutlinedTextField(value = pronunciation, onValueChange = { pronunciation = it }, label = { Text("Pronunciation (e.g. /rɪˈzɪljənt/)") }, singleLine = true)
                    OutlinedTextField(value = meaning, onValueChange = { meaning = it }, label = { Text("English Meaning *") }, maxLines = 2)
                    OutlinedTextField(value = hindi, onValueChange = { hindi = it }, label = { Text("Hindi Meaning (हिंदी अर्थ)") }, singleLine = true)
                    OutlinedTextField(value = synonyms, onValueChange = { synonyms = it }, label = { Text("Synonyms (comma separated)") }, singleLine = true)
                    OutlinedTextField(value = antonyms, onValueChange = { antonyms = it }, label = { Text("Antonyms (comma separated)") }, singleLine = true)
                    OutlinedTextField(value = example, onValueChange = { example = it }, label = { Text("Example Sentence") }, maxLines = 2)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddWord(
                            VocabularyEntryEntity(
                                notebookId = notebookId,
                                word = word.trim(),
                                pronunciation = pronunciation.trim(),
                                meaning = meaning.trim(),
                                hindiMeaning = hindi.trim(),
                                synonyms = synonyms.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                antonyms = antonyms.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                exampleSentences = listOf(example.trim()).filter { it.isNotBlank() }
                            )
                        )
                        showAdd = false
                    },
                    enabled = word.isNotBlank() && meaning.isNotBlank()
                ) { Text("Save Word") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun TodoTrackerSection(
    notebookId: String,
    todos: List<TodoTaskEntity>,
    onAddTodo: (TodoTaskEntity) -> Unit,
    onToggleCompleted: (TodoTaskEntity) -> Unit,
    onDeleteTodo: (String) -> Unit
) {
    var selectedCat by remember { mutableStateOf("ALL") }
    var showAdd by remember { mutableStateOf(false) }
    val categories = listOf("ALL", "TODAY", "TOMORROW", "UPCOMING", "PENDING", "IMPORTANT", "UNSCHEDULED")
    val now = System.currentTimeMillis()

    val filtered = remember(todos, selectedCat) {
        if (selectedCat == "ALL") todos else todos.filter { it.category == selectedCat }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { cat ->
                FilterChip(
                    selected = selectedCat == cat,
                    onClick = { selectedCat = cat },
                    label = { Text(cat.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        Button(
            onClick = { showAdd = true },
            modifier = Modifier.fillMaxWidth().testTag("add_todo_task_button")
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add Task")
        }

        filtered.forEach { task ->
            val isOverdue = !task.completed && task.dueDateMillis != null && task.dueDateMillis < now
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onToggleCompleted(task) }) {
                        Icon(
                            imageVector = if (task.completed) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = "Toggle completion",
                            tint = if (task.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                        )
                        if (task.description.isNotBlank()) {
                            Text(task.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "${task.category} • ${task.priority}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            if (task.dueDateMillis != null) {
                                Text(
                                    text = (if (isOverdue) "Overdue: " else "Due: ") +
                                        SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(task.dueDateMillis)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    IconButton(onClick = { onDeleteTodo(task.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete task")
                    }
                }
            }
        }
    }

    if (showAdd) {
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var cat by remember { mutableStateOf("TODAY") }
        var priority by remember { mutableStateOf("MEDIUM") }
        var dueDays by remember { mutableIntStateOf(0) }

        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New To-Do Task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task Title *") }, singleLine = true)
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Details") }, maxLines = 2)
                    Text("Category", style = MaterialTheme.typography.labelSmall)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("TODAY", "TOMORROW", "UPCOMING", "PENDING", "IMPORTANT", "UNSCHEDULED").forEach { c ->
                            FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) })
                        }
                    }
                    Text("Priority", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("LOW", "MEDIUM", "HIGH", "URGENT").forEach { p ->
                            FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p) })
                        }
                    }
                    Text("Due Schedule", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "Today", 1 to "Tomorrow", 7 to "+7d", -1 to "None").forEach { (days, lbl) ->
                            FilterChip(selected = dueDays == days, onClick = { dueDays = days }, label = { Text(lbl) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val due = if (dueDays >= 0) System.currentTimeMillis() + dueDays * 86_400_000L else null
                        onAddTodo(
                            TodoTaskEntity(
                                notebookId = notebookId,
                                title = title.trim(),
                                description = desc.trim(),
                                category = cat,
                                priority = priority,
                                dueDateMillis = due
                            )
                        )
                        showAdd = false
                    },
                    enabled = title.isNotBlank()
                ) { Text("Add Task") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SavedWorkSection(
    notebookId: String,
    attachments: List<AttachmentEntity>,
    onAddAttachment: (AttachmentEntity) -> Unit,
    onUpdateStatus: (String, String) -> Unit,
    onDeleteAttachment: (String) -> Unit
) {
    val context = LocalContext.current
    var showAddText by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf("ALL") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val savedPath = ExportBackupManager.copyAttachmentToInternalStorage(context, uri, "saved_work")
            onAddAttachment(
                AttachmentEntity(
                    notebookId = notebookId,
                    fileName = File(savedPath).name,
                    fileType = "SCREENSHOT",
                    localUriOrPath = savedPath,
                    caption = "Image / Screenshot saved from gallery",
                    sourceLabel = "Gallery / Screenshots",
                    status = "SAVED",
                    priority = "MEDIUM"
                )
            )
        }
    }

    val filtered = remember(attachments, statusFilter) {
        if (statusFilter == "ALL") attachments else attachments.filter { it.status == statusFilter }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("ALL", "SAVED", "NOT_STARTED", "IN_PROGRESS", "COMPLETED").forEach { st ->
                FilterChip(
                    selected = statusFilter == st,
                    onClick = { statusFilter = st },
                    label = { Text(st.replace("_", " ")) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.weight(1f).testTag("pick_saved_image_button")
            ) {
                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Attach Screenshot")
            }
            OutlinedButton(
                onClick = { showAddText = true },
                modifier = Modifier.weight(1f).testTag("add_saved_note_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save Message / Link")
            }
        }

        filtered.forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.fileName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "${item.sourceLabel} • Priority: ${item.priority}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { onDeleteAttachment(item.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete saved item")
                        }
                    }
                    if (item.localUriOrPath.isNotBlank() && File(item.localUriOrPath).exists()) {
                        Spacer(Modifier.height(8.dp))
                        AsyncImage(
                            model = File(item.localUriOrPath),
                            contentDescription = item.fileName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(MaterialTheme.shapes.small)
                        )
                    }
                    if (item.caption.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(item.caption, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("SAVED", "NOT_STARTED", "IN_PROGRESS", "COMPLETED").forEach { st ->
                            AssistChip(
                                onClick = { onUpdateStatus(item.id, st) },
                                label = {
                                    Text(
                                        text = st.replace("_", " "),
                                        fontWeight = if (item.status == st) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddText) {
        var title by remember { mutableStateOf("") }
        var source by remember { mutableStateOf("WhatsApp Messages") }
        var caption by remember { mutableStateOf("") }
        var priority by remember { mutableStateOf("MEDIUM") }

        AlertDialog(
            onDismissRequest = { showAddText = false },
            title = { Text("Save Work Item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title / File Name *") }, singleLine = true)
                    OutlinedTextField(value = source, onValueChange = { source = it }, label = { Text("Source (WhatsApp, Online Content, etc.)") }, singleLine = true)
                    OutlinedTextField(value = caption, onValueChange = { caption = it }, label = { Text("Saved Content / Description") }, maxLines = 4)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("LOW", "MEDIUM", "HIGH").forEach { p ->
                            FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddAttachment(
                            AttachmentEntity(
                                notebookId = notebookId,
                                fileName = title.trim(),
                                fileType = "DOCUMENT",
                                localUriOrPath = "",
                                caption = caption.trim(),
                                sourceLabel = source.trim().ifBlank { "Saved Messages" },
                                status = "SAVED",
                                priority = priority
                            )
                        )
                        showAddText = false
                    },
                    enabled = title.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddText = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun HandwritingPracticeSection(
    onStartPracticePage: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Handwriting Practice Studio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Launch a dedicated A4 ruled practice page pre-configured with a handwriting-style font (Kalam / Patrick Hand) for letter formation, spacing drills, and bilingual English-Hindi practice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onStartPracticePage,
                modifier = Modifier.fillMaxWidth().testTag("start_handwriting_practice_button")
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open New A4 Practice Sheet")
            }
        }
    }
}

@Composable
private fun StatMiniCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyToolHint(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}
