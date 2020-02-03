package app.familygem;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatAutoCompleteTextView;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.Toast;
import org.folg.gedcom.model.ChildRef;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.ParentFamilyRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SpouseFamilyRef;
import org.folg.gedcom.model.SpouseRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import app.familygem.dettaglio.Evento;
import app.familygem.dettaglio.Famiglia;
import static app.familygem.Globale.gc;

public class EditaIndividuo extends AppCompatActivity {

	Person p;
	EditoreData editoreDataNescita;
	EditoreData editoreDataMorte;

	@Override
	protected void onCreate(Bundle bandolo) {
		super.onCreate(bandolo);
		setContentView(R.layout.edita_individuo );
		Bundle bundle = getIntent().getExtras();
		final String idIndi = bundle.getString("idIndividuo");
		final String idFamiglia = bundle.getString("idFamiglia"); // c'è solo per persone aggiunte da Famiglia
		final int relazione = bundle.getInt("relazione", 0 );
		final int famigliaNum = bundle.getInt("famigliaNum", 0 ); // indice della famiglia per perno che ne ha più di una

		final AppCompatAutoCompleteTextView luogoNascita = findViewById(R.id.luogo_nascita);
		final EditText dataNascita = findViewById( R.id.data_nascita );
		final Switch bottonMorte = findViewById( R.id.defunto );
		// Nuovo individuo in relazione di parentela
		if( relazione > 0 ) {
			p = new Person();
			Person perno = gc.getPerson( idIndi );
			String cogno = "";
			// Cognome del fratello
			if( relazione == 2 ) { // = fratello
				cogno = U.cognome( perno );
			// Cognome del padre
			} else if( relazione == 4 ) { // = figlio da Diagramma o Individuo
				if( U.sesso(perno) == 1 )
					cogno = U.cognome( perno );
				else if( !perno.getSpouseFamilies(gc).isEmpty() ) {
					Family fam = perno.getSpouseFamilies(gc).get(famigliaNum);
					if( !fam.getHusbands(gc).isEmpty() )
						cogno = U.cognome( fam.getHusbands(gc).get(0) );
				}
			} else if( relazione == 6 ) { // = figlio da Famiglia
				Family fam = gc.getFamily(idFamiglia);
				if( !fam.getHusbands(gc).isEmpty() )
					cogno = U.cognome( fam.getHusbands(gc).get(0) );
				else if( !fam.getChildren(gc).isEmpty() )
					cogno = U.cognome( fam.getChildren(gc).get(0) );
			}
			((EditText)findViewById( R.id.cognome )).setText( cogno );
		// Nuovo individuo scollegato
		} else if ( idIndi.equals("TIZIO_NUOVO") ) {
			p = new Person();
		// Carica i dati di un individuo esistente da modificare
		} else {
			p = gc.getPerson(idIndi);
			// Nome e cognome
			if( !p.getNames().isEmpty() ) {
				String epiteto = p.getNames().get( 0 ).getDisplayValue();
				String nome = epiteto.replaceAll( "/.*?/", "" ).trim(); // rimuove il cognome '/.../'
				((EditText)findViewById( R.id.nome )).setText( nome );
				if( epiteto.indexOf('/') < epiteto.lastIndexOf('/') ) {
					String cognome = epiteto.substring( epiteto.indexOf('/') + 1, epiteto.lastIndexOf('/') ).trim();
					((EditText)findViewById( R.id.cognome )).setText( cognome );
				}
			}
			// Sesso
			switch( U.sesso(p) ) {
				case 1:
					((RadioButton)findViewById( R.id.sesso1 )).setChecked(true);
					break;
				case 2:
					((RadioButton)findViewById( R.id.sesso2 )).setChecked(true);
					break;
				case 3:
					((RadioButton)findViewById( R.id.sesso3 )).setChecked(true);
			}
			// Nascita e morte
			for( EventFact fatto : p.getEventsFacts() ) {
				if( fatto.getTag().equals("BIRT") ) {
					if( fatto.getDate() != null )
						dataNascita.setText( fatto.getDate().trim() );
					if( fatto.getPlace() != null )
						luogoNascita.setText(fatto.getPlace().trim());
				}
				if( fatto.getTag().equals("DEAT") ) {
					bottonMorte.setChecked(true);
					findViewById(R.id.morte).setVisibility( View.VISIBLE );
					if( fatto.getDate() != null )
						((EditText)findViewById( R.id.data_morte )).setText( fatto.getDate().trim() );
					if( fatto.getPlace() != null )
						((EditText)findViewById( R.id.luogo_morte )).setText(fatto.getPlace().trim());
				}
			}
		}

		editoreDataNescita = findViewById(R.id.editore_data_nascita);
		editoreDataNescita.inizia( dataNascita );
		editoreDataMorte = findViewById( R.id.editore_data_morte );
		editoreDataMorte.inizia( findViewById( R.id.data_morte ) );

		bottonMorte.setOnCheckedChangeListener( (coso, attivo) -> {
			if (attivo)
				findViewById(R.id.morte).setVisibility( View.VISIBLE );
			else
				findViewById(R.id.morte).setVisibility( View.GONE );
		});

		// Barra
		ActionBar barra = getSupportActionBar();
		View barraAzione = getLayoutInflater().inflate( R.layout.barra_edita, new LinearLayout(getApplicationContext()), false);
		barraAzione.findViewById( R.id.edita_annulla ).setOnClickListener( v -> onBackPressed() );
		barraAzione.findViewById(R.id.edita_salva).setOnClickListener( v -> {
			// Nome
			String epiteto = ((EditText)findViewById(R.id.nome)).getText() + " /" + ((EditText)findViewById(R.id.cognome)).getText() + "/".trim();
			if( p.getNames().isEmpty() ) {
				List<Name> nomi = new ArrayList<>();
				Name nomeCompleto = new Name();
				nomeCompleto.setValue( epiteto );
				nomi.add( nomeCompleto );
				p.setNames( nomi );
			} else
				p.getNames().get(0).setValue( epiteto );

			// Sesso
			String sessoScelto = null;
			if( ((RadioButton)findViewById(R.id.sesso1)).isChecked() )
				sessoScelto = "M";
			else if( ((RadioButton)findViewById(R.id.sesso2)).isChecked() )
				sessoScelto = "F";
			else if( ((RadioButton) findViewById(R.id.sesso3)).isChecked() )
				sessoScelto = "U";
			if( sessoScelto != null ) {
				boolean mancaSesso = true;
				for( EventFact fatto : p.getEventsFacts() ) {
					if (fatto.getTag().equals("SEX")) {
						fatto.setValue(sessoScelto);
						mancaSesso = false;
					}
				}
				if( mancaSesso ) {
					EventFact sesso = new EventFact();
					sesso.setTag( "SEX" );
					sesso.setValue( sessoScelto );
					p.addEventFact( sesso );
				}
			}

			// Nascita
			editoreDataNescita.chiudi();
			String data = ((EditText)findViewById(R.id.data_nascita)).getText().toString();
			String luogo = luogoNascita.getText().toString();
			boolean trovato = false;
			for (EventFact fatto : p.getEventsFacts()) {
				if( fatto.getTag().equals("BIRT") ) {
					/* TODO: if(  data.isEmpty() && luogo.isEmpty() && tagTuttoVuoto(fatto)
					    p.getEventsFacts().remove(fatto);
					    più in generale, eliminare un tag quando è vuoto */
					fatto.setDate( data );
					fatto.setPlace( luogo );
					Evento.ripulisciTag( fatto );
					trovato = true;
				}
			}
			// Se c'è qualche dato da salvare crea il tag
			if( !trovato && ( !data.isEmpty() || !luogo.isEmpty() ) ) {
				EventFact nascita = new EventFact();
				nascita.setTag( "BIRT" );
				nascita.setDate( data );
				nascita.setPlace( luogo );
				Evento.ripulisciTag( nascita );
				p.addEventFact( nascita );
			}

			// Morte
			editoreDataMorte.chiudi();
			data = ((EditText)findViewById(R.id.data_morte)).getText().toString();
			luogo = ((EditText)findViewById(R.id.luogo_morte)).getText().toString();
			trovato = false;
			for( EventFact fatto : p.getEventsFacts() ) {
				if( fatto.getTag().equals("DEAT") ) {
					if( !bottonMorte.isChecked() ) {
						p.getEventsFacts().remove(fatto);
					} else {
						fatto.setDate( data );
						fatto.setPlace( luogo );
						Evento.ripulisciTag( fatto );
					}
					trovato = true;
					break;
				}
			}
			if( !trovato && bottonMorte.isChecked() ) {
				EventFact morte = new EventFact();
				morte.setTag( "DEAT" );
				morte.setDate( data );
				morte.setPlace( luogo );
				Evento.ripulisciTag( morte );
				p.addEventFact( morte );
			}

			// ID individuo nuovo
			Object[] modificati = { p, null }; // il null serve per accogliere una eventuale Family
			if( idIndi.equals("TIZIO_NUOVO") || relazione > 0 ) {
				String nuovoId = U.nuovoId( gc, Person.class );
				p.setId( nuovoId );
				gc.addPerson( p );
				if( Globale.preferenze.alberoAperto().radice == null )
					Globale.preferenze.alberoAperto().radice = nuovoId;
				Globale.preferenze.alberoAperto().individui++;
				Globale.preferenze.salva();
				Globale.individuo = nuovoId; // per mostrarlo orgogliosi in Diagramma
				if( idFamiglia != null ) { // viene da Famiglia
					Famiglia.aggrega( p, gc.getFamily(idFamiglia), relazione );
					modificati[1] = gc.getFamily(idFamiglia);
				} else if( relazione > 0 ) // viene da Diagramma o IndividuoFamiliari
					modificati = aggiungiParente( idIndi, nuovoId, relazione, famigliaNum );
			}
			if( Globale.preferenze.autoSalva )
				Toast.makeText( getBaseContext(), R.string.saved, Toast.LENGTH_SHORT ).show();
			U.salvaJson( true, modificati );
			onBackPressed();
		});
		barra.setCustomView( barraAzione );
		barra.setDisplayShowCustomEnabled( true );
	}

	// Verifica se il perno potrebbe avere o ha molteplici matrimoni e chiede a quale attaccare un figlio
	static boolean controllaMultiMatrimoni( final Intent intento, final Context contesto, final Fragment frammento ) {
		final String idPerno = intento.getStringExtra( "idIndividuo" );
		final Person perno = gc.getPerson(idPerno);
		List<Family> famSposi = perno.getSpouseFamilies(gc);
		int relazione = intento.getIntExtra( "relazione", 0 );
		if( relazione == 4 && famSposi.size() == 1
				&& ( famSposi.get(0).getHusbands(gc).isEmpty() || famSposi.get(0).getWives(gc).isEmpty() )	// C'è un solo genitore
				&& !famSposi.get(0).getChildren(gc).isEmpty() ) { //  e ci sono già dei figli
			String msg = contesto.getString( R.string.new_child_same_parents, U.epiteto(famSposi.get(0).getChildren(gc).get(0)), U.epiteto(perno) );
			new AlertDialog.Builder( contesto ).setMessage( msg )
					.setNeutralButton( R.string.same_family, (dialogo, quale) -> {
						if( intento.getBooleanExtra( "anagrafeScegliParente", false ) ) {
							// apre Anagrafe
							if( frammento != null )
								frammento.startActivityForResult( intento,1401 );
							else
								((Activity)contesto).startActivityForResult( intento,1401 );
						} else // apre EditaIndividuo
							contesto.startActivity( intento );
					})
					.setPositiveButton( R.string.new_family, (dialogo, quale) -> {
						Family famNuova = Chiesa.nuovaFamiglia(true);
						SpouseRef sr = new SpouseRef();
						sr.setRef( idPerno );
						if( U.sesso(perno) == 2 )
							famNuova.addWife( sr );
						else famNuova.addHusband( sr );
						SpouseFamilyRef sfr = new SpouseFamilyRef();
						sfr.setRef( famNuova.getId() );
						perno.addSpouseFamilyRef( sfr );
						intento.putExtra( "famigliaNum", 1 );
						if( intento.getBooleanExtra( "anagrafeScegliParente", false ) ) {
							if( frammento != null )
								frammento.startActivityForResult( intento,1401 );
							else
								((Activity)contesto).startActivityForResult( intento,1401 );
						} else
							contesto.startActivity( intento );
					}).show();
			return true;
		} else if( relazione == 4 && famSposi.size() > 1 ) {
			List<String> famigliePerno = new ArrayList<>();
			for( Family fam : famSposi ) {
				String etichetta = "";
				if( !fam.getHusbands(gc).isEmpty() )
					etichetta = U.epiteto( fam.getHusbands(gc).get(0) );
				if( !fam.getWives(gc).isEmpty() )
					etichetta += " & " + U.epiteto( fam.getWives(gc).get(0) );
				famigliePerno.add( etichetta );
			}
			new AlertDialog.Builder( contesto ).setTitle( R.string.which_family_add_child )
					.setItems( famigliePerno.toArray(new String[0]), (dialog, quale) -> {
						// qui viene creato questo Extra che passerà da Anagrafe per tornare all'Activity/Fragment chiamante
						intento.putExtra( "famigliaNum", quale );
						//s.l( contesto.getClass() +"  "+ intento );
						if( intento.getBooleanExtra( "anagrafeScegliParente", false ) ) {
							// apre Anagrafe
							if( frammento != null )
								frammento.startActivityForResult( intento,1401 );
							else
								((Activity)contesto).startActivityForResult( intento,1401 );
						} else // apre EditaIndividuo
							contesto.startActivity( intento );
					}).show();
			return true;
		}
		return false;
	}

	// Aggiunge un individuo in relazione di parentela con 'perno'
	static Set<Object> cambiati; // lista di Family e Person da restituire per aggiornarne la data
	static Object[] aggiungiParente( String idPerno, String nuovoId, int relazione, int famigliaNum ) {
		SpouseRef refSposo = new SpouseRef();
		ChildRef refFiglio = new ChildRef();
		Person perno = gc.getPerson( idPerno );	// Individuo a cui ci attacchiamo
		Person nuovo = gc.getPerson( nuovoId );
		List<Family> famGenitori = perno.getParentFamilies( gc );
		List<Family> famSposi = perno.getSpouseFamilies( gc );
		List<Family> famDestinoGenitori = nuovo.getParentFamilies(gc);
		List<Family> famDestinoSposi = nuovo.getSpouseFamilies(gc);
		Family famNuova = Chiesa.nuovaFamiglia( false );
		ParentFamilyRef refFamGenitori = new ParentFamilyRef();
		SpouseFamilyRef refFamSposo = new SpouseFamilyRef();
		refFamGenitori.setRef( famNuova.getId() );
		refFamSposo.setRef( famNuova.getId() );
		cambiati = new HashSet<>();
		switch( relazione ) {
			case 1: // Genitore
				refSposo.setRef( nuovoId );
				refFiglio.setRef( idPerno );
				// Perno ha già una famiglia ma con un solo genitore
				if( !famGenitori.isEmpty() && ( famGenitori.get(0).getHusbandRefs().isEmpty() || famGenitori.get(0).getWifeRefs().isEmpty() ) ) {
					if( U.sesso( nuovo ) == 2 )
						famGenitori.get(0).addWife( refSposo );
					else
						famGenitori.get(0).addHusband( refSposo );
					refFamSposo.setRef( famGenitori.get(0).getId() );
					nuovo.addSpouseFamilyRef( refFamSposo );
					stocca( famGenitori.get(0), nuovo );
				// Il nuovo genitore ha già una famiglia in cui aggiungere perno
				} else if( !famDestinoSposi.isEmpty()  ) {
					famDestinoSposi.get(0).addChild( refFiglio );
					refFamGenitori.setRef( famDestinoSposi.get(0).getId() );
					perno.addParentFamilyRef( refFamGenitori );
					stocca( famDestinoSposi.get(0), perno );
				} else { // Si crea una famiglia nuova
					if( U.sesso( nuovo ) == 2 )
						famNuova.addWife( refSposo );
					else
						famNuova.addHusband( refSposo );
					famNuova.addChild( refFiglio );
					perno.addParentFamilyRef( refFamGenitori );
					nuovo.addSpouseFamilyRef( refFamSposo );
					stocca( famNuova, perno, nuovo );
				}
				break;
			case 2: // Fratello
				refFiglio.setRef( nuovoId );
				ChildRef refFratello = new ChildRef();
				refFratello.setRef( idPerno );
				// Perno ha già una famiglia in cui è figlio
				if( !famGenitori.isEmpty() ) {
					famGenitori.get(0).addChild( refFiglio );
					refFamGenitori.setRef( famGenitori.get(0).getId() );
					nuovo.addParentFamilyRef( refFamGenitori );
					stocca( famGenitori.get(0), nuovo );
				// Il nuovo fratello è già in una famiglia in cui aggiungere perno
				} else if( !famDestinoGenitori.isEmpty()  ) {
					famDestinoGenitori.get(0).addChild( refFratello );
					refFamGenitori.setRef( famDestinoGenitori.get(0).getId() );
					perno.addParentFamilyRef( refFamGenitori );
					stocca( famDestinoGenitori.get(0), perno );
				} else { // Si crea una nuova famiglia
					famNuova.addChild( refFratello );
					famNuova.addChild( refFiglio );
					perno.addParentFamilyRef( refFamGenitori );
					nuovo.addParentFamilyRef( refFamGenitori );
					stocca( famNuova, perno, nuovo );
				}
				break;
			case 3: // Coniuge
				refSposo.setRef( idPerno );
				// Perno senza famiglia che si coniuga con persona single già in una famiglia: perno diventa coniuge nella famiglia esistente
				if( famSposi.isEmpty() && !famDestinoSposi.isEmpty() &&
						(famDestinoSposi.get(0).getHusbandRefs().isEmpty() || famDestinoSposi.get(0).getWifeRefs().isEmpty()) ) {
					aggiungiConiuge(famDestinoSposi.get(0), refSposo);
					refFamSposo.setRef( famDestinoSposi.get(0).getId() );
					perno.addSpouseFamilyRef( refFamSposo );
					stocca( famDestinoSposi.get(0), perno );
				}
				// Se perno è l'unico coniuge mette il nuovo sposo nella famiglia esistente
				else if( !famSposi.isEmpty() && (famSposi.get(0).getHusbandRefs().isEmpty() || famSposi.get(0).getWifeRefs().isEmpty()) ) {
					refSposo.setRef( nuovoId );
					aggiungiConiuge(famSposi.get(0), refSposo);
					refFamSposo.setRef( famSposi.get(0).getId() );
					nuovo.addSpouseFamilyRef( refFamSposo );
					stocca( famSposi.get(0), nuovo );
				}
				// In tutti gli altri casi crea una nuova famiglia con perno e il nuovo coniuge
				else {
					if( U.sesso( perno ) == 2 )
						famNuova.addWife( refSposo );
					else
						famNuova.addHusband( refSposo );
					SpouseRef refConiuge = new SpouseRef();
					refConiuge.setRef( nuovoId );
					aggiungiConiuge(famNuova, refConiuge);
					perno.addSpouseFamilyRef( refFamSposo );
					nuovo.addSpouseFamilyRef( refFamSposo );
					stocca( famNuova, perno, nuovo );
				}
				break;
			case 4: // Figlio
				refFiglio.setRef( nuovoId );
				// Perno è genitore in una famiglia esistente
				 if( !famSposi.isEmpty() ) {
					famSposi.get( famigliaNum ).addChild( refFiglio );
					refFamGenitori.setRef( famSposi.get(famigliaNum).getId() );
					stocca( famSposi.get(famigliaNum), nuovo );
				}
				// Perno diventa genitore di un bimbo che è già in una famiglia ma con un solo genitore
				else if( !famDestinoGenitori.isEmpty() &&
						(famDestinoGenitori.get(0).getHusbandRefs().isEmpty() || famDestinoGenitori.get(0).getWifeRefs().isEmpty()) ) {
					String idConiuge = "";
					if( !famDestinoGenitori.get(0).getHusbandRefs().isEmpty() )
						idConiuge = famDestinoGenitori.get(0).getHusbandRefs().get(0).getRef();
					else if( !famDestinoGenitori.get(0).getWifeRefs().isEmpty() )
						idConiuge = famDestinoGenitori.get(0).getWifeRefs().get(0).getRef();
					aggiungiParente( idPerno, idConiuge, 3, 0 );
					break;
				}
				// Altimenti crea una nuova famiglia
				else {
					refSposo.setRef( idPerno );
					if( U.sesso( perno ) == 2 )
						famNuova.addWife( refSposo );
					else
						famNuova.addHusband( refSposo );
					famNuova.addChild( refFiglio );
					perno.addSpouseFamilyRef( refFamSposo );
					stocca( famNuova, perno, nuovo );
				}
				nuovo.addParentFamilyRef( refFamGenitori );
		}
		if( !famNuova.getHusbandRefs().isEmpty() || !famNuova.getWifeRefs().isEmpty() || !famNuova.getChildRefs().isEmpty() ) {
			gc.addFamily( famNuova );
		}
		return cambiati.toArray();
	}
	// Stocca gli oggetti pronti per aggiornargli la data di cambio
	static void stocca( Object ... oggetti ) {
		Collections.addAll( cambiati, oggetti);
	}

	// Aggiunge il coniuge in una coppia husband-wife indipendentemente dal sesso
	public static void aggiungiConiuge(Family famiglia, SpouseRef refNuovo) {
		if( !famiglia.getHusbandRefs().isEmpty() )
			famiglia.addWife( refNuovo );
		else
			famiglia.addHusband( refNuovo );
	}
}