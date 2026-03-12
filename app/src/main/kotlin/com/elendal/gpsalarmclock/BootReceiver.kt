package com.elendal.gpsalarmclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val db = AlarmDatabase.getDatabase(context)
        val scheduler = AlarmScheduler(context)
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val alarms = db.alarmDao().getAllAlarms().first()
                alarms.filter { it.isEnabled }.forEach { alarm ->
                    scheduler.scheduleAlarm(alarm)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling alarms after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
