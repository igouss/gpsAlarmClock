package com.elendal.gpsalarmclock

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (alarmId == -1L) return

        val db = AlarmDatabase.getDatabase(context)
        val scheduler = AlarmScheduler(context)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
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

        return try {
            LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
        } catch (e: Exception) {
            null
        }
    }
}
