package com.example.domain

import com.example.data.local.TreeFileEntity
import java.util.Calendar
import java.util.Locale

data class FolderGroup(
  val folderPath: String, // "(root)" for top-level files, otherwise "dir/sub/"
  val files: List<TreeFileEntity>,
)

data class TraySummary(
  val fileCount: Int,
  val totalChars: Int,
  val estimatedTokens: Int,
)

object TreeBrowserHelper {

  fun filterPathsByFilename(
    files: List<TreeFileEntity>,
    filterQuery: String,
  ): List<TreeFileEntity> {
    val needle = filterQuery.trim().lowercase(Locale.ROOT)
    if (needle.isEmpty()) return files
    return files.filter { it.path.lowercase(Locale.ROOT).contains(needle) }
  }

  fun filterFilesByName(
    files: List<TreeFileEntity>,
    query: String,
    matchFullPath: Boolean = false,
  ): List<TreeFileEntity> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return files
    val tokens = trimmed.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return files
    val searchAgainstPath = matchFullPath || trimmed.contains('/')
    return files.filter { file ->
      val target =
        if (searchAgainstPath) {
          file.path.lowercase(Locale.ROOT)
        } else {
          file.path.substringAfterLast('/').lowercase(Locale.ROOT)
        }
      tokens.all { token -> target.contains(token) }
    }
  }

  fun groupByFolder(files: List<TreeFileEntity>): List<FolderGroup> {
    val grouped = linkedMapOf<String, MutableList<TreeFileEntity>>()
    files.forEach { file ->
      val lastSlash = file.path.lastIndexOf('/')
      val folder =
        if (lastSlash <= 0) "(root)" else file.path.substring(0, lastSlash + 1)
      grouped.getOrPut(folder) { mutableListOf() }.add(file)
    }
    return grouped.entries
      .sortedBy { if (it.key == "(root)") "" else it.key }
      .map { FolderGroup(folderPath = it.key, files = it.value.sortedBy { f -> f.path }) }
  }

  fun computeTraySummary(trayFiles: List<TreeFileEntity>): TraySummary {
    val totalChars = trayFiles.sumOf { it.cachedText?.length ?: 0 }
    val estimatedTokens = if (totalChars == 0) 0 else totalChars / 4
    return TraySummary(
      fileCount = trayFiles.size,
      totalChars = totalChars,
      estimatedTokens = estimatedTokens,
    )
  }

  fun isCurrentClockPast1500(calendar: Calendar = Calendar.getInstance()): Boolean =
    calendar.get(Calendar.HOUR_OF_DAY) >= 15

  fun shouldShowHomeBanner(
    unsentCount: Int,
    hubReachable: Boolean,
    isPast1500: Boolean,
  ): Boolean {
    if (unsentCount <= 0) return false
    return hubReachable || isPast1500
  }

  fun shouldShowQuietBadge(
    unsentCount: Int,
    hubReachable: Boolean,
    isPast1500: Boolean,
  ): Boolean {
    if (unsentCount <= 0) return false
    return !shouldShowHomeBanner(unsentCount, hubReachable, isPast1500)
  }
}
