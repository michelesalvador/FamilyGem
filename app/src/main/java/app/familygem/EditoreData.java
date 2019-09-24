package app.familygem;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuItem;
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
import static app.familygem.Datatore.*;

public class EditoreData extends LinearLayout {

	Datatore datatore;
	Datatore.Data data1;
	Datatore.Data data2;
	EditText editaTesto;
	String[] giorniRuota = { "-","1","2","3","4","5","6","7","8","9","10","11","12","13","14","15",
			"16","17","18","19","20","21","22","23","24","25","26","27","28","29","30","31" };
	String[] mesiRuota = { "-", s(R.string.january), s(R.string.february), s(R.string.march), s(R.string.april), s(R.string.may), s(R.string.june),
			s(R.string.july), s(R.string.august), s(R.string.september), s(R.string.october), s(R.string.november), s(R.string.december) };
	String[] anniRuota = new String[101];
	int[] tipiData = { R.string.exact, R.string.approximate, R.string.calculated, R.string.estimated,
			R.string.after, R.string.before, R.string.between,
			R.string.from, R.string.to, R.string.from_to, R.string.date_phrase };
	Calendar calenda = GregorianCalendar.getInstance();
	boolean veroImputTesto; // stabilisce se l'utente sta effettivamente digitando sulla tastiera virtuale o se il testo viene cambiato in altro modo
	InputMethodManager tastiera;
	boolean tastieraVisibile;

	public EditoreData( Context contesto, AttributeSet as ) {
		super( contesto, as );
	}

	// Azioni da fare una sola volta all'inizio
	//@SuppressLint("ClickableViewAccessibility")
	void inizia( final EditText editaTesto ) {	//, final LinearLayout vistaEditaData, String dataGc

		addView( inflate( getContext(), R.layout.editore_data, null ), this.getLayoutParams() );
		this.editaTesto = editaTesto;

		for( int i = 0; i < anniRuota.length-1; i++ )
			anniRuota[i] = i<10 ? "0"+i : ""+i;
		anniRuota[100] = "-";

		datatore = new Datatore( editaTesto.getText().toString() );
		data1 = datatore.data1;
		data2 = datatore.data2;

		// Arreda l'editore data
		if( Globale.preferenze.esperto ) {
			final TextView elencoTipi = findViewById(R.id.editadata_tipi);
			elencoTipi.setOnClickListener( new View.OnClickListener() {
				@Override
				public void onClick( View vista ) {
					PopupMenu popup = new PopupMenu( getContext(), vista );
					Menu menu = popup.getMenu();
					for( int i=0; i<tipiData.length-1; i++ )
						menu.add( 0, i, 0, tipiData[i] );
					popup.show();
					popup.setOnMenuItemClickListener( new PopupMenu.OnMenuItemClickListener() {
						@Override
						public boolean onMenuItemClick( MenuItem item ) {
							datatore.tipo = item.getItemId();
							// Se eventualmente invisibile
							findViewById( R.id.editadata_prima ).setVisibility( View.VISIBLE );
							if( data1.date == null ) // micro settaggio del carro
								((NumberPicker)findViewById( R.id.prima_anno )).setValue( 100 );
							if( datatore.tipo == 6 || datatore.tipo == 9 ) {
								findViewById( R.id.editadata_seconda ).setVisibility( VISIBLE );
								if( data2.date == null )
									((NumberPicker)findViewById( R.id.seconda_anno )).setValue( 100 );
							} else {
								findViewById( R.id.editadata_seconda ).setVisibility( GONE );
							}
							impostaCeccoDoppia( data2 );
							elencoTipi.setText( tipiData[datatore.tipo] );
							veroImputTesto = false;
							genera( false );
							return true;
						}
					});
				}
			});
			findViewById( R.id.editadata_doppia1 ).setOnClickListener( new View.OnClickListener() {
				@Override
				public void onClick( View vista ) {
					data1.doppia = ((CompoundButton)vista).isChecked();
					veroImputTesto = false;
					genera( false );
				}
			});
			findViewById( R.id.editadata_doppia2 ).setOnClickListener( new View.OnClickListener() {
				@Override
				public void onClick( View vista ) {
					data2.doppia = ((CompoundButton)vista).isChecked();
					veroImputTesto = false;
					genera( false );
				}
			});
			findViewById( R.id.editadata_circa ).setVisibility( GONE );
		} else {
			findViewById( R.id.editadata_circa ).setOnClickListener( new View.OnClickListener() {
				@Override
				public void onClick( View vista ) {
					datatore.tipo = ((CompoundButton)vista).isChecked() ? 1 : 0;
					veroImputTesto = false;
					genera( false );
				}
			});
			findViewById( R.id.editadata_avanzate ).setVisibility( GONE );
		}

		arredaCarro( 1, (NumberPicker)findViewById( R.id.prima_giorno ),
				(NumberPicker)findViewById( R.id.prima_mese ),
				(NumberPicker)findViewById( R.id.prima_secolo ),
				(NumberPicker)findViewById( R.id.prima_anno ) );

		arredaCarro( 2, (NumberPicker)findViewById( R.id.seconda_giorno ),
				(NumberPicker)findViewById( R.id.seconda_mese ),
				(NumberPicker)findViewById( R.id.seconda_secolo ),
				(NumberPicker)findViewById( R.id.seconda_anno ) );

		// Al primo focus mostra sè stesso (EditoreData) nascondendo la tastiera
		tastiera = (InputMethodManager) getContext().getSystemService( Context.INPUT_METHOD_SERVICE );
		editaTesto.setOnFocusChangeListener( new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange( View v, boolean ciapa ) {
				//s.l("onFocusChange "+ciapa);
				if( ciapa ) {
					if( datatore.tipo == 10 )
						genera( false ); // solo per togliere le parentesi alla frase
					else {
						tastieraVisibile = tastiera.hideSoftInputFromWindow( editaTesto.getWindowToken(), 0 ); // ok nasconde tastiera
						/*Window finestra = ((Activity)getContext()).getWindow(); non aiuta la scomparsa della tastiera
						finestra.setSoftInputMode( WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN );*/
						editaTesto.setInputType( InputType.TYPE_NULL ); // disabilita input testo con tastiera
							// necessario in versioni recenti di android in cui la tastiera ricompare
					}
					datatore.data1.date = null; // un resettino
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
				if( event.getAction() == MotionEvent.ACTION_DOWN ) {
					editaTesto.setInputType( InputType.TYPE_CLASS_TEXT ); // riabilita l'input
				} else if( event.getAction() == MotionEvent.ACTION_UP ) {
					tastieraVisibile = tastiera.showSoftInput( editaTesto, 0 ); // fa ricomparire la tastiera
					//veroImputTesto = true;
					//vista.performClick(); non ne vedo l'utilità
				}
				return false;
			}
		} );
		// Imposta l'editore data in base a quanto scritto
		editaTesto.addTextChangedListener( new TextWatcher() {
			@Override
			public void beforeTextChanged( CharSequence testo, int i, int i1, int i2 ) {}
			@Override
			public void onTextChanged( CharSequence testo, int i, int i1, int i2 ) {}
			@Override
			public void afterTextChanged( Editable testo ) {
				//s.l("\tafterTextChanged "+veroImputTesto);
				// non so perché ma in android 5 alla prima editazione viene chiamato 2 volte, che comunque non è un problema
				if( veroImputTesto )
					impostaTutto();
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

	void stilizza( NumberPicker ruota ) {
		// Toglie le famigerate linee divisorie azzurre
		try {
			Field campo = NumberPicker.class.getDeclaredField( "mSelectionDivider" );
			campo.setAccessible( true );
			campo.set( ruota, null );
		} catch( Exception e ) {}
		// Risolve il bug https://issuetracker.google.com/issues/37055335
		ruota.setSaveFromParentEnabled(false);
	}

	// Prende la stringa data, aggiorna le Date e ci modifica tutto l'editore data
	// Chiamato quando clicco sul campo editabile, e dopo ogni editazione del testo
	void impostaTutto() {
		datatore.analizza( editaTesto.getText().toString() );

		((CheckBox)findViewById( R.id.editadata_circa )).setChecked( datatore.tipo == 1 );

		((TextView)findViewById( R.id.editadata_tipi )).setText( tipiData[datatore.tipo] );

		// Primo carro
		if( datatore.tipo == 10 ) // Data frase
			findViewById( R.id.editadata_prima ).setVisibility( GONE );
		else {
			impostaCarro( data1, (NumberPicker)findViewById( R.id.prima_giorno ),
					(NumberPicker)findViewById( R.id.prima_mese ),
					(NumberPicker)findViewById( R.id.prima_secolo ),
					(NumberPicker)findViewById( R.id.prima_anno ) );
			findViewById( R.id.editadata_prima ).setVisibility( VISIBLE );
		}

		// Secondo carro
		if( datatore.tipo == 6 || datatore.tipo == 9 ) {
			impostaCarro( data2, (NumberPicker)findViewById( R.id.seconda_giorno ),
					(NumberPicker)findViewById( R.id.seconda_mese ),
					(NumberPicker)findViewById( R.id.seconda_secolo ),
					(NumberPicker)findViewById( R.id.seconda_anno ) );
			findViewById( R.id.editadata_seconda ).setVisibility( VISIBLE );
		} else
			findViewById( R.id.editadata_seconda ).setVisibility( GONE );

		// I Checkbox della data doppia
		impostaCeccoDoppia( data1 );
		impostaCeccoDoppia( data2 );
	}

	// Gira le ruote di un carro in base a una data
	void impostaCarro( Datatore.Data data, NumberPicker ruotaGiorno, NumberPicker ruotaMese, NumberPicker ruotaSecolo, NumberPicker ruotaAnno ) {
		calenda.clear();
		if( data.date != null )
			calenda.set( data.date.getYear()+1900, data.date.getMonth(), data.date.getDate() );
		ruotaGiorno.setMaxValue( calenda.getActualMaximum(Calendar.DAY_OF_MONTH) );
		if( data.date != null && (data.format.toPattern().equals(G_M_A) || data.format.toPattern().equals(G_M)) )
			ruotaGiorno.setValue( data.date.getDate() );
		else
			ruotaGiorno.setValue( 0 );
		if( data.date == null || data.format.toPattern().equals(A) )
			ruotaMese.setValue( 0 );
		else
			ruotaMese.setValue( data.date.getMonth() + 1 );
		if( data.date == null || data.format.toPattern().equals(G_M) )
			ruotaSecolo.setValue( 0 );
		else
			ruotaSecolo.setValue( (data.date.getYear()+1900)/100 );
		if( data.date == null || data.format.toPattern().equals(G_M) )
			ruotaAnno.setValue( 100 );
		else
			ruotaAnno.setValue( (data.date.getYear()+1900)%100 );
	}

	void impostaCeccoDoppia( Datatore.Data data ) {
		CheckBox cecco;
		if( data.equals(data1) )
			cecco = findViewById( R.id.editadata_doppia1 );
		else
			cecco = findViewById( R.id.editadata_doppia2 );
		if( data.date == null || data.format.toPattern().equals("") || data.format.toPattern().equals(G_M) // date senza anno
				|| (data.equals(data2) && !(datatore.tipo == 6 || datatore.tipo == 9) ) ) {
			cecco.setVisibility( INVISIBLE );
		} else {
			cecco.setChecked( data.doppia );
			cecco.setVisibility( VISIBLE );
		}
	}

	// Aggiorna una Data coi nuovi valori presi dalle ruote
	void aggiorna( Datatore.Data data, NumberPicker ruotaGiorno, NumberPicker ruotaMese, NumberPicker ruotaSecolo, NumberPicker ruotaAnno ) {
		if( tastieraVisibile ) {	// Nasconde eventuale tastiera visibile
			tastieraVisibile = tastiera.hideSoftInputFromWindow( editaTesto.getWindowToken(), 0 );
				// Nasconde subito la tastiera, ma ha bisogno di un secondo tentativo per restituire false. Comunque non è un problema
		}
		int giorno = ruotaGiorno.getValue();
		int mese = ruotaMese.getValue();
		int secolo = ruotaSecolo.getValue();
		int anno = ruotaAnno.getValue();
		// Imposta i giorni del mese in ruotaGiorno
		calenda.set( secolo*100+anno, mese-1, 1 );
		ruotaGiorno.setMaxValue( calenda.getActualMaximum(Calendar.DAY_OF_MONTH) );
		if( data.date == null ) data.date = new Date();
		//data.date.setDate( giorno == 0 ? 1 : giorno );  // così non attribuisce strani valori a caso al giorno 0
		data.date.setDate( giorno );
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
		// Il ceckbox della data doppia
		impostaCeccoDoppia( data );
		veroImputTesto = false;
		genera( false );
	}

	// Ricostruisce la stringa con la data finale e la mette in editaTesto
	void genera( boolean appenaPrimaDiSalvare ) {
		String rifatta;
		if( datatore.tipo == 0 )
			rifatta = rifai( data1 );
		else if( datatore.tipo == 6 )
			rifatta = "BET " + rifai( data1 ) + " AND " + rifai( data2 );
		else if( datatore.tipo == 9 )
			rifatta = "FROM " + rifai( data1 ) + " TO " + rifai( data2 );
		else if( datatore.tipo == 10 ) {
			if( appenaPrimaDiSalvare )
				rifatta = "(" + editaTesto.getText() + ")"; // mette le parentesi intorno a una data frase
			else
				rifatta = datatore.frase;
		} else
			rifatta = prefissi[datatore.tipo] + " " + rifai( data1 );

		editaTesto.setText( rifatta );
	}

	// Scrive la singola data in base al formato
	String rifai ( Datatore.Data data ) {
		String fatta = "";
		if( data.date != null ) {
			// Date con l'anno doppio
			if( data.doppia && !( data.format.toPattern().equals("") || data.format.toPattern().equals(G_M) ) ) {
				Date unAnnoDopo = new Date();
				unAnnoDopo.setYear( data.date.getYear() + 1 );
				String secondoAnno = String.format( Locale.ENGLISH, "%tY", unAnnoDopo );
				//s.l("secondoAnno "+secondoAnno);
				fatta = data.format.format( data.date ) +"/"+ secondoAnno.substring( 2 );
			} else // Le altre date normali
				fatta = data.format.format( data.date );
		}
		return fatta;
	}

	String s( int id ) {
		return Globale.contesto.getString( id );
	}
}