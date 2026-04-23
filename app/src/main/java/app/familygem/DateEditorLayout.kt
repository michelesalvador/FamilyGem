package app.familygem

import android.content.Context
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import app.familygem.constant.Format
import app.familygem.constant.Kind
import org.joda.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/** Layout containing all the instruments to generate a standard GEDCOM date into a given editText. */
class DateEditorLayout(context: Context, set: AttributeSet?) : LinearLayout(context, set) {
    private lateinit var dateConverter: DateConverter
    private lateinit var firstDate: DateConverter.SingleDate
    private var secondDate: DateConverter.SingleDate? = null
    private lateinit var editText: EditText
    private lateinit var alertView: ImageView
    private val days = arrayOf(
        "-", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15",
        "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31"
    )
    private val months = arrayOf(
        "-", s(R.string.january), s(R.string.february), s(R.string.march), s(R.string.april), s(R.string.may), s(R.string.june),
        s(R.string.july), s(R.string.august), s(R.string.september), s(R.string.october), s(R.string.november), s(R.string.december)
    )
    private val years = arrayOfNulls<String>(101)
    private val dateKinds = intArrayOf(
        R.string.exact, R.string.approximate, R.string.calculated, R.string.estimated,
        R.string.after, R.string.before, R.string.between_and,
        R.string.from, R.string.to, R.string.from_to, R.string.date_phrase
    )

    private var trueTextInput = false // The user is actually typing on the virtual keyboard or the text is changed otherwise
    private lateinit var keyboard: InputMethodManager
    private var keyboardVisible = false

    /** Actions to be done only once at the beginning.
     * @param editText Already contains the date text to be edited */
    fun initialize(editText: EditText, alertView: ImageView) {
        addView(inflate(context, R.layout.date_editor, null), this.layoutParams)
        this.editText = editText

        for (i in 0..<years.size - 1) years[i] = if (i < 10) "0$i" else "$i"
        years[100] = "-"

        dateConverter = DateConverter(editText.text.toString())
        firstDate = dateConverter.firstDate
        secondDate = dateConverter.secondDate

        this.alertView = alertView
        alertView.setOnClickListener { _ ->
            Toast.makeText(context, R.string.invalid_date, Toast.LENGTH_LONG).show()
        }
        checkValidDate()

        // Furnishes the date editor
        if (Global.settings.expert) {
            val kindList = findViewById<TextView>(R.id.dateEditor_kinds)
            kindList.setOnClickListener { view: View ->
                val popup = PopupMenu(context, view)
                val menu = popup.menu
                for (i in 0..<dateKinds.size - 1) menu.add(0, i, 0, dateKinds[i])
                popup.show()
                popup.setOnMenuItemClickListener { item: MenuItem ->
                    dateConverter.kind = Kind.entries.toTypedArray()[item.itemId]
                    findViewById<View>(R.id.dateEditor_first).visibility = VISIBLE // If possibly invisible
                    if (firstDate.date == null)  // Micro setting of the pickers
                        findViewById<NumberPicker>(R.id.dateEditor_firstYear).value = 100
                    if (dateConverter.kind == Kind.BETWEEN_AND || dateConverter.kind == Kind.FROM_TO) {
                        findViewById<View>(R.id.dateEditor_secondExpert).visibility = VISIBLE
                        findViewById<View>(R.id.dateEditor_second).visibility = VISIBLE
                        if (secondDate?.date == null) findViewById<NumberPicker>(R.id.dateEditor_secondYear).value = 100
                    } else {
                        findViewById<View>(R.id.dateEditor_secondExpert).visibility = GONE
                        findViewById<View>(R.id.dateEditor_second).visibility = GONE
                    }
                    kindList.setText(dateKinds[item.itemId])
                    generateDate()
                    true
                }
            }
            findViewById<CheckBox>(R.id.dateEditor_firstNegative).setOnClickListener { view: View ->
                firstDate.negative = (view as CheckBox).isChecked
                generateDate()
            }
            findViewById<CheckBox>(R.id.dateEditor_firstDual).setOnClickListener { view: View ->
                firstDate.dual = (view as CheckBox).isChecked
                generateDate()
            }
            findViewById<CheckBox>(R.id.dateEditor_secondNegative).setOnClickListener { view: View ->
                secondDate?.negative = (view as CheckBox).isChecked
                generateDate()
            }
            findViewById<CheckBox>(R.id.dateEditor_secondDual).setOnClickListener { view: View ->
                secondDate?.dual = (view as CheckBox).isChecked
                generateDate()
            }
            findViewById<View>(R.id.dateEditor_approximate).visibility = GONE
        } else {
            findViewById<View>(R.id.dateEditor_approximate).setOnClickListener { view: View ->
                findViewById<View>(R.id.dateEditor_second).visibility = GONE // In case it was visible because of BETWEEN_AND or FROM_TO
                dateConverter.kind = if ((view as CheckBox).isChecked) Kind.APPROXIMATE else Kind.EXACT
                generateDate()
            }
            findViewById<View>(R.id.dateEditor_firstExpert).visibility = GONE
        }

        initializeTrain(
            1, findViewById(R.id.dateEditor_firstDay), findViewById(R.id.dateEditor_firstMonth),
            findViewById(R.id.dateEditor_firstCentury), findViewById(R.id.dateEditor_firstYear)
        )
        initializeTrain(
            2, findViewById(R.id.dateEditor_secondDay), findViewById(R.id.dateEditor_secondMonth),
            findViewById(R.id.dateEditor_secondCentury), findViewById(R.id.dateEditor_secondYear)
        )

        // At first focus DateEditorLayout shows itself hiding the keyboard
        keyboard = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        editText.onFocusChangeListener = OnFocusChangeListener { _, getFocus ->
            if (getFocus) {
                if (dateConverter.kind == Kind.PHRASE) {
                    editText.setText(dateConverter.phrase) // To remove parentheses around the phrase
                } else {
                    keyboardVisible = keyboard.hideSoftInputFromWindow(editText.windowToken, 0) // Hides keyboard
                    // Disables text input from keyboard
                    editText.inputType = InputType.TYPE_NULL // Necessary in recent versions of Android where the keyboard reappears
                }
                setupDateEditor()
                visibility = VISIBLE
            } else visibility = GONE
        }

        // On the second tap brings up the keyboard
        editText.setOnTouchListener { _, event ->
            if (event?.action == MotionEvent.ACTION_DOWN) {
                editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS // Re-enables the input
            } else if (event.action == MotionEvent.ACTION_UP) {
                keyboardVisible = keyboard.showSoftInput(editText, 0) // Makes the keyboard reappear
            }
            false
        }
        // Sets the date editor based on what the user writes
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, i: Int, i1: Int, i2: Int) {}

            override fun onTextChanged(text: CharSequence?, i: Int, i1: Int, i2: Int) {}

            override fun afterTextChanged(text: Editable?) {
                // I don't know why but in Android 5 on first editing it is called 2 times, which isn't a problem anyway
                if (trueTextInput) setupDateEditor()
                trueTextInput = true
            }
        })
    }

    /** Prepares the four pickers of a train with the initial settings.
     * @param train 1 is first, otherwise second train of pickers */
    private fun initializeTrain(
        train: Int, dayPicker: NumberPicker, monthPicker: NumberPicker, centuryPicker: NumberPicker, yearPicker: NumberPicker
    ) {
        dayPicker.minValue = 0
        dayPicker.maxValue = 31
        dayPicker.displayedValues = days
        preparePicker(dayPicker)
        dayPicker.setOnValueChangedListener { _, _, _ ->
            updateDate((if (train == 1) firstDate else secondDate)!!, dayPicker, monthPicker, centuryPicker, yearPicker)
        }
        monthPicker.minValue = 0
        monthPicker.maxValue = 12
        monthPicker.displayedValues = months
        preparePicker(monthPicker)
        monthPicker.setOnValueChangedListener { _, _, _ ->
            updateDate((if (train == 1) firstDate else secondDate)!!, dayPicker, monthPicker, centuryPicker, yearPicker)
        }
        centuryPicker.minValue = 0
        centuryPicker.maxValue = 20
        preparePicker(centuryPicker)
        centuryPicker.setOnValueChangedListener { _, _, _ ->
            updateDate((if (train == 1) firstDate else secondDate)!!, dayPicker, monthPicker, centuryPicker, yearPicker)
        }
        yearPicker.minValue = 0
        yearPicker.maxValue = 100
        yearPicker.displayedValues = years
        preparePicker(yearPicker)
        yearPicker.setOnValueChangedListener { _, _, _ ->
            updateDate((if (train == 1) firstDate else secondDate)!!, dayPicker, monthPicker, centuryPicker, yearPicker)
        }
    }

    private fun preparePicker(picker: NumberPicker) {
        // Removes the dividing blue lines on API <= 22
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

    /** Takes the date string, updates the Dates and modifies the entire date editor.
     * Called when I click on the editable field, and after any text editing. */
    private fun setupDateEditor() {
        dateConverter.analyze(editText.text.toString())
        findViewById<CheckBox>(R.id.dateEditor_approximate).isChecked = dateConverter.kind == Kind.APPROXIMATE
        findViewById<TextView>(R.id.dateEditor_kinds).setText(dateKinds[dateConverter.kind!!.ordinal])
        // First train
        setupTrain(
            firstDate, findViewById(R.id.dateEditor_firstDay), findViewById(R.id.dateEditor_firstMonth),
            findViewById(R.id.dateEditor_firstCentury), findViewById(R.id.dateEditor_firstYear)
        )
        if (Global.settings.expert) setupCheckboxes(firstDate)
        // Second train
        if (dateConverter.kind == Kind.BETWEEN_AND || dateConverter.kind == Kind.FROM_TO) {
            setupTrain(
                secondDate!!, findViewById(R.id.dateEditor_secondDay), findViewById(R.id.dateEditor_secondMonth),
                findViewById(R.id.dateEditor_secondCentury), findViewById(R.id.dateEditor_secondYear)
            )
            if (Global.settings.expert) {
                findViewById<View>(R.id.dateEditor_secondExpert).visibility = VISIBLE
                setupCheckboxes(secondDate!!)
            }
            findViewById<View>(R.id.dateEditor_second).visibility = VISIBLE
        } else {
            findViewById<View>(R.id.dateEditor_secondExpert).visibility = GONE
            findViewById<View>(R.id.dateEditor_second).visibility = GONE
        }
        checkValidDate()
    }

    /** Turns the number pickers of one train based on a date. */
    private fun setupTrain(
        date: DateConverter.SingleDate, dayPicker: NumberPicker, monthPicker: NumberPicker, centuryPicker: NumberPicker, yearPicker: NumberPicker
    ) {
        if (date.date != null) dayPicker.maxValue = date.date!!.dayOfMonth().maximumValue
        dayPicker.value = if (date.date != null && (date.format == Format.D || date.format == Format.D_M || date.format == Format.D_M_Y))
            date.date!!.dayOfMonth else 0
        monthPicker.value =
            if (date.date != null && (date.format == Format.M || date.format == Format.D_M || date.format == Format.M_Y || date.format == Format.D_M_Y))
                date.date!!.monthOfYear else 0
        if (date.date != null && (date.format == Format.Y || date.format == Format.M_Y || date.format == Format.D_M_Y)) {
            val year = abs(date.date!!.year)
            centuryPicker.value = if (year < 2100) year / 100 else 20 // 20 is the maximum century
            yearPicker.value = if (year < 2100) year % 100 else 99 // 99 is the maximum year
        } else {
            centuryPicker.value = 0
            yearPicker.value = 100
        }
    }

    /** Sets the negative and dual checkboxes for a date. */
    private fun setupCheckboxes(date: DateConverter.SingleDate) {
        val negativeCheckbox: CheckBox
        val dualCheckbox: CheckBox
        if (date == firstDate) {
            negativeCheckbox = findViewById(R.id.dateEditor_firstNegative)
            dualCheckbox = findViewById(R.id.dateEditor_firstDual)
        } else {
            negativeCheckbox = findViewById(R.id.dateEditor_secondNegative)
            dualCheckbox = findViewById(R.id.dateEditor_secondDual)
        }
        negativeCheckbox.isEnabled = date.format != Format.OTHER
        negativeCheckbox.isChecked = date.negative
        dualCheckbox.isEnabled = date.hasYear
        dualCheckbox.isChecked = date.dual
    }

    /** Updates a SingleDate with the values taken from a train of pickers. */
    private fun updateDate(
        date: DateConverter.SingleDate, dayPicker: NumberPicker, monthPicker: NumberPicker, centuryPicker: NumberPicker, yearPicker: NumberPicker
    ) {
        if (keyboardVisible) { // Hides any visible keyboard
            // Immediately hides the keyboard, but needs a second try to return false. However, it's not a problem.
            keyboardVisible = keyboard.hideSoftInputFromWindow(editText.windowToken, 0)
        }
        val century = centuryPicker.value
        val year = yearPicker.value
        val month = monthPicker.value
        val singleDate = LocalDate(century * 100 + year, if (month == 0) 1 else month, 1)
        dayPicker.maxValue = singleDate.dayOfMonth().maximumValue // Sets the days of the month in dayPicker
        val day = dayPicker.value
        date.date = singleDate.withDayOfMonth(if (day == 0) 1 else day).withEra(if (date.negative) 0 else 1)
        // Updates the date format
        if (day != 0 && month != 0 && year != 100) date.format = Format.D_M_Y
        else if (day != 0 && month != 0) date.format = Format.D_M
        else if (month != 0 && year != 100) date.format = Format.M_Y
        else if (year != 100) date.format = Format.Y
        else if (month != 0) date.format = Format.M
        else if (day != 0) date.format = Format.D
        else date.format = Format.OTHER
        setupCheckboxes(date)
        generateDate()
    }

    /** Rewrites the string with the final GEDCOM date and puts it in editText. */
    private fun generateDate() {
        val rewritten = when (dateConverter.kind) {
            Kind.EXACT -> writeDate(firstDate)
            Kind.BETWEEN_AND -> "BET " + writeDate(firstDate) + " AND " + writeDate(secondDate!!)
            Kind.FROM_TO -> "FROM " + writeDate(firstDate) + " TO " + writeDate(secondDate!!)
            Kind.PHRASE -> { // The phrase is replaced by an exact date
                dateConverter.kind = Kind.EXACT
                findViewById<TextView>(R.id.dateEditor_kinds)?.setText(dateKinds[0])
                writeDate(firstDate)
            }
            else -> dateConverter.kind!!.prefix + " " + writeDate(firstDate)
        }
        trueTextInput = false
        editText.setText(rewritten)
        checkValidDate()
    }

    /** Writes a single date according to the format. */
    private fun writeDate(singleDate: DateConverter.SingleDate): String {
        var text = ""
        if (singleDate.date != null && singleDate.format != Format.OTHER) {
            text = singleDate.date!!.toString(singleDate.format!!.pattern, Locale.US)
            if (singleDate.dual && singleDate.hasYear) {
                text += "/" + singleDate.date!!.plusYears(1).toString("yy")
            }
        }
        if (singleDate.negative) text += " B.C."
        return text.replace("-", "").uppercase()
    }

    /** Displays or hides the alert icon according to whether the date is valid. */
    private fun checkValidDate() {
        if (Global.settings.expert) {
            val valid = dateConverter.isValid(editText.text.toString())
            alertView.visibility = if (valid) GONE else VISIBLE
        }
    }

    /** If the date is a phrase adds parentheses around it. */
    fun finishEditing() {
        if (dateConverter.kind == Kind.PHRASE) {
            val text = editText.text.toString().replace("[()]".toRegex(), "").trim()
            editText.setText("($text)")
        }
    }

    private fun s(id: Int): String {
        return Global.context.getString(id)
    }

    /** Mocks initialization just for testing purpose. */
    fun testInitialize(editText: EditText) {
        this.editText = editText
        dateConverter = DateConverter(editText.text.toString())
        firstDate = dateConverter.firstDate
        secondDate = dateConverter.secondDate
    }

    fun testGenerateDate() {
        generateDate()
    }

    val testDateConverter: DateConverter
        get() = dateConverter
}
