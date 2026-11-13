package com.example

import com.example.data.local.TreeFileEntity
import com.example.domain.TreeHierarchyBuilder
import com.example.domain.VisibleTreeRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeHierarchyBuilderTest {

  private val sampleFiles =
    listOf(
      TreeFileEntity("README.md", sizeBytes = 120),
      TreeFileEntity("docs/architecture.md", sizeBytes = 340),
      TreeFileEntity("docs/api/endpoints.md", sizeBytes = 512),
      TreeFileEntity("docs/api/Auth.md", sizeBytes = 256),
      TreeFileEntity("memory/2026-11-13.md", sizeBytes = 900),
      TreeFileEntity(".env.local", sizeBytes = 64),
      TreeFileEntity("src/main/App.kt", sizeBytes = 1024),
    )

  @Test
  fun `builds nested hierarchy with sorted folders before files and accurate counts`() {
    val root =
      TreeHierarchyBuilder.buildTree(
        files = sampleFiles,
        filterQuery = "",
        selectedTrayPaths = setOf("docs/api/endpoints.md", "memory/2026-11-13.md"),
      )

    assertEquals(listOf("docs", "memory", "src"), root.rootFolders.map { it.name })
    assertEquals(listOf(".env.local", "README.md"), root.rootFiles.map { it.path })

    val docs = root.rootFolders.first { it.name == "docs" }
    assertEquals("docs/", docs.folderPath)
    assertEquals(3, docs.totalFileCount)
    // memory/ is blocked, so only docs/api/endpoints.md counts as selected
    assertEquals(1, docs.selectedFileCount)
    assertEquals(listOf("api"), docs.subfolders.map { it.name })
    assertEquals(listOf("docs/architecture.md"), docs.files.map { it.path })

    val api = docs.subfolders.first()
    assertEquals(listOf("docs/api/Auth.md", "docs/api/endpoints.md"), api.files.map { it.path })
    assertEquals(1, api.selectedFileCount)
  }

  @Test
  fun `flatten expands only toggled folders unless filtering is active`() {
    val root =
      TreeHierarchyBuilder.buildTree(
        files = sampleFiles,
        selectedTrayPaths = setOf("docs/api/endpoints.md"),
      )

    // With 3 top-level folders and empty expanded map, top-level folders start collapsed
    val collapsedRows =
      TreeHierarchyBuilder.flattenVisibleRows(
        root = root,
        expandedFolders = emptyMap(),
        isFiltering = false,
        selectedTrayPaths = setOf("docs/api/endpoints.md"),
        trimmedTrayPaths = setOf("docs/api/endpoints.md"),
        loadingPaths = setOf("README.md"),
      )
    assertEquals(
      listOf("dir:docs/", "dir:memory/", "dir:src/", "file:.env.local", "file:README.md"),
      collapsedRows.map { it.key },
    )

    val envRow = collapsedRows.filterIsInstance<VisibleTreeRow.FileRow>().first { it.fileName == ".env.local" }
    assertTrue(envRow.isBlocked)
    assertFalse(envRow.isSelected)

    val readmeRow = collapsedRows.filterIsInstance<VisibleTreeRow.FileRow>().first { it.fileName == "README.md" }
    assertFalse(readmeRow.isBlocked)
    assertTrue(readmeRow.isLoading)

    // Expanding docs/ and docs/api/ reveals nested depths
    val expandedRows =
      TreeHierarchyBuilder.flattenVisibleRows(
        root = root,
        expandedFolders = mapOf("docs/" to true, "docs/api/" to true),
        isFiltering = false,
        selectedTrayPaths = setOf("docs/api/endpoints.md"),
        trimmedTrayPaths = setOf("docs/api/endpoints.md"),
        loadingPaths = emptySet(),
      )
    val endpointsRow =
      expandedRows.filterIsInstance<VisibleTreeRow.FileRow>().first { it.fileName == "endpoints.md" }
    assertEquals(2, endpointsRow.depth)
    assertTrue(endpointsRow.isSelected)
    assertTrue(endpointsRow.isTrimmed)
  }

  @Test
  fun `case-insensitive filter trims tree and auto-expands matching ancestor folders`() {
    val filteredRoot = TreeHierarchyBuilder.buildTree(files = sampleFiles, filterQuery = "AUTH")
    assertEquals(1, filteredRoot.totalFileCount)
    assertEquals(listOf("docs"), filteredRoot.rootFolders.map { it.name })
    assertTrue(filteredRoot.rootFiles.isEmpty())

    val rows =
      TreeHierarchyBuilder.flattenVisibleRows(
        root = filteredRoot,
        expandedFolders = emptyMap(),
        isFiltering = true,
        selectedTrayPaths = emptySet(),
        trimmedTrayPaths = emptySet(),
        loadingPaths = emptySet(),
      )
    assertEquals(
      listOf("dir:docs/", "dir:docs/api/", "file:docs/api/Auth.md"),
      rows.map { it.key },
    )
    assertEquals(listOf(0, 1, 2), rows.map { it.depth })
  }

  @Test
  fun `filterFilesByName matches filename only unless query includes slash or matchFullPath`() {
    val files =
      sampleFiles +
        listOf(
          TreeFileEntity("docs/api/docs_overview.md", sizeBytes = 300),
          TreeFileEntity("app/src/main/Utils.kt", sizeBytes = 400),
        )

    // Searching "docs" by name matches only docs_overview.md, not every file under docs/
    val byName = com.example.domain.TreeBrowserHelper.filterFilesByName(files, "docs")
    assertEquals(listOf("docs/api/docs_overview.md"), byName.map { it.path })

    // Multi-token filename match (case-insensitive)
    val multiToken = com.example.domain.TreeBrowserHelper.filterFilesByName(files, "app kt")
    assertEquals(listOf("src/main/App.kt"), multiToken.map { it.path })

    // Query containing '/' searches full path
    val slashQuery = com.example.domain.TreeBrowserHelper.filterFilesByName(files, "api/auth")
    assertEquals(listOf("docs/api/Auth.md"), slashQuery.map { it.path })

    // matchFullPath = true searches directory segments too
    val fullPath = com.example.domain.TreeBrowserHelper.filterFilesByName(files, "docs", matchFullPath = true)
    assertEquals(
      listOf("docs/architecture.md", "docs/api/endpoints.md", "docs/api/Auth.md", "docs/api/docs_overview.md"),
      fullPath.map { it.path },
    )

    // buildTree with matchNameOnly = true prunes folders whose filenames don't match
    val nameTree = TreeHierarchyBuilder.buildTree(files = files, filterQuery = "app", matchNameOnly = true)
    assertEquals(1, nameTree.totalFileCount)
    assertEquals(listOf("src"), nameTree.rootFolders.map { it.name })
  }

  @Test
  fun `builds directory breadcrumb trail and computes parent and ancestor paths`() {
    val rootCrumbs = TreeHierarchyBuilder.buildBreadcrumbs("", rootLabel = "feldbuch")
    assertEquals(1, rootCrumbs.size)
    assertEquals("feldbuch", rootCrumbs[0].label)
    assertEquals("", rootCrumbs[0].folderPath)

    val deepCrumbs = TreeHierarchyBuilder.buildBreadcrumbs("/docs/api/v2/", rootLabel = "feldbuch")
    assertEquals(listOf("feldbuch", "docs", "api", "v2"), deepCrumbs.map { it.label })
    assertEquals(
      listOf("", "docs/", "docs/api/", "docs/api/v2/"),
      deepCrumbs.map { it.folderPath },
    )

    assertEquals("docs/api/", TreeHierarchyBuilder.parentDirectoryOfFile("docs/api/endpoints.md"))
    assertEquals("", TreeHierarchyBuilder.parentDirectoryOfFile("README.md"))
    assertEquals("docs/", TreeHierarchyBuilder.parentDirectoryOfFolder("docs/api/"))
    assertEquals("", TreeHierarchyBuilder.parentDirectoryOfFolder("docs/"))
    assertEquals(
      listOf("docs/", "docs/api/", "docs/api/v2/"),
      TreeHierarchyBuilder.ancestorFolderPaths("docs/api/v2/"),
    )
  }
}
