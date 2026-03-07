package com.elendal.gpsalarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val db = AlarmDatabase.getDatabase(context)
        val scheduler = AlarmScheduler(context)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarms = db.alarmDao().getAllAlarms().first()
                alarms.filter { it.isEnabled }.forEach { alarm ->
                    scheduler.scheduleAlarm(alarm)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
