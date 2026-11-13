package com.example

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.data.local.FeldbuchSettings
import com.example.data.local.SettingsDataStore
import com.example.data.local.TreeFileEntity
import com.example.data.network.DeviceTokenPollResult
import com.example.data.network.GitHubClient
import com.example.data.network.GitHubOAuthClient
import com.example.data.network.GitHubRepositorySummary
import com.example.data.network.InsecureHttpUrlException
import com.example.data.security.DecryptResult
import com.example.data.security.SecretCipher
import com.example.domain.ExportSerializer
import com.example.ui.FeldbuchUiState
import com.example.ui.screens.RepoTreeDrawerSheet
import com.example.ui.theme.FeldbuchTheme
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GitHubOAuthTests {

  @get:Rule val tempFolder = TemporaryFolder()
  @get:Rule val composeRule = createComposeRule()

  private class FakeSecretCipher(var invalidated: Boolean = false) : SecretCipher {
    override fun encrypt(plaintext: String): String =
      if (plaintext.isEmpty()) "" else "enc:${plaintext.reversed()}"

    override fun decrypt(encoded: String): DecryptResult {
      if (encoded.isBlank()) return DecryptResult.Empty
      if (invalidated) return DecryptResult.KeyInvalidated("Invalidated")
      return DecryptResult.Success(encoded.removePrefix("enc:").reversed())
    }
  }

  @Test
  fun `pkce session computes S256 challenge parses redirect callback and enforces https`() {
    val strictClient = GitHubOAuthClient(oauthBaseUrl = "http://github.com", enforceHttps = true)
    val insecureResult = strictClient.createPkceSession(clientId = "Ov23liTestClient")
    assertTrue(insecureResult.isFailure)
    assertTrue(insecureResult.exceptionOrNull() is InsecureHttpUrlException)

    val client = GitHubOAuthClient()
    val session = client.createPkceSession(clientId = "Ov23liTestClient").getOrThrow()

    // Verify S256 challenge = base64url(sha256(verifier))
    val expectedDigest =
      MessageDigest.getInstance("SHA-256")
        .digest(session.codeVerifier.toByteArray(Charsets.US_ASCII))
    val expectedChallenge =
      android.util.Base64.encodeToString(
        expectedDigest,
        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
      )
    assertEquals(expectedChallenge, session.codeChallenge)

    val parsedAuth = Uri.parse(session.authorizeUrl)
    assertEquals("https", parsedAuth.scheme)
    assertEquals("github.com", parsedAuth.host)
    assertEquals("Ov23liTestClient", parsedAuth.getQueryParameter("client_id"))
    assertEquals("S256", parsedAuth.getQueryParameter("code_challenge_method"))
    assertTrue(parsedAuth.getQueryParameter("scope").orEmpty().contains("repo"))

    // Callback parser accepts feldbuch://oauth/github only
    val callback =
      GitHubOAuthClient.parseCallbackUri(
        Uri.parse("feldbuch://oauth/github?code=abc123&state=${session.state}")
      )
    assertNotNull(callback)
    assertEquals("abc123", callback!!.code)
    assertEquals(session.state, callback.state)
    assertNull(GitHubOAuthClient.parseCallbackUri(Uri.parse("https://evil.com/oauth/github?code=x")))
  }

  @Test
  fun `oauth code exchange, device flow, private repo listing, and private tree fetch work with MockWebServer`() {
    runBlocking {
      val server = MockWebServer()
      server.start()
      try {
        val base = server.url("/").toString().trimEnd('/')
        val oauthClient =
          GitHubOAuthClient(oauthBaseUrl = base, apiBaseUrl = base, enforceHttps = false)
        val gitHubClient = GitHubClient(baseUrl = base, enforceHttps = false)

        // 1. Exchange authorization code with PKCE verifier
        server.enqueue(
          MockResponse()
            .setResponseCode(200)
            .setBody("""{"access_token":"gho_private_oauth_token","token_type":"bearer","scope":"repo"}""")
        )
        val token =
          oauthClient
            .exchangeAuthorizationCode(
              clientId = "Ov23liClient",
              code = "auth_code_99",
              codeVerifier = "verifier_xyz",
            )
            .getOrThrow()
        assertEquals("gho_private_oauth_token", token)
        val tokenReq = server.takeRequest()
        val formBody = tokenReq.body.readUtf8()
        assertTrue(formBody.contains("code=auth_code_99"))
        assertTrue(formBody.contains("code_verifier=verifier_xyz"))

        // 2. Device Flow code + pending -> success polling
        server.enqueue(
          MockResponse()
            .setResponseCode(200)
            .setBody(
              """{"device_code":"dev_123","user_code":"ABCD-5678","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}"""
            )
        )
        val device = oauthClient.requestDeviceCode("Ov23liClient").getOrThrow()
        assertEquals("ABCD-5678", device.userCode)
        server.takeRequest()

        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"error":"authorization_pending"}"""))
        assertEquals(
          DeviceTokenPollResult.Pending,
          oauthClient.pollDeviceAccessTokenOnce("Ov23liClient", device.deviceCode),
        )
        server.takeRequest()

        server.enqueue(
          MockResponse()
            .setResponseCode(200)
            .setBody("""{"access_token":"gho_device_token","token_type":"bearer"}""")
        )
        assertEquals(
          DeviceTokenPollResult.Success("gho_device_token"),
          oauthClient.pollDeviceAccessTokenOnce("Ov23liClient", device.deviceCode),
        )
        server.takeRequest()

        // 3. Fetch authenticated user + accessible private/public repositories
        server.enqueue(
          MockResponse()
            .setResponseCode(200)
            .setBody("""{"login":"alice","name":"Alice Field"}""")
        )
        val profile = oauthClient.fetchAuthenticatedUser(token).getOrThrow()
        assertEquals("alice", profile.login)
        assertEquals("Bearer gho_private_oauth_token", server.takeRequest().getHeader("Authorization"))

        server.enqueue(
          MockResponse()
            .setResponseCode(200)
            .setBody(
              """[
                {"name":"vault-private","full_name":"alice/vault-private","default_branch":"main","private":true,"owner":{"login":"alice"},"description":"Private vault"},
                {"name":"public-notes","full_name":"alice/public-notes","default_branch":"main","private":false,"owner":{"login":"alice"},"description":null}
              ]"""
            )
        )
        val repos = oauthClient.fetchAccessibleRepos(token).getOrThrow()
        assertEquals(2, repos.size)
        assertTrue(repos[0].isPrivate)
        assertEquals("alice/vault-private", repos[0].fullName)
        assertFalse(repos[1].isPrivate)
        val reposReq = server.takeRequest()
        assertEquals("Bearer gho_private_oauth_token", reposReq.getHeader("Authorization"))
        assertTrue(reposReq.path!!.contains("visibility=all"))

        // 4. Fetch private repository file tree with OAuth Bearer token
        server.enqueue(
          MockResponse()
            .setResponseCode(200)
            .setBody("""{"tree":[{"path":"README.md","type":"blob","size":42},{"path":"docs/secret-plan.md","type":"blob","size":128}]}""")
        )
        val tree = gitHubClient.fetchTree("alice", "vault-private", "main", token).getOrThrow()
        assertEquals(2, tree.size)
        val treeReq = server.takeRequest()
        assertEquals("Bearer gho_private_oauth_token", treeReq.getHeader("Authorization"))
        assertTrue(treeReq.path!!.contains("/repos/alice/vault-private/git/trees/main"))
      } finally {
        server.shutdown()
      }
    }
  }

  @Test
  fun `oauth token is encrypted at rest, recovers on key invalidation, and omitted from exports`() {
    runBlocking {
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
      try {
        val dsFile = File(tempFolder.root, "oauth_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { dsFile }
        val cipher = FakeSecretCipher()
        val store = SettingsDataStore(dataStore = dataStore, secretCipher = cipher)

        store.saveGitHubOAuthSession(token = "gho_super_secret", login = "alice")
        val raw = dataStore.data.first()
        assertTrue(raw[SettingsDataStore.Keys.ENC_GITHUB_OAUTH_TOKEN]?.startsWith("enc:") == true)
        assertFalse(raw[SettingsDataStore.Keys.ENC_GITHUB_OAUTH_TOKEN].orEmpty().contains("gho_super_secret"))

        val loaded = store.settingsFlow.first()
        assertEquals("gho_super_secret", loaded.githubOAuthToken)
        assertEquals("alice", loaded.githubOAuthLogin)

        // ExportSerializer never leaks OAuth token
        val exported = ExportSerializer.buildExportJson(loaded, 1, emptyList(), emptyList())
        assertFalse(exported.contains("gho_super_secret"))

        // Invalidate keystore key -> clears only OAuth session with notice
        cipher.invalidated = true
        store.migrateAndSanitizeOnLoad()
        val recovered = store.settingsFlow.first()
        assertEquals("", recovered.githubOAuthToken)
        assertEquals("", recovered.githubOAuthLogin)
        assertNotNull(store.secretRecoveryNotice.value)
      } finally {
        scope.cancel()
      }
    }
  }

  @Test
  fun `drawer displays OAuth status and private repo switcher allowing repository selection`() {
    val privateRepo =
      GitHubRepositorySummary(
        owner = "alice",
        name = "private-vault",
        fullName = "alice/private-vault",
        defaultBranch = "main",
        isPrivate = true,
        description = "Private field notes",
      )
    var selectedRepo: GitHubRepositorySummary? = null

    val state =
      FeldbuchUiState(
        treeFiles = listOf(TreeFileEntity("README.md", sizeBytes = 42)),
        settings =
          FeldbuchSettings(
            githubOwner = "alice",
            githubRepo = "public-repo",
            githubOAuthToken = "gho_token",
            githubOAuthLogin = "alice",
          ),
        accessibleRepos = listOf(privateRepo),
      )

    composeRule.setContent {
      FeldbuchTheme {
        RepoTreeDrawerSheet(
          uiState = state,
          onRefreshTree = {},
          onSearchQueryChange = {},
          onToggleSearchByFullPath = {},
          onToggleTrayPath = {},
          onOpenFilePreview = {},
          onOpenTraySheet = {},
          onNavigateToSetup = {},
          onCloseDrawer = {},
          onSelectRepository = { repo -> selectedRepo = repo },
        )
      }
    }

    composeRule.onNodeWithTag("drawer_oauth_login_label").assertTextContains("@alice")
    composeRule.onNodeWithTag("drawer_switch_repo_button").performClick()
    composeRule.onNodeWithTag("drawer_repo_item_alice/private-vault").assertIsDisplayed()
    composeRule.onNodeWithTag("drawer_repo_item_alice/private-vault").performClick()

    assertEquals(privateRepo, selectedRepo)
  }
}
