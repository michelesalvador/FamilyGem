package app.familygem;

import android.content.Context;
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

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import app.familygem.constant.Format;
import app.familygem.constant.Kind;

public class PublisherDateLinearLayout extends LinearLayout {

	GedcomDateConverter gedcomDateConverter;
	GedcomDateConverter.Data data1;
	GedcomDateConverter.Data data2;
	EditText editText;
	String[] daysWheel = { "-","1","2","3","4","5","6","7","8","9","10","11","12","13","14","15",
			"16","17","18","19","20","21","22","23","24","25","26","27","28","29","30","31" };
	String[] monthsWheel = { "-", s(R.string.january), s(R.string.february), s(R.string.march), s(R.string.april), s(R.string.may), s(R.string.june),
			s(R.string.july), s(R.string.august), s(R.string.september), s(R.string.october), s(R.string.november), s(R.string.december) };
	String[] yearsWheel = new String[101];
	int[] dateKinds = { R.string.exact, R.string.approximate, R.string.calculated, R.string.estimated,
			R.string.after, R.string.before, R.string.between_and,
			R.string.from, R.string.to, R.string.from_to, R.string.date_phrase };
	Calendar calendar = GregorianCalendar.getInstance();
	boolean userIsTyping; // determines if the user is actually typing on the virtual keyboard or if the text is changed in some other way	InputMethodManager tastiera;
	InputMethodManager keyboard;
	boolean keyboardIsVisible;

	public PublisherDateLinearLayout(Context context, AttributeSet as ) {
		super( context, as );
	}

	/**
	 * Actions to be done only once at the beginning
	 * */
	void initialize(final EditText editText ) {

		addView( inflate( getContext(), R.layout.editore_data, null ), this.getLayoutParams() );
		this.editText = editText;

		for(int i = 0; i < yearsWheel.length - 1; i++ )
			yearsWheel[i] = i < 10 ? "0" + i : "" + i;
		yearsWheel[100] = "-";

		gedcomDateConverter = new GedcomDateConverter( editText.getText().toString() );
		data1 = gedcomDateConverter.data1;
		data2 = gedcomDateConverter.data2;

		// Setup the date editor
		if( Global.settings.expert ) {
			final TextView listTypes = findViewById(R.id.editadata_tipi);
			listTypes.setOnClickListener( vista -> {
				PopupMenu popup = new PopupMenu(getContext(), vista);
				Menu menu = popup.getMenu();
				for( int i = 0; i < dateKinds.length - 1; i++ )
					menu.add(0, i, 0, dateKinds[i]);
				popup.show();
				popup.setOnMenuItemClickListener(item -> {
					gedcomDateConverter.kind = Kind.values()[item.getItemId()];
					// If possibly invisible
					findViewById(R.id.editadata_prima).setVisibility(View.VISIBLE);
					if( data1.date == null ) // wagon micro setting (??)
						((NumberPicker)findViewById(R.id.prima_anno)).setValue(100);
					if( gedcomDateConverter.kind == Kind.BETWEEN_AND || gedcomDateConverter.kind == Kind.FROM_TO ) {
						findViewById(R.id.editadata_seconda_avanzate).setVisibility(VISIBLE);
						findViewById(R.id.editadata_seconda).setVisibility(VISIBLE);
						if( data2.date == null )
							((NumberPicker)findViewById(R.id.seconda_anno)).setValue(100);
					} else {
						findViewById(R.id.editadata_seconda_avanzate).setVisibility(GONE);
						findViewById(R.id.editadata_seconda).setVisibility(GONE);
					}
					listTypes.setText(dateKinds[item.getItemId()]);
					userIsTyping = false;
					generate();
					return true;
				});
			});
			findViewById(R.id.editadata_negativa1).setOnClickListener(vista -> {
				data1.negative = ((CompoundButton)vista).isChecked();
				userIsTyping = false;
				generate();
			});
			findViewById(R.id.editadata_doppia1).setOnClickListener(vista -> {
				data1.doubleYear = ((CompoundButton)vista).isChecked();
				userIsTyping = false;
				generate();
			});
			findViewById(R.id.editadata_negativa2).setOnClickListener(vista -> {
				data2.negative = ((CompoundButton)vista).isChecked();
				userIsTyping = false;
				generate();
			});
			findViewById(R.id.editadata_doppia2).setOnClickListener(vista -> {
				data2.doubleYear = ((CompoundButton)vista).isChecked();
				userIsTyping = false;
				generate();
			});
			findViewById(R.id.editadata_circa).setVisibility(GONE);
		} else {
			findViewById(R.id.editadata_circa).setOnClickListener(vista -> {
				findViewById(R.id.editadata_seconda).setVisibility(GONE); // casomai fosse visibile per tipi 6 o 9
				gedcomDateConverter.kind = ((CompoundButton)vista).isChecked() ? Kind.APPROXIMATE : Kind.EXACT;
				userIsTyping = false;
				generate();
			});
			findViewById(R.id.editadata_avanzate).setVisibility(GONE);
		}

		setupWagon(1, findViewById(R.id.prima_giorno), findViewById(R.id.prima_mese),
				findViewById(R.id.prima_secolo), findViewById(R.id.prima_anno));

		setupWagon(2, findViewById(R.id.seconda_giorno), findViewById(R.id.seconda_mese),
				findViewById(R.id.seconda_secolo), findViewById(R.id.seconda_anno));

		// At first focus it shows itself (EditoreData) hiding the keyboard
		keyboard = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		editText.setOnFocusChangeListener((v, hasFocus) -> {
			if( hasFocus ) {
				if( gedcomDateConverter.kind == Kind.PHRASE ) {
					//genera(); // Remove the parentheses from the sentence
					editText.setText(gedcomDateConverter.phrase);
				} else {
					keyboardIsVisible = keyboard.hideSoftInputFromWindow( editText.getWindowToken(), 0 ); // ok hide keyboard
					/*Window window = ((Activity)getContext()).getWindow(); it doesn't help that the keyboard disappears
					window.setSoftInputMode( WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN );*/
					editText.setInputType( InputType.TYPE_NULL ); // disable keyboard text input
						//needed in recent versions of android where the keyboard reappears
				}
					gedcomDateConverter.data1.date = null; // a reset
				setAll();
				setVisibility(View.VISIBLE);
			} else
				setVisibility(View.GONE);
		} );

		// The second touch brings up the keyboard
		editText.setOnTouchListener((view, event) -> {
			if( event.getAction() == MotionEvent.ACTION_DOWN ) {
				editText.setInputType(InputType.TYPE_CLASS_TEXT); // re-enable the input
			} else if( event.getAction() == MotionEvent.ACTION_UP ) {
				keyboardIsVisible = keyboard.showSoftInput(editText, 0); // makes the keyboard reappear
				//userIsTyping = true;
				//view.performClick(); non ne vedo l'utilità
			}
			return false;
		});
		// Set the date publisher based on what is written
		editText.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence testo, int i, int i1, int i2) {}
			@Override
			public void onTextChanged(CharSequence testo, int i, int i1, int i2) {}
			@Override
			public void afterTextChanged(Editable testo) {
				// i don't know why but in android 5 on first edition it is called 2 times which is not a problem anyway
				if(userIsTyping)
					setAll();
				userIsTyping = true;
			}
		});
	}

	/**
	 * Prepare the four wheels of a wagon with the initial settings
	 */
	void setupWagon(final int which, final NumberPicker dayWheel, final NumberPicker monthWheel, final NumberPicker centuryWheel, final NumberPicker yearWheel ) {
		dayWheel.setMinValue(0);
		dayWheel.setMaxValue(31);
		dayWheel.setDisplayedValues(daysWheel);
		stylize(dayWheel);
		dayWheel.setOnValueChangedListener( (picker, vecchio, nuovo) ->
				update( which == 1 ? data1 : data2, dayWheel, monthWheel, centuryWheel, yearWheel )
		);
		monthWheel.setMinValue(0);
		monthWheel.setMaxValue(12);
		monthWheel.setDisplayedValues(monthsWheel);
		stylize(monthWheel);
		monthWheel.setOnValueChangedListener( (picker, vecchio, nuovo) ->
				update( which == 1 ? data1 : data2, dayWheel, monthWheel, centuryWheel, yearWheel )
		);
		centuryWheel.setMinValue(0);
		centuryWheel.setMaxValue(20);
		stylize(centuryWheel);
		centuryWheel.setOnValueChangedListener( (picker, vecchio, nuovo) ->
				update( which == 1 ? data1 : data2, dayWheel, monthWheel, centuryWheel, yearWheel )
		);
		yearWheel.setMinValue(0);
		yearWheel.setMaxValue(100);
		yearWheel.setDisplayedValues(yearsWheel);
		stylize(yearWheel);
		yearWheel.setOnValueChangedListener( ( picker, vecchio, nuovo ) ->
				update( which == 1 ? data1 : data2, dayWheel, monthWheel, centuryWheel, yearWheel )
		);
	}

	void stylize(NumberPicker wheel) {
		wheel.setSaveFromParentEnabled(false);
	}

	/**
	 * Take the date string, update the Dates, and edit all of it in the date editor
	 * Called when I click on the editable field, and after each text edit
	 */
	void setAll() {
		gedcomDateConverter.analyze( editText.getText().toString() );
		((CheckBox)findViewById( R.id.editadata_circa )).setChecked( gedcomDateConverter.kind == Kind.APPROXIMATE );
		((TextView)findViewById( R.id.editadata_tipi )).setText( dateKinds[gedcomDateConverter.kind.ordinal()] );

		// First wagon
		setWagon( data1, findViewById( R.id.prima_giorno ), findViewById( R.id.prima_mese ),
				findViewById( R.id.prima_secolo ), findViewById( R.id.prima_anno ) );
		if( Global.settings.expert )
			setCheckboxes( data1 );

		// Second wagon
		if( gedcomDateConverter.kind == Kind.BETWEEN_AND || gedcomDateConverter.kind == Kind.FROM_TO ) {
			setWagon( data2, findViewById( R.id.seconda_giorno ), findViewById( R.id.seconda_mese ),
					findViewById( R.id.seconda_secolo ), findViewById( R.id.seconda_anno ) );
			if( Global.settings.expert ) {
				findViewById( R.id.editadata_seconda_avanzate ).setVisibility( VISIBLE );
				setCheckboxes( data2 );
			}
			findViewById( R.id.editadata_seconda ).setVisibility( VISIBLE );
		} else {
			findViewById( R.id.editadata_seconda_avanzate ).setVisibility( GONE );
			findViewById( R.id.editadata_seconda ).setVisibility( GONE );
		}
	}

	/**
	 * Spin the wheels of a wagon according to a date
	 */
	void setWagon(GedcomDateConverter.Data data, NumberPicker dayPicker, NumberPicker monthPicker, NumberPicker centuryPicker, NumberPicker yearPicker) {
		calendar.clear();
		if( data.date != null )
			calendar.setTime(data.date);
		dayPicker.setMaxValue(calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
		if( data.date != null && (data.isFormat(Format.D_M_Y) || data.isFormat(Format.D_M)) )
			dayPicker.setValue(data.date.getDate());
		else
			dayPicker.setValue(0);
		if( data.date == null || data.isFormat(Format.Y) )
			monthPicker.setValue(0);
		else
			monthPicker.setValue(data.date.getMonth() + 1);
		if( data.date == null || data.isFormat(Format.D_M) )
			centuryPicker.setValue(0);
		else
			centuryPicker.setValue((data.date.getYear() + 1900) / 100);
		if( data.date == null || data.isFormat(Format.D_M) )
			yearPicker.setValue(100);
		else
			yearPicker.setValue((data.date.getYear() + 1900) % 100);
	}

	/**
	 * Set the Checkboxes for a date which can be negative and double
	 */
	void setCheckboxes(GedcomDateConverter.Data data) {
		CheckBox checkboxBC, checkboxDouble;
		if( data.equals(data1) ) {
			checkboxBC = findViewById(R.id.editadata_negativa1);
			checkboxDouble = findViewById(R.id.editadata_doppia1);
		} else {
			checkboxBC = findViewById(R.id.editadata_negativa2);
			checkboxDouble = findViewById(R.id.editadata_doppia2);
		}
		if( data.date == null || data.isFormat(Format.EMPTY) || data.isFormat(Format.D_M) ) { // dates without year
			checkboxBC.setVisibility(INVISIBLE);
			checkboxDouble.setVisibility(INVISIBLE);
		} else {
			checkboxBC.setChecked(data.negative);
			checkboxBC.setVisibility(VISIBLE);
			checkboxDouble.setChecked(data.doubleYear);
			checkboxDouble.setVisibility(VISIBLE);
		}
	}

	/**
	 * Update a Date with the new values taken from the wheels
	 */
	void update(GedcomDateConverter.Data data, NumberPicker dayPicker, NumberPicker monthPicker, NumberPicker centuryPicker, NumberPicker yearPicker ) {
		if(keyboardIsVisible) {	// Hides any visible keyboard
			keyboardIsVisible = keyboard.hideSoftInputFromWindow( editText.getWindowToken(), 0 );
				// Hides the keyboard right away, but needs a second try to return false. It's not a problem anyway
		}
		int day = dayPicker.getValue();
		int month = monthPicker.getValue();
		int century = centuryPicker.getValue();
		int year = yearPicker.getValue();
		// Set the days of the month in dayWheel
		calendar.set( century*100+year, month-1, 1 );
		dayPicker.setMaxValue( calendar.getActualMaximum(Calendar.DAY_OF_MONTH) );
		if( data.date == null ) data.date = new Date();
		data.date.setDate( day == 0 ? 1 : day );  // otherwise the M_A date goes back one month
		data.date.setMonth( month == 0 ? 0 : month - 1 );
		data.date.setYear( year == 100 ? -1899 : century*100 + year - 1900 );
		if( day != 0 && month != 0 && year != 100 )
			data.format.applyPattern(Format.D_M_Y);
		else if( day != 0 && month != 0 )
			data.format.applyPattern(Format.D_M);
		else if( month != 0 && year != 100 )
			data.format.applyPattern(Format.M_Y);
		else if( year != 100 )
			data.format.applyPattern(Format.Y);
		else
			data.format.applyPattern(Format.EMPTY);
		setCheckboxes( data );
		userIsTyping = false;
		generate();
	}

	/**
	 * Rebuilds the string with the end date and puts it in {@link #editText}
	 */
	void generate() {
		String redone;
		if( gedcomDateConverter.kind == Kind.EXACT )
			redone = redo(data1);
		else if( gedcomDateConverter.kind == Kind.BETWEEN_AND )
			redone = "BET " + redo(data1) + " AND " + redo(data2);
		else if( gedcomDateConverter.kind == Kind.FROM_TO )
			redone = "FROM " + redo(data1) + " TO " + redo(data2);
		else if( gedcomDateConverter.kind == Kind.PHRASE ) {
			// The phrase is replaced by the exact date
			gedcomDateConverter.kind = Kind.EXACT;
			((TextView)findViewById(R.id.editadata_tipi)).setText(dateKinds[0]);
			redone = redo(data1);
		} else
			redone = gedcomDateConverter.kind.prefix + " " + redo(data1);
		editText.setText(redone);
	}

	/**
	 * Writes the single date according to the format
	 * */
	String redo(GedcomDateConverter.Data data ) {
		String done = "";
		if( data.date != null ) {
			// Dates with double year
			if( data.doubleYear && !(data.isFormat(Format.EMPTY) || data.isFormat(Format.D_M)) ) {
				Date aYearLater = new Date();
				aYearLater.setYear( data.date.getYear() + 1 );
				String secondoAnno = String.format( Locale.ENGLISH, "%tY", aYearLater );
				done = data.format.format( data.date ) +"/"+ secondoAnno.substring( 2 );
			} else // The other normal dates
				done = data.format.format( data.date );
		}
		if( data.negative)
			done += " B.C.";
		return done;
	}

	/**
	 * Called from outside essentially just to add parentheses to the given sentence
	 * */
	void encloseInParentheses() {
		if( gedcomDateConverter.kind == Kind.PHRASE ) {
			editText.setText("(" + editText.getText() + ")");
		}
	}

	String s(int id) {
		return Global.context.getString(id);
	}
}