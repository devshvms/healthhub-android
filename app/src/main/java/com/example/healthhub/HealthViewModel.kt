package com.example.healthhub

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HealthViewModel(
    val healthConnectManager: HealthConnectManager,
    private val sharedPreferences: SharedPreferences,
    private val firestoreManager: FirestoreManager = FirestoreManager()
) : ViewModel() {

    private val _syncState = MutableStateFlow("Idle")
    val syncState: StateFlow<String> = _syncState

    private val _lastSyncedTime = MutableStateFlow(sharedPreferences.getString("last_synced", "Never"))
    val lastSyncedTime: StateFlow<String?> = _lastSyncedTime

    private val _stepsData = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val stepsData: StateFlow<List<Map<String, Any>>> = _stepsData

    private val _heartRateData = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val heartRateData: StateFlow<List<Map<String, Any>>> = _heartRateData

    private val _sleepData = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val sleepData: StateFlow<List<Map<String, Any>>> = _sleepData

    private val _stressData = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val stressData: StateFlow<List<Map<String, Any>>> = _stressData

    private val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    private val _isLoggedIn = MutableStateFlow(auth.currentUser?.isAnonymous == false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val _userProfile = MutableStateFlow<Map<String, Any>?>(null)
    val userProfile: StateFlow<Map<String, Any>?> = _userProfile

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _toastMessage = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val toastMessage: kotlinx.coroutines.flow.SharedFlow<String> = _toastMessage

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _isLoggedIn.value = user != null && !user.isAnonymous
            if (user != null) {
                fetchUserProfile()
            }
        }
        fetchDashboardData()
    }

    fun fetchUserProfile() {
        viewModelScope.launch {
            val profile = firestoreManager.getUserProfile()
            val user = auth.currentUser
            if (profile != null) {
                _userProfile.value = profile
            } else if (user != null && !user.isAnonymous) {
                val newProfile = mapOf("name" to (user.displayName ?: "Unknown"), "email" to (user.email ?: ""))
                firestoreManager.saveUserProfile(newProfile)
                _userProfile.value = newProfile
            }
        }
    }

    fun saveAge(age: String) {
        viewModelScope.launch {
            firestoreManager.saveUserProfile(mapOf("age" to age))
            fetchUserProfile()
        }
    }

    fun fetchDashboardData() {
        viewModelScope.launch {
            try {
                val fortyEightHoursAgo = java.time.Instant.now().minus(48, java.time.temporal.ChronoUnit.HOURS).toString()
                _stepsData.value = firestoreManager.getHealthData("steps", "startTime", fortyEightHoursAgo)
                _heartRateData.value = firestoreManager.getHealthData("heart_rate", "time", fortyEightHoursAgo)
                _sleepData.value = firestoreManager.getHealthData("sleep", "endTime", fortyEightHoursAgo)
                _stressData.value = firestoreManager.getHealthData("stress", "time", fortyEightHoursAgo)
            } catch (e: Exception) {
                e.printStackTrace()
                _toastMessage.emit("Down-sync failed: ${e.localizedMessage ?: "Network error"}")
            }
        }
    }

    fun syncCurrentDayData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                if (!healthConnectManager.hasAllPermissions()) {
                    _isRefreshing.value = false
                    return@launch
                }
                val endTime = Instant.now()
                val startTime = endTime.minus(24, ChronoUnit.HOURS)

                // Automatic Schema Migration: Purge collections if they contain old schema data (missing 'timestamp')
                val stepsSnapshot = firestoreManager.getHealthData("steps", null, null).firstOrNull()
                if (stepsSnapshot != null && !stepsSnapshot.containsKey("timestamp")) {
                    firestoreManager.purgeHealthData("steps")
                    firestoreManager.purgeHealthData("heart_rate")
                    firestoreManager.purgeHealthData("sleep")
                    firestoreManager.purgeHealthData("weight")
                    firestoreManager.purgeHealthData("stress")
                }

                // Sync Steps
                val stepDocs = healthConnectManager.readAggregatedSteps(startTime, endTime)
                val standardizedSteps = stepDocs.map { it + ("timestamp" to it["endTime"]!!) }
                if (standardizedSteps.isNotEmpty()) {
                    firestoreManager.saveHealthData("steps", standardizedSteps)
                }

                // Sync Heart Rate
                val hr = healthConnectManager.readHeartRate(startTime, endTime)
                val hrDocs = hr.flatMap { record ->
                    record.samples.map { sample ->
                        mapOf(
                            "beatsPerMinute" to sample.beatsPerMinute,
                            "time" to sample.time.toString(),
                            "timestamp" to sample.time.toString(),
                            "source" to record.metadata.dataOrigin.packageName
                        )
                    }
                }
                if (hrDocs.isNotEmpty()) {
                    firestoreManager.saveHealthData("heart_rate", hrDocs)
                }

                // Sync Sleep
                val sleep = healthConnectManager.readSleep(startTime, endTime)
                val sleepDocs = sleep.map { record ->
                    val durationHours = java.time.Duration.between(record.startTime, record.endTime).toMinutes() / 60.0
                    mapOf(
                        "durationHours" to durationHours,
                        "startTime" to record.startTime.toString(),
                        "endTime" to record.endTime.toString(),
                        "timestamp" to record.endTime.toString(),
                        "source" to record.metadata.dataOrigin.packageName
                    )
                }
                if (sleepDocs.isNotEmpty()) {
                    firestoreManager.saveHealthData("sleep", sleepDocs)
                }
                
                // Sync Weight
                val weight = healthConnectManager.readWeight(startTime, endTime)
                val weightDocs = weight.map { record ->
                    mapOf(
                        "weightKg" to record.weight.inKilograms,
                        "time" to record.time.toString(),
                        "timestamp" to record.time.toString(),
                        "source" to record.metadata.dataOrigin.packageName
                    )
                }
                if (weightDocs.isNotEmpty()) {
                    firestoreManager.saveHealthData("weight", weightDocs)
                    firestoreManager.saveUserProfile(mapOf("latestWeightKg" to weightDocs.last()["weightKg"]!!))
                    fetchUserProfile()
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                _toastMessage.emit("Sync failed: ${e.localizedMessage ?: "Network error"}")
            } finally {
                fetchDashboardData() // Reload dashboard (sync down) regardless of up-sync success
                _isRefreshing.value = false
            }
        }
    }

    fun syncData() {
        viewModelScope.launch {
            try {
                if (!healthConnectManager.hasAllPermissions()) {
                    _syncState.value = "Permissions missing."
                    return@launch
                }
                _syncState.value = "Syncing..."
                val endTime = Instant.now()
                val startTime = endTime.minus(30, ChronoUnit.DAYS)
                // Automatic Schema Migration: Purge collections if they contain old schema data (missing 'timestamp')
                val stepsSnapshot = firestoreManager.getHealthData("steps", null, null).firstOrNull()
                if (stepsSnapshot != null && !stepsSnapshot.containsKey("timestamp")) {
                    firestoreManager.purgeHealthData("steps")
                    firestoreManager.purgeHealthData("heart_rate")
                    firestoreManager.purgeHealthData("sleep")
                    firestoreManager.purgeHealthData("weight")
                    firestoreManager.purgeHealthData("stress")
                }

                // Sync Steps
                val stepDocs = healthConnectManager.readAggregatedSteps(startTime, endTime)
                val standardizedSteps = stepDocs.map { it + ("timestamp" to it["endTime"]!!) }
                if (standardizedSteps.isNotEmpty()) {
                    firestoreManager.saveHealthData("steps", standardizedSteps)
                }

                // Sync Heart Rate
                val hr = healthConnectManager.readHeartRate(startTime, endTime)
                val hrDocs = hr.flatMap { record ->
                    record.samples.map { sample ->
                        mapOf(
                            "beatsPerMinute" to sample.beatsPerMinute,
                            "time" to sample.time.toString(),
                            "timestamp" to sample.time.toString(),
                            "source" to record.metadata.dataOrigin.packageName
                        )
                    }
                }
                if (hrDocs.isNotEmpty()) {
                    firestoreManager.saveHealthData("heart_rate", hrDocs)
                }

                // Sync Sleep
                val sleep = healthConnectManager.readSleep(startTime, endTime)
                val sleepDocs = sleep.map { record ->
                    val durationHours = java.time.Duration.between(record.startTime, record.endTime).toMinutes() / 60.0
                    mapOf(
                        "durationHours" to durationHours,
                        "startTime" to record.startTime.toString(),
                        "endTime" to record.endTime.toString(),
                        "timestamp" to record.endTime.toString(),
                        "source" to record.metadata.dataOrigin.packageName
                    )
                }
                if (sleepDocs.isNotEmpty()) {
                    firestoreManager.saveHealthData("sleep", sleepDocs)
                }

                // Sync Weight
                val weight = healthConnectManager.readWeight(startTime, endTime)
                val weightDocs = weight.map { record ->
                    mapOf(
                        "weightKg" to record.weight.inKilograms,
                        "time" to record.time.toString(),
                        "timestamp" to record.time.toString(),
                        "source" to record.metadata.dataOrigin.packageName
                    )
                }
                if (weightDocs.isNotEmpty()) {
                    firestoreManager.saveHealthData("weight", weightDocs)
                    firestoreManager.saveUserProfile(mapOf("latestWeightKg" to weightDocs.last()["weightKg"]!!))
                    fetchUserProfile() // Refresh profile with latest weight
                }

                // Mock Stress Data (Health Connect doesn't have a direct Stress API)
                val stressDocs = listOf(
                    mapOf("level" to 30, "time" to Instant.now().minus(2, ChronoUnit.DAYS).toString(), "timestamp" to Instant.now().minus(2, ChronoUnit.DAYS).toString()),
                    mapOf("level" to 60, "time" to Instant.now().minus(1, ChronoUnit.DAYS).toString(), "timestamp" to Instant.now().minus(1, ChronoUnit.DAYS).toString()),
                    mapOf("level" to 45, "time" to Instant.now().toString(), "timestamp" to Instant.now().toString())
                )
                firestoreManager.saveHealthData("stress", stressDocs)

                // Update last synced time
                val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss").withZone(ZoneId.systemDefault())
                val timeString = formatter.format(Instant.now())
                sharedPreferences.edit().putString("last_synced", timeString).apply()
                _lastSyncedTime.value = timeString

                _syncState.value = "Sync complete. Uploaded ${stepDocs.size} step records."

            } catch (e: Exception) {
                _syncState.value = "Error: ${e.message}"
                _toastMessage.emit("Full Sync failed: ${e.localizedMessage ?: "Network error"}")
            } finally {
                // Refresh dashboard (sync down) regardless of up-sync success
                fetchDashboardData()
            }
        }
    }
}
