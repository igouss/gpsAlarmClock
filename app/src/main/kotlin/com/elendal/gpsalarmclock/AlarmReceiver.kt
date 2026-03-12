package com.elendal.gpsalarmclock

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_IS_SNOOZE = "extra_is_snooze"
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (alarmId == -1L) return

        val isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)

        val db = AlarmDatabase.getDatabase(context)
        val scheduler = AlarmScheduler(context)
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (isSnooze) {
                    // Snooze: skip geofence check, just ring
                    val alarm = db.alarmDao().getById(alarmId) ?: return@launch
                    context.startForegroundService(Intent(context, AlarmService::class.java).apply {
                        putExtra(AlarmService.EXTRA_ALARM_ID, alarm.id)
                        putExtra(AlarmService.EXTRA_ALARM_LABEL, alarm.label)
                    })
                    return@launch
                }

                val alarm = db.alarmDao().getById(alarmId) ?: return@launch

                if (alarm.isGeoFenced) {
                    val location = getLastKnownLocation(context)
                    if (location != null) {
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            location.latitude, location.longitude,
                            alarm.geoFenceLat, alarm.geoFenceLng,
                            results
                        )
                        val distance = results[0]
                        if (distance > alarm.geoFenceRadius) {
                            // Outside fence — reschedule to keep checking, don't ring
                            if (alarm.isEnabled) {
                                scheduler.scheduleAlarm(alarm)
                            }
                            return@launch
                        }
                    }
                    // If location is null, fall through and ring (fail open)
                }

                // Ring the alarm via AlarmService
                context.startForegroundService(Intent(context, AlarmService::class.java).apply {
                    putExtra(AlarmService.EXTRA_ALARM_ID, alarm.id)
                    putExtra(AlarmService.EXTRA_ALARM_LABEL, alarm.label)
                })

                // Reschedule for next occurrence
                if (alarm.isEnabled) {
                    scheduler.scheduleAlarm(alarm)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing alarm $alarmId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocation(context: Context): Location? {
        val hasPermission = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return null

        val client = LocationServices.getFusedLocationProviderClient(context)
        return try {
            withTimeoutOrNull(5000L) {
                val cts = CancellationTokenSource()
                try {
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
                } finally {
                    cts.cancel()
                }
            } ?: client.lastLocation.await()
        } catch (e: Exception) {
            null
        }
    }
}
