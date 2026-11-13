package com.example.data.twa

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.trusted.TrustedWebActivityIntentBuilder
import com.example.domain.UrlValidationResult
import com.example.domain.UrlValidator

enum class StellwerkLaunchMode {
  TWA,
  CUSTOM_TAB,
  BROWSER_VIEW,
  FAILED,
}

data class StellwerkLaunchResult(
  val mode: StellwerkLaunchMode,
  val message: String,
)

interface StellwerkLauncherBackend {
  fun resolveCustomTabsPackage(context: Context): String?

  fun launchTrustedWebActivityOrCustomTab(
    context: Context,
    uri: Uri,
    customTabsPackage: String,
  ): StellwerkLaunchResult

  fun launchCustomTab(
    context: Context,
    uri: Uri,
    customTabsPackage: String?,
  ): StellwerkLaunchResult

  fun launchSystemBrowser(context: Context, uri: Uri): StellwerkLaunchResult
}

object AndroidStellwerkLauncherBackend : StellwerkLauncherBackend {
  override fun resolveCustomTabsPackage(context: Context): String? =
    CustomTabsClient.getPackageName(context, emptyList())

  override fun launchTrustedWebActivityOrCustomTab(
    context: Context,
    uri: Uri,
    customTabsPackage: String,
  ): StellwerkLaunchResult {
    val connection =
      object : CustomTabsServiceConnection() {
        override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
          client.warmup(0L)
          val session = client.newSession(null)
          if (session != null) {
            try {
              val twaIntent = TrustedWebActivityIntentBuilder(uri).build(session)
              if (context !is Activity) {
                twaIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              }
              twaIntent.launchTrustedWebActivity(context)
              return
            } catch (_: Exception) {
              // Fall through to Custom Tab fallback below
            }
          }
          launchCustomTab(context, uri, customTabsPackage)
        }

        override fun onServiceDisconnected(name: ComponentName) = Unit
      }

    val appCtx = context.applicationContext
    val bound =
      try {
        CustomTabsClient.bindCustomTabsService(appCtx, customTabsPackage, connection)
      } catch (_: Exception) {
        false
      }

    return if (bound) {
      StellwerkLaunchResult(
        mode = StellwerkLaunchMode.TWA,
        message = "Opening Stellwerk in Trusted Web Activity…",
      )
    } else {
      launchCustomTab(context, uri, customTabsPackage)
    }
  }

  override fun launchCustomTab(
    context: Context,
    uri: Uri,
    customTabsPackage: String?,
  ): StellwerkLaunchResult {
    return try {
      val customTabsIntent =
        TrustedWebActivityIntentBuilder(uri).buildCustomTabsIntent().also { cti ->
          if (!customTabsPackage.isNullOrBlank()) {
            cti.intent.setPackage(customTabsPackage)
          }
          if (context !is Activity) {
            cti.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        }
      customTabsIntent.launchUrl(context, uri)
      StellwerkLaunchResult(
        mode = StellwerkLaunchMode.CUSTOM_TAB,
        message = "Opened Stellwerk in Custom Tab.",
      )
    } catch (_: ActivityNotFoundException) {
      launchSystemBrowser(context, uri)
    } catch (_: Exception) {
      launchSystemBrowser(context, uri)
    }
  }

  override fun launchSystemBrowser(context: Context, uri: Uri): StellwerkLaunchResult {
    return try {
      val intent =
        Intent(Intent.ACTION_VIEW, uri).apply {
          if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        }
      context.startActivity(intent)
      StellwerkLaunchResult(
        mode = StellwerkLaunchMode.BROWSER_VIEW,
        message = "Opened Stellwerk in browser.",
      )
    } catch (_: ActivityNotFoundException) {
      StellwerkLaunchResult(
        mode = StellwerkLaunchMode.FAILED,
        message = "No compatible browser or Custom Tabs provider found.",
      )
    }
  }

  fun buildFallbackCustomTabIntent(uri: Uri): CustomTabsIntent =
    CustomTabsIntent.Builder().setShowTitle(true).build().also {
      it.intent.data = uri
    }
}

object StellwerkLauncher {

  fun launch(
    context: Context,
    rawUrl: String,
    backend: StellwerkLauncherBackend = AndroidStellwerkLauncherBackend,
  ): StellwerkLaunchResult {
    val cleanUrl = rawUrl.trim()
    when (val check = UrlValidator.validateHttps(cleanUrl, "Stellwerk URL", allowBlank = false)) {
      is UrlValidationResult.Invalid ->
        return StellwerkLaunchResult(
          mode = StellwerkLaunchMode.FAILED,
          message = check.message,
        )
      is UrlValidationResult.Valid -> Unit
    }

    val uri = Uri.parse(cleanUrl)
    val customTabsPkg = backend.resolveCustomTabsPackage(context)
    return if (!customTabsPkg.isNullOrBlank()) {
      backend.launchTrustedWebActivityOrCustomTab(context, uri, customTabsPkg)
    } else {
      backend.launchCustomTab(context, uri, null)
    }
  }
}
