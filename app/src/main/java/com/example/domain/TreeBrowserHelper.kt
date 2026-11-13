package com.example.domain

import com.example.data.local.TreeFileEntity
import java.time.LocalTime

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
    val needle = filterQuery.trim().lowercase()
    if (needle.isEmpty()) return files
    return files.filter { it.path.lowercase().contains(needle) }
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

  fun shouldShowHomeBanner(
    unsentCount: Int,
    hubReachable: Boolean,
    localTime: LocalTime,
  ): Boolean {
    if (unsentCount <= 0) return false
    val isPast1500 = !localTime.isBefore(LocalTime.of(15, 0))
    return hubReachable || isPast1500
  }

  fun shouldShowQuietBadge(
    unsentCount: Int,
    hubReachable: Boolean,
    localTime: LocalTime,
  ): Boolean {
    if (unsentCount <= 0) return false
    return !shouldShowHomeBanner(unsentCount, hubReachable, localTime)
  }
}
