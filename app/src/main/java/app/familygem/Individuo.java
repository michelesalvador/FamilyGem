package app.familygem;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
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
    protected void onCreate( Bundle stato ) {
		super.onCreate( stato );
		String id = getIntent().getStringExtra( "idIndividuo" );
			// todo : investigabile: risulta null eliminando un evento nella famiglia..
			// todo : intent esiste, ma come "svuotato" della stringa "idIndividuo"
		//s.l( "OnCREATE trovato id della PERSONA " +  id + "  INTENTO : " + getIntent() );
		if( id == null || gc == null ) return;
		uno = gc.getPerson( id );
		Globale.individuo = id;	// non so bene perché ma potrebbe essere utile
        setContentView(R.layout.individuo);
        if( Globale.preferenze.esperto )
			((TextView)findViewById( R.id.persona_id )).setText( uno.getId() );
		Toolbar barra = findViewById(R.id.toolbar);
		barra.setTitle( U.epiteto(uno) );
        setSupportActionBar( barra );
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Assegna alla vista pagina un adapter che gestisce le tre schede
        ViewPager vistaPagina = findViewById( R.id.schede_persona );
        vistaPagina.setAdapter( new ImpaginatoreSezioni() );

		// arricchisce il tablayout
		tabLayout = findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(vistaPagina);	// altrimenti il testo nei TabItem scompare (?!)
        tabLayout.getTabAt(0).setText(R.string.media);
        tabLayout.getTabAt(1).setText(R.string.events);
        tabLayout.getTabAt(2).setText(R.string.relatives);
		tabLayout.getTabAt( getIntent().getIntExtra( "scheda", 1 ) ).select();

		// In sostanza per animare il FAB
	    final FloatingActionButton fab = findViewById( R.id.persona_fab );
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

		U.unaFoto( uno, (ImageView)findViewById(R.id.persona_foto) );
		U.unaFoto( uno, (ImageView)findViewById(R.id.persona_sfondo) );

		fab.setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View vista ) {
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
							//s.l( "matrimoniali = " + tag +" : "+ altriEventi.get( tag ) );
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
						SubMenu subFonte = menu.addSubMenu( 0, 0, 0, R.string.source );
						subFonte.add( 0, 25, 0, R.string.new_source_note );
						subFonte.add( 0, 26, 0, R.string.new_source );
						subFonte.add( 0, 27, 0, R.string.link_source );
						break;
					case 2: // Individuo Familiari
						menu.add( 0, 30, 0, R.string.new_relative );
						if( U.ciSonoIndividuiCollegabili( uno ) )
							menu.add( 0, 31, 0, R.string.link_person );
				}
				popup.show();
				popup.setOnMenuItemClickListener( new PopupMenu.OnMenuItemClickListener() {
					@Override
					public boolean onMenuItemClick( MenuItem item ) {
						CharSequence[] familiari = { getText(R.string.parent), getText(R.string.sibling), getText(R.string.spouse), getText(R.string.child) };
															// DUBBIO : "Padre" , "Madre" ?    "Marito"  "Moglie" ?
						AlertDialog.Builder builder = new AlertDialog.Builder( Individuo.this );
						switch( item.getItemId() ) {
							// Scheda Eventi
							case 0:
								break;
							// Media
							case 10: // Cerca media locale
								U.appAcquisizioneImmagine( Individuo.this, null, 2173 );
								break;
							case 11: // Cerca oggetto media
								U.appAcquisizioneImmagine( Individuo.this, null, 2174 );
								break;
							case 12:    // Collega media in Galleria
								Intent inten = new Intent( Individuo.this, Principe.class );
								inten.putExtra( "galleriaScegliMedia", true );
								startActivityForResult( inten,43614 );
								break;
							case 20: // Crea nome
								Name nom = new Name();
								nom.setValue( "" );
								uno.addName( nom );
								Ponte.manda( nom, "oggetto" );
								Ponte.manda( uno, "contenitore" );
								startActivity( new Intent( Individuo.this, Nome.class ) );
								break;
							case 21: // Crea sesso
								AlertDialog.Builder costruttore = new AlertDialog.Builder( tabLayout.getContext() );
								String[] sessoNomi = { getString(R.string.male), getString(R.string.female), getString(R.string.unknown) };
								costruttore.setSingleChoiceItems( sessoNomi, -1, new DialogInterface.OnClickListener() {
									public void onClick( DialogInterface dialo, int i ) {
										EventFact genere = new EventFact();
										genere.setTag( "SEX" );
										String[] sessoValori = { "M", "F", "U" };
										genere.setValue( sessoValori[i] );
										uno.addEventFact( genere );
										dialo.dismiss();
										Globale.editato = true;
										recreate();
										U.salvaJson();
									}
								});
								costruttore.create().show();
								break;
							case 22: { // Crea nota
								Note not = new Note();
								not.setValue( "" );
								uno.addNote( not );
								Ponte.manda( not, "oggetto" );
								Ponte.manda( uno, "contenitore" );
								startActivity( new Intent( Individuo.this, Nota.class ) );
								// todo? Dettaglio.edita( View vistaValore );
								break;
							}
							case 23: // Crea nota condivisa
								Quaderno.nuovaNota( Individuo.this, uno );
								break;
							case 24:    // Collega nota condivisa
								Intent inte = new Intent( Individuo.this, Principe.class );
								inte.putExtra( "quadernoScegliNota", true );
								startActivityForResult( inte,4074 );
								break;
							case 25:    // Nuova fonte-nota
								SourceCitation citaz = new SourceCitation();
								citaz.setValue( "" );
								uno.addSourceCitation( citaz );
								Ponte.manda( citaz, "oggetto" );
								Ponte.manda( uno, "contenitore" );
								startActivity( new Intent( Individuo.this, CitazioneFonte.class ) );
								break;
							case 26:    // Nuova fonte
 								Biblioteca.nuovaFonte( Individuo.this, uno );
								break;
							case 27:    // Collega fonte
								Intent intento = new Intent( Individuo.this, Principe.class );
								intento.putExtra( "bibliotecaScegliFonte", true );
								startActivityForResult( intento,50473 );
								break;
							// Scheda Familiari
							case 30:
								builder.setItems( familiari, new DialogInterface.OnClickListener() {
									@Override
									public void onClick( DialogInterface dialog, int quale ) {
										Intent intento = new Intent( getApplicationContext(), EditaIndividuo.class );
										intento.putExtra( "idIndividuo", uno.getId() );
										intento.putExtra( "relazione", quale + 1 );
										if( EditaIndividuo.controllaMultiMatrimoni(intento,Individuo.this,null) )
											return;
										startActivity( intento );
									}
								});
								builder.show();
								break;
							case 31:
								builder.setItems( familiari, new DialogInterface.OnClickListener() {
									@Override
									public void onClick( DialogInterface dialog, int quale ) {
										Intent intento = new Intent( getApplication(), Principe.class );
										intento.putExtra( "idIndividuo", uno.getId() ); // solo per controllaMultiMatrimoni()
										intento.putExtra( "anagrafeScegliParente", true );
										intento.putExtra( "relazione", quale + 1 );
										if( EditaIndividuo.controllaMultiMatrimoni(intento,Individuo.this,null) )
											return;
										startActivityForResult( intento,1401 );
									}
								});
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
									Ponte.manda( nuovoEvento, "oggetto" );
									Ponte.manda( uno, "contenitore" );
									startActivity( new Intent( Individuo.this, Evento.class ) );
									return true;
								}
								return false;
						}
						return true;
					}
				} );
			}
		} );
    }
	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data ) {
		if( resultCode == RESULT_OK ) {
			if( requestCode == 2173 ) { // File fornito da un'app diventa media locale eventualmente ritagliato con Android Image Cropper
				Media media = new Media();
				media.setFileTag("FILE");
				uno.addMedia( media );
				if( U.ritagliaImmagine( this, null, data, media ) ) { // restituisce true se è un'immagine ritagliabile
					Globale.editato = false; // così non scatta recreate() che negli Android nuovi fa scomparire il dialogo di richiesta ritaglio
					U.salvaJson();
					return;
				}
			} else if( requestCode == 2174 ) { // File dalle app in nuovo Media condiviso, con proposta di ritagliarlo
				Media media = Galleria.nuovoMedia( uno );
				if( U.ritagliaImmagine( this, null, data, media ) ) {
					Globale.editato = false;
					U.salvaJson();
					return;
				}
			} else if( requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE ) {
				// Ottiene l'immagine ritagliata da Android Image Cropper
				U.fineRitaglioImmagine( data );
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
				EditaIndividuo.aggiungiParente( uno.getId(),
						data.getStringExtra("idParente"),
						data.getIntExtra("relazione",0),
						data.getIntExtra("famigliaNum",0) );
			}
			U.salvaJson();
			Globale.editato = true;  // Prima scatta onActivityResult(), poi onRestart() che rinfresca il contenuto
		} else
			Toast.makeText( this, R.string.something_wrong, Toast.LENGTH_LONG ).show();
	}

	// Chiamato dopo onBackPressed() ricarica la pagina per aggiornare i contenuti
	@Override
	public void onRestart() {
		super.onRestart();
		if( Globale.editato ) {
			recreate();
		}
	}

    class ImpaginatoreSezioni extends FragmentPagerAdapter {

        ImpaginatoreSezioni() {
            super( getSupportFragmentManager() );
        }

        @Override	// in realtà CREA le schede
        public Fragment getItem( int position ) {
            // seleziona una delle tre schede
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



	// Menu Opzioni
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add( 0, 0, 0, R.string.diagram );
		if( !uno.getParentFamilies( gc ).isEmpty() )
			menu.add( 0, 1, 0, R.string.family_as_child );
		if( !uno.getSpouseFamilies( gc ).isEmpty() )
			menu.add( 0, 2, 0, R.string.family_as_spouse );
		if( !Globale.preferenze.alberoAperto().radice.equals(uno.getId()) )
			menu.add( 0, 3, 0, "Imposta come radice" );
		menu.add( 0, 4, 0, R.string.modify );
		menu.add( 0, 5, 0, R.string.delete );
		return true;
	}
	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
		switch ( item.getItemId() ) {
			case 0:	// Diagramma
				Globale.individuo = uno.getId();	// in caso di back stack
				startActivity( new Intent( getApplicationContext(), Principe.class ) );
				return true;
			case 1:	// Famiglia come figlio
				Intent intento = new Intent( this, Famiglia.class );
				intento.putExtra( "idFamiglia", uno.getParentFamilies(gc).get(0).getId() );
				startActivity( intento );
				return true;
			case 2:	// Famiglia come coniuge
				intento = new Intent( this, Famiglia.class );
				intento.putExtra( "idFamiglia", uno.getSpouseFamilies(gc).get(0).getId() );
				startActivity( intento );
				return true;
			case 3: // Imposta come radice
				Globale.preferenze.alberoAperto().radice = uno.getId();
				Globale.preferenze.salva();
				Snackbar.make( tabLayout, U.epiteto(uno) + " è la persona principale dell'albero.", Snackbar.LENGTH_LONG ).show();
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
		U.risultatoPermessi( this, codice, permessi, accordi );
	}
}