package com.example.domain

import com.example.data.local.TreeFileEntity

data class CappedContextItem(
  val file: TreeFileEntity,
  val sentText: String,
  val originalChars: Int,
  val sentChars: Int,
  val wasTrimmed: Boolean,
)

data class CappedContextResult(
  val items: List<CappedContextItem>,
  val totalOriginalChars: Int,
  val totalSentChars: Int,
  val estimatedTokens: Int,
  val trimmedPaths: Set<String>,
)

object ContextCapLimiter {
  const val DEFAULT_PER_FILE_CHAR_CAP = 20_000
  const val DEFAULT_TOTAL_CHAR_CAP = 60_000

  fun apply(
    files: List<TreeFileEntity>,
    perFileCap: Int = DEFAULT_PER_FILE_CHAR_CAP,
    totalCap: Int = DEFAULT_TOTAL_CHAR_CAP,
  ): CappedContextResult {
    val safePerFile = perFileCap.coerceAtLeast(0)
    val safeTotal = totalCap.coerceAtLeast(0)

    var remainingTotal = safeTotal
    var totalOriginal = 0
    var totalSent = 0
    val items = mutableListOf<CappedContextItem>()
    val trimmedPaths = mutableSetOf<String>()

    files.forEach { file ->
      if (!PathBlocklist.isBlocked(file.path)) {
        val raw = file.cachedText.orEmpty()
        val origLen = raw.length
        totalOriginal += origLen

        val allowedForThisFile = minOf(origLen, safePerFile, remainingTotal)
        val sentText = raw.substring(0, allowedForThisFile)
        val trimmed = allowedForThisFile < origLen
        if (trimmed) trimmedPaths.add(file.path)

        remainingTotal = (remainingTotal - allowedForThisFile).coerceAtLeast(0)
        totalSent += allowedForThisFile

        items.add(
          CappedContextItem(
            file = file,
            sentText = sentText,
            originalChars = origLen,
            sentChars = allowedForThisFile,
            wasTrimmed = trimmed,
          )
        )
      }
    }

    return CappedContextResult(
      items = items,
      totalOriginalChars = totalOriginal,
      totalSentChars = totalSent,
      estimatedTokens = if (totalSent == 0) 0 else totalSent / 4,
      trimmedPaths = trimmedPaths,
    )
  }
}
