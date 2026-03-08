package com.elendal.gpsalarmclock

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddEditAlarmBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_ALARM_ID = "alarm_id"
        private const val ARG_HOUR = "hour"
        private const val ARG_MINUTE = "minute"
        private const val ARG_LABEL = "label"
        private const val ARG_TYPE = "alarm_type"
        private const val ARG_START_DATE = "start_date"
        private const val ARG_END_DATE = "end_date"
        private const val ARG_IS_GEOFENCED = "is_geofenced"
        private const val ARG_GEO_LAT = "geo_lat"
        private const val ARG_GEO_LNG = "geo_lng"
        private const val ARG_GEO_RADIUS = "geo_radius"

        fun newInstance(alarm: Alarm? = null): AddEditAlarmBottomSheet {
            return AddEditAlarmBottomSheet().apply {
                arguments = Bundle().apply {
                    if (alarm != null) {
                        putLong(ARG_ALARM_ID, alarm.id)
                        putInt(ARG_HOUR, alarm.hour)
                        putInt(ARG_MINUTE, alarm.minute)
                        putString(ARG_LABEL, alarm.label)
                        putString(ARG_TYPE, alarm.alarmType.name)
                        alarm.startDate?.let { putLong(ARG_START_DATE, it) }
                        alarm.endDate?.let { putLong(ARG_END_DATE, it) }
                        putBoolean(ARG_IS_GEOFENCED, alarm.isGeoFenced)
                        putDouble(ARG_GEO_LAT, alarm.geoFenceLat)
                        putDouble(ARG_GEO_LNG, alarm.geoFenceLng)
                        putFloat(ARG_GEO_RADIUS, alarm.geoFenceRadius)
                    }
                }
            }
        }
    }

    var onSave: ((Alarm) -> Unit)? = null

    private var editingAlarmId: Long = 0L
    private var selectedStartDate: Long? = null
    private var selectedEndDate: Long? = null

    // Geofence state
    private var selectedLat: Double = 51.5
    private var selectedLng: Double = -0.1
    private var selectedRadius: Float = 500f
    private var hasSelectedLocation: Boolean = false

    private lateinit var etLabel: TextInputEditText
    private lateinit var timePicker: TimePicker
    private lateinit var chipGroup: ChipGroup
    private lateinit var chipFullWeek: Chip
    private lateinit var chipWorkday: Chip
    private lateinit var chipWeekend: Chip
    private lateinit var chipDateRange: Chip
    private lateinit var layoutDateRange: LinearLayout
    private lateinit var tvStartDate: TextInputEditText
    private lateinit var tvEndDate: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var switchGeofence: MaterialSwitch
    private lateinit var btnSetLocation: com.google.android.material.button.MaterialButton
    private lateinit var tvLocationInfo: TextView

    // Must be registered before onCreateView

    private val mapPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                selectedLat = intent.getDoubleExtra(MapPickerActivity.EXTRA_LAT, 51.5)
                selectedLng = intent.getDoubleExtra(MapPickerActivity.EXTRA_LNG, -0.1)
                selectedRadius = intent.getFloatExtra(MapPickerActivity.EXTRA_RADIUS, 500f)
                hasSelectedLocation = true
                updateLocationLabel()
            }
        }
    }

    private val privacyDisclosureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // User understood — now request the background location permission
            backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            // User declined — uncheck the toggle and inform them
            switchGeofence.isChecked = false
            updateGeofenceVisibility()
            Toast.makeText(requireContext(), "Geofence alarms require background location", Toast.LENGTH_SHORT).show()
        }
    }

    private val backgroundLocationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            switchGeofence.isChecked = false
            updateGeofenceVisibility()
            Toast.makeText(requireContext(), "Geofence alarms require background location", Toast.LENGTH_SHORT).show()
        }
        // If granted, the toggle stays checked — nothing more to do
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_add_edit_alarm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etLabel = view.findViewById(R.id.et_label)
        timePicker = view.findViewById(R.id.time_picker)
        chipGroup = view.findViewById(R.id.chip_group_type)
        chipFullWeek = view.findViewById(R.id.chip_full_week)
        chipWorkday = view.findViewById(R.id.chip_workday)
        chipWeekend = view.findViewById(R.id.chip_weekend)
        chipDateRange = view.findViewById(R.id.chip_date_range)
        layoutDateRange = view.findViewById(R.id.layout_date_range)
        tvStartDate = view.findViewById(R.id.et_start_date)
        tvEndDate = view.findViewById(R.id.et_end_date)
        btnSave = view.findViewById(R.id.btn_save)
        btnCancel = view.findViewById(R.id.btn_cancel)
        switchGeofence = view.findViewById(R.id.switch_geofence)
        btnSetLocation = view.findViewById(R.id.btn_set_location)
        tvLocationInfo = view.findViewById(R.id.tv_location_info)

        timePicker.setIs24HourView(DateFormat.is24HourFormat(requireContext()))

        // Restore arguments
        arguments?.let { args ->
            editingAlarmId = args.getLong(ARG_ALARM_ID, 0L)
            etLabel.setText(args.getString(ARG_LABEL, ""))
            val hour = args.getInt(ARG_HOUR, 7)
            val minute = args.getInt(ARG_MINUTE, 0)
            timePicker.hour = hour
            timePicker.minute = minute
            selectedStartDate = if (args.containsKey(ARG_START_DATE)) args.getLong(ARG_START_DATE) else null
            selectedEndDate = if (args.containsKey(ARG_END_DATE)) args.getLong(ARG_END_DATE) else null

            val typeName = args.getString(ARG_TYPE, AlarmType.FULL_WEEK.name)
            when (AlarmType.valueOf(typeName)) {
                AlarmType.FULL_WEEK -> chipFullWeek.isChecked = true
                AlarmType.WORKDAY -> chipWorkday.isChecked = true
                AlarmType.WEEKEND -> chipWeekend.isChecked = true
                AlarmType.DATE_RANGE -> chipDateRange.isChecked = true
            }

            // Restore geofence state
            val isGeoFenced = args.getBoolean(ARG_IS_GEOFENCED, false)
            switchGeofence.isChecked = isGeoFenced
            if (args.containsKey(ARG_GEO_LAT)) {
                selectedLat = args.getDouble(ARG_GEO_LAT, 51.5)
                selectedLng = args.getDouble(ARG_GEO_LNG, -0.1)
                selectedRadius = args.getFloat(ARG_GEO_RADIUS, 500f)
                hasSelectedLocation = isGeoFenced
            }
        } ?: run {
            chipFullWeek.isChecked = true
            timePicker.hour = 7
            timePicker.minute = 0
        }

        updateDateRangeVisibility()
        updateDateLabels()
        updateGeofenceVisibility()
        updateLocationLabel()

        chipGroup.setOnCheckedStateChangeListener { _, _ ->
            updateDateRangeVisibility()
        }

        tvStartDate.setOnClickListener { showStartDatePicker() }
        tvEndDate.setOnClickListener { showEndDatePicker() }

        view.findViewById<TextInputLayout>(R.id.til_start_date).setEndIconOnClickListener { showStartDatePicker() }
        view.findViewById<TextInputLayout>(R.id.til_end_date).setEndIconOnClickListener { showEndDatePicker() }

        switchGeofence.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val bgPermission = Manifest.permission.ACCESS_BACKGROUND_LOCATION
                if (ContextCompat.checkSelfPermission(requireContext(), bgPermission) == PackageManager.PERMISSION_GRANTED) {
                    // Already granted — proceed normally
                    updateGeofenceVisibility()
                } else {
                    // Must show prominent disclosure before requesting background location
                    val intent = Intent(requireContext(), PrivacyDisclosureActivity::class.java)
                    privacyDisclosureLauncher.launch(intent)
                    // Don't update visibility yet — wait for permission result
                }
            } else {
                updateGeofenceVisibility()
            }
        }

        btnSetLocation.setOnClickListener {
            val intent = Intent(requireContext(), MapPickerActivity::class.java).apply {
                putExtra(MapPickerActivity.EXTRA_LAT, selectedLat)
                putExtra(MapPickerActivity.EXTRA_LNG, selectedLng)
                putExtra(MapPickerActivity.EXTRA_RADIUS, selectedRadius)
            }
            mapPickerLauncher.launch(intent)
        }

        btnSave.setOnClickListener { saveAlarm() }
        btnCancel.setOnClickListener { dismiss() }
    }

    private fun updateDateRangeVisibility() {
        layoutDateRange.visibility = if (chipDateRange.isChecked) View.VISIBLE else View.GONE
    }

    private fun updateGeofenceVisibility() {
        val isOn = switchGeofence.isChecked
        btnSetLocation.visibility = if (isOn) View.VISIBLE else View.GONE
        tvLocationInfo.visibility = if (isOn && hasSelectedLocation) View.VISIBLE else View.GONE
    }

    private fun updateLocationLabel() {
        if (hasSelectedLocation) {
            val latStr = String.format(Locale.US, "%.4f", selectedLat)
            val lngStr = String.format(Locale.US, "%.4f", selectedLng)
            tvLocationInfo.text = "📍 $latStr, $lngStr (${selectedRadius.toInt()} m)"
            tvLocationInfo.visibility = if (switchGeofence.isChecked) View.VISIBLE else View.GONE
        } else {
            tvLocationInfo.visibility = View.GONE
        }
    }

    private fun updateDateLabels() {
        val fmt = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        tvStartDate.setText(selectedStartDate?.let { fmt.format(Date(it)) } ?: "")
        tvEndDate.setText(selectedEndDate?.let { fmt.format(Date(it)) } ?: "")
    }

    private fun showStartDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.select_start_date))
            .setSelection(selectedStartDate ?: MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            selectedStartDate = selection
            updateDateLabels()
        }
        picker.show(parentFragmentManager, "start_date_picker")
    }

    private fun showEndDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.select_end_date))
            .setSelection(selectedEndDate ?: MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            selectedEndDate = selection
            updateDateLabels()
        }
        picker.show(parentFragmentManager, "end_date_picker")
    }

    private fun getSelectedAlarmType(): AlarmType {
        return when (chipGroup.checkedChipId) {
            R.id.chip_workday -> AlarmType.WORKDAY
            R.id.chip_weekend -> AlarmType.WEEKEND
            R.id.chip_date_range -> AlarmType.DATE_RANGE
            else -> AlarmType.FULL_WEEK
        }
    }

    private fun saveAlarm() {
        val label = etLabel.text?.toString()?.trim() ?: ""
        val hour = timePicker.hour
        val minute = timePicker.minute
        val alarmType = getSelectedAlarmType()

        if (alarmType == AlarmType.DATE_RANGE) {
            if (selectedStartDate == null || selectedEndDate == null) {
                return
            }
        }

        val isGeoFenced = switchGeofence.isChecked && hasSelectedLocation

        val alarm = Alarm(
            id = editingAlarmId,
            label = label,
            hour = hour,
            minute = minute,
            alarmType = alarmType,
            startDate = if (alarmType == AlarmType.DATE_RANGE) selectedStartDate else null,
            endDate = if (alarmType == AlarmType.DATE_RANGE) selectedEndDate else null,
            isEnabled = true,
            isGeoFenced = isGeoFenced,
            geoFenceLat = if (isGeoFenced) selectedLat else 0.0,
            geoFenceLng = if (isGeoFenced) selectedLng else 0.0,
            geoFenceRadius = if (isGeoFenced) selectedRadius else 500f
        )
        onSave?.invoke(alarm)
        dismiss()
    }
}
