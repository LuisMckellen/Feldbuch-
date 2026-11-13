package com.example.data.network

import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class ApiChatMessage(val role: String, val content: String)

sealed class ChatApiResult {
  data class Success(val reply: String) : ChatApiResult()

  data class Failure(
    val shortReason: String, // timeout, 401, 402, 429, network, or HTTP code
    val detailMessage: String,
  ) : ChatApiResult()
}

class OpenAiClient(
  private val enforceHttps: Boolean = true,
  callTimeoutSeconds: Long = 75L,
) {
  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  internal val hubClient: OkHttpClient =
    FeldbuchHttpClients.createHubClient(enforceHttps = enforceHttps)

  internal val apiClient: OkHttpClient =
    FeldbuchHttpClients.createChatClient(
      enforceHttps = enforceHttps,
      callTimeoutSeconds = callTimeoutSeconds,
    )

  suspend fun checkHubReachable(hubUrl: String): Boolean =
    withContext(Dispatchers.IO) {
      val trimmed = hubUrl.trim()
      if (trimmed.isEmpty()) return@withContext false
      try {
        checkHubReachableOrThrow(trimmed)
      } catch (_: Exception) {
        false
      }
    }

  suspend fun checkHubReachableOrThrow(hubUrl: String): Boolean =
    withContext(Dispatchers.IO) {
      val trimmed = hubUrl.trim()
      if (enforceHttps) {
        FeldbuchHttpClients.requireHttpsUrl(trimmed, OkHttpService.HUB)
      }
      val request = Request.Builder().url(trimmed).get().build()
      hubClient.newCall(request).execute().use { response -> response.isSuccessful }
    }

  suspend fun fetchModels(endpointUrl: String, apiKey: String): Result<List<String>> =
    withContext(Dispatchers.IO) {
      val base = normalizeBaseEndpoint(endpointUrl)
      if (base.isEmpty()) {
        return@withContext Result.failure(
          InsecureHttpUrlException("Chat URL is required and must use https://")
        )
      }
      try {
        if (enforceHttps) {
          FeldbuchHttpClients.requireHttpsUrl(base, OkHttpService.CHAT)
        }
        val url = "$base/models"
        val reqBuilder = Request.Builder().url(url).get()
        if (apiKey.isNotBlank()) {
          reqBuilder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
        }
        apiClient.newCall(reqBuilder.build()).execute().use { response ->
          val body = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            return@withContext Result.failure(
              IllegalStateException("HTTP ${response.code}: ${body.take(200)}")
            )
          }
          val root = JSONObject(body)
          val dataArray = root.optJSONArray("data") ?: JSONArray()
          val models = buildList {
            for (i in 0 until dataArray.length()) {
              val id = dataArray.optJSONObject(i)?.optString("id").orEmpty()
              if (id.isNotBlank()) add(id)
            }
          }
          Result.success(models)
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun sendChatCompletion(
    endpointUrl: String,
    apiKey: String,
    model: String,
    messages: List<ApiChatMessage>,
    maxTokens: Int = 2048,
  ): ChatApiResult =
    withContext(Dispatchers.IO) {
      val base = normalizeBaseEndpoint(endpointUrl)
      if (base.isEmpty()) {
        return@withContext ChatApiResult.Failure(
          shortReason = "network",
          detailMessage = "Set your HTTPS /v1 endpoint URL in Setup before chatting.",
        )
      }
      try {
        if (enforceHttps) {
          FeldbuchHttpClients.requireHttpsUrl(base, OkHttpService.CHAT)
        }
        val url =
          if (base.endsWith("/chat/completions")) base else "$base/chat/completions"

        val messagesJson = JSONArray()
        messages.forEach { msg ->
          messagesJson.put(
            JSONObject().apply {
              put("role", msg.role)
              put("content", msg.content)
            }
          )
        }

        val payload =
          JSONObject().apply {
            put("model", model.ifBlank { "gpt-4o-mini" })
            put("max_tokens", maxTokens.coerceAtLeast(1))
            put("messages", messagesJson)
          }

        val reqBuilder =
          Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")

        if (apiKey.isNotBlank()) {
          reqBuilder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
        }

        apiClient.newCall(reqBuilder.build()).execute().use { response ->
          val body = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            val reason =
              when (response.code) {
                401 -> "401"
                402 -> "402"
                429 -> "429"
                else -> "HTTP ${response.code}"
              }
            val serverMessage = extractErrorMessage(body).ifBlank { "HTTP ${response.code}" }
            return@withContext ChatApiResult.Failure(
              shortReason = reason,
              detailMessage = serverMessage,
            )
          }
          val root = JSONObject(body)
          val choices = root.optJSONArray("choices")
          val content =
            choices
              ?.optJSONObject(0)
              ?.optJSONObject("message")
              ?.optString("content")
              ?.trim()
              .orEmpty()

          if (content.isEmpty()) {
            return@withContext ChatApiResult.Failure(
              shortReason = "network",
              detailMessage = "Endpoint returned an empty reply.",
            )
          }
          ChatApiResult.Success(content)
        }
      } catch (e: SocketTimeoutException) {
        ChatApiResult.Failure("timeout", e.message ?: "Request timed out.")
      } catch (e: InterruptedIOException) {
        ChatApiResult.Failure("timeout", e.message ?: "Request timed out.")
      } catch (e: Exception) {
        ChatApiResult.Failure("network", e.message ?: "Network error.")
      }
    }

  private fun extractErrorMessage(rawBody: String): String {
    if (rawBody.isBlank()) return ""
    return try {
      val root = JSONObject(rawBody)
      val errObj = root.optJSONObject("error")
      errObj?.optString("message")?.takeIf { it.isNotBlank() }
        ?: root.optString("message").takeIf { it.isNotBlank() }
        ?: root.optString("name").takeIf { it.isNotBlank() }
        ?: rawBody.take(180)
    } catch (_: Exception) {
      rawBody.take(180)
    }
  }

  internal fun normalizeBaseEndpoint(rawUrl: String): String {
    val trimmed = rawUrl.trim().trimEnd('/')
    return if (trimmed.endsWith("/chat/completions")) {
      trimmed.removeSuffix("/chat/completions")
    } else {
      trimmed
    }
  }
}
