package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ChatMessageEntity
import com.example.domain.UrlValidationResult
import com.example.domain.UrlValidator
import com.example.ui.FeldbuchUiState
import com.example.ui.components.ArchivalEmptyStateCard
import com.example.ui.components.TypingDotsIndicator
import com.example.ui.theme.FeldbuchMotion
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
  uiState: FeldbuchUiState,
  onComposerTextChange: (String) -> Unit,
  onSendMessage: () -> Unit,
  onRetryMessage: (ChatMessageEntity) -> Unit,
  onOpenTraySheet: () -> Unit,
  onNavigateToSetup: () -> Unit,
  onMessageCopied: (String) -> Unit,
) {
  val listState = rememberLazyListState()
  val clipboard = LocalClipboardManager.current
  val numberFmt = NumberFormat.getIntegerInstance(Locale.getDefault())
  val endpointValidation = UrlValidator.validateHttps(uiState.settings.endpointUrl, "Endpoint URL")

  LaunchedEffect(uiState.messages.size) {
    if (uiState.messages.isNotEmpty()) {
      listState.animateScrollToItem(uiState.messages.size - 1)
    }
  }

  val hasTrayFiles = uiState.trayFiles.isNotEmpty()
  val trayBarBg by
    animateColorAsState(
      targetValue =
        if (hasTrayFiles) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surfaceContainer,
      animationSpec = tween(FeldbuchMotion.FAST_ENTER_MS),
      label = "chatTrayBg",
    )
  val trayBarBorder by
    animateColorAsState(
      targetValue =
        if (hasTrayFiles) MaterialTheme.colorScheme.secondary.copy(alpha = 0.42f)
        else MaterialTheme.colorScheme.outlineVariant,
      animationSpec = tween(FeldbuchMotion.FAST_ENTER_MS),
      label = "chatTrayBorder",
    )
  val totalCap = uiState.settings.totalCharCap.coerceAtLeast(1)
  val trayCapacityFraction by
    animateFloatAsState(
      targetValue = (uiState.trayCharCount.toFloat() / totalCap.toFloat()).coerceIn(0f, 1f),
      animationSpec = FeldbuchMotion.SnappySpring,
      label = "chatTrayCapacity",
    )

  Column(modifier = Modifier.fillMaxSize()) {
    // Context Tray Attachment Bar
    Surface(
      onClick = onOpenTraySheet,
      color = trayBarBg,
      border = BorderStroke(1.dp, trayBarBorder),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      shape = MaterialTheme.shapes.small,
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
              imageVector = Icons.Outlined.Inventory2,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            val trimmedSuffix = if (uiState.trimmedTrayPaths.isNotEmpty()) " · trimmed" else ""
            Text(
              text =
                if (uiState.trayFiles.isEmpty()) "No files attached · Tap to inspect tray"
                else "Attached: ${uiState.trayFiles.size} files (~${numberFmt.format(uiState.trayEstimatedTokens)} tok)$trimmedSuffix",
              style = MaterialTheme.typography.labelMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Text(
            text = "Tray",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
        if (hasTrayFiles) {
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
          ) {
            Box(
              modifier =
                Modifier.fillMaxHeight()
                  .fillMaxWidth(trayCapacityFraction)
                  .background(MaterialTheme.colorScheme.secondary)
            )
          }
        }
      }
    }

    if (endpointValidation is UrlValidationResult.Invalid) {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text =
              if (uiState.settings.endpointUrl.isBlank()) "Set your https:// /v1 endpoint in Setup to start chatting."
              else endpointValidation.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
          )
          TextButton(onClick = onNavigateToSetup) {
            Icon(
              imageVector = Icons.Outlined.Settings,
              contentDescription = null,
              modifier = Modifier.padding(end = 4.dp),
            )
            Text("Setup")
          }
        }
      }
    }

    // Message Timeline
    if (uiState.messages.isEmpty() && !uiState.isSendingChat) {
      Box(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        ArchivalEmptyStateCard(
          icon = Icons.Outlined.Forum,
          eyebrow = "FIELD NOTES CHAT",
          title = "Brainstorm away from your desk",
          body =
            "Attach relevant Markdown files in Tree, then ask questions against your HTTPS /v1 endpoint. Sensitive files are automatically blocked and context is capped safely.",
        )
      }
    } else {
      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        items(uiState.messages, key = { it.id }) { msg ->
          Column(
            modifier =
              Modifier.fillMaxWidth()
                .animateItem(
                  fadeInSpec = tween(FeldbuchMotion.FAST_ENTER_MS),
                  fadeOutSpec = tween(FeldbuchMotion.FAST_EXIT_MS),
                  placementSpec = FeldbuchMotion.OffsetSpring,
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            ChatBubble(
              message = msg,
              onCopy = {
                clipboard.setText(AnnotatedString(msg.content))
                onMessageCopied("Copied reply to clipboard")
              },
            )
            if (msg.errorReason != null) {
              InlineMessageErrorCard(
                reason = msg.errorReason,
                detail = msg.errorDetail,
                isRetrying = uiState.isSendingChat,
                onRetry = { onRetryMessage(msg) },
              )
            }
          }
        }

        if (uiState.isSendingChat) {
          item(key = "chat_typing_indicator") {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.Start,
            ) {
              Surface(
                shape =
                  RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 16.dp,
                  ),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 1.dp,
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Text(
                    text = "MODEL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  TypingDotsIndicator()
                }
              }
            }
          }
        }
      }
    }

    // Composer Bar
    val canSend = !uiState.isSendingChat && uiState.chatComposerText.isNotBlank()
    val sendScale by
      animateFloatAsState(
        targetValue = if (canSend) 1f else 0.94f,
        animationSpec = FeldbuchMotion.BouncySpring,
        label = "sendButtonScale",
      )

    Surface(
      color = MaterialTheme.colorScheme.surfaceContainerLowest,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      tonalElevation = 2.dp,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = uiState.chatComposerText,
          onValueChange = onComposerTextChange,
          placeholder = { Text("Ask about the attached notes…") },
          maxLines = 4,
          modifier = Modifier.weight(1f).testTag("chat_input"),
        )
        FilledIconButton(
          onClick = onSendMessage,
          enabled = canSend,
          colors =
            IconButtonDefaults.filledIconButtonColors(
              containerColor = MaterialTheme.colorScheme.secondary,
              contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .graphicsLayer {
                scaleX = sendScale
                scaleY = sendScale
              }
              .testTag("send_chat_button"),
        ) {
          if (uiState.isSendingChat) {
            CircularProgressIndicator(
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onSecondary,
              modifier = Modifier.height(20.dp).width(20.dp),
            )
          } else {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.Send,
              contentDescription = "Send question",
            )
          }
        }
      }
    }
  }
}

@Composable
private fun InlineMessageErrorCard(
  reason: String,
  detail: String?,
  isRetrying: Boolean,
  onRetry: () -> Unit,
) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    Surface(
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.errorContainer,
      contentColor = MaterialTheme.colorScheme.onErrorContainer,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
      modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(0.88f).testTag("inline_error_card"),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Outlined.ErrorOutline,
          contentDescription = "Send failed",
          tint = MaterialTheme.colorScheme.error,
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Send failed ($reason)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
          )
          if (!detail.isNullOrBlank()) {
            Text(
              text = detail,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        OutlinedButton(
          onClick = onRetry,
          enabled = !isRetrying,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("retry_chat_button"),
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
}

@Composable
private fun ChatBubble(message: ChatMessageEntity, onCopy: () -> Unit) {
  val isUser = message.role == "user"
  val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestampMs))

  val bubbleShape =
    RoundedCornerShape(
      topStart = 16.dp,
      topEnd = 16.dp,
      bottomStart = if (isUser) 16.dp else 4.dp,
      bottomEnd = if (isUser) 4.dp else 16.dp,
    )

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
  ) {
    Surface(
      shape = bubbleShape,
      color =
        if (isUser) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerLowest,
      contentColor =
        if (isUser) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface,
      border =
        if (isUser) null
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      tonalElevation = if (isUser) 0.dp else 1.dp,
      modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(0.88f),
    ) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (isUser) "YOU · $timeStr" else "MODEL · $timeStr",
            style = MaterialTheme.typography.labelSmall,
            color =
              if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (!isUser) {
            IconButton(
              onClick = onCopy,
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Copy reply",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        Text(
          text = message.content,
          style = MaterialTheme.typography.bodyMedium,
        )

        if (isUser && message.contextPaths.isNotBlank()) {
          val fileCount = message.contextPaths.split(',').count { it.isNotBlank() }
          Text(
            text = "Context: $fileCount ${if (fileCount == 1) "file" else "files"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
          )
        }
      }
    }
  }
}
