package com.example

import com.example.data.local.ChatMessageEntity
import com.example.data.local.FeldbuchSettings
import com.example.data.local.SessionNoteEntity
import com.example.data.local.TreeFileEntity
import com.example.domain.ExportSerializer
import com.example.domain.NoteDraftPrompt
import com.example.domain.TreeBrowserHelper
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FeldbuchDomainTest {

  private val sampleFiles =
    listOf(
      TreeFileEntity(path = "README.md", sizeBytes = 100, cachedText = "Hello World!"),
      TreeFileEntity(
        path = "CLAUDE/jobs/pending/2026-11-13-feldbuch.md",
        sizeBytes = 200,
        cachedText = "12345678",
      ),
      TreeFileEntity(
        path = "CLAUDE/IDEAS.md",
        sizeBytes = 150,
        cachedText = "Idea one",
      ),
    )

  @Test
  fun `filename filter is case-insensitive substring on path only`() {
    val filtered = TreeBrowserHelper.filterPathsByFilename(sampleFiles, "FELDBUCH")
    assertEquals(listOf("CLAUDE/jobs/pending/2026-11-13-feldbuch.md"), filtered.map { it.path })

    val grouped = TreeBrowserHelper.groupByFolder(sampleFiles)
    assertEquals(listOf("(root)", "CLAUDE/", "CLAUDE/jobs/pending/"), grouped.map { it.folderPath })
  }

  @Test
  fun `tray summary computes exact file count, chars, and chars-over-4 tokens`() {
    val two = sampleFiles.take(2)
    val summary = TreeBrowserHelper.computeTraySummary(two)
    assertEquals(2, summary.fileCount)
    assertEquals(20, summary.totalChars)
    assertEquals(5, summary.estimatedTokens)

    val one = sampleFiles.take(1)
    val dropped = TreeBrowserHelper.computeTraySummary(one)
    assertEquals(1, dropped.fileCount)
    assertEquals(12, dropped.totalChars)
    assertEquals(3, dropped.estimatedTokens)
  }

  @Test
  fun `banner and quiet badge logic obey hub status and 15-00 clock threshold`() {
    val before1500 =
      Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 14)
        set(Calendar.MINUTE, 59)
      }
    val at1500 =
      Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 15)
        set(Calendar.MINUTE, 0)
      }
    assertFalse(TreeBrowserHelper.isCurrentClockPast1500(before1500))
    assertTrue(TreeBrowserHelper.isCurrentClockPast1500(at1500))

    // Hub closed, before 15:00, messages waiting -> quiet badge only, no banner
    assertFalse(
      TreeBrowserHelper.shouldShowHomeBanner(
        unsentCount = 3,
        hubReachable = false,
        isPast1500 = TreeBrowserHelper.isCurrentClockPast1500(before1500),
      )
    )
    assertTrue(
      TreeBrowserHelper.shouldShowQuietBadge(
        unsentCount = 3,
        hubReachable = false,
        isPast1500 = TreeBrowserHelper.isCurrentClockPast1500(before1500),
      )
    )

    // Hub closed, after 15:00, messages waiting -> banner shows
    assertTrue(
      TreeBrowserHelper.shouldShowHomeBanner(
        unsentCount = 3,
        hubReachable = false,
        isPast1500 = TreeBrowserHelper.isCurrentClockPast1500(at1500),
      )
    )
    assertFalse(
      TreeBrowserHelper.shouldShowQuietBadge(
        unsentCount = 3,
        hubReachable = false,
        isPast1500 = TreeBrowserHelper.isCurrentClockPast1500(at1500),
      )
    )

    // Hub open, any time, messages waiting -> banner shows
    assertTrue(
      TreeBrowserHelper.shouldShowHomeBanner(
        unsentCount = 1,
        hubReachable = true,
        isPast1500 = false,
      )
    )

    // No messages waiting -> neither banner nor badge
    assertFalse(
      TreeBrowserHelper.shouldShowHomeBanner(
        unsentCount = 0,
        hubReachable = true,
        isPast1500 = true,
      )
    )
    assertFalse(
      TreeBrowserHelper.shouldShowQuietBadge(
        unsentCount = 0,
        hubReachable = false,
        isPast1500 = false,
      )
    )
  }

  @Test
  fun `copy formatter appends message count and time range`() {
    val startMs = 1763020800000L
    val endMs = startMs + 15 * 60 * 1000L
    val copied =
      NoteDraftPrompt.formatForCopy(
        draftText =
          "What I was thinking about: GitHub tree\nWhat I concluded: nothing settled\nStill open / what to weigh: caching",
        messageCount = 4,
        startMs = startMs,
        endMs = endMs,
      )
    assertTrue(copied.contains("What I concluded: nothing settled"))
    assertTrue(copied.lines().last().startsWith("4 messages, "))
  }

  @Test
  fun `export serializer never leaks PAT or API key`() {
    val json =
      ExportSerializer.buildExportJson(
        settings =
          FeldbuchSettings(
            githubOwner = "octocat",
            githubRepo = "notes",
            githubBranch = "main",
            githubPat = "ghp_SUPER_SECRET_TOKEN",
            apiKey = "sk-SECRET_API_KEY",
          ),
        treeFileCount = 3,
        messages =
          listOf(
            ChatMessageEntity(
              id = 1,
              role = "user",
              content = "hello",
              timestampMs = 1763020800000L,
            )
          ),
        sessionNotes = emptyList(),
      )
    assertFalse(json.contains("ghp_SUPER_SECRET_TOKEN"))
    assertFalse(json.contains("sk-SECRET_API_KEY"))
    assertTrue(json.contains("octocat"))
  }

  @Test
  fun `markdown and text note exporters reflect edited draft and omit secrets`() {
    val startMs = 1763020800000L
    val endMs = startMs + 10 * 60 * 1000L
    val draft =
      SessionNoteEntity(
        id = 1L,
        draftText = "What I was thinking about: initial",
        messageCount = 3,
        startTimestampMs = startMs,
        endTimestampMs = endMs,
        createdAtMs = endMs,
        isCurrentDraft = true,
      )
    val settings =
      FeldbuchSettings(
        githubOwner = "octocat",
        githubRepo = "feldbuch-notes",
        githubBranch = "main",
        githubPat = "ghp_SECRET",
        apiKey = "sk-SECRET",
      )
    val edited =
      "What I was thinking about: export options\nWhat I concluded: support md and txt\nStill open / what to weigh: none"

    val markdown =
      ExportSerializer.buildSessionNotesMarkdown(
        settings = settings,
        editableDraftText = edited,
        currentDraft = draft,
        allSessionNotes = listOf(draft),
        fallbackMessageCount = 3,
        fallbackStartMs = startMs,
        fallbackEndMs = endMs,
      )
    assertTrue(markdown.startsWith("# Feldbuch Session Notes"))
    assertTrue(markdown.contains("`octocat/feldbuch-notes`"))
    assertTrue(markdown.contains("What I concluded: support md and txt"))
    assertTrue(markdown.contains("3 messages, "))
    assertFalse(markdown.contains("ghp_SECRET"))
    assertFalse(markdown.contains("sk-SECRET"))

    val plainText =
      ExportSerializer.buildSessionNotesText(
        settings = settings,
        editableDraftText = edited,
        currentDraft = draft,
        allSessionNotes = listOf(draft),
        fallbackMessageCount = 3,
        fallbackStartMs = startMs,
        fallbackEndMs = endMs,
      )
    assertTrue(plainText.startsWith("FELDBUCH SESSION NOTES"))
    assertTrue(plainText.contains("What I concluded: support md and txt"))
    assertTrue(plainText.contains("3 messages, "))
    assertFalse(plainText.contains("ghp_SECRET"))
    assertFalse(plainText.contains("sk-SECRET"))
  }
}
