package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.BreadcrumbSegment
import com.example.domain.TreeHierarchyBuilder
import com.example.ui.theme.FeldbuchMotion

@Composable
fun RepoBreadcrumbBar(
  currentDirectoryPath: String,
  rootLabel: String,
  onNavigateToDirectory: (String) -> Unit,
  modifier: Modifier = Modifier,
  testTagPrefix: String = "drawer",
) {
  val segments =
    remember(currentDirectoryPath, rootLabel) {
      TreeHierarchyBuilder.buildBreadcrumbs(
        directoryPath = currentDirectoryPath,
        rootLabel = rootLabel,
      )
    }
  val listState = rememberLazyListState()

  LaunchedEffect(segments.size, currentDirectoryPath) {
    if (segments.isNotEmpty()) {
      listState.animateScrollToItem(segments.lastIndex)
    }
  }

  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = modifier.fillMaxWidth().testTag("${testTagPrefix}_breadcrumb_bar"),
  ) {
    LazyRow(
      state = listState,
      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      itemsIndexed(
        items = segments,
        key = { _, seg -> "crumb_${seg.folderPath}" },
      ) { index, segment ->
        val isRoot = index == 0
        val isCurrent = index == segments.lastIndex
        val segmentTag =
          if (isRoot) "${testTagPrefix}_breadcrumb_root"
          else "${testTagPrefix}_breadcrumb_${segment.folderPath.trimEnd('/')}"

        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier.animateItem(
              fadeInSpec = tween(FeldbuchMotion.FAST_ENTER_MS),
              fadeOutSpec = tween(FeldbuchMotion.FAST_EXIT_MS),
              placementSpec = FeldbuchMotion.OffsetSpring,
            ),
        ) {
          if (index > 0) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp),
            )
          }

          BreadcrumbChip(
            segment = segment,
            isRoot = isRoot,
            isCurrent = isCurrent,
            onClick = { onNavigateToDirectory(segment.folderPath) },
            modifier = Modifier.testTag(segmentTag),
          )
        }
      }
    }
  }
}

@Composable
private fun BreadcrumbChip(
  segment: BreadcrumbSegment,
  isRoot: Boolean,
  isCurrent: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val bgColor by
    animateColorAsState(
      targetValue =
        if (isCurrent) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
      animationSpec = tween(FeldbuchMotion.FAST_ENTER_MS),
      label = "crumbBg",
    )
  val fgColor by
    animateColorAsState(
      targetValue =
        if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
      animationSpec = tween(FeldbuchMotion.FAST_ENTER_MS),
      label = "crumbFg",
    )
  val borderColor by
    animateColorAsState(
      targetValue =
        if (isCurrent) MaterialTheme.colorScheme.secondary.copy(alpha = 0.50f)
        else MaterialTheme.colorScheme.outlineVariant,
      animationSpec = tween(FeldbuchMotion.FAST_ENTER_MS),
      label = "crumbBorder",
    )

  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .clip(MaterialTheme.shapes.small)
        .clickable(onClick = onClick)
        .padding(horizontal = 2.dp, vertical = 4.dp),
  ) {
    Surface(
      shape = MaterialTheme.shapes.small,
      color = bgColor,
      border = BorderStroke(0.5.dp, borderColor),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      ) {
        if (isRoot) {
          Icon(
            imageVector = Icons.Outlined.Home,
            contentDescription = "Repository root",
            tint = fgColor,
            modifier = Modifier.size(14.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
          text = segment.label,
          style =
            MaterialTheme.typography.labelSmall.copy(
              fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
            ),
          color = fgColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
