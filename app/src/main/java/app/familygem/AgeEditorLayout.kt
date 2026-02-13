package app.familygem

import android.content.Context
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.RadioButton
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.min

/** Layout containing all the instruments to generate a standard GEDCOM age into a given editText. */
class AgeEditorLayout(context: Context, set: AttributeSet?) : LinearLayout(context, set) {
    private lateinit var ageConverter: AgeConverter
    private lateinit var age: AgeConverter.Age
    private lateinit var editText: EditText
    private lateinit var yearsPicker: NumberPicker
    private lateinit var monthsPicker: NumberPicker
    private lateinit var daysPicker: NumberPicker
    private lateinit var alertView: ImageView
    private var trueTextInput = false // The user is actually typing on the virtual keyboard or the text is changed otherwise
    private lateinit var keyboard: InputMethodManager
    private var keyboardVisible = false

    /** Actions to be done only once at the beginning.
     * @param editText Already contains the age text to be edited */
    fun initialize(editText: EditText, alertView: ImageView) {
        addView(inflate(context, R.layout.age_editor, null), this.layoutParams)
        this.editText = editText
        yearsPicker = findViewById(R.id.ageEditor_years)
        monthsPicker = findViewById(R.id.ageEditor_months)
        daysPicker = findViewById(R.id.ageEditor_days)
        keyboard = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        ageConverter = AgeConverter(editText.text.toString())
        age = ageConverter.age

        findViewById<RadioButton>(R.id.ageEditor_exact).setOnClickListener {
            age.modifier = AgeConverter.Modifier.EXACT
            generateAge()
        }
        findViewById<RadioButton>(R.id.ageEditor_less).setOnClickListener {
            age.modifier = AgeConverter.Modifier.LESS
            generateAge()
        }
        findViewById<RadioButton>(R.id.ageEditor_more).setOnClickListener {
            age.modifier = AgeConverter.Modifier.MORE
            generateAge()
        }

        yearsPicker.apply {
            val years = mutableListOf<String>()
            val maxYears = min(abs(Global.settings.currentTree.settings.lifeSpan), 999)
            years.add("-")
            for (i in 1..maxYears) years.add("$i")
            minValue = 0
            maxValue = maxYears
            displayedValues = years.toTypedArray()
            preparePicker(this)
            setOnValueChangedListener { _, _, _ ->
                age.period = age.period.withYears(value)
                generateAge()
            }
        }
        monthsPicker.apply {
            minValue = 0
            maxValue = 12
            displayedValues = arrayOf("-", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12")
            preparePicker(this)
            setOnValueChangedListener { _, _, _ ->
                age.period = age.period.withMonths(value)
                generateAge()
            }
        }
        daysPicker.apply {
            minValue = 0
            maxValue = 31
            displayedValues = arrayOf(
                "-", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16",
                "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31"
            )
            preparePicker(this)
            setOnValueChangedListener { _, _, _ ->
                age.period = age.period.withDays(value)
                generateAge()
            }
        }

        this.alertView = alertView
        alertView.setOnClickListener { _ ->
            Toast.makeText(context, R.string.invalid_age, Toast.LENGTH_LONG).show()
        }
        checkValidAge()

        // At first focus AgeEditorLayout shows itself
        editText.onFocusChangeListener = OnFocusChangeListener { _, getFocus ->
            if (getFocus) {
                setupAgeEditor()
                visibility = VISIBLE
            } else visibility = GONE
        }
        // On the second tap brings up the keyboard
        editText.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                keyboardVisible = keyboard.showSoftInput(editText, 0)
            }
            false
        }
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, i: Int, i1: Int, i2: Int) {}

            override fun onTextChanged(text: CharSequence?, i: Int, i1: Int, i2: Int) {}

            override fun afterTextChanged(text: Editable?) {
                if (trueTextInput) setupAgeEditor()
                trueTextInput = true
            }
        })
    }

    private fun preparePicker(picker: NumberPicker) {
        // Removes the divider blue lines on API <= 22
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                val field = NumberPicker::class.java.getDeclaredField("mSelectionDivider")
                field.isAccessible = true
                field.set(picker, null)
            } catch (_: Exception) {
            }
        }
        // Fixes the bug https://issuetracker.google.com/issues/37055335
        picker.isSaveFromParentEnabled = false
    }

    /** Takes the age string, updates the Age and modifies the entire age editor.
     * Called when I click on the editable field, and after any text editing. */
    private fun setupAgeEditor() {
        age.scan(editText.text.toString())
        when (age.modifier) {
            AgeConverter.Modifier.LESS -> findViewById<RadioButton>(R.id.ageEditor_less).isChecked = true
            AgeConverter.Modifier.MORE -> findViewById<RadioButton>(R.id.ageEditor_more).isChecked = true
            else -> findViewById<RadioButton>(R.id.ageEditor_exact).isChecked = true
        }
        yearsPicker.value = if (age.period.years > yearsPicker.maxValue) yearsPicker.maxValue else age.period.years
        monthsPicker.value = if (age.period.months > monthsPicker.maxValue) monthsPicker.maxValue else age.period.months
        daysPicker.value = if (age.period.days > daysPicker.maxValue) daysPicker.maxValue else age.period.days
        checkValidAge()
    }

    /** Rewrites the string with the final GEDCOM age and puts it in editText. */
    private fun generateAge() {
        if (keyboardVisible) {
            keyboardVisible = keyboard.hideSoftInputFromWindow(editText.windowToken, 0)
        }
        val builder = StringBuilder()
        if (age.modifier == AgeConverter.Modifier.LESS) builder.append("< ")
        else if (age.modifier == AgeConverter.Modifier.MORE) builder.append("> ")
        if (age.period.years > 0) builder.append(age.period.years).append("y ")
        if (age.period.months > 0) builder.append(age.period.months).append("m ")
        if (age.period.days > 0) builder.append(age.period.days).append("d")
        trueTextInput = false
        editText.setText(builder.toString().trim())
        checkValidAge()
    }

    /** Displays or hides the alert icon according to whether the age is valid. */
    private fun checkValidAge() {
        if (Global.settings.expert) {
            val valid = ageConverter.isValid(editText.text.toString())
            alertView.visibility = if (valid) GONE else VISIBLE
        }
    }
}
