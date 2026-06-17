package com.example.healthhub

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

class HealthConnectManager(private val context: Context) {
    val isAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class)
    )

    suspend fun hasAllPermissions(): Boolean {
        if (!isAvailable) return false
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    suspend fun readAggregatedSteps(startTime: Instant, endTime: Instant): List<Map<String, Any>> {
        val response = healthConnectClient.aggregateGroupByDuration(
            androidx.health.connect.client.request.AggregateGroupByDurationRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                timeRangeSlicer = java.time.Duration.ofMinutes(15)
            )
        )
        return response.mapNotNull { bucket ->
            val count = bucket.result[StepsRecord.COUNT_TOTAL] ?: return@mapNotNull null
            mapOf(
                "count" to count,
                "startTime" to bucket.startTime.toString(),
                "endTime" to bucket.endTime.toString(),
                "source" to "health_connect_aggregate"
            )
        }
    }

    suspend fun readSteps(startTime: Instant, endTime: Instant): List<StepsRecord> {
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )
        return response.records
    }

    suspend fun readHeartRate(startTime: Instant, endTime: Instant): List<HeartRateRecord> {
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )
        return response.records
    }

    suspend fun readSleep(startTime: Instant, endTime: Instant): List<SleepSessionRecord> {
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )
        return response.records
    }

    suspend fun readExercise(startTime: Instant, endTime: Instant): List<ExerciseSessionRecord> {
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )
        return response.records
    }

    suspend fun readWeight(startTime: Instant, endTime: Instant): List<WeightRecord> {
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )
        return response.records
    }
}
