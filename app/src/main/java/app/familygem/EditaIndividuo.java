package app.familygem;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatAutoCompleteTextView;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.Toast;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
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
		final String idFamiglia = bundle.getString("idFamiglia");
		final int relazione = bundle.getInt("relazione", 0 );

		final AppCompatAutoCompleteTextView luogoNascita = findViewById(R.id.luogo_nascita);
		final EditText dataNascita = findViewById( R.id.data_nascita );
		final Switch bottonMorte = findViewById( R.id.defunto );
		// Nuovo individuo in relazione di parentela
		if( relazione > 0 ) {
			String[] parentele = { "genitore", "fratello", "coniuge", "figlio" };   // todo elimina
			//setTitle( "Nuovo " + parentele[relazione-1] );
			p = new Person();
		// Nuovo individuo scollegato
		} else if ( idIndi.equals("TIZIO_NUOVO") ) {
			//setTitle( "Nuova persona" );
			p = new Person();
		// Carica i dati di un individuo esistente da modificare
		} else {
			p = gc.getPerson(idIndi);
			if( !p.getNames().isEmpty() ) {
				String epiteto = p.getNames().get( 0 ).getDisplayValue();
				String nome = epiteto.replaceAll( "/.*?/", "" ).trim();
				( (EditText) findViewById( R.id.nome ) ).setText( nome );
				if( epiteto.lastIndexOf('/') > 0 ) {
					String cognome = epiteto.substring( epiteto.indexOf('/') + 1, epiteto.lastIndexOf('/') ).trim();
					( (EditText) findViewById( R.id.cognome ) ).setText( cognome );
				}
			}
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
					//s.l( p.getId() );
					Globale.preferenze.alberoAperto().individui += 1;
					Globale.preferenze.salva();
					if( idFamiglia != null ) {
						Famiglia.aggrega( p, gc.getFamily(idFamiglia), relazione );
					} else if( relazione > 0 )
						aggiungiParente( idIndi, nuovoId, relazione );
				}
				Toast.makeText( getBaseContext(), R.string.saved, Toast.LENGTH_SHORT ).show();
				U.salvaJson();
				Globale.editato = true;
				onBackPressed();
			}
		});
		barra.setCustomView( barraAzione );
		barra.setDisplayShowCustomEnabled( true );
	}

	@Override
	protected void onActivityResult( int requestCode, int resultCode, Intent data ) {
		if (requestCode == 9746 && resultCode == RESULT_OK) {
			Place place = PlaceAutocomplete.getPlace( this, data );
			s.l( "Place: " + place.getName() +" > "+ place.getAttributions() );
		} else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
			Status status = PlaceAutocomplete.getStatus(this, data);
			s.l( status.getStatusMessage() );
		} else if (resultCode == RESULT_CANCELED) {
			// The user canceled the operation.
		}
	}

	// Aggiunge un individuo in relazione di parentela con 'perno'
	static void aggiungiParente( String idPerno, String nuovoId, int relazione ) {
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
				// Crea nuovo matrimonio per chi è già sposato
				if( !famSposi.isEmpty() && famSposi.get(0).getHusbandRefs().size() > 0 && famSposi.get(0).getWifeRefs().size() > 0 ) {
					refSposo.setRef( idPerno );
					refConiuge.setRef( nuovoId );
					if( U.sesso( perno ) == 1 ) {
						famNuova.addHusband( refSposo );
						famNuova.addWife( refConiuge );
					} else {
						famNuova.addWife( refSposo );
						famNuova.addHusband( refConiuge );
					}
					perno.addSpouseFamilyRef( refFamSposo );
				} else { // Altrimenti mette sposo nella famiglia esistente
					refSposo.setRef( nuovoId );
					if( !famSposi.isEmpty() ) {
						if( U.sesso( nuovo ) == 1 )
							famSposi.get(0).addHusband( refSposo );
						else
							famSposi.get(0).addWife( refSposo );
						refFamSposo.setRef( famSposi.get(0).getId() );
					} else {
						refConiuge.setRef( idPerno );
						if( U.sesso( nuovo ) == 1 ) {
							famNuova.addHusband( refSposo );
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
					famSposi.get( 0 ).addChild( refFiglio );
					refFamGenitori.setRef( famSposi.get(0).getId() );
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