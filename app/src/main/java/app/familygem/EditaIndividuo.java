package app.familygem;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatAutoCompleteTextView;
import android.view.View;
import android.widget.CompoundButton;
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
import java.util.List;
import app.familygem.dettaglio.Evento;
import app.familygem.dettaglio.Famiglia;
import static app.familygem.Globale.gc;

public class EditaIndividuo extends AppCompatActivity {

	Person p;
	EditoreData editoreDataNescita;
	EditoreData editoreDataMorte;

	@Override
	protected void onCreate(Bundle stato) {
		super.onCreate(stato);
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
			// Se ci sono i permessi il primo Nome e Cognome li prende dal profilo 'io' nei contatti
			if( Globale.preferenze.alberi.size()==1 && gc.getPeople().isEmpty()
					&& ContextCompat.checkSelfPermission(getApplicationContext(),Manifest.permission.READ_CONTACTS)
					== PackageManager.PERMISSION_GRANTED ) {
				Cursor c = Globale.contesto.getContentResolver().query( ContactsContract.Profile.CONTENT_URI,null,null,null,null );
				if( c != null && c.moveToFirst() ) {
					String epiteto = c.getString(c.getColumnIndex("display_name_alt"));
					String nome = epiteto.substring( epiteto.indexOf(',')+1 ).trim();
					String cognome = epiteto.substring( 0, epiteto.indexOf(',') );
					((EditText)findViewById( R.id.nome )).setText( nome );
					((EditText)findViewById( R.id.cognome )).setText( cognome );
					// non riesco a individuare le colonne indirizzo, email , telefono....
					for( String col : c.getColumnNames() ) s.l( col +" = "+ c.getString( c.getColumnIndex( col ) ) );
					/*	sort_key = Michele Salvador
						display_name = Michele Salvador
						display_name_alt = Salvador, Michele
						sort_key_alt = Salvador, Michele    */
					c.close();
				}
			}
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
		editoreDataMorte.inizia( (EditText)findViewById( R.id.data_morte ) );

		bottonMorte.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged( CompoundButton coso, boolean attivo ) {
				if( attivo )
					findViewById(R.id.morte).setVisibility( View.VISIBLE );
				else
					findViewById(R.id.morte).setVisibility( View.GONE );
			}
		});

		// Barra
		ActionBar barra = this.getSupportActionBar();
		View barraAzione = getLayoutInflater().inflate( R.layout.barra_edita, new LinearLayout(getApplicationContext()), false);
		barraAzione.findViewById( R.id.edita_annulla ).setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View v ) {
				onBackPressed();
			}
		} );
		barraAzione.findViewById(R.id.edita_salva).setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View v ) {

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
				if (((RadioButton) findViewById(R.id.sesso1)).isChecked())
					sessoScelto = "M";
				else if (((RadioButton) findViewById(R.id.sesso2)).isChecked())
					sessoScelto = "F";
				else if (((RadioButton) findViewById(R.id.sesso3)).isChecked())
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
				if( editoreDataNescita.tipo == 10 ) editoreDataNescita.genera( true );
				String data = ((EditText)findViewById(R.id.data_nascita)).getText().toString();
				String luogo = luogoNascita.getText().toString();
				boolean trovato = false;
				for (EventFact fatto : p.getEventsFacts()) {
					if( fatto.getTag().equals("BIRT") ) {
						/* TODO: if(  data.isEmpty() && luogo.isEmpty() && tagTuttoVuoto(fatto)
						todo:  p.getEventsFacts().remove(fatto);
						todo: più in generale, eliminare un tag quando è vuoto */
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
				if( editoreDataMorte.tipo == 10 ) editoreDataMorte.genera( true );
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
				if( idIndi.equals("TIZIO_NUOVO") || relazione > 0 ) {
					int val, max = 0;
					for( Person prs : gc.getPeople() ) {
						val = Anagrafe.idNumerico( prs.getId() );
						if( val > max )
							max = val;
					}
					String nuovoId = "I" + ( max + 1 );
					p.setId( nuovoId );
					gc.addPerson( p );
					if( Globale.preferenze.alberoAperto().radice == null )
						Globale.preferenze.alberoAperto().radice = nuovoId;
					Globale.preferenze.alberoAperto().individui++;
					Globale.preferenze.salva();
					Globale.individuo = nuovoId; // per mostrarlo orgogliosi in Diagramma
					if( idFamiglia != null ) { // viene da Famiglia
						Famiglia.aggrega( p, gc.getFamily(idFamiglia), relazione );
					} else if( relazione > 0 ) // viene da Diagramma o IndividuoFamiliari
						aggiungiParente( idIndi, nuovoId, relazione, famigliaNum );
				}
				if( Globale.preferenze.autoSalva )
					Toast.makeText( getBaseContext(), R.string.saved, Toast.LENGTH_SHORT ).show();
				U.salvaJson();
				Globale.editato = true;
				onBackPressed();
			}
		});
		barra.setCustomView( barraAzione );
		barra.setDisplayShowCustomEnabled( true );
	}

	// Verifica se il perno ha molteplici matrimoni e chiede a quale attaccare un figlio
	static boolean controllaMultiMatrimoni( final Intent intento, final Context contesto, final Fragment frammento ) {
		final String idPerno = intento.getStringExtra( "idIndividuo" );
		final Person perno = gc.getPerson(idPerno);
		List<Family> famSposi = perno.getSpouseFamilies(gc);
		int relazione = intento.getIntExtra( "relazione", 0 );
		if( relazione == 4 && famSposi.size() == 1
				&& ( famSposi.get(0).getHusbands(gc).isEmpty() || famSposi.get(0).getWives(gc).isEmpty() )	// C'è un solo genitore
				&& !famSposi.get(0).getChildren(gc).isEmpty() ) { //  e ci sono già dei figli
			AlertDialog.Builder costruttore = new AlertDialog.Builder( contesto );
			String msg = contesto.getString( R.string.new_child_same_parents, U.epiteto(famSposi.get(0).getChildren(gc).get(0)), U.epiteto(perno) );
			costruttore.setMessage( msg )
					.setNeutralButton( R.string.same_family, new DialogInterface.OnClickListener() {
						@Override
						public void onClick( DialogInterface dialogo, int quale ) {
							if( intento.getBooleanExtra( "anagrafeScegliParente", false ) ) {
								// apre Anagrafe
								if( frammento != null )
									frammento.startActivityForResult( intento,1401 );
								else
									((Activity)contesto).startActivityForResult( intento,1401 );
							} else // apre EditaIndividuo
								contesto.startActivity( intento );
						}
					} )
					.setPositiveButton( R.string.new_family, new DialogInterface.OnClickListener() {
						@Override
						public void onClick( DialogInterface dialogo, int quale ) {
							Family famNuova = Chiesa.nuovaFamiglia();
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
						}
					} )
					.create().show();
			return true;
		} else if( intento.getIntExtra("relazione",0) == 4 && famSposi.size() > 1 ) {
			AlertDialog.Builder costruttore = new AlertDialog.Builder( contesto );
			costruttore.setTitle( R.string.which_family_add_child );
			List<String> famigliePerno = new ArrayList<>();
			for( Family fam : famSposi ) {
				String etichetta = "";
				if( !fam.getHusbands(gc).isEmpty() )
					etichetta = U.epiteto( fam.getHusbands(gc).get(0) );
				if( !fam.getWives(gc).isEmpty() )
					etichetta += " & " + U.epiteto( fam.getWives(gc).get(0) );
				famigliePerno.add( etichetta );
			}
			costruttore.setItems( famigliePerno.toArray(new String[0]), new DialogInterface.OnClickListener() {
				@Override
				public void onClick( DialogInterface dialog, int quale ) {
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
				}
			}).create().show();
			return true;
		}
		return false;
	}

	// Aggiunge un individuo in relazione di parentela con 'perno'
	static void aggiungiParente( String idPerno, String nuovoId, int relazione, int famigliaNum ) {
		SpouseRef refSposo = new SpouseRef();
		ChildRef refFiglio = new ChildRef();
		Person perno = gc.getPerson( idPerno );    // Individuo a cui ci attacchiamo
		Person nuovo = gc.getPerson( nuovoId );
		List<Family> famGenitori = perno.getParentFamilies( gc );
		List<Family> famSposi = perno.getSpouseFamilies( gc );
		Family famNuova = new Family();
		int val, max = 0;
		for( Family fam : gc.getFamilies() ) {
			val = Anagrafe.idNumerico( fam.getId() );
			if( val > max )
				max = val;
		}
		famNuova.setId( "F" + (max + 1) );
		ParentFamilyRef refFamGenitori = new ParentFamilyRef();
		SpouseFamilyRef refFamSposo = new SpouseFamilyRef();
		refFamGenitori.setRef( famNuova.getId() );
		refFamSposo.setRef( famNuova.getId() );
		switch( relazione ) {
			case 1:    // Genitore
				refSposo.setRef( nuovoId );
				refFiglio.setRef( idPerno );
				List<Family> famDestinatarie = nuovo.getSpouseFamilies(gc);
				// Perno ha già una famiglia ma con un solo genitore
				if( !famGenitori.isEmpty() && ( famGenitori.get(0).getHusbandRefs().isEmpty() || famGenitori.get(0).getWifeRefs().isEmpty() ) ) {
					if( U.sesso( nuovo ) == 1 )
						famGenitori.get(0).addHusband( refSposo );
					else
						famGenitori.get(0).addWife( refSposo );
					refFamSposo.setRef( famGenitori.get(0).getId() );
					nuovo.addSpouseFamilyRef( refFamSposo );
				// Il nuovo genitore ha già una famiglia in cui aggiungere perno
				} else if( !famDestinatarie.isEmpty()  ) {
					famDestinatarie.get(0).addChild( refFiglio );
					refFamGenitori.setRef( famDestinatarie.get(0).getId() );
					perno.addParentFamilyRef( refFamGenitori );
				} else { // Si crea una famiglia nuova
					if( U.sesso( nuovo ) == 1 )
						famNuova.addHusband( refSposo );
					else
						famNuova.addWife( refSposo );
					famNuova.addChild( refFiglio );
					perno.addParentFamilyRef( refFamGenitori );
					nuovo.addSpouseFamilyRef( refFamSposo );
				}
				break;
			case 2:    // Fratello
				refFiglio.setRef( nuovoId );
				ChildRef refFratello = new ChildRef();
				refFratello.setRef( idPerno );
				List<Family> famDestino = nuovo.getParentFamilies(gc);
				// Perno ha già una famiglia in cui è figlio
				if( !famGenitori.isEmpty() ) {
					famGenitori.get(0).addChild( refFiglio );
					refFamGenitori.setRef( famGenitori.get(0).getId() );
					nuovo.addParentFamilyRef( refFamGenitori );
				// Il nuovo fratello è già in una famiglia in cui aggiungere perno
				} else if( !famDestino.isEmpty()  ) {
					famDestino.get(0).addChild( refFratello );
					refFamGenitori.setRef( famDestino.get(0).getId() );
					perno.addParentFamilyRef( refFamGenitori );
				} else { // Si crea una nuova famiglia
					famNuova.addChild( refFratello );
					famNuova.addChild( refFiglio );
					perno.addParentFamilyRef( refFamGenitori );
					nuovo.addParentFamilyRef( refFamGenitori );
				}
				break;
			case 3:    // Coniuge
				SpouseRef refConiuge = new SpouseRef();
				// Crea nuovo matrimonio se perno è già sposato
				if( !famSposi.isEmpty() && famSposi.get(0).getHusbandRefs().size() > 0 && famSposi.get(0).getWifeRefs().size() > 0 ) {
					refSposo.setRef( idPerno );
					refConiuge.setRef( nuovoId );
					if( U.sesso( perno ) == 1 ) {
						famNuova.addHusband( refSposo );
						famNuova.addWife( refConiuge );
					} else {
						famNuova.addHusband( refConiuge );
						famNuova.addWife( refSposo );
					}
					perno.addSpouseFamilyRef( refFamSposo );
				} else {
					refSposo.setRef( nuovoId );
					// Altrimenti mette nuovo sposo nella famiglia esistente di perno (che quindi è l'unico coniuge)
					if( !famSposi.isEmpty() ) {
						if( U.sesso( nuovo ) == 1 )
							famSposi.get(0).addHusband( refSposo );
						else
							famSposi.get(0).addWife( refSposo );
						refFamSposo.setRef( famSposi.get(0).getId() );
					// Oppure crea una nuova famiglia per con nuovo e perno
					} else {
						refConiuge.setRef( idPerno );
						if( U.sesso( nuovo ) == 1 ) { // todo questa creazione di una nuova famiglia è quasi identica a quella qui sopra.
							famNuova.addHusband( refSposo ); // todo forse si può mettere meglio..
							famNuova.addWife( refConiuge );
						} else {
							famNuova.addHusband( refConiuge );
							famNuova.addWife( refSposo );
						}
						perno.addSpouseFamilyRef( refFamSposo );
					}
				}
				nuovo.addSpouseFamilyRef( refFamSposo );
				break;
			case 4:    // Figlio
				refFiglio.setRef( nuovoId );
				if( !famSposi.isEmpty() ) {
					famSposi.get( famigliaNum ).addChild( refFiglio );
					refFamGenitori.setRef( famSposi.get(famigliaNum).getId() );
				} else {
					refSposo.setRef( idPerno );
					if( U.sesso( perno ) == 1 )
						famNuova.addHusband( refSposo );
					else
						famNuova.addWife( refSposo );
					famNuova.addChild( refFiglio );
					perno.addSpouseFamilyRef( refFamSposo );
				}
				nuovo.addParentFamilyRef( refFamGenitori );
		}
		if( !famNuova.getHusbands(gc).isEmpty() || !famNuova.getWives(gc).isEmpty() || !famNuova.getChildren(gc).isEmpty() ) {
			gc.addFamily( famNuova );
		}
	}
}