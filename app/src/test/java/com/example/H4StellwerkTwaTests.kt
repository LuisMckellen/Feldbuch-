package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FeldbuchSettings
import com.example.data.local.SettingsDataStore
import com.example.data.security.DecryptResult
import com.example.data.security.SecretCipher
import com.example.data.twa.AndroidStellwerkLauncherBackend
import com.example.data.twa.AppSigningFingerprint
import com.example.data.twa.StellwerkLaunchMode
import com.example.data.twa.StellwerkLaunchResult
import com.example.data.twa.StellwerkLauncher
import com.example.data.twa.StellwerkLauncherBackend
import com.example.ui.FeldbuchUiState
import com.example.ui.screens.StellwerkSettingsCard
import com.example.ui.theme.FeldbuchTheme
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class H4StellwerkTwaTests {

  @get:Rule val tempFolder = TemporaryFolder()
  @get:Rule val composeRule = createComposeRule()

  private class FakeSecretCipher : SecretCipher {
    override fun encrypt(plaintext: String): String = plaintext

    override fun decrypt(encoded: String): DecryptResult =
      if (encoded.isEmpty()) DecryptResult.Empty else DecryptResult.Success(encoded)
  }

  private class RecordingBackend(
    private val customTabsPkg: String?,
  ) : StellwerkLauncherBackend {
    var twaCalls = 0
    var customTabCalls = 0
    var browserCalls = 0
    var lastUri: Uri? = null

    override fun resolveCustomTabsPackage(context: Context): String? = customTabsPkg

    override fun launchTrustedWebActivityOrCustomTab(
      context: Context,
      uri: Uri,
      customTabsPackage: String,
    ): StellwerkLaunchResult {
      twaCalls++
      lastUri = uri
      return StellwerkLaunchResult(StellwerkLaunchMode.TWA, "TWA launched")
    }

    override fun launchCustomTab(
      context: Context,
      uri: Uri,
      customTabsPackage: String?,
    ): StellwerkLaunchResult {
      customTabCalls++
      lastUri = uri
      return StellwerkLaunchResult(StellwerkLaunchMode.CUSTOM_TAB, "Custom Tab fallback")
    }

    override fun launchSystemBrowser(context: Context, uri: Uri): StellwerkLaunchResult {
      browserCalls++
      lastUri = uri
      return StellwerkLaunchResult(StellwerkLaunchMode.BROWSER_VIEW, "Browser fallback")
    }
  }

  @Test
  fun `stellwerk launcher rejects cleartext http and selects TWA or Custom Tab fallback for https`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    val httpBackend = RecordingBackend("com.android.chrome")
    val httpRes = StellwerkLauncher.launch(context, "http://stellwerk.tailnet.ts.net", httpBackend)
    assertEquals(StellwerkLaunchMode.FAILED, httpRes.mode)
    assertTrue(httpRes.message.contains("https://"))
    assertEquals(0, httpBackend.twaCalls)
    assertEquals(0, httpBackend.customTabCalls)

    val blankRes = StellwerkLauncher.launch(context, "   ", httpBackend)
    assertEquals(StellwerkLaunchMode.FAILED, blankRes.mode)

    val twaRes = StellwerkLauncher.launch(context, "https://stellwerk.tailnet.ts.net/app", httpBackend)
    assertEquals(StellwerkLaunchMode.TWA, twaRes.mode)
    assertEquals(1, httpBackend.twaCalls)
    assertEquals("https://stellwerk.tailnet.ts.net/app", httpBackend.lastUri.toString())

    val fallbackBackend = RecordingBackend(null)
    val customTabRes =
      StellwerkLauncher.launch(context, "https://stellwerk.tailnet.ts.net", fallbackBackend)
    assertEquals(StellwerkLaunchMode.CUSTOM_TAB, customTabRes.mode)
    assertEquals(1, fallbackBackend.customTabCalls)
    assertEquals(0, fallbackBackend.twaCalls)

    // Verify real Android CustomTabsIntent builder sets URI cleanly
    val builtCustomTab =
      AndroidStellwerkLauncherBackend.buildFallbackCustomTabIntent(
        Uri.parse("https://stellwerk.tailnet.ts.net")
      )
    assertNotNull(builtCustomTab.intent)
    assertEquals("https://stellwerk.tailnet.ts.net", builtCustomTab.intent.dataString)
  }

  @Test
  fun `app signing fingerprint formats uppercase SHA-256 and builds valid assetlinks json`() {
    val certBytes = "feldbuch-test-cert".toByteArray(Charsets.UTF_8)
    val sha256 = AppSigningFingerprint.formatSha256ColonHex(certBytes)

    // 32 bytes -> 32 pairs of hex separated by 31 colons = 95 chars
    assertEquals(95, sha256.length)
    assertTrue(sha256.matches(Regex("^([0-9A-F]{2}:){31}[0-9A-F]{2}$")))

    val snippet =
      AppSigningFingerprint.buildAssetLinksSnippet(
        packageName = "com.example.feldbuch",
        sha256Fingerprint = sha256,
      )
    val arr = JSONArray(snippet)
    assertEquals(1, arr.length())
    val obj = arr.getJSONObject(0)
    assertEquals(
      "delegate_permission/common.handle_all_urls",
      obj.getJSONArray("relation").getString(0),
    )
    val target = obj.getJSONObject("target")
    assertEquals("android_app", target.getString("namespace"))
    assertEquals("com.example.feldbuch", target.getString("package_name"))
    assertEquals(sha256, target.getJSONArray("sha256_cert_fingerprints").getString(0))
  }

  @Test
  fun `settings datastore persists stellwerk https url`() {
    runBlocking {
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
      try {
        val dsFile = File(tempFolder.root, "stellwerk_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { dsFile }
        val store = SettingsDataStore(dataStore, FakeSecretCipher())

        store.updateStellwerkUrl("  https://stellwerk.tailnet.ts.net  ")
        val loaded = store.settingsFlow.first()
        assertEquals("https://stellwerk.tailnet.ts.net", loaded.stellwerkUrl)

        store.updateStellwerkUrl("")
        assertFalse(store.settingsFlow.first().stellwerkUrl.isNotEmpty())
      } finally {
        scope.cancel()
      }
    }
  }

  @Test
  fun `stellwerk settings card validates https copies sha256 and triggers save and open`() {
    var savedUrl: String? = null
    var openedUrl: String? = null
    var copiedMsg: String? = null
    val sampleSha =
      "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"

    composeRule.setContent {
      FeldbuchTheme {
        StellwerkSettingsCard(
          uiState =
            FeldbuchUiState(
              settings = FeldbuchSettings(stellwerkUrl = ""),
              appPackageName = "com.example.feldbuch",
              appSha256Fingerprint = sampleSha,
            ),
          onSaveStellwerkUrl = { savedUrl = it },
          onOpenStellwerk = { openedUrl = it },
          onCopiedMessage = { copiedMsg = it },
        )
      }
    }

    composeRule
      .onNodeWithTag("stellwerk_sha256_text")
      .assertIsDisplayed()
      .assertTextContains(sampleSha)

    composeRule.onNodeWithTag("copy_sha256_button").assertIsEnabled().performClick()
    assertEquals("Copied SHA-256 fingerprint to clipboard.", copiedMsg)

    // Cleartext http:// disables Save & Open
    composeRule
      .onNodeWithTag("stellwerk_url_input")
      .performTextReplacement("http://stellwerk.tailnet.ts.net")
    composeRule.onNodeWithTag("save_stellwerk_button").assertIsNotEnabled()
    composeRule.onNodeWithTag("open_stellwerk_button").assertIsNotEnabled()

    // Valid https:// enables Save & Open
    composeRule
      .onNodeWithTag("stellwerk_url_input")
      .performTextReplacement("https://stellwerk.tailnet.ts.net")
    composeRule.onNodeWithTag("save_stellwerk_button").assertIsEnabled().performClick()
    assertEquals("https://stellwerk.tailnet.ts.net", savedUrl)

    composeRule.onNodeWithTag("open_stellwerk_button").assertIsEnabled().performClick()
    assertEquals("https://stellwerk.tailnet.ts.net", openedUrl)
  }
}
