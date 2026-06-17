package com.example.healthhub.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.healthhub.HealthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: HealthViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val lastSyncedTime by viewModel.lastSyncedTime.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    var hasPermissions by remember { mutableStateOf(false) }
    var isAvailable by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
        onResult = { granted ->
            hasPermissions = granted.containsAll(viewModel.healthConnectManager.permissions)
        }
    )

    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken("422390726294-fna9r1vsqm2ietdhrvqns0mu799mb0j5.apps.googleusercontent.com")
        .requestEmail()
        .build()

    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val loginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account?.idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(credential).addOnCompleteListener {
                    viewModel.fetchUserProfile()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        isAvailable = viewModel.healthConnectManager.isAvailable
        if (isAvailable) {
            hasPermissions = viewModel.healthConnectManager.hasAllPermissions()
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text("HealthHub Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        // Profile Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (isLoggedIn && userProfile != null) {
                    val name = userProfile?.get("name") as? String ?: "No Name"
                    val email = userProfile?.get("email") as? String ?: ""
                    val age = userProfile?.get("age") as? String ?: "Not set"
                    val weight = userProfile?.get("latestWeightKg") as? Number

                    Text("Welcome, $name", style = MaterialTheme.typography.titleMedium)
                    Text(email, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Age: $age", style = MaterialTheme.typography.bodyMedium)
                        Text("Weight: ${weight?.toFloat() ?: "--"} kg", style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = {
                        FirebaseAuth.getInstance().signOut()
                        googleSignInClient.signOut()
                        FirebaseAuth.getInstance().signInAnonymously() // Revert to anonymous
                    }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Sign Out")
                    }
                } else {
                    Text("You are using HealthHub anonymously.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { loginLauncher.launch(googleSignInClient.signInIntent) }) {
                        Text("Sign in with Google")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(32.dp))

        // Syncing Section
        if (!isAvailable) {
            Text(
                "⚠️ Health Connect is not available on this device.\n" +
                "If you are using an emulator (Android 13 or lower), please install the 'Health Connect' app from the Play Store.",
                color = MaterialTheme.colorScheme.error
            )
        } else if (!hasPermissions) {
            Text("Health Connect permissions are required to sync your Samsung Health, Fitbit, and Google Fit data.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                permissionLauncher.launch(viewModel.healthConnectManager.permissions)
            }) {
                Text("Grant Health Connect Permissions")
            }
        } else {
            Text("Permissions Granted! ✓", color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Status: $syncState")
            Spacer(modifier = Modifier.height(8.dp))
            Text("Last Synced: $lastSyncedTime", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(onClick = { viewModel.syncData() }) {
                Text("Sync Health Data to Firestore")
            }
        }
    }
}
