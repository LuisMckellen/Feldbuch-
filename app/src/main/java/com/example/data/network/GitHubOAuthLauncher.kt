package com.example.data.network

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FeldbuchOAuthConfig {
  fun buildConfigClientId(): String =
    BuildConfig.GITHUB_OAUTH_CLIENT_ID
      .trim()
      .takeUnless { it.isEmpty() || it.startsWith("YOUR_") }
      .orEmpty()
}

object OAuthRedirectDispatcher {
  private val _pendingUri = MutableStateFlow<Uri?>(null)
  val pendingUri: StateFlow<Uri?> = _pendingUri.asStateFlow()

  fun dispatch(uri: Uri?) {
    if (GitHubOAuthClient.parseCallbackUri(uri) != null) {
      _pendingUri.value = uri
    }
  }

  fun consume(uri: Uri) {
    if (_pendingUri.value == uri) {
      _pendingUri.value = null
    }
  }
}

object OAuthBrowserLauncher {
  fun launch(context: Context, url: String): Boolean {
    val parsed = Uri.parse(url)
    return try {
      val customTabsIntent = CustomTabsIntent.Builder().setShowTitle(true).build()
      customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      customTabsIntent.launchUrl(context, parsed)
      true
    } catch (_: Exception) {
      try {
        val fallback =
          Intent(Intent.ACTION_VIEW, parsed).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(fallback)
        true
      } catch (_: ActivityNotFoundException) {
        false
      }
    }
  }
}
