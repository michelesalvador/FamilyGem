package app.familygem;

import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.appcompat.widget.PopupMenu;

import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

import app.familygem.constant.Format;
import app.familygem.constant.Kind;

/**
 * Layout containing all the instruments to generate a standard GEDCOM date into a given editText.
 */
public class DateEditorLayout extends LinearLayout {

    private GedcomDateConverter dateConverter;
    private GedcomDateConverter.SingleDate firstDate;
    private GedcomDateConverter.SingleDate secondDate;
    private EditText editText;
    private final String[] days = {"-", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15",
            "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31"};
    private final String[] months = {"-", s(R.string.january), s(R.string.february), s(R.string.march), s(R.string.april), s(R.string.may), s(R.string.june),
            s(R.string.july), s(R.string.august), s(R.string.september), s(R.string.october), s(R.string.november), s(R.string.december)};
    private final String[] years = new String[101];
    private final int[] dateKinds = {R.string.exact, R.string.approximate, R.string.calculated, R.string.estimated,
            R.string.after, R.string.before, R.string.between_and,
            R.string.from, R.string.to, R.string.from_to, R.string.date_phrase};
    private final Calendar calendar = GregorianCalendar.getInstance();
    private boolean trueTextInput; // The user is actually typing on the virtual keyboard or the text is changed otherwise
    private InputMethodManager keyboard;
    private boolean keyboardVisible;

    public DateEditorLayout(Context context, AttributeSet set) {
        super(context, set);
    }

    /**
     * Actions to be done only once at the beginning.
     *
     * @param editText Already contains the date text to be edited
     */
    void initialize(final EditText editText) {

        addView(inflate(getContext(), R.layout.date_editor, null), this.getLayoutParams());
        this.editText = editText;

        for (int i = 0; i < years.length - 1; i++)
            years[i] = i < 10 ? "0" + i : "" + i;
        years[100] = "-";

        dateConverter = new GedcomDateConverter(editText.getText().toString());
        firstDate = dateConverter.firstDate;
        secondDate = dateConverter.secondDate;

        // Furnishes the date editor
        if (Global.settings.expert) {
            final TextView kindList = findViewById(R.id.dateEditor_kinds);
            kindList.setOnClickListener(view -> {
                PopupMenu popup = new PopupMenu(getContext(), view);
                Menu menu = popup.getMenu();
                for (int i = 0; i < dateKinds.length - 1; i++)
                    menu.add(0, i, 0, dateKinds[i]);
                popup.show();
                popup.setOnMenuItemClickListener(item -> {
                    dateConverter.kind = Kind.values()[item.getItemId()];
                    findViewById(R.id.dateEditor_first).setVisibility(View.VISIBLE); // If possibly invisible
                    if (firstDate.date == null) // Micro setting of the pickers
                        ((NumberPicker)findViewById(R.id.dateEditor_firstYear)).setValue(100);
                    if (dateConverter.kind == Kind.BETWEEN_AND || dateConverter.kind == Kind.FROM_TO) {
                        findViewById(R.id.dateEditor_secondExpert).setVisibility(VISIBLE);
                        findViewById(R.id.dateEditor_second).setVisibility(VISIBLE);
                        if (secondDate.date == null)
                            ((NumberPicker)findViewById(R.id.dateEditor_secondYear)).setValue(100);
                    } else {
                        findViewById(R.id.dateEditor_secondExpert).setVisibility(GONE);
                        findViewById(R.id.dateEditor_second).setVisibility(GONE);
                    }
                    kindList.setText(dateKinds[item.getItemId()]);
                    trueTextInput = false;
                    generateDate();
                    return true;
                });
            });
            findViewById(R.id.dateEditor_firstNegative).setOnClickListener(view -> {
                firstDate.negative = ((CompoundButton)view).isChecked();
                trueTextInput = false;
                generateDate();
            });
            findViewById(R.id.dateEditor_firstDouble).setOnClickListener(view -> {
                firstDate.doubleDate = ((CompoundButton)view).isChecked();
                trueTextInput = false;
                generateDate();
            });
            findViewById(R.id.dateEditor_secondNegative).setOnClickListener(view -> {
                secondDate.negative = ((CompoundButton)view).isChecked();
                trueTextInput = false;
                generateDate();
            });
            findViewById(R.id.dateEditor_secondDouble).setOnClickListener(view -> {
                secondDate.doubleDate = ((CompoundButton)view).isChecked();
                trueTextInput = false;
                generateDate();
            });
            findViewById(R.id.dateEditor_approximate).setVisibility(GONE);
        } else {
            findViewById(R.id.dateEditor_approximate).setOnClickListener(view -> {
                findViewById(R.id.dateEditor_second).setVisibility(GONE); // In case it was visible because of BETWEEN_AND or FROM_TO
                dateConverter.kind = ((CompoundButton)view).isChecked() ? Kind.APPROXIMATE : Kind.EXACT;
                trueTextInput = false;
                generateDate();
            });
            findViewById(R.id.dateEditor_firstExpert).setVisibility(GONE);
        }

        initializeTrain(1, findViewById(R.id.dateEditor_firstDay), findViewById(R.id.dateEditor_firstMonth),
                findViewById(R.id.dateEditor_firstCentury), findViewById(R.id.dateEditor_firstYear));

        initializeTrain(2, findViewById(R.id.dateEditor_secondDay), findViewById(R.id.dateEditor_secondMonth),
                findViewById(R.id.dateEditor_secondCentury), findViewById(R.id.dateEditor_secondYear));

        // At first focus DateEditorLayout shows itself hiding the keyboard
        keyboard = (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        editText.setOnFocusChangeListener((view, getFocus) -> {
            if (getFocus) {
                if (dateConverter.kind == Kind.PHRASE) {
                    editText.setText(dateConverter.phrase); // To remove parentheses around the phrase
                } else {
                    keyboardVisible = keyboard.hideSoftInputFromWindow(editText.getWindowToken(), 0); // Hides keyboard
                    // Disables text input from keyboard
                    editText.setInputType(InputType.TYPE_NULL); // Necessary in recent versions of Android where the keyboard reappears
                }
                dateConverter.firstDate.date = null; // Just a reset
                setupDateEditor();
                setVisibility(View.VISIBLE);
            } else
                setVisibility(View.GONE);
        });

        // On the second tap brings up the keyboard
        editText.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS); // Re-enables the input
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                keyboardVisible = keyboard.showSoftInput(editText, 0); // Makes the keyboard reappear
            }
            return false;
        });
        // Sets the date editor based on what the user writes
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence text, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                // I don't know why but in Android 5 on first editing it is called 2 times, which isn't a problem anyway
                if (trueTextInput) setupDateEditor();
                trueTextInput = true;
            }
        });
    }

    /**
     * Prepares the four pickers of one train with the initial settings.
     *
     * @param train 1 is first, otherwise second train of pickers
     */
    void initializeTrain(int train, NumberPicker dayPicker, NumberPicker monthPicker, NumberPicker centuryPicker, NumberPicker yearPicker) {
        dayPicker.setMinValue(0);
        dayPicker.setMaxValue(31);
        dayPicker.setDisplayedValues(days);
        preparePicker(dayPicker);
        dayPicker.setOnValueChangedListener((picker, old, neu) ->
                updateDate(train == 1 ? firstDate : secondDate, dayPicker, monthPicker, centuryPicker, yearPicker)
        );
        monthPicker.setMinValue(0);
        monthPicker.setMaxValue(12);
        monthPicker.setDisplayedValues(months);
        preparePicker(monthPicker);
        monthPicker.setOnValueChangedListener((picker, old, neu) ->
                updateDate(train == 1 ? firstDate : secondDate, dayPicker, monthPicker, centuryPicker, yearPicker)
        );
        centuryPicker.setMinValue(0);
        centuryPicker.setMaxValue(20);
        preparePicker(centuryPicker);
        centuryPicker.setOnValueChangedListener((picker, old, neu) ->
                updateDate(train == 1 ? firstDate : secondDate, dayPicker, monthPicker, centuryPicker, yearPicker)
        );
        yearPicker.setMinValue(0);
        yearPicker.setMaxValue(100);
        yearPicker.setDisplayedValues(years);
        preparePicker(yearPicker);
        yearPicker.setOnValueChangedListener((picker, old, neu) ->
                updateDate(train == 1 ? firstDate : secondDate, dayPicker, monthPicker, centuryPicker, yearPicker)
        );
    }

    void preparePicker(NumberPicker picker) {
        // Removes the dividing blue lines on API <= 22
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                Field field = NumberPicker.class.getDeclaredField("mSelectionDivider");
                field.setAccessible(true);
                field.set(picker, null);
            } catch (Exception ignored) {
            }
        }
        // Fixes the bug https://issuetracker.google.com/issues/37055335
        picker.setSaveFromParentEnabled(false);
    }

    /**
     * Takes the date string, updates the Dates and modifies the entire date editor.
     * Called when I click on the editable field, and after any text editing.
     */
    void setupDateEditor() {
        dateConverter.analyze(editText.getText().toString());
        ((CheckBox)findViewById(R.id.dateEditor_approximate)).setChecked(dateConverter.kind == Kind.APPROXIMATE);
        ((TextView)findViewById(R.id.dateEditor_kinds)).setText(dateKinds[dateConverter.kind.ordinal()]);
        // First train
        setupTrain(firstDate, findViewById(R.id.dateEditor_firstDay), findViewById(R.id.dateEditor_firstMonth),
                findViewById(R.id.dateEditor_firstCentury), findViewById(R.id.dateEditor_firstYear));
        if (Global.settings.expert)
            setupCheckboxes(firstDate);
        // Second train
        if (dateConverter.kind == Kind.BETWEEN_AND || dateConverter.kind == Kind.FROM_TO) {
            setupTrain(secondDate, findViewById(R.id.dateEditor_secondDay), findViewById(R.id.dateEditor_secondMonth),
                    findViewById(R.id.dateEditor_secondCentury), findViewById(R.id.dateEditor_secondYear));
            if (Global.settings.expert) {
                findViewById(R.id.dateEditor_secondExpert).setVisibility(VISIBLE);
                setupCheckboxes(secondDate);
            }
            findViewById(R.id.dateEditor_second).setVisibility(VISIBLE);
        } else {
            findViewById(R.id.dateEditor_secondExpert).setVisibility(GONE);
            findViewById(R.id.dateEditor_second).setVisibility(GONE);
        }
    }

    /**
     * Turns the number pickers of one train based on a date.
     */
    void setupTrain(GedcomDateConverter.SingleDate date, NumberPicker dayPicker, NumberPicker monthPicker, NumberPicker centuryPicker, NumberPicker yearPicker) {
        calendar.clear();
        if (date.date != null)
            calendar.setTime(date.date);
        dayPicker.setMaxValue(calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        if (date.date != null && (date.isFormat(Format.D_M_Y) || date.isFormat(Format.D_M)))
            dayPicker.setValue(date.date.getDate());
        else
            dayPicker.setValue(0);
        if (date.date == null || date.isFormat(Format.Y))
            monthPicker.setValue(0);
        else
            monthPicker.setValue(date.date.getMonth() + 1);
        if (date.date == null || date.isFormat(Format.D_M))
            centuryPicker.setValue(0);
        else
            centuryPicker.setValue((date.date.getYear() + 1900) / 100);
        if (date.date == null || date.isFormat(Format.D_M))
            yearPicker.setValue(100);
        else
            yearPicker.setValue((date.date.getYear() + 1900) % 100);
    }

    /**
     * Sets the checkboxes for a date which can be negative and double.
     */
    void setupCheckboxes(GedcomDateConverter.SingleDate date) {
        CheckBox negativeCheckbox, doubleCheckbox;
        if (date.equals(firstDate)) {
            negativeCheckbox = findViewById(R.id.dateEditor_firstNegative);
            doubleCheckbox = findViewById(R.id.dateEditor_firstDouble);
        } else {
            negativeCheckbox = findViewById(R.id.dateEditor_secondNegative);
            doubleCheckbox = findViewById(R.id.dateEditor_secondDouble);
        }
        if (date.date == null || date.isFormat(Format.OTHER) || date.isFormat(Format.D_M)) { // Date without year
            negativeCheckbox.setVisibility(INVISIBLE);
            doubleCheckbox.setVisibility(INVISIBLE);
        } else {
            negativeCheckbox.setChecked(date.negative);
            negativeCheckbox.setVisibility(VISIBLE);
            doubleCheckbox.setChecked(date.doubleDate);
            doubleCheckbox.setVisibility(VISIBLE);
        }
    }

    /**
     * Updates a SingleDate with the values taken from a train of pickers.
     */
    void updateDate(GedcomDateConverter.SingleDate date, NumberPicker dayPicker, NumberPicker monthPicker, NumberPicker centuryPicker, NumberPicker yearPicker) {
        if (keyboardVisible) { // Hides any visible keyboard
            // Immediately hides the keyboard, but needs a second try to return false. However it's not a problem.
            keyboardVisible = keyboard.hideSoftInputFromWindow(editText.getWindowToken(), 0);
        }
        int day = dayPicker.getValue();
        int month = monthPicker.getValue();
        int century = centuryPicker.getValue();
        int year = yearPicker.getValue();
        // Sets the days of the month in dayPicker
        calendar.set(century * 100 + year, month - 1, 1);
        dayPicker.setMaxValue(calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        if (date.date == null) date.date = new Date();
        date.date.setDate(day == 0 ? 1 : day); // Otherwise the M_Y date moves back by one month
        date.date.setMonth(month == 0 ? 0 : month - 1);
        date.date.setYear(year == 100 ? -1899 : century * 100 + year - 1900);
        if (day != 0 && month != 0 && year != 100)
            date.format.applyPattern(Format.D_M_Y);
        else if (day != 0 && month != 0)
            date.format.applyPattern(Format.D_M);
        else if (month != 0 && year != 100)
            date.format.applyPattern(Format.M_Y);
        else if (year != 100)
            date.format.applyPattern(Format.Y);
        else
            date.format.applyPattern(Format.OTHER);
        setupCheckboxes(date);
        trueTextInput = false;
        generateDate();
    }

    /**
     * Rewrites the string with the final date and puts it in editText.
     */
    void generateDate() {
        String rewritten;
        if (dateConverter.kind == Kind.EXACT)
            rewritten = writeDate(firstDate);
        else if (dateConverter.kind == Kind.BETWEEN_AND)
            rewritten = "BET " + writeDate(firstDate) + " AND " + writeDate(secondDate);
        else if (dateConverter.kind == Kind.FROM_TO)
            rewritten = "FROM " + writeDate(firstDate) + " TO " + writeDate(secondDate);
        else if (dateConverter.kind == Kind.PHRASE) {
            // The phrase is replaced by an exact date
            dateConverter.kind = Kind.EXACT;
            ((TextView)findViewById(R.id.dateEditor_kinds)).setText(dateKinds[0]);
            rewritten = writeDate(firstDate);
        } else
            rewritten = dateConverter.kind.prefix + " " + writeDate(firstDate);
        editText.setText(rewritten);
    }

    /**
     * Writes a single date according to the format.
     */
    String writeDate(GedcomDateConverter.SingleDate date) {
        String text = "";
        if (date.date != null) {
            // Date with a double year
            if (date.doubleDate && !(date.isFormat(Format.OTHER) || date.isFormat(Format.D_M))) {
                Date secondDate = new Date();
                secondDate.setYear(date.date.getYear() + 1);
                String secondYear = String.format(Locale.ENGLISH, "%tY", secondDate);
                text = date.format.format(date.date) + "/" + secondYear.substring(2);
            } else // Other normal date
                text = date.format.format(date.date);
        }
        if (date.negative)
            text += " B.C.";
        return text;
    }

    /**
     * If the date is a phrase adds parentheses around it.
     */
    public void finishEditing() {
        if (dateConverter.kind == Kind.PHRASE) {
            String text = editText.getText().toString().replaceAll("[()]", "").trim();
            editText.setText("(" + text + ")");
        }
    }

    String s(int id) {
        return Global.context.getString(id);
    }
}
