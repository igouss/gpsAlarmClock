package com.elendal.gpsalarmclock

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String = "",
    val hour: Int,
    val minute: Int,
    val alarmType: AlarmType,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val isEnabled: Boolean = true,
    val isGeoFenced: Boolean = false,
    val geoFenceLat: Double = 0.0,
    val geoFenceLng: Double = 0.0,
    val geoFenceRadius: Float = 500f
)
