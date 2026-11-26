package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.GlobalSearchResult
import com.example.data.model.NotebookEntity
import com.example.ui.components.NotebookEditorDialog
import com.example.ui.components.NotebookIcons
import com.example.ui.components.formatTimestampShort
import com.example.ui.viewmodel.LibrarySnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    snapshot: LibrarySnapshot,
    onOpenNotebook: (NotebookEntity) -> Unit,
    onOpenSearchResult: (GlobalSearchResult) -> Unit,
    onSearchGlobally: (String) -> List<GlobalSearchResult>,
    onTogglePin: (NotebookEntity) -> Unit,
    onToggleLayout: () -> Unit,
    onCreateNotebook: (
        name: String,
        purpose: String,
        shortDesc: String,
        detailedDesc: String,
        iconKey: String,
        colorHex: Long,
        coverStyle: String
    ) -> Unit,
    onOpenSettings: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }

    val visibleNotebooks = remember(snapshot.notebooks, snapshot.preferences.showArchivedOnDashboard) {
        snapshot.notebooks.filter { nb ->
            !nb.hidden && (snapshot.preferences.showArchivedOnDashboard || !nb.archived)
        }
    }

    val pinnedNotebooks = remember(visibleNotebooks) {
        visibleNotebooks.filter { it.pinned }
    }

    val recentlyOpened = remember(visibleNotebooks) {
        visibleNotebooks.filter { it.lastOpenedAt != null }
            .sortedByDescending { it.lastOpenedAt?.seconds ?: 0L }
            .take(5)
    }

    val recentlyEdited = remember(visibleNotebooks, snapshot.pages, snapshot.topics) {
        val latestPageByNb = snapshot.pages.groupBy { it.notebookId }
            .mapValues { (_, list) -> list.maxOfOrNull { it.updatedAt?.seconds ?: 0L } ?: 0L }
        visibleNotebooks.sortedByDescending { nb ->
            maxOf(nb.updatedAt?.seconds ?: 0L, latestPageByNb[nb.id] ?: 0L)
        }.take(5)
    }

    val topicCounts = remember(snapshot.topics) {
        snapshot.topics.groupingBy { it.notebookId }.eachCount()
    }
    val pageCounts = remember(snapshot.pages) {
        snapshot.pages.groupingBy { it.notebookId }.eachCount()
    }

    val searchResults = remember(searchQuery, snapshot) {
        if (searchQuery.isBlank()) emptyList() else onSearchGlobally(searchQuery)
    }

    val isGrid = snapshot.preferences.dashboardLayout == "GRID"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = snapshot.preferences.customAppName.ifBlank { "Knowledge Library" },
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${visibleNotebooks.size} Notebooks • ${snapshot.topics.size} Topics • ${snapshot.pages.size} Pages",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onToggleLayout,
                        modifier = Modifier.testTag("toggle_layout_button")
                    ) {
                        Icon(
                            imageVector = if (isGrid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = if (isGrid) "Switch to list view" else "Switch to grid view"
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings and customization"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.testTag("create_notebook_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create new notebook")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search notebooks, topics, pages, Hindi/English notes, vocabulary…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("global_search_input")
            )

            if (searchQuery.isNotBlank()) {
                GlobalSearchResultsList(
                    results = searchResults,
                    onSelect = onOpenSearchResult
                )
            } else if (isGrid) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 172.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        DashboardHighlights(
                            pinnedNotebooks = pinnedNotebooks,
                            recentlyOpened = recentlyOpened,
                            recentlyEdited = recentlyEdited,
                            onOpenNotebook = onOpenNotebook
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "ALL NOTEBOOKS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                        )
                    }

                    items(visibleNotebooks, key = { it.id }) { notebook ->
                        NotebookGridCard(
                            notebook = notebook,
                            topicCount = topicCounts[notebook.id] ?: 0,
                            pageCount = pageCounts[notebook.id] ?: 0,
                            onOpen = { onOpenNotebook(notebook) },
                            onTogglePin = { onTogglePin(notebook) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        DashboardHighlights(
                            pinnedNotebooks = pinnedNotebooks,
                            recentlyOpened = recentlyOpened,
                            recentlyEdited = recentlyEdited,
                            onOpenNotebook = onOpenNotebook
                        )
                    }

                    item {
                        Text(
                            text = "ALL NOTEBOOKS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                        )
                    }

                    items(visibleNotebooks, key = { it.id }) { notebook ->
                        NotebookListCard(
                            notebook = notebook,
                            topicCount = topicCounts[notebook.id] ?: 0,
                            pageCount = pageCounts[notebook.id] ?: 0,
                            onOpen = { onOpenNotebook(notebook) },
                            onTogglePin = { onTogglePin(notebook) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        NotebookEditorDialog(
            initial = null,
            defaultCoverStyle = snapshot.preferences.defaultCoverStyle,
            onDismiss = { showCreateDialog = false },
            onSave = { name, purpose, shortDesc, detailedDesc, iconKey, colorHex, coverStyle ->
                onCreateNotebook(name, purpose, shortDesc, detailedDesc, iconKey, colorHex, coverStyle)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun DashboardHighlights(
    pinnedNotebooks: List<NotebookEntity>,
    recentlyOpened: List<NotebookEntity>,
    recentlyEdited: List<NotebookEntity>,
    onOpenNotebook: (NotebookEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(MaterialTheme.shapes.large)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(R.drawable.img_library_banner)
                    .crossfade(true)
                    .build(),
                contentDescription = "Personal Knowledge Shelf",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xCC111814),
                                Color(0x66111814),
                                Color.Transparent
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Digital Notebook Collection",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Structured topics • A4 ruled writing • Voice & Gemini AI",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE5E7EB)
                )
            }
        }

        if (pinnedNotebooks.isNotEmpty()) {
            Text(
                text = "PINNED NOTEBOOKS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pinnedNotebooks.forEach { nb ->
                    AssistChip(
                        onClick = { onOpenNotebook(nb) },
                        leadingIcon = {
                            Icon(
                                imageVector = NotebookIcons.resolve(nb.iconKey),
                                contentDescription = null,
                                tint = Color(nb.coverColorHex),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = {
                            Text("${nb.notebookNumber.toString().padStart(2, '0')} • ${nb.name}")
                        }
                    )
                }
            }
        }

        if (recentlyOpened.isNotEmpty() || recentlyEdited.isNotEmpty()) {
            val combined = (recentlyOpened + recentlyEdited).distinctBy { it.id }.take(5)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (recentlyOpened.isNotEmpty()) "RECENTLY OPENED & EDITED" else "RECENTLY EDITED",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                combined.forEach { nb ->
                    Surface(
                        tonalElevation = 2.dp,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.clickable { onOpenNotebook(nb) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(nb.coverColorHex))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = nb.name,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotebookGridCard(
    notebook: NotebookEntity,
    topicCount: Int,
    pageCount: Int,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit
) {
    val coverColor = Color(notebook.coverColorHex)
    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notebook_card_${notebook.id}")
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Tactile physical notebook spine
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(216.dp)
                    .background(
                        if (notebook.coverStyle == "SPIRAL") {
                            Brush.verticalGradient(listOf(coverColor, coverColor.copy(alpha = 0.7f)))
                        } else {
                            Brush.verticalGradient(listOf(coverColor, coverColor))
                        }
                    )
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
                    .height(192.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = coverColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "NB ${notebook.notebookNumber.toString().padStart(2, '0')}",
                                style = MaterialTheme.typography.labelSmall,
                                color = coverColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                        IconButton(
                            onClick = onTogglePin,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (notebook.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (notebook.pinned) "Unpin notebook" else "Pin notebook",
                                tint = if (notebook.pinned) coverColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = NotebookIcons.resolve(notebook.iconKey),
                            contentDescription = null,
                            tint = coverColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = notebook.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = notebook.shortDescription.ifBlank { notebook.purpose },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column {
                    Text(
                        text = "$topicCount topics • $pageCount pages",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTimestampShort(notebook.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open notebook",
                            tint = coverColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotebookListCard(
    notebook: NotebookEntity,
    topicCount: Int,
    pageCount: Int,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit
) {
    val coverColor = Color(notebook.coverColorHex)
    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notebook_list_card_${notebook.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(coverColor)
            )
            Spacer(Modifier.width(12.dp))
            Surface(
                color = coverColor.copy(alpha = 0.14f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = NotebookIcons.resolve(notebook.iconKey),
                        contentDescription = null,
                        tint = coverColor
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${notebook.notebookNumber.toString().padStart(2, '0')} — ${notebook.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = notebook.purpose.ifBlank { notebook.shortDescription },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$topicCount topics • $pageCount pages • Updated ${formatTimestampShort(notebook.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onTogglePin) {
                Icon(
                    imageVector = if (notebook.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = "Pin toggle",
                    tint = if (notebook.pinned) coverColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onOpen) {
                Text("Open")
            }
        }
    }
}

@Composable
private fun GlobalSearchResultsList(
    results: List<GlobalSearchResult>,
    onSelect: (GlobalSearchResult) -> Unit
) {
    if (results.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No matching notebooks, topics, pages, or vocabulary found.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "${results.size} Search Results",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        items(results, key = { it.id }) { res ->
            Card(
                onClick = { onSelect(res) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${res.kind.label} • ${res.notebookName}" +
                                (res.topicName?.let { " / $it" } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = res.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (res.snippet.isNotBlank()) {
                            Text(
                                text = res.snippet,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    TextButton(onClick = { onSelect(res) }) {
                        Text("Open")
                    }
                }
            }
        }
    }
}
