package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.feldbuchDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "feldbuch_settings"
)

data class FeldbuchSettings(
  val githubOwner: String = "",
  val githubRepo: String = "",
  val githubBranch: String = "main",
  val githubPat: String = "",
  val endpointUrl: String = "",
  val apiKey: String = "",
  val modelName: String = "gpt-4o-mini",
  val hubUrl: String = "",
  val treeFetchedAtMs: Long = 0L,
  val selectedTrayPaths: Set<String> = emptySet(),
)

class SettingsDataStore(private val context: Context) {
  private object Keys {
    val GITHUB_OWNER = stringPreferencesKey("github_owner")
    val GITHUB_REPO = stringPreferencesKey("github_repo")
    val GITHUB_BRANCH = stringPreferencesKey("github_branch")
    val GITHUB_PAT = stringPreferencesKey("github_pat")
    val ENDPOINT_URL = stringPreferencesKey("endpoint_url")
    val API_KEY = stringPreferencesKey("api_key")
    val MODEL_NAME = stringPreferencesKey("model_name")
    val HUB_URL = stringPreferencesKey("hub_url")
    val TREE_FETCHED_AT_MS = longPreferencesKey("tree_fetched_at_ms")
    val SELECTED_TRAY_PATHS = stringSetPreferencesKey("selected_tray_paths")
  }

  val settingsFlow: Flow<FeldbuchSettings> =
    context.feldbuchDataStore.data.map { prefs ->
      FeldbuchSettings(
        githubOwner = prefs[Keys.GITHUB_OWNER] ?: "",
        githubRepo = prefs[Keys.GITHUB_REPO] ?: "",
        githubBranch = prefs[Keys.GITHUB_BRANCH]?.ifBlank { "main" } ?: "main",
        githubPat = prefs[Keys.GITHUB_PAT] ?: "",
        endpointUrl = prefs[Keys.ENDPOINT_URL] ?: "",
        apiKey = prefs[Keys.API_KEY] ?: "",
        modelName = prefs[Keys.MODEL_NAME]?.ifBlank { "gpt-4o-mini" } ?: "gpt-4o-mini",
        hubUrl = prefs[Keys.HUB_URL] ?: "",
        treeFetchedAtMs = prefs[Keys.TREE_FETCHED_AT_MS] ?: 0L,
        selectedTrayPaths = prefs[Keys.SELECTED_TRAY_PATHS] ?: emptySet(),
      )
    }

  suspend fun updateGitHubSettings(
    owner: String,
    repo: String,
    branch: String,
    pat: String,
  ) {
    context.feldbuchDataStore.edit { prefs ->
      prefs[Keys.GITHUB_OWNER] = owner.trim()
      prefs[Keys.GITHUB_REPO] = repo.trim()
      prefs[Keys.GITHUB_BRANCH] = branch.trim().ifBlank { "main" }
      prefs[Keys.GITHUB_PAT] = pat.trim()
    }
  }

  suspend fun updateChatAndHubSettings(
    endpointUrl: String,
    apiKey: String,
    modelName: String,
    hubUrl: String,
  ) {
    context.feldbuchDataStore.edit { prefs ->
      prefs[Keys.ENDPOINT_URL] = endpointUrl.trim()
      prefs[Keys.API_KEY] = apiKey.trim()
      prefs[Keys.MODEL_NAME] = modelName.trim().ifBlank { "gpt-4o-mini" }
      prefs[Keys.HUB_URL] = hubUrl.trim()
    }
  }

  suspend fun updateTreeFetchedAt(fetchedAtMs: Long) {
    context.feldbuchDataStore.edit { prefs -> prefs[Keys.TREE_FETCHED_AT_MS] = fetchedAtMs }
  }

  suspend fun updateSelectedTrayPaths(paths: Set<String>) {
    context.feldbuchDataStore.edit { prefs -> prefs[Keys.SELECTED_TRAY_PATHS] = paths }
  }
}
