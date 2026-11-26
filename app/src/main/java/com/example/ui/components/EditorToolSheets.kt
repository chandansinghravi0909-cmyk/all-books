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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.PageEntity
import com.example.ui.theme.AppFontCatalog
import com.example.ui.theme.InkSwatches
import com.example.ui.theme.PaperTone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorFormattingSheet(
    handwritingMode: Boolean,
    fontKey: String,
    fontSizeSp: Int,
    inkColorHex: Long,
    lineSpacing: Double,
    textAlignment: String,
    bold: Boolean,
    italic: Boolean,
    paperColorKey: String,
    onToggleHandwritingMode: (Boolean) -> Unit,
    onSelectFont: (String) -> Unit,
    onChangeFontSize: (Int) -> Unit,
    onSelectInkColor: (Long) -> Unit,
    onSelectLineSpacing: (Double) -> Unit,
    onSelectAlignment: (String) -> Unit,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onSelectPaperTone: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Paper & Typography Controls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Text("Writing Style", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = handwritingMode,
                    onClick = { onToggleHandwritingMode(true) },
                    label = { Text("Pen Handwriting") },
                    modifier = Modifier.testTag("mode_handwriting_chip")
                )
                FilterChip(
                    selected = !handwritingMode,
                    onClick = { onToggleHandwritingMode(false) },
                    label = { Text("Clean Print") },
                    modifier = Modifier.testTag("mode_normal_chip")
                )
            }
            Text(
                text = if (handwritingMode) {
                    "Natural human pen handwriting aligned to notebook lines (Kalam supports English & हिन्दी)."
                } else {
                    "Clean, crisp print typography aligned to ruled lines."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Font Family", style = MaterialTheme.typography.labelMedium)
            val fonts = if (handwritingMode) AppFontCatalog.handwritingFonts else AppFontCatalog.normalFonts
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                fonts.forEach { f ->
                    FilterChip(
                        selected = fontKey == f.key,
                        onClick = { onSelectFont(f.key) },
                        label = { Text(f.displayName) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Font Size: ${fontSizeSp}sp", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = false,
                        onClick = { if (fontSizeSp > 12) onChangeFontSize(fontSizeSp - 1) },
                        label = { Text("A-") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { if (fontSizeSp < 30) onChangeFontSize(fontSizeSp + 1) },
                        label = { Text("A+") }
                    )
                }
            }

            Text("Style & Alignment", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = bold, onClick = onToggleBold, label = { Text("Bold", fontWeight = FontWeight.Bold) })
                FilterChip(selected = italic, onClick = onToggleItalic, label = { Text("Italic") })
                listOf("START" to "Left", "CENTER" to "Center", "END" to "Right").forEach { (key, label) ->
                    FilterChip(
                        selected = textAlignment == key,
                        onClick = { onSelectAlignment(key) },
                        label = { Text(label) }
                    )
                }
            }

            Text("Line Spacing", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1.35 to "Compact", 1.55 to "Standard", 1.85 to "Wide").forEach { (mult, label) ->
                    FilterChip(
                        selected = kotlin.math.abs(lineSpacing - mult) < 0.08,
                        onClick = { onSelectLineSpacing(mult) },
                        label = { Text(label) }
                    )
                }
            }

            Text("Ink Color", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InkSwatches.options.forEach { (label, hex) ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .border(
                                width = if (inkColorHex == hex) 3.dp else 1.dp,
                                color = if (inkColorHex == hex) MaterialTheme.colorScheme.primary else Color.LightGray,
                                shape = CircleShape
                            )
                            .clickable { onSelectInkColor(hex) }
                    )
                }
            }

            Text("A4 Paper Tone", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaperTone.entries.forEach { tone ->
                    FilterChip(
                        selected = paperColorKey == tone.key,
                        onClick = { onSelectPaperTone(tone.key) },
                        label = { Text(tone.label) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageManagerSheet(
    pages: List<PageEntity>,
    currentPageId: String,
    onSelectPage: (PageEntity) -> Unit,
    onCreateNewPage: () -> Unit,
    onRenamePage: (PageEntity, String) -> Unit,
    onDuplicatePage: (PageEntity) -> Unit,
    onSwapPageOrder: (PageEntity, PageEntity) -> Unit,
    onDeletePage: (PageEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<PageEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<PageEntity?>(null) }

    val filtered = remember(pages, search) {
        val q = search.trim().lowercase()
        pages.filter {
            q.isBlank() || it.title.lowercase().contains(q) || it.content.lowercase().contains(q)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pages (${pages.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(onClick = { onCreateNewPage(); onDismiss() }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Page")
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search pages by title or text…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(filtered, key = { _, p -> p.id }) { idx, page ->
                    val isCurrent = page.id == currentPageId
                    Surface(
                        onClick = { onSelectPage(page); onDismiss() },
                        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Page ${page.pageNumber}: ${page.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = page.content.ifBlank { "Blank ruled page" },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (search.isBlank() && idx > 0) {
                                IconButton(onClick = { onSwapPageOrder(page, filtered[idx - 1]) }) {
                                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                                }
                            }
                            if (search.isBlank() && idx < filtered.lastIndex) {
                                IconButton(onClick = { onSwapPageOrder(page, filtered[idx + 1]) }) {
                                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(onClick = { renameTarget = page }) {
                                Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDuplicatePage(page) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { deleteTarget = page },
                                modifier = Modifier.testTag("delete_page_${page.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete page",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        var newTitle by remember(target.id) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Page") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Page Title") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRenamePage(target, newTitle)
                    renameTarget = null
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Page ${target.pageNumber}?") },
            text = {
                Text(
                    if (pages.size <= 1) {
                        "Delete \"${target.title}\"? This is the last page here, so you will return to your notebook."
                    } else {
                        "Delete \"${target.title}\"? Remaining pages will be renumbered automatically."
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    onDeletePage(target)
                    deleteTarget = null
                    onDismiss()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}
