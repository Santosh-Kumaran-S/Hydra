package com.hydra.sensors

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.hydra.algorithm.HeartRateDataSource
import com.hydra.algorithm.Reading
import java.time.Duration
import java.time.Instant

enum class HcState { UNAVAILABLE, UPDATE_REQUIRED, NEEDS_PERMISSION, READY }

/**
 * Reads the newest heart-rate sample that ANOTHER app (e.g. the watch's companion app) has written to Health Connect.
 * These are synced readings with their own timestamps, not a live stream. Nothing is simulated.
 */
class HealthConnectHeartRateSource(private val ctx: Context) : HeartRateDataSource {
    companion object { val PERMS = setOf(HealthPermission.getReadPermission(HeartRateRecord::class)) }

    private fun client() = HealthConnectClient.getOrCreate(ctx)

    suspend fun state(): HcState = when (HealthConnectClient.getSdkStatus(ctx)) {
        HealthConnectClient.SDK_AVAILABLE ->
            if (client().permissionController.getGrantedPermissions().containsAll(PERMS)) HcState.READY else HcState.NEEDS_PERMISSION
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcState.UPDATE_REQUIRED
        else -> HcState.UNAVAILABLE
    }

    /** The system permission dialog must be launched from UI; this only reports whether access is granted. */
    override suspend fun requestAccess(): Boolean = state() == HcState.READY

    override suspend fun latest(): Reading<Int>? {
        val now = Instant.now()
        val resp = client().readRecords(
            ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(now.minus(Duration.ofHours(3)), now)))
        var best: Reading<Int>? = null
        for (rec in resp.records) {
            val src = rec.metadata.dataOrigin.packageName
            for (s in rec.samples) {
                if (s.time.isAfter(now)) continue
                if (best == null || s.time.isAfter(best.measuredAt)) best = Reading(s.beatsPerMinute.toInt(), s.time, src)
            }
        }
        return best
    }

    override fun disconnect() { /* nothing held open; access is revoked in Health Connect settings */ }
}
