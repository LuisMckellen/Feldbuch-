package com.example.data.network

import android.net.Uri
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class PkceAuthSession(
  val state: String,
  val codeVerifier: String,
  val codeChallenge: String,
  val authorizeUrl: String,
  val clientId: String,
  val redirectUri: String,
)

data class GitHubDeviceCodeResponse(
  val deviceCode: String,
  val userCode: String,
  val verificationUri: String,
  val expiresInSeconds: Int,
  val intervalSeconds: Int,
)

sealed class DeviceTokenPollResult {
  data class Success(val accessToken: String) : DeviceTokenPollResult()

  data object Pending : DeviceTokenPollResult()

  data class SlowDown(val extraSeconds: Int = 5) : DeviceTokenPollResult()

  data object Expired : DeviceTokenPollResult()

  data object Denied : DeviceTokenPollResult()

  data class Error(val message: String) : DeviceTokenPollResult()
}

data class GitHubUserProfile(
  val login: String,
  val name: String,
  val avatarUrl: String,
)

data class GitHubRepositorySummary(
  val owner: String,
  val name: String,
  val fullName: String,
  val defaultBranch: String,
  val isPrivate: Boolean,
  val description: String,
)

data class OAuthCallbackPayload(
  val code: String?,
  val state: String?,
  val error: String?,
  val errorDescription: String?,
)

class GitHubOAuthClient(
  private val oauthBaseUrl: String = DEFAULT_OAUTH_BASE_URL,
  private val apiBaseUrl: String = DEFAULT_API_BASE_URL,
  private val enforceHttps: Boolean = true,
) {
  private val client: OkHttpClient = FeldbuchHttpClients.createGitHubClient(enforceHttps = enforceHttps)
  private val random = SecureRandom()

  fun createPkceSession(
    clientId: String,
    redirectUri: String = REDIRECT_URI,
    scopes: String = DEFAULT_SCOPES,
  ): Result<PkceAuthSession> {
    val cleanClientId = clientId.trim()
    if (cleanClientId.isEmpty()) {
      return Result.failure(
        IllegalArgumentException("GitHub OAuth Client ID is required to sign in.")
      )
    }

    val stateBytes = ByteArray(24).also { random.nextBytes(it) }
    val verifierBytes = ByteArray(32).also { random.nextBytes(it) }
    val state = encodeBase64Url(stateBytes)
    val codeVerifier = encodeBase64Url(verifierBytes)
    val challengeDigest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
    val codeChallenge = encodeBase64Url(challengeDigest)

    return try {
      val base =
        if (enforceHttps) {
          FeldbuchHttpClients.requireHttpsUrl(oauthBaseUrl, OkHttpService.GITHUB)
        } else {
          oauthBaseUrl.toHttpUrl()
        }
      val authorizeUrl =
        base
          .newBuilder()
          .addPathSegments("login/oauth/authorize")
          .addQueryParameter("client_id", cleanClientId)
          .addQueryParameter("redirect_uri", redirectUri)
          .addQueryParameter("scope", scopes)
          .addQueryParameter("state", state)
          .addQueryParameter("code_challenge", codeChallenge)
          .addQueryParameter("code_challenge_method", "S256")
          .build()
          .toString()

      Result.success(
        PkceAuthSession(
          state = state,
          codeVerifier = codeVerifier,
          codeChallenge = codeChallenge,
          authorizeUrl = authorizeUrl,
          clientId = cleanClientId,
          redirectUri = redirectUri,
        )
      )
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun exchangeAuthorizationCode(
    clientId: String,
    code: String,
    codeVerifier: String,
    redirectUri: String = REDIRECT_URI,
  ): Result<String> =
    withContext(Dispatchers.IO) {
      val cleanClientId = clientId.trim()
      val cleanCode = code.trim()
      if (cleanClientId.isEmpty() || cleanCode.isEmpty()) {
        return@withContext Result.failure(
          IllegalArgumentException("Client ID and authorization code are required.")
        )
      }

      try {
        val url =
          oauthUrl("login/oauth/access_token")
        val body =
          FormBody.Builder()
            .add("client_id", cleanClientId)
            .add("code", cleanCode)
            .add("redirect_uri", redirectUri)
            .add("code_verifier", codeVerifier)
            .build()

        val request =
          Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
          val raw = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            return@withContext Result.failure(
              IllegalStateException("GitHub OAuth token HTTP ${response.code}")
            )
          }
          val json = JSONObject(raw)
          val token = json.optString("access_token").trim()
          if (token.isNotEmpty()) {
            return@withContext Result.success(token)
          }
          val errDesc =
            json.optString("error_description").ifBlank {
              json.optString("error").ifBlank { "OAuth token exchange failed." }
            }
          Result.failure(IllegalStateException(errDesc))
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun requestDeviceCode(
    clientId: String,
    scopes: String = DEFAULT_SCOPES,
  ): Result<GitHubDeviceCodeResponse> =
    withContext(Dispatchers.IO) {
      val cleanClientId = clientId.trim()
      if (cleanClientId.isEmpty()) {
        return@withContext Result.failure(
          IllegalArgumentException("GitHub OAuth Client ID is required for Device Flow.")
        )
      }

      try {
        val url = oauthUrl("login/device/code")
        val body =
          FormBody.Builder()
            .add("client_id", cleanClientId)
            .add("scope", scopes)
            .build()

        val request =
          Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
          val raw = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            return@withContext Result.failure(
              IllegalStateException("GitHub device code HTTP ${response.code}")
            )
          }
          val json = JSONObject(raw)
          val deviceCode = json.optString("device_code").trim()
          val userCode = json.optString("user_code").trim()
          val verificationUri =
            json.optString("verification_uri").trim().ifBlank { "https://github.com/login/device" }
          if (deviceCode.isEmpty() || userCode.isEmpty()) {
            val err =
              json.optString("error_description").ifBlank {
                json.optString("error").ifBlank { "Invalid GitHub device code response." }
              }
            return@withContext Result.failure(IllegalStateException(err))
          }
          Result.success(
            GitHubDeviceCodeResponse(
              deviceCode = deviceCode,
              userCode = userCode,
              verificationUri = verificationUri,
              expiresInSeconds = json.optInt("expires_in", 900).coerceAtLeast(60),
              intervalSeconds = json.optInt("interval", 5).coerceAtLeast(1),
            )
          )
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun pollDeviceAccessTokenOnce(
    clientId: String,
    deviceCode: String,
  ): DeviceTokenPollResult =
    withContext(Dispatchers.IO) {
      try {
        val url = oauthUrl("login/oauth/access_token")
        val body =
          FormBody.Builder()
            .add("client_id", clientId.trim())
            .add("device_code", deviceCode.trim())
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .build()

        val request =
          Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
          val raw = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            return@withContext DeviceTokenPollResult.Error("GitHub token HTTP ${response.code}")
          }
          val json = JSONObject(raw)
          val token = json.optString("access_token").trim()
          if (token.isNotEmpty()) {
            return@withContext DeviceTokenPollResult.Success(token)
          }
          when (val error = json.optString("error")) {
            "authorization_pending" -> DeviceTokenPollResult.Pending
            "slow_down" -> DeviceTokenPollResult.SlowDown(5)
            "expired_token" -> DeviceTokenPollResult.Expired
            "access_denied" -> DeviceTokenPollResult.Denied
            else -> {
              val detail = json.optString("error_description").ifBlank { error.ifBlank { "OAuth polling error" } }
              DeviceTokenPollResult.Error(detail)
            }
          }
        }
      } catch (e: Exception) {
        DeviceTokenPollResult.Error(e.message ?: "Network error during OAuth login")
      }
    }

  suspend fun fetchAuthenticatedUser(accessToken: String): Result<GitHubUserProfile> =
    withContext(Dispatchers.IO) {
      val token = accessToken.trim()
      if (token.isEmpty()) {
        return@withContext Result.failure(IllegalArgumentException("Missing GitHub access token."))
      }
      try {
        val url = apiUrl("user")
        val request =
          Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("X-GitHub-Api-Version", "2022-11-28")
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).execute().use { response ->
          val raw = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            return@withContext Result.failure(
              IllegalStateException("GitHub user HTTP ${response.code}")
            )
          }
          val json = JSONObject(raw)
          val login = json.optString("login").trim()
          if (login.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Missing login in GitHub user response."))
          }
          Result.success(
            GitHubUserProfile(
              login = login,
              name = json.optString("name").ifBlank { login },
              avatarUrl = json.optString("avatar_url"),
            )
          )
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun fetchAccessibleRepos(accessToken: String): Result<List<GitHubRepositorySummary>> =
    withContext(Dispatchers.IO) {
      val token = accessToken.trim()
      if (token.isEmpty()) {
        return@withContext Result.failure(IllegalArgumentException("Missing GitHub access token."))
      }
      try {
        val base =
          if (enforceHttps) {
            FeldbuchHttpClients.requireHttpsUrl(apiBaseUrl, OkHttpService.GITHUB)
          } else {
            apiBaseUrl.toHttpUrl()
          }
        val url =
          base
            .newBuilder()
            .addPathSegments("user/repos")
            .addQueryParameter("visibility", "all")
            .addQueryParameter("affiliation", "owner,collaborator,organization_member")
            .addQueryParameter("sort", "updated")
            .addQueryParameter("per_page", "100")
            .build()

        val request =
          Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("X-GitHub-Api-Version", "2022-11-28")
            .addHeader("Authorization", "Bearer $token")
            .build()

        client.newCall(request).execute().use { response ->
          val raw = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            return@withContext Result.failure(
              IllegalStateException("GitHub repos HTTP ${response.code}")
            )
          }
          val arr = JSONArray(raw)
          val repos = buildList {
            for (i in 0 until arr.length()) {
              val item = arr.optJSONObject(i) ?: continue
              val ownerLogin = item.optJSONObject("owner")?.optString("login").orEmpty().trim()
              val name = item.optString("name").trim()
              val fullName = item.optString("full_name").trim().ifBlank { "$ownerLogin/$name" }
              if (ownerLogin.isNotEmpty() && name.isNotEmpty()) {
                add(
                  GitHubRepositorySummary(
                    owner = ownerLogin,
                    name = name,
                    fullName = fullName,
                    defaultBranch = item.optString("default_branch").trim().ifBlank { "main" },
                    isPrivate = item.optBoolean("private", false),
                    description = item.optString("description").takeUnless { it == "null" }.orEmpty(),
                  )
                )
              }
            }
          }
          Result.success(repos)
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  private fun oauthUrl(pathSegments: String) =
    (if (enforceHttps) {
        FeldbuchHttpClients.requireHttpsUrl(oauthBaseUrl, OkHttpService.GITHUB)
      } else {
        oauthBaseUrl.toHttpUrl()
      })
      .newBuilder()
      .addPathSegments(pathSegments)
      .build()

  private fun apiUrl(pathSegments: String) =
    (if (enforceHttps) {
        FeldbuchHttpClients.requireHttpsUrl(apiBaseUrl, OkHttpService.GITHUB)
      } else {
        apiBaseUrl.toHttpUrl()
      })
      .newBuilder()
      .addPathSegments(pathSegments)
      .build()

  private fun encodeBase64Url(bytes: ByteArray): String =
    Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

  companion object {
    const val DEFAULT_OAUTH_BASE_URL = "https://github.com"
    const val DEFAULT_API_BASE_URL = "https://api.github.com"
    const val REDIRECT_SCHEME = "feldbuch"
    const val REDIRECT_HOST = "oauth"
    const val REDIRECT_PATH = "/github"
    const val REDIRECT_URI = "$REDIRECT_SCHEME://$REDIRECT_HOST$REDIRECT_PATH"
    const val DEFAULT_SCOPES = "repo read:user"

    fun parseCallbackUri(uri: Uri?): OAuthCallbackPayload? {
      if (uri == null) return null
      if (uri.scheme != REDIRECT_SCHEME || uri.host != REDIRECT_HOST || uri.path != REDIRECT_PATH) {
        return null
      }
      return OAuthCallbackPayload(
        code = uri.getQueryParameter("code"),
        state = uri.getQueryParameter("state"),
        error = uri.getQueryParameter("error"),
        errorDescription = uri.getQueryParameter("error_description"),
      )
    }
  }
}
