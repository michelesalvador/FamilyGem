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
import androidx.core.util.Pair;
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
import java.util.Collections;
import java.util.List;
import app.familygem.detail.CitazioneFonte;
import app.familygem.detail.Estensione;
import app.familygem.detail.Evento;
import app.familygem.detail.Famiglia;
import app.familygem.detail.Immagine;
import app.familygem.detail.Indirizzo;
import app.familygem.detail.Nota;
import app.familygem.visitor.TrovaPila;
import static app.familygem.Global.gc;

public class Dettaglio extends AppCompatActivity {

	public LinearLayout box;
	public Object object; // Name Media SourceCitation ecc.
	List<Egg> eggs = new ArrayList<>(); // List of all the possible editable pieces
	List<Pair<String, String>> otherEvents; // Eventi per il FAB di Famiglia
	public Person unRappresentanteDellaFamiglia; // una Person della Famiglia per nascondere nel FAB 'Collega persona'
	EditoreData editoreData;

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		setContentView( R.layout.dettaglio );
		getSupportActionBar().setDisplayHomeAsUpEnabled( true );
		box = findViewById( R.id.dettaglio_scatola );
		U.gedcomSicuro( gc );

		object = Memoria.getOggetto();
		if( object == null) {
			onBackPressed(); // salta tutti gli altri dettagli senza oggetto
		} else
			impagina();

		// List of other events
		String[] otherEventTags = {"ANUL", "CENS", "DIVF", "ENGA", "MARB", "MARC", "MARL", "MARS", "RESI", "EVEN", "NCHI"};
		otherEvents = new ArrayList<>();
		for( String tag : otherEventTags ) {
			EventFact event = new EventFact();
			event.setTag(tag);
			String label = event.getDisplayType();
			if( Global.settings.expert )
				label += " — " + tag;
			otherEvents.add(new Pair<>(tag, label));
		}
		Collections.sort(otherEvents, (item1, item2) -> item1.second.compareTo(item2.second));

		FloatingActionButton fab = findViewById(R.id.fab);
		fab.setOnClickListener(view -> {
			PopupMenu popup = menuFAB(view);
			popup.show();
			popup.setOnMenuItemClickListener(item -> {
				// FAB + mette un nuovo uovo e lo rende subito editabile
				int id = item.getItemId();
				boolean toBeSaved = false;
				if( id < 100 ) {
					Object coso = eggs.get(id).yolk;
					if( coso instanceof Address ) {	// coso è un new Address()
						if( object instanceof EventFact )
							((EventFact)object).setAddress( (Address)coso );
						else if( object instanceof Submitter )
							((Submitter)object).setAddress( (Address)coso );
						else if( object instanceof Repository )
							((Repository)object).setAddress( (Address)coso );
					}
					// Tag necessari per poi esportare in Gedcom
					if( object instanceof Name && coso.equals("Type") ) {
						((Name)object).setTypeTag("TYPE");
					} else if( object instanceof Repository ) {
						if( coso.equals("Www") )
							((Repository)object).setWwwTag("WWW");
						if( coso.equals("Email") )
							((Repository)object).setEmailTag("EMAIL");
					} else if( object instanceof Submitter ) {
						if( coso.equals("Www") )
							((Submitter)object).setWwwTag("WWW");
						if( coso.equals("Email") )
							((Submitter)object).setEmailTag("EMAIL");
					}
					View pezzo = placePiece(eggs.get(id).title, "", coso, eggs.get(id).multiLine);
					if( coso instanceof String )
						edit(pezzo);
					// todo : aprire Address nuovo per editarlo
				} else if( id == 101 ) {
					Magazzino.nuovoArchivio(this, (Source)object);
				} else if( id == 102 ) {
					Intent intento = new Intent(this, Principal.class);
					intento.putExtra("magazzinoScegliArchivio", true);
					startActivityForResult(intento, 4562);
				} else if( id == 103 ) { // Nuova nota
					Note note = new Note();
					note.setValue("");
					((NoteContainer)object).addNote(note);
					Memoria.aggiungi(note);
					startActivity(new Intent(this, Nota.class));
					toBeSaved = true;
				} else if( id == 104 ) { // Nuova nota condivisa
					Quaderno.newNote(this, object);
				} else if( id == 105 ) { // Collega nota condivisa
					Intent intento = new Intent(this, Principal.class);
					intento.putExtra("quadernoScegliNota", true);
					startActivityForResult(intento, 7074);
				} else if( id == 106 ) { // Cerca media locale
					F.appAcquisizioneImmagine(this, null, 4173, (MediaContainer)object);
				} else if( id == 107 ) { // Cerca media condiviso
					F.appAcquisizioneImmagine(this, null, 4174, (MediaContainer)object);
				} else if( id == 108 ) { // Collega media condiviso
					Intent inten = new Intent(this, Principal.class);
					inten.putExtra("galleriaScegliMedia", true);
					startActivityForResult(inten, 43616);
				} else if( id == 109 ) { // Nuova fonte-nota
					SourceCitation citaz = new SourceCitation();
					citaz.setValue("");
					if( object instanceof Note ) ((Note)object).addSourceCitation(citaz);
					else ((SourceCitationContainer)object).addSourceCitation(citaz);
					Memoria.aggiungi(citaz);
					startActivity(new Intent(this, CitazioneFonte.class));
					toBeSaved = true;
				} else if( id == 110 ) {  // Nuova fonte
					Biblioteca.nuovaFonte(this, object);
				} else if( id == 111 ) { // Collega fonte
					Intent intent = new Intent(this, Principal.class);
					intent.putExtra("bibliotecaScegliFonte", true);
					startActivityForResult(intent, 5065);
				} else if( id == 120 || id == 121 ) { // Crea nuovo familiare
					Intent intent = new Intent(this, EditaIndividuo.class);
					intent.putExtra("idIndividuo", "TIZIO_NUOVO");
					intent.putExtra("idFamiglia", ((Family)object).getId());
					intent.putExtra("relazione", id - 115);
					startActivity(intent);
				} else if( id == 122 || id == 123 ) { // Collega persona esistente
					Intent intent = new Intent(this, Principal.class);
					intent.putExtra("anagrafeScegliParente", true);
					intent.putExtra("relazione", id - 117);
					startActivityForResult(intent, 34417);
				} else if( id == 124 ) { // Metti matrimonio
					EventFact marriage = new EventFact();
					marriage.setTag("MARR");
					marriage.setDate("");
					marriage.setPlace("");
					marriage.setType("");
					((Family)object).addEventFact(marriage);
					Memoria.aggiungi(marriage);
					startActivity(new Intent(this, Evento.class));
					toBeSaved = true;
				} else if( id == 125 ) { // Metti divorzio
					EventFact divorce = new EventFact();
					divorce.setTag("DIV");
					divorce.setDate("");
					((Family)object).addEventFact(divorce);
					Memoria.aggiungi(divorce);
					startActivity(new Intent(this, Evento.class));
					toBeSaved = true;
				} else if( id >= 200 ) { // Metti altro evento
					EventFact event = new EventFact();
					event.setTag(otherEvents.get(id - 200).first);
					((Family)object).addEventFact(event);
					refresh();
					toBeSaved = true;
				}
				if( toBeSaved )
					U.save(true, object);
				return true;
			});
		});
		// Prova del menu: se è vuoto nasconde il fab
		if( !menuFAB(null).getMenu().hasVisibleItems() ) // todo ok?
			fab.hide();
	}

	// Menu del FAB: solo coi metodi che non sono già presenti in box
	PopupMenu menuFAB(View vista) {
		PopupMenu popup = new PopupMenu(this, vista);
		Menu menu = popup.getMenu();
		String[] conIndirizzo = {"Www", "Email", "Phone", "Fax"}; // questi oggetti compaiono nel FAB di Evento se esiste un Indirizzo
		int u = 0;
		for( Egg egg : eggs ) {
			boolean giaMesso = false;
			boolean indirizzoPresente = false;
			for( int i = 0; i < box.getChildCount(); i++ ) {
				Object ogg = box.getChildAt(i).getTag(R.id.tag_oggetto);
				if( ogg != null && ogg.equals(egg.yolk) )
					giaMesso = true;
				if( ogg instanceof Address )
					indirizzoPresente = true;
			}
			if( !giaMesso ) {
				if( egg.common || (indirizzoPresente && Arrays.asList(conIndirizzo).contains(egg.yolk)) )
					menu.add(0, u, 0, egg.title);
			}
			u++;
		}
		if( object instanceof Family ) {
			boolean ciSonoFigli = !((Family)object).getChildRefs().isEmpty();
			SubMenu newMemberMenu = menu.addSubMenu(0, 100, 0, R.string.new_relative);
			// Not-expert can add maximum two parents // todo: expert too??
			if( !(!Global.settings.expert && ((Family)object).getHusbandRefs().size() + ((Family)object).getWifeRefs().size() >= 2) )
				newMemberMenu.add(0, 120, 0, ciSonoFigli ? R.string.parent : R.string.partner);
			newMemberMenu.add(0, 121, 0, R.string.child);
			if( U.ciSonoIndividuiCollegabili(unRappresentanteDellaFamiglia) ) {
				SubMenu linkMemberMenu = menu.addSubMenu(0, 100, 0, R.string.link_person);
				if( !(!Global.settings.expert && ((Family)object).getHusbandRefs().size() + ((Family)object).getWifeRefs().size() >= 2) )
					linkMemberMenu.add(0, 122, 0, ciSonoFigli ? R.string.parent : R.string.partner);
				linkMemberMenu.add(0, 123, 0, R.string.child);
			}
			SubMenu eventSubMenu = menu.addSubMenu(0, 100, 0, R.string.event);
			String marriageLabel = getString(R.string.marriage) + " / " + getString(R.string.relationship);
			String divorceLabel = getString(R.string.divorce) + " / " + getString(R.string.separation);
			if( Global.settings.expert ) {
				marriageLabel += " — MARR";
				divorceLabel += " — DIV";
			}
			eventSubMenu.add(0, 124, 0, marriageLabel);
			eventSubMenu.add(0, 125, 0, divorceLabel);

			// Gli altri eventi che si possono inserire
			SubMenu subAltri = eventSubMenu.addSubMenu(0, 100, 0, R.string.other);
			int i = 0;
			for( Pair<String, String> event : otherEvents ) {
				subAltri.add(0, 200 + i, 0, event.second);
				i++;
			}
		}
		if( object instanceof Source && findViewById(R.id.citazione_fonte) == null ) { // todo dubbio: non dovrebbe essere citazione_ARCHIVIO ?
			SubMenu subArchivio = menu.addSubMenu(0, 100, 0, R.string.repository);
			subArchivio.add(0, 101, 0, R.string.new_repository);
			if( !gc.getRepositories().isEmpty() )
				subArchivio.add(0, 102, 0, R.string.link_repository);
		}
		if( object instanceof NoteContainer ) {
			SubMenu subNota = menu.addSubMenu(0, 100, 0, R.string.note);
			subNota.add(0, 103, 0, R.string.new_note);
			subNota.add(0, 104, 0, R.string.new_shared_note);
			if( !gc.getNotes().isEmpty() )
				subNota.add(0, 105, 0, R.string.link_shared_note);
		}
		if( object instanceof MediaContainer ) {
			SubMenu subMedia = menu.addSubMenu(0, 100, 0, R.string.media);
			subMedia.add(0, 106, 0, R.string.new_media);
			subMedia.add(0, 107, 0, R.string.new_shared_media);
			if( !gc.getMedia().isEmpty() )
				subMedia.add(0, 108, 0, R.string.link_shared_media);
		}
		if( (object instanceof SourceCitationContainer || object instanceof Note) && Global.settings.expert ) {
			SubMenu subFonte = menu.addSubMenu(0, 100, 0, R.string.source);
			subFonte.add(0, 109, 0, R.string.new_source_note);
			subFonte.add(0, 110, 0, R.string.new_source);
			if( !gc.getSources().isEmpty() )
				subFonte.add(0, 111, 0, R.string.link_source);
		}
		return popup;
	}

	// Imposta ciò che è stato scelto nelle liste
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if( resultCode == RESULT_OK ) {
			// Dal submenu 'Collega...' in FAB
			if( requestCode == 34417 ) { // Familiare scelto in Anagrafe
				Person aggiungendo = gc.getPerson(data.getStringExtra("idParente"));
				Famiglia.aggrega(aggiungendo, (Family)object, data.getIntExtra("relazione", 0));
				U.save(true, Memoria.oggettoCapo());
				return;
			} else if( requestCode == 5065 ) { // Fonte scelta in Biblioteca
				SourceCitation citaFonte = new SourceCitation();
				citaFonte.setRef(data.getStringExtra("idFonte"));
				if( object instanceof Note ) ((Note)object).addSourceCitation(citaFonte);
				else ((SourceCitationContainer)object).addSourceCitation(citaFonte);
			} else if( requestCode == 7074 ) { // Nota condivisa
				NoteRef rifNota = new NoteRef();
				rifNota.setRef(data.getStringExtra("idNota"));
				((NoteContainer)object).addNoteRef(rifNota);
			} else if( requestCode == 4173 ) { // File preso dal file manager o altra app diventa media locale
				Media media = new Media();
				media.setFileTag("FILE");
				((MediaContainer)object).addMedia(media);
				if( F.proponiRitaglio(this, null, data, media) ) {
					U.save(false, Memoria.oggettoCapo());
					return;
				}
			} else if( requestCode == 4174 ) { // File preso dal file manager diventa media condiviso
				Media media = Galleria.nuovoMedia(object);
				if( F.proponiRitaglio(this, null, data, media) ) {
					U.save(false, media, Memoria.oggettoCapo());
					return;
				}
			} else if( requestCode == 43616 ) { // Media da Galleria
				MediaRef rifMedia = new MediaRef();
				rifMedia.setRef(data.getStringExtra("idMedia"));
				((MediaContainer)object).addMediaRef(rifMedia);
			} else if( requestCode == 4562 ) { // Archivio scelto in Magazzino da Fonte
				RepositoryRef archRef = new RepositoryRef();
				archRef.setRef(data.getStringExtra("idArchivio"));
				((Source)object).setRepositoryRef(archRef);
			} else if( requestCode == 5173 ) { // Salva in Media un file scelto con le app da Immagine
				if( F.proponiRitaglio(this, null, data, (Media)object) ) {
					U.save(false, Memoria.oggettoCapo());
					return;
				}
			} else if( requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE ) {
				F.fineRitaglioImmagine(data);
			}
			//  da menu contestuale 'Scegli...'
			if( requestCode == 5390 ) { // Imposta l'archivio che è stato scelto in Magazzino da ArchivioRef
				((RepositoryRef)object).setRef(data.getStringExtra("idArchivio"));
			} else if( requestCode == 7047 ) { // Imposta la fonte che è stata scelta in Biblioteca da CitazioneFonte
				((SourceCitation)object).setRef(data.getStringExtra("idFonte"));
			}
			U.save(true, Memoria.oggettoCapo());
				// 'true' indica di ricaricare sia questo Dettaglio grazie al seguente onRestart(), sia Individuo o Famiglia
		} else if( requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE )
			Global.edited = true;
	}

	// Aggiorna i contenuti quando si torna indietro con backPressed()
	@Override
	public void onRestart() {
		super.onRestart();
		if( Global.edited ) { // rinfresca il dettaglio
			refresh();
		}
	}

	public void impagina() {}

	// Ricarica i contenuti del dettaglio, inclusa la data di modifica
	public void refresh() {
		box.removeAllViews();
		eggs.clear();
		impagina();
	}

	// Inserisce la bava di lumaca
	public void placeSlug(String tag) {
		placeSlug(tag, null);
	}

	public void placeSlug(String tag, String id) {
		LinearLayout vistaBava = findViewById(R.id.dettaglio_bava);
		if( Global.settings.expert ) {
			vistaBava.removeAllViews();
			for( final Memoria.Passo passo : Memoria.getPila() ) {
				View vistaGoccia = LayoutInflater.from(this).inflate(R.layout.pezzo_bava, box, false);
				TextView testoGoccia = vistaGoccia.findViewById(R.id.bava_goccia);
				if( Memoria.getPila().indexOf(passo) < Memoria.getPila().size() - 1 ) {
					if( passo.oggetto instanceof Visitable ) // le estensioni GedcomTag non sono Visitable ed è impossibile trovargli la pila
						vistaGoccia.setOnClickListener(v -> {
							new TrovaPila(gc, passo.oggetto);
							startActivity(new Intent(this, Memoria.classi.get(passo.oggetto.getClass())));
						});
				} else {
					passo.tag = tag;
					testoGoccia.setTypeface(null, Typeface.BOLD);
				}
				testoGoccia.setText(passo.tag);
				vistaBava.addView(vistaGoccia);
			}
			// Agiunge l'id ai record capostipiti INDI, FAMI, REPO... ad es. 'SOUR S123'
			if( id != null ) {
				View vistaId = LayoutInflater.from(this).inflate(R.layout.pezzo_bava, vistaBava, false);
				TextView testoId = vistaId.findViewById(R.id.bava_goccia);
				testoId.setText(id);
				testoId.setTypeface(null, Typeface.BOLD);
				vistaBava.addView(vistaId);
			}
		} else
			vistaBava.setVisibility(View.GONE);
	}

	// Return 'object' casted in the required class,
	// or a new instance of the class, but in this case it immediately goes back
	public Object cast(Class aClass) {
		Object casted = null;
		try {
			// male che vada gli passa una nuova istanza della classe, giusto per non inchiodare il dettaglio
			if( aClass.equals(GedcomTag.class) )
				casted = new GedcomTag(null, null, null);
			else
				casted = aClass.newInstance();
			casted = aClass.cast(object);
		} catch( Exception e ) {
			onBackPressed();
		}
		return casted;
	}

	// An item of all the possible that can be displayed on a 'dettaglio' activity
	class Egg {
		String title;
		Object yolk; // Can be a method string ("Value", "Date", "Type"...) or an Address
		boolean common; // indica se farlo comparire nel menu del FAB per inserire il pezzo
		boolean multiLine;
		Egg(String title, Object yolk, boolean common, boolean multiLine) {
			this.title = title;
			this.yolk = yolk;
			this.common = common;
			this.multiLine = multiLine;
			eggs.add(this);
		}
	}

	// Overloading of the following method
	public void place(String title, String method) {
		place(title, method, true, false);
	}

	// Try to put a basic editable text piece
	public void place(String title, String method, boolean common, boolean multiLine) {
		new Egg(title, method, common, multiLine);
		String text;
		try {
			text = (String)object.getClass().getMethod("get" + method).invoke(object);
		} catch( Exception e ) {
			text = "ERROR: " + e.getMessage();
		}
		// Value 'Y' is hidden for not-experts
		if( !Global.settings.expert && object instanceof EventFact && method.equals("Value")
				&& text != null && text.equals("Y") ) {
			String tag = ((EventFact)object).getTag();
			if( tag != null && (tag.equals("BIRT") || tag.equals("CHR") || tag.equals("DEAT")
					|| tag.equals("MARR") || tag.equals("DIV")) )
				return;
		}
		placePiece(title, text, method, multiLine);
	}

	// Diverse firme per intercettare i vari tipi di oggetto
	public void place(String title, Address address) {
		Address addressNotNull = address == null ? new Address() : address;
		new Egg(title, addressNotNull, true, false);
		placePiece(title, writeAddress(address, false), addressNotNull, false);
	}

	// Events of Famiglia
	public void place(String title, EventFact event) {
		EventFact eventNotNull = event == null ? new EventFact() : event;
		placePiece(title, writeEvent(event), eventNotNull, false);
	}

	public View placePiece(String title, String text, Object object, boolean multiLine) {
		if( text == null ) return null;
		View pieceView = LayoutInflater.from(box.getContext()).inflate(R.layout.pezzo_fatto, box, false);
		box.addView(pieceView);
		((TextView)pieceView.findViewById(R.id.fatto_titolo)).setText(title);
		((TextView)pieceView.findViewById(R.id.fatto_testo)).setText(text);
		EditText editText = pieceView.findViewById(R.id.fatto_edita);
		if( multiLine ) {
			editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
			editText.setVerticalScrollBarEnabled(true);
		}
		View.OnClickListener click = null;
		if( object instanceof Integer ) {    // Nome e cognome in modalità inesperto
			click = this::edit;
		} else if( object instanceof String ) { // Method
			click = this::edit;
			// Se si tratta di una data
			if( object.equals("Date") ) {
				editoreData = pieceView.findViewById(R.id.fatto_data);
				editoreData.inizia(editText);
			}
		} else if( object instanceof Address ) { // Indirizzo
			click = v -> {
				Memoria.aggiungi(object);
				startActivity(new Intent(this, Indirizzo.class));
			};
		} else if( object instanceof EventFact ) { // Evento
			click = v -> {
				Memoria.aggiungi(object);
				startActivity(new Intent(this, Evento.class));
			};
			// Gli EventFact della famiglia possono avere delle note e dei media
			LinearLayout scatolaNote = pieceView.findViewById(R.id.fatto_note);
			U.placeNotes(scatolaNote, object, false);
			U.placeMedia(scatolaNote, object, false);
		} else if( object instanceof GedcomTag ) { // Estensione
			click = v -> {
				Memoria.aggiungi(object);
				startActivity(new Intent(this, Estensione.class));
			};
		}
		pieceView.setOnClickListener(click);
		registerForContextMenu(pieceView);
		pieceView.setTag(R.id.tag_oggetto, object); // Serve a vari processi per riconoscere il pezzo
		return pieceView;
	}

	public void placeExtensions(ExtensionContainer contenitore) {
		for( app.familygem.Estensione est : U.trovaEstensioni(contenitore) ) {
			placePiece(est.nome, est.testo, est.gedcomTag, false);
		}
	}

	public static String writeAddress(Address adr, boolean oneLine) {
		if( adr == null ) return null;
		String txt = "";
		String br = oneLine ? ", " : "\n";
		if( adr.getValue() != null )
			txt = adr.getValue() + br;
		if( adr.getAddressLine1() != null )
			txt += adr.getAddressLine1() + br;
		if( adr.getAddressLine2() != null )
			txt += adr.getAddressLine2() + br;
		if( adr.getAddressLine3() != null )
			txt += adr.getAddressLine3() + br;
		if( adr.getPostalCode() != null ) txt += adr.getPostalCode() + " ";
		if( adr.getCity() != null ) txt += adr.getCity() + " ";
		if( adr.getState() != null ) txt += adr.getState();
		if( adr.getPostalCode() != null || adr.getCity() != null || adr.getState() != null )
			txt += br;
		if( adr.getCountry() != null )
			txt += adr.getCountry();
		if( txt.endsWith(br))
			txt = txt.substring(0, txt.length() - br.length()).trim();
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

	// Compose the title of family events
	public String writeEventTitle(Family family, EventFact event) {
		String tit;
		switch( event.getTag() ) {
			case "MARR":
				tit = U.areMarried(family) ? getString(R.string.marriage) : getString(R.string.relationship);
				break;
			case "DIV":
				tit = U.areMarried(family) ? getString(R.string.divorce) : getString(R.string.separation);
				break;
			case "EVEN":
				tit = getString(R.string.event);
				break;
			case "RESI":
				tit = getString(R.string.residence);
				break;
			default:
				tit = event.getDisplayType();
		}
		if( event.getType() != null && !event.getType().isEmpty() && !event.getType().equals("marriage") )
			tit += " (" + TypeView.getTranslatedType(event.getType(), TypeView.Combo.RELATIONSHIP) + ")";
		return tit;
	}

	// Compone il testo dell'evento in Famiglia
	public static String writeEvent(EventFact ef) {
		if( ef == null ) return null;
		String txt = "";
		if( ef.getValue() != null ) {
			if( ef.getValue().equals("Y") && ef.getTag() != null
					&& (ef.getTag().equals("MARR") || ef.getTag().equals("DIV")) )
				txt = Global.context.getString(R.string.yes);
			else
				txt = ef.getValue();
			txt += "\n";
		}
		if( ef.getDate() != null )
			txt += new Datatore(ef.getDate()).writeDateLong() + "\n";
		if( ef.getPlace() != null )
			txt += ef.getPlace() + "\n";
		Address indirizzo = ef.getAddress();
		if( indirizzo != null )
			txt += writeAddress(indirizzo, true) + "\n";
		if( txt.endsWith("\n") )
			txt = txt.substring(0, txt.length() - 1);
		return txt;
	}

	EditText editText;
	void edit(View pieceView) {
		FloatingActionButton fab = findViewById(R.id.fab);
		ActionBar barra = getSupportActionBar();

		// Termina l'eventuale editazione di un altro pezzo
		for( int i = 0; i < box.getChildCount(); i++ ) {
			View otherPiece = box.getChildAt(i);
			EditText editText = otherPiece.findViewById(R.id.fatto_edita);
			if( editText != null && editText.isShown() ) {
				TextView textView = otherPiece.findViewById(R.id.fatto_testo);
				if( !editText.getText().toString().equals(textView.getText().toString()) ) // se c'è stata editazione
					save(otherPiece, barra, fab);
				else
					ripristina(otherPiece, barra, fab);
			}
		}
		// Poi rende editabile questo pezzo
		TextView textView = pieceView.findViewById(R.id.fatto_testo);
		textView.setVisibility(View.GONE);
		fab.hide();
		Object pieceObject = pieceView.getTag(R.id.tag_oggetto);
		boolean showInput = false;
		editText = pieceView.findViewById(R.id.fatto_edita);
		// Luogo
		if( pieceObject.equals("Place") ) {
			showInput = true;
			// Se non l'ha già fatto, sostituisce vistaEdita con TrovaLuogo
			if( !(editText instanceof TrovaLuogo) ) {
				ViewGroup parent = (ViewGroup)pieceView;  // todo: si potrebbe usare direttamente vistaPezzo se fosse un ViewGroup o LinearLayout anzicé View
				int index = parent.indexOfChild(editText);
				parent.removeView(editText);
				editText = new TrovaLuogo(editText.getContext(), null);
				editText.setId(R.id.fatto_edita);
				parent.addView(editText, index);
			} else
				editText.setVisibility(View.VISIBLE);
		} // Name type
		else if( object instanceof Name && pieceObject.equals("Type") ) {
			if( !(editText instanceof TypeView) ) {
				ViewGroup parent = (ViewGroup)pieceView;
				parent.removeView(editText);
				editText = new TypeView(editText.getContext(), TypeView.Combo.NAME);
				parent.addView(editText, parent.indexOfChild(editText));
			} else
				editText.setVisibility(View.VISIBLE);
		} // Marriage/relationship type
		else if( object instanceof EventFact && pieceObject.equals("Type") && ((EventFact)object).getTag().equals("MARR") ) {
			if( !(editText instanceof TypeView) ) {
				ViewGroup parent = (ViewGroup)pieceView;
				parent.removeView(editText);
				editText = new TypeView(editText.getContext(), TypeView.Combo.RELATIONSHIP);
				parent.addView(editText, parent.indexOfChild(editText));
			} else
				editText.setVisibility(View.VISIBLE);
		} // Data
		else if( pieceObject.equals("Date") ) {
			editText.setVisibility(View.VISIBLE);
		} // Tutti gli altri casi normali di editazione
		else {
			showInput = true;
			editText.setVisibility(View.VISIBLE);
		}
		if( showInput ) {
			InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
		}
		CharSequence text = textView.getText();
		editText.setText(text);
		editText.requestFocus();
		editText.setSelection(text.length()); // Cursor at the end

		// Intercetta il 'Done' e 'Next' sulla tastiera
		editText.setOnEditorActionListener((vista, actionId, keyEvent) -> {
			if( actionId == EditorInfo.IME_ACTION_DONE )
				save(pieceView, barra, fab);
			else if( actionId == EditorInfo.IME_ACTION_NEXT ) {
				if( !editText.getText().toString().equals(textView.getText().toString()) )
					save(pieceView, barra, fab);
				else
					ripristina(pieceView, barra, fab);
				View nextPiece = box.getChildAt(box.indexOfChild(pieceView) + 1);
				if( nextPiece != null && nextPiece.getTag(R.id.tag_oggetto) instanceof String )
					edit(nextPiece);
			}
			return false;
		});

		// ActionBar personalizzata
		barra.setDisplayHomeAsUpEnabled( false );	// nasconde freccia <-
		qualeMenu = 0;
		invalidateOptionsMenu();
		View barraAzione = getLayoutInflater().inflate( R.layout.barra_edita, new LinearLayout(box.getContext()), false);
		barraAzione.findViewById(R.id.edita_annulla).setOnClickListener( v -> {
			editText.setText( textView.getText() );
			ripristina( pieceView, barra, fab );
		});
		barraAzione.findViewById(R.id.edita_salva).setOnClickListener( v -> save( pieceView, barra, fab ) );
		barra.setCustomView( barraAzione );
		barra.setDisplayShowCustomEnabled( true );
	}

	void save(View pieceView, ActionBar actionBar, FloatingActionButton fab) {
		if( editoreData != null ) editoreData.chiudi(); // In sostanza solo per aggiungere le parentesi alla data frase
		String text = editText.getText().toString().trim();
		Object pieceObject = pieceView.getTag(R.id.tag_oggetto);
		if( pieceObject instanceof Integer ) { // Salva nome e cognome per inesperti
			String nome = ((EditText)box.getChildAt(0).findViewById(R.id.fatto_edita)).getText().toString();
			String cognome = ((EditText)box.getChildAt(1).findViewById(R.id.fatto_edita)).getText().toString();
			((Name)object).setValue( nome + " /" + cognome + "/" );
		} else try { // Tutti gli altri normali metodi
			object.getClass().getMethod("set" + pieceObject, String.class).invoke(object, text);
		} catch( Exception e ) {
			Toast.makeText(box.getContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
			return; // in caso di errore rimane in modalità editore
		}
		((TextView)pieceView.findViewById(R.id.fatto_testo)).setText(text);
		ripristina(pieceView, actionBar, fab);
		U.save(true, Memoria.oggettoCapo());
		/*if( Memoria.getPila().size() == 1 ) {
			ricrea(); // Todo Bisognerebbe aggiornare la data Cambiamento del record, però magari senza ricaricare tutto.
		}*/
		// In immagine modificato il percorso aggiorna l'immagine
		if( this instanceof Immagine && pieceObject.equals("File") )
			((Immagine)this).aggiornaImmagine();
		// Se ha modificato un autore chiede di referenziarlo in header
		else if( object instanceof Submitter )
			U.autorePrincipale(this, ((Submitter)object).getId());
		else if( this instanceof Evento )
			refresh(); // To update the title bar
	}

	// Operazioni comuni a Salva e Annulla
	void ripristina(View vistaPezzo, ActionBar barra, FloatingActionButton fab) {
		editText.setVisibility(View.GONE);
		vistaPezzo.findViewById(R.id.fatto_data).setVisibility(View.GONE);
		vistaPezzo.findViewById(R.id.fatto_testo).setVisibility(View.VISIBLE);
		barra.setDisplayShowCustomEnabled(false); // nasconde barra personalizzata
		barra.setDisplayHomeAsUpEnabled(true);
		qualeMenu = 1;
		invalidateOptionsMenu();
		InputMethodManager imm = (InputMethodManager)getSystemService(getApplicationContext().INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(vistaPezzo.getWindowToken(), 0);
		if( !(object instanceof Note && !Global.settings.expert) ) // Le note in modalità inesperto non hanno fab
			fab.show();
	}

	// Menu opzioni
	int qualeMenu = 1;	// serve per nasconderlo quando si entra in modalità editore
	@Override
	public boolean onCreateOptionsMenu( Menu menu ) {
		if( qualeMenu == 1 ) {	// Menu standard della barra
			if( object instanceof Submitter && ( gc.getHeader()==null || // Autore non principale
					gc.getHeader().getSubmitter(gc)==null || !gc.getHeader().getSubmitter(gc).equals(object) ))
				menu.add( 0, 1, 0, R.string.make_default );
			if( object instanceof Media ) {
				if( box.findViewById(R.id.immagine_foto).getTag(R.id.tag_tipo_file).equals(1) )
					menu.add( 0, 2, 0, R.string.crop );
				menu.add( 0, 3, 0, R.string.choose_file );
			}
			if( object instanceof Family )
				menu.add( 0, 4, 0, R.string.delete );
			else if( !(object instanceof Submitter && U.autoreHaCondiviso((Submitter)object)) ) // autore che ha condiviso non può essere eliminato
				menu.add( 0, 5, 0, R.string.delete );
		}
		return true;
	}
	@Override // è evocato quando viene scelta una voce del menu E cliccando freccia indietro
	public boolean onOptionsItemSelected( MenuItem item ) {
		int id = item.getItemId();
		if( id == 1 ) { // Autore principale
			Podio.autorePrincipale( (Submitter)object);
		} else if( id == 2 ) { // Immagine: ritaglia
			croppaImmagine( box );
		} else if( id == 3 ) { // Immagine: scegli
			F.appAcquisizioneImmagine( this, null, 5173, null );
		} else if( id == 4 ) {	// Famiglia
			Family fam = (Family)object;
			if( fam.getHusbandRefs().size() + fam.getWifeRefs().size() + fam.getChildRefs().size() > 0 ) {
				new AlertDialog.Builder(this).setMessage( R.string.really_delete_family )
						.setPositiveButton(android.R.string.yes, (dialog, i) -> {
							Chiesa.deleteFamily(fam);
							onBackPressed();
						}).setNeutralButton(android.R.string.cancel, null).show();
			} else {
				Chiesa.deleteFamily(fam);
				onBackPressed();
			}
		} else if( id == 5 ) { // Tutti gli altri
			// todo: conferma eliminazione di tutti gli oggetti..
			elimina();
			U.save(true); // l'aggiornamento delle date avviene negli Override di elimina()
			onBackPressed();
		} else if( id == android.R.id.home ){
			onBackPressed();
		}
		return true;
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		if( object instanceof EventFact )
			Evento.ripulisciTag((EventFact)object);
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
				Family fam = (Family)object;
				// Genera le etichette per le voci 'famiglia' (come figlio e come coniuge)
				String[] etichetteFam = { null, null };
				if( pers.getParentFamilies(gc).size() > 1 && pers.getSpouseFamilies(gc).size() > 1 ) {
					etichetteFam[0] = getString(R.string.family_as_child);
					etichetteFam[1] = getString(R.string.family_as_spouse);
				}
				menu.add(0, 10, 0, R.string.diagram );
				menu.add(0, 11, 0, R.string.card );
				if( etichetteFam[0] != null )
					menu.add(0, 12, 0, etichetteFam[0]);
				if( etichetteFam[1] != null )
					menu.add(0, 13, 0, etichetteFam[1]);
				if( fam.getChildren(gc).indexOf(pers) > 0 )
					menu.add(0, 14, 0, R.string.move_before);
				if( fam.getChildren(gc).indexOf(pers) < fam.getChildren(gc).size() - 1 && fam.getChildren(gc).contains(pers) )
					// così esclude i genitori il cui indice è -1
					menu.add(0, 15, 0, R.string.move_after);
				menu.add(0, 16, 0, R.string.modify);
				menu.add(0, 17, 0, R.string.unlink);
				menu.add(0, 18, 0, R.string.delete);
			} else if( oggettoPezzo instanceof Note ) {
				menu.add(0, 20, 0, R.string.copy);
				if( ((Note)oggettoPezzo).getId() != null )
					menu.add(0, 21, 0, R.string.unlink);
				menu.add(0, 22, 0, R.string.delete);
			} else if( oggettoPezzo instanceof SourceCitation ) {
				menu.add(0, 30, 0, R.string.copy);
				menu.add(0, 31, 0, R.string.delete);
			} else if( oggettoPezzo instanceof Media ) {
				if( ((Media)oggettoPezzo).getId() != null )
					menu.add(0, 40, 0, R.string.unlink);
				menu.add(0, 41, 0, R.string.delete);
			} else if( oggettoPezzo instanceof Address ) {
				menu.add(0, 50, 0, R.string.copy);
				menu.add(0, 51, 0, R.string.delete);
			} else if( oggettoPezzo instanceof EventFact ) {
				menu.add(0, 55, 0, R.string.copy);
				Family fam = (Family)object;
				if( fam.getEventsFacts().indexOf(oggettoPezzo) > 0 )
					menu.add(0, 56, 0, R.string.move_up);
				if( fam.getEventsFacts().contains(oggettoPezzo)
						&& fam.getEventsFacts().indexOf(oggettoPezzo) < fam.getEventsFacts().size() - 1 )
					menu.add(0, 57, 0, R.string.move_down);
				menu.add(0, 58, 0, R.string.delete);
			} else if( oggettoPezzo instanceof GedcomTag ) {
				menu.add(0, 60, 0, R.string.copy);
				menu.add(0, 61, 0, R.string.delete);
			} else if( oggettoPezzo instanceof Source ) {
				menu.add(0, 70, 0, R.string.copy);
				menu.add(0, 71, 0, R.string.choose_source);
			} else if( oggettoPezzo instanceof RepositoryRef ) {
				menu.add(0, 80, 0, R.string.copy);
				menu.add(0, 81, 0, R.string.delete);
			} else if( oggettoPezzo instanceof Repository ) {
				menu.add(0, 90, 0, R.string.copy);
				menu.add(0, 91, 0, R.string.choose_repository);
			} else if( oggettoPezzo instanceof Integer ) {
				if( oggettoPezzo.equals(43614) ) { // Immaginona
					// è un'immagine ritagliabile
					if( vistaPezzo.findViewById(R.id.immagine_foto).getTag(R.id.tag_tipo_file).equals(1) )
						menu.add(0, 100, 0, R.string.crop);
					menu.add(0, 101, 0, R.string.choose_file);
				} else if( oggettoPezzo.equals(4043) || oggettoPezzo.equals(6064) ) // Nome e cognome per inesperti
					menu.add(0, 0, 0, R.string.copy);
			} else if( oggettoPezzo instanceof String ) {
				menu.add(0, 0, 0, R.string.copy);
				menu.add(0, 1, 0, R.string.delete);
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
				U.copiaNegliAppunti(((TextView)vistaPezzo.findViewById(R.id.fatto_titolo)).getText(),
						((TextView)vistaPezzo.findViewById(R.id.fatto_testo)).getText());
				return true;
			case 1: // Elimina pezzo editabile
				try {
					object.getClass().getMethod("set" + oggettoPezzo, String.class).invoke(object, (Object)null);
				} catch( Exception e ) {
					Toast.makeText(box.getContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
					break;
				}
				break;
			case 10: // Diagramma
				U.qualiGenitoriMostrare(this, pers, 1);
				return true;
			case 11: // Scheda persona
				Memoria.setPrimo(pers);
				startActivity(new Intent(this, Individuo.class));
				return true;
			case 12: // Famiglia come figlio
				U.qualiGenitoriMostrare(this, pers, 2);
				return true;
			case 13: // Famiglia come coniuge
				U.qualiConiugiMostrare(this, pers, null);
				return true;
			case 14: // Figlio sposta su
				Family fa = (Family)object;
				ChildRef refBimbo = fa.getChildRefs().get(fa.getChildren(gc).indexOf(pers));
				fa.getChildRefs().add(fa.getChildRefs().indexOf(refBimbo) - 1, refBimbo);
				fa.getChildRefs().remove(fa.getChildRefs().lastIndexOf(refBimbo));
				break;
			case 15: // Figlio sposta giù
				Family f = (Family)object;
				ChildRef childRef = f.getChildRefs().get(f.getChildren(gc).indexOf(pers));
				f.getChildRefs().add(f.getChildRefs().indexOf(childRef) + 2, childRef);
				f.getChildRefs().remove(f.getChildRefs().indexOf(childRef));
				break;
			case 16: // Modifica
				Intent i = new Intent(this, EditaIndividuo.class);
				i.putExtra("idIndividuo", pers.getId());
				startActivity(i);
				return true;
			case 17: // Scollega
				Famiglia.scollega((SpouseFamilyRef)vistaPezzo.getTag(R.id.tag_spouse_family_ref),
						(SpouseRef)vistaPezzo.getTag(R.id.tag_spouse_ref));
				U.updateChangeDate(pers);
				trovaUnAltroRappresentanteDellaFamiglia(pers);
				break;
			case 18: // Elimina membro
				new AlertDialog.Builder(this).setMessage(R.string.really_delete_person)
						.setPositiveButton(R.string.delete, (dialog, id) -> {
							Anagrafe.eliminaPersona(this, pers.getId());
							box.removeView(vistaPezzo);
							trovaUnAltroRappresentanteDellaFamiglia(pers);
						}).setNeutralButton(R.string.cancel, null).show();
				return true;
			case 20: // Nota
				U.copiaNegliAppunti(getText(R.string.note), ((TextView)vistaPezzo.findViewById(R.id.nota_testo)).getText());
				return true;
			case 21:
				U.scollegaNota((Note)oggettoPezzo, object, null);
				break;
			case 22:
				Object[] capi = U.eliminaNota((Note)oggettoPezzo, vistaPezzo);
				U.save(true, capi);
				return true;
			case 30: // Citazione fonte
				U.copiaNegliAppunti(getText(R.string.source_citation),
						((TextView)vistaPezzo.findViewById(R.id.fonte_testo)).getText() + "\n"
								+ ((TextView)vistaPezzo.findViewById(R.id.citazione_testo)).getText());
				return true;
			case 31:
				if( object instanceof Note ) // Note non estende SourceCitationContainer
					((Note)object).getSourceCitations().remove(oggettoPezzo);
				else
					((SourceCitationContainer)object).getSourceCitations().remove(oggettoPezzo);
				Memoria.annullaIstanze(oggettoPezzo);
				break;
			case 40: // Media
				Galleria.scollegaMedia(((Media)oggettoPezzo).getId(), (MediaContainer)object);
				break;
			case 41:
				Object[] capiMedia = Galleria.eliminaMedia((Media)oggettoPezzo, null);
				U.save(true, capiMedia); // un media condiviso può dover aggiornare le date di più capi
				refresh();
				return true;
			case 51:
				eliminaIndirizzo(object);
				break;
			case 56: // Evento di Famiglia
				int index1 = ((Family)object).getEventsFacts().indexOf(oggettoPezzo);
				Collections.swap(((Family)object).getEventsFacts(), index1, index1 - 1);
				break;
			case 57:
				int index2 = ((Family)object).getEventsFacts().indexOf(oggettoPezzo);
				Collections.swap(((Family)object).getEventsFacts(), index2, index2 + 1);
				break;
			case 58:
				((Family)object).getEventsFacts().remove(oggettoPezzo);
				Memoria.annullaIstanze(oggettoPezzo);
				break;
			case 61: // Estensione
				U.eliminaEstensione((GedcomTag)oggettoPezzo, object, null);
				break;
			// Fonte
			case 70: // Copia
				U.copiaNegliAppunti(getText(R.string.source), ((TextView)vistaPezzo.findViewById(R.id.fonte_testo)).getText());
				return true;
			case 71: // Scegli in Biblioteca
				Intent inte = new Intent(this, Principal.class);
				inte.putExtra("bibliotecaScegliFonte", true);
				startActivityForResult(inte, 7047);
				return true;
			// Citazione archivio
			case 80: // Copia
				U.copiaNegliAppunti(getText(R.string.repository_citation),
						((TextView)vistaPezzo.findViewById(R.id.fonte_testo)).getText() + "\n"
								+ ((TextView)vistaPezzo.findViewById(R.id.citazione_testo)).getText());
				return true;
			case 81: // Elimina
				((Source)object).setRepositoryRef(null);
				Memoria.annullaIstanze(oggettoPezzo);
				break;
			// Archivio
			case 90: // Copia
				U.copiaNegliAppunti(getText(R.string.repository), ((TextView)vistaPezzo.findViewById(R.id.fonte_testo)).getText());
				return true;
			case 91: // Scegli in Magazzino
				Intent intn = new Intent(this, Principal.class);
				intn.putExtra("magazzinoScegliArchivio", true);
				startActivityForResult(intn, 5390);
				return true;
			case 100: // Immaginona ritaglia
				croppaImmagine(vistaPezzo);
				return true;
			case 101: // scegli immagine
				F.appAcquisizioneImmagine(this, null, 5173, null);
				return true;
			default:
				return false;
		}
		// Prima ricrea la pagina e poi salva, che per alberi grossi può metterci alcuni secondi
		//closeContextMenu(); // Inutile. La chiusura del menu aspetta la fine del salvataggio,
		// a meno di mettere salvaJson() dentro un postDelayed() di almeno 500 ms
		U.updateChangeDate(Memoria.oggettoCapo());
		refresh();
		U.save(true, (Object[])null);
		return true;
	}

	// Corregge unRappresentanteDellaFamiglia per far comparire correttamente "Collega persona esistente" nel menu
	private void trovaUnAltroRappresentanteDellaFamiglia(Person p) {
		if( unRappresentanteDellaFamiglia.equals(p) ) {
			Family fam = (Family)object;
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
	private void croppaImmagine(View vista) {
		ImageView vistaImg = vista.findViewById(R.id.immagine_foto);
		File fileMedia = null;
		String percorso = (String)vistaImg.getTag(R.id.tag_percorso);
		if( percorso != null )
			fileMedia = new File(percorso);
		Uri uriMedia = (Uri)vistaImg.getTag(R.id.tag_uri);
		Global.mediaCroppato = (Media)object;
		F.tagliaImmagine(this, fileMedia, uriMedia, null);
	}

	// Chiude tastiera eventualmente visibile
	@Override
	protected void onPause() {
		super.onPause();
		if( editText != null ) {
			InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
		}
	}

	@Override
	public void onRequestPermissionsResult(int codice, String[] permessi, int[] accordi) {
		super.onRequestPermissionsResult(codice, permessi, accordi);
		F.risultatoPermessi(this, null, codice, permessi, accordi,
				object instanceof MediaContainer ? (MediaContainer)object : null);
				// Immagine ha 'object' instance di Media, non di MediaContainer
	}
}
