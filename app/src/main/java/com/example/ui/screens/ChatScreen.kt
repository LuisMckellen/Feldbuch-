package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ChatMessageEntity
import com.example.ui.FeldbuchUiState
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
  uiState: FeldbuchUiState,
  onSendMessage: (String) -> Unit,
  onOpenTraySheet: () -> Unit,
  onNavigateToSetup: () -> Unit,
  onMessageCopied: (String) -> Unit,
) {
  var draftQuestion by rememberSaveable { mutableStateOf("") }
  val listState = rememberLazyListState()
  val clipboard = LocalClipboardManager.current
  val numberFmt = NumberFormat.getIntegerInstance(Locale.getDefault())

  LaunchedEffect(uiState.messages.size) {
    if (uiState.messages.isNotEmpty()) {
      listState.animateScrollToItem(uiState.messages.size - 1)
    }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    // Context Tray Attachment Bar
    Surface(
      onClick = onOpenTraySheet,
      color = MaterialTheme.colorScheme.surfaceContainer,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      shape = MaterialTheme.shapes.small,
    ) {
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
          Text(
            text =
              if (uiState.trayFiles.isEmpty()) "No files attached · Tap to inspect tray"
              else "Attached: ${uiState.trayFiles.size} files (~${numberFmt.format(uiState.trayEstimatedTokens)} tok)",
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
    }

    if (uiState.settings.endpointUrl.isBlank()) {
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
            text = "Set your /v1 endpoint in Setup to start chatting.",
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
    if (uiState.messages.isEmpty()) {
      Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Icon(
          imageVector = Icons.Outlined.Forum,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = "Brainstorm away from your desk",
          style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Attach relevant Markdown files in Notes, then ask questions against your OpenAI-compatible /v1 endpoint. All turns persist locally.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
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
          ChatBubble(
            message = msg,
            onCopy = {
              clipboard.setText(AnnotatedString(msg.content))
              onMessageCopied("Copied reply to clipboard")
            },
          )
        }
      }
    }

    // Composer Bar
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainerLowest,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = draftQuestion,
          onValueChange = { draftQuestion = it },
          placeholder = { Text("Ask about the attached notes…") },
          maxLines = 4,
          modifier = Modifier.weight(1f).testTag("chat_input"),
        )
        FilledIconButton(
          onClick = {
            val q = draftQuestion
            draftQuestion = ""
            onSendMessage(q)
          },
          enabled = !uiState.isSendingChat && draftQuestion.isNotBlank(),
          colors =
            IconButtonDefaults.filledIconButtonColors(
              containerColor = MaterialTheme.colorScheme.secondary,
              contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("send_chat_button"),
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
private fun ChatBubble(message: ChatMessageEntity, onCopy: () -> Unit) {
  val isUser = message.role == "user"
  val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestampMs))

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
  ) {
    Surface(
      shape = MaterialTheme.shapes.medium,
      color =
        if (isUser) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerLowest,
      contentColor =
        if (isUser) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface,
      border =
        if (isUser) null
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
