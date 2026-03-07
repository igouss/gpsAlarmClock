package com.elendal.gpsalarmclock

import androidx.room.TypeConverter

class AlarmTypeConverter {
    @TypeConverter
    fun fromAlarmType(value: AlarmType): String = value.name

    @TypeConverter
    fun toAlarmType(value: String): AlarmType = AlarmType.valueOf(value)
}
