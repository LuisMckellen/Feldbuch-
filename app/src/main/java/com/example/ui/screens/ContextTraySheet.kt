package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.TreeFileEntity
import com.example.domain.ContextCapLimiter
import com.example.ui.components.EditorialCardHeader
import com.example.ui.theme.FeldbuchMotion
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextTraySheet(
  trayFiles: List<TreeFileEntity>,
  trimmedPaths: Set<String>,
  totalChars: Int,
  totalOriginalChars: Int,
  estimatedTokens: Int,
  perFileCap: Int,
  totalCap: Int,
  onRemovePath: (String) -> Unit,
  onClearTray: () -> Unit,
  onPreviewFile: (TreeFileEntity) -> Unit,
  onDismiss: () -> Unit,
) {
  val numberFmt = NumberFormat.getIntegerInstance(Locale.getDefault())
  val cappedResult = ContextCapLimiter.apply(trayFiles, perFileCap = perFileCap, totalCap = totalCap)
  val itemsByPath = cappedResult.items.associateBy { it.file.path }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 20.dp)
          .navigationBarsPadding()
          .padding(bottom = 20.dp)
    ) {
      val capacityFraction by
        animateFloatAsState(
          targetValue = (totalChars.toFloat() / totalCap.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
          animationSpec = FeldbuchMotion.SnappySpring,
          label = "sheetTrayCapacity",
        )

      EditorialCardHeader(
        icon = Icons.Outlined.Inventory2,
        title = "Context Tray",
        trailing = {
          if (trayFiles.isNotEmpty()) {
            TextButton(
              onClick = onClearTray,
              modifier = Modifier.testTag("clear_tray_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.DeleteSweep,
                contentDescription = "Clear context tray",
                modifier = Modifier.padding(end = 4.dp),
              )
              Text("Clear")
            }
          }
        },
      )
      Spacer(modifier = Modifier.height(6.dp))
      val capInfo =
        if (totalOriginalChars > totalChars) {
          " (trimmed from ${numberFmt.format(totalOriginalChars)})"
        } else {
          ""
        }
      Text(
        text =
          "${trayFiles.size} files · ${numberFmt.format(totalChars)} chars$capInfo · ~${numberFmt.format(estimatedTokens)} tokens",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
      )
      Text(
        text = "Caps: ${numberFmt.format(perFileCap)}/file · ${numberFmt.format(totalCap)} total",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(5.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      ) {
        Box(
          modifier =
            Modifier.fillMaxHeight()
              .fillMaxWidth(capacityFraction)
              .clip(MaterialTheme.shapes.extraSmall)
              .background(
                if (trimmedPaths.isNotEmpty()) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.secondary
              )
        )
      }

      Spacer(modifier = Modifier.height(12.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Spacer(modifier = Modifier.height(12.dp))

      if (trayFiles.isEmpty()) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Icon(
            imageVector = Icons.Outlined.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "No files attached yet",
            style = MaterialTheme.typography.titleMedium,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Tick any unlocked file in the repo tree to fetch its raw text and attach it to your chat prompt.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          items(trayFiles, key = { it.path }) { file ->
            val cappedItem = itemsByPath[file.path]
            val sentChars = cappedItem?.sentChars ?: (file.cachedText?.length ?: 0)
            val isTrimmed = file.path in trimmedPaths || (cappedItem?.wasTrimmed == true)
            Surface(
              onClick = { onPreviewFile(file) },
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.surfaceContainer,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              modifier =
                Modifier.animateItem(
                  fadeInSpec = tween(FeldbuchMotion.FAST_ENTER_MS),
                  fadeOutSpec = tween(FeldbuchMotion.FAST_EXIT_MS),
                  placementSpec = FeldbuchMotion.OffsetSpring,
                ),
            ) {
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = file.path,
                      style = MaterialTheme.typography.labelMedium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isTrimmed) {
                      Spacer(modifier = Modifier.width(6.dp))
                      Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.testTag("tray_trimmed_badge"),
                      ) {
                        Text(
                          text = "trimmed",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSecondaryContainer,
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                      }
                    }
                  }
                  Text(
                    text =
                      "${numberFmt.format(sentChars)} sent chars · ~${numberFmt.format(sentChars / 4)} tok",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(
                  onClick = { onRemovePath(file.path) },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove ${file.path} from tray",
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
