package com.example.domain

import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed class UrlValidationResult {
  data object Valid : UrlValidationResult()

  data class Invalid(val message: String) : UrlValidationResult()
}

object UrlValidator {
  fun validateHttps(
    rawUrl: String,
    fieldName: String = "URL",
    allowBlank: Boolean = false,
  ): UrlValidationResult {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) {
      return if (allowBlank) UrlValidationResult.Valid
      else UrlValidationResult.Invalid("$fieldName is required")
    }

    val lower = trimmed.lowercase(Locale.ROOT)
    if (lower.startsWith("http://")) {
      return UrlValidationResult.Invalid("$fieldName must use https:// (http:// is not allowed)")
    }
    if (!lower.startsWith("https://")) {
      return UrlValidationResult.Invalid("$fieldName must start with https://")
    }

    val parsed = trimmed.toHttpUrlOrNull()
    return if (parsed == null || !parsed.isHttps || parsed.host.isBlank()) {
      UrlValidationResult.Invalid("$fieldName is not a valid https:// URL")
    } else {
      UrlValidationResult.Valid
    }
  }

  fun isValidHttps(rawUrl: String, allowBlank: Boolean = false): Boolean =
    validateHttps(rawUrl, allowBlank = allowBlank) is UrlValidationResult.Valid
}
