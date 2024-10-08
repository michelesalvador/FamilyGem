package app.familygem

import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.TimePicker
import androidx.fragment.app.DialogFragment
import org.joda.time.LocalTime
import org.joda.time.format.DateTimeFormat

class TimePickerFragment(private val settingsActivity: SettingsActivity) : DialogFragment(), TimePickerDialog.OnTimeSetListener {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Uses the settings time as the default values for the picker
        val time = Global.settings.notifyTime.split(":")
        return TimePickerDialog(activity, this, time[0].toInt(), time[1].toInt(), DateFormat.is24HourFormat(activity))
    }

    override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {
        // Hours are returned in 24 format, also for AM/PM picker
        val localTime = LocalTime(hourOfDay, minute)
        val format = DateTimeFormat.forPattern("HH:mm")
        Global.settings.notifyTime = localTime.toString(format) // Notifier will save settings
        settingsActivity.writeNotifyTime()
        Notifier(context, null, 0, Notifier.What.UPDATE)
    }
}
