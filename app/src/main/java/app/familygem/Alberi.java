package app.familygem;

import android.content.DialogInterface;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.folg.gedcom.model.ChildRef;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.ParentFamilyRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SpouseFamilyRef;
import org.folg.gedcom.model.SpouseRef;
import org.folg.gedcom.parser.JsonParser;
import org.folg.gedcom.visitors.GedcomWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Alberi extends AppCompatActivity {

    ListView lista;
	List< Map<String,String> > alberelli;

	@Override
	protected void onCreate( Bundle stato ) {
	    super.onCreate( stato );
        setContentView( R.layout.alberi );
        lista = findViewById( R.id.lista_alberi );

        if( Globale.preferenze.alberi != null ) {

            // Lista degli alberi genealogici
            alberelli = new ArrayList<>();
			aggiornaLista();

            // Dà i dati in pasto all'adattatore
			SimpleAdapter adapter = new SimpleAdapter( this, alberelli,
                    android.R.layout.simple_list_item_2,
                    new String[]{ "titolo", "dati" },
                    new int[]{ android.R.id.text1, android.R.id.text2 });
            lista.setAdapter(adapter);

            // Click su un albero della lista
            lista.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick( AdapterView<?> parent, View view, int position, long id ) {
					int id_num = Integer.parseInt( (String)((HashMap)lista.getItemAtPosition(position)).get("id") );
					if( !apriJson( id_num ) )
						return;
	                startActivity( new Intent( Alberi.this, Principe.class ) );
                }
            });

            registerForContextMenu(lista);
        }

        // Fab
        findViewById(R.id.fab).setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick( View v ) {
                startActivity( new Intent( Alberi.this, AlberoNuovo.class) );
            }
        });
    }

	void aggiornaLista() {
		alberelli.clear();
		for( Armadio.Cassetto alb : Globale.preferenze.alberi) {
			Map<String,String> dato = new HashMap<>(3);
			dato.put( "id", String.valueOf( alb.id ) );
			dato.put( "titolo", alb.nome );
			dato.put( "dati", alb.individui + " "+ getText(R.string.persons) + " - "+ alb.generazioni + " " + getText(R.string.generations) );
			alberelli.add( dato );
		}
		lista.invalidateViews();
	}

	// Apertura di un Json per editare tutto in Gedcomy
    static boolean apriJson( int id ) {
		try {
			File file = new File( Globale.contesto.getFilesDir(), id + ".json");
			String contenuto = FileUtils.readFileToString( file );
			JsonParser jp = new JsonParser();
			Globale.gc = jp.fromJson( contenuto );
			Globale.preferenze.idAprendo = id;
			Globale.preferenze.salva();
			Globale.individuo = Globale.preferenze.alberoAperto().radice;
			Principe.arredaTestataMenu();
		} catch( Exception e ) {
			Toast.makeText( Globale.contesto, e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
			e.printStackTrace();
			return false;
		}
		return true;
	}

	// Restituisce un Gedcom giusto per ricavarne informazioni qui in Alberi
	Gedcom leggiJson( int idAlbero ) {
		Gedcom gc;
		try {
			String contenuto = FileUtils.readFileToString( new File( getBaseContext().getFilesDir(), idAlbero + ".json" ) );
			gc = new JsonParser().fromJson( contenuto );
		} catch( IOException e ) {
			Toast.makeText( getBaseContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
			return null;
		}
		if( gc == null ) {
			Toast.makeText( getBaseContext(), R.string.no_useful_data, Toast.LENGTH_LONG ).show();
			return null;
		}
		return gc;
	}

	@Override
	public boolean onCreateOptionsMenu( Menu menu ) {
		menu.add(0,0,0, R.string.make_backup );
		return true;
	}
	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
		switch( item.getItemId() ) {
			case 0:
				//getContext().getExternalFilesDir(null) + "/backup.zip";	// pubblica ma non permanente, la crea se non esiste
				//Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/Family Gem backup.zip";
					// /storage/emulated/0/Documents/Family Gem backup.zip  richiede API 19,  non la crea se inesistente
				File cartellaDocumenti = new File( Environment.getExternalStorageDirectory() + "/Documents" );
					// /storage/emulated/0/Documents
				if( !cartellaDocumenti.exists() )
					cartellaDocumenti.mkdir();	// crea solo la cartella Documenti, non tutto il percorso
				File fileZip = new File( cartellaDocumenti.getAbsolutePath(),"Family Gem backup.zip" );
				//s.l( fileZip.getAbsolutePath() );
				List<File> files = (List<File>) FileUtils.listFiles( getFilesDir(), TrueFileFilter.INSTANCE, null );
						// terzo parametro se TrueFileFilter.INSTANCE lista anche le subdirectory
				byte[] buffer = new byte[128];	// Crea un buffer for reading the files
				try {
					ZipOutputStream zos = new ZipOutputStream( new FileOutputStream(fileZip) );
					for( File currentFile :  files ) {
						//s.l( currentFile.getAbsolutePath() );
						if (!currentFile.isDirectory()) {
							FileInputStream fis = new FileInputStream( currentFile );
							zos.putNextEntry( new ZipEntry( currentFile.getName() ) );	// add ZIP entry to output stream
							int read;
							while( (read = fis.read(buffer)) != -1 ) {
								zos.write(buffer, 0, read);
							}
							// complete the entry
							zos.closeEntry();
							fis.close();
						}
					}
					zos.close();
					MediaScannerConnection.scanFile(this, new String[]{fileZip.getAbsolutePath()},null,null);
						// necessario per far comparire il file in Windows
					Toast.makeText( getBaseContext(), fileZip.getAbsolutePath(), Toast.LENGTH_LONG ).show();
				} catch ( IOException e) {
					e.printStackTrace();
					Toast.makeText( getBaseContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
				}

		}
		return true;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		menu.add(0, 0, 0, R.string.tree_info );
		menu.add(0, 1, 0, R.string.rename);
		menu.add(0, 2, 0, R.string.find_errors);
		menu.add(0, 3, 0, R.string.export_gedcom);
		menu.add(0, 4, 0, R.string.delete);
	}
	@Override
	public boolean onContextItemSelected(final MenuItem item ) {
		final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		final HashMap albero = (HashMap) lista.getItemAtPosition( info.position );
		final int idAlbero = Integer.parseInt( (String)albero.get("id") );
		//s.l( info.id +"  "+ info.position +"  "+ lista.getCount()	+"\n"+ idAlbero +"  "+ albero.get("titolo") );
		int id = item.getItemId();
		if( id == 0 ) {	// Info Gedcom
			File file = new File( getFilesDir(), idAlbero + ".json" );
			if( !file.exists() ) {
				Toast.makeText( getBaseContext(), getString(R.string.cant_find_file) + "\n" + file.getAbsolutePath(), Toast.LENGTH_LONG ).show();
				return false;
			}
			Intent intent = new Intent( Alberi.this, InfoAlbero.class);
			intent.putExtra( "idAlbero", idAlbero );
			startActivity(intent);
		} else if( id == 1 ) {	// Rinomina albero
			View vistaMessaggio = LayoutInflater.from( this ).inflate(R.layout.albero_nomina, lista, false );
			AlertDialog.Builder builder = new AlertDialog.Builder( this );
			builder.setView( vistaMessaggio ).setTitle( R.string.tree_name );
			final EditText nuovoNome = vistaMessaggio.findViewById( R.id.nuovo_nome_albero );
			nuovoNome.setText( albero.get("titolo").toString() );
			builder.setPositiveButton( R.string.rename, new DialogInterface.OnClickListener() {
				public void onClick( DialogInterface dialog, int id ) {
					Globale.preferenze.rinomina( idAlbero, nuovoNome.getText().toString() );
					aggiornaLista();
				}
			})
				.setNeutralButton( R.string.cancel, null );
			AlertDialog dialog = builder.create();
			dialog.show();
			dialog.getWindow().setSoftInputMode( WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE );
		} else if( id == 2 ) {	// Correggi errori
			trovaErrori( idAlbero, false );
		} else if( id == 3 ) { // Esporta Gedcom
			try {
				GedcomWriter scrittore = new GedcomWriter();
				Gedcom gc = leggiJson( idAlbero );
				String nomeFile = Globale.preferenze.getAlbero(idAlbero).nome + ".ged";
				gc.getHeader().setFile( nomeFile );
				File fileGc = new File( Environment.getExternalStorageDirectory() + "/Documents", nomeFile );
				scrittore.write( gc, fileGc );
				MediaScannerConnection.scanFile(this, new String[]{fileGc.getAbsolutePath()},null,null);
					// necessario per rendere il file immediatamente visibile da Windows
				Toast.makeText( getBaseContext(), fileGc.getAbsolutePath(), Toast.LENGTH_LONG ).show();
			} catch( IOException e ) {
				Toast.makeText( getBaseContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
			}
		} else if( id == 4 ) {	// Elimina albero
			AlertDialog.Builder builder = new AlertDialog.Builder( this );
			builder.setMessage( R.string.really_delete_tree );
			builder.setPositiveButton( R.string.delete, new DialogInterface.OnClickListener() {
				public void onClick( DialogInterface dialog, int id ) {
					File file = new File( getFilesDir(), idAlbero + ".json");
					file.delete();
					Globale.preferenze.elimina( idAlbero );
					aggiornaLista();
				}
			}).setNeutralButton( R.string.cancel, null )
				.create().show();
		} else {
			return false;
		}
		return true;
	}

	Gedcom trovaErrori( final int idAlbero, final boolean correggi ) {
		Gedcom gc = leggiJson( idAlbero );
		String txt = "";
		int errori = 0;
		int num;
		// Riferimenti da una persona alla famiglia dei genitori e dei figli
		for( Person p : gc.getPeople() ) {
			for( ParentFamilyRef pfr : p.getParentFamilyRefs() ) {
				Family fam = gc.getFamily( pfr.getRef() );
				//s.l( U.epiteto( p ) +"   "+ fam.getId() );
				if( fam == null ) {
					if( correggi ) {
						p.getParentFamilyRefs().remove( pfr );
						break;
					} else errori++;
				} else {
					num = 0;
					for( ChildRef cr : fam.getChildRefs() )
						if( cr.getRef().equals( p.getId() ) ) {
							num++;
							if( num > 1 && correggi ) {
								fam.getChildRefs().remove( cr );
								break;
							}
						}
					if( num != 1 ) {
						if( correggi && num == 0 ) {
							p.getParentFamilyRefs().remove( pfr );
							break;
						} else errori++;
					}
				}
			}
			for( SpouseFamilyRef sfr : p.getSpouseFamilyRefs() ) {
				Family fam = gc.getFamily( sfr.getRef() );
				if( fam == null ) {
					if( correggi ) {
						p.getSpouseFamilyRefs().remove( sfr );
						break;
					} else errori++;
				} else {
					num = 0;
					for( SpouseRef sr : fam.getHusbandRefs() )
						if( sr.getRef().equals( p.getId() ) ) {
							num++;
							if( num > 1 && correggi ) {
								fam.getHusbandRefs().remove( sr );
								break;
							}
						}
					for( SpouseRef sr : fam.getWifeRefs() )
						if( sr.getRef().equals( p.getId() ) ) {
							num++;
							if( num > 1 && correggi ) {
								fam.getWifeRefs().remove( sr );
								break;
							}
						}
					if( num != 1 ) {
						if( num == 0 && correggi ) {
							p.getSpouseFamilyRefs().remove( sfr );
							break;
						} else errori++;
					}
				}
			}
		}
		// Riferimenti dalle famiglie alle persone appartenenti
		for( Family f : gc.getFamilies() ) {
			for( SpouseRef sr : f.getHusbandRefs() ) {
				Person marito = gc.getPerson( sr.getRef() );
				if( marito == null ) {
					if( correggi ) {
						f.getHusbandRefs().remove( sr );
						break;
					} else errori++;
				} else {
					num = 0;
					for( SpouseFamilyRef sfr : marito.getSpouseFamilyRefs() )
						if( sfr.getRef().equals( f.getId() ) ) {
							num++;
							if( num > 1 && correggi ) {
								marito.getSpouseFamilyRefs().remove( sfr );
								break;
							}
						}
					if( num != 1 ) {
						if( num == 0 && correggi ) {
							f.getHusbandRefs().remove( sr );
							break;
						} else errori++;
					}

				}
			}
			for( SpouseRef sr : f.getWifeRefs() ) {
				Person moglie = gc.getPerson( sr.getRef() );
				if( moglie == null ) {
					if( correggi ) {
						f.getWifeRefs().remove( sr );
						break;
					} else errori++;
				} else {
					num = 0;
					for( SpouseFamilyRef sfr : moglie.getSpouseFamilyRefs() )
						if( sfr.getRef().equals( f.getId() ) ) {
							num++;
							if( num > 1 && correggi ) {
								moglie.getSpouseFamilyRefs().remove( sfr );
								break;
							}
						}
					if( num != 1 ) {
						if( num == 0 && correggi ) {
							f.getWifeRefs().remove( sr );
							break;
						} else errori++;
					}
				}
			}
			for( ChildRef cr : f.getChildRefs() ) {
				Person figlio = gc.getPerson( cr.getRef() );
				if( figlio == null ) {
					if( correggi ) {
						f.getChildRefs().remove( cr );
						break;
					} else errori++;
				} else {
					num = 0;
					for( ParentFamilyRef pfr : figlio.getParentFamilyRefs() )
						if( pfr.getRef().equals( f.getId() ) ) {
							num++;
							if( num > 1 && correggi ) {
								figlio.getParentFamilyRefs().remove( pfr );
								break;
							}
						}
					if( num != 1 ) {
						if( num == 0 && correggi ) {
							f.getChildRefs().remove( cr );
							break;
						} else errori++;
					}
				}
			}
		}
		if( !correggi ) {
			AlertDialog.Builder dialog = new AlertDialog.Builder( this );
			dialog.setMessage( errori==0 ? getText(R.string.all_ok) : getString(R.string.errors_found,errori) );
			if( errori > 0 ) {
				dialog.setPositiveButton( R.string.correct, new DialogInterface.OnClickListener() {
					public void onClick( DialogInterface dialogo, int i ) {
						dialogo.cancel();
						Gedcom gcCorretto = trovaErrori( idAlbero, true );
						U.salvaJson( gcCorretto, idAlbero );
						trovaErrori( idAlbero, false );	// riapre per ammirere il risultato
					}
				} );
			}
			dialog.setNeutralButton( R.string.cancel, new DialogInterface.OnClickListener() {
				public void onClick( DialogInterface dialogo, int i ) {
					dialogo.cancel();
				}
			} ).show();
		}
		return gc;
	}
}