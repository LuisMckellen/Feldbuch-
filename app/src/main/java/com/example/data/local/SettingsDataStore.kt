package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.security.DecryptResult
import com.example.data.security.KeystoreSecretCipher
import com.example.data.security.SecretCipher
import com.example.domain.ContextCapLimiter
import com.example.domain.PathBlocklist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

private val Context.feldbuchDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "feldbuch_settings"
)

data class FeldbuchSettings(
  val githubOwner: String = "",
  val githubRepo: String = "",
  val githubBranch: String = "main",
  val githubPat: String = "",
  val githubOAuthClientId: String = "",
  val githubOAuthToken: String = "",
  val githubOAuthLogin: String = "",
  val endpointUrl: String = "",
  val apiKey: String = "",
  val modelName: String = "gpt-4o-mini",
  val hubUrl: String = "",
  val maxTokens: Int = DEFAULT_MAX_TOKENS,
  val perFileCharCap: Int = ContextCapLimiter.DEFAULT_PER_FILE_CHAR_CAP,
  val totalCharCap: Int = ContextCapLimiter.DEFAULT_TOTAL_CHAR_CAP,
  val euleUrl: String = "",
  val euleKey: String = "",
  val euleDemoMode: Boolean = false,
  val stellwerkUrl: String = "",
  val treeFetchedAtMs: Long = 0L,
  val treeAutoSyncEnabled: Boolean = true,
  val treeSyncIntervalHours: Long = DEFAULT_TREE_SYNC_INTERVAL_HOURS,
  val selectedTrayPaths: Set<String> = emptySet(),
) {
  companion object {
    const val DEFAULT_MAX_TOKENS = 2048
    const val DEFAULT_TREE_SYNC_INTERVAL_HOURS = 6L
  }
}

class SettingsDataStore(
  private val dataStore: DataStore<Preferences>,
  private val secretCipher: SecretCipher = KeystoreSecretCipher(),
) {
  constructor(
    context: Context,
    secretCipher: SecretCipher = KeystoreSecretCipher(),
  ) : this(context.feldbuchDataStore, secretCipher)

  object Keys {
    val GITHUB_OWNER = stringPreferencesKey("github_owner")
    val GITHUB_REPO = stringPreferencesKey("github_repo")
    val GITHUB_BRANCH = stringPreferencesKey("github_branch")
    val LEGACY_GITHUB_PAT = stringPreferencesKey("github_pat")
    val ENC_GITHUB_PAT = stringPreferencesKey("enc_github_pat")
    val GITHUB_OAUTH_CLIENT_ID = stringPreferencesKey("github_oauth_client_id")
    val ENC_GITHUB_OAUTH_TOKEN = stringPreferencesKey("enc_github_oauth_token")
    val GITHUB_OAUTH_LOGIN = stringPreferencesKey("github_oauth_login")
    val ENDPOINT_URL = stringPreferencesKey("endpoint_url")
    val LEGACY_API_KEY = stringPreferencesKey("api_key")
    val ENC_API_KEY = stringPreferencesKey("enc_api_key")
    val MODEL_NAME = stringPreferencesKey("model_name")
    val HUB_URL = stringPreferencesKey("hub_url")
    val MAX_TOKENS = intPreferencesKey("max_tokens")
    val PER_FILE_CHAR_CAP = intPreferencesKey("per_file_char_cap")
    val TOTAL_CHAR_CAP = intPreferencesKey("total_char_cap")
    val EULE_URL = stringPreferencesKey("eule_url")
    val ENC_EULE_KEY = stringPreferencesKey("enc_eule_key")
    val EULE_DEMO_MODE = booleanPreferencesKey("eule_demo_mode")
    val STELLWERK_URL = stringPreferencesKey("stellwerk_url")
    val TREE_FETCHED_AT_MS = longPreferencesKey("tree_fetched_at_ms")
    val TREE_AUTO_SYNC_ENABLED = booleanPreferencesKey("tree_auto_sync_enabled")
    val TREE_SYNC_INTERVAL_HOURS = longPreferencesKey("tree_sync_interval_hours")
    val SELECTED_TRAY_PATHS = stringSetPreferencesKey("selected_tray_paths")
  }

  private val _secretRecoveryNotice = MutableStateFlow<String?>(null)
  val secretRecoveryNotice: StateFlow<String?> = _secretRecoveryNotice.asStateFlow()

  val settingsFlow: Flow<FeldbuchSettings> =
    dataStore.data.map { prefs ->
      val pat = decryptQuietly(prefs[Keys.ENC_GITHUB_PAT].orEmpty(), prefs[Keys.LEGACY_GITHUB_PAT].orEmpty())
      val oauthToken = decryptQuietly(prefs[Keys.ENC_GITHUB_OAUTH_TOKEN].orEmpty(), "")
      val apiKey = decryptQuietly(prefs[Keys.ENC_API_KEY].orEmpty(), prefs[Keys.LEGACY_API_KEY].orEmpty())
      val euleKey = decryptQuietly(prefs[Keys.ENC_EULE_KEY].orEmpty(), "")
      val sanitizedTray = PathBlocklist.filterAllowed(prefs[Keys.SELECTED_TRAY_PATHS] ?: emptySet())

      FeldbuchSettings(
        githubOwner = prefs[Keys.GITHUB_OWNER] ?: "",
        githubRepo = prefs[Keys.GITHUB_REPO] ?: "",
        githubBranch = prefs[Keys.GITHUB_BRANCH]?.ifBlank { "main" } ?: "main",
        githubPat = pat,
        githubOAuthClientId = prefs[Keys.GITHUB_OAUTH_CLIENT_ID] ?: "",
        githubOAuthToken = oauthToken,
        githubOAuthLogin = prefs[Keys.GITHUB_OAUTH_LOGIN] ?: "",
        endpointUrl = prefs[Keys.ENDPOINT_URL] ?: "",
        apiKey = apiKey,
        modelName = prefs[Keys.MODEL_NAME]?.ifBlank { "gpt-4o-mini" } ?: "gpt-4o-mini",
        hubUrl = prefs[Keys.HUB_URL] ?: "",
        maxTokens = (prefs[Keys.MAX_TOKENS] ?: FeldbuchSettings.DEFAULT_MAX_TOKENS).coerceAtLeast(1),
        perFileCharCap =
          (prefs[Keys.PER_FILE_CHAR_CAP] ?: ContextCapLimiter.DEFAULT_PER_FILE_CHAR_CAP)
            .coerceAtLeast(1),
        totalCharCap =
          (prefs[Keys.TOTAL_CHAR_CAP] ?: ContextCapLimiter.DEFAULT_TOTAL_CHAR_CAP)
            .coerceAtLeast(1),
        euleUrl = prefs[Keys.EULE_URL] ?: "",
        euleKey = euleKey,
        euleDemoMode = prefs[Keys.EULE_DEMO_MODE] ?: false,
        stellwerkUrl = prefs[Keys.STELLWERK_URL] ?: "",
        treeFetchedAtMs = prefs[Keys.TREE_FETCHED_AT_MS] ?: 0L,
        treeAutoSyncEnabled = prefs[Keys.TREE_AUTO_SYNC_ENABLED] ?: true,
        treeSyncIntervalHours =
          (prefs[Keys.TREE_SYNC_INTERVAL_HOURS] ?: FeldbuchSettings.DEFAULT_TREE_SYNC_INTERVAL_HOURS)
            .coerceIn(1L, 24L),
        selectedTrayPaths = sanitizedTray,
      )
    }

  suspend fun migrateAndSanitizeOnLoad() {
    val recoveryMessages = mutableListOf<String>()
    dataStore.edit { prefs ->
      // 1. Migrate legacy plaintext GitHub PAT -> Keystore AES-GCM and delete plaintext key
      val legacyPat = prefs[Keys.LEGACY_GITHUB_PAT]
      if (!legacyPat.isNullOrBlank() && prefs[Keys.ENC_GITHUB_PAT].isNullOrBlank()) {
        prefs[Keys.ENC_GITHUB_PAT] = secretCipher.encrypt(legacyPat.trim())
      }
      if (prefs.contains(Keys.LEGACY_GITHUB_PAT)) {
        prefs.remove(Keys.LEGACY_GITHUB_PAT)
      }

      // 2. Migrate legacy plaintext API key -> Keystore AES-GCM and delete plaintext key
      val legacyApi = prefs[Keys.LEGACY_API_KEY]
      if (!legacyApi.isNullOrBlank() && prefs[Keys.ENC_API_KEY].isNullOrBlank()) {
        prefs[Keys.ENC_API_KEY] = secretCipher.encrypt(legacyApi.trim())
      }
      if (prefs.contains(Keys.LEGACY_API_KEY)) {
        prefs.remove(Keys.LEGACY_API_KEY)
      }

      // 3. Validate decryptability; if key missing or permanently invalidated, clear only that secret
      val encPat = prefs[Keys.ENC_GITHUB_PAT].orEmpty()
      if (encPat.isNotBlank() && secretCipher.decrypt(encPat) is DecryptResult.KeyInvalidated) {
        prefs.remove(Keys.ENC_GITHUB_PAT)
        recoveryMessages.add("GitHub PAT")
      }

      val encApi = prefs[Keys.ENC_API_KEY].orEmpty()
      if (encApi.isNotBlank() && secretCipher.decrypt(encApi) is DecryptResult.KeyInvalidated) {
        prefs.remove(Keys.ENC_API_KEY)
        recoveryMessages.add("Chat API key")
      }

      val encOAuth = prefs[Keys.ENC_GITHUB_OAUTH_TOKEN].orEmpty()
      if (encOAuth.isNotBlank() && secretCipher.decrypt(encOAuth) is DecryptResult.KeyInvalidated) {
        prefs.remove(Keys.ENC_GITHUB_OAUTH_TOKEN)
        prefs.remove(Keys.GITHUB_OAUTH_LOGIN)
        recoveryMessages.add("GitHub OAuth session")
      }

      val encEule = prefs[Keys.ENC_EULE_KEY].orEmpty()
      if (encEule.isNotBlank() && secretCipher.decrypt(encEule) is DecryptResult.KeyInvalidated) {
        prefs.remove(Keys.ENC_EULE_KEY)
        recoveryMessages.add("Eule key")
      }

      // 4. Filter out any blocked paths from persisted selectedTrayPaths
      val existingTray = prefs[Keys.SELECTED_TRAY_PATHS]
      if (!existingTray.isNullOrEmpty()) {
        val allowed = PathBlocklist.filterAllowed(existingTray)
        if (allowed.size != existingTray.size) {
          prefs[Keys.SELECTED_TRAY_PATHS] = allowed
        }
      }
    }

    if (recoveryMessages.isNotEmpty()) {
      val joined = recoveryMessages.joinToString(" and ")
      _secretRecoveryNotice.value =
        "$joined could not be unlocked (keystore key missing or invalidated) and was cleared. Please re-enter it in Setup."
    }
  }

  fun dismissSecretRecoveryNotice() {
    _secretRecoveryNotice.value = null
  }

  suspend fun updateGitHubSettings(
    owner: String,
    repo: String,
    branch: String,
    pat: String,
  ) {
    val cleanPat = pat.trim()
    dataStore.edit { prefs ->
      prefs[Keys.GITHUB_OWNER] = owner.trim()
      prefs[Keys.GITHUB_REPO] = repo.trim()
      prefs[Keys.GITHUB_BRANCH] = branch.trim().ifBlank { "main" }
      prefs.remove(Keys.LEGACY_GITHUB_PAT)
      if (cleanPat.isEmpty()) {
        prefs.remove(Keys.ENC_GITHUB_PAT)
      } else {
        prefs[Keys.ENC_GITHUB_PAT] = secretCipher.encrypt(cleanPat)
      }
    }
  }

  suspend fun updateGitHubOAuthClientId(clientId: String) {
    dataStore.edit { prefs ->
      val clean = clientId.trim()
      if (clean.isEmpty()) {
        prefs.remove(Keys.GITHUB_OAUTH_CLIENT_ID)
      } else {
        prefs[Keys.GITHUB_OAUTH_CLIENT_ID] = clean
      }
    }
  }

  suspend fun saveGitHubOAuthSession(token: String, login: String) {
    val cleanToken = token.trim()
    val cleanLogin = login.trim()
    dataStore.edit { prefs ->
      if (cleanToken.isEmpty()) {
        prefs.remove(Keys.ENC_GITHUB_OAUTH_TOKEN)
        prefs.remove(Keys.GITHUB_OAUTH_LOGIN)
      } else {
        prefs[Keys.ENC_GITHUB_OAUTH_TOKEN] = secretCipher.encrypt(cleanToken)
        prefs[Keys.GITHUB_OAUTH_LOGIN] = cleanLogin
      }
    }
  }

  suspend fun clearGitHubOAuthSession() {
    dataStore.edit { prefs ->
      prefs.remove(Keys.ENC_GITHUB_OAUTH_TOKEN)
      prefs.remove(Keys.GITHUB_OAUTH_LOGIN)
    }
  }

  suspend fun selectGitHubRepository(owner: String, repo: String, branch: String) {
    dataStore.edit { prefs ->
      prefs[Keys.GITHUB_OWNER] = owner.trim()
      prefs[Keys.GITHUB_REPO] = repo.trim()
      prefs[Keys.GITHUB_BRANCH] = branch.trim().ifBlank { "main" }
    }
  }

  suspend fun updateChatAndHubSettings(
    endpointUrl: String,
    apiKey: String,
    modelName: String,
    hubUrl: String,
    maxTokens: Int = FeldbuchSettings.DEFAULT_MAX_TOKENS,
  ) {
    val cleanKey = apiKey.trim()
    dataStore.edit { prefs ->
      prefs[Keys.ENDPOINT_URL] = endpointUrl.trim()
      prefs.remove(Keys.LEGACY_API_KEY)
      if (cleanKey.isEmpty()) {
        prefs.remove(Keys.ENC_API_KEY)
      } else {
        prefs[Keys.ENC_API_KEY] = secretCipher.encrypt(cleanKey)
      }
      prefs[Keys.MODEL_NAME] = modelName.trim().ifBlank { "gpt-4o-mini" }
      prefs[Keys.HUB_URL] = hubUrl.trim()
      prefs[Keys.MAX_TOKENS] = maxTokens.coerceAtLeast(1)
    }
  }

  suspend fun updateEuleSettings(
    euleUrl: String,
    euleKey: String,
    demoMode: Boolean,
  ) {
    val cleanKey = euleKey.trim()
    dataStore.edit { prefs ->
      prefs[Keys.EULE_URL] = euleUrl.trim()
      if (cleanKey.isEmpty()) {
        prefs.remove(Keys.ENC_EULE_KEY)
      } else {
        prefs[Keys.ENC_EULE_KEY] = secretCipher.encrypt(cleanKey)
      }
      prefs[Keys.EULE_DEMO_MODE] = demoMode
    }
  }

  suspend fun updateStellwerkUrl(stellwerkUrl: String) {
    val cleanUrl = stellwerkUrl.trim()
    dataStore.edit { prefs ->
      if (cleanUrl.isEmpty()) {
        prefs.remove(Keys.STELLWERK_URL)
      } else {
        prefs[Keys.STELLWERK_URL] = cleanUrl
      }
    }
  }

  suspend fun updateTreeFetchedAt(fetchedAtMs: Long) {
    dataStore.edit { prefs -> prefs[Keys.TREE_FETCHED_AT_MS] = fetchedAtMs }
  }

  suspend fun updateTreeSyncPreferences(enabled: Boolean, intervalHours: Long) {
    val safeHours = intervalHours.coerceIn(1L, 24L)
    dataStore.edit { prefs ->
      prefs[Keys.TREE_AUTO_SYNC_ENABLED] = enabled
      prefs[Keys.TREE_SYNC_INTERVAL_HOURS] = safeHours
    }
  }

  suspend fun updateSelectedTrayPaths(paths: Set<String>) {
    val allowed = PathBlocklist.filterAllowed(paths)
    dataStore.edit { prefs -> prefs[Keys.SELECTED_TRAY_PATHS] = allowed }
  }

  private fun decryptQuietly(encrypted: String, legacyPlain: String): String {
    if (encrypted.isNotBlank()) {
      return when (val result = secretCipher.decrypt(encrypted)) {
        is DecryptResult.Success -> result.plaintext
        is DecryptResult.Empty -> ""
        is DecryptResult.KeyInvalidated -> ""
      }
    }
    return legacyPlain
  }
}
