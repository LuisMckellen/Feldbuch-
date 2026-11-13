package com.example.data.network

import com.example.domain.UrlValidationResult
import com.example.domain.UrlValidator
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

enum class OkHttpService(val displayName: String) {
  HUB("Hub"),
  EULE("Eule"),
  CHAT("Chat"),
  GITHUB("GitHub"),
}

class InsecureHttpUrlException(message: String) : IOException(message)

class HttpsOnlyInterceptor(
  private val service: OkHttpService,
  private val enforceHttps: Boolean = true,
) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val url = request.url
    if (enforceHttps && !url.isHttps) {
      throw InsecureHttpUrlException(
        "${service.displayName} URL must use https:// (${url.scheme}:// is not allowed): $url"
      )
    }
    return chain.proceed(request)
  }
}

object FeldbuchHttpClients {

  fun requireHttpsUrl(
    rawUrl: String,
    service: OkHttpService,
  ): HttpUrl {
    val trimmed = rawUrl.trim()
    when (val result = UrlValidator.validateHttps(trimmed, "${service.displayName} URL")) {
      is UrlValidationResult.Invalid -> throw InsecureHttpUrlException(result.message)
      is UrlValidationResult.Valid -> Unit
    }
    val parsed =
      trimmed.toHttpUrlOrNull()
        ?: throw InsecureHttpUrlException("${service.displayName} URL is malformed: $trimmed")
    if (!parsed.isHttps) {
      throw InsecureHttpUrlException(
        "${service.displayName} URL must use https:// (${parsed.scheme}:// is not allowed): $trimmed"
      )
    }
    return parsed
  }

  fun createClient(
    service: OkHttpService,
    enforceHttps: Boolean = true,
    connectTimeoutSeconds: Long = 15L,
    readTimeoutSeconds: Long = 30L,
    writeTimeoutSeconds: Long = 30L,
    callTimeoutSeconds: Long = 45L,
  ): OkHttpClient {
    val interceptor = HttpsOnlyInterceptor(service = service, enforceHttps = enforceHttps)
    return OkHttpClient.Builder()
      .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
      .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
      .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
      .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
      .addInterceptor(interceptor)
      .addNetworkInterceptor(interceptor)
      .build()
  }

  fun createHubClient(enforceHttps: Boolean = true): OkHttpClient =
    createClient(
      service = OkHttpService.HUB,
      enforceHttps = enforceHttps,
      connectTimeoutSeconds = 2L,
      readTimeoutSeconds = 2L,
      writeTimeoutSeconds = 2L,
      callTimeoutSeconds = 2L,
    )

  fun createEuleClient(
    enforceHttps: Boolean = true,
    callTimeoutSeconds: Long = 30L,
  ): OkHttpClient =
    createClient(
      service = OkHttpService.EULE,
      enforceHttps = enforceHttps,
      connectTimeoutSeconds = 15L,
      readTimeoutSeconds = callTimeoutSeconds,
      writeTimeoutSeconds = 30L,
      callTimeoutSeconds = callTimeoutSeconds,
    )

  fun createChatClient(
    enforceHttps: Boolean = true,
    callTimeoutSeconds: Long = 75L,
  ): OkHttpClient =
    createClient(
      service = OkHttpService.CHAT,
      enforceHttps = enforceHttps,
      connectTimeoutSeconds = 15L,
      readTimeoutSeconds = callTimeoutSeconds,
      writeTimeoutSeconds = 30L,
      callTimeoutSeconds = callTimeoutSeconds,
    )

  fun createGitHubClient(enforceHttps: Boolean = true): OkHttpClient =
    createClient(
      service = OkHttpService.GITHUB,
      enforceHttps = enforceHttps,
      connectTimeoutSeconds = 15L,
      readTimeoutSeconds = 30L,
      writeTimeoutSeconds = 30L,
      callTimeoutSeconds = 45L,
    )
}
