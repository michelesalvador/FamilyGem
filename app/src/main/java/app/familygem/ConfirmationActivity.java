// Attività finale all'importazione delle novità in un albero già esistente

package app.familygem;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
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
import app.familygem.visitor.MediaContainers;
import app.familygem.visitor.NoteContainers;
import app.familygem.visitor.ListOfSourceCitations;
import app.familygem.visitor.MediaList;

public class ConfirmationActivity extends BaseActivity {

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		setContentView( R.layout.conferma );
		if( !Comparison.getLista().isEmpty() ) {

			// Albero vecchio
			CardView carta = findViewById( R.id.conferma_vecchio );
			Settings.Tree tree = Global.settings.getTree( Global.settings.openTree);
			((TextView)carta.findViewById(R.id.confronto_titolo )).setText( tree.title);
			String txt = TreesActivity.scriviDati( this, tree);
			((TextView)carta.findViewById(R.id.confronto_testo )).setText( txt );
			carta.findViewById( R.id.confronto_data ).setVisibility( View.GONE );

			int aggiungi = 0;
			int sostitui = 0;
			int elimina = 0;
			for( Comparison.Fronte fronte : Comparison.getLista() ) {
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
				Comparison.reset();
				startActivity( new Intent( ConfirmationActivity.this, TreesActivity.class ) );
			});

			findViewById(R.id.conferma_ok ).setOnClickListener( v -> {
				// Modifica l'id e tutti i ref agli oggetti con doppiaOpzione e destino da aggiungere
				boolean fattoQualcosa = false;
				for( Comparison.Fronte fronte : Comparison.getLista() ) {
					if( fronte.doppiaOpzione && fronte.destino == 1 ) {
						String idNuovo;
						fattoQualcosa = true;
						switch( fronte.tipo ) {
							case 1: // Note
								idNuovo = idMassimo( Note.class );
								Note n2 = (Note) fronte.object2;
								new NoteContainers( Global.gc2, n2, idNuovo ); // aggiorna tutti i ref alla nota
								n2.setId( idNuovo ); // poi aggiorna l'id della nota
								break;
							case 2: // Submitter
								idNuovo = idMassimo( Submitter.class );
								((Submitter)fronte.object2).setId( idNuovo );
								break;
							case 3: // Repository
								idNuovo = idMassimo( Repository.class );
								Repository repo2 = (Repository)fronte.object2;
								for( Source fon : Global.gc2.getSources() )
									if( fon.getRepositoryRef() != null && fon.getRepositoryRef().getRef().equals(repo2.getId()) )
										fon.getRepositoryRef().setRef( idNuovo );
								repo2.setId( idNuovo );
								break;
							case 4: // Media
								idNuovo = idMassimo( Media.class );
								Media m2 = (Media) fronte.object2;
								new MediaContainers( Global.gc2, m2, idNuovo );
								m2.setId( idNuovo );
								break;
							case 5: // Source
								idNuovo = idMassimo( Source.class );
								Source s2 = (Source) fronte.object2;
								ListOfSourceCitations citaFonte = new ListOfSourceCitations( Global.gc2, s2.getId() );
								for( ListOfSourceCitations.Triplet tri : citaFonte.list)
									tri.citation.setRef( idNuovo );
								s2.setId( idNuovo );
								break;
							case 6: // Person
								idNuovo = idMassimo( Person.class );
								Person p2 = (Person) fronte.object2;
								for( Family fam : Global.gc2.getFamilies() ) {
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
								Family f2 = (Family) fronte.object2;
								for( Person per : Global.gc2.getPeople() ) {
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
					U.saveJson( Global.gc2, Global.treeId2);

				// La regolare aggiunta/sostituzione/eliminazione dei record da albero2 ad albero
				for( Comparison.Fronte fronte : Comparison.getLista() ) {
					switch( fronte.tipo ) {
						case 1: // Nota
							if( fronte.destino > 1 )
								Global.gc.getNotes().remove( fronte.object );
							if( fronte.destino > 0 && fronte.destino < 3 ) {
								Global.gc.addNote( (Note) fronte.object2 );
								copiaTuttiFile( fronte.object2 );
							}
							break;
						case 2: // Submitter
							if( fronte.destino > 1 )
								Global.gc.getSubmitters().remove( fronte.object );
							if( fronte.destino > 0 && fronte.destino < 3 )
								Global.gc.addSubmitter( (Submitter) fronte.object2 );
							break;
						case 3: // Repository
							if( fronte.destino > 1 )
								Global.gc.getRepositories().remove( fronte.object );
							if( fronte.destino > 0 && fronte.destino < 3 ) {
								Global.gc.addRepository( (Repository) fronte.object2 );
								copiaTuttiFile( fronte.object2 );
							}
							break;
						case 4: // Media
							if( fronte.destino > 1 )
								Global.gc.getMedia().remove( fronte.object );
							if( fronte.destino > 0 && fronte.destino < 3 ) {
								Global.gc.addMedia( (Media) fronte.object2 );
								vediSeCopiareFile( (Media)fronte.object2 );
							}
							break;
						case 5: // Source
							if( fronte.destino > 1 )
								Global.gc.getSources().remove( fronte.object );
							if( fronte.destino > 0 && fronte.destino < 3 ) {
								Global.gc.addSource( (Source) fronte.object2 );
								copiaTuttiFile( fronte.object2 );
							}
							break;
						case 6: // Person
							if( fronte.destino > 1 )
								Global.gc.getPeople().remove( fronte.object );
							if( fronte.destino > 0 && fronte.destino < 3 ) {
								Global.gc.addPerson( (Person) fronte.object2 );
								copiaTuttiFile( fronte.object2 );
							}
							break;
						case 7: // Family
							if( fronte.destino > 1 )
								Global.gc.getFamilies().remove( fronte.object );
							if( fronte.destino > 0 && fronte.destino < 3 ) {
								Global.gc.addFamily( (Family) fronte.object2 );
								copiaTuttiFile( fronte.object2 );
							}
					}
				}
				U.saveJson( Global.gc, Global.settings.openTree);

				// Se ha fatto tutto propone di eliminare l'albero importato
				boolean tuttiOk = true;
				for( Comparison.Fronte fron : Comparison.getLista() )
					if( fron.destino == 0 ) {
						tuttiOk = false;
						break;
					}
				if( tuttiOk ) {
					Global.settings.getTree( Global.treeId2).grade = 30;
					Global.settings.save();
					new AlertDialog.Builder( ConfirmationActivity.this )
							.setMessage( R.string.all_imported_delete )
							.setPositiveButton( android.R.string.ok, (d, i) -> {
								TreesActivity.deleteTree( this, Global.treeId2);
								concludi();
							}).setNegativeButton( R.string.no, (d, i) -> concludi() )
							.setOnCancelListener( dialog -> concludi() ).show();
				} else
					concludi();
			});
		} else onBackPressed();
	}

	// Apre l'elenco degli alberi
	void concludi() {
		Comparison.reset();
		startActivity( new Intent( this, TreesActivity.class ) );
	}

	// Calcola l'id più alto per una certa classe confrontando albero nuovo e vecchio
	String idMassimo( Class classe ) {
		String id = U.nuovoId( Global.gc, classe ); // id nuovo rispetto ai record dell'albero vecchio
		String id2 = U.nuovoId( Global.gc2, classe ); // e dell'albero nuovo
		if( Integer.valueOf( id.substring(1) ) > Integer.valueOf( id2.substring(1) ) ) // toglie la lettera iniziale
			return id;
		else
			return id2;
	}

	// Se un object nuovo ha dei media, valuta se copiare i file nella cartella immagini dell'albero vecchio
	// comunque  aggiorna il collegamento nel Media
	void copiaTuttiFile( Object object ) {
		MediaList cercaMedia = new MediaList( Global.gc2, 2 );
		((Visitable)object).accept( cercaMedia );
		for( Media media : cercaMedia.list) {
			vediSeCopiareFile( media );
		}
	}
	void vediSeCopiareFile( Media media ) {
		String origine = F.mediaPath( Global.treeId2, media );
		if( origine != null ) {
			File fileOrigine = new File( origine );
			File dirMemoria = getExternalFilesDir( String.valueOf(Global.settings.openTree) ); // dovrebbe stare fuori dal loop ma vabè
			String nomeFile = origine.substring( origine.lastIndexOf('/') + 1 );
			File fileGemello = new File( dirMemoria.getAbsolutePath(), nomeFile );
			if( fileGemello.isFile()	// se il file corrispondente esiste già
					&& fileGemello.lastModified() == fileOrigine.lastModified() // e hanno la stessa data
					&& fileGemello.length() == fileOrigine.length() ) { // e la stessa dimensione
				// Allora utilizza il file già esistente
				media.setFile( fileGemello.getAbsolutePath() );
			} else { // Altrimenti copia il file nuovo
				File fileDestinazione = F.fileNomeProgressivo( dirMemoria.getAbsolutePath(), nomeFile );
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