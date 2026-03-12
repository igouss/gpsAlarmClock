package com.elendal.gpsalarmclock

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class GpsAlarmClockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // Delete the channel if it was previously created with sound — Android ignores
        // setSound(null) on an existing channel, so we must delete and recreate.
        val existing = manager.getNotificationChannel(AlarmService.CHANNEL_ID)
        if (existing?.sound != null) {
            manager.deleteNotificationChannel(AlarmService.CHANNEL_ID)
        }

        val alarmChannel = NotificationChannel(
            AlarmService.CHANNEL_ID,
            getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.alarm_channel_description)
            setSound(null, null)
            enableVibration(false)
        }

        manager.createNotificationChannel(alarmChannel)
    }
}
