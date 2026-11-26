package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.data.ai.AiChatMessage
import com.example.data.ai.AiChatRole
import com.example.data.ai.AiContext
import com.example.data.ai.AiConversationSession
import com.example.data.ai.AiProposedAction
import com.example.data.model.SeedData
import com.example.ui.components.AiAssistantSheet
import com.example.ui.theme.KnowledgeLibraryTheme
import com.example.ui.viewmodel.AiAgentUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class AiAssistantSheetRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `assistant sheet sends editable prompt clears thinking indicator and supports retry on error`() {
        val nb = SeedData.defaultNotebooks().first()
        val ctx = AiContext(notebook = nb)
        var sentPrompt = ""
        var retried = false
        var uiState by mutableStateOf(AiAgentUiState())

        composeRule.setContent {
            KnowledgeLibraryTheme {
                AiAssistantSheet(
                    aiEnabled = true,
                    agentState = uiState,
                    context = ctx,
                    onSendMessage = { prompt, _ ->
                        sentPrompt = prompt
                        uiState = uiState.copy(
                            isBusy = true,
                            statusMessage = "Thinking with notebook context…",
                            activeSession = uiState.activeSession.copy(
                                messages = listOf(AiChatMessage(role = AiChatRole.USER, text = prompt))
                            )
                        )
                    },
                    onRetry = { retried = true },
                    onCancelRequest = {},
                    onNewChat = {},
                    onSelectSession = {},
                    onDeleteSession = {},
                    onConfirmProposal = { _, _ -> },
                    onDismissProposal = { _, _ -> },
                    onUndoAction = {},
                    onUpsertProgress = { _, _, _ -> },
                    onDeleteProgress = {},
                    onInsertIntoCurrentPage = {},
                    onSaveAsNewPage = {},
                    onDismiss = {}
                )
            }
        }

        // Editable input field + send
        composeRule.onNodeWithTag("ai_prompt_input").performTextInput("Explain Gut Microbiome")
        composeRule.onNodeWithTag("ai_send_button").performClick()
        composeRule.waitForIdle()

        assertEquals("Explain Gut Microbiome", sentPrompt)
        composeRule.onNodeWithTag("ai_thinking_indicator").assertIsDisplayed()

        // Complete response -> thinking indicator disappears and message + action chips show
        composeRule.runOnUiThread {
            uiState = uiState.copy(
                isBusy = false,
                statusMessage = null,
                activeSession = AiConversationSession(
                    messages = listOf(
                        AiChatMessage(id = "u1", role = AiChatRole.USER, text = "Explain Gut Microbiome"),
                        AiChatMessage(
                            id = "a1",
                            role = AiChatRole.ASSISTANT,
                            text = "The gut microbiome consists of trillions of beneficial microorganisms.",
                            modelUsed = "gemini-3.5-flash-lite",
                            usedFallbackModel = true
                        )
                    )
                )
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_thinking_indicator").assertDoesNotExist()
        composeRule.onNodeWithTag("ai_assistant_message_a1").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_fallback_badge_a1").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_insert_button_a1").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_new_page_button_a1").assertIsDisplayed()

        // Error state displays clear error card + working Retry button
        composeRule.runOnUiThread {
            uiState = uiState.copy(
                errorMessage = "No internet connection. Please connect and tap Retry.",
                lastFailedPrompt = "Explain Gut Microbiome"
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_error_card").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_retry_button").performClick()
        composeRule.waitForIdle()
        assertTrue("Retry button must invoke onRetry callback", retried)
    }

    @Test
    fun `destructive proposal requires explicit confirmation dialog before executing`() {
        val nb = SeedData.defaultNotebooks().first()
        val ctx = AiContext(notebook = nb)
        var confirmedProposalId: String? = null

        val destructiveProposal = AiProposedAction.OrganizePage(
            id = "prop_org_1",
            summary = "Organize Page 1 with headings",
            pageId = "page_1",
            pageTitle = "Page 1",
            originalContent = "rough unorganized notes",
            organizedContent = "# Organized Notes\n- Bullet 1"
        )

        val state = AiAgentUiState(
            activeSession = AiConversationSession(
                messages = listOf(
                    AiChatMessage(
                        id = "a_org",
                        role = AiChatRole.ASSISTANT,
                        text = "I reorganized your notes. Please review before replacing.",
                        proposals = listOf(destructiveProposal)
                    )
                )
            )
        )

        composeRule.setContent {
            KnowledgeLibraryTheme {
                AiAssistantSheet(
                    aiEnabled = true,
                    agentState = state,
                    context = ctx,
                    onSendMessage = { _, _ -> },
                    onRetry = {},
                    onCancelRequest = {},
                    onNewChat = {},
                    onSelectSession = {},
                    onDeleteSession = {},
                    onConfirmProposal = { _, prop -> confirmedProposalId = prop.id },
                    onDismissProposal = { _, _ -> },
                    onUndoAction = {},
                    onUpsertProgress = { _, _, _ -> },
                    onDeleteProgress = {},
                    onDismiss = {}
                )
            }
        }

        // First tap on proposal button must open confirmation AlertDialog (NOT execute immediately)
        composeRule.onNodeWithTag("ai_proposal_confirm_prop_org_1").performClick()
        composeRule.waitForIdle()
        assertEquals("Destructive action must wait for confirmation dialog", null, confirmedProposalId)
        composeRule.onNodeWithText("Confirm Notebook Change").assertExists()

        // Confirm inside dialog -> executes proposal
        composeRule.onNodeWithTag("ai_confirm_destructive_ok").performClick()
        composeRule.waitForIdle()
        assertEquals("prop_org_1", confirmedProposalId)
    }
}
