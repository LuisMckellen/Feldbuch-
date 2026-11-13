package com.example.domain

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

object PathBlocklist {

  fun normalize(rawPath: String): String {
    var current = percentDecodeSafely(rawPath.trim())
    current = current.replace('\\', '/')
    current = current.replace(Regex("/+"), "/")
    while (current.startsWith("./") || current.startsWith("/")) {
      current =
        when {
          current.startsWith("./") -> current.removePrefix("./")
          current.startsWith("/") -> current.removePrefix("/")
          else -> current
        }
    }
    return current.lowercase(Locale.ROOT)
  }

  fun isBlocked(rawPath: String): Boolean {
    val normalized = normalize(rawPath)
    if (normalized.isEmpty()) return false

    if (
      normalized == "memory" ||
        normalized.startsWith("memory/") ||
        normalized.contains("/memory/") ||
        normalized.endsWith("/memory")
    ) {
      return true
    }

    if (normalized.contains("transcripts")) {
      return true
    }

    val fileName = normalized.substringAfterLast('/')
    if (fileName.startsWith(".env")) return true
    if (fileName.endsWith(".key")) return true
    if (fileName.endsWith(".pem")) return true

    return false
  }

  fun filterAllowed(paths: Set<String>): Set<String> = paths.filterNot(::isBlocked).toSet()

  private fun percentDecodeSafely(input: String): String {
    var result = input
    repeat(2) {
      val decoded = decodeOnce(result)
      if (decoded == result) return result
      result = decoded
    }
    return result
  }

  private fun decodeOnce(input: String): String {
    if (!input.contains('%')) return input
    val out = ByteArrayOutputStream(input.length)
    var i = 0
    while (i < input.length) {
      val c = input[i]
      if (c == '%' && i + 2 < input.length) {
        val hi = Character.digit(input[i + 1], 16)
        val lo = Character.digit(input[i + 2], 16)
        if (hi >= 0 && lo >= 0) {
          out.write((hi shl 4) or lo)
          i += 3
          continue
        }
      }
      val bytes = c.toString().toByteArray(StandardCharsets.UTF_8)
      out.write(bytes, 0, bytes.size)
      i++
    }
    return String(out.toByteArray(), StandardCharsets.UTF_8)
  }
}
