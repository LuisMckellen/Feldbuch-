package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.data.network.OAuthRedirectDispatcher
import com.example.ui.FeldbuchApp
import com.example.ui.theme.FeldbuchTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    OAuthRedirectDispatcher.dispatch(intent?.data)
    setContent { FeldbuchTheme { FeldbuchApp() } }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    OAuthRedirectDispatcher.dispatch(intent.data)
  }
}
