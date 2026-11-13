package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class GitHubTreeEntry(
  val path: String,
  val sizeBytes: Long,
)

class GitHubClient(
  private val baseUrl: String = DEFAULT_BASE_URL,
  private val enforceHttps: Boolean = true,
) {
  private val client: OkHttpClient = FeldbuchHttpClients.createGitHubClient(enforceHttps = enforceHttps)

  suspend fun fetchTree(
    owner: String,
    repo: String,
    branch: String,
    pat: String,
  ): Result<List<GitHubTreeEntry>> =
    withContext(Dispatchers.IO) {
      val cleanOwner = owner.trim()
      val cleanRepo = repo.trim()
      val cleanBranch = branch.trim().ifBlank { "main" }

      if (cleanOwner.isEmpty() || cleanRepo.isEmpty()) {
        return@withContext Result.failure(
          IllegalArgumentException("GitHub owner and repo are required.")
        )
      }

      try {
        val parsedBase =
          if (enforceHttps) {
            FeldbuchHttpClients.requireHttpsUrl(baseUrl, OkHttpService.GITHUB)
          } else {
            baseUrl.toHttpUrl()
          }
        val url =
          parsedBase
            .newBuilder()
            .addPathSegment("repos")
            .addPathSegment(cleanOwner)
            .addPathSegment(cleanRepo)
            .addPathSegment("git")
            .addPathSegment("trees")
            .addPathSegment(cleanBranch)
            .addQueryParameter("recursive", "1")
            .build()

        val reqBuilder =
          Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("X-GitHub-Api-Version", "2022-11-28")

        if (pat.isNotBlank()) {
          reqBuilder.addHeader("Authorization", "Bearer ${pat.trim()}")
        }

        client.newCall(reqBuilder.build()).execute().use { response ->
          val body = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            return@withContext Result.failure(
              IllegalStateException("GitHub tree HTTP ${response.code}")
            )
          }

          val root = JSONObject(body)
          val treeArr = root.optJSONArray("tree") ?: JSONArray()
          val entries = buildList {
            for (i in 0 until treeArr.length()) {
              val item = treeArr.optJSONObject(i) ?: continue
              if (item.optString("type") == "blob") {
                val path = item.optString("path")
                if (path.isNotBlank()) {
                  add(GitHubTreeEntry(path = path, sizeBytes = item.optLong("size", 0L)))
                }
              }
            }
          }
          Result.success(entries.sortedBy { it.path })
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun fetchRawFile(
    owner: String,
    repo: String,
    branch: String,
    path: String,
    pat: String,
  ): Result<String> =
    withContext(Dispatchers.IO) {
      val cleanOwner = owner.trim()
      val cleanRepo = repo.trim()
      val cleanBranch = branch.trim().ifBlank { "main" }

      if (cleanOwner.isEmpty() || cleanRepo.isEmpty()) {
        return@withContext Result.failure(
          IllegalArgumentException("GitHub owner and repo are required.")
        )
      }

      try {
        val parsedBase =
          if (enforceHttps) {
            FeldbuchHttpClients.requireHttpsUrl(baseUrl, OkHttpService.GITHUB)
          } else {
            baseUrl.toHttpUrl()
          }
        val urlBuilder =
          parsedBase
            .newBuilder()
            .addPathSegment("repos")
            .addPathSegment(cleanOwner)
            .addPathSegment(cleanRepo)
            .addPathSegment("contents")

        path.trimStart('/').split('/').filter { it.isNotEmpty() }.forEach { segment ->
          urlBuilder.addPathSegment(segment)
        }
        urlBuilder.addQueryParameter("ref", cleanBranch)

        val reqBuilder =
          Request.Builder()
            .url(urlBuilder.build())
            .get()
            .addHeader("Accept", "application/vnd.github.raw")
            .addHeader("X-GitHub-Api-Version", "2022-11-28")

        if (pat.isNotBlank()) {
          reqBuilder.addHeader("Authorization", "Bearer ${pat.trim()}")
        }

        client.newCall(reqBuilder.build()).execute().use { response ->
          val body = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            return@withContext Result.failure(
              IllegalStateException("GitHub content HTTP ${response.code} for $path")
            )
          }
          Result.success(body)
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  companion object {
    const val DEFAULT_BASE_URL = "https://api.github.com"
  }
}
