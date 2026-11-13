package com.example.data.network

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class ApiChatMessage(val role: String, val content: String)

class OpenAiClient {
  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  private val hubClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(2, TimeUnit.SECONDS)
      .readTimeout(2, TimeUnit.SECONDS)
      .callTimeout(2, TimeUnit.SECONDS)
      .build()

  private val apiClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .callTimeout(75, TimeUnit.SECONDS)
      .build()

  suspend fun checkHubReachable(hubUrl: String): Boolean =
    withContext(Dispatchers.IO) {
      val trimmed = hubUrl.trim()
      if (trimmed.isEmpty()) return@withContext false
      try {
        val request = Request.Builder().url(trimmed).get().build()
        hubClient.newCall(request).execute().use { response -> response.isSuccessful }
      } catch (_: Exception) {
        false
      }
    }

  suspend fun fetchModels(endpointUrl: String, apiKey: String): Result<List<String>> =
    withContext(Dispatchers.IO) {
      val base = normalizeBaseEndpoint(endpointUrl)
      if (base.isEmpty()) {
        return@withContext Result.failure(IllegalArgumentException("Endpoint URL is not configured."))
      }
      try {
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
  ): Result<String> =
    withContext(Dispatchers.IO) {
      val base = normalizeBaseEndpoint(endpointUrl)
      if (base.isEmpty()) {
        return@withContext Result.failure(
          IllegalArgumentException("Set your /v1 endpoint URL in Setup before chatting.")
        )
      }
      try {
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
            return@withContext Result.failure(
              IllegalStateException("Endpoint returned HTTP ${response.code}: ${body.take(240)}")
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
            return@withContext Result.failure(
              IllegalStateException("Endpoint returned an empty reply.")
            )
          }
          Result.success(content)
        }
      } catch (e: Exception) {
        Result.failure(e)
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
