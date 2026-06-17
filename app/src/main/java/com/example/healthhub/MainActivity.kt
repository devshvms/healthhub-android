package com.example.healthhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.healthhub.theme.HealthHubTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    val healthConnectManager = HealthConnectManager(this)
    val prefs = getSharedPreferences("healthhub_prefs", android.content.Context.MODE_PRIVATE)
    val viewModel = HealthViewModel(healthConnectManager, prefs)

    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    if (auth.currentUser == null) {
        auth.signInAnonymously()
    }

    enableEdgeToEdge()
    setContent {
      HealthHubTheme { 
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { 
            MainNavigation(viewModel) 
        } 
      }
    }
  }
}
