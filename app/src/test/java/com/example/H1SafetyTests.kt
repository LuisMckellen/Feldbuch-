package com.example

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ChatMessageEntity
import com.example.data.local.FeldbuchDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.local.TreeFileEntity
import com.example.data.network.ApiChatMessage
import com.example.data.network.ChatApiResult
import com.example.data.network.FeldbuchHttpClients
import com.example.data.network.InsecureHttpUrlException
import com.example.data.network.OkHttpService
import com.example.data.network.OpenAiClient
import com.example.data.security.DecryptResult
import com.example.data.security.SecretCipher
import com.example.domain.ContextCapLimiter
import com.example.domain.PathBlocklist
import com.example.domain.UrlValidationResult
import com.example.domain.UrlValidator
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
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
class H1SafetyTests {

  @get:Rule val tempFolder = TemporaryFolder()

  private class FakeSecretCipher(var invalidateKey: Boolean = false) : SecretCipher {
    override fun encrypt(plaintext: String): String =
      if (plaintext.isEmpty()) "" else "fake_enc:${plaintext.reversed()}"

    override fun decrypt(encoded: String): DecryptResult {
      if (encoded.isBlank()) return DecryptResult.Empty
      if (invalidateKey) return DecryptResult.KeyInvalidated("Simulated invalidated key")
      if (!encoded.startsWith("fake_enc:")) {
        return DecryptResult.KeyInvalidated("Corrupted")
      }
      return DecryptResult.Success(encoded.removePrefix("fake_enc:").reversed())
    }
  }

  @Test
  fun `blocklist matches memory transcripts env key and pem variants case-insensitively`() {
    val blocked =
      listOf(
        "memory/daily.md",
        "MEMORY/DAILY.MD",
        "docs/memory/2026.md",
        "./memory/notes.md",
        "../ignored/..\\memory\\secret.md",
        "%6d%65%6d%6f%72%79/secret.md",
        "docs/%4D%45%4D%4F%52%59/file.md",
        "transcripts/2026-11-13.md",
        "archive/Transcripts-call.txt",
        ".env",
        ".env.local",
        "deploy/.ENV.production",
        "secrets/private.key",
        "certs/CA.PEM",
      )
    blocked.forEach { path ->
      assertTrue("Expected blocked: $path", PathBlocklist.isBlocked(path))
    }

    val allowed =
      listOf(
        "README.md",
        "docs/architecture.md",
        "docs/memory_palace.md",
        "notes/env_example.md",
        "src/main/App.kt",
      )
    allowed.forEach { path ->
      assertFalse("Expected allowed: $path", PathBlocklist.isBlocked(path))
    }

    val filtered =
      PathBlocklist.filterAllowed(setOf("README.md", "memory/a.md", "certs/a.pem", "docs/b.md"))
    assertEquals(setOf("README.md", "docs/b.md"), filtered)
  }

  @Test
  fun `context cap enforces 20000 per file and 60000 total and marks trimmed files`() {
    val files =
      listOf(
        TreeFileEntity(path = "a.md", cachedText = "a".repeat(25_000)),
        TreeFileEntity(path = "b.md", cachedText = "b".repeat(19_000)),
        TreeFileEntity(path = "c.md", cachedText = "c".repeat(19_000)),
        TreeFileEntity(path = "d.md", cachedText = "d".repeat(10_000)),
        TreeFileEntity(path = "memory/hidden.md", cachedText = "secret"),
      )

    val result = ContextCapLimiter.apply(files, perFileCap = 20_000, totalCap = 60_000)

    // Blocked file excluded completely
    assertEquals(listOf("a.md", "b.md", "c.md", "d.md"), result.items.map { it.file.path })
    // a.md cut from 25k -> 20k
    assertEquals(20_000, result.items[0].sentChars)
    assertTrue(result.items[0].wasTrimmed)
    // b.md fits intact (19k), c.md fits intact (19k), total so far = 58k
    assertEquals(19_000, result.items[1].sentChars)
    assertFalse(result.items[1].wasTrimmed)
    assertEquals(19_000, result.items[2].sentChars)
    assertFalse(result.items[2].wasTrimmed)
    // d.md truncated from 10k -> 2k to stay strictly at 60k total
    assertEquals(2_000, result.items[3].sentChars)
    assertTrue(result.items[3].wasTrimmed)

    assertEquals(60_000, result.totalSentChars)
    assertEquals(15_000, result.estimatedTokens)
    assertEquals(setOf("a.md", "d.md"), result.trimmedPaths)
  }

  @Test
  fun `url validator enforces https and rejects http`() {
    assertTrue(UrlValidator.validateHttps("http://eule.ts.net/v1", "Endpoint") is UrlValidationResult.Invalid)
    assertTrue(UrlValidator.validateHttps("ftp://eule.ts.net", "Endpoint") is UrlValidationResult.Invalid)
    assertTrue(UrlValidator.validateHttps("", "Endpoint", allowBlank = false) is UrlValidationResult.Invalid)
    assertTrue(UrlValidator.validateHttps("", "Endpoint", allowBlank = true) is UrlValidationResult.Valid)
    assertTrue(UrlValidator.validateHttps("https://eule.ts.net/v1", "Endpoint") is UrlValidationResult.Valid)
  }

  @Test
  fun `openai client rejects http when enforceHttps is true`() = runBlocking {
    val strictClient = OpenAiClient(enforceHttps = true)
    val result =
      strictClient.sendChatCompletion(
        endpointUrl = "http://insecure.local/v1",
        apiKey = "",
        model = "gpt-4o-mini",
        messages = listOf(ApiChatMessage("user", "hi")),
      )
    assertTrue(result is ChatApiResult.Failure)
    assertEquals("network", (result as ChatApiResult.Failure).shortReason)
  }

  @Test
  fun `openai client sends max_tokens and classifies 401 402 429 and timeout`() = runBlocking {
    val server = MockWebServer()
    server.start()
    try {
      val client = OpenAiClient(enforceHttps = false, callTimeoutSeconds = 1L)
      val baseUrl = server.url("/v1").toString()

      // 1. 200 OK with max_tokens verification
      server.enqueue(
        MockResponse()
          .setResponseCode(200)
          .setBody("""{"choices":[{"message":{"content":"Answer from model"}}]}""")
      )
      val ok =
        client.sendChatCompletion(
          endpointUrl = baseUrl,
          apiKey = "sk-test",
          model = "qwen-test",
          messages = listOf(ApiChatMessage("user", "question")),
          maxTokens = 2048,
        )
      assertTrue(ok is ChatApiResult.Success)
      assertEquals("Answer from model", (ok as ChatApiResult.Success).reply)

      val recorded = server.takeRequest()
      val sentJson = JSONObject(recorded.body.readUtf8())
      assertEquals(2048, sentJson.getInt("max_tokens"))
      assertEquals("qwen-test", sentJson.getString("model"))
      assertEquals("Bearer sk-test", recorded.getHeader("Authorization"))

      // 2. 401
      server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Unauthorized"}}"""))
      val r401 = client.sendChatCompletion(baseUrl, "", "m", listOf(ApiChatMessage("user", "q")))
      assertEquals("401", (r401 as ChatApiResult.Failure).shortReason)

      // 3. 402
      server.enqueue(MockResponse().setResponseCode(402).setBody("""{"name":"PaymentRequired"}"""))
      val r402 = client.sendChatCompletion(baseUrl, "", "m", listOf(ApiChatMessage("user", "q")))
      assertEquals("402", (r402 as ChatApiResult.Failure).shortReason)

      // 4. 429
      server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"Rate limited"}}"""))
      val r429 = client.sendChatCompletion(baseUrl, "", "m", listOf(ApiChatMessage("user", "q")))
      assertEquals("429", (r429 as ChatApiResult.Failure).shortReason)

      // 5. Timeout
      server.enqueue(
        MockResponse()
          .setResponseCode(200)
          .setBody("""{"choices":[{"message":{"content":"late"}}]}""")
          .setBodyDelay(3, TimeUnit.SECONDS)
      )
      val rTimeout = client.sendChatCompletion(baseUrl, "", "m", listOf(ApiChatMessage("user", "q")))
      assertEquals("timeout", (rTimeout as ChatApiResult.Failure).shortReason)
    } finally {
      server.shutdown()
    }
  }

  @Test
  fun `settings datastore encrypts secrets migrates legacy plaintext recovers invalid key and filters tray`() =
    runBlocking {
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
      try {
        val dsFile = File(tempFolder.root, "h1_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { dsFile }
        val cipher = FakeSecretCipher()
        val store = SettingsDataStore(dataStore = dataStore, secretCipher = cipher)

        // Seed legacy plaintext secrets + blocked tray path
        dataStore.edit { prefs ->
          prefs[SettingsDataStore.Keys.LEGACY_GITHUB_PAT] = "ghp_legacy_secret"
          prefs[SettingsDataStore.Keys.LEGACY_API_KEY] = "sk_legacy_secret"
          prefs[SettingsDataStore.Keys.SELECTED_TRAY_PATHS] =
            setOf("README.md", "memory/private.md", ".env.local")
        }

        store.migrateAndSanitizeOnLoad()

        val rawPrefs = dataStore.data.first()
        assertFalse(rawPrefs.contains(SettingsDataStore.Keys.LEGACY_GITHUB_PAT))
        assertFalse(rawPrefs.contains(SettingsDataStore.Keys.LEGACY_API_KEY))
        assertTrue(rawPrefs[SettingsDataStore.Keys.ENC_GITHUB_PAT]?.startsWith("fake_enc:") == true)
        assertTrue(rawPrefs[SettingsDataStore.Keys.ENC_API_KEY]?.startsWith("fake_enc:") == true)
        assertEquals(setOf("README.md"), rawPrefs[SettingsDataStore.Keys.SELECTED_TRAY_PATHS])

        val loaded = store.settingsFlow.first()
        assertEquals("ghp_legacy_secret", loaded.githubPat)
        assertEquals("sk_legacy_secret", loaded.apiKey)
        assertEquals(setOf("README.md"), loaded.selectedTrayPaths)
        assertNull(store.secretRecoveryNotice.value)

        // Simulate KeyPermanentlyInvalidatedException / missing key on restart
        cipher.invalidateKey = true
        store.migrateAndSanitizeOnLoad()

        val afterRecovery = store.settingsFlow.first()
        assertEquals("", afterRecovery.githubPat)
        assertEquals("", afterRecovery.apiKey)
        assertNotNull(store.secretRecoveryNotice.value)
        val rawAfter = dataStore.data.first()
        assertFalse(rawAfter.contains(SettingsDataStore.Keys.ENC_GITHUB_PAT))
        assertFalse(rawAfter.contains(SettingsDataStore.Keys.ENC_API_KEY))
      } finally {
        scope.cancel()
      }
    }

  @Test
  fun `room dao excludes failed messages from unsent list until retried`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, FeldbuchDatabase::class.java).allowMainThreadQueries().build()
    try {
      val dao = db.feldbuchDao()
      val msgId =
        dao.insertMessage(
          ChatMessageEntity(
            role = "user",
            content = "Unsent failed prompt",
            timestampMs = 1000L,
            errorReason = "401",
            errorDetail = "Invalid key",
          )
        )
      assertTrue(dao.observeUnsentMessages().first().isEmpty())

      dao.clearMessageErrorAndUpdateContext(msgId, "README.md")
      val unsent = dao.observeUnsentMessages().first()
      assertEquals(1, unsent.size)
      assertNull(unsent[0].errorReason)
      assertEquals("README.md", unsent[0].contextPaths)
    } finally {
      db.close()
    }
  }

  @Test
  fun `okhttp enforces https and throws clear exception on http for hub eule and chat`() =
    runBlocking {
      val server = MockWebServer()
      server.start()
      try {
        val httpBase = server.url("/").toString()

        // 1. Direct URL validation throws InsecureHttpUrlException for Hub, Eule, and Chat
        listOf(OkHttpService.HUB, OkHttpService.EULE, OkHttpService.CHAT).forEach { service ->
          var thrown: InsecureHttpUrlException? = null
          try {
            FeldbuchHttpClients.requireHttpsUrl("http://tailnet.local/test", service)
          } catch (e: InsecureHttpUrlException) {
            thrown = e
          }
          assertNotNull("Expected InsecureHttpUrlException for $service", thrown)
          assertTrue(thrown!!.message!!.contains(service.displayName))
          assertTrue(thrown.message!!.contains("https://"))

          // Valid https:// succeeds
          val parsed = FeldbuchHttpClients.requireHttpsUrl("https://eule.ts.net/api", service)
          assertTrue(parsed.isHttps)
          assertEquals("eule.ts.net", parsed.host)
        }

        // 2. OkHttp interceptor rejects http:// calls before hitting the wire
        val clients =
          listOf(
            OkHttpService.HUB to FeldbuchHttpClients.createHubClient(enforceHttps = true),
            OkHttpService.EULE to FeldbuchHttpClients.createEuleClient(enforceHttps = true),
            OkHttpService.CHAT to FeldbuchHttpClients.createChatClient(enforceHttps = true),
          )
        clients.forEach { (service, client) ->
          var thrown: InsecureHttpUrlException? = null
          try {
            client.newCall(Request.Builder().url(httpBase).get().build()).execute().close()
          } catch (e: InsecureHttpUrlException) {
            thrown = e
          }
          assertNotNull("Expected OkHttp interceptor to block http:// for $service", thrown)
          assertTrue(thrown!!.message!!.contains(service.displayName))
          assertTrue(thrown.message!!.contains("https://"))
        }
        assertEquals(0, server.requestCount)

        // 3. OpenAiClient throwing Hub helper & fetchModels reject http:// clearly
        val strictOpenAi = OpenAiClient(enforceHttps = true)
        var hubThrown: InsecureHttpUrlException? = null
        try {
          strictOpenAi.checkHubReachableOrThrow("${httpBase}health")
        } catch (e: InsecureHttpUrlException) {
          hubThrown = e
        }
        assertNotNull(hubThrown)
        assertTrue(hubThrown!!.message!!.contains("Hub URL"))

        val modelsResult = strictOpenAi.fetchModels("${httpBase}v1", "")
        assertTrue(modelsResult.isFailure)
        assertTrue(modelsResult.exceptionOrNull() is InsecureHttpUrlException)
      } finally {
        server.shutdown()
      }
    }
}
