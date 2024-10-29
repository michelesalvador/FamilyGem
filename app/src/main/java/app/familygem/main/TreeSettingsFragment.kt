package app.familygem.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.addTextChangedListener
import app.familygem.DatePickerFragment
import app.familygem.Global
import app.familygem.Notifier
import app.familygem.R
import app.familygem.U
import app.familygem.databinding.TreeSettingsFragmentBinding
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat

class TreeSettingsFragment : BaseFragment(R.layout.tree_settings_fragment) {

    private lateinit var binding: TreeSettingsFragmentBinding
    private val treeSettings = Global.settings.currentTree.settings
    private lateinit var yearsEdit: EditText
    private lateinit var dateGroup: RadioGroup
    private lateinit var dateView: TextView
    var date: LocalDate? = null
    var edited = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = TreeSettingsFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as AppCompatActivity?)!!.supportActionBar!!.title = getString(R.string.tree_settings)

        // Life span
        yearsEdit = binding.treeSettingsYears
        yearsEdit.setText(treeSettings.lifeSpan.toString())
        yearsEdit.addTextChangedListener {
            edited = true
        }

        // Current date
        dateGroup = binding.treeSettingsCurrentDate
        dateView = binding.treeSettingsDate
        dateGroup.check(if (treeSettings.customDate) R.id.treeSettings_fixedDate else R.id.treeSettings_deviceDate)
        dateGroup.setOnCheckedChangeListener { _, selected ->
            val fixed = selected == R.id.treeSettings_fixedDate
            dateView.setTextColor(if (fixed) color(R.color.text) else color(R.color.gray_text))
            if (fixed && date == null) date = LocalDate.now()
            edited = true
        }
        if (treeSettings.fixedDate != null) date = LocalDate(treeSettings.fixedDate)
        writeDate()
        dateView.setTextColor(if (treeSettings.customDate) color(R.color.text) else color(R.color.gray_text))
        dateView.setOnClickListener {
            dateGroup.check(R.id.treeSettings_fixedDate)
            DatePickerFragment(this).show(requireActivity().supportFragmentManager, "datePicker")
        }
    }

    fun color(int: Int): Int {
        return ResourcesCompat.getColor(resources, int, null)
    }

    fun writeDate() {
        val formatter = DateTimeFormat.forPattern("d MMMM y")
        dateView.text = if (date == null) LocalDate.now().toString(formatter)
        else date!!.toString(formatter)
    }

    override fun onPause() {
        super.onPause()
        // Saves tree settings
        if (edited) {
            treeSettings.lifeSpan = U.extractNum(yearsEdit.text.toString())
            treeSettings.customDate = dateGroup.checkedRadioButtonId == R.id.treeSettings_fixedDate
            if (date != null) treeSettings.fixedDate = date.toString() // Converts the date to format "2000-12-31"
            if (treeSettings.customDate) {
                Notifier(context, Global.gc, Global.settings.openTree, Notifier.What.DELETE)
                Global.settings.currentTree.birthdays.clear()
            }
            Global.settings.save()
            Global.edited = true
        }
    }
}
