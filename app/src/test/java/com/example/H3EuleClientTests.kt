package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FeldbuchDatabase
import com.example.data.local.FeldbuchSettings
import com.example.data.local.OutboxKind
import com.example.data.local.OutboxState
import com.example.data.network.EuleApiResult
import com.example.data.network.EuleAskResponse
import com.example.data.network.EuleCitation
import com.example.data.network.EuleCitationCodec
import com.example.data.network.FakeEuleApi
import com.example.data.network.OkHttpEuleApi
import com.example.data.outbox.EuleOutboxSender
import com.example.data.outbox.OutboxProcessor
import com.example.ui.FeldbuchUiState
import com.example.ui.screens.EuleCitationDialog
import com.example.ui.screens.EuleScreen
import com.example.ui.theme.FeldbuchTheme
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class H3EuleClientTests {

  @get:Rule val composeRule = createComposeRule()

  private fun createInMemoryDb(): FeldbuchDatabase {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return Room.inMemoryDatabaseBuilder(context, FeldbuchDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @Test
  fun `eule health search ask and suggest handle auth headers and payloads`() {
    runBlocking {
      val server = MockWebServer()
      server.start()
      try {
        // 1. /health 200 -> true, 500 -> false
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"ok":false}"""))

        val authedApi =
          OkHttpEuleApi(
            baseUrl = server.url("/").toString(),
            apiKey = "secret-eule-key",
            enforceHttps = false,
          )
        assertTrue(authedApi.checkHealth())
        assertFalse(authedApi.checkHealth())

        val healthReq1 = server.takeRequest()
        assertEquals("/health", healthReq1.path)
        assertEquals("Bearer secret-eule-key", healthReq1.getHeader("Authorization"))
        server.takeRequest() // consume second health req

        // 2. /api/search omits Authorization header when apiKey is blank
        server.enqueue(
          MockResponse()
            .setResponseCode(200)
            .setBody(
              """
              {
                "hits": [
                  {
                    "path": "memory/2026-11-10-field-ops.md",
                    "title": "Field Ops Handoff",
                    "snippet": "Gate latch replaced at north culvert.",
                    "score": 0.91
                  }
                ]
              }
              """.trimIndent()
            )
        )
        val noAuthApi =
          OkHttpEuleApi(
            baseUrl = server.url("/").toString(),
            apiKey = "",
            enforceHttps = false,
          )
        val searchRes = noAuthApi.search(query = "culvert gate", limit = 15)
        assertTrue(searchRes is EuleApiResult.Success)
        val hits = (searchRes as EuleApiResult.Success).data
        assertEquals(1, hits.size)
        assertEquals("memory/2026-11-10-field-ops.md", hits[0].path)
        assertEquals("Field Ops Handoff", hits[0].title)
        assertEquals(0.91, hits[0].score ?: 0.0, 0.001)

        val searchReq = server.takeRequest()
        assertEquals("GET", searchReq.method)
        assertTrue(searchReq.path.orEmpty().startsWith("/api/search?"))
        assertEquals("culvert gate", searchReq.requestUrl?.queryParameter("q"))
        assertEquals("15", searchReq.requestUrl?.queryParameter("limit"))
        assertNull(searchReq.getHeader("Authorization"))

        // 3. POST /api/ask sends Idempotency-Key and parses citations
        server.enqueue(
          MockResponse()
            .setResponseCode(200)
            .setBody(
              """
              {
                "answer": "Stellwerk runs behind Caddy on Tailscale.",
                "citations": [
                  {
                    "path": "projects/stellwerk/architecture.md",
                    "title": "Architecture",
                    "snippet": "Caddy terminates TLS for Tailscale."
                  }
                ]
              }
              """.trimIndent()
            )
        )
        val askRes = authedApi.ask(question = "How does Stellwerk expose HTTPS?", idempotencyKey = "ask-uuid-1")
        assertTrue(askRes is EuleApiResult.Success)
        val askData = (askRes as EuleApiResult.Success).data
        assertEquals("Stellwerk runs behind Caddy on Tailscale.", askData.answer)
        assertEquals(1, askData.citations.size)
        assertEquals("projects/stellwerk/architecture.md", askData.citations[0].path)
        assertEquals("Architecture", askData.citations[0].label)

        val askReq = server.takeRequest()
        assertEquals("POST", askReq.method)
        assertEquals("/api/ask", askReq.path)
        assertEquals("ask-uuid-1", askReq.getHeader("Idempotency-Key"))
        assertEquals("Bearer secret-eule-key", askReq.getHeader("Authorization"))
        val askBody = JSONObject(askReq.body.readUtf8())
        assertEquals("How does Stellwerk expose HTTPS?", askBody.getString("question"))

        // 4. POST /api/memory/suggest sends text, from="feldbuch", Idempotency-Key
        server.enqueue(
          MockResponse()
            .setResponseCode(200)
            .setBody("""{"id":"sug-42","status":"pending","message":"Queued for owner approval"}""")
        )
        val suggestRes =
          authedApi.suggestMemory(
            text = "Remember to inspect south pump seals.",
            from = "feldbuch",
            idempotencyKey = "sug-uuid-9",
          )
        assertTrue(suggestRes is EuleApiResult.Success)
        val sugData = (suggestRes as EuleApiResult.Success).data
        assertEquals("sug-42", sugData.id)
        assertEquals("pending", sugData.status)

        val sugReq = server.takeRequest()
        assertEquals("POST", sugReq.method)
        assertEquals("/api/memory/suggest", sugReq.path)
        assertEquals("sug-uuid-9", sugReq.getHeader("Idempotency-Key"))
        val sugBody = JSONObject(sugReq.body.readUtf8())
        assertEquals("Remember to inspect south pump seals.", sugBody.getString("text"))
        assertEquals("feldbuch", sugBody.getString("from"))
      } finally {
        server.shutdown()
      }
    }
  }

  @Test
  fun `eule api handles 401 500 timeout malformed json and rejects cleartext http`() {
    runBlocking {
      val server = MockWebServer()
      server.start()
      try {
        val api =
          OkHttpEuleApi(
            baseUrl = server.url("/").toString(),
            apiKey = "bad-key",
            enforceHttps = false,
            callTimeoutSeconds = 1L,
          )

        // 401 with JSON "name" field
        server.enqueue(
          MockResponse()
            .setResponseCode(401)
            .setBody("""{"name":"UnauthorizedError","message":"Invalid Eule bearer token"}""")
        )
        val r401 = api.ask("test")
        assertTrue(r401 is EuleApiResult.Failure)
        val f401 = r401 as EuleApiResult.Failure
        assertEquals("401", f401.shortReason)
        assertTrue(f401.detailMessage.contains("UnauthorizedError"))
        assertTrue(f401.detailMessage.contains("Invalid Eule bearer token"))

        // 500
        server.enqueue(
          MockResponse()
            .setResponseCode(500)
            .setBody("""{"name":"InternalServerError","message":"Index locked"}""")
        )
        val r500 = api.search("notes")
        assertTrue(r500 is EuleApiResult.Failure)
        assertEquals("500", (r500 as EuleApiResult.Failure).shortReason)
        assertTrue(r500.detailMessage.contains("InternalServerError"))

        // Malformed JSON
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json-at-all"))
        val rMalformed = api.ask("test")
        assertTrue(rMalformed is EuleApiResult.Failure)
        assertEquals("malformed", (rMalformed as EuleApiResult.Failure).shortReason)

        // Timeout
        server.enqueue(
          MockResponse()
            .setResponseCode(200)
            .setBody("""{"answer":"late","citations":[]}""")
            .setBodyDelay(2500L, TimeUnit.MILLISECONDS)
        )
        val rTimeout = api.ask("slow")
        assertTrue(rTimeout is EuleApiResult.Failure)
        assertEquals("timeout", (rTimeout as EuleApiResult.Failure).shortReason)

        // Strict HTTPS check rejects cleartext http://
        val httpApi = OkHttpEuleApi(baseUrl = "http://eule.tailnet.ts.net", enforceHttps = true)
        val httpResult = httpApi.search("culvert")
        assertTrue(httpResult is EuleApiResult.Failure)
        assertEquals("network", (httpResult as EuleApiResult.Failure).shortReason)
        assertTrue(httpResult.detailMessage.contains("https://"))
      } finally {
        server.shutdown()
      }
    }
  }

  @Test
  fun `eule outbox sender routes note and idea to suggest and question to ask with answer persistence`() {
    runBlocking {
      val server = MockWebServer()
      server.start()
      val db = createInMemoryDb()
      try {
        val dao = db.feldbuchDao()
        val api =
          OkHttpEuleApi(
            baseUrl = server.url("/").toString(),
            apiKey = "",
            enforceHttps = false,
          )
        val processor = OutboxProcessor(dao = dao, sender = EuleOutboxSender(api))

        val noteItem = processor.enqueueCapture(OutboxKind.NOTE, "Note body for suggest", id = "note-id-1")
        val ideaItem = processor.enqueueCapture(OutboxKind.IDEA, "Idea body for suggest", id = "idea-id-2")
        val qItem = processor.enqueueCapture(OutboxKind.QUESTION, "Where is the valve log?", id = "q-id-3")

        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"s1","status":"pending"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"s2","status":"pending"}"""))
        server.enqueue(
          MockResponse()
            .setResponseCode(200)
            .setBody(
              """
              {
                "answer": "Valve logs live in ops/valves.md",
                "citations": [{"path": "ops/valves.md", "label": "valves.md"}]
              }
              """.trimIndent()
            )
        )

        val report = processor.flushQueued()
        assertEquals(3, report.sentCount)

        val req1 = server.takeRequest()
        val req2 = server.takeRequest()
        val req3 = server.takeRequest()
        assertEquals("/api/memory/suggest", req1.path)
        assertEquals(noteItem.id, req1.getHeader("Idempotency-Key"))
        assertEquals("/api/memory/suggest", req2.path)
        assertEquals(ideaItem.id, req2.getHeader("Idempotency-Key"))
        assertEquals("/api/ask", req3.path)
        assertEquals(qItem.id, req3.getHeader("Idempotency-Key"))

        val storedQuestion = dao.getOutboxItemById(qItem.id)
        assertNotNull(storedQuestion)
        assertEquals(OutboxState.SENT.wireValue, storedQuestion?.state)
        assertEquals("Valve logs live in ops/valves.md", storedQuestion?.answerText)
        val decodedCitations = EuleCitationCodec.decode(storedQuestion?.citationsJson)
        assertEquals(1, decodedCitations.size)
        assertEquals("ops/valves.md", decodedCitations[0].path)
      } finally {
        db.close()
        server.shutdown()
      }
    }
  }

  @Test
  fun `fake eule api demo mode supports health search ask and suggest`() {
    runBlocking {
      val fake = FakeEuleApi()
      assertTrue(fake.checkHealth())
      val searchRes = fake.search("culvert")
      assertTrue(searchRes is EuleApiResult.Success)
      assertEquals(1, (searchRes as EuleApiResult.Success).data.size)

      val askRes = fake.ask("How does sync work?")
      assertTrue(askRes is EuleApiResult.Success)
      assertTrue((askRes as EuleApiResult.Success).data.citations.isNotEmpty())

      val suggestRes = fake.suggestMemory("Offline idea")
      assertTrue(suggestRes is EuleApiResult.Success)
      assertEquals("pending", (suggestRes as EuleApiResult.Success).data.status)
    }
  }

  @Test
  fun `eule screen renders clickable citations and dialog shows full cited path`() {
    var tappedCitation: EuleCitation? = null
    val citation =
      EuleCitation(
        path = "projects/stellwerk/architecture.md",
        label = "architecture.md",
        snippet = "Tailscale HTTPS terminates at Caddy.",
      )
    val uiState =
      FeldbuchUiState(
        settings = FeldbuchSettings(euleDemoMode = true),
        euleReachable = true,
        euleAskLastQuestion = "How does HTTPS terminate?",
        euleAskResponse =
          EuleAskResponse(
            answer = "Tailscale HTTPS terminates cleanly at Caddy.",
            citations = listOf(citation),
          ),
      )

    composeRule.setContent {
      FeldbuchTheme {
        EuleScreen(
          uiState = uiState,
          onSearchQueryChange = {},
          onSearchSubmit = {},
          onAskInputChange = {},
          onAskSubmit = {},
          onRetryAsk = {},
          onSelectCitation = { tappedCitation = it },
          onRefreshEuleHealth = {},
          onFlushOutboxNow = {},
          onRetryOutboxItem = {},
          onClearSentOutbox = {},
          onNavigateToSetup = {},
        )
        EuleCitationDialog(
          citation = citation,
          onCopiedPath = {},
          onDismiss = {},
        )
      }
    }

    composeRule.onNodeWithTag("eule_ask_answer_text").assertIsDisplayed()
    composeRule.onNodeWithTag("eule_citation_chip_0").performClick()
    assertEquals("projects/stellwerk/architecture.md", tappedCitation?.path)
    composeRule
      .onNodeWithTag("citation_dialog_path")
      .assertIsDisplayed()
      .assertTextContains("projects/stellwerk/architecture.md")
  }
}
