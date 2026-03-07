package com.elendal.gpsalarmclock

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioAttributes
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val CHANNEL_ID = "alarm_channel"
        const val NOTIFICATION_ID_BASE = 1000
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

                // Ring the alarm
                createNotificationChannel(context)
                fireNotification(context, alarm)

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

    private fun fireNotification(context: Context, alarm: Alarm) {
        val dismissIntent = Intent(context, DismissAlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarm.id)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.alarm_notification_title))
            .setContentText(context.getString(R.string.alarm_notification_text))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_delete, context.getString(R.string.dismiss), dismissPendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID_BASE + alarm.id.toInt(), notification)
    }

    private fun createNotificationChannel(context: Context) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.alarm_channel_description)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
            setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, audioAttributes)
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
