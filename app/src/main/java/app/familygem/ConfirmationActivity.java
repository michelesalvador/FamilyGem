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

/**
 * Final activity when importing news in an existing tree
 * */
public class ConfirmationActivity extends BaseActivity {

	@Override
	protected void onCreate( Bundle bundle ) {
		super.onCreate( bundle );
		setContentView( R.layout.conferma );
		if( !Comparison.getList().isEmpty() ) {

			// Old tree
			CardView card = findViewById( R.id.conferma_vecchio );
			Settings.Tree tree = Global.settings.getTree( Global.settings.openTree);
			((TextView)card.findViewById(R.id.confronto_titolo )).setText( tree.title);
			String txt = TreesActivity.writeData( this, tree);
			((TextView)card.findViewById(R.id.confronto_testo )).setText( txt );
			card.findViewById( R.id.confronto_data ).setVisibility( View.GONE );

			int add = 0;
			int replace = 0;
			int delete = 0;
			for( Comparison.Front front : Comparison.getList() ) {
				switch( front.destiny) {
					case 1: add++;
						break;
					case 2: replace++;
						break;
					case 3: delete++;
				}
			}
			String text = getString( R.string.accepted_news, add+replace+delete, add, replace, delete );
			((TextView)findViewById(R.id.conferma_testo )).setText( text );

			findViewById(R.id.conferma_annulla ).setOnClickListener( v -> {
				Comparison.reset();
				startActivity( new Intent( ConfirmationActivity.this, TreesActivity.class ) );
			});

			findViewById(R.id.conferma_ok ).setOnClickListener( v -> {
				//Change the id and all refs to objects with canBothAddAndReplace and destiny to add // Modifica l'id e tutti i ref agli oggetti con doppiaOpzione e destino da aggiungere
				boolean changed = false;
				for( Comparison.Front front : Comparison.getList() ) {
					if( front.canBothAddAndReplace && front.destiny == 1 ) {
						String newID;
						changed = true;
						switch( front.type) {
							case 1: // Note
								newID = maxID( Note.class );
								Note n2 = (Note) front.object2;
								new NoteContainers( Global.gc2, n2, newID ); // updates all refs to the note
								n2.setId( newID ); // then update the note id
								break;
							case 2: // Submitter
								newID = maxID( Submitter.class );
								((Submitter)front.object2).setId( newID );
								break;
							case 3: // Repository
								newID = maxID( Repository.class );
								Repository repo2 = (Repository)front.object2;
								for( Source fon : Global.gc2.getSources() )
									if( fon.getRepositoryRef() != null && fon.getRepositoryRef().getRef().equals(repo2.getId()) )
										fon.getRepositoryRef().setRef( newID );
								repo2.setId( newID );
								break;
							case 4: // Media
								newID = maxID( Media.class );
								Media m2 = (Media) front.object2;
								new MediaContainers( Global.gc2, m2, newID );
								m2.setId( newID );
								break;
							case 5: // Source
								newID = maxID( Source.class );
								Source s2 = (Source) front.object2;
								ListOfSourceCitations sourceCitations = new ListOfSourceCitations( Global.gc2, s2.getId() );
								for( ListOfSourceCitations.Triplet tri : sourceCitations.list)
									tri.citation.setRef( newID );
								s2.setId( newID );
								break;
							case 6: // Person
								newID = maxID( Person.class );
								Person p2 = (Person) front.object2;
								for( Family fam : Global.gc2.getFamilies() ) {
									for( SpouseRef sr : fam.getHusbandRefs() )
										if( sr.getRef().equals(p2.getId()) )
											sr.setRef( newID );
									for( SpouseRef sr : fam.getWifeRefs() )
										if( sr.getRef().equals(p2.getId()) )
											sr.setRef( newID );
									for( ChildRef cr : fam.getChildRefs() )
										if( cr.getRef().equals(p2.getId()) )
											cr.setRef( newID );
								}
								p2.setId( newID );
								break;
							case 7: // Family
								newID = maxID( Family.class );
								Family f2 = (Family) front.object2;
								for( Person per : Global.gc2.getPeople() ) {
									for( ParentFamilyRef pfr : per.getParentFamilyRefs() )
										if( pfr.getRef().equals(f2.getId()) )
											pfr.setRef( newID );
									for( SpouseFamilyRef sfr : per.getSpouseFamilyRefs() )
										if( sfr.getRef().equals(f2.getId()) )
											sfr.setRef( newID );
								}
								f2.setId( newID );
						}
					}
				}
				if( changed )
					U.saveJson( Global.gc2, Global.treeId2);

				// Regular addition / replacement / deletion of records from tree2 to tree
				for( Comparison.Front front : Comparison.getList() ) {
					switch( front.type) {
						case 1: // Note
							if( front.destiny > 1 )
								Global.gc.getNotes().remove( front.object );
							if( front.destiny > 0 && front.destiny < 3 ) {
								Global.gc.addNote( (Note) front.object2 );
								copyAllFiles( front.object2 );
							}
							break;
						case 2: // Submitter
							if( front.destiny > 1 )
								Global.gc.getSubmitters().remove( front.object );
							if( front.destiny > 0 && front.destiny < 3 )
								Global.gc.addSubmitter( (Submitter) front.object2 );
							break;
						case 3: // Repository
							if( front.destiny > 1 )
								Global.gc.getRepositories().remove( front.object );
							if( front.destiny > 0 && front.destiny < 3 ) {
								Global.gc.addRepository( (Repository) front.object2 );
								copyAllFiles( front.object2 );
							}
							break;
						case 4: // Media
							if( front.destiny > 1 )
								Global.gc.getMedia().remove( front.object );
							if( front.destiny > 0 && front.destiny < 3 ) {
								Global.gc.addMedia( (Media) front.object2 );
								checkIfShouldCopyFiles( (Media)front.object2 );
							}
							break;
						case 5: // Source
							if( front.destiny > 1 )
								Global.gc.getSources().remove( front.object );
							if( front.destiny > 0 && front.destiny < 3 ) {
								Global.gc.addSource( (Source) front.object2 );
								copyAllFiles( front.object2 );
							}
							break;
						case 6: // Person
							if( front.destiny > 1 )
								Global.gc.getPeople().remove( front.object );
							if( front.destiny > 0 && front.destiny < 3 ) {
								Global.gc.addPerson( (Person) front.object2 );
								copyAllFiles( front.object2 );
							}
							break;
						case 7: // Family
							if( front.destiny > 1 )
								Global.gc.getFamilies().remove( front.object );
							if( front.destiny > 0 && front.destiny < 3 ) {
								Global.gc.addFamily( (Family) front.object2 );
								copyAllFiles( front.object2 );
							}
					}
				}
				U.saveJson( Global.gc, Global.settings.openTree);

				// If he has done everything he proposes to delete the imported tree (?)//Se ha fatto tutto propone di eliminare l'albero importato
				boolean allOK = true;
				for( Comparison.Front front : Comparison.getList() )
					if( front.destiny == 0 ) {
						allOK = false;
						break;
					}
				if( allOK ) {
					Global.settings.getTree( Global.treeId2).grade = 30;
					Global.settings.save();
					new AlertDialog.Builder( ConfirmationActivity.this )
							.setMessage( R.string.all_imported_delete )
							.setPositiveButton( android.R.string.ok, (d, i) -> {
								TreesActivity.deleteTree( this, Global.treeId2);
								done();
							}).setNegativeButton( R.string.no, (d, i) -> done() )
							.setOnCancelListener( dialog -> done() ).show();
				} else
					done();
			});
		} else onBackPressed();
	}

	/**
	 * Opens the tree list
	 * */
	void done() {
		Comparison.reset();
		startActivity( new Intent( this, TreesActivity.class ) );
	}

	/**
	 * Calculate the highest id for a certain class by comparing new and old tree
	 * Calcola l'id più alto per una certa classe confrontando albero nuovo e vecchio
	 * */
	String maxID(Class classe ) {
		String id = U.newID( Global.gc, classe ); // new id against old tree records
		String id2 = U.newID( Global.gc2, classe ); // and of the new tree
		if( Integer.parseInt( id.substring(1) ) > Integer.parseInt( id2.substring(1) ) ) // removes the initial letter
			return id;
		else
			return id2;
	}

	/**
	 * If a new object has media, consider copying the files to the old tree image folder
	 * still update the link in the Media
	 *
	 * Se un object nuovo ha dei media, valuta se copiare i file nella cartella immagini dell'albero vecchio
	 * comunque
	 * aggiorna il collegamento nel Media
	 * */
	void copyAllFiles(Object object ) {
		MediaList searchMedia = new MediaList( Global.gc2, 2 );
		((Visitable)object).accept( searchMedia );
		for( Media media : searchMedia.list) {
			checkIfShouldCopyFiles( media );
		}
	}
	void checkIfShouldCopyFiles(Media media ) {
		String path = F.mediaPath( Global.treeId2, media );
		if( path != null ) {
			File filePath = new File( path );
			File memoryDir = getExternalFilesDir( String.valueOf(Global.settings.openTree) ); // it should stay out of the loop but oh well //dovrebbe stare fuori dal loop ma vabè
			String nameFile = path.substring( path.lastIndexOf('/') + 1 );
			File twinFile = new File( memoryDir.getAbsolutePath(), nameFile );
			if( twinFile.isFile()	// if the corresponding file already exists
					&& twinFile.lastModified() == filePath.lastModified() // and have the same date
					&& twinFile.length() == filePath.length() ) { // and the same size
				// Then use the already existing file
				media.setFile( twinFile.getAbsolutePath() );
			} else { // Otherwise copy the new file
				File destinationFile = F.nextAvailableFileName( memoryDir.getAbsolutePath(), nameFile );
				try {
					FileUtils.copyFile( filePath, destinationFile );
				} catch( IOException e ) {
					e.printStackTrace();
				}
				media.setFile( destinationFile.getAbsolutePath() );
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem i ) {
		onBackPressed();
		return true;
	}
}