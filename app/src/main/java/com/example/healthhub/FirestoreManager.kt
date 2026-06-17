package com.example.healthhub

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class FirestoreManager {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private suspend fun <T> retryWithTimeout(
        times: Int = 3,
        timeoutMillis: Long = 5000L,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        for (i in 0 until times) {
            try {
                return withTimeout(timeoutMillis) {
                    block()
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                lastException = java.net.UnknownHostException("Network Timeout: Unable to connect to Firestore.")
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw lastException ?: Exception("Unknown error")
    }

    suspend fun saveHealthData(collectionName: String, data: List<Map<String, Any>>) {
        val user = auth.currentUser ?: return
        if (data.isEmpty()) return

        val collectionRef = db.collection("users").document(user.uid).collection(collectionName)

        // 1. Delta Sync: Fetch the latest document to get the max timestamp
        val snapshot = retryWithTimeout {
            collectionRef
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
        }
        
        val maxTime = snapshot.documents.firstOrNull()?.getString("timestamp")
        
        // 2. Filter incoming data to only push newer records
        val newRecords = if (maxTime != null) {
            data.filter { 
                val itemTime = it["timestamp"] as? String
                itemTime != null && itemTime > maxTime 
            }
        } else {
            data
        }

        if (newRecords.isEmpty()) return
        
        // 3. Firestore batch limit is 500 — chunk into groups of 400 for safety
        for (chunk in newRecords.chunked(400)) {
            retryWithTimeout {
                val batch = db.batch()
                for (item in chunk) {
                    // Build a deterministic ID from the record's key fields so re-sync overwrites, not duplicates
                    val idSource = when {
                        item.containsKey("startTime") && item.containsKey("endTime") ->
                            "${item["startTime"]}_${item["endTime"]}_${item["source"] ?: ""}"
                        item.containsKey("time") ->
                            "${item["time"]}_${item["source"] ?: ""}"
                        else -> item.hashCode().toString()
                    }
                    val docId = idSource.replace("/", "_").replace(".", "_").take(200)
                    val docRef = collectionRef.document(docId)
                    batch.set(docRef, item)
                }
                batch.commit().await()
            }
        }
    }

    /**
     * Delete all documents in a health data collection for the current user.
     * Handles collections with more than 500 docs by looping in chunks.
     */
    suspend fun purgeHealthData(collectionName: String) {
        val user = auth.currentUser ?: return
        val collection = db.collection("users").document(user.uid).collection(collectionName)
        var deleted: Int
        do {
            val snapshot = collection.limit(500).get().await()
            deleted = snapshot.documents.size
            if (deleted > 0) {
                val batch = db.batch()
                for (doc in snapshot.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit().await()
            }
        } while (deleted >= 500)
    }

    suspend fun getHealthData(collectionName: String, timeField: String? = null, afterTimeStr: String? = null): List<Map<String, Any>> {
        val user = auth.currentUser ?: return emptyList()
        var query: com.google.firebase.firestore.Query = db.collection("users").document(user.uid).collection(collectionName)
        if (timeField != null && afterTimeStr != null) {
            query = query.whereGreaterThanOrEqualTo(timeField, afterTimeStr)
        }
        val snapshot = retryWithTimeout { query.get().await() }
        return snapshot.documents.mapNotNull { it.data }
    }

    suspend fun saveUserProfile(profile: Map<String, Any>) {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).set(profile, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    suspend fun getUserProfile(): Map<String, Any>? {
        val user = auth.currentUser ?: return null
        val snapshot = db.collection("users").document(user.uid).get().await()
        return snapshot.data
    }
}
