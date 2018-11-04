package app.familygem;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Spinner;
import java.lang.reflect.Field;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class EditoreData extends LinearLayout {

	Data data1;
	Data data2;
	String frase; // data frase
	int tipo;
	EditText editaTesto;
	String[] giorniRuota = { "-","1","2","3","4","5","6","7","8","9","10","11","12","13","14","15",
			"16","17","18","19","20","21","22","23","24","25","26","27","28","29","30","31" };
	String[] mesiRuota = { "-", s(R.string.january), s(R.string.february), s(R.string.march), s(R.string.april), s(R.string.may), s(R.string.june),
			s(R.string.july), s(R.string.august), s(R.string.september), s(R.string.october), s(R.string.november), s(R.string.december) };
	String[] anniRuota = { "","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","",
			"","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","",
			"","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","","-"	};
	String[] paterni = { "d MMM yyy", "d M yyy", "MMM yyy", "M yyy", "d MMM", "yyy" };
	String G_M_A = paterni[0];
	String G_m_A = paterni[1];
	String M_A = paterni[2];
	String m_A = paterni[3];
	String G_M = paterni[4];
	String A = paterni[5];
	String[] mesiGedcom = { "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC" };
	String[] prefissi = { "", "ABT", "CAL", "EST", "AFT", "BEF", "BET",	"FROM", "TO", "FROM", "(" };
	Calendar calenda = GregorianCalendar.getInstance();
	boolean veroImputTesto; // stabilisce se l'utente sta effettivamente digitando sulla tastiera virtuale o se il testo viene cambiato in altro modo
	boolean abilitaSpinner; // il tipo è scelto cliccando sullo spinner o scrivendo il testo

	class Data {
		Date date;
		SimpleDateFormat format;
	}

	public EditoreData( Context contesto, AttributeSet as ) {
		super( contesto, as );
	}

	// Azioni da fare una sola volta all'inizio
	@SuppressLint("ClickableViewAccessibility")
	void inizia( final EditText editaTesto ) {    //, final LinearLayout vistaEditaData, String dataGc

		addView( inflate( getContext(), R.layout.editore_data, null ), this.getLayoutParams() );
		veroImputTesto = false;
		this.editaTesto = editaTesto;

		for( int i = 0; i < anniRuota.length-1; i++ )
			anniRuota[i] = i<10 ? "0"+i : ""+i ;

		// Arreda l'editore data
		Spinner elencoTipi = findViewById(R.id.editadata_tipi);
		String[] tipiData = { s(R.string.exact), s(R.string.approximate), s(R.string.calculated), s(R.string.estimated),
				s(R.string.after), s(R.string.before), s(R.string.between),
				s(R.string.from), s(R.string.to), s(R.string.from_to), s(R.string.date_phrase) };
		ArrayAdapter<String> adattatore = new ArrayAdapter<>( Globale.contesto, R.layout.elemento_spinner, tipiData );
		elencoTipi.setAdapter( adattatore );
		elencoTipi.setOnItemSelectedListener( new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected( AdapterView<?> pv, View v, int posizione, long id ) {
				//s.l( "abilitaSpinner  " + abilitaSpinner );
				if( abilitaSpinner ) {
					tipo = posizione;
					if( tipo == 10 ) {
						frase = editaTesto.getText().toString();
						findViewById( R.id.editadata_prima ).setVisibility( View.GONE );
					} else {
						if( data1 == null || data1.date == null )
							data1 = scanna( "" );
						findViewById( R.id.editadata_prima ).setVisibility( View.VISIBLE );
					}
					if( tipo == 6 || tipo == 9 ) {
						if( data2 == null )
							data2 = scanna( "" );
						findViewById( R.id.editadata_seconda ).setVisibility( View.VISIBLE );
					} else
						findViewById(R.id.editadata_seconda).setVisibility( View.GONE );
					veroImputTesto = false;
					genera( false );
				}
				abilitaSpinner = true;
			}
			@Override
			public void onNothingSelected( AdapterView<?> pv ) {}
		});

		arredaCarro( 1, (NumberPicker)findViewById( R.id.prima_giorno ),
				(NumberPicker)findViewById( R.id.prima_mese ),
				(NumberPicker)findViewById( R.id.prima_secolo ),
				(NumberPicker)findViewById( R.id.prima_anno ) );

		arredaCarro( 2, (NumberPicker)findViewById( R.id.seconda_giorno ),
				(NumberPicker)findViewById( R.id.seconda_mese ),
				(NumberPicker)findViewById( R.id.seconda_secolo ),
				(NumberPicker)findViewById( R.id.seconda_anno ) );

		// Al primo focus mostra sè stesso (EditoreData) nascondendo la tastiera
		final InputMethodManager imm = (InputMethodManager) getContext().getSystemService( Context.INPUT_METHOD_SERVICE );
		editaTesto.setOnFocusChangeListener( new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange( View v, boolean ciapa ) {
				//s.l( "editaTesto onFocusChange " + ciapa );
				if( ciapa ) {
					if( tipo == 10 )
						genera( false ); // solo per togliere le parentesi alla frase
					else {
						imm.hideSoftInputFromWindow( editaTesto.getWindowToken(), 0 ); // ok nasconde tastiera
						/*Window finestra = ((Activity)getContext()).getWindow(); non aiuta la scomparsa della tastiera
						finestra.setSoftInputMode( WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN );*/
						editaTesto.setInputType( InputType.TYPE_NULL ); // disabilita input testo con tastiera
							// necessario in versioni recenti di android in cui la tastiera ricompare
					}
					impostaTutto();
					setVisibility( View.VISIBLE );
				} else
					setVisibility( View.GONE );
			}
		} );

		// Al secondo tocco fa comparire la tastiera
		editaTesto.setOnTouchListener( new OnTouchListener() {
			@Override
			public boolean onTouch( View vista, MotionEvent event ) {
				//s.l("onTouch " + event.getAction() );
				if( event.getAction() == MotionEvent.ACTION_DOWN ) {
					editaTesto.setInputType( InputType.TYPE_CLASS_TEXT ); // riabilita l'input
				} else if( event.getAction() == MotionEvent.ACTION_UP ) {
					imm.showSoftInput( editaTesto, 0 ); // fa ricomparire la tastiera
					veroImputTesto = true;
					//vista.performClick(); non ne vedo l'utilità
				}
				return false;
			}
		} );
		/* Abbandonato in favore di OnTouchListener
		editaTesto.setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View v ) {
				s.l( "editaTesto onClick");
				imm.showSoftInput( editaTesto, 0 );
				editaTesto.setInputType( InputType.TYPE_CLASS_TEXT ); // riabilita l'input ma il cursore non compare o non blinka
				//editaTesto.requestFocus(); // non cambia niente
				//editaTesto.setRawInputType( InputType.TYPE_CLASS_TEXT ); idem
				editaTesto.setCursorVisible( true ); // forse aiuta a far comparire il cursore, anche se non blinka
			}
		});*/
		// Imposta l'editore data in base a quanto scritto
		editaTesto.addTextChangedListener( new TextWatcher() {
			@Override
			public void beforeTextChanged( CharSequence testo, int i, int i1, int i2 ) {}
			@Override
			public void onTextChanged( CharSequence testo, int i, int i1, int i2 ) {}
			@Override
			public void afterTextChanged( Editable testo ) {
				// non so perché ma in android 5 alla prima editazione viene chiamato 2 volte, che comunque non è un problema
				//s.l( "veroImputTesto " + veroImputTesto +"  " + testo );
				if( veroImputTesto ) {
					impostaTutto();
				}
				veroImputTesto = true;
			}
		} );
	}

	// Prepara le quattro ruote di un carro con le impostazioni iniziali
	void arredaCarro( final int quale, final NumberPicker ruotaGiorno, final NumberPicker ruotaMese, final NumberPicker ruotaSecolo, final NumberPicker ruotaAnno ) {
		ruotaGiorno.setMinValue(0);
		ruotaGiorno.setMaxValue(31);
		ruotaGiorno.setDisplayedValues( giorniRuota );
		stilizza(ruotaGiorno);
		ruotaGiorno.setOnValueChangedListener( new NumberPicker.OnValueChangeListener() {
			@Override
			public void onValueChange( NumberPicker picker, int vecchio, int nuovo ) {
				aggiorna( quale==1?data1:data2, ruotaGiorno, ruotaMese, ruotaSecolo, ruotaAnno );
			}
		});
		ruotaMese.setMinValue(0);
		ruotaMese.setMaxValue(12);
		ruotaMese.setDisplayedValues( mesiRuota );
		stilizza(ruotaMese);
		ruotaMese.setOnValueChangedListener( new NumberPicker.OnValueChangeListener() {
			@Override
			public void onValueChange( NumberPicker picker, int vecchio, int nuovo ) {
				aggiorna( quale==1?data1:data2, ruotaGiorno, ruotaMese, ruotaSecolo, ruotaAnno );
			}
		});
		ruotaSecolo.setMinValue(0);
		ruotaSecolo.setMaxValue(20);
		stilizza(ruotaSecolo);
		ruotaSecolo.setOnValueChangedListener( new NumberPicker.OnValueChangeListener() {
			@Override
			public void onValueChange( NumberPicker picker, int vecchio, int nuovo ) {
				aggiorna( quale==1?data1:data2, ruotaGiorno, ruotaMese, ruotaSecolo, ruotaAnno );
			}
		});
		ruotaAnno.setMinValue(0);
		ruotaAnno.setMaxValue(100);
		ruotaAnno.setDisplayedValues( anniRuota );
		stilizza(ruotaAnno);
		ruotaAnno.setOnValueChangedListener( new NumberPicker.OnValueChangeListener() {
			@Override
			public void onValueChange( NumberPicker picker, int vecchio, int nuovo ) {
				aggiorna( quale==1?data1:data2, ruotaGiorno, ruotaMese, ruotaSecolo, ruotaAnno );
			}
		});
	}

	// Toglie le famigerate linee divisorie azzurre
	void stilizza( NumberPicker ruota ) {
		try {
			Field campo = NumberPicker.class.getDeclaredField( "mSelectionDivider" );
			campo.setAccessible( true );
			campo.set( ruota, null );
		} catch( Exception e ) {
			e.printStackTrace();
		}
	}

	// Prende la stringa data, aggiorna le Date e ci modifica tutto l'editore data
	// Chiamato quando clicco sul campo editabile, e dopo ogni editazione del testo
	void impostaTutto() {
		data1 = null;
		data2 = null;
		frase = null;
		String dataGc = editaTesto.getText().toString().trim();

		// Riconosce i tipi con prefisso e converte la stringa in Data
		for( int t = 1; t < prefissi.length; t++  ) {
			if( dataGc.startsWith(prefissi[t]) ) {
				tipo = t;
				if( t == 6 && dataGc.contains("AND") ) { // BET... AND
					data1 = scanna( dataGc.substring( 4, dataGc.indexOf("AND")-1 ));
					if( dataGc.length() > dataGc.indexOf("AND")+3 )
						data2 = scanna( dataGc.substring( dataGc.indexOf("AND")+4 ));
				} else if( t == 7 && dataGc.contains("TO") ) { // FROM... TO
					tipo = 9;
					data1 = scanna( dataGc.substring( 5, dataGc.indexOf("TO")-1 ));
					if( dataGc.length() > dataGc.indexOf("TO")+2 )
						data2 = scanna( dataGc.substring( dataGc.indexOf("TO")+3 ) );
				} else if( t == 10 ) { // Data frase
					data1 = scanna( "" );
					if( dataGc.endsWith(")") )
						frase = dataGc.substring( 1, dataGc.indexOf(")") );
					else
						frase = dataGc;
				} else if( dataGc.length() > prefissi[t].length() ) // Altri prefissi seguiti da qualcosa
					data1 = scanna( dataGc.substring( prefissi[t].length() + 1 ) );
				else
					data1 = scanna( "" ); // Prefisso e basta
				break;
			}
		}
		// Rimane da provare il tipo 0
		if( data1 == null ) {
			data1 = scanna( dataGc );
			tipo = 0;
		}

		// La data non è stata interpretata e non è una frase tra parentesi
		if( data1.date == null && tipo != 10 ) {
			//data1 = scanna( "" );
			frase = dataGc;
			tipo = 10;
		}

		abilitaSpinner = false;
		((Spinner)findViewById(R.id.editadata_tipi)).setSelection( tipo );
			// Il settaggio dello spinner non avviene istantaneamente, ma solo dopo che è finito impostaTutto

		// Primo carro
		if( tipo == 10 ) // Data frase
			findViewById( R.id.editadata_prima ).setVisibility( GONE );
		else {
			impostaCarro( data1, (NumberPicker)findViewById( R.id.prima_giorno ),
					(NumberPicker)findViewById( R.id.prima_mese ),
					(NumberPicker)findViewById( R.id.prima_secolo ),
					(NumberPicker)findViewById( R.id.prima_anno ) );
			findViewById( R.id.editadata_prima ).setVisibility( VISIBLE );
		}
		// Secondo carro
		if( tipo == 6 || tipo == 9 ) {
			if( data2 == null || data2.date == null )
				data2 = scanna( "" );
			impostaCarro( data2, (NumberPicker)findViewById( R.id.seconda_giorno ),
					(NumberPicker)findViewById( R.id.seconda_mese ),
					(NumberPicker)findViewById( R.id.seconda_secolo ),
					(NumberPicker)findViewById( R.id.seconda_anno ) );
			findViewById( R.id.editadata_seconda ).setVisibility( VISIBLE );
		} else
			findViewById( R.id.editadata_seconda ).setVisibility( GONE );
	}

	// Prende una stringa e ci farcisce la mia classe Data
	Data scanna( String dataGc ) {
		DateFormatSymbols simboliFormato = new DateFormatSymbols();
		simboliFormato.setShortMonths( mesiGedcom );
		dataGc = dataGc.replaceAll("[/\\\\_\\-|.,;:?'\"#^&*°+=~()\\[\\]{}]", " ");
		Data data = new Data();
		for( String forma : paterni ) {
			data.format = new SimpleDateFormat( forma, simboliFormato );
			try {
				if( dataGc.isEmpty() ) {
					data.date = data.format.parse( "1 JAN 1900" );
					data.format.applyPattern( "" );
					return data;
				} else
					data.date = data.format.parse( dataGc );
				break;
			} catch( ParseException e ) {
				//s.l( "'"+ forma + "' non ha funzionato. " + e.getLocalizedMessage() );
			}
		}
		if( data.format.toPattern().equals(G_m_A) )
			data.format.applyPattern( G_M_A );
		if( data.format.toPattern().equals(m_A) )
			data.format.applyPattern( M_A );
		return data;
	}

	// Gira le ruote di un carro in base a una data
	void impostaCarro( Data data, NumberPicker ruotaGiorno, NumberPicker ruotaMese, NumberPicker ruotaSecolo, NumberPicker ruotaAnno ) {
		calenda.clear();
		calenda.set( data.date.getYear()+1900, data.date.getMonth(), data.date.getDate() );
		ruotaGiorno.setMaxValue( calenda.getActualMaximum(Calendar.DAY_OF_MONTH) );
		if( data.format.toPattern().equals(G_M_A) || data.format.toPattern().equals(G_M) )
			ruotaGiorno.setValue( data.date.getDate() );
		else
			ruotaGiorno.setValue( 0 );
		if( data.format.toPattern().equals(A) || data.format.toPattern().equals("") )
			ruotaMese.setValue( 0 );
		else
			ruotaMese.setValue( data.date.getMonth() + 1 );
		if( data.format.toPattern().equals(G_M) || data.format.toPattern().equals("") )
			ruotaSecolo.setValue( 0 );
		else
			ruotaSecolo.setValue( (data.date.getYear()+1900)/100 );
		if( data.format.toPattern().equals(G_M) || data.format.toPattern().equals("") )
			ruotaAnno.setValue( 100 );
		else
			ruotaAnno.setValue( (data.date.getYear()+1900)%100 );
	}

	// Aggiorna una Data coi nuovi valori presi dalle ruote
	void aggiorna( Data data, NumberPicker ruotaGiorno, NumberPicker ruotaMese, NumberPicker ruotaSecolo, NumberPicker ruotaAnno ) {
		int giorno = ruotaGiorno.getValue();
		int mese = ruotaMese.getValue();
		int secolo = ruotaSecolo.getValue();
		int anno = ruotaAnno.getValue();
		// Imposta i giorni del mese in ruotaGiorno
		calenda.set( secolo*100+anno, mese-1, 1 );
		ruotaGiorno.setMaxValue( calenda.getActualMaximum(Calendar.DAY_OF_MONTH) );
		data.date.setDate( giorno == 0 ? 1 : giorno );  // così non attribuisce strani valori a caso al giorno 0
		data.date.setMonth( mese - 1 );
		data.date.setYear( secolo*100 + anno - 1900 );
		if( giorno != 0 && mese != 0 && anno != 100 )
			data.format.applyPattern( G_M_A );
		else if( giorno != 0 && mese != 0 )
			data.format.applyPattern( G_M );
		else if( mese != 0 && anno != 100 )
			data.format.applyPattern( M_A );
		else if( anno != 100 )
			data.format.applyPattern( A );
		else
			data.format.applyPattern( "" );
		veroImputTesto = false;
		genera( false );
	}

	// Ricostruisce la stringa con la data finale e la mette in editaTesto
	void genera( boolean appenaPrimaDiSalvare ) {
		String rifatta;
		if( tipo == 0 )
			rifatta = data1.format.format( data1.date );
		else if( tipo == 6 )
			rifatta = "BET " + data1.format.format( data1.date ) + " AND " + data2.format.format( data2.date );
		else if( tipo == 9 )
			rifatta = "FROM " + data1.format.format( data1.date ) + " TO " + data2.format.format( data2.date );
		else if( tipo == 10 ) {
			if( appenaPrimaDiSalvare )
				rifatta = "(" + editaTesto.getText() + ")"; // mette le parentesi intorno a una data frase
			else
				rifatta = frase;
		} else
			rifatta = prefissi[tipo] + " " + data1.format.format( data1.date );

		editaTesto.setText( rifatta );
	}

	String s( int id ) {
		return Globale.contesto.getString( id );
	}
}