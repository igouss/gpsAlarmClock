package com.elendal.gpsalarmclock

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DismissAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1L)
        if (alarmId == -1L) return
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancel(AlarmReceiver.NOTIFICATION_ID_BASE + alarmId.toInt())
    }
}
