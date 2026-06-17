package com.example.healthhub

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.healthhub.ui.dashboard.DashboardScreen
import com.example.healthhub.ui.settings.SettingsScreen

@Composable
fun MainNavigation(viewModel: HealthViewModel) {
  var selectedTab by remember { mutableIntStateOf(0) }

  Scaffold(
    bottomBar = {
      NavigationBar {
        NavigationBarItem(
          icon = { Icon(Icons.Filled.Home, contentDescription = "Dashboard") },
          label = { Text("Dashboard") },
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 }
        )
        NavigationBarItem(
          icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
          label = { Text("Settings") },
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 }
        )
      }
    }
  ) { innerPadding ->
    if (selectedTab == 0) {
        DashboardScreen(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
    } else {
        SettingsScreen(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
    }
  }
}
