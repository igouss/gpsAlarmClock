package com.elendal.gpsalarmclock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Locale

class AlarmsAdapter(
    private val onItemClick: (Alarm) -> Unit,
    private val onItemLongClick: (Alarm) -> Boolean,
    private val onToggle: (Alarm) -> Unit
) : ListAdapter<Alarm, AlarmsAdapter.AlarmViewHolder>(AlarmDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.card_alarm)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_alarm_time)
        private val tvLabel: TextView = itemView.findViewById(R.id.tv_alarm_label)
        private val chipType: Chip = itemView.findViewById(R.id.chip_alarm_type)
        private val switchEnabled: MaterialSwitch = itemView.findViewById(R.id.switch_alarm_enabled)

        fun bind(alarm: Alarm) {
            tvTime.text = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute)
            tvLabel.text = alarm.label.ifEmpty { itemView.context.getString(R.string.no_label) }
            chipType.text = when (alarm.alarmType) {
                AlarmType.FULL_WEEK -> itemView.context.getString(R.string.type_full_week)
                AlarmType.WORKDAY -> itemView.context.getString(R.string.type_workday)
                AlarmType.WEEKEND -> itemView.context.getString(R.string.type_weekend)
                AlarmType.DATE_RANGE -> itemView.context.getString(R.string.type_date_range)
            }

            // Avoid triggering listener during bind
            switchEnabled.setOnCheckedChangeListener(null)
            switchEnabled.isChecked = alarm.isEnabled

            val alpha = if (alarm.isEnabled) 1f else 0.5f
            cardView.alpha = alpha

            switchEnabled.setOnCheckedChangeListener { _, _ ->
                onToggle(alarm)
            }

            cardView.setOnClickListener { onItemClick(alarm) }
            cardView.setOnLongClickListener { onItemLongClick(alarm) }
        }
    }

    class AlarmDiffCallback : DiffUtil.ItemCallback<Alarm>() {
        override fun areItemsTheSame(oldItem: Alarm, newItem: Alarm) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Alarm, newItem: Alarm) = oldItem == newItem
    }
}
