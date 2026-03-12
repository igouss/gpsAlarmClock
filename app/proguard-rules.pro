# Add project specific ProGuard rules here.

# Room entities
-keep class com.elendal.gpsalarmclock.Alarm { *; }
-keep enum com.elendal.gpsalarmclock.AlarmType { *; }

# OSMDroid
-dontwarn org.osmdroid.**
-keep class org.osmdroid.** { *; }

# BroadcastReceivers (referenced by name in AlarmManager PendingIntents)
-keep class com.elendal.gpsalarmclock.AlarmReceiver { *; }
-keep class com.elendal.gpsalarmclock.BootReceiver { *; }
-keep class com.elendal.gpsalarmclock.DismissAlarmReceiver { *; }
-keep class com.elendal.gpsalarmclock.GeofenceBroadcastReceiver { *; }

# AlarmService
-keep class com.elendal.gpsalarmclock.AlarmService { *; }

# Room - keep generated implementations and converters
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.elendal.gpsalarmclock.AlarmTypeConverter { *; }
-keep class com.elendal.gpsalarmclock.**_Impl { *; }
