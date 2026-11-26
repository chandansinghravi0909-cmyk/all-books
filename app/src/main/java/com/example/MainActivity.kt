package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.authStateFlow
import com.example.core.configureAppCheck
import com.example.core.signOutUser
import com.example.data.ai.AiAgentStorage
import com.example.data.ai.AiContext
import com.example.data.ai.AiProposedAction
import com.example.data.ai.AiUndoSnapshot
import com.example.data.ai.AndroidNetworkConnectivityChecker
import com.example.data.ai.FirestoreNotebookActionBridge
import com.example.data.ai.NotebookAiRepository
import com.example.data.model.NotebookEntity
import com.example.data.model.PageEntity
import com.example.data.model.SearchResultKind
import com.example.data.model.SubtopicEntity
import com.example.data.model.TopicEntity
import com.example.data.model.UiState
import com.example.data.repository.LibraryRepository
import com.example.ui.components.AiAssistantSheet
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.NotebookHomeScreen
import com.example.ui.screens.PhysicalNotebookEditorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SubtopicInfoScreen
import com.example.ui.screens.TopicInfoScreen
import com.example.ui.theme.KnowledgeLibraryTheme
import com.example.ui.viewmodel.AiAgentUiState
import com.example.ui.viewmodel.AiAssistantViewModel
import com.example.ui.viewmodel.LibrarySnapshot
import com.example.ui.viewmodel.LibraryViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // App Check MUST be configured before any Firebase AI / Firestore calls
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            val currentUser by Firebase.auth.authStateFlow()
                .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

            val activeUser = currentUser
            if (activeUser == null) {
                KnowledgeLibraryTheme {
                    AuthScreen(onAuthSuccess = {
                        // Auth state flow updates automatically when Firebase Auth completes
                    })
                }
            } else {
                key(activeUser.uid) {
                    AuthenticatedLibraryApp(
                        userId = activeUser.uid,
                        userEmail = activeUser.email
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
    }
}

@Composable
private fun AuthenticatedLibraryApp(
    userId: String,
    userEmail: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val libraryViewModel: LibraryViewModel = viewModel(
        key = "lib_$userId",
        factory = remember(userId, context) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LibraryViewModel(
                        repository = LibraryRepository(context.applicationContext),
                        currentUserId = userId
                    ) as T
                }
            }
        }
    )

    val aiViewModel: AiAssistantViewModel = viewModel(
        key = "ai_$userId",
        factory = remember(userId, context) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appCtx = context.applicationContext
                    return AiAssistantViewModel(
                        aiRepository = NotebookAiRepository(
                            connectivityChecker = AndroidNetworkConnectivityChecker(appCtx)
                        ),
                        storage = AiAgentStorage(appCtx, userId)
                    ) as T
                }
            }
        }
    )

    val uiState by libraryViewModel.libraryState.collectAsStateWithLifecycle()
    val statusBanner by libraryViewModel.statusBanner.collectAsStateWithLifecycle()
    val agentState by aiViewModel.agentState.collectAsStateWithLifecycle()
    val topicGenState by aiViewModel.topicGenState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            libraryViewModel.clearStatusBanner()
        }
    }

    val prefs = (uiState as? UiState.Success<LibrarySnapshot>)?.data?.preferences

    KnowledgeLibraryTheme(
        themeMode = prefs?.themeMode ?: "SYSTEM",
        accentColorKey = prefs?.accentColorKey ?: "FOREST",
        uiFontKey = prefs?.uiFontKey ?: "plus_jakarta_sans"
    ) {
        when (val state = uiState) {
            is UiState.Loading -> {
                Scaffold { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "Loading your 13 Notebooks…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            is UiState.Error -> {
                Scaffold { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Unable to load Knowledge Library",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    signOutUser(credentialManager, scope)
                                }
                            ) {
                                Text("Sign Out & Retry")
                            }
                        }
                    }
                }
            }

            is UiState.Success -> {
                LibraryNavHost(
                    snapshot = state.data,
                    userId = userId,
                    userEmail = userEmail,
                    snackbarHostState = snackbarHostState,
                    libraryViewModel = libraryViewModel,
                    aiViewModel = aiViewModel,
                    agentState = agentState,
                    topicGenState = topicGenState,
                    onSignOut = { signOutUser(credentialManager, scope) }
                )
            }
        }
    }
}

@Composable
private fun LibraryNavHost(
    snapshot: LibrarySnapshot,
    userId: String,
    userEmail: String?,
    snackbarHostState: SnackbarHostState,
    libraryViewModel: LibraryViewModel,
    aiViewModel: AiAssistantViewModel,
    agentState: AiAgentUiState,
    topicGenState: com.example.ui.viewmodel.TopicGenUiState,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    var activeAiContext by remember { mutableStateOf<AiContext?>(null) }
    var aiCalledFromEditor by remember { mutableStateOf(false) }
    var activeEditorPageContext by remember { mutableStateOf<PageEntity?>(null) }
    var pendingAiInsertion by remember { mutableStateOf<String?>(null) }
    var editorReloadNonce by remember { mutableIntStateOf(0) }

    val aiActionBridge = remember(userId, libraryViewModel) {
        FirestoreNotebookActionBridge(
            userId = userId,
            createPageOp = libraryViewModel::createPageSuspend,
            updatePageOp = libraryViewModel::savePageFieldsSuspend,
            deletePageOp = libraryViewModel::deletePageSuspend,
            createTopicOp = libraryViewModel::createTopicSuspend,
            createSubtopicOp = libraryViewModel::createSubtopicSuspend
        )
    }

    fun openAiAgent(
        fromEditor: Boolean,
        notebook: NotebookEntity?,
        topic: TopicEntity? = null,
        subtopic: SubtopicEntity? = null,
        currentPage: PageEntity? = null,
        personalNotes: String = "",
        currentPageText: String = "",
        selectedText: String = ""
    ) {
        val richContext = buildRichAiContext(
            snapshot = snapshot,
            notebook = notebook,
            topic = topic,
            subtopic = subtopic,
            currentPage = currentPage,
            personalNotes = personalNotes,
            currentPageText = currentPageText,
            selectedText = selectedText
        )
        aiCalledFromEditor = fromEditor
        activeEditorPageContext = currentPage
        aiViewModel.bindToContext(richContext)
        activeAiContext = richContext
    }

    fun createNewPageAndOpen(
        notebookId: String,
        topicId: String?,
        subtopicId: String?,
        handwritingDefault: Boolean = true,
        initialTitle: String? = null,
        initialContent: String = "",
        replaceCurrentEditor: Boolean = false
    ) {
        val scopedPages = snapshot.pages.filter {
            it.notebookId == notebookId &&
                (it.topicId ?: "") == (topicId ?: "") &&
                (it.subtopicId ?: "") == (subtopicId ?: "")
        }
        val nextNum = (scopedPages.maxOfOrNull { it.pageNumber } ?: 0) + 1
        val prefs = snapshot.preferences
        val newPage = PageEntity(
            notebookId = notebookId,
            topicId = topicId,
            subtopicId = subtopicId,
            title = initialTitle ?: "Page $nextNum",
            content = initialContent,
            pageNumber = nextNum,
            handwritingMode = handwritingDefault,
            fontKey = if (handwritingDefault) prefs.handwritingFontKey else prefs.writingFontKey,
            fontSizeSp = prefs.defaultFontSizeSp,
            lineSpacingMultiplier = prefs.lineSpacingMultiplier,
            paperColorKey = prefs.paperColorKey,
            inkColorHex = if (handwritingDefault) 0xFF1B3F8BL else 0xFF1C2430L
        )
        libraryViewModel.createPage(newPage) { createdId ->
            navController.navigate("editor/${Uri.encode(notebookId)}/${Uri.encode(createdId)}") {
                if (replaceCurrentEditor) {
                    popUpTo("editor/{notebookId}/{pageId}") { inclusive = true }
                }
            }
        }
    }

    fun openOrCreateScopeWriting(
        notebookId: String,
        topicId: String?,
        subtopicId: String?,
        handwritingDefault: Boolean
    ) {
        val existing = snapshot.pages
            .filter {
                it.notebookId == notebookId &&
                    (it.topicId ?: "") == (topicId ?: "") &&
                    (it.subtopicId ?: "") == (subtopicId ?: "")
            }
            .sortedBy { it.pageNumber }
            .firstOrNull()
        if (existing != null) {
            navController.navigate("editor/${Uri.encode(notebookId)}/${Uri.encode(existing.id)}")
        } else {
            createNewPageAndOpen(notebookId, topicId, subtopicId, handwritingDefault)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "dashboard"
        ) {
            composable("dashboard") {
                DashboardScreen(
                    snapshot = snapshot,
                    onOpenNotebook = { nb ->
                        libraryViewModel.markNotebookOpened(nb.id)
                        navController.navigate("notebook/${Uri.encode(nb.id)}")
                    },
                    onOpenSearchResult = { result ->
                        when (result.kind) {
                            SearchResultKind.NOTEBOOK,
                            SearchResultKind.VOCABULARY,
                            SearchResultKind.SAVED_WORK -> {
                                libraryViewModel.markNotebookOpened(result.notebookId)
                                navController.navigate("notebook/${Uri.encode(result.notebookId)}")
                            }
                            SearchResultKind.TOPIC -> {
                                val tId = result.topicId
                                if (!tId.isNullOrBlank()) {
                                    navController.navigate("topic/${Uri.encode(result.notebookId)}/${Uri.encode(tId)}")
                                } else {
                                    navController.navigate("notebook/${Uri.encode(result.notebookId)}")
                                }
                            }
                            SearchResultKind.SUBTOPIC -> {
                                val tId = result.topicId
                                val sId = result.subtopicId
                                if (!tId.isNullOrBlank() && !sId.isNullOrBlank()) {
                                    navController.navigate(
                                        "subtopic/${Uri.encode(result.notebookId)}/${Uri.encode(tId)}/${Uri.encode(sId)}"
                                    )
                                } else {
                                    navController.navigate("notebook/${Uri.encode(result.notebookId)}")
                                }
                            }
                            SearchResultKind.PAGE -> {
                                val pId = result.pageId
                                if (!pId.isNullOrBlank()) {
                                    navController.navigate("editor/${Uri.encode(result.notebookId)}/${Uri.encode(pId)}")
                                } else {
                                    navController.navigate("notebook/${Uri.encode(result.notebookId)}")
                                }
                            }
                        }
                    },
                    onSearchGlobally = { query -> libraryViewModel.searchGlobally(query, snapshot) },
                    onTogglePin = libraryViewModel::togglePinNotebook,
                    onToggleLayout = {
                        val nextLayout = if (snapshot.preferences.dashboardLayout == "GRID") "LIST" else "GRID"
                        libraryViewModel.savePreferences(snapshot.preferences.copy(dashboardLayout = nextLayout))
                    },
                    onCreateNotebook = { name, purpose, shortDesc, detailedDesc, iconKey, colorHex, coverStyle ->
                        libraryViewModel.createNotebook(
                            name = name,
                            purpose = purpose,
                            shortDescription = shortDesc,
                            detailedDescription = detailedDesc,
                            iconKey = iconKey,
                            coverColorHex = colorHex,
                            coverStyle = coverStyle,
                            existingCount = snapshot.notebooks.size
                        )
                    },
                    onOpenSettings = { navController.navigate("settings") }
                )
            }

            composable(
                route = "notebook/{notebookId}",
                arguments = listOf(navArgument("notebookId") { type = NavType.StringType })
            ) { backStackEntry ->
                val nbId = Uri.decode(backStackEntry.arguments?.getString("notebookId") ?: "")
                val notebook = snapshot.notebooks.firstOrNull { it.id == nbId }
                if (notebook == null) {
                    MissingEntityScreen("Notebook not found") { navController.popBackStack() }
                } else {
                    NotebookHomeScreen(
                        notebook = notebook,
                        snapshot = snapshot,
                        topicGenState = topicGenState,
                        onBack = { navController.popBackStack() },
                        onOpenTopic = { topic ->
                            navController.navigate("topic/${Uri.encode(nbId)}/${Uri.encode(topic.id)}")
                        },
                        onOpenPage = { page ->
                            navController.navigate("editor/${Uri.encode(nbId)}/${Uri.encode(page.id)}")
                        },
                        onDeletePage = { page ->
                            val scopePages = snapshot.pages
                                .filter { it.notebookId == nbId && it.topicId.isNullOrBlank() && it.subtopicId.isNullOrBlank() }
                                .sortedBy { it.pageNumber }
                            libraryViewModel.deletePageAndRenumber(page, scopePages)
                        },
                        onStartQuickWriting = { _ ->
                            openOrCreateScopeWriting(nbId, null, null, handwritingDefault = true)
                        },
                        onOpenAiAssistant = {
                            openAiAgent(
                                fromEditor = false,
                                notebook = notebook
                            )
                        },
                        onCreateTopic = { topic, starterSubs ->
                            libraryViewModel.createTopic(topic, starterSubs)
                        },
                        onGenerateTopicWithAi = { topicName ->
                            aiViewModel.generateTopicMetadata(
                                notebookName = notebook.name,
                                notebookPurpose = notebook.purpose,
                                topicName = topicName,
                                languagePref = snapshot.preferences.aiResponseLanguage
                            )
                        },
                        onResetTopicAiGen = aiViewModel::resetTopicGenState,
                        onSwapTopicOrder = libraryViewModel::swapTopicOrder,
                        onDeleteTopic = libraryViewModel::deleteTopic,
                        onUpdateNotebook = { name, purpose, shortDesc, detailedDesc, iconKey, colorHex, coverStyle ->
                            libraryViewModel.updateNotebookDetails(
                                nbId, name, purpose, shortDesc, detailedDesc, iconKey, colorHex, coverStyle
                            )
                        },
                        onAddFitnessLog = libraryViewModel::createFitnessLog,
                        onDeleteFitnessLog = libraryViewModel::deleteFitnessLog,
                        onAddVocabulary = libraryViewModel::createVocabulary,
                        onToggleVocabularyMastered = libraryViewModel::toggleVocabularyMastered,
                        onDeleteVocabulary = libraryViewModel::deleteVocabulary,
                        onAddTodo = libraryViewModel::createTodo,
                        onToggleTodoCompleted = libraryViewModel::toggleTodoCompleted,
                        onDeleteTodo = libraryViewModel::deleteTodo,
                        onAddAttachment = libraryViewModel::createAttachment,
                        onUpdateAttachmentStatus = libraryViewModel::updateAttachmentStatus,
                        onDeleteAttachment = libraryViewModel::deleteAttachment
                    )
                }
            }

            composable(
                route = "topic/{notebookId}/{topicId}",
                arguments = listOf(
                    navArgument("notebookId") { type = NavType.StringType },
                    navArgument("topicId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val nbId = Uri.decode(backStackEntry.arguments?.getString("notebookId") ?: "")
                val topicId = Uri.decode(backStackEntry.arguments?.getString("topicId") ?: "")
                val notebook = snapshot.notebooks.firstOrNull { it.id == nbId }
                val topic = snapshot.topics.firstOrNull { it.id == topicId }
                if (notebook == null || topic == null) {
                    MissingEntityScreen("Topic not found") { navController.popBackStack() }
                } else {
                    TopicInfoScreen(
                        notebook = notebook,
                        topic = topic,
                        snapshot = snapshot,
                        onBack = { navController.popBackStack() },
                        onBackToDashboard = {
                            navController.popBackStack("dashboard", inclusive = false)
                        },
                        onOpenSubtopic = { sub ->
                            navController.navigate(
                                "subtopic/${Uri.encode(nbId)}/${Uri.encode(topicId)}/${Uri.encode(sub.id)}"
                            )
                        },
                        onOpenRelatedTopic = { relatedTitle ->
                            val matchInNotebook = snapshot.topics.firstOrNull {
                                it.notebookId == nbId && it.name.equals(relatedTitle, ignoreCase = true)
                            }
                            val matchAny = matchInNotebook ?: snapshot.topics.firstOrNull {
                                it.name.equals(relatedTitle, ignoreCase = true)
                            }
                            if (matchAny != null) {
                                navController.navigate(
                                    "topic/${Uri.encode(matchAny.notebookId)}/${Uri.encode(matchAny.id)}"
                                )
                            } else {
                                libraryViewModel.createTopic(
                                    TopicEntity(
                                        notebookId = nbId,
                                        name = relatedTitle,
                                        shortDescription = "Related to ${topic.name}",
                                        purpose = "Explore $relatedTitle connected with ${topic.name}.",
                                        relatedTopics = listOf(topic.name)
                                    )
                                ) { createdId ->
                                    navController.navigate("topic/${Uri.encode(nbId)}/${Uri.encode(createdId)}")
                                }
                            }
                        },
                        onOpenPage = { page ->
                            navController.navigate("editor/${Uri.encode(nbId)}/${Uri.encode(page.id)}")
                        },
                        onCreateNewPage = {
                            createNewPageAndOpen(nbId, topicId, null, handwritingDefault = true)
                        },
                        onDeletePage = { page ->
                            val scopePages = snapshot.pages
                                .filter { it.notebookId == nbId && it.topicId == topicId && it.subtopicId.isNullOrBlank() }
                                .sortedBy { it.pageNumber }
                            libraryViewModel.deletePageAndRenumber(page, scopePages)
                        },
                        onStartWriting = {
                            openOrCreateScopeWriting(nbId, topicId, null, handwritingDefault = true)
                        },
                        onOpenAiAssistant = {
                            openAiAgent(
                                fromEditor = false,
                                notebook = notebook,
                                topic = topic,
                                personalNotes = topic.personalNotes
                            )
                        },
                        onUpdateDefaultInfo = { name, shortDesc, purpose, intro, covers, concepts, related ->
                            libraryViewModel.updateTopicDefaultInfo(
                                topicId, name, shortDesc, purpose, intro, covers, concepts, related
                            )
                        },
                        onUpdatePersonalSection = { notes, observations, examples, links ->
                            libraryViewModel.updateTopicPersonalSection(
                                topicId, notes, observations, examples, links
                            )
                        },
                        onCreateSubtopic = libraryViewModel::createSubtopic,
                        onAddAttachment = libraryViewModel::createAttachment
                    )
                }
            }

            composable(
                route = "subtopic/{notebookId}/{topicId}/{subtopicId}",
                arguments = listOf(
                    navArgument("notebookId") { type = NavType.StringType },
                    navArgument("topicId") { type = NavType.StringType },
                    navArgument("subtopicId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val nbId = Uri.decode(backStackEntry.arguments?.getString("notebookId") ?: "")
                val topicId = Uri.decode(backStackEntry.arguments?.getString("topicId") ?: "")
                val subId = Uri.decode(backStackEntry.arguments?.getString("subtopicId") ?: "")
                val notebook = snapshot.notebooks.firstOrNull { it.id == nbId }
                val topic = snapshot.topics.firstOrNull { it.id == topicId }
                val subtopic = snapshot.subtopics.firstOrNull { it.id == subId }
                if (notebook == null || topic == null || subtopic == null) {
                    MissingEntityScreen("Subtopic not found") { navController.popBackStack() }
                } else {
                    SubtopicInfoScreen(
                        notebook = notebook,
                        topic = topic,
                        subtopic = subtopic,
                        snapshot = snapshot,
                        onBack = { navController.popBackStack() },
                        onOpenPage = { page ->
                            navController.navigate("editor/${Uri.encode(nbId)}/${Uri.encode(page.id)}")
                        },
                        onCreateNewPage = {
                            createNewPageAndOpen(nbId, topicId, subId, handwritingDefault = true)
                        },
                        onDeletePage = { page ->
                            val scopePages = snapshot.pages
                                .filter { it.notebookId == nbId && it.topicId == topicId && it.subtopicId == subId }
                                .sortedBy { it.pageNumber }
                            libraryViewModel.deletePageAndRenumber(page, scopePages)
                        },
                        onStartWriting = {
                            openOrCreateScopeWriting(nbId, topicId, subId, handwritingDefault = true)
                        },
                        onOpenAiAssistant = {
                            openAiAgent(
                                fromEditor = false,
                                notebook = notebook,
                                topic = topic,
                                subtopic = subtopic,
                                personalNotes = subtopic.personalNotes
                            )
                        },
                        onUpdateSubtopic = { name, shortDesc, purpose, intro, personalNotes ->
                            libraryViewModel.updateSubtopicInfo(
                                subId, name, shortDesc, purpose, intro, personalNotes
                            )
                        }
                    )
                }
            }

            composable(
                route = "editor/{notebookId}/{pageId}",
                arguments = listOf(
                    navArgument("notebookId") { type = NavType.StringType },
                    navArgument("pageId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val nbId = Uri.decode(backStackEntry.arguments?.getString("notebookId") ?: "")
                val pageId = Uri.decode(backStackEntry.arguments?.getString("pageId") ?: "")
                val notebook = snapshot.notebooks.firstOrNull { it.id == nbId }
                val activePage = snapshot.pages.firstOrNull { it.id == pageId }
                if (notebook == null || activePage == null) {
                    MissingEntityScreen("Page not found") { navController.popBackStack() }
                } else {
                    val topic = activePage.topicId?.let { tid -> snapshot.topics.firstOrNull { it.id == tid } }
                    val subtopic = activePage.subtopicId?.let { sid -> snapshot.subtopics.firstOrNull { it.id == sid } }
                    val pagesInScope = snapshot.pages
                        .filter {
                            it.notebookId == activePage.notebookId &&
                                (it.topicId ?: "") == (activePage.topicId ?: "") &&
                                (it.subtopicId ?: "") == (activePage.subtopicId ?: "")
                        }
                        .sortedBy { it.pageNumber }

                    key(activePage.id, editorReloadNonce) {
                        PhysicalNotebookEditorScreen(
                            notebook = notebook,
                            topic = topic,
                            subtopic = subtopic,
                            pagesInScope = pagesInScope,
                            activePage = activePage,
                            preferences = snapshot.preferences,
                            pendingAiInsertion = pendingAiInsertion,
                            onClearPendingAiInsertion = { pendingAiInsertion = null },
                            onSelectPage = { target ->
                                navController.navigate("editor/${Uri.encode(nbId)}/${Uri.encode(target.id)}") {
                                    popUpTo("editor/{notebookId}/{pageId}") { inclusive = true }
                                }
                            },
                            onCreateNewPage = {
                                createNewPageAndOpen(
                                    notebookId = nbId,
                                    topicId = activePage.topicId,
                                    subtopicId = activePage.subtopicId,
                                    handwritingDefault = activePage.handwritingMode,
                                    replaceCurrentEditor = true
                                )
                            },
                            onRenamePage = { page, newTitle ->
                                libraryViewModel.renamePage(page.id, newTitle)
                            },
                            onDuplicatePage = { page ->
                                val nextNum = (pagesInScope.maxOfOrNull { it.pageNumber } ?: 0) + 1
                                libraryViewModel.duplicatePage(page, nextNum) { createdId ->
                                    navController.navigate("editor/${Uri.encode(nbId)}/${Uri.encode(createdId)}") {
                                        popUpTo("editor/{notebookId}/{pageId}") { inclusive = true }
                                    }
                                }
                            },
                            onSwapPageOrder = libraryViewModel::swapPageOrder,
                            onDeletePage = { page ->
                                val currentIdx = pagesInScope.indexOfFirst { it.id == page.id }
                                val remaining = pagesInScope.filter { it.id != page.id }
                                val fallback = if (remaining.isNotEmpty()) {
                                    remaining.getOrElse((currentIdx - 1).coerceAtLeast(0)) { remaining.first() }
                                } else {
                                    null
                                }
                                libraryViewModel.deletePageAndRenumber(page, pagesInScope)
                                if (page.id == activePage.id) {
                                    if (fallback != null) {
                                        navController.navigate("editor/${Uri.encode(nbId)}/${Uri.encode(fallback.id)}") {
                                            popUpTo("editor/{notebookId}/{pageId}") { inclusive = true }
                                        }
                                    } else {
                                        navController.popBackStack()
                                    }
                                }
                            },
                            onPersistPageFields = libraryViewModel::savePageFieldsSuspend,
                            onOpenAiWithContext = { currentContent, selectedText ->
                                openAiAgent(
                                    fromEditor = true,
                                    notebook = notebook,
                                    topic = topic,
                                    subtopic = subtopic,
                                    currentPage = activePage,
                                    personalNotes = topic?.personalNotes ?: subtopic?.personalNotes ?: "",
                                    currentPageText = currentContent,
                                    selectedText = selectedText
                                )
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }

            composable("settings") {
                SettingsScreen(
                    snapshot = snapshot,
                    userEmail = userEmail,
                    userId = userId,
                    topicGenState = topicGenState,
                    onBack = { navController.popBackStack() },
                    onSavePreferences = libraryViewModel::savePreferences,
                    onCreateNotebook = { name, purpose, shortDesc, detailedDesc, iconKey, colorHex, coverStyle ->
                        libraryViewModel.createNotebook(
                            name, purpose, shortDesc, detailedDesc, iconKey, colorHex, coverStyle,
                            snapshot.notebooks.size
                        )
                    },
                    onUpdateNotebook = libraryViewModel::updateNotebookDetails,
                    onTogglePinNotebook = libraryViewModel::togglePinNotebook,
                    onToggleHideNotebook = libraryViewModel::toggleHideNotebook,
                    onToggleArchiveNotebook = libraryViewModel::toggleArchiveNotebook,
                    onSwapNotebookOrder = libraryViewModel::swapNotebookOrder,
                    onDeleteNotebook = libraryViewModel::deleteNotebook,
                    onCreateTopic = { topic, starterSubs ->
                        libraryViewModel.createTopic(topic, starterSubs)
                    },
                    onGenerateTopicWithAi = { nbName, nbPurpose, topicName ->
                        aiViewModel.generateTopicMetadata(
                            notebookName = nbName,
                            notebookPurpose = nbPurpose,
                            topicName = topicName,
                            languagePref = snapshot.preferences.aiResponseLanguage
                        )
                    },
                    onResetTopicAiGen = aiViewModel::resetTopicGenState,
                    onDeleteTopic = libraryViewModel::deleteTopic,
                    onCreateSubtopic = libraryViewModel::createSubtopic,
                    onDeleteSubtopic = libraryViewModel::deleteSubtopic,
                    onRestoreFromJson = { jsonText ->
                        libraryViewModel.restoreFromJson(jsonText, snapshot)
                    },
                    onPostMessage = libraryViewModel::postMessage,
                    onSignOut = onSignOut
                )
            }
        }

        // Global Snackbar host for status confirmations (saves, backups, restores, AI copy)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    activeAiContext?.let { ctx ->
        // Keep context refreshed with latest snapshot pages/topics while sheet stays open
        val liveCtx = remember(ctx, snapshot) {
            buildRichAiContext(
                snapshot = snapshot,
                notebook = ctx.notebook?.let { nb -> snapshot.notebooks.firstOrNull { it.id == nb.id } ?: nb },
                topic = ctx.topic?.let { t -> snapshot.topics.firstOrNull { it.id == t.id } ?: t },
                subtopic = ctx.subtopic?.let { s -> snapshot.subtopics.firstOrNull { it.id == s.id } ?: s },
                currentPage = ctx.currentPage?.let { p -> snapshot.pages.firstOrNull { it.id == p.id } ?: p },
                personalNotes = ctx.personalNotes,
                currentPageText = ctx.currentPageText,
                selectedText = ctx.selectedText
            )
        }

        AiAssistantSheet(
            aiEnabled = snapshot.preferences.aiEnabled,
            agentState = agentState,
            context = liveCtx,
            defaultVoiceLanguage = snapshot.preferences.voiceInputLanguage,
            onSendMessage = { userPrompt, actionLabel ->
                aiViewModel.sendMessage(
                    userRequest = userPrompt,
                    actionLabel = actionLabel,
                    context = liveCtx,
                    snapshotProvider = { snapshot }
                )
            },
            onRetry = {
                aiViewModel.retryLastFailed(
                    context = liveCtx,
                    snapshotProvider = { snapshot }
                )
            },
            onCancelRequest = aiViewModel::cancelActiveRequest,
            onNewChat = {
                aiViewModel.startNewConversation(liveCtx)
            },
            onSelectSession = aiViewModel::selectSavedSession,
            onDeleteSession = aiViewModel::deleteSavedSession,
            onConfirmProposal = { messageId, proposal ->
                aiViewModel.executeProposal(
                    messageId = messageId,
                    proposal = proposal,
                    bridge = aiActionBridge,
                    snapshot = snapshot,
                    onPageMutated = { result ->
                        if (result.succeeded) {
                            libraryViewModel.postMessage(result.message)
                            val openPageId = activeEditorPageContext?.id
                            val mutatedOpenPage = openPageId != null && when (proposal) {
                                is AiProposedAction.AppendToPage -> proposal.pageId == openPageId
                                is AiProposedAction.OrganizePage -> proposal.pageId == openPageId
                                is AiProposedAction.RenamePage -> proposal.pageId == openPageId
                                is AiProposedAction.MoveContent -> proposal.sourcePageId == openPageId || proposal.targetPageId == openPageId
                                else -> false
                            }
                            if (mutatedOpenPage) {
                                editorReloadNonce += 1
                                result.updatedActivePageContent?.let { updatedText ->
                                    activeAiContext = liveCtx.copy(currentPageText = updatedText)
                                }
                            }
                        } else {
                            libraryViewModel.postMessage("Action failed: ${result.message}")
                        }
                    }
                )
            },
            onDismissProposal = aiViewModel::dismissProposal,
            onUndoAction = { undoSnapshot ->
                aiViewModel.undoLastAction(
                    undoSnapshot = undoSnapshot,
                    bridge = aiActionBridge,
                    onRestored = { result ->
                        libraryViewModel.postMessage(result.message)
                        if (result.succeeded) {
                            val openPageId = activeEditorPageContext?.id
                            val affectedOpenPage = openPageId != null && when (undoSnapshot) {
                                is AiUndoSnapshot.RestorePage -> undoSnapshot.pageId == openPageId
                                is AiUndoSnapshot.RestoreTwoPages -> undoSnapshot.sourcePageId == openPageId || undoSnapshot.targetPageId == openPageId
                                is AiUndoSnapshot.DeleteCreatedPage -> undoSnapshot.pageId == openPageId
                            }
                            if (affectedOpenPage) {
                                editorReloadNonce += 1
                            }
                        }
                    }
                )
            },
            onUpsertProgress = { concept, status, notes ->
                val nbId = liveCtx.notebook?.id ?: "general"
                aiViewModel.updateLearningProgressManually(
                    notebookId = nbId,
                    topicId = liveCtx.topic?.id,
                    subtopicId = liveCtx.subtopic?.id,
                    conceptName = concept,
                    status = status,
                    notes = notes
                )
            },
            onDeleteProgress = aiViewModel::deleteLearningProgress,
            onInsertIntoCurrentPage = if (aiCalledFromEditor) {
                { generated ->
                    pendingAiInsertion = generated
                    activeAiContext = null
                }
            } else null,
            onSaveAsNewPage = if (liveCtx.notebook != null) {
                { generated ->
                    createNewPageAndOpen(
                        notebookId = liveCtx.notebook.id,
                        topicId = liveCtx.topic?.id ?: activeEditorPageContext?.topicId,
                        subtopicId = liveCtx.subtopic?.id ?: activeEditorPageContext?.subtopicId,
                        handwritingDefault = true,
                        initialTitle = "AI Notes: ${liveCtx.topic?.name ?: liveCtx.notebook.name}".take(120),
                        initialContent = generated,
                        replaceCurrentEditor = aiCalledFromEditor
                    )
                    activeAiContext = null
                }
            } else null,
            onSaveToTopicAiInfo = when {
                liveCtx.subtopic != null -> {
                    { generated ->
                        libraryViewModel.saveAiInfoToSubtopic(liveCtx.subtopic.id, generated)
                        activeAiContext = null
                    }
                }
                liveCtx.topic != null -> {
                    { generated ->
                        libraryViewModel.saveAiInfoToTopic(liveCtx.topic.id, generated)
                        activeAiContext = null
                    }
                }
                else -> null
            },
            onDismiss = {
                // Keep conversation state intact when closing sheet
                activeAiContext = null
            },
            onCopied = {
                libraryViewModel.postMessage("Copied AI response to clipboard.")
            }
        )
    }
}

private fun buildRichAiContext(
    snapshot: LibrarySnapshot,
    notebook: NotebookEntity?,
    topic: TopicEntity?,
    subtopic: SubtopicEntity?,
    currentPage: PageEntity?,
    personalNotes: String,
    currentPageText: String,
    selectedText: String
): AiContext {
    val nbId = notebook?.id
    val notebookTopics = if (nbId != null) {
        snapshot.topics.filter { it.notebookId == nbId }.sortedBy { it.sortOrder }
    } else emptyList()

    val topicSubtopics = if (topic != null) {
        snapshot.subtopics.filter { it.topicId == topic.id }.sortedBy { it.sortOrder }
    } else emptyList()

    val relatedPages = if (nbId != null) {
        val sameTopicPages = if (topic != null) {
            snapshot.pages.filter { it.notebookId == nbId && it.topicId == topic.id && it.id != currentPage?.id }
        } else {
            emptyList()
        }
        val otherNotebookPages = snapshot.pages.filter {
            it.notebookId == nbId && it.id != currentPage?.id && (topic == null || it.topicId != topic.id)
        }
        (sameTopicPages.sortedBy { it.pageNumber } + otherNotebookPages.sortedByDescending { it.updatedAt?.seconds ?: 0L })
            .take(8)
    } else emptyList()

    val specializedSummary = buildString {
        when (notebook?.notebookType?.uppercase()) {
            "FITNESS" -> {
                val logs = snapshot.fitnessLogs.filter { it.notebookId == nbId }.take(6)
                if (logs.isNotEmpty()) {
                    appendLine("Recent Fitness Logs:")
                    logs.forEach { log ->
                        appendLine(
                            "- ${log.exerciseName} (${log.muscleGroup}): ${log.sets} sets x ${log.reps} @ ${log.weightKg}kg, RIR ${log.rir}, RPE ${log.rpe}" +
                                if (log.notes.isNotBlank()) " — ${log.notes.take(100)}" else ""
                        )
                    }
                }
            }
            "VOCABULARY" -> {
                val words = snapshot.vocabulary.filter { it.notebookId == nbId }.take(10)
                if (words.isNotEmpty()) {
                    appendLine("Saved Vocabulary Words:")
                    words.forEach { w ->
                        appendLine("- ${w.word}: ${w.meaning} (${if (w.mastered) "Mastered" else "Learning"})")
                    }
                }
            }
            "TODO" -> {
                val todos = snapshot.todos.filter { it.notebookId == nbId }.take(10)
                if (todos.isNotEmpty()) {
                    appendLine("Current Tasks / To-Dos:")
                    todos.forEach { t ->
                        appendLine("- [${if (t.completed) "Done" else t.priority}] ${t.title} (${t.category})")
                    }
                }
            }
            "SAVED_WORK" -> {
                val items = snapshot.attachments.filter { it.notebookId == nbId }.take(8)
                if (items.isNotEmpty()) {
                    appendLine("Saved Work / Resources:")
                    items.forEach { a ->
                        appendLine("- ${a.fileName} [${a.fileType} / ${a.status}]: ${a.caption.take(100)}")
                    }
                }
            }
            else -> Unit
        }
    }.trim()

    return AiContext(
        notebook = notebook,
        topic = topic,
        subtopic = subtopic,
        currentPage = currentPage,
        personalNotes = personalNotes,
        currentPageText = currentPageText,
        selectedText = selectedText,
        relatedPages = relatedPages,
        notebookTopics = notebookTopics,
        topicSubtopics = topicSubtopics,
        specializedContextSummary = specializedSummary,
        responseLanguage = snapshot.preferences.aiResponseLanguage
    )
}

@Composable
private fun MissingEntityScreen(message: String, onBack: () -> Unit) {
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(message, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onBack) { Text("Go Back") }
            }
        }
    }
}
