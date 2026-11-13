package com.example.data.network

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class EuleSearchHit(
  val path: String,
  val title: String,
  val snippet: String,
  val score: Double? = null,
)

data class EuleCitation(
  val path: String,
  val label: String,
  val snippet: String = "",
)

data class EuleAskResponse(
  val answer: String,
  val citations: List<EuleCitation>,
)

data class EuleSuggestResponse(
  val id: String,
  val status: String,
  val message: String,
)

sealed class EuleApiResult<out T> {
  data class Success<T>(val data: T) : EuleApiResult<T>()

  data class Failure(
    val shortReason: String,
    val detailMessage: String,
  ) : EuleApiResult<Nothing>()
}

interface EuleApi {
  suspend fun checkHealth(): Boolean

  suspend fun search(query: String, limit: Int = 20): EuleApiResult<List<EuleSearchHit>>

  suspend fun ask(
    question: String,
    idempotencyKey: String? = null,
  ): EuleApiResult<EuleAskResponse>

  suspend fun suggestMemory(
    text: String,
    from: String = "feldbuch",
    idempotencyKey: String? = null,
  ): EuleApiResult<EuleSuggestResponse>
}

object EuleCitationCodec {
  fun encode(citations: List<EuleCitation>): String {
    val arr = JSONArray()
    citations.forEach { c ->
      arr.put(
        JSONObject().apply {
          put("path", c.path)
          put("label", c.label)
          if (c.snippet.isNotBlank()) put("snippet", c.snippet)
        }
      )
    }
    return arr.toString()
  }

  fun decode(rawJson: String?): List<EuleCitation> {
    if (rawJson.isNullOrBlank()) return emptyList()
    return try {
      val arr = JSONArray(rawJson)
      buildList {
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          val path = obj.optString("path").trim()
          if (path.isNotEmpty()) {
            val label = obj.optString("label").trim().ifBlank { path.substringAfterLast('/') }
            add(
              EuleCitation(
                path = path,
                label = label,
                snippet = obj.optString("snippet").trim(),
              )
            )
          }
        }
      }
    } catch (_: Exception) {
      emptyList()
    }
  }
}

class OkHttpEuleApi(
  private val baseUrl: String,
  private val apiKey: String = "",
  private val enforceHttps: Boolean = true,
  callTimeoutSeconds: Long = 20L,
) : EuleApi {

  private val client: OkHttpClient =
    FeldbuchHttpClients.createEuleClient(
      enforceHttps = enforceHttps,
      callTimeoutSeconds = callTimeoutSeconds,
    )
  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  override suspend fun checkHealth(): Boolean =
    withContext(Dispatchers.IO) {
      val url =
        buildBaseUrl("health").getOrNull() ?: return@withContext false
      val req = applyOptionalAuth(Request.Builder().url(url).get()).build()
      try {
        client.newCall(req).execute().use { it.isSuccessful }
      } catch (_: Exception) {
        false
      }
    }

  override suspend fun search(
    query: String,
    limit: Int,
  ): EuleApiResult<List<EuleSearchHit>> =
    withContext(Dispatchers.IO) {
      val base = buildBaseUrl().getOrElse { return@withContext insecureUrlFailure(it) }
      val url =
        base
          .newBuilder()
          .addPathSegments("api/search")
          .addQueryParameter("q", query.trim())
          .addQueryParameter("limit", limit.coerceIn(1, 100).toString())
          .build()

      val req =
        applyOptionalAuth(
          Request.Builder().url(url).get().addHeader("Accept", "application/json")
        )
          .build()

      executeCall(req) { body -> parseSearchHits(body) }
    }

  override suspend fun ask(
    question: String,
    idempotencyKey: String?,
  ): EuleApiResult<EuleAskResponse> =
    withContext(Dispatchers.IO) {
      val url = buildBaseUrl("api/ask").getOrElse { return@withContext insecureUrlFailure(it) }
      val payload = JSONObject().put("question", question.trim()).toString()
      val builder =
        Request.Builder()
          .url(url)
          .post(payload.toRequestBody(jsonMediaType))
          .addHeader("Accept", "application/json")
      if (!idempotencyKey.isNullOrBlank()) {
        builder.addHeader("Idempotency-Key", idempotencyKey.trim())
      }
      val req = applyOptionalAuth(builder).build()
      executeCall(req) { body -> parseAskResponse(body) }
    }

  override suspend fun suggestMemory(
    text: String,
    from: String,
    idempotencyKey: String?,
  ): EuleApiResult<EuleSuggestResponse> =
    withContext(Dispatchers.IO) {
      val url =
        buildBaseUrl("api/memory/suggest").getOrElse { return@withContext insecureUrlFailure(it) }
      val payload =
        JSONObject()
          .put("text", text.trim())
          .put("from", from.trim().ifBlank { "feldbuch" })
          .toString()
      val builder =
        Request.Builder()
          .url(url)
          .post(payload.toRequestBody(jsonMediaType))
          .addHeader("Accept", "application/json")
      if (!idempotencyKey.isNullOrBlank()) {
        builder.addHeader("Idempotency-Key", idempotencyKey.trim())
      }
      val req = applyOptionalAuth(builder).build()
      executeCall(req) { body -> parseSuggestResponse(body) }
    }

  private fun buildBaseUrl(extraPath: String? = null) =
    runCatching {
      val clean = baseUrl.trim().trimEnd('/')
      val parsed =
        if (enforceHttps) {
          FeldbuchHttpClients.requireHttpsUrl(clean, OkHttpService.EULE)
        } else {
          clean.toHttpUrl()
        }
      if (extraPath.isNullOrBlank()) {
        parsed
      } else {
        parsed.newBuilder().addPathSegments(extraPath).build()
      }
    }

  private fun applyOptionalAuth(builder: Request.Builder): Request.Builder {
    val cleanKey = apiKey.trim()
    if (cleanKey.isNotEmpty()) {
      builder.addHeader("Authorization", "Bearer $cleanKey")
    }
    return builder
  }

  private fun insecureUrlFailure(t: Throwable): EuleApiResult.Failure =
    EuleApiResult.Failure(
      shortReason = "network",
      detailMessage = t.message ?: "Eule URL must use https://",
    )

  private fun <T> executeCall(
    request: Request,
    parser: (String) -> T,
  ): EuleApiResult<T> =
    try {
      client.newCall(request).execute().use { response ->
        val rawBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          val reason =
            when (response.code) {
              401 -> "401"
              402 -> "402"
              429 -> "429"
              500 -> "500"
              else -> "HTTP ${response.code}"
            }
          val detail = extractErrorNameMessage(rawBody, response.code)
          return EuleApiResult.Failure(shortReason = reason, detailMessage = detail)
        }
        EuleApiResult.Success(parser(rawBody))
      }
    } catch (_: SocketTimeoutException) {
      EuleApiResult.Failure("timeout", "Eule request timed out")
    } catch (e: InterruptedIOException) {
      if (e.message?.contains("timeout", ignoreCase = true) != false) {
        EuleApiResult.Failure("timeout", "Eule request timed out")
      } else {
        EuleApiResult.Failure("network", e.message ?: "Eule request interrupted")
      }
    } catch (e: JSONException) {
      EuleApiResult.Failure("malformed", "Malformed JSON from Eule: ${e.message ?: "parse error"}")
    } catch (e: InsecureHttpUrlException) {
      EuleApiResult.Failure("network", e.message ?: "Eule URL must use https://")
    } catch (e: IOException) {
      EuleApiResult.Failure("network", e.message ?: "Network error contacting Eule")
    } catch (e: Exception) {
      EuleApiResult.Failure("error", e.message ?: "Unexpected Eule error")
    }

  private fun extractErrorNameMessage(rawBody: String, code: Int): String {
    if (rawBody.isBlank()) return "HTTP $code error"
    return try {
      val root = JSONObject(rawBody)
      val name = root.optString("name").trim()
      val msg = root.optString("message").trim()
      when {
        name.isNotEmpty() && msg.isNotEmpty() && !msg.equals(name, ignoreCase = true) ->
          "$name: $msg"
        name.isNotEmpty() -> name
        msg.isNotEmpty() -> msg
        else -> {
          val nested = root.optJSONObject("error")?.optString("message").orEmpty().trim()
          nested.ifBlank { "HTTP $code error" }
        }
      }
    } catch (_: Exception) {
      rawBody.take(180)
    }
  }

  private fun parseSearchHits(rawBody: String): List<EuleSearchHit> {
    val trimmed = rawBody.trim()
    val arr =
      if (trimmed.startsWith("[")) {
        JSONArray(trimmed)
      } else {
        val root = JSONObject(trimmed)
        root.optJSONArray("hits")
          ?: root.optJSONArray("results")
          ?: root.optJSONArray("items")
          ?: throw JSONException("Missing hits array in search response")
      }
    return buildList {
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val path = obj.optString("path").trim()
        if (path.isNotEmpty()) {
          val title = obj.optString("title").trim().ifBlank { path.substringAfterLast('/') }
          val snippet = obj.optString("snippet").trim().ifBlank { obj.optString("text").trim() }
          val score = if (obj.has("score") && !obj.isNull("score")) obj.optDouble("score") else null
          add(EuleSearchHit(path = path, title = title, snippet = snippet, score = score))
        }
      }
    }
  }

  private fun parseAskResponse(rawBody: String): EuleAskResponse {
    val root = JSONObject(rawBody)
    if (!root.has("answer") && !root.has("text")) {
      throw JSONException("Missing answer field in /api/ask response")
    }
    val answer = root.optString("answer").ifBlank { root.optString("text") }.trim()
    val citationsArr = root.optJSONArray("citations") ?: JSONArray()
    val citations = buildList {
      for (i in 0 until citationsArr.length()) {
        when (val elem = citationsArr.opt(i)) {
          is String -> {
            val path = elem.trim()
            if (path.isNotEmpty()) {
              add(EuleCitation(path = path, label = path.substringAfterLast('/')))
            }
          }
          is JSONObject -> {
            val path = elem.optString("path").trim()
            if (path.isNotEmpty()) {
              val label =
                elem.optString("title").trim()
                  .ifBlank { elem.optString("label").trim() }
                  .ifBlank { path.substringAfterLast('/') }
              val snippet = elem.optString("snippet").trim()
              add(EuleCitation(path = path, label = label, snippet = snippet))
            }
          }
        }
      }
    }
    return EuleAskResponse(answer = answer, citations = citations)
  }

  private fun parseSuggestResponse(rawBody: String): EuleSuggestResponse {
    val root = JSONObject(rawBody)
    val id = root.optString("id").trim()
    val status = root.optString("status").trim().ifBlank { "pending" }
    val message = root.optString("message").trim().ifBlank { "Pending owner approval" }
    if (id.isEmpty() && !root.has("status") && !root.has("ok")) {
      throw JSONException("Unexpected /api/memory/suggest response")
    }
    return EuleSuggestResponse(id = id, status = status, message = message)
  }
}

class FakeEuleApi(
  var reachable: Boolean = true,
) : EuleApi {

  private val demoCorpus =
    listOf(
      EuleSearchHit(
        path = "memory/2026-11-10-field-ops.md",
        title = "Field Ops Handoff",
        snippet = "Drainage inspection completed at north culvert; gate latch replaced.",
        score = 0.94,
      ),
      EuleSearchHit(
        path = "projects/stellwerk/architecture.md",
        title = "Stellwerk Architecture",
        snippet = "Tailscale HTTPS terminates at Caddy; Feldbuch syncs queued captures to Eule.",
        score = 0.89,
      ),
      EuleSearchHit(
        path = "notes/backup-checklist.md",
        title = "Weekly Backup Checklist",
        snippet = "Verify Tailscale ACLs, SQLite WAL snapshots, and memory suggestion queue.",
        score = 0.82,
      ),
    )

  override suspend fun checkHealth(): Boolean = reachable

  override suspend fun search(
    query: String,
    limit: Int,
  ): EuleApiResult<List<EuleSearchHit>> {
    val q = query.trim().lowercase(Locale.ROOT)
    val matches =
      if (q.isEmpty()) {
        demoCorpus
      } else {
        demoCorpus.filter {
          it.path.lowercase(Locale.ROOT).contains(q) ||
            it.title.lowercase(Locale.ROOT).contains(q) ||
            it.snippet.lowercase(Locale.ROOT).contains(q)
        }
      }
    return EuleApiResult.Success(matches.take(limit.coerceAtLeast(1)))
  }

  override suspend fun ask(
    question: String,
    idempotencyKey: String?,
  ): EuleApiResult<EuleAskResponse> {
    val cleanQ = question.trim()
    if (cleanQ.isEmpty()) {
      return EuleApiResult.Failure("400", "Question cannot be blank.")
    }
    return EuleApiResult.Success(
      EuleAskResponse(
        answer =
          "[Demo mode] Eule found relevant context for \"$cleanQ\": Feldbuch captures queue offline and sync suggestions for owner review over Tailscale HTTPS.",
        citations =
          listOf(
            EuleCitation(
              path = "projects/stellwerk/architecture.md",
              label = "architecture.md",
              snippet = "Tailscale HTTPS terminates at Caddy; Feldbuch syncs queued captures to Eule.",
            ),
            EuleCitation(
              path = "memory/2026-11-10-field-ops.md",
              label = "2026-11-10-field-ops.md",
              snippet = "Drainage inspection completed at north culvert; gate latch replaced.",
            ),
          ),
      )
    )
  }

  override suspend fun suggestMemory(
    text: String,
    from: String,
    idempotencyKey: String?,
  ): EuleApiResult<EuleSuggestResponse> {
    val sugId = "demo-${(idempotencyKey ?: text.hashCode().toString()).take(8)}"
    return EuleApiResult.Success(
      EuleSuggestResponse(
        id = sugId,
        status = "pending",
        message = "Saved as pending memory suggestion for owner approval.",
      )
    )
  }
}
