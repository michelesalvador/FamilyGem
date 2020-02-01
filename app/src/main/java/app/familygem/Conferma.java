// Attività finale all'importazione delle novità in un albero già esistente

package app.familygem;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.ChildRef;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.ParentFamilyRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SpouseFamilyRef;
import org.folg.gedcom.model.SpouseRef;
import org.folg.gedcom.model.Submitter;
import org.folg.gedcom.model.Visitable;
import java.io.File;
import java.io.IOException;
import app.familygem.visita.ContenitoriMedia;
import app.familygem.visita.ContenitoriNota;
import app.familygem.visita.ListaCitazioniFonte;
import app.familygem.visita.ListaMedia;

public class Conferma extends AppCompatActivity {

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		setContentView( R.layout.conferma );
		if( !Confronto.getLista().isEmpty() ) {

			// Albero vecchio
			CardView carta = findViewById( R.id.conferma_vecchio );
			Armadio.Cassetto cassetto = Globale.preferenze.getAlbero( Globale.preferenze.idAprendo );
			((TextView)carta.findViewById(R.id.confronto_titolo )).setText( cassetto.nome );
			String txt = Alberi.scriviDati( this, cassetto );
			((TextView)carta.findViewById(R.id.confronto_testo )).setText( txt );
			carta.findViewById( R.id.confronto_data ).setVisibility( View.GONE );

			int aggiungi = 0;
			int sostitui = 0;
			int elimina = 0;
			for( Confronto.Fronte fronte : Confronto.getLista() ) {
				switch( fronte.destino ) {
					case 1: aggiungi++;
						break;
					case 2: sostitui++;
						break;
					case 3: elimina++;
				}
			}
			String testo = getString( R.string.accepted_news, aggiungi+sostitui+elimina, aggiungi, sostitui, elimina );
			((TextView)findViewById(R.id.conferma_testo )).setText( testo );

			findViewById(R.id.conferma_annulla ).setOnClickListener( v -> {
				Confronto.getLista().clear();
				Confronto.getInstance().posizione = 0;
				startActivity( new Intent( Conferma.this, Alberi.class ) );
			});

			findViewById(R.id.conferma_ok ).setOnClickListener( v -> {
				// Modifica l'id e tutti i ref agli oggetti con doppiaOpzione e destino da aggiungere
				boolean fattoQualcosa = false;
				for( Confronto.Fronte fronte : Confronto.getLista() ) {
					if( fronte.doppiaOpzione && fronte.destino == 1 ) {
						String idNuovo;
						fattoQualcosa = true;
						switch( fronte.tipo ) {
							case 1: // Note
								idNuovo = idMassimo( Note.class );
								Note n2 = (Note) fronte.oggetto2;
								new ContenitoriNota( Globale.gc2, n2, idNuovo ); // aggiorna tutti i ref alla nota
								n2.setId( idNuovo ); // poi aggiorna l'id della nota
								break;
							case 2: // Submitter
								idNuovo = idMassimo( Submitter.class );
								((Submitter)fronte.oggetto2).setId( idNuovo );
								break;
							case 3: // Repository
								idNuovo = idMassimo( Repository.class );
								Repository repo2 = (Repository)fronte.oggetto2;
								for( Source fon : Globale.gc2.getSources() )
									if( fon.getRepositoryRef() != null && fon.getRepositoryRef().getRef().equals(repo2.getId()) )
										fon.getRepositoryRef().setRef( idNuovo );
								repo2.setId( idNuovo );
								break;
							case 4: // Media
								idNuovo = idMassimo( Media.class );
								Media m2 = (Media) fronte.oggetto2;
								new ContenitoriMedia( Globale.gc2, m2, idNuovo );
								m2.setId( idNuovo );
								break;
							case 5: // Source
								idNuovo = idMassimo( Source.class );
								Source s2 = (Source) fronte.oggetto2;
								ListaCitazioniFonte citaFonte = new ListaCitazioniFonte( Globale.gc2, s2.getId() );
								for( ListaCitazioniFonte.Tripletta tri : citaFonte.lista )
									tri.citazione.setRef( idNuovo );
								s2.setId( idNuovo );
								break;
							case 6: // Person
								idNuovo = idMassimo( Person.class );
								Person p2 = (Person) fronte.oggetto2;
								for( Family fam : Globale.gc2.getFamilies() ) {
									for( SpouseRef sr : fam.getHusbandRefs() )
										if( sr.getRef().equals(p2.getId()) )
											sr.setRef( idNuovo );
									for( SpouseRef sr : fam.getWifeRefs() )
										if( sr.getRef().equals(p2.getId()) )
											sr.setRef( idNuovo );
									for( ChildRef cr : fam.getChildRefs() )
										if( cr.getRef().equals(p2.getId()) )
											cr.setRef( idNuovo );
								}
								p2.setId( idNuovo );
								break;
							case 7: // Family
								idNuovo = idMassimo( Family.class );
								Family f2 = (Family) fronte.oggetto2;
								for( Person per : Globale.gc2.getPeople() ) {
									for( ParentFamilyRef pfr : per.getParentFamilyRefs() )
										if( pfr.getRef().equals(f2.getId()) )
											pfr.setRef( idNuovo );
									for( SpouseFamilyRef sfr : per.getSpouseFamilyRefs() )
										if( sfr.getRef().equals(f2.getId()) )
											sfr.setRef( idNuovo );
								}
								f2.setId( idNuovo );
						}
					}
				}
				if( fattoQualcosa )
					U.salvaJson( Globale.gc2, Globale.idAlbero2 );

				// La regolare aggiunta/sostituzione/eliminazione dei record da albero2 ad albero
				for( Confronto.Fronte fronte : Confronto.getLista() ) {
					switch( fronte.tipo ) {
						case 1: // Nota
							if( fronte.destino > 1 )
								Globale.gc.getNotes().remove( fronte.oggetto );
							if( fronte.destino > 0 && fronte.destino < 3 ) {
								Globale.gc.addNote( (Note) fronte.oggetto2 );
								copiaTuttiFile( fronte.oggetto2 );
							}
							break;
						case 2: // Submitter
							if( fronte.destino > 1 )
								Globale.gc.getSubmitters().remove( fronte.oggetto );
							if( fronte.destino > 0 && fronte.destino < 3 )
								Globale.gc.addSubmitter( (Submitter) fronte.oggetto2 );
							break;
						case 3: // Repository
							if( fronte.destino > 1 )
								Globale.gc.getRepositories().remove( fronte.oggetto );
							if( fronte.destino > 0 && fronte.destino < 3 ) {
								Globale.gc.addRepository( (Repository) fronte.oggetto2 );
								copiaTuttiFile( fronte.oggetto2 );
							}
							break;
						case 4: // Media
							if( fronte.destino > 1 )
								Globale.gc.getMedia().remove( fronte.oggetto );
							if( fronte.destino > 0 && fronte.destino < 3 ) {
								Globale.gc.addMedia( (Media) fronte.oggetto2 );
								vediSeCopiareFile( (Media)fronte.oggetto2 );
							}
							break;
						case 5: // Source
							if( fronte.destino > 1 )
								Globale.gc.getSources().remove( fronte.oggetto );
							if( fronte.destino > 0 && fronte.destino < 3 ) {
								Globale.gc.addSource( (Source) fronte.oggetto2 );
								copiaTuttiFile( fronte.oggetto2 );
							}
							break;
						case 6: // Person
							if( fronte.destino > 1 )
								Globale.gc.getPeople().remove( fronte.oggetto );
							if( fronte.destino > 0 && fronte.destino < 3 ) {
								Globale.gc.addPerson( (Person) fronte.oggetto2 );
								copiaTuttiFile( fronte.oggetto2 );
							}
							break;
						case 7: // Family
							if( fronte.destino > 1 )
								Globale.gc.getFamilies().remove( fronte.oggetto );
							if( fronte.destino > 0 && fronte.destino < 3 ) {
								Globale.gc.addFamily( (Family) fronte.oggetto2 );
								copiaTuttiFile( fronte.oggetto2 );
							}
					}
				}
				U.salvaJson( Globale.gc, Globale.preferenze.idAprendo );

				// Se ha fatto tutto propone di eliminare l'albero importato
				boolean tuttiOk = true;
				for( Confronto.Fronte fron : Confronto.getLista() )
					if( fron.destino == 0 ) {
						tuttiOk = false;
						break;
					}
				if( tuttiOk ) {
					Globale.preferenze.getAlbero( Globale.idAlbero2 ).grado = 30;
					Globale.preferenze.salva();
					new AlertDialog.Builder( Conferma.this )
							.setMessage( R.string.all_imported_delete )
							.setPositiveButton( android.R.string.ok, (d, i) -> {
								Alberi.eliminaAlbero( this, Globale.idAlbero2 );
								concludi();
							}).setNegativeButton( R.string.no, (d, i) -> concludi() )
							.setOnCancelListener( dialog -> concludi() ).show();
				} else
					concludi();
				Confronto.getLista().clear();
				Confronto.getInstance().posizione = 0;
			});
		} else onBackPressed();
	}

	// Apre l'elenco degli alberi
	void concludi() {
		Globale.editato = true;
		startActivity( new Intent( this, Alberi.class ) );
	}

	// Calcola l'id più alto per una certa classe confrontando albero nuovo e vecchio
	String idMassimo( Class classe ) {
		String id = U.nuovoId( Globale.gc, classe ); // id nuovo rispetto ai record dell'albero vecchio
		String id2 = U.nuovoId( Globale.gc2, classe ); // e dell'albero nuovo
		if( Integer.valueOf( id.substring(1) ) > Integer.valueOf( id2.substring(1) ) ) // toglie la lettera iniziale
			return id;
		else
			return id2;
	}

	// Se un oggetto nuovo ha dei media, valuta se copiare i file nella cartella immagini dell'albero vecchio
	// comunque  aggiorna il collegamento nel Media
	void copiaTuttiFile( Object oggetto ) {
		ListaMedia cercaMedia = new ListaMedia( Globale.gc2, 2 );
		((Visitable)oggetto).accept( cercaMedia );
		for( Media media : cercaMedia.lista ) {
			vediSeCopiareFile( media );
		}
	}
	void vediSeCopiareFile( Media media ) {
		String origine = U.percorsoMedia( Globale.idAlbero2, media );
		if( origine != null ) {
			File fileOrigine = new File( origine );
			File dirMemoria = new File( getExternalFilesDir(null) +"/"+ Globale.preferenze.idAprendo ); // dovrebbe stare fuori dal loop ma vabè
			String nomeFile = origine.substring( origine.lastIndexOf('/') + 1 );
			File fileGemello = new File( dirMemoria.getAbsolutePath(), nomeFile );
			if( fileGemello.isFile()	// se il file corrispondente esiste già
					&& fileGemello.lastModified() == fileOrigine.lastModified() // e hanno la stessa data
					&& fileGemello.length() == fileOrigine.length() ) { // e la stessa dimensione
				// Allora utilizza il file già esistente
				media.setFile( fileGemello.getAbsolutePath() );
			} else { // Altrimenti copia il file nuovo
				File fileDestinazione = U.fileNomeProgressivo( dirMemoria.getAbsolutePath(), nomeFile );
				try {
					FileUtils.copyFile( fileOrigine, fileDestinazione );
				} catch( IOException e ) {
					e.printStackTrace();
				}
				media.setFile( fileDestinazione.getAbsolutePath() );
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem i ) {
		onBackPressed();
		return true;
	}
}