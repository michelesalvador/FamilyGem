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
import java.lang.reflect.Field;
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
	EditText editaTesto;
	String[] giorniRuota = { "-","1","2","3","4","5","6","7","8","9","10","11","12","13","14","15",
			"16","17","18","19","20","21","22","23","24","25","26","27","28","29","30","31" };
	String[] mesiRuota = { "-", s(R.string.january), s(R.string.february), s(R.string.march), s(R.string.april), s(R.string.may), s(R.string.june),
			s(R.string.july), s(R.string.august), s(R.string.september), s(R.string.october), s(R.string.november), s(R.string.december) };
	String[] anniRuota = new String[101];
	int[] dateKinds = { R.string.exact, R.string.approximate, R.string.calculated, R.string.estimated,
			R.string.after, R.string.before, R.string.between_and,
			R.string.from, R.string.to, R.string.from_to, R.string.date_phrase };
	Calendar calenda = GregorianCalendar.getInstance();
	boolean veroImputTesto; // stabilisce se l'utente sta effettivamente digitando sulla tastiera virtuale o se il testo viene cambiato in altro modo
	InputMethodManager tastiera;
	boolean tastieraVisibile;

	public PublisherDateLinearLayout(Context contesto, AttributeSet as ) {
		super( contesto, as );
	}

	// Azioni da fare una sola volta all'inizio
	void inizia( final EditText editaTesto ) {

		addView( inflate( getContext(), R.layout.editore_data, null ), this.getLayoutParams() );
		this.editaTesto = editaTesto;

		for( int i = 0; i < anniRuota.length - 1; i++ )
			anniRuota[i] = i < 10 ? "0" + i : "" + i;
		anniRuota[100] = "-";

		gedcomDateConverter = new GedcomDateConverter( editaTesto.getText().toString() );
		data1 = gedcomDateConverter.data1;
		data2 = gedcomDateConverter.data2;

		// Arreda l'editore data
		if( Global.settings.expert ) {
			final TextView elencoTipi = findViewById(R.id.editadata_tipi);
			elencoTipi.setOnClickListener( vista -> {
				PopupMenu popup = new PopupMenu(getContext(), vista);
				Menu menu = popup.getMenu();
				for( int i = 0; i < dateKinds.length - 1; i++ )
					menu.add(0, i, 0, dateKinds[i]);
				popup.show();
				popup.setOnMenuItemClickListener(item -> {
					gedcomDateConverter.kind = Kind.values()[item.getItemId()];
					// Se eventualmente invisibile
					findViewById(R.id.editadata_prima).setVisibility(View.VISIBLE);
					if( data1.date == null ) // micro settaggio del carro
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
					elencoTipi.setText(dateKinds[item.getItemId()]);
					veroImputTesto = false;
					genera();
					return true;
				});
			});
			findViewById(R.id.editadata_negativa1).setOnClickListener(vista -> {
				data1.negativa = ((CompoundButton)vista).isChecked();
				veroImputTesto = false;
				genera();
			});
			findViewById(R.id.editadata_doppia1).setOnClickListener(vista -> {
				data1.doppia = ((CompoundButton)vista).isChecked();
				veroImputTesto = false;
				genera();
			});
			findViewById(R.id.editadata_negativa2).setOnClickListener(vista -> {
				data2.negativa = ((CompoundButton)vista).isChecked();
				veroImputTesto = false;
				genera();
			});
			findViewById(R.id.editadata_doppia2).setOnClickListener(vista -> {
				data2.doppia = ((CompoundButton)vista).isChecked();
				veroImputTesto = false;
				genera();
			});
			findViewById(R.id.editadata_circa).setVisibility(GONE);
		} else {
			findViewById(R.id.editadata_circa).setOnClickListener(vista -> {
				findViewById(R.id.editadata_seconda).setVisibility(GONE); // casomai fosse visibile per tipi 6 o 9
				gedcomDateConverter.kind = ((CompoundButton)vista).isChecked() ? Kind.APPROXIMATE : Kind.EXACT;
				veroImputTesto = false;
				genera();
			});
			findViewById(R.id.editadata_avanzate).setVisibility(GONE);
		}

		arredaCarro(1, findViewById(R.id.prima_giorno), findViewById(R.id.prima_mese),
				findViewById(R.id.prima_secolo), findViewById(R.id.prima_anno));

		arredaCarro(2, findViewById(R.id.seconda_giorno), findViewById(R.id.seconda_mese),
				findViewById(R.id.seconda_secolo), findViewById(R.id.seconda_anno));

		// Al primo focus mostra sè stesso (EditoreData) nascondendo la tastiera
		tastiera = (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		editaTesto.setOnFocusChangeListener((v, ciapaFocus) -> {
			if( ciapaFocus ) {
				if( gedcomDateConverter.kind == Kind.PHRASE ) {
					//genera(); // Toglie le parentesi alla frase
					editaTesto.setText(gedcomDateConverter.frase);
				} else {
					tastieraVisibile = tastiera.hideSoftInputFromWindow( editaTesto.getWindowToken(), 0 ); // ok nasconde tastiera
					/*Window finestra = ((Activity)getContext()).getWindow(); non aiuta la scomparsa della tastiera
					finestra.setSoftInputMode( WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN );*/
					editaTesto.setInputType( InputType.TYPE_NULL ); // disabilita input testo con tastiera
						// necessario in versioni recenti di android in cui la tastiera ricompare
				}
				gedcomDateConverter.data1.date = null; // un resettino
				impostaTutto();
				setVisibility(View.VISIBLE);
			} else
				setVisibility(View.GONE);
		} );

		// Al secondo tocco fa comparire la tastiera
		editaTesto.setOnTouchListener((vista, event) -> {
			if( event.getAction() == MotionEvent.ACTION_DOWN ) {
				editaTesto.setInputType(InputType.TYPE_CLASS_TEXT); // riabilita l'input
			} else if( event.getAction() == MotionEvent.ACTION_UP ) {
				tastieraVisibile = tastiera.showSoftInput(editaTesto, 0); // fa ricomparire la tastiera
				//veroImputTesto = true;
				//vista.performClick(); non ne vedo l'utilità
			}
			return false;
		});
		// Imposta l'editore data in base a quanto scritto
		editaTesto.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence testo, int i, int i1, int i2) {}
			@Override
			public void onTextChanged(CharSequence testo, int i, int i1, int i2) {}
			@Override
			public void afterTextChanged(Editable testo) {
				// non so perché ma in android 5 alla prima editazione viene chiamato 2 volte, che comunque non è un problema
				if( veroImputTesto )
					impostaTutto();
				veroImputTesto = true;
			}
		});
	}

	// Prepara le quattro ruote di un carro con le impostazioni iniziali
	void arredaCarro( final int quale, final NumberPicker ruotaGiorno, final NumberPicker ruotaMese, final NumberPicker ruotaSecolo, final NumberPicker ruotaAnno ) {
		ruotaGiorno.setMinValue(0);
		ruotaGiorno.setMaxValue(31);
		ruotaGiorno.setDisplayedValues( giorniRuota );
		stilizza(ruotaGiorno);
		ruotaGiorno.setOnValueChangedListener( (picker, vecchio, nuovo) ->
				aggiorna( quale == 1 ? data1 : data2, ruotaGiorno, ruotaMese, ruotaSecolo, ruotaAnno )
		);
		ruotaMese.setMinValue(0);
		ruotaMese.setMaxValue(12);
		ruotaMese.setDisplayedValues( mesiRuota );
		stilizza(ruotaMese);
		ruotaMese.setOnValueChangedListener( (picker, vecchio, nuovo) ->
				aggiorna( quale == 1 ? data1 : data2, ruotaGiorno, ruotaMese, ruotaSecolo, ruotaAnno )
		);
		ruotaSecolo.setMinValue(0);
		ruotaSecolo.setMaxValue(20);
		stilizza(ruotaSecolo);
		ruotaSecolo.setOnValueChangedListener( (picker, vecchio, nuovo) ->
				aggiorna( quale == 1 ? data1 : data2, ruotaGiorno, ruotaMese, ruotaSecolo, ruotaAnno )
		);
		ruotaAnno.setMinValue(0);
		ruotaAnno.setMaxValue(100);
		ruotaAnno.setDisplayedValues( anniRuota );
		stilizza(ruotaAnno);
		ruotaAnno.setOnValueChangedListener( ( picker, vecchio, nuovo ) ->
				aggiorna( quale == 1 ? data1 : data2, ruotaGiorno, ruotaMese, ruotaSecolo, ruotaAnno )
		);
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
		gedcomDateConverter.analizza( editaTesto.getText().toString() );
		((CheckBox)findViewById( R.id.editadata_circa )).setChecked( gedcomDateConverter.kind == Kind.APPROXIMATE );
		((TextView)findViewById( R.id.editadata_tipi )).setText( dateKinds[gedcomDateConverter.kind.ordinal()] );

		// Primo carro
		impostaCarro( data1, findViewById( R.id.prima_giorno ), findViewById( R.id.prima_mese ),
				findViewById( R.id.prima_secolo ), findViewById( R.id.prima_anno ) );
		if( Global.settings.expert )
			impostaCecchi( data1 );

		// Secondo carro
		if( gedcomDateConverter.kind == Kind.BETWEEN_AND || gedcomDateConverter.kind == Kind.FROM_TO ) {
			impostaCarro( data2, findViewById( R.id.seconda_giorno ), findViewById( R.id.seconda_mese ),
					findViewById( R.id.seconda_secolo ), findViewById( R.id.seconda_anno ) );
			if( Global.settings.expert ) {
				findViewById( R.id.editadata_seconda_avanzate ).setVisibility( VISIBLE );
				impostaCecchi( data2 );
			}
			findViewById( R.id.editadata_seconda ).setVisibility( VISIBLE );
		} else {
			findViewById( R.id.editadata_seconda_avanzate ).setVisibility( GONE );
			findViewById( R.id.editadata_seconda ).setVisibility( GONE );
		}
	}

	// Gira le ruote di un carro in base a una data
	void impostaCarro(GedcomDateConverter.Data data, NumberPicker ruotaGiorno, NumberPicker ruotaMese, NumberPicker ruotaSecolo, NumberPicker ruotaAnno) {
		calenda.clear();
		if( data.date != null )
			calenda.setTime(data.date);
		ruotaGiorno.setMaxValue(calenda.getActualMaximum(Calendar.DAY_OF_MONTH));
		if( data.date != null && (data.isFormat(Format.D_M_Y) || data.isFormat(Format.D_M)) )
			ruotaGiorno.setValue(data.date.getDate());
		else
			ruotaGiorno.setValue(0);
		if( data.date == null || data.isFormat(Format.Y) )
			ruotaMese.setValue(0);
		else
			ruotaMese.setValue(data.date.getMonth() + 1);
		if( data.date == null || data.isFormat(Format.D_M) )
			ruotaSecolo.setValue(0);
		else
			ruotaSecolo.setValue((data.date.getYear() + 1900) / 100);
		if( data.date == null || data.isFormat(Format.D_M) )
			ruotaAnno.setValue(100);
		else
			ruotaAnno.setValue((data.date.getYear() + 1900) % 100);
	}

	// Imposta i Checkbox per una data che può essere negativa e doppia
	void impostaCecchi(GedcomDateConverter.Data data) {
		CheckBox ceccoBC, ceccoDoppia;
		if( data.equals(data1) ) {
			ceccoBC = findViewById(R.id.editadata_negativa1);
			ceccoDoppia = findViewById(R.id.editadata_doppia1);
		} else {
			ceccoBC = findViewById(R.id.editadata_negativa2);
			ceccoDoppia = findViewById(R.id.editadata_doppia2);
		}
		if( data.date == null || data.isFormat(Format.EMPTY) || data.isFormat(Format.D_M) ) { // date senza anno
			ceccoBC.setVisibility(INVISIBLE);
			ceccoDoppia.setVisibility(INVISIBLE);
		} else {
			ceccoBC.setChecked(data.negativa);
			ceccoBC.setVisibility(VISIBLE);
			ceccoDoppia.setChecked(data.doppia);
			ceccoDoppia.setVisibility(VISIBLE);
		}
	}

	// Aggiorna una Data coi nuovi valori presi dalle ruote
	void aggiorna(GedcomDateConverter.Data data, NumberPicker ruotaGiorno, NumberPicker ruotaMese, NumberPicker ruotaSecolo, NumberPicker ruotaAnno ) {
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
		data.date.setDate( giorno == 0 ? 1 : giorno );  // altrimenti la data M_A arretra di un mese
		data.date.setMonth( mese == 0 ? 0 : mese - 1 );
		data.date.setYear( anno == 100 ? -1899 : secolo*100 + anno - 1900 );
		if( giorno != 0 && mese != 0 && anno != 100 )
			data.format.applyPattern(Format.D_M_Y);
		else if( giorno != 0 && mese != 0 )
			data.format.applyPattern(Format.D_M);
		else if( mese != 0 && anno != 100 )
			data.format.applyPattern(Format.M_Y);
		else if( anno != 100 )
			data.format.applyPattern(Format.Y);
		else
			data.format.applyPattern(Format.EMPTY);
		impostaCecchi( data );
		veroImputTesto = false;
		genera();
	}

	// Ricostruisce la stringa con la data finale e la mette in editaTesto
	void genera() {
		String rifatta;
		if( gedcomDateConverter.kind == Kind.EXACT )
			rifatta = rifai(data1);
		else if( gedcomDateConverter.kind == Kind.BETWEEN_AND )
			rifatta = "BET " + rifai(data1) + " AND " + rifai(data2);
		else if( gedcomDateConverter.kind == Kind.FROM_TO )
			rifatta = "FROM " + rifai(data1) + " TO " + rifai(data2);
		else if( gedcomDateConverter.kind == Kind.PHRASE ) {
			// La frase viene sostituita da data esatta
			gedcomDateConverter.kind = Kind.EXACT;
			((TextView)findViewById(R.id.editadata_tipi)).setText(dateKinds[0]);
			rifatta = rifai(data1);
		} else
			rifatta = gedcomDateConverter.kind.prefix + " " + rifai(data1);
		editaTesto.setText(rifatta);
	}

	// Scrive la singola data in base al formato
	String rifai( GedcomDateConverter.Data data ) {
		String fatta = "";
		if( data.date != null ) {
			// Date con l'anno doppio
			if( data.doppia && !(data.isFormat(Format.EMPTY) || data.isFormat(Format.D_M)) ) {
				Date unAnnoDopo = new Date();
				unAnnoDopo.setYear( data.date.getYear() + 1 );
				String secondoAnno = String.format( Locale.ENGLISH, "%tY", unAnnoDopo );
				fatta = data.format.format( data.date ) +"/"+ secondoAnno.substring( 2 );
			} else // Le altre date normali
				fatta = data.format.format( data.date );
		}
		if( data.negativa )
			fatta += " B.C.";
		return fatta;
	}

	// Chiamato dall'esterno in sostanza solo per aggiungere le parentesi alla data-frase
	void chiudi() {
		if( gedcomDateConverter.kind == Kind.PHRASE ) {
			editaTesto.setText("(" + editaTesto.getText() + ")");
		}
	}

	String s(int id) {
		return Global.context.getString(id);
	}
}