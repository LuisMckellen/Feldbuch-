package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Outbox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.OutboxState
import com.example.data.network.EuleCitation
import com.example.data.network.EuleSearchHit
import com.example.ui.FeldbuchUiState
import com.example.ui.components.EditorialCardHeader
import com.example.ui.components.PulsingStatusBeacon
import com.example.ui.components.SpinningSyncIcon
import com.example.ui.components.TypingDotsIndicator
import com.example.ui.theme.FeldbuchMotion
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EuleScreen(
  uiState: FeldbuchUiState,
  onSearchQueryChange: (String) -> Unit,
  onSearchSubmit: () -> Unit,
  onAskInputChange: (String) -> Unit,
  onAskSubmit: () -> Unit,
  onRetryAsk: () -> Unit,
  onSelectCitation: (EuleCitation) -> Unit,
  onRefreshEuleHealth: () -> Unit,
  onFlushOutboxNow: () -> Unit,
  onRetryOutboxItem: (String) -> Unit,
  onClearSentOutbox: () -> Unit,
  onNavigateToSetup: () -> Unit,
) {
  val hasQueuedOrFailed =
    uiState.outboxItems.any {
      it.state == OutboxState.QUEUED.wireValue || it.state == OutboxState.SENDING.wireValue
    }
  val hasSent = uiState.outboxItems.any { it.state == OutboxState.SENT.wireValue }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Eule Connection Status Strip
    item {
      EuleStatusStrip(
        uiState = uiState,
        onRefreshHealth = onRefreshEuleHealth,
        onNavigateToSetup = onNavigateToSetup,
      )
    }

    // Search Section
    item {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 1.dp,
        modifier =
          Modifier.fillMaxWidth()
            .animateContentSize(animationSpec = FeldbuchMotion.ExpandSizeSpring)
            .testTag("eule_search_card"),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          EditorialCardHeader(
            icon = Icons.Outlined.Search,
            title = "Search Memory",
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            OutlinedTextField(
              value = uiState.euleSearchQuery,
              onValueChange = onSearchQueryChange,
              placeholder = { Text("Search indexed notes & memory…") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
              keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
              textStyle = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.weight(1f).testTag("eule_search_input"),
            )
            Button(
              onClick = onSearchSubmit,
              enabled = !uiState.isSearchingEule && uiState.euleSearchQuery.isNotBlank(),
              modifier = Modifier.minimumInteractiveComponentSize().testTag("eule_search_button"),
            ) {
              if (uiState.isSearchingEule) {
                CircularProgressIndicator(
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(16.dp),
                )
              } else {
                Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search Eule")
              }
            }
          }

          uiState.euleSearchErrorReason?.let { reason ->
            EuleInlineErrorCard(
              reason = reason,
              detail = uiState.euleSearchErrorDetail,
              isRetrying = uiState.isSearchingEule,
              onRetry = onSearchSubmit,
              modifier = Modifier.testTag("eule_search_error_card"),
            )
          }

          if (uiState.hasSearchedEule && uiState.euleSearchHits.isEmpty() && uiState.euleSearchErrorReason == null) {
            Text(
              text = "No matching memory documents found.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.testTag("eule_search_empty"),
            )
          }

          uiState.euleSearchHits.forEachIndexed { idx, hit ->
            EuleSearchHitCard(hit = hit, index = idx)
          }
        }
      }
    }

    // Ask Section
    item {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 1.dp,
        modifier =
          Modifier.fillMaxWidth()
            .animateContentSize(animationSpec = FeldbuchMotion.ExpandSizeSpring)
            .testTag("eule_ask_card"),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          EditorialCardHeader(
            icon = Icons.Outlined.AutoAwesome,
            title = "Ask Eule",
            trailing = {
              if (uiState.isAskingEule) {
                TypingDotsIndicator()
              }
            },
          )

          OutlinedTextField(
            value = uiState.euleAskInput,
            onValueChange = onAskInputChange,
            placeholder = { Text("Ask a question over your memory corpus…") },
            minLines = 2,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth().testTag("eule_ask_input"),
          )

          Button(
            onClick = onAskSubmit,
            enabled = !uiState.isAskingEule && uiState.euleAskInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("eule_ask_button"),
          ) {
            if (uiState.isAskingEule) {
              CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Asking Eule…")
            } else {
              Text("Ask")
            }
          }

          uiState.euleAskErrorReason?.let { reason ->
            EuleInlineErrorCard(
              reason = reason,
              detail = uiState.euleAskErrorDetail,
              isRetrying = uiState.isAskingEule,
              onRetry = onRetryAsk,
              modifier = Modifier.testTag("eule_ask_error_card"),
            )
          }

          AnimatedVisibility(
            visible = uiState.euleAskResponse != null,
            enter = FeldbuchMotion.expandFadeIn(),
            exit = FeldbuchMotion.shrinkFadeOut(),
          ) {
            uiState.euleAskResponse?.let { answerResp ->
            Surface(
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.surfaceContainer,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              modifier = Modifier.fillMaxWidth().testTag("eule_ask_answer_card"),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                uiState.euleAskLastQuestion?.let { q ->
                  Text(
                    text = q,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                  )
                }
                Text(
                  text = answerResp.answer,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.testTag("eule_ask_answer_text"),
                )
                if (answerResp.citations.isNotEmpty()) {
                  Text(
                    text = "CITATIONS (TAP TO VIEW PATH)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    answerResp.citations.forEachIndexed { idx, citation ->
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
                        modifier = Modifier.testTag("eule_citation_chip_$idx"),
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
    }

    item {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }

    // Outbox Section
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Outlined.Outbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "EULE OUTBOX (${uiState.outboxItems.size})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          if (hasQueuedOrFailed) {
            OutlinedButton(
              onClick = onFlushOutboxNow,
              enabled = !uiState.isFlushingOutbox,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("eule_sync_outbox_button"),
            ) {
              if (uiState.isFlushingOutbox) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
              } else {
                Icon(
                  imageVector = Icons.Outlined.Sync,
                  contentDescription = null,
                  modifier = Modifier.padding(end = 4.dp),
                )
              }
              Text("Sync now")
            }
          }
          if (hasSent) {
            TextButton(
              onClick = onClearSentOutbox,
              modifier = Modifier.testTag("eule_clear_sent_button"),
            ) {
              Text("Clear sent")
            }
          }
        }
      }
    }

    if (uiState.outboxItems.isEmpty()) {
      item {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Outbox is empty. Notes, ideas, and questions captured in the Note tab sync here automatically over Tailscale HTTPS.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp),
          )
        }
      }
    } else {
      items(uiState.outboxItems, key = { it.id }) { item ->
        OutboxItemCard(
          item = item,
          onRetry = { onRetryOutboxItem(item.id) },
          onSelectCitation = onSelectCitation,
          modifier =
            Modifier.animateItem(
              fadeInSpec = tween(FeldbuchMotion.FAST_ENTER_MS),
              fadeOutSpec = tween(FeldbuchMotion.FAST_EXIT_MS),
              placementSpec = FeldbuchMotion.OffsetSpring,
            ),
        )
      }
    }
  }
}

@Composable
private fun EuleStatusStrip(
  uiState: FeldbuchUiState,
  onRefreshHealth: () -> Unit,
  onNavigateToSetup: () -> Unit,
) {
  val dotColor =
    when {
      uiState.euleReachable -> MaterialTheme.colorScheme.tertiary
      uiState.isEuleConfigured -> MaterialTheme.colorScheme.error
      else -> MaterialTheme.colorScheme.outline
    }
  val title =
    when {
      uiState.settings.euleUrl.isNotBlank() && uiState.euleReachable -> "Eule connected"
      uiState.settings.euleDemoMode && uiState.settings.euleUrl.isBlank() -> "Eule Demo mode active"
      uiState.settings.euleUrl.isNotBlank() -> "Eule unreachable"
      else -> "Eule not configured"
    }
  val subtitle =
    when {
      uiState.settings.euleUrl.isNotBlank() -> uiState.settings.euleUrl
      uiState.settings.euleDemoMode -> "Using local fake corpus for offline demos"
      else -> "Set Tailscale HTTPS URL or enable Demo mode in Setup"
    }

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth().testTag("eule_status_strip"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f),
      ) {
        PulsingStatusBeacon(
          color = dotColor,
          isPulsing = uiState.euleReachable || uiState.isCheckingEule,
          modifier = Modifier.testTag("eule_status_dot"),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text(text = title, style = MaterialTheme.typography.labelLarge)
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      if (uiState.isEuleConfigured) {
        IconButton(
          onClick = onRefreshHealth,
          enabled = !uiState.isCheckingEule,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("eule_refresh_health_button"),
        ) {
          SpinningSyncIcon(
            isSpinning = uiState.isCheckingEule,
            imageVector = Icons.Outlined.Refresh,
            contentDescription = "Check Eule health",
            tint =
              if (uiState.isCheckingEule) MaterialTheme.colorScheme.secondary
              else MaterialTheme.colorScheme.onSurface,
          )
        }
      } else {
        TextButton(
          onClick = onNavigateToSetup,
          modifier = Modifier.testTag("eule_go_setup_button"),
        ) {
          Text("Setup")
        }
      }
    }
  }
}

@Composable
private fun EuleSearchHitCard(hit: EuleSearchHit, index: Int) {
  Surface(
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth().testTag("eule_search_hit_$index"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = hit.title,
          style = MaterialTheme.typography.labelLarge,
          modifier = Modifier.weight(1f),
        )
        hit.score?.let { sc ->
          Text(
            text = String.format(Locale.US, "%.2f", sc),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
      }
      Text(
        text = hit.path,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (hit.snippet.isNotBlank()) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = hit.snippet,
          style = MaterialTheme.typography.bodySmall,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
