package com.elendal.gpsalarmclock

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_EXACT_ALARM_ASKED = "exact_alarm_asked"
    }

    private lateinit var viewModel: AlarmViewModel
    private lateinit var adapter: AlarmsAdapter

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* permission result, proceed regardless */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestNotificationPermissionIfNeeded()
        checkExactAlarmPermission()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        viewModel = ViewModelProvider(this)[AlarmViewModel::class.java]

        adapter = AlarmsAdapter(
            context = this,
            onItemClick = { alarm -> showEditBottomSheet(alarm) },
            onItemLongClick = { alarm ->
                showDeleteConfirmation(alarm)
                true
            },
            onToggle = { alarm -> viewModel.toggleAlarm(alarm) }
        )

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_alarms)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        viewModel.alarms.observe(this) { alarms ->
            adapter.submitList(alarms)
        }

        val fab = findViewById<FloatingActionButton>(R.id.fab_add_alarm)
        fab.setOnClickListener { showAddBottomSheet() }
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (alarmManager.canScheduleExactAlarms()) return

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_EXACT_ALARM_ASKED, false)) return
        prefs.edit().putBoolean(KEY_EXACT_ALARM_ASKED, true).apply()

        AlertDialog.Builder(this)
            .setTitle("Alarm permission required")
            .setMessage("GPS Alarm Clock needs permission to schedule exact alarms. Tap OK to open settings.")
            .setPositiveButton("OK") { _, _ ->
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun showAddBottomSheet() {
        val sheet = AddEditAlarmBottomSheet.newInstance()
        sheet.onSave = { alarm -> viewModel.addAlarm(alarm) }
        sheet.show(supportFragmentManager, "add_alarm")
    }

    private fun showEditBottomSheet(alarm: Alarm) {
        val sheet = AddEditAlarmBottomSheet.newInstance(alarm)
        sheet.onSave = { updatedAlarm -> viewModel.updateAlarm(updatedAlarm) }
        sheet.show(supportFragmentManager, "edit_alarm")
    }

    private fun showDeleteConfirmation(alarm: Alarm) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_alarm_title)
            .setMessage(getString(R.string.delete_alarm_message, alarm.label.ifEmpty { getString(R.string.no_label) }))
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteAlarm(alarm) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
