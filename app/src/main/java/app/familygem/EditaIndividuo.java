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
import android.widget.ArrayAdapter;
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
		String idIndi = bundle.getString("idIndividuo");
		String idFamiglia = bundle.getString("idFamiglia"); // Arriva da Famiglia e dal nuovo sistema NuovoParente
		int relazione = bundle.getInt("relazione", 0 );
		//int famigliaNum = bundle.getInt("famigliaNum", 0 ); // indice della famiglia per perno che ne ha più di una

		AppCompatAutoCompleteTextView luogoNascita = findViewById(R.id.luogo_nascita);
		EditText dataNascita = findViewById( R.id.data_nascita );
		Switch bottonMorte = findViewById( R.id.defunto );
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
				else if( idFamiglia != null ) {
					Family fam = gc.getFamily(idFamiglia);
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
				if( relazione >= 5 ) { // viene da Famiglia
					Famiglia.aggrega( p, gc.getFamily(idFamiglia), relazione );
					modificati[1] = gc.getFamily(idFamiglia);
				} else if( relazione > 0 ) // viene da Diagramma o IndividuoFamiliari
					modificati = Globale.preferenze.esperto ? aggiungiParente2( idIndi, nuovoId, idFamiglia, relazione )
						: aggiungiParente( idIndi, nuovoId, idFamiglia, relazione );
			}
			if( Globale.preferenze.autoSalva )
				Toast.makeText( getBaseContext(), R.string.saved, Toast.LENGTH_SHORT ).show();
			U.salvaJson( true, modificati );
			onBackPressed();
		});
		barra.setCustomView( barraAzione );
		barra.setDisplayShowCustomEnabled( true );
	}

	// Verifica se il perno potrebbe avere o ha molteplici matrimoni e chiede a quale attaccare un coniuge o un figlio
	// è anche responsabile di settare "idFamiglia" se necessario
	static boolean controllaMultiMatrimoni( final Intent intento, final Context contesto, final Fragment frammento ) {
		final String idPerno = intento.getStringExtra( "idIndividuo" );
		final Person perno = gc.getPerson(idPerno);
		List<Family> famGenitori = perno.getParentFamilies(gc);
		List<Family> famSposi = perno.getSpouseFamilies(gc);
		int relazione = intento.getIntExtra( "relazione", 0 );
		ArrayAdapter<NuovoParente.VoceFamiglia> adapter = new ArrayAdapter<>(contesto, android.R.layout.simple_list_item_1);

		// Genitori: esiste già una famiglia
		if( relazione == 1 && famGenitori.size() == 1 ) {
			intento.putExtra( "idFamiglia", famGenitori.get(0).getId() );
		} // Genitori: esistono più famiglie
		if( relazione == 1 && famGenitori.size() > 1 ) {
			for( Family fam : famGenitori )
				if( fam.getHusbandRefs().isEmpty() || fam.getWifeRefs().isEmpty() )
					adapter.add( new NuovoParente.VoceFamiglia(contesto,fam) );
			if( adapter.getCount() == 1 )
				intento.putExtra( "idFamiglia", adapter.getItem(0).famiglia.getId() );
			else if( adapter.getCount() > 1 ) {
				/* Famiglie dei genitori
				for(int i=0; i < adapter.getCount(); i++) {
					Family fama = adapter.getItem(i).famiglia;
					if( fama != null ) {
						for( Person padre : fama.getHusbands(gc) )
							adapter.add( new NuovoParente.VoceFamiglia(contesto,padre) );
						for( Person madre : fama.getWives(gc) )
							adapter.add( new NuovoParente.VoceFamiglia(contesto,madre) );
					}
				} // Nuova famiglia
				adapter.add( new NuovoParente.VoceFamiglia(contesto) );*/
				new AlertDialog.Builder(contesto).setTitle( R.string.which_family_add_parent )
						.setAdapter( adapter, (dialog, quale) -> {
							/*if( quale == adapter.getCount() - 1 ) { // Nuova famiglia
								Family nuovaFam = Chiesa.nuovaFamiglia(true);
								intento.putExtra( "idFamiglia", nuovaFam.getId() );
							} else if( quale < adapter.getCount() - 1 ) { // Una delle famiglie esistenti
								Family familia = adapter.getItem(quale).famiglia;
								intento.putExtra( "idFamiglia", familia != null ? familia.getId() : null );
							}*/
							intento.putExtra( "idFamiglia", adapter.getItem(quale).famiglia.getId() );
							concludiDialogo(contesto, intento, frammento);
						}).show();
				return true;
			}
		} // Fratello
		else if( relazione == 2 && famGenitori.size() == 1 ) {
			intento.putExtra( "idFamiglia", famGenitori.get(0).getId() );
		} else if( relazione == 2 && famGenitori.size() > 1 ) {
			new AlertDialog.Builder(contesto).setTitle( R.string.which_family_add_sibling )
					.setItems( U.elencoFamiglie(famGenitori), (dialog, quale) -> {
						intento.putExtra( "idFamiglia", famGenitori.get(quale).getId() );
						concludiDialogo(contesto, intento, frammento);
					}).show();
			return true;
		} // Coniuge
		else if( relazione == 3 && famSposi.size() == 1 ) {
			if( famSposi.get(0).getHusbandRefs().isEmpty() || famSposi.get(0).getWifeRefs().isEmpty() ) // Se c'è uno slot libero
				intento.putExtra( "idFamiglia", famSposi.get(0).getId() );
		} else if( relazione == 3 && famSposi.size() > 1 ) {
			for( Family fam : famSposi ) {
				if( fam.getHusbandRefs().isEmpty() || fam.getWifeRefs().isEmpty() )
					adapter.add( new NuovoParente.VoceFamiglia(contesto,fam) );
			}
			// Nel caso di zero famiglie papabili, idFamiglia rimane null
			if( adapter.getCount() == 1 ) {
				intento.putExtra( "idFamiglia", adapter.getItem(0).famiglia.getId() );
			} else if( adapter.getCount() > 1 ) {
				//adapter.add(new NuovoParente.VoceFamiglia(contesto,perno) );
				new AlertDialog.Builder(contesto).setTitle( R.string.which_family_add_spouse )
						.setAdapter( adapter, (dialog, quale) -> {
							/*if( quale == adapter.getCount() - 1 ) { // Nuova famiglia
								Family nuovaFam = Chiesa.nuovaFamiglia(true);
								intento.putExtra( "idFamiglia", nuovaFam.getId() );
							} else if( quale < adapter.getCount() - 1 ) // Una delle famiglie esistenti
								intento.putExtra( "idFamiglia", adapter.getItem(quale).famiglia.getId() );*/
							intento.putExtra( "idFamiglia", adapter.getItem(quale).famiglia.getId() );
							concludiDialogo(contesto, intento, frammento);
						}).show();
				return true;
			}
		} // Figlio: esiste già una famiglia con o senza figli
		else if( relazione == 4 && famSposi.size() == 1 ) {
			intento.putExtra( "idFamiglia", famSposi.get(0).getId() );
		} else if( relazione == 4 && famSposi.size() > 1 ) {
			new AlertDialog.Builder(contesto).setTitle( R.string.which_family_add_child )
					.setItems( U.elencoFamiglie(famSposi), (dialog, quale) -> {
						intento.putExtra( "idFamiglia", famSposi.get(quale).getId() );
						concludiDialogo(contesto, intento, frammento);
					}).show();
			return true;
		}
		return false;
	}

	// Conclusione della funzione precedente
	static void concludiDialogo(Context contesto, Intent intento, Fragment frammento) {
		if( intento.getBooleanExtra( "anagrafeScegliParente", false ) ) {
			// apre Anagrafe
			if( frammento != null )
				frammento.startActivityForResult( intento,1401 );
			else
				((Activity)contesto).startActivityForResult( intento,1401 );
		} else // apre EditaIndividuo
			contesto.startActivity( intento );
	}

	// Nuova versione dell'aggiunta di un parente a perno, per ora solo per utenti esperti
	static Object[] aggiungiParente2( String idPerno, String nuovoId, String idFamiglia, int relazione ) {
		Person perno = gc.getPerson( idPerno );
		Person nuovo = gc.getPerson( nuovoId );
		SpouseRef refSposo = new SpouseRef();
		ChildRef refFiglio = new ChildRef();
		Person genitore = null;
		SpouseRef refGenitore = new SpouseRef();
		boolean fNuova = idFamiglia == null;
		if( idFamiglia != null && idFamiglia.startsWith("NUOVA_FAMIGLIA_DI") ) {
			refGenitore.setRef( idFamiglia.substring(17) );
			genitore = gc.getPerson(refGenitore.getRef());
		}
		Family famiglia = fNuova || genitore!=null ? Chiesa.nuovaFamiglia(true) : gc.getFamily(idFamiglia);
		ParentFamilyRef refFamGenitori = new ParentFamilyRef();
		SpouseFamilyRef refFamSposo = new SpouseFamilyRef();
		refFamGenitori.setRef( famiglia.getId() );
		refFamSposo.setRef( famiglia.getId() );
		cambiati = new HashSet<>();
		if( relazione == 1) { // Genitore
			refSposo.setRef( nuovoId );
			aggiungiConiuge( famiglia, refSposo );
			refFamSposo.setRef( famiglia.getId() );
			nuovo.addSpouseFamilyRef( refFamSposo );
			if(fNuova) {
				refFiglio.setRef( idPerno );
				famiglia.addChild( refFiglio );
				perno.addParentFamilyRef( refFamGenitori );
			}
		} else if( relazione == 2) { // Fratello
			refFiglio.setRef( nuovoId );
			famiglia.addChild( refFiglio );
			nuovo.addParentFamilyRef( refFamGenitori );
			if(fNuova) {
				ChildRef refPerno = new ChildRef();
				refPerno.setRef( idPerno );
				famiglia.addChild( refPerno );
				perno.addParentFamilyRef( refFamGenitori );
			} else if( genitore != null ) {
				aggiungiConiuge( famiglia, refGenitore );
				genitore.addSpouseFamilyRef( refFamSposo );
			}
		} else if( relazione == 3) { // Coniuge
			refSposo.setRef( nuovoId );
			aggiungiConiuge(famiglia, refSposo);
			nuovo.addSpouseFamilyRef(refFamSposo);
			if( fNuova || genitore != null ) {
				refGenitore.setRef( idPerno );
				aggiungiConiuge( famiglia, refGenitore );
				perno.addSpouseFamilyRef( refFamSposo );
			}
		} else if( relazione == 4) { // Figlio
			refFiglio.setRef( nuovoId );
			famiglia.addChild( refFiglio );
			nuovo.addParentFamilyRef( refFamGenitori );
			if( fNuova || genitore != null ) {
				refSposo.setRef( idPerno );
				aggiungiConiuge( famiglia, refSposo );
				perno.addSpouseFamilyRef( refFamSposo );
			}
		}
		if(fNuova)
			stocca( famiglia, nuovo, perno);
		else
			stocca( famiglia, nuovo);
		return cambiati.toArray();
	}

	// Aggiunge un individuo in relazione di parentela con 'perno'
	static Set<Object> cambiati; // lista di Family e Person da restituire per aggiornarne la data
	static Object[] aggiungiParente( String idPerno, String nuovoId, String idFamiglia, int relazione ) {
		SpouseRef refSposo = new SpouseRef();
		ChildRef refFiglio = new ChildRef();
		Person perno = gc.getPerson( idPerno );	// Individuo a cui ci attacchiamo
		Person nuovo = gc.getPerson( nuovoId );
		/*Family famGenitori = perno.getParentFamilies(gc).isEmpty() ? null
				: perno.getParentFamilies(gc).get(0); // comunque qui famigliaNum è sempre 0
		Family famSposi = perno.getSpouseFamilies(gc).isEmpty() ? null
				: perno.getSpouseFamilies(gc).get(famigliaNum);*/
		Family fam = idFamiglia != null ? gc.getFamily(idFamiglia) : null;
		Family famDestinoGenitori = nuovo.getParentFamilies(gc).isEmpty() ? null
				: nuovo.getParentFamilies(gc).get(0);
		Family famDestinoSposi = nuovo.getSpouseFamilies(gc).isEmpty() ? null
				: nuovo.getSpouseFamilies(gc).get(0);
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
				// Perno ha già una famiglia ma con nessuno o un solo genitore
				if( fam != null && (fam.getHusbandRefs().isEmpty() || fam.getWifeRefs().isEmpty()) ) {
					aggiungiConiuge( fam, refSposo );
					refFamSposo.setRef( fam.getId() );
					nuovo.addSpouseFamilyRef( refFamSposo );
					stocca( fam, nuovo );
				// Il nuovo genitore ha già una famiglia in cui aggiungere perno
				} else if( famDestinoSposi != null ) {
					famDestinoSposi.addChild( refFiglio );
					refFamGenitori.setRef( famDestinoSposi.getId() );
					perno.addParentFamilyRef( refFamGenitori );
					stocca( famDestinoSposi, perno );
				} else { // Si crea una famiglia nuova
					aggiungiConiuge( famNuova, refSposo );
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
				if( fam != null ) {
					fam.addChild( refFiglio );
					refFamGenitori.setRef( fam.getId() );
					nuovo.addParentFamilyRef( refFamGenitori );
					stocca( fam, nuovo );
				// Il nuovo fratello è già in una famiglia in cui aggiungere perno
				} else if( famDestinoGenitori != null ) {
					famDestinoGenitori.addChild( refFratello );
					refFamGenitori.setRef( famDestinoGenitori.getId() );
					perno.addParentFamilyRef( refFamGenitori );
					stocca( famDestinoGenitori, perno );
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
				if( fam == null && famDestinoSposi != null &&
						(famDestinoSposi.getHusbandRefs().isEmpty() || famDestinoSposi.getWifeRefs().isEmpty()) ) {
					aggiungiConiuge(famDestinoSposi, refSposo);
					refFamSposo.setRef( famDestinoSposi.getId() );
					perno.addSpouseFamilyRef( refFamSposo );
					stocca( famDestinoSposi, perno );
				}
				// Se perno è l'unico coniuge mette il nuovo sposo nella famiglia esistente
				else if( fam != null && (fam.getHusbandRefs().isEmpty() || fam.getWifeRefs().isEmpty()) ) {
					refSposo.setRef( nuovoId );
					aggiungiConiuge(fam, refSposo);
					refFamSposo.setRef( fam.getId() );
					nuovo.addSpouseFamilyRef( refFamSposo );
					stocca( fam, nuovo );
				}
				// In tutti gli altri casi crea una nuova famiglia con perno e il nuovo coniuge
				else {
					aggiungiConiuge( famNuova, refSposo );
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
				refSposo.setRef( idPerno );
				// Perno è genitore in una famiglia esistente
				 if( fam != null ) {
					 fam.addChild( refFiglio );
					refFamGenitori.setRef( fam.getId() );
					stocca( fam, nuovo );
				}
				// Perno diventa genitore di un bimbo che è già in una famiglia ma con nessun o un solo genitore
				else if( famDestinoGenitori != null && (famDestinoGenitori.getHusbandRefs().isEmpty()
						 || famDestinoGenitori.getWifeRefs().isEmpty()) ) {
					aggiungiConiuge(famDestinoGenitori, refSposo);
					refFamSposo.setRef( famDestinoGenitori.getId() );
					perno.addSpouseFamilyRef( refFamSposo );
					stocca( famDestinoGenitori, perno );
				}
				// Altimenti crea una nuova famiglia
				else {
					aggiungiConiuge(famNuova, refSposo);
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

	// Aggiunta il coniuge in una famiglia: il primo in base al sesso, il secondo riempiendo il ruolo vuoto, i seguenti per sesso
	public static void aggiungiConiuge(Family fam, SpouseRef sr) {
		Person tizio = Globale.gc.getPerson( sr.getRef() );
		if( fam.getHusbandRefs().isEmpty() && fam.getWifeRefs().isEmpty() ) {
			if( U.sesso(tizio) == 2 ) fam.addWife( sr );
			else fam.addHusband( sr );
		} else if( fam.getHusbandRefs().isEmpty() ) {
			fam.addHusband( sr );
		} else if( fam.getWifeRefs().isEmpty() ) {
			fam.addWife( sr );
		} else {
			if( U.sesso(tizio) == 2 ) fam.addWife( sr );
			else fam.addHusband( sr );
		}
	}

}