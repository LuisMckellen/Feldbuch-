package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.OutboxEntity
import com.example.data.local.OutboxKind
import com.example.data.local.OutboxState
import com.example.data.network.EuleCitation
import com.example.data.network.EuleCitationCodec
import com.example.ui.components.PulsingStatusBeacon
import com.example.ui.theme.FeldbuchMotion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OutboxItemCard(
  item: OutboxEntity,
  onRetry: () -> Unit,
  onSelectCitation: (EuleCitation) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val kind = OutboxKind.fromWire(item.kind)
  val state = OutboxState.fromWire(item.state)
  val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.createdAt))
  val citations = remember(item.citationsJson) { EuleCitationCodec.decode(item.citationsJson) }

  val (targetBg, targetFg) =
    when (state) {
      OutboxState.QUEUED ->
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
      OutboxState.SENDING ->
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
      OutboxState.SENT ->
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
      OutboxState.FAILED ->
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }

  val badgeBg by
    animateColorAsState(
      targetValue = targetBg,
      animationSpec = tween(FeldbuchMotion.FAST_ENTER_MS),
      label = "outboxBadgeBg",
    )
  val badgeFg by
    animateColorAsState(
      targetValue = targetFg,
      animationSpec = tween(FeldbuchMotion.FAST_ENTER_MS),
      label = "outboxBadgeFg",
    )

  Surface(
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.surfaceContainerLowest,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier =
      modifier
        .fillMaxWidth()
        .animateContentSize(animationSpec = FeldbuchMotion.ExpandSizeSpring)
        .testTag("outbox_item_${item.id}"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "${kind.label.uppercase(Locale.ROOT)} · $timeStr",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
          shape = MaterialTheme.shapes.extraSmall,
          color = badgeBg,
          modifier = Modifier.testTag("outbox_state_${item.state}"),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          ) {
            if (state == OutboxState.SENDING) {
              PulsingStatusBeacon(
                color = MaterialTheme.colorScheme.secondary,
                isPulsing = true,
                coreSize = 6.dp,
              )
              Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
              text =
                if (item.attempts > 0 && state != OutboxState.SENT) {
                  "${state.label} (${item.attempts}/8)"
                } else {
                  state.label
                },
              style = MaterialTheme.typography.labelSmall,
              color = badgeFg,
            )
          }
        }
      }

      Text(
        text = item.body,
        style = MaterialTheme.typography.bodyMedium,
      )

      if (!item.answerText.isNullOrBlank()) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth().testTag("outbox_answer_${item.id}"),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = "EULE ANSWER",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.secondary,
            )
            Text(
              text = item.answerText,
              style = MaterialTheme.typography.bodySmall,
            )
            if (citations.isNotEmpty()) {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                citations.forEachIndexed { idx, citation ->
                  AssistChip(
                    onClick = { onSelectCitation(citation) },
                    label = {
                      Text(
                        text = citation.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    },
                    leadingIcon = {
                      Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                      )
                    },
                    modifier = Modifier.testTag("outbox_citation_${item.id}_$idx"),
                  )
                }
              }
            }
          }
        }
      }

      if (!item.lastError.isNullOrBlank()) {
        Text(
          text = item.lastError,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }

      if (state == OutboxState.FAILED) {
        OutlinedButton(
          onClick = onRetry,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("outbox_retry_button_${item.id}"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = null,
            modifier = Modifier.padding(end = 6.dp),
          )
          Text("Send again")
        }
      }
    }
  }
}

@Composable
fun EuleInlineErrorCard(
  reason: String,
  detail: String?,
  isRetrying: Boolean,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Outlined.ErrorOutline,
        contentDescription = "Eule request failed",
        tint = MaterialTheme.colorScheme.error,
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Eule error ($reason)",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onErrorContainer,
        )
        if (!detail.isNullOrBlank()) {
          Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.92f),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      OutlinedButton(
        onClick = onRetry,
        enabled = !isRetrying,
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(
          imageVector = Icons.Outlined.Refresh,
          contentDescription = null,
          modifier = Modifier.padding(end = 4.dp),
        )
        Text("Retry")
      }
    }
  }
}

@Composable
fun EuleCitationDialog(
  citation: EuleCitation,
  onCopiedPath: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val clipboard = LocalClipboardManager.current
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = citation.label.ifBlank { "Cited Path" },
        style = MaterialTheme.typography.titleMedium,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "SOURCE PATH",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = citation.path,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(10.dp).testTag("citation_dialog_path"),
          )
        }
        if (citation.snippet.isNotBlank()) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          Text(
            text = citation.snippet,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Close") }
    },
    dismissButton = {
      TextButton(
        onClick = {
          clipboard.setText(AnnotatedString(citation.path))
          onCopiedPath("Copied cited path to clipboard.")
        }
      ) {
        Text("Copy path")
      }
    },
  )
}
