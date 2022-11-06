package app.familygem;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.annotation.NonNull;
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
import app.familygem.detail.SourceCitationActivity;
import app.familygem.detail.ExtensionActivity;
import app.familygem.detail.EventActivity;
import app.familygem.detail.FamilyActivity;
import app.familygem.detail.ImageActivity;
import app.familygem.detail.AddressActivity;
import app.familygem.detail.NoteActivity;
import app.familygem.visitor.FindStack;
import static app.familygem.Global.gc;

public class DetailActivity extends AppCompatActivity {

	public LinearLayout box;
	public Object object; // Name Media SourceCitation ecc.
	List<Egg> eggs = new ArrayList<>(); // List of all the possible editable pieces
	List<Pair<String, String>> otherEvents; // Events for the Family FAB
	public Person aRepresentativeOfTheFamily; // a family Person to hide in the FAB 'Colleague person' TODO what? Original: una Persona della Famiglia per nascondere nel FAB 'Collega persona'
	PublisherDateLinearLayout publisherDateLinearLayout;
	FloatingActionButton fab;
	ActionBar actionBar;

	@Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.activity_detail);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		box = findViewById(R.id.detail_box);
		fab = findViewById(R.id.fab);
		actionBar = getSupportActionBar();
		U.ensureGlobalGedcomNotNull(gc);

		object = Memory.getObject();
		if( object == null ) {
			onBackPressed(); // skip all other details without object
		} else
			format();

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
				// FAB + puts a new egg and makes it immediately editable
				int id = item.getItemId();
				boolean toBeSaved = false;
				if( id < 100 ) {
					Object thing = eggs.get(id).yolk;
						if( thing instanceof Address ) { // thing is a new Address()
						if( object instanceof EventFact )
							((EventFact)object).setAddress( (Address)thing );
						else if( object instanceof Submitter )
							((Submitter)object).setAddress( (Address)thing );
						else if( object instanceof Repository )
							((Repository)object).setAddress( (Address)thing );
					}
					// Tags needed to then export to Gedcom
					if( object instanceof Name && thing.equals("Type") ) {
						((Name)object).setTypeTag("TYPE");
					} else if( object instanceof Repository ) {
						if( thing.equals("Www") )
							((Repository)object).setWwwTag("WWW");
						if( thing.equals("Email") )
							((Repository)object).setEmailTag("EMAIL");
					} else if( object instanceof Submitter ) {
						if( thing.equals("Www") )
							((Submitter)object).setWwwTag("WWW");
						if( thing.equals("Email") )
							((Submitter)object).setEmailTag("EMAIL");
					}
					View piece = placePiece(eggs.get(id).title, "", thing, eggs.get(id).multiLine);
					if( thing instanceof String )
						edit(piece);
					// TODO open new Address ("to edit it"?)["for editing"?] //aprire Address nuovo per editarlo
				} else if( id == 101 ) {//TODO code smell: use of magic numbers
					RepositoryFragment.newRepository(this, (Source)object);
				} else if( id == 102 ) {
					Intent intent = new Intent(this, Principal.class);
					intent.putExtra("magazzinoScegliArchivio", true); //TODO translate
					startActivityForResult(intent, 4562);
				} else if( id == 103 ) { // New note
					Note note = new Note();
					note.setValue("");
					((NoteContainer)object).addNote(note);
					Memory.add(note);
					startActivity(new Intent(this, NoteActivity.class));
					toBeSaved = true;
				} else if( id == 104 ) { // New shared note
					NotebookFragment.newNote(this, object);
				} else if( id == 105 ) { // Link shared note
					Intent intent = new Intent(this, Principal.class);
					intent.putExtra("quadernoScegliNota", true); //TODO translate
					startActivityForResult(intent, 7074);
				} else if( id == 106 ) { // Search for local media
					F.displayImageCaptureDialog(this, null, 4173, (MediaContainer)object);
				} else if( id == 107 ) { // Search for shared media
					F.displayImageCaptureDialog(this, null, 4174, (MediaContainer)object);
				} else if( id == 108 ) { // Link shared media
					Intent intent = new Intent(this, Principal.class);
					intent.putExtra("galleriaScegliMedia", true); //TODO translate
					startActivityForResult(intent, 43616);
				} else if( id == 109 ) { // New source citation //Nuova fonte-nota
					SourceCitation citation = new SourceCitation();
					citation.setValue("");
					if( object instanceof Note ) ((Note)object).addSourceCitation(citation);
					else ((SourceCitationContainer)object).addSourceCitation(citation);
					Memory.add(citation);
					startActivity(new Intent(this, SourceCitationActivity.class));
					toBeSaved = true;
				} else if( id == 110 ) {  // New source
					LibraryFragment.newSource(this, object);
				} else if( id == 111 ) { // Link source
					Intent intent = new Intent(this, Principal.class);
					intent.putExtra("bibliotecaScegliFonte", true); //TODO translate
					startActivityForResult(intent, 5065);
				} else if( id == 120 || id == 121 ) { // Create new family member
					Intent intent = new Intent(this, IndividualEditorActivity.class);
					intent.putExtra("idIndividuo", "TIZIO_NUOVO"); //TODO translate
					intent.putExtra("idFamiglia", ((Family)object).getId()); //TODO translate
					intent.putExtra("relazione", id - 115); //TODO translate
					startActivity(intent);
				} else if( id == 122 || id == 123 ) { // Link existing person
					Intent intent = new Intent(this, Principal.class);
					intent.putExtra("anagrafeScegliParente", true); //TODO translate
					intent.putExtra("relazione", id - 117);
					startActivityForResult(intent, 34417);
				} else if( id == 124 ) { // Put marriage - Metti matrimonio
					EventFact marriage = new EventFact();
					marriage.setTag("MARR");
					marriage.setDate("");
					marriage.setPlace("");
					marriage.setType("");
					((Family)object).addEventFact(marriage);
					Memory.add(marriage);
					startActivity(new Intent(this, EventActivity.class));
					toBeSaved = true;
				} else if( id == 125 ) { // Put divorce - Metti divorzio
					EventFact divorce = new EventFact();
					divorce.setTag("DIV");
					divorce.setDate("");
					((Family)object).addEventFact(divorce);
					Memory.add(divorce);
					startActivity(new Intent(this, EventActivity.class));
					toBeSaved = true;
				} else if( id >= 200 ) { // Put another event - Metti altro evento
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
		// Menu test: if it is empty it hides the fab
		if( !menuFAB(null).getMenu().hasVisibleItems() ) // todo ok?
			fab.hide();
	}

	/**
	 * FAB menu: only with methods that are not already present in the box
	 * */
	PopupMenu menuFAB(View vista) {
		PopupMenu popup = new PopupMenu(this, vista);
		Menu menu = popup.getMenu();
		String[] conIndirizzo = {"Www", "Email", "Phone", "Fax"}; // questi oggetti compaiono nel FAB di Evento se esiste un Indirizzo
		int u = 0;
		for( Egg egg : eggs ) {
			boolean giaMesso = false;
			boolean indirizzoPresente = false;
			for( int i = 0; i < box.getChildCount(); i++ ) {
				Object ogg = box.getChildAt(i).getTag(R.id.tag_object);
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
			if( U.ciSonoIndividuiCollegabili(aRepresentativeOfTheFamily) ) {
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

	/**
	 * Set what has been chosen in the lists
	 * */
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if( resultCode == RESULT_OK ) {
			// From the 'Connect/Link ...' submenu in FAB
			if( requestCode == 34417 ) { // Family member chosen in the Registry
				Person personToBeAdded = gc.getPerson(data.getStringExtra("idParente")); //TODO translate
				FamilyActivity.connect(personToBeAdded, (Family)object, data.getIntExtra("relazione", 0));
				U.save(true, Memory.firstObject());
				return;
			} else if( requestCode == 5065 ) { // Source chosen in the Library
				SourceCitation sourceCitation = new SourceCitation();
				sourceCitation.setRef(data.getStringExtra("idFonte")); //TODO translate
				if( object instanceof Note ) ((Note)object).addSourceCitation(sourceCitation);
				else ((SourceCitationContainer)object).addSourceCitation(sourceCitation);
			} else if( requestCode == 7074 ) { // Shared note
				NoteRef noteRef = new NoteRef();
				noteRef.setRef(data.getStringExtra("idNota")); //TODO translate
				((NoteContainer)object).addNoteRef(noteRef);
			} else if( requestCode == 4173 ) { // File taken from file manager or other app becomes local media //File preso dal file manager o altra app diventa media locale
				Media media = new Media();
				media.setFileTag("FILE");
				((MediaContainer)object).addMedia(media);
				if( F.proposeCropping(this, null, data, media) ) {
					U.save(false, Memory.firstObject());
					return;
				}
			} else if( requestCode == 4174 ) { // File taken from the file manager becomes shared media
				Media media = GalleryFragment.newMedia(object);
				if( F.proposeCropping(this, null, data, media) ) {
					U.save(false, media, Memory.firstObject());
					return;
				}
			} else if( requestCode == 43616 ) { // Media from the Gallery
				MediaRef mediaRef = new MediaRef();
				mediaRef.setRef(data.getStringExtra("idMedia"));
				((MediaContainer)object).addMediaRef(mediaRef);
			} else if( requestCode == 4562 ) { // Repository selected in database (? lit. "Warehouse") from source //Archivio scelto in Magazzino da Fonte
				RepositoryRef archRef = new RepositoryRef();
				archRef.setRef(data.getStringExtra("idArchivio"));
				((Source)object).setRepositoryRef(archRef);
			} else if( requestCode == 5173 ) { // Save in Media a file chosen with the apps from Image // Salva in Media un file scelto con le app da Immagine
				if( F.proposeCropping(this, null, data, (Media)object) ) {
					U.save(false, Memory.firstObject());
					return;
				}
			} else if( requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE ) {
				F.endImageCropping(data);
			}
			//  from the context menu 'Choose ...'
			if( requestCode == 5390 ) { // Sets the archive that has been chosen in database (? lit. "Warehouse") from Repository Ref //Imposta l'archivio che è stato scelto in Magazzino da ArchivioRef
				((RepositoryRef)object).setRef(data.getStringExtra("idArchivio"));
			} else if( requestCode == 7047 ) { // Set the source that has been chosen in the Library by SourceCitation
				((SourceCitation)object).setRef(data.getStringExtra("idFonte"));
			}
			U.save(true, Memory.firstObject());
				// 'true' indicates to reload both this Detail thanks to the following onRestart (), and Individual or Family //'true' indica di ricaricare sia questo Dettaglio grazie al seguente onRestart(), sia Individuo o Famiglia
		} else if( requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE )
			Global.edited = true;
	}

	/**
	 * Update contents when back with backPressed()
	 * Aggiorna i contenuti quando si torna indietro con backPressed()
	 * */
	@Override
	public void onRestart() {
		super.onRestart();
		if( Global.edited ) { // refresh the detail
			refresh();
		}
	}

	public void format() {} //original: impagina()

	/**
	 * Reload the contents of the detail, including the modification date
	 * */
	public void refresh() {
		box.removeAllViews();
		eggs.clear();
		format();
	}

	// Place the tags slug
	public void placeSlug(String tag) {
		placeSlug(tag, null);
	}

	public void placeSlug(String tag, String id) {
		FlexboxLayout slugLayout = findViewById(R.id.dettaglio_bava);
		if( Global.settings.expert ) {
			slugLayout.removeAllViews();
			for( final Memory.Step step : Memory.getStepStack() ) {
				View stepView = LayoutInflater.from(this).inflate(R.layout.pezzo_bava, box, false);
				TextView stepText = stepView.findViewById(R.id.bava_goccia);
				if( Memory.getStepStack().indexOf(step) < Memory.getStepStack().size() - 1 ) {
					if( step.object instanceof Visitable ) // GedcomTag extensions are not Visitable and it is impossible to find the stack of them //le estensioni GedcomTag non sono Visitable ed è impossibile trovargli la pila
						stepView.setOnClickListener(v -> {
							new FindStack(gc, step.object);
							startActivity(new Intent(this, Memory.classes.get(step.object.getClass())));
						});
				} else {
					step.tag = tag;
					stepText.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
				}
				String label = step.tag;
				if( id != null ) {
					label += " " + id; // Id for main records INDI, FAMI, REPO... e.g. 'SOUR S123'
					stepView.setOnClickListener(v -> {
						concludeOtherPiece();
						U.editId(this, (ExtensionContainer)object, this::refresh);
					});
				}
				stepText.setText(label);
				slugLayout.addView(stepView);
			}
		} else
			slugLayout.setVisibility(View.GONE);
	}

	// Conclude the possible editing of another piece
	private void concludeOtherPiece() {
		for( int i = 0; i < box.getChildCount(); i++ ) {
			View otherPiece = box.getChildAt(i);
			EditText editText = otherPiece.findViewById(R.id.fatto_edita);
			if( editText != null && editText.isShown() ) {
				TextView textView = otherPiece.findViewById(R.id.fatto_testo);
				if( !editText.getText().toString().equals(textView.getText().toString()) ) // if there has been editing
					save(otherPiece);
				else
					restore(otherPiece);
			}
		}
	}

	/**
	 * Return 'object' casted in the required class,
	 * or a new instance of the class, but in this case it immediately goes back
	 * TODO code smell: no type safety and reflection creating new classes.
	 * */
	public Object cast(Class aClass) {
		Object casted = null;
		try {
			// Google translate badly he passes a new instance of the class, just so as not to nail the detail // male che vada gli passa una nuova istanza della classe, giusto per non inchiodare il dettaglio
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

	/**
	 * A wrapper for every possible widget that can be displayed on a 'Details...' activity
	 * */
	class Egg {
		String title;
		Object yolk; // Can be a method string ("Value", "Date", "Type"...) or an Address
		boolean common; // indicates whether to make it appear in the FAB menu to insert the piece
		boolean multiLine;
		Egg(String title, Object yolk, boolean common, boolean multiLine) {
			this.title = title;
			this.yolk = yolk;
			this.common = common;
			this.multiLine = multiLine;
			eggs.add(this); //TODO this is bad form: it relies on the side effect of creating an object, which should naturally be stateless, and makes it unclear from reading the code what should happen, besides for the compiler not necessarily knowing that anything was done with the object, which I would imagine makes garbage collection slower - besides for being bad form.
		}
	}

	/**
	 * This is an overload of the next method
	 * */
	public void place(String title, String method) {
		place(title, method, true, false);
	}

	/**
	 * Attempt to put a basic editable text piece in the layout
	 * */
	public void place(String title, String method, boolean common, boolean multiLine) {
		new Egg(title, method, common, multiLine);
		String text;
		try {
			text = (String)object.getClass().getMethod("get" + method).invoke(object); //TODO this reflection is bad performance
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

	/**
	 * Places this address in the layout. [place] is implemented with different signatures to
	 * accommodate various types of objects being placed.
	 * */
	public void place(String title, Address address) {
		Address addressNotNull = address == null ? new Address() : address;
		new Egg(title, addressNotNull, true, false);
		placePiece(title, writeAddress(address, false), addressNotNull, false);
	}

	/**
	 * @param event Events of [{@link FamilyActivity}]
	 * */
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
		if( object instanceof Integer ) {    //Full name in inexperienced mode
			click = this::edit;
		} else if( object instanceof String ) { // Method
			click = this::edit;
			// If it is a date
			if( object.equals("Date") ) {
				publisherDateLinearLayout = pieceView.findViewById(R.id.fatto_data);
				publisherDateLinearLayout.initialize(editText);
			}
		} else if( object instanceof Address ) { // Address
			click = v -> {
				Memory.add(object);
				startActivity(new Intent(this, AddressActivity.class));
			};
		} else if( object instanceof EventFact ) { // Event
			click = v -> {
				Memory.add(object);
				startActivity(new Intent(this, EventActivity.class));
			};
			// Family EventFacts can have notes and media
			LinearLayout scatolaNote = pieceView.findViewById(R.id.fatto_note);
			U.placeNotes(scatolaNote, object, false);
			U.placeMedia(scatolaNote, object, false);
		} else if( object instanceof GedcomTag ) { // Extension
			click = v -> {
				Memory.add(object);
				startActivity(new Intent(this, ExtensionActivity.class));
			};
		}
		pieceView.setOnClickListener(click);
		registerForContextMenu(pieceView);
		pieceView.setTag(R.id.tag_object, object); // It serves various processes to recognize the piece //Serve a vari processi per riconoscere il pezzo
		return pieceView;
	}

	public void placeExtensions(ExtensionContainer container) {
		for( Extension ext : U.findExtensions(container) ) {
			placePiece(ext.nome, ext.testo, ext.gedcomTag, false);
		}
	}

	public static String writeAddress(Address adr, boolean oneLine) {
		if( adr == null ) return null;
		String txt = ""; //TODO use StringBuilder
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

	/**
	 * Delete an address from the 3 possible containers
	 * */
	public void deleteAddress(Object container ) {
		if( container instanceof EventFact )
			((EventFact)container).setAddress( null );
		else if( container instanceof Repository )
			((Repository)container).setAddress( null );
		else if( container instanceof Submitter )
			((Submitter)container).setAddress( null );
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

	/**
	 * He composes the text of the event in FamilyActivity
	 * // Compone il testo dell'evento in Famiglia
	 * */
	public static String writeEvent(EventFact ef) {
		if( ef == null ) return null;
		String txt = ""; //TODO use StringBuilder
		if( ef.getValue() != null ) {
			if( ef.getValue().equals("Y") && ef.getTag() != null
					&& (ef.getTag().equals("MARR") || ef.getTag().equals("DIV")) )
				txt = Global.context.getString(R.string.yes);
			else
				txt = ef.getValue();
			txt += "\n";
		}
		if( ef.getDate() != null )
			txt += new GedcomDateConverter(ef.getDate()).writeDateLong() + "\n";
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
		concludeOtherPiece();
		// Then make this piece editable //Poi rende editabile questo pezzo
		TextView textView = pieceView.findViewById(R.id.fatto_testo);
		textView.setVisibility(View.GONE);
		fab.hide();
		Object pieceObject = pieceView.getTag(R.id.tag_object);
		boolean showInput = false;
		editText = pieceView.findViewById(R.id.fatto_edita);
		// Place
		if( pieceObject.equals("Place") ) {
			showInput = true;
			// If it hasn't already done so, it replaces EditText with PlaceFinderTextView
			if( !(editText instanceof PlaceFinderTextView) ) {
				ViewGroup parent = (ViewGroup)pieceView;  // todo: you could use Partview (vistaPezzo) directly if it were a ViewGroup or LinearLayout instead of View
				int index = parent.indexOfChild(editText);
				parent.removeView(editText);
				editText = new PlaceFinderTextView(editText.getContext(), null);
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
		} // All other normal editing cases
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

		// Intercept the 'Done' and 'Next' on the keyboard
		editText.setOnEditorActionListener((vista, actionId, keyEvent) -> {
			if( actionId == EditorInfo.IME_ACTION_DONE )
				save(pieceView);
			else if( actionId == EditorInfo.IME_ACTION_NEXT ) {
				if( !editText.getText().toString().equals(textView.getText().toString()) )
					save(pieceView);
				else
					restore(pieceView);
				View nextPiece = box.getChildAt(box.indexOfChild(pieceView) + 1);
				if( nextPiece != null && nextPiece.getTag(R.id.tag_object) instanceof String )
					edit(nextPiece);
			}
			return false;
		});

		// Custom ActionBar
		actionBar.setDisplayHomeAsUpEnabled(false);    // hides arrow <-
		whichMenu = 0;
		invalidateOptionsMenu();
		View editBar = getLayoutInflater().inflate(R.layout.barra_edita, new LinearLayout(box.getContext()), false);
		editBar.findViewById(R.id.edita_annulla).setOnClickListener(v -> {
			editText.setText(textView.getText());
			restore(pieceView);
		});
		editBar.findViewById(R.id.edita_salva).setOnClickListener(v -> save(pieceView));
		actionBar.setCustomView(editBar);
		actionBar.setDisplayShowCustomEnabled(true);
	}

	void save(View pieceView) {
		if( publisherDateLinearLayout != null ) publisherDateLinearLayout.encloseInParentheses(); // Basically just to add parentheses to the given sentence
		String text = editText.getText().toString().trim();
		Object pieceObject = pieceView.getTag(R.id.tag_object);
		if( pieceObject instanceof Integer ) { // Save first and last name for inexperienced (non-expert mode?)
			String firstName = ((EditText)box.getChildAt(0).findViewById(R.id.fatto_edita)).getText().toString();
			String lastName = ((EditText)box.getChildAt(1).findViewById(R.id.fatto_edita)).getText().toString();
			((Name)object).setValue( firstName + " /" + lastName + "/" );
		} else try { // All other normal methods
			object.getClass().getMethod("set" + pieceObject, String.class).invoke(object, text); //TODO reflection
		} catch( Exception e ) {
			Toast.makeText(box.getContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
			return; // in case of error it remains in editor mode
		}
		((TextView)pieceView.findViewById(R.id.fatto_testo)).setText(text);
		restore(pieceView);
		U.save(true, Memory.firstObject());
		/*if( Memory.getStepStack().size() == 1 ) {
			ricrea(); // Todo The record change date should be updated, but perhaps without reloading everything.
		}*/
		// In modified image the path updates the image (?) //In immagine modificato il percorso aggiorna l'immagine
		if( this instanceof ImageActivity && pieceObject.equals("File") )
			((ImageActivity)this).updateImage();
		// If he has modified an author, he asks to refer to him in the header //Se ha modificato un autore chiede di referenziarlo in header
		else if( object instanceof Submitter )
			U.autorePrincipale(this, ((Submitter)object).getId());
		else if( this instanceof EventActivity)
			refresh(); // To update the title bar
	}

	/**
	 * Operations common to Save and Cancel
	 * */
	void restore(View viewPiece) {
		editText.setVisibility(View.GONE);
		viewPiece.findViewById(R.id.fatto_data).setVisibility(View.GONE);
		viewPiece.findViewById(R.id.fatto_testo).setVisibility(View.VISIBLE);
		actionBar.setDisplayShowCustomEnabled(false); // hides custom bar// nasconde barra personalizzata
		actionBar.setDisplayHomeAsUpEnabled(true);
		whichMenu = 1;
		invalidateOptionsMenu();
		InputMethodManager imm = (InputMethodManager)getSystemService(getApplicationContext().INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(viewPiece.getWindowToken(), 0);
		if( !(object instanceof Note && !Global.settings.expert) ) // Notes in inexperienced mode have no fab //Le note in modalità inesperto non hanno fab
			fab.show();
	}

	/**
	 * Options menu
	 * serves to hide it when entering editor mode
	 * */
	int whichMenu = 1;
	@Override
	public boolean onCreateOptionsMenu( Menu menu ) {
		if( whichMenu == 1 ) { // Standard bar menu//Menu standard della barra
			if( object instanceof Submitter && (gc.getHeader() == null || // Non-principal(/main?) author
					gc.getHeader().getSubmitter(gc) == null || !gc.getHeader().getSubmitter(gc).equals(object)) )
				menu.add(0, 1, 0, R.string.make_default);
			if( object instanceof Media ) {
				if( box.findViewById(R.id.immagine_foto).getTag(R.id.tag_tipo_file).equals(1) )
					menu.add(0, 2, 0, R.string.crop);
				menu.add(0, 3, 0, R.string.choose_file);
			}
			if( object instanceof Family )
				menu.add(0, 4, 0, R.string.delete);
			else if( !(object instanceof Submitter && U.submitterHasShared((Submitter)object)) ) // the author who shared cannot be deleted
				menu.add(0, 5, 0, R.string.delete);
		}
		return true;
	}
	/**
	 * is invoked when a menu item is chosen AND by clicking the back arrow
	 * */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if( id == 1 ) { // Main author //TODO code smell : magic number
			ListOfAuthorsFragment.mainAuthor((Submitter)object);
		} else if( id == 2 ) { // Image: crop
			cropImage(box);
		} else if( id == 3 ) { // Image: choose
			F.displayImageCaptureDialog(this, null, 5173, null);
		} else if( id == 4 ) { // Family
			Family fam = (Family)object;
			if( fam.getHusbandRefs().size() + fam.getWifeRefs().size() + fam.getChildRefs().size() > 0 ) {
				new AlertDialog.Builder(this).setMessage(R.string.really_delete_family)
						.setPositiveButton(android.R.string.yes, (dialog, i) -> {
							ChurchFragment.deleteFamily(fam);
							onBackPressed();
						}).setNeutralButton(android.R.string.cancel, null).show();
			} else {
				ChurchFragment.deleteFamily(fam);
				onBackPressed();
			}
		} else if( id == 5 ) { // All the others
			// todo: confirm deletion of all objects..
			delete();
			U.save (true); // the update of the dates takes place in the Overrides of delete()
			onBackPressed();
		} else if( id == android.R.id.home ) {
			onBackPressed();
		}
		return true;
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		if( object instanceof EventFact )
			EventActivity.cleanUpTag((EventFact)object);
		Memory.clearStackAndRemove();
	}

	public void delete() {}

	// Contextual menu
	View pieceView; // editable text, notes, quotes, media ...
	Object pieceObject;
	Person person; // as it is used a lot, we make it a pieceObject in its own right
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo info) { // info is null
		if( whichMenu != 0 ) { // If we are in edit mode show the editor menus
			pieceView = view;
			pieceObject = view.getTag(R.id.tag_object);
			if( pieceObject instanceof Person ) {
				person = (Person)pieceObject;
				Family fam = (Family)object;
				// Generate labels for 'family' entries (such as child and spouse)
				String[] famLabels = {null, null};
				if( person.getParentFamilies(gc).size() > 1 && person.getSpouseFamilies(gc).size() > 1 ) {
					famLabels[0] = getString(R.string.family_as_child);
					famLabels[1] = getString(R.string.family_as_spouse);
				}
				menu.add(0, 10, 0, R.string.diagram);
				menu.add(0, 11, 0, R.string.card);
				if( famLabels[0] != null )
					menu.add(0, 12, 0, famLabels[0]);
				if( famLabels[1] != null )
					menu.add(0, 13, 0, famLabels[1]);
				if( fam.getChildren(gc).indexOf(person) > 0 )
					menu.add(0, 14, 0, R.string.move_before);
				if( fam.getChildren(gc).indexOf(person) < fam.getChildren(gc).size() - 1 && fam.getChildren(gc).contains(person) )
					// thus excludes parents whose index is -1
					menu.add(0, 15, 0, R.string.move_after);
				menu.add(0, 16, 0, R.string.modify);
				if( FamilyActivity.findParentFamilyRef(person, fam) != null )
					menu.add(0, 17, 0, R.string.lineage);
				menu.add(0, 18, 0, R.string.unlink);
				menu.add(0, 19, 0, R.string.delete);
			} else if( pieceObject instanceof Note ) {
				menu.add(0, 20, 0, R.string.copy);
				if( ((Note)pieceObject).getId() != null )
					menu.add(0, 21, 0, R.string.unlink);
				menu.add(0, 22, 0, R.string.delete);
			} else if( pieceObject instanceof SourceCitation ) {
				menu.add(0, 30, 0, R.string.copy);
				menu.add(0, 31, 0, R.string.delete);
			} else if( pieceObject instanceof Media ) {
				if( ((Media)pieceObject).getId() != null )
					menu.add(0, 40, 0, R.string.unlink);
				menu.add(0, 41, 0, R.string.delete);
			} else if( pieceObject instanceof Address ) {
				menu.add(0, 50, 0, R.string.copy);
				menu.add(0, 51, 0, R.string.delete);
			} else if( pieceObject instanceof EventFact ) {
				menu.add(0, 55, 0, R.string.copy);
				Family fam = (Family)object;
				if( fam.getEventsFacts().indexOf(pieceObject) > 0 )
					menu.add(0, 56, 0, R.string.move_up);
				if( fam.getEventsFacts().contains(pieceObject)
						&& fam.getEventsFacts().indexOf(pieceObject) < fam.getEventsFacts().size() - 1 )
					menu.add(0, 57, 0, R.string.move_down);
				menu.add(0, 58, 0, R.string.delete);
			} else if( pieceObject instanceof GedcomTag ) {
				menu.add(0, 60, 0, R.string.copy);
				menu.add(0, 61, 0, R.string.delete);
			} else if( pieceObject instanceof Source ) {
				menu.add(0, 70, 0, R.string.copy);
				menu.add(0, 71, 0, R.string.choose_source);
			} else if( pieceObject instanceof RepositoryRef ) {
				menu.add(0, 80, 0, R.string.copy);
				menu.add(0, 81, 0, R.string.delete);
			} else if( pieceObject instanceof Repository ) {
				menu.add(0, 90, 0, R.string.copy);
				menu.add(0, 91, 0, R.string.choose_repository);
			} else if( pieceObject instanceof Integer ) {
				if( pieceObject.equals(43614) ) { //Google translate: "Imagine it", probably Image// Immaginona
					// it is a croppable image
					if( pieceView.findViewById(R.id.immagine_foto).getTag(R.id.tag_tipo_file).equals(1) )
						menu.add(0, 100, 0, R.string.crop);
					menu.add(0, 101, 0, R.string.choose_file);
				} else if( pieceObject.equals(4043) || pieceObject.equals(6064) ) // Name and surname for inexperienced
					menu.add(0, 0, 0, R.string.copy);
			} else if( pieceObject instanceof String ) {
				if( ((TextView)view.findViewById(R.id.fatto_testo)).getText().length() > 0 )
					menu.add(0, 0, 0, R.string.copy);
				menu.add(0, 1, 0, R.string.delete);
			}
		}
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		switch( item.getItemId() ) {
			// TODo all deletes require deletion confirmation
			// Copy
			case 0:	// Editable piece
			case 50: // Address
			case 55: // Event
			case 60: // Extension
				U.copyToClipboard(((TextView)pieceView.findViewById(R.id.fatto_titolo)).getText(),
						((TextView)pieceView.findViewById(R.id.fatto_testo)).getText());
				return true;
			case 1: // Delete editable piece
				try {
					object.getClass().getMethod("set" + pieceObject, String.class).invoke(object, (Object)null);
				} catch( Exception e ) {
					Toast.makeText(box.getContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
					break;
				}
				break;
			case 10: // Diagram
				U.askWhichParentsToShow(this, person, 1);
				return true;
			case 11: // Person card? - Scheda persona
				Memory.setFirst(person);
				startActivity(new Intent(this, IndividualPersonActivity.class));
				return true;
			case 12: // Family as? with? a son - Famiglia come figlio
				U.askWhichParentsToShow(this, person, 2);
				return true;
			case 13: // Family as a spouse? - Famiglia come coniuge
				U.askWhichSpouceToShow(this, person, null);
				return true;
			case 14: // Son moves on? - Figlio sposta su
				Family fa = (Family)object;
				ChildRef childRef1 = fa.getChildRefs().get(fa.getChildren(gc).indexOf(person));
				fa.getChildRefs().add(fa.getChildRefs().indexOf(childRef1) - 1, childRef1);
				fa.getChildRefs().remove(fa.getChildRefs().lastIndexOf(childRef1));
				break;
			case 15: // Son moves down? - Figlio sposta giù
				Family f = (Family)object;
				ChildRef childRef = f.getChildRefs().get(f.getChildren(gc).indexOf(person));
				f.getChildRefs().add(f.getChildRefs().indexOf(childRef) + 2, childRef);
				f.getChildRefs().remove(f.getChildRefs().indexOf(childRef)); //TODO can this be optimizeed to use removal by object?
				break;
			case 16: // Modification
				Intent i = new Intent(this, IndividualEditorActivity.class);
				i.putExtra("idIndividuo", person.getId());
				startActivity(i);
				return true;
			case 17: // Lineage
				FamilyActivity.chooseLineage(this, person, (Family)object);
				break;
			case 18: // Disconnect
				FamilyActivity.disconnect((SpouseFamilyRef)pieceView.getTag(R.id.tag_spouse_family_ref),
						(SpouseRef)pieceView.getTag(R.id.tag_spouse_ref));
				U.updateChangeDate(person);
				findAnotherRepresentativeOfTheFamily(person);
				break;
			case 19: // Delete member
				new AlertDialog.Builder(this).setMessage(R.string.really_delete_person)
						.setPositiveButton(R.string.delete, (dialog, id) -> {
							RegistryOfficeFragment.deletePerson(this, person.getId());
							box.removeView(pieceView);
							findAnotherRepresentativeOfTheFamily(person);
						}).setNeutralButton(R.string.cancel, null).show();
				return true;
			case 20: // Nota
				U.copyToClipboard(getText(R.string.note), ((TextView)pieceView.findViewById(R.id.nota_testo)).getText());
				return true;
			case 21:
				U.disconnectNote((Note)pieceObject, object, null);
				break;
			case 22:
				Object[] heads /*capi*/ = U.deleteNote((Note)pieceObject, pieceView);
				U.save(true, heads);
				return true;
			case 30: // Source citation
				U.copyToClipboard(getText(R.string.source_citation),
						((TextView)pieceView.findViewById(R.id.fonte_testo)).getText() + "\n"
								+ ((TextView)pieceView.findViewById(R.id.citazione_testo)).getText());
				return true;
			case 31:
				if( object instanceof Note ) // Notes does not extend SourceCitationContainer
					((Note)object).getSourceCitations().remove(pieceObject);
				else
					((SourceCitationContainer)object).getSourceCitations().remove(pieceObject);
				Memory.setInstanceAndAllSubsequentToNull(pieceObject);
				break;
			case 40: // Media
				GalleryFragment.disconnectMedia(((Media)pieceObject).getId(), (MediaContainer)object);
				break;
			case 41:
				Object[] mediaHeads = GalleryFragment.deleteMedia((Media)pieceObject, null);
				U.save(true, mediaHeads); // a shared media may need to update the dates of multiple heads
				refresh();
				return true;
			case 51:
				deleteAddress(object);
				break;
			case 56: // Family Event
				int index1 = ((Family)object).getEventsFacts().indexOf(pieceObject);
				Collections.swap(((Family)object).getEventsFacts(), index1, index1 - 1);
				break;
			case 57:
				int index2 = ((Family)object).getEventsFacts().indexOf(pieceObject);
				Collections.swap(((Family)object).getEventsFacts(), index2, index2 + 1);
				break;
			case 58:
				((Family)object).getEventsFacts().remove(pieceObject);
				Memory.setInstanceAndAllSubsequentToNull(pieceObject);
				break;
			case 61: // Extension
				U.deleteExtension((GedcomTag)pieceObject, object, null);
				break;
			// Source
			case 70: // Copy
				U.copyToClipboard(getText(R.string.source), ((TextView)pieceView.findViewById(R.id.fonte_testo)).getText());
				return true;
			case 71: // Choose in the Library
				Intent inte = new Intent(this, Principal.class);
				inte.putExtra("bibliotecaScegliFonte", true);
				startActivityForResult(inte, 7047);
				return true;
			// Citation repository
			case 80: // Copy
				U.copyToClipboard(getText(R.string.repository_citation),
						((TextView)pieceView.findViewById(R.id.fonte_testo)).getText() + "\n"
								+ ((TextView)pieceView.findViewById(R.id.citazione_testo)).getText());
				return true;
			case 81: // Delete
				((Source)object).setRepositoryRef(null);
				Memory.setInstanceAndAllSubsequentToNull(pieceObject);
				break;
			// Repository
			case 90: // Copy
				U.copyToClipboard(getText(R.string.repository), ((TextView)pieceView.findViewById(R.id.fonte_testo)).getText());
				return true;
			case 91: // Choose in magazine?
				Intent intn = new Intent(this, Principal.class);
				intn.putExtra("magazzinoScegliArchivio", true);
				startActivityForResult(intn, 5390);
				return true;
			case 100: // Crop image
				cropImage(pieceView);
				return true;
			case 101: // Choose image
				F.displayImageCaptureDialog(this, null, 5173, null);
				return true;
			default:
				return false;
		}
		// First recreate the page and then save, which for large trees can take a few seconds
		// closeContextMenu(); // Useless. Closing the menu waits for the end of saving,
		// unless you put saveJson () inside a postDelayed () of at least 500 ms
		U.updateChangeDate(Memory.firstObject());
		refresh();
		U.save(true, (Object[])null);
		return true;
	}

	/**
	 * Fix a Family Representative to correctly show "Link Existing Person" in the menu
	 * */
	private void findAnotherRepresentativeOfTheFamily(Person p) {
		if( aRepresentativeOfTheFamily.equals(p) ) {
			Family fam = (Family)object;
			if( !fam.getHusbands(gc).isEmpty() )
				aRepresentativeOfTheFamily = fam.getHusbands(gc).get(0);
			else if( !fam.getWives(gc).isEmpty() )
				aRepresentativeOfTheFamily = fam.getWives(gc).get(0);
			else if( !fam.getChildren(gc).isEmpty() )
				aRepresentativeOfTheFamily = fam.getChildren(gc).get(0);
			else
				aRepresentativeOfTheFamily = null;
		}
	}

	/**
	 * Receives a View in which there is the image to be cropped and starts cropping
	 * */
	private void cropImage(View view) {
		ImageView imageView = view.findViewById(R.id.immagine_foto);
		File mediaFile = null;
		String path = (String)imageView.getTag(R.id.tag_percorso);
		if( path != null )
			mediaFile = new File(path);
		Uri mediaUri = (Uri)imageView.getTag(R.id.tag_uri);
		Global.croppedMedia = (Media)object;
		F.cropImage(this, mediaFile, mediaUri, null);
	}

	/**
	 * Closes the keyboard that may be visible
	 * */
	@Override
	protected void onPause() {
		super.onPause();
		if( editText != null ) {
			InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		F.permissionsResult(this, null, requestCode, permissions, grantResults,
				object instanceof MediaContainer ? (MediaContainer)object : null);
				// Image has Media 'object' instance, not MediaContainer
	}
}
