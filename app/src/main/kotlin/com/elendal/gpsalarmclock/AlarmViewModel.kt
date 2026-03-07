package com.elendal.gpsalarmclock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AlarmRepository
    private val scheduler: AlarmScheduler
    val alarms: LiveData<List<Alarm>>

    init {
        val db = AlarmDatabase.getDatabase(application)
        repository = AlarmRepository(db.alarmDao())
        scheduler = AlarmScheduler(application)
        alarms = repository.allAlarms.asLiveData()
    }

    fun addAlarm(alarm: Alarm) = viewModelScope.launch {
        val id = repository.insert(alarm)
        val savedAlarm = alarm.copy(id = id)
        scheduler.scheduleAlarm(savedAlarm)
    }

    fun updateAlarm(alarm: Alarm) = viewModelScope.launch {
        repository.update(alarm)
        scheduler.cancelAlarm(alarm)
        if (alarm.isEnabled) {
            scheduler.scheduleAlarm(alarm)
        }
    }

    fun deleteAlarm(alarm: Alarm) = viewModelScope.launch {
        scheduler.cancelAlarm(alarm)
        repository.delete(alarm)
    }

    fun toggleAlarm(alarm: Alarm) = viewModelScope.launch {
        val updated = alarm.copy(isEnabled = !alarm.isEnabled)
        repository.update(updated)
        if (updated.isEnabled) {
            scheduler.scheduleAlarm(updated)
        } else {
            scheduler.cancelAlarm(updated)
        }
    }
}
