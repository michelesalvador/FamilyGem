package app.familygem;

import android.content.Intent;
import android.os.Bundle;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import androidx.core.util.Pair;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.TextView;
import android.widget.Toast;
import com.theartofdev.edmodo.cropper.CropImage;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaRef;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SourceCitation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import app.familygem.constants.Gender;
import app.familygem.dettaglio.CitazioneFonte;
import app.familygem.dettaglio.Evento;
import app.familygem.dettaglio.Nome;
import app.familygem.dettaglio.Nota;
import static app.familygem.Global.gc;

public class Individuo extends AppCompatActivity {

	Person uno;
	TabLayout tabLayout;
	String[] mainEventTags = {"BIRT", "BAPM", "RESI", "OCCU", "DEAT", "BURI"};
	List<Pair<String, String>> otherEvents; // List of tag + label

	@Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		U.gedcomSicuro(gc);
		uno = (Person)Memoria.getOggetto();
		// Se l'app va in background e viene stoppata, 'Memoria' è resettata e quindi 'uno' sarà null
		if( uno == null && bundle != null ) {
			uno = gc.getPerson(bundle.getString("idUno")); // In bundle è salvato l'id dell'individuo
			Memoria.setPrimo(uno); // Altrimenti la memoria è senza una pila
		}
		if( uno == null ) return; // Capita raramente che il bundle non faccia il suo lavoro
		Global.indi = uno.getId();
		setContentView(R.layout.individuo);

		// Barra
		Toolbar barra = findViewById(R.id.toolbar);
		setSupportActionBar( barra );
		getSupportActionBar().setDisplayHomeAsUpEnabled(true); // fa comparire la freccia indietro e il menu

		// Assegna alla vista pagina un adapter che gestisce le tre schede
		ViewPager vistaPagina = findViewById( R.id.schede_persona );
		ImpaginatoreSezioni impaginatoreSezioni = new ImpaginatoreSezioni();
		vistaPagina.setAdapter( impaginatoreSezioni );

		// arricchisce il tablayout
		tabLayout = findViewById(R.id.tabs);
		tabLayout.setupWithViewPager(vistaPagina);	// altrimenti il testo nei TabItem scompare (?!)
		tabLayout.getTabAt(0).setText(R.string.media);
		tabLayout.getTabAt(1).setText(R.string.events);
		tabLayout.getTabAt(2).setText(R.string.relatives);
		tabLayout.getTabAt(getIntent().getIntExtra("scheda", 1)).select();

		// per animare il FAB
		final FloatingActionButton fab = findViewById( R.id.fab );
		vistaPagina.addOnPageChangeListener( new ViewPager.OnPageChangeListener() {
			@Override
			public void onPageScrolled( int posizione,  // 0 tra la prima e la seconda, 1 tra la seconda e la terza...
										float scostamento, // 1->0 a destra, 0->1 a sinistra
										int positionOffsetPixels ) {
				if( scostamento > 0 )
					fab.hide();
				else
					fab.show();
			}
			@Override
			public void onPageSelected( int position ) {}
			@Override
			public void onPageScrollStateChanged( int state ) {}
		});

		// List of other events
		String[] otherEventTags = {"CHR", "CREM", "ADOP", "BARM", "BLES", "CONF", "FCOM", "ORDN", //Events
				"NATU", "EMIG", "IMMI", "CENS", "PROB", "WILL", "GRAD", "RETI", "EVEN",
				"CAST", "DSCR", "EDUC", "NATI", "NCHI", "PROP", "RELI", "SSN", "TITL", // Attributes
				"_MILT"}; // User-defined
			/* Standard GEDCOM tags missing in the EventFact.DISPLAY_TYPE list:
				BASM (there is BATM instead) CHRA IDNO NMR FACT */
		otherEvents = new ArrayList<>();
		for( String tag : otherEventTags ) {
			String label = EventFact.DISPLAY_TYPE.get(tag);
			if( Global.settings.expert )
				label += " — " + tag;
			otherEvents.add(new Pair<>(tag, label));
		}
		// Alphabetically sorted by label
		Collections.sort(otherEvents, (item1, item2) -> item1.second.compareTo(item2.second));
	}

	class ImpaginatoreSezioni extends FragmentPagerAdapter {

		ImpaginatoreSezioni() {
			super( getSupportFragmentManager() );
		}

		@Override	// in realtà non seleziona ma CREA le tre schede
		public Fragment getItem( int position ) {
			Fragment scheda = new Fragment();
			if( position == 0 )
				scheda = new IndividuoMedia();
			else if( position == 1 )
				scheda = new IndividuoEventi();
			else if( position == 2 )
				scheda = new IndividuoFamiliari();
			return scheda;
		}

		@Override   // necessario
		public int getCount() {
			return 3;
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		if( uno == null || Global.edited )
			uno = gc.getPerson(Global.indi);

		if( uno == null ) { // ritornando indietro nella Scheda di un individuo che è stato eliminato
			onBackPressed();
			return;
		}

		// Tutto ciò che nella pagina può cambiare
		if( Global.settings.expert )
			((TextView)findViewById(R.id.persona_id)).setText("INDI " + uno.getId());
		CollapsingToolbarLayout barraCollasso = findViewById(R.id.toolbar_layout);
		barraCollasso.setTitle(U.epiteto(uno)); // aggiorna il titolo se il nome viene modificato, ma non lo setta se è una stringa vuota
		F.unaFoto(Global.gc, uno, findViewById(R.id.persona_foto));
		F.unaFoto(Global.gc, uno, findViewById(R.id.persona_sfondo));
		if( Global.edited ) {
			// Ricostruisce le tre schede ritornando alla pagina
			for( int i = 0; i < 3; i++ ) {
				Fragment scheda = getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.schede_persona + ":" + i);
				if( scheda != null ) { // alla prima creazione dell'activity sono null
					getSupportFragmentManager().beginTransaction().detach(scheda).commit();
					getSupportFragmentManager().beginTransaction().attach(scheda).commit();
				}
				// ToDo tornando indietro dopo una editazione non aggiorna la scheda 0 coi media...
			}
			invalidateOptionsMenu();
		}

		// Menu FAB
		findViewById(R.id.fab).setOnClickListener(vista -> {
			PopupMenu popup = new PopupMenu(Individuo.this, vista);
			Menu menu = popup.getMenu();
			switch( tabLayout.getSelectedTabPosition() ) {
				case 0: // Individuo Media
					menu.add(0, 10, 0, R.string.new_media);
					menu.add(0, 11, 0, R.string.new_shared_media);
					if( !gc.getMedia().isEmpty() )
						menu.add(0, 12, 0, R.string.link_shared_media);
					break;
				case 1: // Individuo Eventi
					menu.add(0, 20, 0, R.string.name);
					// Sesso
					if( Gender.getGender(uno) == Gender.NONE )
						menu.add(0, 21, 0, R.string.sex);
					// Main events
					SubMenu eventSubMenu = menu.addSubMenu(R.string.event);
					CharSequence[] mainEventLabels = {getText(R.string.birth), getText(R.string.baptism), getText(R.string.residence), getText(R.string.occupation), getText(R.string.death), getText(R.string.burial)};
					int i;
					for( i = 0; i < mainEventLabels.length; i++ ) {
						CharSequence label = mainEventLabels[i];
						if( Global.settings.expert )
							label += " — " + mainEventTags[i];
						eventSubMenu.add(0, 40 + i, 0, label);
					}
					// Other events
					SubMenu otherSubMenu = eventSubMenu.addSubMenu(R.string.other);
					i = 0;
					for( Pair item : otherEvents ) {
						otherSubMenu.add(0, 50 + i, 0, (String)item.second);
						i++;
					}
					SubMenu subNota = menu.addSubMenu(R.string.note);
					subNota.add(0, 22, 0, R.string.new_note);
					subNota.add(0, 23, 0, R.string.new_shared_note);
					if( !gc.getNotes().isEmpty() )
						subNota.add(0, 24, 0, R.string.link_shared_note);
					if( Global.settings.expert ) {
						SubMenu subFonte = menu.addSubMenu(R.string.source);
						subFonte.add(0, 25, 0, R.string.new_source_note);
						subFonte.add(0, 26, 0, R.string.new_source);
						if( !gc.getSources().isEmpty() )
							subFonte.add(0, 27, 0, R.string.link_source);
					}
					break;
				case 2: // Individuo Familiari
					menu.add(0, 30, 0, R.string.new_relative);
					if( U.ciSonoIndividuiCollegabili(uno) )
						menu.add(0, 31, 0, R.string.link_person);
			}
			popup.show();
			popup.setOnMenuItemClickListener(item -> {
				CharSequence[] familiari = {getText(R.string.parent), getText(R.string.sibling), getText(R.string.partner), getText(R.string.child)};
				AlertDialog.Builder builder = new AlertDialog.Builder(Individuo.this);
				switch( item.getItemId() ) {
					// Scheda Eventi
					case 0:
						break;
					// Media
					case 10: // Cerca media locale
						F.appAcquisizioneImmagine(Individuo.this, null, 2173, uno);
						break;
					case 11: // Cerca oggetto media
						F.appAcquisizioneImmagine(Individuo.this, null, 2174, uno);
						break;
					case 12: // Collega media in Galleria
						Intent inten = new Intent(Individuo.this, Principal.class);
						inten.putExtra("galleriaScegliMedia", true);
						startActivityForResult(inten, 43614);
						break;
					case 20: // Crea nome
						Name nome = new Name();
						nome.setValue("//");
						uno.addName(nome);
						Memoria.aggiungi(nome);
						startActivity(new Intent(Individuo.this, Nome.class));
						break;
					case 21: // Crea sesso
						String[] sessoNomi = { getString(R.string.male), getString(R.string.female), getString(R.string.unknown) };
						new AlertDialog.Builder( tabLayout.getContext() )
								.setSingleChoiceItems( sessoNomi, -1, ( dialo, i ) -> {
									EventFact genere = new EventFact();
									genere.setTag( "SEX" );
									String[] sessoValori = { "M", "F", "U" };
									genere.setValue( sessoValori[i] );
									uno.addEventFact( genere );
									dialo.dismiss();
									IndividuoEventi.aggiornaRuoliConiugali(uno);
									IndividuoEventi tabEventi = (IndividuoEventi) getSupportFragmentManager().findFragmentByTag( "android:switcher:" + R.id.schede_persona + ":1" );
									tabEventi.refresh(1);
									U.salvaJson(true, uno);
								}).show();
						break;
					case 22: { // Crea nota
						Note nota = new Note();
						nota.setValue( "" );
						uno.addNote( nota );
						Memoria.aggiungi( nota );
						startActivity( new Intent( Individuo.this, Nota.class ) );
						// todo? Dettaglio.edita( View vistaValore );
						break;
					}
					case 23: // Crea nota condivisa
						Quaderno.nuovaNota( Individuo.this, uno );
						break;
					case 24: // Collega nota condivisa
						Intent inte = new Intent( Individuo.this, Principal.class );
						inte.putExtra( "quadernoScegliNota", true );
						startActivityForResult( inte,4074 );
						break;
					case 25: // Nuova fonte-nota
						SourceCitation citaz = new SourceCitation();
						citaz.setValue("");
						uno.addSourceCitation(citaz);
						Memoria.aggiungi(citaz);
						startActivity(new Intent(Individuo.this, CitazioneFonte.class));
						break;
					case 26: // Nuova fonte
						Biblioteca.nuovaFonte(Individuo.this, uno);
						break;
					case 27: // Collega fonte
						Intent intento = new Intent(Individuo.this, Principal.class);
						intento.putExtra("bibliotecaScegliFonte", true);
						startActivityForResult(intento, 50473);
						break;
					// Scheda Familiari
					case 30:// Collega persona nuova
						if( Global.settings.expert ) {
							DialogFragment dialog = new NuovoParente(uno, null, null, true, null);
							dialog.show(getSupportFragmentManager(), "scegli");
						} else {
							builder.setItems(familiari, (dialog, quale) -> {
								Intent intento1 = new Intent(getApplicationContext(), EditaIndividuo.class);
								intento1.putExtra("idIndividuo", uno.getId());
								intento1.putExtra("relazione", quale + 1);
								if( U.controllaMultiMatrimoni(intento1, Individuo.this, null) )
									return;
								startActivity(intento1);
							}).show();
						}
						break;
					case 31: // Collega persona esistente
						if( Global.settings.expert ) {
							DialogFragment dialog = new NuovoParente(uno, null, null, false, null);
							dialog.show(getSupportFragmentManager(), "scegli");
						} else {
							builder.setItems(familiari, (dialog, quale) -> {
								Intent intento2 = new Intent(getApplication(), Principal.class);
								intento2.putExtra("idIndividuo", uno.getId());
								intento2.putExtra("anagrafeScegliParente", true);
								intento2.putExtra("relazione", quale + 1);
								if( U.controllaMultiMatrimoni(intento2, Individuo.this, null) )
									return;
								startActivityForResult(intento2, 1401);
							}).show();
						}
						break;
					default:
						String keyTag = null;
						if( item.getItemId() >= 50 ) {
							keyTag = otherEvents.get(item.getItemId() - 50).first;
						} else if( item.getItemId() >= 40 )
							keyTag = mainEventTags[item.getItemId() - 40];
						if( keyTag != null ) {
							EventFact nuovoEvento = new EventFact();
							nuovoEvento.setTag(keyTag);
							switch( keyTag ) {
								case "OCCU":
									nuovoEvento.setValue("");
									break;
								case "RESI":
									nuovoEvento.setPlace("");
									break;
								case "BIRT":
								case "DEAT":
								case "CHR":
								case "BAPM":
								case "BURI":
									nuovoEvento.setPlace("");
									nuovoEvento.setDate("");
							}
							uno.addEventFact(nuovoEvento);
							Memoria.aggiungi(nuovoEvento);
							startActivity(new Intent(Individuo.this, Evento.class));
							U.salvaJson(true, uno);
							return true;
						}
						return false;
				}
				return true;
			});
		});
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString("idUno", uno.getId());
		s.l("outState",outState);
	}

	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data ) {
		super.onActivityResult( requestCode, resultCode, data );
		if( resultCode == RESULT_OK ) {
			if( requestCode == 2173 ) { // File fornito da un'app diventa media locale eventualmente ritagliato con Android Image Cropper
				Media media = new Media();
				media.setFileTag("FILE");
				uno.addMedia( media );
				if( F.proponiRitaglio( this, null, data, media ) ) { // restituisce true se è un'immagine ritagliabile
					U.salvaJson( true, uno );
					return;
				}
			} else if( requestCode == 2174 ) { // File dalle app in nuovo Media condiviso, con proposta di ritagliarlo
				Media media = Galleria.nuovoMedia( uno );
				if( F.proponiRitaglio( this, null, data, media ) ) {
					U.salvaJson( true, media, uno );
					return;
				}
			} else if( requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE ) {
				// Ottiene l'immagine ritagliata da Android Image Cropper
				F.fineRitaglioImmagine( data );
				U.salvaJson(true); // la data di cambio per i Media condivisi viene già salvata nel passaggio precedente
						// todo passargli Global.mediaCroppato ?
				return;
			} else if( requestCode == 43614 ) { // Media da Galleria
				MediaRef rifMedia = new MediaRef();
				rifMedia.setRef( data.getStringExtra("idMedia") );
				uno.addMediaRef( rifMedia );
			} else if( requestCode == 4074  ) { // Nota
				NoteRef rifNota = new NoteRef();
				rifNota.setRef( data.getStringExtra("idNota") );
				uno.addNoteRef( rifNota );
			} else if( requestCode == 50473  ) { // Fonte
				SourceCitation citaz = new SourceCitation();
				citaz.setRef( data.getStringExtra("idFonte") );
				uno.addSourceCitation( citaz );
			} else if( requestCode == 1401  ) { // Parente
				Object[] modificati = EditaIndividuo.aggiungiParente(
						data.getStringExtra("idIndividuo"), // corrisponde a uno.getId()
						data.getStringExtra("idParente"),
						data.getStringExtra("idFamiglia"),
						data.getIntExtra("relazione", 0),
						data.getStringExtra("collocazione") );
				U.salvaJson( true, modificati );
				return;
			}
			U.salvaJson( true, uno );
		} else if( requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE ) // se clic su freccia indietro in Crop Image
			Global.edited = true;
	}

	@Override
	public void onBackPressed() {
		Memoria.arretra();
		super.onBackPressed();
	}

	// Menu Opzioni
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, 0, 0, R.string.diagram);
		String[] familyLabels = Diagram.getFamilyLabels(this, uno, null);
		if( familyLabels[0] != null )
			menu.add(0, 1, 0, familyLabels[0]);
		if( familyLabels[1] != null )
			menu.add(0, 2, 0, familyLabels[1]);
		if( Global.settings.getCurrentTree().root == null || !Global.settings.getCurrentTree().root.equals(uno.getId()) )
			menu.add(0, 3, 0, R.string.make_root);
		menu.add(0, 4, 0, R.string.modify);
		menu.add(0, 5, 0, R.string.delete);
		return true;
	}
	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
		switch ( item.getItemId() ) {
			case 0:	// Diagramma
				U.qualiGenitoriMostrare(this, uno, 1);
				return true;
			case 1: // Famiglia come figlio
				U.qualiGenitoriMostrare(this, uno, 2);
				return true;
			case 2: // Famiglia come coniuge
				U.qualiConiugiMostrare(this, uno, null);
				return true;
			case 3: // Imposta come radice
				Global.settings.getCurrentTree().root = uno.getId();
				Global.settings.save();
				Toast.makeText(this, getString(R.string.this_is_root, U.epiteto(uno)), Toast.LENGTH_LONG).show();
				return true;
			case 4: // Modifica
				Intent intent1 = new Intent(this, EditaIndividuo.class);
				intent1.putExtra("idIndividuo", uno.getId());
				startActivity(intent1);
				return true;
			case 5:	// Elimina
				new AlertDialog.Builder(this).setMessage(R.string.really_delete_person)
						.setPositiveButton(R.string.delete, (dialog, i) -> {
							Family[] famiglie = Anagrafe.eliminaPersona(this, uno.getId());
							if( !U.controllaFamiglieVuote( this, this::onBackPressed, true, famiglie) )
								onBackPressed();
						}).setNeutralButton(R.string.cancel, null).show();
				return true;
			default:
				onBackPressed();
		}
		return false;
	}

	@Override
	public void onRequestPermissionsResult( int codice, String[] permessi, int[] accordi ) {
		super.onRequestPermissionsResult(codice, permessi, accordi);
		F.risultatoPermessi(this, null, codice, permessi, accordi, uno);
	}
}
