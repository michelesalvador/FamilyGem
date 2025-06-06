package app.familygem

import android.app.AlarmManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.os.LocaleListCompat
import app.familygem.databinding.SettingsActivityBinding
import org.joda.time.LocalTime
import org.joda.time.format.DateTimeFormat
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

class SettingsActivity : BaseActivity() {

    private lateinit var binding: SettingsActivityBinding
    private lateinit var languages: MutableList<Language>

    /** The actual Language of the app, otherwise the "system language". */
    private val actualLanguage: Language
        get() {
            val firstLocale = AppCompatDelegate.getApplicationLocales()[0]
            if (firstLocale != null) {
                for (i in 1 until languages.size) {
                    val language = languages[i]
                    if (firstLocale.toString().startsWith(language.code!!)) return language
                }
            }
            return languages[0]
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Auto save
        val saveSwitch = binding.settingsAutoSave
        saveSwitch.isChecked = Global.settings.autoSave
        saveSwitch.setOnCheckedChangeListener { _, checked ->
            Global.settings.autoSave = checked
            Global.settings.save()
        }

        // Load tree at startup
        val loadSwitch = binding.settingsLoadTree
        loadSwitch.isChecked = Global.settings.loadTree
        loadSwitch.setOnCheckedChangeListener { _, checked ->
            Global.settings.loadTree = checked
            Global.settings.save()
        }

        // Expert mode
        val expertSwitch = binding.settingsExpert
        expertSwitch.isChecked = Global.settings.expert
        expertSwitch.setOnCheckedChangeListener { _, checked ->
            Global.settings.expert = checked
            Global.settings.save()
        }

        // Birthday notification time
        writeNotifyTime()
        binding.settingsNotifyTime.setOnClickListener {
            TimePickerFragment(this).show(supportFragmentManager, "timePicker")
        }

        // Alarms & reminders permission button
        if (!isExactAlarmsGranted()) {
            val button = binding.settingsAlarms
            button.visibility = View.VISIBLE
            button.setOnClickListener {
                // Opens the exact alarm page in system settings
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        }

        // Local backup
        binding.settingsBackup.setOnClickListener {
            startActivity(Intent(this, BackupActivity::class.java))
        }

        // Language picker
        languages = ArrayList()
        languages.add(Language(null, 0)) // System language
        // Gets languages from locales_config.xml
        val xpp: XmlPullParser = resources.getXml(R.xml.locales_config)
        while (xpp.eventType != XmlPullParser.END_DOCUMENT) {
            if (xpp.eventType == XmlPullParser.START_TAG && xpp.name == "locale") {
                val percent = xpp.getAttributeValue(null, "percent")
                languages.add(Language(xpp.getAttributeValue(0), percent?.toInt() ?: 100))
            }
            xpp.next()
        }
        languages.sort()
        val languageView = binding.settingsLanguage
        val actual = actualLanguage
        languageView.text = actual.toString()
        val languageArray = languages.map { it.toString() }.toTypedArray()
        languageView.setOnClickListener { view: View ->
            AlertDialog.Builder(view.context)
                .setSingleChoiceItems(languageArray, languages.indexOf(actual)) { dialog, item ->
                    // Sets app locale and store it for the future
                    val appLocale = LocaleListCompat.forLanguageTags(languages[item].code)
                    AppCompatDelegate.setApplicationLocales(appLocale)
                    // Updates app global context for this session only
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        binding.settingsLayout.postDelayed({
                            Global.context = ContextCompat.getContextForLanguage(applicationContext)
                        }, 50) // Waits just a bit to get the correct locale
                    }
                    dialog.dismiss()
                }.show()
        }

        // About
        binding.settingsAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    fun writeNotifyTime() {
        val localTime = LocalTime.parse(Global.settings.notifyTime)
        val format = DateTimeFormat.shortTime()
        binding.settingsNotifyTime.text = localTime.toString(format).replace("^0".toRegex(), "")
    }

    private fun isExactAlarmsGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService<AlarmManager>()!!.canScheduleExactAlarms()
        } else true
    }

    override fun onResume() {
        super.onResume()
        if (isExactAlarmsGranted()) {
            binding.settingsAlarms.visibility = View.GONE
        }
    }

    inner class Language(val code: String?, private val percent: Int) : Comparable<Language> {
        override fun toString(): String {
            return if (code == null) {
                // Returns the string "System language" on the device locale, not on the app locale
                val configuration = Configuration(resources.configuration)
                val supportedLocales = languages.filterNot { it.code == null }.map { it.code }.toTypedArray()
                val deviceLocale = LocaleManagerCompat.getSystemLocales(Global.context).getFirstMatch(supportedLocales)
                configuration.setLocale(deviceLocale)
                createConfigurationContext(configuration).getText(R.string.system_language).toString()
            } else {
                val locale = Locale(code)
                var txt = locale.getDisplayLanguage(locale)
                txt = txt.substring(0, 1).uppercase() + txt.substring(1)
                if (percent < 100) txt += " ($percent%)"
                txt
            }
        }

        override fun compareTo(other: Language): Int {
            return if (other.code == null) 1
            else toString().compareTo(other.toString())
        }
    }
}
