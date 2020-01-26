package app.familygem;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.theartofdev.edmodo.cropper.CropImage;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaRef;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SourceCitation;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import app.familygem.dettaglio.CitazioneFonte;
import app.familygem.dettaglio.Evento;
import app.familygem.dettaglio.Famiglia;
import app.familygem.dettaglio.Nome;
import app.familygem.dettaglio.Nota;
import static app.familygem.Globale.gc;

public class Individuo extends AppCompatActivity {

	Person uno;
	TabLayout tabLayout;
	String[] pochiEventiTag = { "BIRT", "BAPM", "RESI", "OCCU", "DEAT", "BURI" };
	TreeMap<String,String> altriEventi;

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		String id = getIntent().getStringExtra( "idIndividuo" );
		if( gc == null ) {
			Alberi.apriGedcom( Globale.preferenze.idAprendo, false );
		}
		uno = gc.getPerson( id );
		//Globale.individuo = id;	// passava l'id alle schede fragment
		if( Memoria.getOggetto() != uno ) // per evitare di ricreare pile già iniziate
			Memoria.setPrimo( uno, "INDI" );
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
		tabLayout.getTabAt( getIntent().getIntExtra( "scheda", 1 ) ).select();

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
		if( uno == null || Globale.editato )
			uno = gc.getPerson( getIntent().getStringExtra( "idIndividuo" ) );

		if( uno == null ) { // ritornando indietro nella Scheda di un individuo che è stato eliminato
			onBackPressed();
			return;
		}

		// Tutto ciò che nella pagina può cambiare
		if( Globale.preferenze.esperto )
			((TextView)findViewById( R.id.persona_id )).setText( uno.getId() );
 		CollapsingToolbarLayout barraCollasso = findViewById(R.id.toolbar_layout);
		barraCollasso.setTitle( U.epiteto(uno) ); // aggiorna il titolo se il nome viene modificato, ma non lo setta se è una stringa vuota
		//s.l( "barraCollasso "+barraCollasso+" '"+U.epiteto( uno )+"'");
		U.unaFoto( Globale.gc, uno, findViewById(R.id.persona_foto) );
		U.unaFoto( Globale.gc, uno, findViewById(R.id.persona_sfondo) );
		if( Globale.editato ) {
			// Ricostruisce le tre schede ritornando alla pagina
			for( int i=0; i<3; i++ ) {
				Fragment scheda = getSupportFragmentManager().findFragmentByTag( "android:switcher:" + R.id.schede_persona + ":"+i );
				//s.l(i+" "+scheda);
				if( scheda != null ) // alla prima creazione dell'activity sono null
					getSupportFragmentManager().beginTransaction().detach( scheda ).attach( scheda ).commit();
				// ToDo tornando indietro dopo una editazione non aggiorna la scheda 0 coi media...
			}
		}

		// Menu FAB
		findViewById( R.id.fab ).setOnClickListener( vista -> {
			PopupMenu popup = new PopupMenu( Individuo.this, vista );
			Menu menu = popup.getMenu();
			switch( tabLayout.getSelectedTabPosition() ){
				case 0: // Individuo Media
					menu.add( 0, 10, 0, R.string.new_media );
					menu.add( 0, 11, 0, R.string.new_shared_media );
					menu.add( 0, 12, 0, R.string.link_shared_media );
					break;
				case 1: // Individuo Eventi
					menu.add( 0, 20, 0, R.string.name );
					// Sesso
					if( U.sesso(uno) == 0 )
						menu.add( 0, 21, 0, R.string.sex );
					// Eventi principali
					SubMenu subEvento = menu.addSubMenu( 0, 0, 0, R.string.event );
					CharSequence[] pochiEventiDefiniz = { getText(R.string.birth), getText(R.string.baptism), getText(R.string.residence), getText(R.string.occupation), getText(R.string.death), getText(R.string.burial) };
					int i;
					for( i = 0; i < pochiEventiDefiniz.length; i++ )
						subEvento.add( 0, 40+i, 0, pochiEventiDefiniz[i] );
					// Lista semplificata di altri eventi
					SubMenu subAltri = subEvento.addSubMenu( 0, 0, 0, R.string.other );
					altriEventi = new TreeMap<>( EventFact.DISPLAY_TYPE );
					for( String tag : pochiEventiTag )
						altriEventi.remove( tag );
					Set<String> eventiFamiglia = EventFact.FAMILY_EVENT_FACT_TAGS;
					for( String tag : EventFact.PERSONAL_EVENT_FACT_TAGS )
						eventiFamiglia.remove( tag );
					for( String tag : eventiFamiglia ) {
						altriEventi.remove( tag );
					}
					Iterator<Map.Entry<String,String>> eventi = altriEventi.entrySet().iterator();
					while( eventi.hasNext() ) {	// Rimuove i tag lunghi e _speciali
						Map.Entry<String,String> ev = eventi.next();
						if( ev.getKey().length() > 4 || ev.getKey().startsWith( "_" ) )
							eventi.remove();
					}
					i = 0;
					for( Map.Entry<String,String> event : altriEventi.entrySet() ) {
						subAltri.add( 0, 50+i, 0, event.getValue() + " - " + event.getKey() );
						i++;
					}
					SubMenu subNota = menu.addSubMenu( 0, 0, 0, R.string.note );
					subNota.add( 0, 22, 0, R.string.new_note );
					subNota.add( 0, 23, 0, R.string.new_shared_note );
					subNota.add( 0, 24, 0, R.string.link_shared_note );
					if( Globale.preferenze.esperto ) {
						SubMenu subFonte = menu.addSubMenu( 0, 0, 0, R.string.source );
						subFonte.add( 0, 25, 0, R.string.new_source_note );
						subFonte.add( 0, 26, 0, R.string.new_source );
						subFonte.add( 0, 27, 0, R.string.link_source );
					}
					break;
				case 2: // Individuo Familiari
					menu.add( 0, 30, 0, R.string.new_relative );
					if( U.ciSonoIndividuiCollegabili( uno ) )
						menu.add( 0, 31, 0, R.string.link_person );
			}
			popup.show();
			popup.setOnMenuItemClickListener( item -> {
				CharSequence[] familiari = { getText(R.string.parent), getText(R.string.sibling), getText(R.string.spouse), getText(R.string.child) };
													// DUBBIO : "Padre" , "Madre" ?	"Marito"  "Moglie" ?
				AlertDialog.Builder builder = new AlertDialog.Builder( Individuo.this );
				switch( item.getItemId() ) {
					// Scheda Eventi
					case 0:
						break;
					// Media
					case 10: // Cerca media locale
						U.appAcquisizioneImmagine( Individuo.this, null, 2173, uno );
						break;
					case 11: // Cerca oggetto media
						U.appAcquisizioneImmagine( Individuo.this, null, 2174, uno );
						break;
					case 12:	// Collega media in Galleria
						Intent inten = new Intent( Individuo.this, Principe.class );
						inten.putExtra( "galleriaScegliMedia", true );
						startActivityForResult( inten,43614 );
						break;
					case 20: // Crea nome
						Name nome = new Name();
						nome.setValue( "//" );
						uno.addName( nome );
						Memoria.aggiungi( nome );
						startActivity( new Intent( Individuo.this, Nome.class ) );
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
									IndividuoEventi tabEventi = (IndividuoEventi) getSupportFragmentManager().findFragmentByTag( "android:switcher:" + R.id.schede_persona + ":1" );
									tabEventi.aggiorna( 1 );
									U.salvaJson( true, uno );
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
					case 24:	// Collega nota condivisa
						Intent inte = new Intent( Individuo.this, Principe.class );
						inte.putExtra( "quadernoScegliNota", true );
						startActivityForResult( inte,4074 );
						break;
					case 25:	// Nuova fonte-nota
						SourceCitation citaz = new SourceCitation();
						citaz.setValue( "" );
						uno.addSourceCitation( citaz );
						Memoria.aggiungi( citaz );
						startActivity( new Intent( Individuo.this, CitazioneFonte.class ) );
						break;
					case 26:	// Nuova fonte
						 Biblioteca.nuovaFonte( Individuo.this, uno );
						break;
					case 27:	// Collega fonte
						Intent intento = new Intent( Individuo.this, Principe.class );
						intento.putExtra( "bibliotecaScegliFonte", true );
						startActivityForResult( intento,50473 );
						break;
					// Scheda Familiari
					case 30:
						builder.setItems( familiari, (dialog, quale) -> {
							Intent intento1 = new Intent( getApplicationContext(), EditaIndividuo.class );
							intento1.putExtra( "idIndividuo", uno.getId() );
							intento1.putExtra( "relazione", quale + 1 );
							if( EditaIndividuo.controllaMultiMatrimoni( intento1,Individuo.this,null) )
								return;
							startActivity( intento1 );
						});
						builder.show();
						break;
					case 31:
						builder.setItems( familiari, (dialog, quale) -> {
							Intent intento12 = new Intent( getApplication(), Principe.class );
							intento12.putExtra( "idIndividuo", uno.getId() ); // solo per controllaMultiMatrimoni()
							intento12.putExtra( "anagrafeScegliParente", true );
							intento12.putExtra( "relazione", quale + 1 );
							if( EditaIndividuo.controllaMultiMatrimoni( intento12,Individuo.this,null) )
								return;
							startActivityForResult( intento12,1401 );
						} );
						builder.show();
						break;
					default:
						String tagChiave = null;
						if( item.getItemId() >= 50 ) {
							tagChiave = altriEventi.keySet().toArray( new String[altriEventi.size()] )[item.getItemId() - 50];
						} else if( item.getItemId() >= 40  )
							tagChiave = pochiEventiTag[item.getItemId()-40];
						if( tagChiave != null ) {
							EventFact nuovoEvento = new EventFact();
							nuovoEvento.setTag( tagChiave );
							switch( tagChiave ) {
								case "OCCU":
									nuovoEvento.setValue("");
									break;
								case "RESI":
									nuovoEvento.setPlace("");
									break;
								case "BIRT":
								case "DEAT":
								case "CHR":
									nuovoEvento.setValue( "Y" );
								case "BAPM":
								case "BURI":
									nuovoEvento.setPlace("");
									nuovoEvento.setDate("");
							}
							uno.addEventFact( nuovoEvento );
							Memoria.aggiungi( nuovoEvento );
							startActivity( new Intent( Individuo.this, Evento.class ) );
							return true;
						}
						return false;
				}
				return true;
			});
		});
	}

	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data ) {
		super.onActivityResult( requestCode, resultCode, data );
		if( resultCode == RESULT_OK ) {
			if( requestCode == 2173 ) { // File fornito da un'app diventa media locale eventualmente ritagliato con Android Image Cropper
				Media media = new Media();
				media.setFileTag("FILE");
				uno.addMedia( media );
				if( U.ritagliaImmagine( this, null, data, media ) ) { // restituisce true se è un'immagine ritagliabile
					U.salvaJson( false, uno );
						// false così non scatta recreate() che negli Android nuovi fa scomparire il dialogo di richiesta ritaglio
					return;
				}
			} else if( requestCode == 2174 ) { // File dalle app in nuovo Media condiviso, con proposta di ritagliarlo
				Media media = Galleria.nuovoMedia( uno );
				if( U.ritagliaImmagine( this, null, data, media ) ) {
					U.salvaJson( false, media, uno );
					return;
				}
			} else if( requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE ) {
				// Ottiene l'immagine ritagliata da Android Image Cropper
				U.fineRitaglioImmagine( data );
				U.salvaJson(true); // la data di cambio per i Media condivisi viene già salvata nel passaggio precedente
						// todo passargli Globale.mediaCroppato ?
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
				Object[] modificati = EditaIndividuo.aggiungiParente( uno.getId(),
						data.getStringExtra("idParente"),
						data.getIntExtra("relazione",0),
						data.getIntExtra("famigliaNum",0) );
				U.salvaJson( true, modificati );
				return;
			}
			U.salvaJson( true, uno );
		} else if( requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE ) // se clic su freccia indietro in Crop Image
			Globale.editato = true;
		else
			Toast.makeText( this, R.string.something_wrong, Toast.LENGTH_LONG ).show();
	}

	@Override
	public void onBackPressed() {
		Memoria.arretra();
		super.onBackPressed();
	}

	// Menu Opzioni
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add( 0, 0, 0, R.string.diagram );
		if( !uno.getParentFamilies( gc ).isEmpty() )
			menu.add( 0, 1, 0, uno.getSpouseFamilies(gc).isEmpty() ? R.string.family : R.string.family_as_child );
		if( !uno.getSpouseFamilies( gc ).isEmpty() )
			menu.add( 0, 2, 0, uno.getParentFamilies(gc).isEmpty() ? R.string.family : R.string.family_as_spouse );
		if( Globale.preferenze.alberoAperto().radice == null || !Globale.preferenze.alberoAperto().radice.equals(uno.getId()) )
			menu.add( 0, 3, 0, R.string.make_root );
		menu.add( 0, 4, 0, R.string.modify );
		menu.add( 0, 5, 0, R.string.delete );
		return true;
	}
	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
		switch ( item.getItemId() ) {
			case 0:	// Diagramma
				Globale.individuo = uno.getId();
				startActivity( new Intent( getApplicationContext(), Principe.class ) );
				return true;
			case 1:	// Famiglia come figlio
				U.qualiGenitoriMostrare( this, uno, Famiglia.class );
				return true;
			case 2:	// Famiglia come coniuge
				U.qualiConiugiMostrare( this, uno );
				return true;
			case 3: // Imposta come radice
				Globale.preferenze.alberoAperto().radice = uno.getId();
				Globale.preferenze.salva();
				Snackbar.make( tabLayout, getString( R.string.this_is_root, U.epiteto(uno) ), Snackbar.LENGTH_LONG ).show();
				return true;
			case 4: // Modifica
				Intent intent = new Intent( this, EditaIndividuo.class );
				intent.putExtra( "idIndividuo", uno.getId() );
				startActivity( intent );
				return true;
			case 5:	// Elimina
				Anagrafe.elimina( uno.getId(), this, null );
				return true;
			default:
				onBackPressed();
		}
		return false;
	}

	@Override
	public void onRequestPermissionsResult( int codice, String[] permessi, int[] accordi ) {
		U.risultatoPermessi( this, codice, permessi, accordi, uno );
	}
}