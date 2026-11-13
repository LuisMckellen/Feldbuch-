package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.network.GitHubRepositorySummary
import com.example.ui.FeldbuchUiState
import com.example.ui.components.SpinningSyncIcon
import com.example.ui.theme.FeldbuchMotion

@Composable
fun DrawerGitHubAuthBar(
  uiState: FeldbuchUiState,
  showRepoPicker: Boolean,
  onToggleRepoPicker: () -> Unit,
  onOpenGitHubOAuth: () -> Unit,
  onSignOutGitHub: () -> Unit,
  onRefreshRepos: () -> Unit,
  onSelectRepository: (GitHubRepositorySummary) -> Unit,
) {
  if (!uiState.isGitHubOAuthSignedIn) {
    Surface(
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.fillMaxWidth().testTag("drawer_oauth_signed_out_bar"),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Private repos require OAuth2",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
        TextButton(
          onClick = onOpenGitHubOAuth,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("drawer_oauth_signin_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.LockOpen,
            contentDescription = null,
            modifier = Modifier.padding(end = 4.dp),
          )
          Text("Sign in")
        }
      }
    }
    return
  }

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Surface(
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.fillMaxWidth().testTag("drawer_oauth_signed_in_bar"),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Outlined.VerifiedUser,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.secondary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "@${uiState.settings.githubOAuthLogin}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).testTag("drawer_oauth_login_label"),
        )
        val pickerChevronRotation by
          animateFloatAsState(
            targetValue = if (showRepoPicker) 180f else 0f,
            animationSpec = FeldbuchMotion.SnappySpring,
            label = "repoPickerChevron",
          )
        TextButton(
          onClick = {
            if (!showRepoPicker && uiState.accessibleRepos.isEmpty()) {
              onRefreshRepos()
            }
            onToggleRepoPicker()
          },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("drawer_switch_repo_button"),
        ) {
          Text("Repos (${uiState.accessibleRepos.size})")
          Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = if (showRepoPicker) "Hide repositories" else "Switch repository",
            modifier = Modifier.graphicsLayer { rotationZ = pickerChevronRotation },
          )
        }
        IconButton(
          onClick = onSignOutGitHub,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("drawer_github_signout_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.Logout,
            contentDescription = "Sign out of GitHub OAuth",
          )
        }
      }
    }

    AnimatedVisibility(
      visible = showRepoPicker,
      enter = FeldbuchMotion.expandFadeIn(),
      exit = FeldbuchMotion.shrinkFadeOut(),
    ) {
      Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().testTag("drawer_repo_picker"),
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "ACCESSIBLE REPOSITORIES",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(
              onClick = onRefreshRepos,
              enabled = !uiState.isLoadingRepos,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("drawer_refresh_repos_button"),
            ) {
              SpinningSyncIcon(
                isSpinning = uiState.isLoadingRepos,
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "Refresh repositories",
                tint =
                  if (uiState.isLoadingRepos) MaterialTheme.colorScheme.secondary
                  else MaterialTheme.colorScheme.onSurface,
              )
            }
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

          if (uiState.isLoadingRepos && uiState.accessibleRepos.isEmpty()) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.height(18.dp).width(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Loading private & public repos…", style = MaterialTheme.typography.bodySmall)
            }
          } else if (uiState.accessibleRepos.isEmpty()) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                text = "No repositories returned yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              OutlinedButton(
                onClick = onRefreshRepos,
                modifier = Modifier.minimumInteractiveComponentSize(),
              ) {
                Text("Reload repositories")
              }
            }
          } else {
            LazyColumn(
              modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
              contentPadding = PaddingValues(vertical = 4.dp),
            ) {
              items(uiState.accessibleRepos, key = { it.fullName }) { repo ->
                val isActive =
                  repo.owner.equals(uiState.settings.githubOwner, ignoreCase = true) &&
                    repo.name.equals(uiState.settings.githubRepo, ignoreCase = true)
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .animateItem(
                        fadeInSpec = tween(FeldbuchMotion.FAST_ENTER_MS),
                        fadeOutSpec = tween(FeldbuchMotion.FAST_EXIT_MS),
                        placementSpec = FeldbuchMotion.OffsetSpring,
                      )
                      .clickable { onSelectRepository(repo) }
                      .minimumInteractiveComponentSize()
                      .padding(horizontal = 12.dp, vertical = 6.dp)
                      .testTag("drawer_repo_item_${repo.fullName}"),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                    imageVector = if (repo.isPrivate) Icons.Outlined.Lock else Icons.Outlined.Public,
                    contentDescription = if (repo.isPrivate) "Private repository" else "Public repository",
                    tint =
                      if (repo.isPrivate) MaterialTheme.colorScheme.secondary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = repo.fullName,
                      style = MaterialTheme.typography.labelMedium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text =
                        buildString {
                          append(if (repo.isPrivate) "Private" else "Public")
                          append(" · ")
                          append(repo.defaultBranch)
                        },
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  if (isActive) {
                    Icon(
                      imageVector = Icons.Outlined.Check,
                      contentDescription = "Selected repository",
                      tint = MaterialTheme.colorScheme.secondary,
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
