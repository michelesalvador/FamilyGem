package app.familygem

import android.app.DatePickerDialog
import android.app.Dialog

import android.os.Bundle
import android.widget.DatePicker
import androidx.fragment.app.DialogFragment
import app.familygem.main.TreeSettingsFragment
import org.joda.time.LocalDate
import java.util.Calendar

class DatePickerFragment(val fragment: TreeSettingsFragment) : DialogFragment(), DatePickerDialog.OnDateSetListener {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val year: Int
        val month: Int
        val day: Int
        if (fragment.date == null) {
            val now = LocalDate.now() // Current time
            year = now.year
            month = now.monthOfYear - 1
            day = now.dayOfMonth
        } else {
            year = fragment.date!!.year
            month = fragment.date!!.monthOfYear - 1
            day = fragment.date!!.dayOfMonth
        }
        val dialog = DatePickerDialog(requireActivity(), this, year, month, day)
        val calendar: Calendar = Calendar.getInstance()
        calendar.set(1, 0, 1);
        dialog.datePicker.minDate = calendar.timeInMillis;
        calendar.set(3000, 11, 31);
        dialog.datePicker.maxDate = calendar.timeInMillis;
        dialog.datePicker.init(year, month, day, null) // Fixes a bug of datePicker with years before 1900
        return dialog
    }

    override fun onDateSet(picker: DatePicker?, year: Int, month: Int, day: Int) {
        fragment.date = LocalDate(year, month + 1, day)
        fragment.writeDate()
        fragment.edited = true
    }
}
