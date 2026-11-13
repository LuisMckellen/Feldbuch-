package com.example.domain

import com.example.data.local.TreeFileEntity
import java.util.Locale

data class TreeFolderNode(
  val name: String,
  val folderPath: String, // e.g. "docs/" or "docs/api/"
  val subfolders: List<TreeFolderNode>,
  val files: List<TreeFileEntity>,
  val totalFileCount: Int,
  val selectedFileCount: Int,
)

data class BreadcrumbSegment(
  val label: String,
  val folderPath: String, // "" for root, "docs/", "docs/api/"
)

data class RepoTreeRoot(
  val rootFolders: List<TreeFolderNode>,
  val rootFiles: List<TreeFileEntity>,
) {
  val totalFileCount: Int
    get() = rootFiles.size + rootFolders.sumOf { it.totalFileCount }
}

sealed class VisibleTreeRow(open val key: String, open val depth: Int) {
  data class FolderRow(
    val folderPath: String,
    val name: String,
    override val depth: Int,
    val isExpanded: Boolean,
    val totalFileCount: Int,
    val selectedFileCount: Int,
  ) : VisibleTreeRow(key = "dir:$folderPath", depth = depth)

  data class FileRow(
    val file: TreeFileEntity,
    val fileName: String,
    override val depth: Int,
    val isBlocked: Boolean,
    val isSelected: Boolean,
    val isTrimmed: Boolean,
    val isLoading: Boolean,
  ) : VisibleTreeRow(key = "file:${file.path}", depth = depth)
}

object TreeHierarchyBuilder {

  private class MutableFolder(val name: String, val folderPath: String) {
    val subfolders = linkedMapOf<String, MutableFolder>()
    val files = mutableListOf<TreeFileEntity>()

    fun toImmutable(selectedTrayPaths: Set<String>): TreeFolderNode {
      val immutableSubs =
        subfolders.values
          .sortedBy { it.name.lowercase(Locale.ROOT) }
          .map { it.toImmutable(selectedTrayPaths) }
      val sortedFiles = files.sortedBy { it.path.substringAfterLast('/').lowercase(Locale.ROOT) }
      val directSelected =
        sortedFiles.count { !PathBlocklist.isBlocked(it.path) && it.path in selectedTrayPaths }
      val totalCount = sortedFiles.size + immutableSubs.sumOf { it.totalFileCount }
      val totalSelected = directSelected + immutableSubs.sumOf { it.selectedFileCount }
      return TreeFolderNode(
        name = name,
        folderPath = folderPath,
        subfolders = immutableSubs,
        files = sortedFiles,
        totalFileCount = totalCount,
        selectedFileCount = totalSelected,
      )
    }
  }

  fun buildTree(
    files: List<TreeFileEntity>,
    filterQuery: String = "",
    selectedTrayPaths: Set<String> = emptySet(),
    matchNameOnly: Boolean = false,
  ): RepoTreeRoot {
    val filtered =
      if (matchNameOnly) {
        TreeBrowserHelper.filterFilesByName(files, filterQuery, matchFullPath = false)
      } else {
        TreeBrowserHelper.filterPathsByFilename(files, filterQuery)
      }
    val root = MutableFolder(name = "", folderPath = "")

    filtered.forEach { file ->
      val cleanPath = file.path.trimStart('/')
      if (cleanPath.isEmpty()) return@forEach
      val segments = cleanPath.split('/')
      if (segments.size == 1) {
        root.files.add(file)
      } else {
        var cursor = root
        val pathBuilder = StringBuilder()
        for (i in 0 until segments.size - 1) {
          val segment = segments[i]
          if (segment.isEmpty()) continue
          pathBuilder.append(segment).append('/')
          val currentFolderPath = pathBuilder.toString()
          cursor =
            cursor.subfolders.getOrPut(segment) {
              MutableFolder(name = segment, folderPath = currentFolderPath)
            }
        }
        cursor.files.add(file)
      }
    }

    val rootImmutable = root.toImmutable(selectedTrayPaths)
    return RepoTreeRoot(
      rootFolders = rootImmutable.subfolders,
      rootFiles = rootImmutable.files,
    )
  }

  fun flattenVisibleRows(
    root: RepoTreeRoot,
    expandedFolders: Map<String, Boolean>,
    isFiltering: Boolean,
    selectedTrayPaths: Set<String>,
    trimmedTrayPaths: Set<String>,
    loadingPaths: Set<String>,
  ): List<VisibleTreeRow> {
    val rows = mutableListOf<VisibleTreeRow>()

    fun appendFolder(folder: TreeFolderNode, depth: Int) {
      val defaultExpanded = depth == 0 && root.rootFolders.size <= 2
      val isExpanded = isFiltering || (expandedFolders[folder.folderPath] ?: defaultExpanded)
      rows.add(
        VisibleTreeRow.FolderRow(
          folderPath = folder.folderPath,
          name = folder.name,
          depth = depth,
          isExpanded = isExpanded,
          totalFileCount = folder.totalFileCount,
          selectedFileCount = folder.selectedFileCount,
        )
      )
      if (isExpanded) {
        folder.subfolders.forEach { childFolder -> appendFolder(childFolder, depth + 1) }
        folder.files.forEach { file -> rows.add(toFileRow(file, depth + 1, selectedTrayPaths, trimmedTrayPaths, loadingPaths)) }
      }
    }

    root.rootFolders.forEach { topFolder -> appendFolder(topFolder, depth = 0) }
    root.rootFiles.forEach { rootFile ->
      rows.add(toFileRow(rootFile, depth = 0, selectedTrayPaths, trimmedTrayPaths, loadingPaths))
    }

    return rows
  }

  private fun toFileRow(
    file: TreeFileEntity,
    depth: Int,
    selectedTrayPaths: Set<String>,
    trimmedTrayPaths: Set<String>,
    loadingPaths: Set<String>,
  ): VisibleTreeRow.FileRow {
    val blocked = PathBlocklist.isBlocked(file.path)
    return VisibleTreeRow.FileRow(
      file = file,
      fileName = file.path.substringAfterLast('/'),
      depth = depth,
      isBlocked = blocked,
      isSelected = !blocked && file.path in selectedTrayPaths,
      isTrimmed = !blocked && file.path in trimmedTrayPaths,
      isLoading = file.path in loadingPaths,
    )
  }

  fun buildBreadcrumbs(
    directoryPath: String,
    rootLabel: String = "root",
  ): List<BreadcrumbSegment> {
    val cleanRoot = rootLabel.trim().ifBlank { "root" }
    val normalized = directoryPath.replace('\\', '/').trim().trim('/')
    if (normalized.isEmpty()) {
      return listOf(BreadcrumbSegment(label = cleanRoot, folderPath = ""))
    }
    val segments = normalized.split('/').filter { it.isNotEmpty() }
    val result = mutableListOf(BreadcrumbSegment(label = cleanRoot, folderPath = ""))
    val cumulative = StringBuilder()
    for (seg in segments) {
      cumulative.append(seg).append('/')
      result.add(BreadcrumbSegment(label = seg, folderPath = cumulative.toString()))
    }
    return result
  }

  fun parentDirectoryOfFile(filePath: String): String {
    val normalized = filePath.replace('\\', '/').trim().trimStart('/')
    val slashIdx = normalized.lastIndexOf('/')
    return if (slashIdx <= 0) "" else normalized.substring(0, slashIdx + 1)
  }

  fun parentDirectoryOfFolder(folderPath: String): String {
    val normalized = folderPath.replace('\\', '/').trim().trim('/')
    val slashIdx = normalized.lastIndexOf('/')
    return if (slashIdx <= 0) "" else normalized.substring(0, slashIdx + 1)
  }

  fun ancestorFolderPaths(folderPath: String): List<String> {
    val normalized = folderPath.replace('\\', '/').trim().trim('/')
    if (normalized.isEmpty()) return emptyList()
    val cumulative = StringBuilder()
    return normalized.split('/').filter { it.isNotEmpty() }.map { seg ->
      cumulative.append(seg).append('/')
      cumulative.toString()
    }
  }
}
