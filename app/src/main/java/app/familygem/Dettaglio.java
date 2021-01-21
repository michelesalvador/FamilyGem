package app.familygem;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.theartofdev.edmodo.cropper.CropImage;
import org.folg.gedcom.model.Address;
import org.folg.gedcom.model.ChildRef;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.ExtensionContainer;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.GedcomTag;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;
import org.folg.gedcom.model.MediaRef;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.NoteRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.RepositoryRef;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;
import org.folg.gedcom.model.SpouseFamilyRef;
import org.folg.gedcom.model.SpouseRef;
import org.folg.gedcom.model.Submitter;
import org.folg.gedcom.model.Visitable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import app.familygem.dettaglio.CitazioneFonte;
import app.familygem.dettaglio.Estensione;
import app.familygem.dettaglio.Evento;
import app.familygem.dettaglio.Famiglia;
import app.familygem.dettaglio.Immagine;
import app.familygem.dettaglio.Indirizzo;
import app.familygem.dettaglio.Nota;
import app.familygem.visita.TrovaPila;
import static app.familygem.Globale.gc;

public class Dettaglio extends AppCompatActivity {

	public LinearLayout box;
	public Object oggetto;	// Name Media SourceCitation ecc.
	List<Uovo> ovi = new ArrayList<>();	// Lista dei pezzi editabili
	TreeMap<String,String> eventiVari; // Eventi per il FAB di Famiglia
	public Person unRappresentanteDellaFamiglia; // una Person della Famiglia per nascondere nel FAB 'Collega persona'
	EditoreData editoreData;

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		setContentView( R.layout.dettaglio );
		getSupportActionBar().setDisplayHomeAsUpEnabled( true );
		box = findViewById( R.id.dettaglio_scatola );
		U.gedcomSicuro( gc );

		oggetto = Memoria.getOggetto();
		if( oggetto == null) {
			onBackPressed(); // salta tutti gli altri dettagli senza oggetto
		} else
			impagina();
		FloatingActionButton fab = findViewById( R.id.fab );
		fab.setOnClickListener( vista -> {
			PopupMenu popup = menuFAB( vista );
			popup.show();
			popup.setOnMenuItemClickListener( item -> {
				// FAB + mette un nuovo uovo e lo rende subito editabile
				int id = item.getItemId();
				if( id < 100 ) {
					Object coso = ovi.get( id ).oggetto;
					if( coso instanceof Address ) {	// coso è un new Address()
						if( oggetto instanceof EventFact )
							((EventFact)oggetto).setAddress( (Address)coso );
						else if( oggetto instanceof Submitter )
							((Submitter)oggetto).setAddress( (Address)coso );
						else if( oggetto instanceof Repository )
							((Repository)oggetto).setAddress( (Address)coso );
					}
					// Tag necessari per poi esportare in Gedcom
					if( oggetto instanceof Name && coso.equals("Type") ) {
						((Name)oggetto).setTypeTag("TYPE");
					} else if( oggetto instanceof Repository ) {
						if( coso.equals("Www") )
							((Repository)oggetto).setWwwTag("WWW");
						if( coso.equals("Email") )
							((Repository)oggetto).setEmailTag("EMAIL");
					} else if( oggetto instanceof Submitter ) {
						if( coso.equals("Www") )
							((Submitter)oggetto).setWwwTag("WWW");
						if( coso.equals("Email") )
							((Submitter)oggetto).setEmailTag("EMAIL");
					}
					View pezzo = creaPezzo( ovi.get(id).titolo, "", coso, ovi.get(id).multiLinea );
					if( coso instanceof String )
						edita( pezzo );
					// todo : aprire Address nuovo per editarlo
				} else if( id == 101 ) {
					Magazzino.nuovoArchivio( this, (Source)oggetto );
				} else if( id == 102 ) {
					Intent intento = new Intent( this, Principe.class );
					intento.putExtra( "magazzinoScegliArchivio", true );
					startActivityForResult( intento,4562 );
				} else if( id == 103 ) { // Nuova nota
					Note nota = new Note();
					nota.setValue( "" );
					((NoteContainer)oggetto).addNote( nota );
					Memoria.aggiungi( nota );
					startActivity( new Intent( this, Nota.class ) );
				} else if( id == 104 ) { // Nuova nota condivisa
					Quaderno.nuovaNota( this, oggetto );
				} else if( id == 105 ) { // Collega nota condivisa
					Intent intento = new Intent( this, Principe.class );
					intento.putExtra( "quadernoScegliNota", true );
					startActivityForResult( intento,7074 );
				} else if( id == 106 ) { // Cerca media locale
					F.appAcquisizioneImmagine( this, null, 4173, (MediaContainer)oggetto );
				} else if( id == 107 ) { // Cerca media condiviso
					F.appAcquisizioneImmagine( this, null, 4174, (MediaContainer)oggetto );
				} else if( id == 108 ) { // Collega media condiviso
					Intent inten = new Intent( this, Principe.class );
					inten.putExtra( "galleriaScegliMedia", true );
					startActivityForResult( inten,43616 );
				} else if( id == 109 ) { // Nuova fonte-nota
					SourceCitation citaz = new SourceCitation();
					citaz.setValue( "" );
					if( oggetto instanceof Note ) ((Note)oggetto).addSourceCitation( citaz );
					else ((SourceCitationContainer)oggetto).addSourceCitation( citaz );
					Memoria.aggiungi( citaz );
					startActivity( new Intent( this, CitazioneFonte.class ) );
				} else if( id == 110 ) {  // Nuova fonte
					Biblioteca.nuovaFonte( this, oggetto );
				} else if( id == 111 ) { // Collega fonte
					Intent intent = new Intent( this, Principe.class );
					intent.putExtra( "bibliotecaScegliFonte", true );
					startActivityForResult( intent, 5065 );
				} else if( id == 120 || id == 121 ) { // Crea nuovo familiare
					Intent intento = new Intent( this, EditaIndividuo.class );
					intento.putExtra( "idIndividuo", "TIZIO_NUOVO" );
					intento.putExtra( "idFamiglia", ((Family)oggetto).getId() );
					intento.putExtra( "relazione", id - 115 );
					startActivity( intento );
				} else if( id == 122 || id == 123 ) { // Collega persona esistente
					Intent intento = new Intent( this, Principe.class );
					intento.putExtra( "anagrafeScegliParente", true );
					intento.putExtra( "relazione", id - 117 );
					startActivityForResult( intento, 34417 );
				} else if( id == 124 ) { // Metti matrimonio
					EventFact nuovoEvento = new EventFact();
					nuovoEvento.setTag( "MARR" );
					nuovoEvento.setDate( "" );
					nuovoEvento.setPlace( "" );
					((Family)oggetto).addEventFact( nuovoEvento );
					Memoria.aggiungi( nuovoEvento );
					startActivity( new Intent( this, Evento.class ) );
				} else if( id == 125 ) { // Metti divorzio
					EventFact nuovoEvento = new EventFact();
					nuovoEvento.setTag( "DIV" );
					nuovoEvento.setDate( "" );
					((Family)oggetto).addEventFact( nuovoEvento );
					Memoria.aggiungi( nuovoEvento );
					startActivity( new Intent( this, Evento.class ) );
				} else if( id >= 200 ) { // Metti altro evento
					EventFact nuovoEvento = new EventFact();
					nuovoEvento.setTag( eventiVari.keySet().toArray( new String[eventiVari.size()] )[id - 200] );
					((Family)oggetto).addEventFact( nuovoEvento );
					creaPezzo( nuovoEvento.getDisplayType(), "", nuovoEvento, false );
				}
				return true;
			});
		});
		// Prova del menu: se è vuoto nasconde il fab
		if( !menuFAB(null).getMenu().hasVisibleItems() ) // todo ok?
			fab.hide();
	}

	// Menu del FAB: solo coi metodi che non sono già presenti in box
	PopupMenu menuFAB( View vista ) {
		PopupMenu popup = new PopupMenu( this, vista );
		Menu menu = popup.getMenu();
		String[] conIndirizzo = { "Www", "Email", "Phone", "Fax" }; // questi oggetti compaiono nel FAB di Evento se esiste un Indirizzo
		int u = 0;
		for( Uovo uovo : ovi ) {
			boolean giaMesso = false;
			boolean indirizzoPresente = false;
			for( int i = 0; i < box.getChildCount(); i++ ) {
				Object ogg = box.getChildAt(i).getTag(R.id.tag_oggetto);
				if( ogg != null && ogg.equals(uovo.oggetto) )
					giaMesso = true;
				if( ogg instanceof Address )
					indirizzoPresente = true;
			}
			if( !giaMesso ) {
				if( uovo.comune || ( indirizzoPresente && Arrays.asList(conIndirizzo).contains(uovo.oggetto) ) )
					menu.add( 0, u, 0, uovo.titolo );
			}
			u++;
		}
		if( oggetto instanceof Family ) {
			boolean ciSonoFigli = !((Family)oggetto).getChildRefs().isEmpty();
			SubMenu subNuovi = menu.addSubMenu( 0, 100, 0, R.string.new_relative );
			subNuovi.add( 0, 120, 0, ciSonoFigli ? R.string.parent : R.string.spouse );	// todo? nascondere Coniuge se ci sono già nella famiglia?
			// todo? o magari mettere avviso "Questa famiglia ha già i genitori... Vuoi forse creare un altro matrimonio per uno dei genitori?"
			subNuovi.add( 0, 121, 0, R.string.child );
			if( U.ciSonoIndividuiCollegabili( unRappresentanteDellaFamiglia ) ) {
				SubMenu subCollega = menu.addSubMenu( 0, 100, 0, R.string.link_person );
				subCollega.add( 0, 122, 0, ciSonoFigli ? R.string.parent : R.string.spouse );
				subCollega.add( 0, 123, 0, R.string.child );
			}
			SubMenu subEvento = menu.addSubMenu( 0, 100, 0, R.string.event );
			subEvento.add( 0, 124, 0, R.string.marriage );
			subEvento.add( 0, 125, 0, R.string.divorce );
			// Crea la lista degli altri eventi che si possono inserire
			Set<String> eventiIndividuo = EventFact.PERSONAL_EVENT_FACT_TAGS;
			for( String tag : EventFact.FAMILY_EVENT_FACT_TAGS )
				eventiIndividuo.remove( tag );
			eventiVari = new TreeMap<>( EventFact.DISPLAY_TYPE );
			for( String tag : eventiIndividuo )
				eventiVari.remove( tag );
			Iterator<Map.Entry<String,String>> eventi = eventiVari.entrySet().iterator();
			while( eventi.hasNext() ) { // Rimuove i tag lunghi e _speciali
				Map.Entry<String,String> ev = eventi.next();
				if( ev.getKey().length() > 4 || ev.getKey().startsWith( "_" ) )
					eventi.remove();
			}
			eventiVari.remove( "MARR" );
			eventiVari.remove( "DIV" );
			SubMenu subAltri = subEvento.addSubMenu( 0, 100, 0, R.string.other );
			int i = 0;
			for( TreeMap.Entry<String,String> event : eventiVari.entrySet() ) {
				String eventLabel = event.getValue();
				if( Globale.preferenze.esperto )
					eventLabel += " - " + event.getKey();
				subAltri.add( 0, 200+i, 0, eventLabel );
				i++;
			}
		}
		if( oggetto instanceof Source && findViewById(R.id.citazione_fonte) == null ) { // todo dubbio: non dovrebbe essere citazione_ARCHIVIO ?
			SubMenu subArchivio = menu.addSubMenu( 0, 100, 0, R.string.repository );
			subArchivio.add( 0, 101, 0, R.string.new_repository );
			if( !gc.getRepositories().isEmpty() )
				subArchivio.add( 0, 102, 0, R.string.link_repository );
		}
		if( oggetto instanceof NoteContainer ) {
			SubMenu subNota = menu.addSubMenu( 0, 100, 0, R.string.note );
			subNota.add( 0, 103, 0, R.string.new_note );
			subNota.add( 0, 104, 0, R.string.new_shared_note );
			if( !gc.getNotes().isEmpty() )
				subNota.add( 0, 105, 0, R.string.link_shared_note );
		}
		if( oggetto instanceof MediaContainer ) {
			SubMenu subMedia = menu.addSubMenu( 0, 100, 0, R.string.media );
			subMedia.add( 0, 106, 0, R.string.new_media );
			subMedia.add( 0, 107, 0, R.string.new_shared_media );
			if( !gc.getMedia().isEmpty() )
				subMedia.add( 0, 108, 0, R.string.link_shared_media );
		}
		if( ( oggetto instanceof SourceCitationContainer || oggetto instanceof Note ) && Globale.preferenze.esperto ) {
			SubMenu subFonte = menu.addSubMenu( 0, 100, 0, R.string.source );
			subFonte.add( 0, 109, 0, R.string.new_source_note );
			subFonte.add( 0, 110, 0, R.string.new_source );
			if( !gc.getSources().isEmpty() )
				subFonte.add( 0, 111, 0, R.string.link_source );
		}
		return popup;
	}

	// Imposta ciò che è stato scelto nelle liste
	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data ) {
		super.onActivityResult( requestCode, resultCode, data );
		if( resultCode == RESULT_OK ) {
			// Dal submenu 'Collega...' in FAB
			if( requestCode == 34417 ) { // Familiare scelto in Anagrafe
				Person aggiungendo = gc.getPerson(data.getStringExtra("idParente"));
				Famiglia.aggrega( aggiungendo, (Family)oggetto, data.getIntExtra("relazione",0) );
				U.salvaJson( true, Memoria.oggettoCapo() );
				return;
			} else if( requestCode == 5065 ) { // Fonte scelta in Biblioteca
				SourceCitation citaFonte = new SourceCitation();
				citaFonte.setRef( data.getStringExtra("idFonte") );
				if( oggetto instanceof Note ) ((Note)oggetto).addSourceCitation( citaFonte );
				else ((SourceCitationContainer)oggetto).addSourceCitation( citaFonte );
			} else if( requestCode == 7074 ) {  // Nota condivisa
				NoteRef rifNota = new NoteRef();
				rifNota.setRef( data.getStringExtra( "idNota" ) );
				((NoteContainer)oggetto).addNoteRef( rifNota );
			} else if( requestCode == 4173 ) { // File preso dal file manager o altra app diventa media locale
				Media media = new Media();
				media.setFileTag("FILE");
				((MediaContainer)oggetto).addMedia( media );
				if( F.proponiRitaglio( this, null, data, media ) ) {
					U.salvaJson( false, Memoria.oggettoCapo() );
					return;
				}
			} else if( requestCode == 4174 ) { // File preso dal file manager diventa media condiviso
				Media media = Galleria.nuovoMedia( oggetto );
				if( F.proponiRitaglio( this, null, data, media ) ) {
					U.salvaJson( false, media, Memoria.oggettoCapo() );
					return;
				}
			} else if( requestCode == 43616 ) { // Media da Galleria
				MediaRef rifMedia = new MediaRef();
				rifMedia.setRef( data.getStringExtra("idMedia") );
				((MediaContainer)oggetto).addMediaRef( rifMedia );
			} else if( requestCode == 4562  ) { // Archivio scelto in Magazzino da Fonte
				RepositoryRef archRef = new RepositoryRef();
				archRef.setRef( data.getStringExtra("idArchivio") );
				((Source)oggetto).setRepositoryRef( archRef );
			} else if( requestCode == 5173 ) { // Salva in Media un file scelto con le app da Immagine
				if( F.proponiRitaglio( this, null, data, (Media)oggetto ) ) {
					U.salvaJson( false, Memoria.oggettoCapo() );
					return;
				}
			} else if( requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE ) {
				F.fineRitaglioImmagine( data );
			}
			//  da menu contestuale 'Scegli...'
			if( requestCode == 5390  ) { // Imposta l'archivio che è stato scelto in Magazzino da ArchivioRef
				((RepositoryRef)oggetto).setRef( data.getStringExtra("idArchivio") );
			} else if( requestCode == 7047  ) { // Imposta la fonte che è stata scelta in Biblioteca da CitazioneFonte
				((SourceCitation)oggetto).setRef( data.getStringExtra("idFonte") );
			}
			U.salvaJson( true, Memoria.oggettoCapo() );
					 // indica di ricaricare sia questo Dettaglio grazie al seguente onRestart(), sia Individuo o Famiglia
		} else if( requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE )
			Globale.editato = true;
	}

	// Aggiorna i contenuti quando si torna indietro con backPressed()
	@Override
	public void onRestart() {
		super.onRestart();
		if( Globale.editato ) { // rinfresca il dettaglio
			ricrea();
		}
	}

	public void impagina() {}

	// Ricarica i contenuti del dettaglio, inclusa la data di modifica
	public void ricrea() {
		box.removeAllViews();
		ovi.clear();
		impagina();
	}

	// Inserisce la bava di lumaca
	public void mettiBava( String tag ) {
		mettiBava( tag, null );
	}

	public void mettiBava( String tag, String id ) {
		LinearLayout vistaBava = findViewById( R.id.dettaglio_bava );
		if( Globale.preferenze.esperto ) {
			vistaBava.removeAllViews();
			for( final Memoria.Passo passo : Memoria.getPila() ) {
				View vistaGoccia = LayoutInflater.from( this ).inflate( R.layout.pezzo_bava, box, false );
				TextView testoGoccia = vistaGoccia.findViewById( R.id.bava_goccia );
				if( Memoria.getPila().indexOf(passo) < Memoria.getPila().size() - 1 ) {
					if( passo.oggetto instanceof Visitable ) // le estensioni GedcomTag non sono Visitable ed è impossibile trovargli la pila
						vistaGoccia.setOnClickListener( v -> {
							new TrovaPila( gc, passo.oggetto );
							startActivity( new Intent(this, Memoria.classi.get(passo.oggetto.getClass())) );
						});
				} else {
					passo.tag = tag;
					testoGoccia.setTypeface( null, Typeface.BOLD );
				}
				testoGoccia.setText( passo.tag );
				vistaBava.addView( vistaGoccia );
			}
			// Agiunge l'id ai record capostipiti INDI, FAMI, REPO... ad es. 'SOUR S123'
			if( id != null ) {
				View vistaId = LayoutInflater.from( this ).inflate( R.layout.pezzo_bava, vistaBava, false );
				TextView testoId = vistaId.findViewById( R.id.bava_goccia );
				testoId.setText( id );
				testoId.setTypeface( null, Typeface.BOLD );
				vistaBava.addView( vistaId );
			}
		} else
			vistaBava.setVisibility( View.GONE );
	}

	// Restitisce 'oggetto' castato nella classe richiesta,
	// oppure una nuova istanza della classe ma in questo caso torna subito indietro
	public Object casta( Class classe ) {
		Object castato = null;
		try {
			// male che vada gli passa una nuova istanza della classe, giusto per non inchiodare il dettaglio
			if( classe.equals( GedcomTag.class ) )
				castato = new GedcomTag(null, null, null);
			else
				castato = classe.newInstance();
			castato = classe.cast( oggetto );
		} catch( Exception e ) {
			onBackPressed();
		}
		return castato;
	}

	class Uovo {
		String titolo;
		Object oggetto;
		boolean comune; // indica se farlo comparire nel menu del FAB per inserire il pezzo
		boolean multiLinea;
		Uovo( String titolo, Object oggetto, boolean comune, boolean multiLinea ) {
			this.titolo = titolo;
			this.oggetto = oggetto;
			this.comune = comune;
			this.multiLinea = multiLinea;
			ovi.add( this );
		}
	}

	// Il metodo base per i pezzi mono-linea
	public void metti( String titolo, String metodo ) {
		metti( titolo, metodo, true, false );
	}

	public void metti( String titolo, String metodo, boolean comune, boolean multiLinea ) {
		new Uovo( titolo, metodo, comune, multiLinea );
		String testo;
		try {
			testo = (String) oggetto.getClass().getMethod( "get" + metodo ).invoke( oggetto );
		} catch( Exception e ) {
			testo = "ERROR: " + e.getMessage();
		}
		// Mette il pezzo, tranne che per i Value con 'Y' per gli inesperti
		if(!( !Globale.preferenze.esperto && oggetto instanceof EventFact && metodo.equals("Value") && testo!=null && testo.equals("Y") && ((EventFact)oggetto).getTag()!=null &&
				( ((EventFact)oggetto).getTag().equals("BIRT") || ((EventFact)oggetto).getTag().equals("CHR") || ((EventFact)oggetto).getTag().equals("DEAT") || ((EventFact)oggetto).getTag().equals("MARR") ) ))
			creaPezzo( titolo, testo, metodo, multiLinea );
	}

	// Diverse firme per intercettare i vari tipi di oggetto
	public void metti( String titolo, Address indirizzo ) {
		Address indirizzoNonNullo = indirizzo==null ? new Address() : indirizzo;
		new Uovo( titolo, indirizzoNonNullo, true, false );
		creaPezzo( titolo, indirizzo(indirizzo), indirizzoNonNullo, false );
	}

	// Eventi della Famiglia
	public void metti( String titolo, EventFact evento ) {
		EventFact eventoNonNullo = evento==null ? new EventFact() : evento;
		creaPezzo( titolo, evento(evento), eventoNonNullo, false );
	}

	public View creaPezzo( String titolo, String testo, Object coso, boolean multiLinea ) {
		if( testo == null ) return null;
		View vistaPezzo = LayoutInflater.from(box.getContext()).inflate( R.layout.pezzo_fatto, box, false );
		box.addView( vistaPezzo );
		((TextView)vistaPezzo.findViewById( R.id.fatto_titolo )).setText( titolo );
		((TextView)vistaPezzo.findViewById( R.id.fatto_testo )).setText( testo );
		// todo: e se spostassi vistaEditabile.setText in edita()? Da testare con editoreData e onActivityResult()...
		EditText vistaEditabile = vistaPezzo.findViewById( R.id.fatto_edita );
		vistaEditabile.setText( testo ); // ok nella maggior parte dei casi, ma il testo mostrato in vistaEditabile non si aggiorna dopo onActivityResult()
		vistaEditabile.post( () -> {
			vistaEditabile.setText( testo ); // un po' ridondante, ma aggiorna il testo in vistaEditabile dopo onActivityResult()
		});
		if( multiLinea ) {
			vistaEditabile.setInputType( InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE );
			vistaEditabile.setVerticalScrollBarEnabled( true );
		}
		View.OnClickListener clicco = null;
		if( coso instanceof Integer ) {	// Nome e cognome in modalità inesperto
			clicco = this::edita;
		} else if( coso instanceof String ) { // Metodo
			clicco = this::edita;
			// Se si tratta di una data
			if( coso.equals("Date") ) {
				editoreData = vistaPezzo.findViewById( R.id.fatto_data );
				editoreData.inizia( vistaEditabile );
			}
		} else if( coso instanceof Address ) { // Indirizzo
			clicco = vista -> {
				Memoria.aggiungi( coso );
				startActivity( new Intent( this, Indirizzo.class ) );
			};
		} else if( coso instanceof EventFact ) { // Evento
			clicco = vista -> {
				Memoria.aggiungi( coso );
				startActivity( new Intent( this, Evento.class ) );
			};
			// Gli EventFact della famiglia possono avere delle note e dei media
			LinearLayout scatolaNote = vistaPezzo.findViewById( R.id.fatto_note );
			U.mettiNote( scatolaNote, coso, false );
			U.mettiMedia( scatolaNote, coso, false );
		} else if( coso instanceof GedcomTag ) { // Estensione
			clicco = vista -> {
				Memoria.aggiungi( coso );
				startActivity( new Intent( this, Estensione.class ) );
			};
		}
		vistaPezzo.setOnClickListener( clicco );
		registerForContextMenu( vistaPezzo );
		vistaPezzo.setTag( R.id.tag_oggetto, coso ); // Serve a vari processi per riconoscere il pezzo
		return vistaPezzo;
	}

	public void mettiEstensioni( ExtensionContainer contenitore ) {
		for( app.familygem.Estensione est : U.trovaEstensioni( contenitore ) ) {
			creaPezzo( est.nome, est.testo, est.gedcomTag, false );
		}
	}

	public static String indirizzo( Address ind ) {
		if( ind == null ) return null;
		String txt = "";
		if( ind.getValue() != null )
			txt = ind.getValue() + "\n";
		if( ind.getAddressLine1() != null )
			txt += ind.getAddressLine1() + "\n";
		if( ind.getAddressLine2() != null )
			txt += ind.getAddressLine2() + "\n";
		if( ind.getAddressLine3() != null )
			txt += ind.getAddressLine3() + "\n";
		if( ind.getPostalCode() != null ) txt += ind.getPostalCode() + " ";
		if( ind.getCity() != null ) txt += ind.getCity() + " ";
		if( ind.getState() != null ) txt += ind.getState();
		if( ind.getPostalCode()!=null || ind.getCity()!=null || ind.getState()!=null )
			txt += "\n";
		if( ind.getCountry() != null )
			txt += ind.getCountry();
		if( txt.endsWith("\n") )
			txt = txt.substring( 0, txt.length() - 1 ).trim();
		return txt;
	}

	// Elimina un indirizzo dai 3 possibili contenitori
	public void eliminaIndirizzo( Object contenitore ) {
		if( contenitore instanceof EventFact )
			((EventFact)contenitore).setAddress( null );
		else if( contenitore instanceof Repository )
			((Repository)contenitore).setAddress( null );
		else if( contenitore instanceof Submitter )
			((Submitter)contenitore).setAddress( null );
	}

	// Compone il testo dell'evento in Famiglia
	public static String evento( EventFact ef ) {
		if( ef == null ) return null;
		String txt = "";
		if( ef.getValue() != null ) {
			if( ef.getValue().equals("Y") && ef.getTag() != null && ef.getTag().equals("MARR") )
				txt = Globale.contesto.getString(R.string.yes);
			else
				txt = ef.getValue();
			txt += "\n";
		}
		if( ef.getDate() != null )
			txt += ef.getDate() + "\n";
		if( ef.getPlace() != null )
			txt += ef.getPlace();
		Address indirizzo = ef.getAddress();
		if( indirizzo != null )
			txt += indirizzo(indirizzo) + "\n";
		if( txt.endsWith( "\n" ) )
			txt = txt.substring( 0, txt.length() - 1 );
		return txt;
	}

	EditText vistaEdita;
	void edita( View vistaPezzo ) {
		FloatingActionButton fab = findViewById( R.id.fab );
		ActionBar barra = getSupportActionBar();

		// Termina l'eventuale editazione di un altro pezzo
		for( int i=0; i < box.getChildCount(); i++ ) {
			View altroPezzo = box.getChildAt(i);
			EditText vistaEdita = altroPezzo.findViewById( R.id.fatto_edita );
			if( vistaEdita != null && vistaEdita.isShown() ) {
				TextView vistaTesto = altroPezzo.findViewById( R.id.fatto_testo );
				if( !vistaEdita.getText().toString().equals(vistaTesto.getText().toString()) ) // se c'è stata editazione
					salva( altroPezzo, barra, fab );
				else
					ripristina( altroPezzo, barra, fab );
			}
		}
		// Poi rende editabile questo pezzo
		TextView vistaTesto = vistaPezzo.findViewById( R.id.fatto_testo );
		vistaTesto.setVisibility( View.GONE );
		fab.hide();
		Object contenutoPezzo = vistaPezzo.getTag(R.id.tag_oggetto);
		boolean mostraTastiera = false;
		vistaEdita = vistaPezzo.findViewById( R.id.fatto_edita );
		// Luogo
		if( contenutoPezzo.equals("Place") ) {
			mostraTastiera = true;
			// Se non l'ha già fatto, sostituisce vistaEdita con TrovaLuogo
			if( !(vistaEdita instanceof TrovaLuogo) ) {
				ViewGroup parent = (ViewGroup) vistaPezzo;  // todo: si potrebbe usare direttamente vistaPezzo se fosse un ViewGroup o LinearLayout anzicé View
				int index = parent.indexOfChild( vistaEdita );
				parent.removeView( vistaEdita );
				vistaEdita = new TrovaLuogo( vistaEdita.getContext(), null );
				vistaEdita.setId( R.id.fatto_edita );
				vistaEdita.setText( vistaTesto.getText() );
				parent.addView( vistaEdita, index );
			} else
				vistaEdita.setVisibility( View.VISIBLE );
		} // Name type
		else if( oggetto instanceof Name && contenutoPezzo.equals("Type") ) {
			if( !(vistaEdita instanceof TipoNome) ) {
				ViewGroup parent = (ViewGroup) vistaPezzo;
				int index = parent.indexOfChild( vistaEdita );
				parent.removeView( vistaEdita );
				vistaEdita =  new TipoNome( vistaEdita.getContext(), null );
				vistaEdita.setText( vistaTesto.getText() );
				parent.addView( vistaEdita, index );
			} else
				vistaEdita.setVisibility( View.VISIBLE );
		} // Data
		else if( contenutoPezzo.equals("Date") ) {
			vistaEdita.setVisibility( View.VISIBLE );
		} // Tutti gli altri casi normali di editazione
		else {
			mostraTastiera = true;
			vistaEdita.setVisibility( View.VISIBLE );
		}
		if( mostraTastiera ) {
			InputMethodManager imm = (InputMethodManager) getSystemService( Context.INPUT_METHOD_SERVICE );
			imm.toggleSoftInput( InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY );
		}
		vistaEdita.requestFocus();
		vistaEdita.setSelection( vistaEdita.getText().length() );	// cursore alla fine

		// Intercetta il 'Done' e 'Next' sulla tastiera
		vistaEdita.setOnEditorActionListener( (vista, actionId, keyEvent) -> {
			if( actionId == EditorInfo.IME_ACTION_DONE )
				salva( vistaPezzo, barra, fab );
			else if( actionId == EditorInfo.IME_ACTION_NEXT ) {
				if( !vistaEdita.getText().toString().equals(vistaTesto.getText().toString()) )
					salva( vistaPezzo, barra, fab );
				else
					ripristina( vistaPezzo, barra, fab );
				View nextPezzo = box.getChildAt(box.indexOfChild(vistaPezzo) + 1);
				if( nextPezzo != null && nextPezzo.getTag(R.id.tag_oggetto) instanceof String )
					edita(nextPezzo);
			}
			return false;
		});

		// ActionBar personalizzata
		barra.setDisplayHomeAsUpEnabled( false );	// nasconde freccia <-
		qualeMenu = 0;
		invalidateOptionsMenu();
		View barraAzione = getLayoutInflater().inflate( R.layout.barra_edita, new LinearLayout(box.getContext()), false);
		barraAzione.findViewById(R.id.edita_annulla).setOnClickListener( v -> {
			vistaEdita.setText( vistaTesto.getText() );
			ripristina( vistaPezzo, barra, fab );
		});
		barraAzione.findViewById(R.id.edita_salva).setOnClickListener( v -> salva( vistaPezzo, barra, fab ) );
		barra.setCustomView( barraAzione );
		barra.setDisplayShowCustomEnabled( true );
	}

	void salva( View vistaPezzo, ActionBar barra, FloatingActionButton fab ) {
		if( editoreData != null ) editoreData.chiudi(); // In sostanza solo per aggiungere le parentesi alla data frase
		String testo = vistaEdita.getText().toString().trim();
		Object oggettoPezzo = vistaPezzo.getTag(R.id.tag_oggetto);
		if( oggettoPezzo instanceof Integer ) { // Salva nome e cognome per inesperti
			String nome = ((EditText)box.getChildAt(0).findViewById(R.id.fatto_edita)).getText().toString();
			String cognome = ((EditText)box.getChildAt(1).findViewById(R.id.fatto_edita)).getText().toString();
			((Name)oggetto).setValue( nome + " /" + cognome + "/" );
		} else try { // Tutti gli altri normali metodi
			oggetto.getClass().getMethod( "set" + oggettoPezzo, String.class ).invoke( oggetto, testo );
		} catch( Exception e ) {
			//e.printStackTrace();
			Toast.makeText( box.getContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
			return;	// in caso di errore rimane in modalità editore
		}
		((TextView)vistaPezzo.findViewById( R.id.fatto_testo )).setText( testo );
		ripristina( vistaPezzo, barra, fab );
		U.salvaJson( true, Memoria.oggettoCapo() );
		/*if( Memoria.getPila().size() == 1 ) {
			ricrea(); // Todo Bisognerebbe aggiornare la data Cambiamento del record, però magari senza ricaricare tutto.
		}*/
		// In immagine modificato il percorso aggiorna l'immagine
		if( this instanceof Immagine && oggettoPezzo.equals("File") )
			((Immagine)this).aggiornaImmagine();
		// Se ha modificato un autore chiede di referenziarlo in header
		else if( oggetto instanceof Submitter )
			U.autorePrincipale( this, ((Submitter)oggetto).getId() );
	}

	// Operazioni comuni a Salva e Annulla
	void ripristina( View vistaPezzo, ActionBar barra, FloatingActionButton fab ) {
		vistaEdita.setVisibility( View.GONE );
		vistaPezzo.findViewById( R.id.fatto_data ).setVisibility( View.GONE );
		vistaPezzo.findViewById( R.id.fatto_testo ).setVisibility( View.VISIBLE );
		barra.setDisplayShowCustomEnabled( false );	// nasconde barra personalizzata
		barra.setDisplayHomeAsUpEnabled( true );
		qualeMenu = 1;
		invalidateOptionsMenu();
		InputMethodManager imm = (InputMethodManager)getSystemService(getApplicationContext().INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow( vistaPezzo.getWindowToken(), 0 );
		if( !(oggetto instanceof Note && !Globale.preferenze.esperto) ) // Le note in modalità inesperto non hanno fab
			fab.show();
	}

	// Menu opzioni
	int qualeMenu = 1;	// serve per nasconderlo quando si entra in modalità editore
	@Override
	public boolean onCreateOptionsMenu( Menu menu ) {
		if( qualeMenu == 1 ) {	// Menu standard della barra
			if( oggetto instanceof Submitter && ( gc.getHeader()==null || // Autore non principale
					gc.getHeader().getSubmitter(gc)==null || !gc.getHeader().getSubmitter(gc).equals(oggetto) ))
				menu.add( 0, 1, 0, R.string.make_default );
			if( oggetto instanceof Media ) {
				if( box.findViewById(R.id.immagine_foto).getTag(R.id.tag_tipo_file).equals(1) )
					menu.add( 0, 2, 0, R.string.crop );
				menu.add( 0, 3, 0, R.string.choose_file );
			}
			if( oggetto instanceof Family )
				menu.add( 0, 4, 0, R.string.delete );
			else if( !(oggetto instanceof Submitter && U.autoreHaCondiviso((Submitter)oggetto)) ) // autore che ha condiviso non può essere eliminato
				menu.add( 0, 5, 0, R.string.delete );
		}
		return true;
	}
	@Override // è evocato quando viene scelta una voce del menu E cliccando freccia indietro
	public boolean onOptionsItemSelected( MenuItem item ) {
		int id = item.getItemId();
		if( id == 1 ) { // Autore principale
			Podio.autorePrincipale( (Submitter)oggetto );
		} else if( id == 2 ) { // Immagine: ritaglia
			croppaImmagine( box );
		} else if( id == 3 ) { // Immagine: scegli
			F.appAcquisizioneImmagine( this, null, 5173, null );
		} else if( id == 4 ) {	// Famiglia
			Family fam = (Family) oggetto;
			if( fam.getHusbandRefs().size() + fam.getWifeRefs().size() + fam.getChildRefs().size() > 0 ) {
				new AlertDialog.Builder(this).setMessage( R.string.really_delete_family )
						.setPositiveButton(android.R.string.yes, (dialog, i) -> {
							Chiesa.eliminaFamiglia( fam.getId() );
							onBackPressed();
						}).setNeutralButton(android.R.string.cancel, null).show();
			} else {
				Chiesa.eliminaFamiglia( fam.getId() );
				onBackPressed();
			}
		} else if( id == 5 ) { // Tutti gli altri
			// todo: conferma eliminazione di tutti gli oggetti..
			elimina();
			U.salvaJson(true); // l'aggiornamento delle date avviene negli Override di elimina()
			onBackPressed();
		} else if( id == android.R.id.home ){
			onBackPressed();
		}
		return true;
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		if( oggetto instanceof EventFact )
			Evento.ripulisciTag( (EventFact)oggetto );
		Memoria.arretra();
	}

	public void elimina() {}

	// Menu contestuale
	View vistaPezzo;	// testo editabile, note, citazioni, media...
	Object oggettoPezzo;
	Person pers; // siccome usato molto ne facciamo un oggettoPezzo a sè stante
	@Override
	public void onCreateContextMenu( ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info ) {	// info è null
		if( qualeMenu != 0 ) {	// Se siamo in modalità edita mostra i menu editore
			vistaPezzo = vista;
			oggettoPezzo = vista.getTag( R.id.tag_oggetto );
			if( oggettoPezzo instanceof Person ) {
				pers = (Person) oggettoPezzo;
				Family fam = (Family) oggetto;
				// Genera le etichette per le voci 'famiglia' (come figlio e come coniuge)
				String[] etichetteFam = { null, null };
				if( pers.getParentFamilies(gc).size() > 1 && pers.getSpouseFamilies(gc).size() > 1 ) {
					etichetteFam[0] = getString(R.string.family_as_child);
					etichetteFam[1] = getString(R.string.family_as_spouse);
				}
				menu.add(0, 10, 0, R.string.diagram );
				menu.add(0, 11, 0, R.string.card );
				if( etichetteFam[0] != null )
					menu.add( 0, 12, 0, etichetteFam[0] );
				if( etichetteFam[1] != null )
					menu.add( 0, 13, 0, etichetteFam[1] );
				if( fam.getChildren(gc).indexOf(pers) > 0 )
					menu.add( 0, 14, 0, R.string.move_before );
				if( fam.getChildren(gc).indexOf(pers) < fam.getChildren(gc).size()-1 && fam.getChildren(gc).indexOf(pers) >= 0 )
					// così esclude i genitori il cui indice è -1
					menu.add( 0, 15, 0, R.string.move_after );
				menu.add( 0, 16, 0, R.string.modify );
				menu.add( 0, 17, 0, R.string.unlink );
				menu.add( 0, 18, 0, R.string.delete );
			} else if( oggettoPezzo instanceof Note ) {
				menu.add( 0, 20, 0, R.string.copy );
				if( ( (Note) oggettoPezzo ).getId() != null )
					menu.add( 0, 21, 0, R.string.unlink );
				menu.add( 0, 22, 0, R.string.delete );
			} else if( oggettoPezzo instanceof SourceCitation ) {
				menu.add( 0, 30, 0, R.string.copy );
				menu.add( 0, 31, 0, R.string.delete );
			} else if( oggettoPezzo instanceof Media ) {
				if( ( (Media) oggettoPezzo ).getId() != null )
					menu.add( 0, 40, 0, R.string.unlink );
				menu.add( 0, 41, 0, R.string.delete );
			} else if( oggettoPezzo instanceof Address ) {
				menu.add( 0, 50, 0, R.string.copy );
				menu.add( 0, 51, 0, R.string.delete );
			} else if( oggettoPezzo instanceof EventFact ) {
				menu.add( 0, 55, 0, R.string.copy );
				menu.add( 0, 56, 0, R.string.delete );
			} else if( oggettoPezzo instanceof GedcomTag ) {
				menu.add( 0, 60, 0, R.string.copy );
				menu.add( 0, 61, 0, R.string.delete );
			} else if( oggettoPezzo instanceof Source ) {
				menu.add( 0, 70, 0, R.string.copy );
				menu.add( 0, 71, 0, R.string.choose_source );
			} else if( oggettoPezzo instanceof RepositoryRef ) {
				menu.add( 0, 80, 0, R.string.copy );
				menu.add( 0, 81, 0, R.string.delete );
			} else if( oggettoPezzo instanceof Repository ) {
				menu.add( 0, 90, 0, R.string.copy );
				menu.add( 0, 91, 0, R.string.choose_repository );
			} else if( oggettoPezzo instanceof Integer ) {
				if( oggettoPezzo.equals( 43614 ) ) { // Immaginona
					// è un'immagine ritagliabile
					if( vistaPezzo.findViewById(R.id.immagine_foto).getTag(R.id.tag_tipo_file).equals(1) )
						menu.add( 0, 100, 0, R.string.crop );
					menu.add( 0, 101, 0, R.string.choose_file );
				} else if( oggettoPezzo.equals(4043) || oggettoPezzo.equals(6064) ) // Nome e cognome per inesperti
					menu.add( 0, 0, 0, R.string.copy );
			} else if( oggettoPezzo instanceof String ) {
				menu.add( 0, 0, 0, R.string.copy );
				menu.add( 0, 1, 0, R.string.delete );
			}
		}
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		switch( item.getItemId() ) {
			// TODo tutti gli elimina necessitano di conferma eliminazione
			// Copia
			case 0:	// Pezzo editabile
			case 50: // Address
			case 55: // Evento
			case 60: // Estensione
				U.copiaNegliAppunti( ((TextView)vistaPezzo.findViewById( R.id.fatto_titolo )).getText(),
						((TextView)vistaPezzo.findViewById(R.id.fatto_testo)).getText() );
				return true;
			case 1: // Elimina pezzo editabile
				try {
					oggetto.getClass().getMethod( "set" + oggettoPezzo, String.class ).invoke( oggetto, (Object)null );
				} catch( Exception e ) {
					Toast.makeText( box.getContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
					break;
				}
				break;
			case 10: // Diagramma
				U.qualiGenitoriMostrare( this, pers, 1 );
				return true;
			case 11: // Scheda persona
				Memoria.setPrimo( pers );
				startActivity( new Intent(this, Individuo.class) );
				return true;
			case 12: // Famiglia come figlio
				U.qualiGenitoriMostrare( this, pers, 2 );
				return true;
			case 13: // Famiglia come coniuge
				U.qualiConiugiMostrare( this, pers, null );
				return true;
			case 14: // Figlio sposta su
				Family fa = (Family) oggetto;
				ChildRef refBimbo = fa.getChildRefs().get( fa.getChildren(gc).indexOf(pers) );
				fa.getChildRefs().add( fa.getChildRefs().indexOf(refBimbo)-1, refBimbo );
				fa.getChildRefs().remove( fa.getChildRefs().lastIndexOf(refBimbo) );
				break;
			case 15: // Figlio sposta giù
				Family f = (Family) oggetto;
				ChildRef refBimb = f.getChildRefs().get( f.getChildren(gc).indexOf(pers) );
				f.getChildRefs().add( f.getChildRefs().indexOf(refBimb)+2, refBimb );
				f.getChildRefs().remove( f.getChildRefs().indexOf(refBimb) );
				break;
			case 16: // Modifica
				Intent i = new Intent( this, EditaIndividuo.class );
				i.putExtra( "idIndividuo", pers.getId() );
				startActivity( i );
				return true;
			case 17: // Scollega
				Famiglia.scollega( (SpouseFamilyRef) vistaPezzo.getTag(R.id.tag_spouse_family_ref),
						(SpouseRef) vistaPezzo.getTag(R.id.tag_spouse_ref) );
				U.aggiornaDate( pers );
				trovaUnAltroRappresentanteDellaFamiglia( pers );
				break;
			case 18: // Elimina membro
				new AlertDialog.Builder(this).setMessage(R.string.really_delete_person)
						.setPositiveButton(R.string.delete, (dialog, id) -> {
							Anagrafe.eliminaPersona(this, pers.getId());
							box.removeView( vistaPezzo );
							trovaUnAltroRappresentanteDellaFamiglia( pers );
						}).setNeutralButton(R.string.cancel, null).show();
				return true;
			case 20: // Nota
				U.copiaNegliAppunti( getText(R.string.note), ((TextView)vistaPezzo.findViewById(R.id.nota_testo)).getText() );
				return true;
			case 21:
				U.scollegaNota( (Note)oggettoPezzo, oggetto, null );
				break;
			case 22:
				Object[] capi = U.eliminaNota( (Note)oggettoPezzo, vistaPezzo );
				U.salvaJson( true, capi );
				return true;
			case 30: // Citazione fonte
				U.copiaNegliAppunti( getText(R.string.source_citation),
						((TextView)vistaPezzo.findViewById( R.id.fonte_testo )).getText() + "\n"
						+ ((TextView)vistaPezzo.findViewById(R.id.citazione_testo)).getText() );
				return true;
			case 31:
				if( oggetto instanceof Note ) // Note non estende SourceCitationContainer
					((Note)oggetto).getSourceCitations().remove( oggettoPezzo );
				else
					((SourceCitationContainer)oggetto).getSourceCitations().remove( oggettoPezzo );
				Memoria.annullaIstanze(oggettoPezzo);
				break;
			case 40: // Media
				Galleria.scollegaMedia( ((Media)oggettoPezzo).getId(), (MediaContainer)oggetto, null );
				break;
			case 41:
				Object[] capiMedia = Galleria.eliminaMedia( (Media)oggettoPezzo, null );
				U.salvaJson( true, capiMedia ); // un media condiviso può dover aggiornare le date di più capi
				ricrea();
				return true;
			case 51:
				eliminaIndirizzo( oggetto );
				break;
			case 56: // Evento di Famiglia
				((Family)oggetto).getEventsFacts().remove( oggettoPezzo );
				Memoria.annullaIstanze( oggettoPezzo );
				break;
			case 61: // Estensione
				U.eliminaEstensione( (GedcomTag)oggettoPezzo, oggetto, null );
				break;
			// Fonte
			case 70: // Copia
				U.copiaNegliAppunti( getText(R.string.source), ((TextView)vistaPezzo.findViewById(R.id.fonte_testo)).getText() );
				return true;
			case 71: // Scegli in Biblioteca
				Intent inte = new Intent( this, Principe.class );
				inte.putExtra( "bibliotecaScegliFonte", true );
				startActivityForResult( inte,7047 );
				return true;
			// Citazione archivio
			case 80: // Copia
				U.copiaNegliAppunti( getText(R.string.repository_citation),
						((TextView)vistaPezzo.findViewById( R.id.fonte_testo )).getText() + "\n"
						+ ((TextView)vistaPezzo.findViewById(R.id.citazione_testo)).getText() );
				return true;
			case 81: // Elimina
				((Source)oggetto).setRepositoryRef( null );
				Memoria.annullaIstanze(oggettoPezzo);
				break;
			// Archivio
			case 90: // Copia
				U.copiaNegliAppunti( getText(R.string.repository), ((TextView)vistaPezzo.findViewById(R.id.fonte_testo)).getText() );
				return true;
			case 91: // Scegli in Magazzino
				Intent intn = new Intent( this, Principe.class );
				intn.putExtra( "magazzinoScegliArchivio", true );
				startActivityForResult( intn,5390 );
				return true;
			case 100: // Immaginona ritaglia
				croppaImmagine( vistaPezzo );
				return true;
			case 101: // scegli immagine
				F.appAcquisizioneImmagine( this, null, 5173, null );
				return true;
			default:
				return false;
		}
		// Prima ricrea la pagina e poi salva, che per alberi grossi può metterci alcuni secondi
		//closeContextMenu(); // Inutile. La chiusura del menu aspetta la fine del salvataggio,
		// a meno di mettere salvaJson() dentro un postDelayed() di almeno 500 ms
		U.aggiornaDate( Memoria.oggettoCapo() );
		ricrea();
		U.salvaJson( true, null );
		return true;
	}

	// Corregge unRappresentanteDellaFamiglia per far comparire correttamente "Collega persona esistente" nel menu
	private void trovaUnAltroRappresentanteDellaFamiglia( Person p ) {
		if( unRappresentanteDellaFamiglia.equals( p ) ) {
			Family fam = (Family) oggetto;
			if( !fam.getHusbands(gc).isEmpty() )
				unRappresentanteDellaFamiglia = fam.getHusbands(gc).get(0);
			else if( !fam.getWives(gc).isEmpty() )
				unRappresentanteDellaFamiglia = fam.getWives(gc).get(0);
			else if( !fam.getChildren(gc).isEmpty() )
				unRappresentanteDellaFamiglia = fam.getChildren(gc).get(0);
			else
				unRappresentanteDellaFamiglia = null;
		}
	}

	// Riceve una View in cui c'è l'immagine da ritagliare e avvia il ritaglio
	private void croppaImmagine( View vista ) {
		ImageView vistaImg = vista.findViewById( R.id.immagine_foto );
		File fileMedia = null;
		String percorso = (String) vistaImg.getTag( R.id.tag_percorso );
		if( percorso != null )
			fileMedia = new File( percorso );
		Uri uriMedia = (Uri) vistaImg.getTag( R.id.tag_uri );
		Globale.mediaCroppato = (Media)oggetto;
		F.tagliaImmagine( this, fileMedia, uriMedia, null );
	}

	// Chiude tastiera eventualmente visibile
	@Override
	protected void onPause() {
		super.onPause();
		if( vistaEdita != null ) {
			InputMethodManager imm = (InputMethodManager) getSystemService( Context.INPUT_METHOD_SERVICE );
			imm.hideSoftInputFromWindow( vistaEdita.getWindowToken(), 0 );
		}
	}

	@Override
	public void onRequestPermissionsResult( int codice, String[] permessi, int[] accordi ) {
		F.risultatoPermessi( this, null, codice, permessi, accordi,
				oggetto instanceof MediaContainer ? (MediaContainer)oggetto : null );
				// Immagine ha 'oggetto' instance di Media, non di MediaContainer
	}
}