package com.knowlesy.gitsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.knowlesy.gitsync.theme.GitSyncTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (intent?.getBooleanExtra("trigger_sync", false) == true) {
      Thread {
        android.util.Log.d("GitSyncDebug", "Starting command-line triggered sync...")
        val engine = com.knowlesy.gitsync.git.GitSyncEngine(applicationContext)
        val result = engine.executeSync()
        android.util.Log.d("GitSyncDebug", "Command-line triggered sync completed: $result")
      }.start()
    }

    enableEdgeToEdge()
    setContent {
      GitSyncTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }
}
