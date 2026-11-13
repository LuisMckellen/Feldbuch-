package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.data.local.FeldbuchSettings
import com.example.data.local.TreeFileEntity
import com.example.ui.FeldbuchUiState
import com.example.ui.screens.RepoTreeDrawerSheet
import com.example.ui.theme.FeldbuchTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class RepoTreeDrawerSearchTest {

  @get:Rule val composeRule = createComposeRule()

  @Test
  fun `drawer top search bar filters files by name, expands ancestors, and clears cleanly`() {
    val files =
      listOf(
        TreeFileEntity("README.md", sizeBytes = 100),
        TreeFileEntity("docs/architecture.md", sizeBytes = 200),
        TreeFileEntity("docs/api/endpoints.md", sizeBytes = 300),
        TreeFileEntity("src/main/App.kt", sizeBytes = 400),
        TreeFileEntity("memory/secret.md", sizeBytes = 50),
      )

    var state by
      mutableStateOf(
        FeldbuchUiState(
          treeFiles = files,
          settings = FeldbuchSettings(githubOwner = "octocat", githubRepo = "feldbuch"),
        )
      )

    composeRule.setContent {
      FeldbuchTheme {
        RepoTreeDrawerSheet(
          uiState = state,
          onRefreshTree = {},
          onSearchQueryChange = { q -> state = state.copy(drawerSearchQuery = q) },
          onToggleSearchByFullPath = {
            state = state.copy(drawerSearchByFullPath = !state.drawerSearchByFullPath)
          },
          onToggleTrayPath = {},
          onOpenFilePreview = {},
          onOpenTraySheet = {},
          onNavigateToSetup = {},
          onCloseDrawer = {},
        )
      }
    }

    composeRule.onNodeWithTag("drawer_search_filter_input").assertIsDisplayed()
    composeRule.onNodeWithTag("drawer_search_filter_input").performTextInput("endpoints")

    composeRule.onNodeWithTag("drawer_search_match_count").assertTextContains("1 match of 5")
    composeRule.onNodeWithTag("drawer_file_docs/api/endpoints.md").assertIsDisplayed()
    composeRule.onAllNodesWithTag("drawer_file_README.md").assertCountEquals(0)

    // Clear button restores full tree
    composeRule.onNodeWithTag("drawer_search_clear_button").performClick()
    composeRule.onNodeWithTag("drawer_file_README.md").assertIsDisplayed()
  }

  @Test
  fun `drawer breadcrumb bar tracks current folder path and navigates back when tapped`() {
    val files =
      listOf(
        TreeFileEntity("README.md", sizeBytes = 100),
        TreeFileEntity("docs/architecture.md", sizeBytes = 200),
        TreeFileEntity("docs/api/endpoints.md", sizeBytes = 300),
        TreeFileEntity("src/main/App.kt", sizeBytes = 400),
        TreeFileEntity("memory/secret.md", sizeBytes = 50),
      )

    composeRule.setContent {
      FeldbuchTheme {
        RepoTreeDrawerSheet(
          uiState =
            FeldbuchUiState(
              treeFiles = files,
              settings = FeldbuchSettings(githubOwner = "octocat", githubRepo = "feldbuch"),
            ),
          onRefreshTree = {},
          onSearchQueryChange = {},
          onToggleSearchByFullPath = {},
          onToggleTrayPath = {},
          onOpenFilePreview = {},
          onOpenTraySheet = {},
          onNavigateToSetup = {},
          onCloseDrawer = {},
        )
      }
    }

    composeRule.onNodeWithTag("drawer_breadcrumb_bar").assertIsDisplayed()
    composeRule
      .onNodeWithTag("drawer_breadcrumb_root")
      .assertIsDisplayed()
      .assertTextContains("feldbuch")

    // Expand docs/ -> docs/api/
    composeRule.onNodeWithTag("drawer_folder_docs/").performClick()
    composeRule
      .onNodeWithTag("drawer_breadcrumb_docs")
      .assertIsDisplayed()
      .assertTextContains("docs")

    composeRule.onNodeWithTag("drawer_folder_docs/api/").performClick()
    composeRule
      .onNodeWithTag("drawer_breadcrumb_docs/api")
      .assertIsDisplayed()
      .assertTextContains("api")
    composeRule.onNodeWithTag("drawer_file_docs/api/endpoints.md").assertIsDisplayed()

    // Tap "docs" breadcrumb to step back up: collapses docs/api/, keeps docs/ open
    composeRule.onNodeWithTag("drawer_breadcrumb_docs").performClick()
    composeRule.onAllNodesWithTag("drawer_breadcrumb_docs/api").assertCountEquals(0)
    composeRule.onAllNodesWithTag("drawer_file_docs/api/endpoints.md").assertCountEquals(0)
    composeRule.onNodeWithTag("drawer_file_docs/architecture.md").assertIsDisplayed()

    // Tap root breadcrumb to return to repository root
    composeRule.onNodeWithTag("drawer_breadcrumb_root").performClick()
    composeRule.onAllNodesWithTag("drawer_breadcrumb_docs").assertCountEquals(0)
    composeRule.onAllNodesWithTag("drawer_file_docs/architecture.md").assertCountEquals(0)
    composeRule.onNodeWithTag("drawer_file_README.md").assertIsDisplayed()
  }
}
