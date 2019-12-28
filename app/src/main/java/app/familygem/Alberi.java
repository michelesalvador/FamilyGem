package app.familygem;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;
import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.ChildRef;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Header;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaRef;
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
import app.familygem.visita.ListaMedia;

public class Alberi extends AppCompatActivity {

	ListView lista;
	List<Map<String,String>> alberelli;

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		setContentView( R.layout.alberi );
		lista = findViewById( R.id.lista_alberi );

		if( Globale.preferenze.alberi != null ) {

			// Lista degli alberi genealogici
			alberelli = new ArrayList<>();
			aggiornaLista();

			// Dà i dati in pasto all'adattatore
			SimpleAdapter adapter = new SimpleAdapter( this, alberelli,
					R.layout.pezzo_albero,
					new String[] { "titolo", "dati" },
					new int[] { R.id.albero_titolo, R.id.albero_dati }) {
				// Individua ciascuna vista dell'elenco
				@Override
				public View getView( final int posiz, View convertView, ViewGroup parent ) {
					View vistaAlbero = super.getView( posiz, convertView, parent );
					//final int idAlbero = Integer.parseInt( (String)((HashMap)lista.getItemAtPosition(posiz)).get("id") );
					final int idAlbero = Integer.parseInt( alberelli.get(posiz).get("id") );
					//s.l("idAlbero "+idAlbero + " "+Globale.preferenze.getAlbero(idAlbero).derivato);
					final boolean derivato = Globale.preferenze.getAlbero(idAlbero).grado == 20;
					final boolean esaurito = Globale.preferenze.getAlbero(idAlbero).grado == 30;
					if( derivato ) {
						vistaAlbero.setBackgroundColor( getResources().getColor( R.color.evidenzia ) );
						vistaAlbero.setOnClickListener( new View.OnClickListener() {
							@Override
							public void onClick( View v ) {
								if( !AlberoNuovo.confronta( Alberi.this, Globale.preferenze.getAlbero(idAlbero) ) ) {
									Globale.preferenze.getAlbero(idAlbero).grado = 10; // viene retrocesso
									Globale.preferenze.salva();
									aggiornaLista();
									Toast.makeText( Alberi.this, R.string.something_wrong, Toast.LENGTH_LONG ).show();
								}
							}
						});
					} else if ( esaurito ) {
						vistaAlbero.setBackgroundColor( 0xffdddddd );
						vistaAlbero.setOnClickListener( new View.OnClickListener() {
							@Override
							public void onClick( View v ) {
								if( !AlberoNuovo.confronta( Alberi.this, Globale.preferenze.getAlbero(idAlbero) ) ) {
									Globale.preferenze.getAlbero(idAlbero).grado = 10; // viene retrocesso
									Globale.preferenze.salva();
									aggiornaLista();
									Toast.makeText( Alberi.this, R.string.something_wrong, Toast.LENGTH_LONG ).show();
								}
							}
						});
					} else {
						vistaAlbero.setBackgroundColor( Color.TRANSPARENT ); // bisogna dirglielo esplicitamente altrimenti colora a caso
						vistaAlbero.setOnClickListener( new View.OnClickListener() {
							@Override
							public void onClick( View v ) {
								if( !( Globale.gc != null && idAlbero == Globale.preferenze.idAprendo ) ) // se non è già aperto
									if( !apriGedcom( idAlbero, true ) )
										return;
								startActivity( new Intent( Alberi.this, Principe.class ) );
								/*findViewById( R.id.alberi_circolo ).setVisibility( View.VISIBLE );
								new Thread( new Runnable() {
									@Override
									public void run() {
										int id_num = Integer.parseInt( (String)((HashMap)lista.getItemAtPosition(position)).get("id") );
										if( !( Globale.gc!=null && id_num==Globale.preferenze.idAprendo ) ) // se non è già aperto
											if( apriGedcom( id_num, true ) ) // TODo: in caso di Exception (ad es. x file inesistente) il Toast crasha
												return;
										startActivity( new Intent( Alberi.this, Principe.class ) );
									}
								}).start();*/
							}
						});
					}
					vistaAlbero.findViewById(R.id.albero_menu).setOnClickListener( new View.OnClickListener() {
						@Override
						public void onClick( View vista ) {
							boolean esiste = new File( getFilesDir(), idAlbero + ".json" ).exists();
							final Armadio.Cassetto cassetto = Globale.preferenze.getAlbero(idAlbero);
							PopupMenu popup = new PopupMenu( Alberi.this, vista );
							Menu menu = popup.getMenu();
							if( idAlbero == Globale.preferenze.idAprendo && Globale.daSalvare )
								menu.add(0, -1, 0, R.string.save );
							if( (Globale.preferenze.esperto && derivato) || (Globale.preferenze.esperto && esaurito) )
								menu.add(0, 0, 0, R.string.open );
							if( !esaurito || Globale.preferenze.esperto )
								menu.add(0, 1, 0, R.string.tree_info );
							if( (!derivato && !esaurito) || Globale.preferenze.esperto )
								menu.add(0, 2, 0, R.string.rename );
							if( !esaurito )
								menu.add(0, 3, 0, R.string.find_errors );
							if( esiste && !derivato && !esaurito ) // non si può ri-condividere un albero ricevuto indietro, anche se sei esperto..
								menu.add(0, 4, 0, R.string.share_tree );
							if( esiste && !derivato && !esaurito && Globale.preferenze.getAlbero(idAlbero).grado != 0 // cioè dev'essere 9 o 10
									&& cassetto.condivisioni != null && Globale.preferenze.alberi.size() > 1 )
								menu.add(0, 5, 0, R.string.compare );
							if( esiste && Globale.preferenze.esperto && !esaurito )
								menu.add(0, 6, 0, R.string.export_gedcom );
							if( esiste && (!derivato || Globale.preferenze.esperto) && (!esaurito || Globale.preferenze.esperto) )
								menu.add(0, 7, 0, R.string.make_backup );
							menu.add(0, 8, 0, R.string.delete );
							popup.show();
							popup.setOnMenuItemClickListener( new PopupMenu.OnMenuItemClickListener() {
								@Override
								public boolean onMenuItemClick( MenuItem item ) {
									int id = item.getItemId();
									if( id == -1 ) { // Salva
										U.salvaJson( Globale.gc, idAlbero );
										Globale.daSalvare = false;
									} else if( id == 0 ) { // Apre un albero derivato
										apriGedcom( idAlbero, true );
										startActivity( new Intent( Alberi.this, Principe.class ) );
									} else if( id == 1 ) { // Info Gedcom
										Intent intento = new Intent( Alberi.this, InfoAlbero.class );
										intento.putExtra( "idAlbero", idAlbero );
										startActivity(intento);
									} else if( id == 2 ) { // Rinomina albero
										View vistaMessaggio = LayoutInflater.from( Alberi.this ).inflate(R.layout.albero_nomina, lista, false );
										AlertDialog.Builder builder = new AlertDialog.Builder( Alberi.this );
										builder.setView( vistaMessaggio ).setTitle( R.string.title );
										final EditText editaNome = vistaMessaggio.findViewById( R.id.nuovo_nome_albero );
										//nuovoNome.setText( albero.get("titolo").toString() );
										editaNome.setText( alberelli.get(posiz).get("titolo") );
										builder.setPositiveButton( R.string.rename, new DialogInterface.OnClickListener() {
											public void onClick( DialogInterface dialog, int id ) {
												Globale.preferenze.rinomina( idAlbero, editaNome.getText().toString() );
												aggiornaLista();
											}
										}).setNeutralButton( R.string.cancel, null );
										AlertDialog dialog = builder.create();
										dialog.show();
										dialog.getWindow().setSoftInputMode( WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE );
									} else if( id == 3 ) { // Correggi errori
										trovaErrori( idAlbero, false );
									} else if( id == 4 ) { // Condividi albero
										Intent intento = new Intent( Alberi.this, Condivisione.class);
										intento.putExtra( "idAlbero", idAlbero );
										startActivity( intento );
									} else if( id == 5 ) { // Confronta con alberi esistenti
										if( AlberoNuovo.confronta( Alberi.this, cassetto ) ) {
											cassetto.grado = 20;
											aggiornaLista();
										} else
											Toast.makeText( Alberi.this, R.string.no_results, Toast.LENGTH_LONG ).show();
									} else if( id == 6 ) { // Esporta Gedcom
										int perm = ContextCompat.checkSelfPermission(getApplicationContext(),Manifest.permission.WRITE_EXTERNAL_STORAGE);
										if( perm == PackageManager.PERMISSION_DENIED )
											ActivityCompat.requestPermissions( Alberi.this,
													new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE, String.valueOf(idAlbero), cassetto.nome }, 6366 );
										else if( perm == PackageManager.PERMISSION_GRANTED )
											esportaGedcom( idAlbero, cassetto.nome );
									} else if( id == 7 ) { // Fai backup
										int perm = ContextCompat.checkSelfPermission(getApplicationContext(),Manifest.permission.WRITE_EXTERNAL_STORAGE);
										if( perm == PackageManager.PERMISSION_DENIED )
											ActivityCompat.requestPermissions( Alberi.this,
													new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE, String.valueOf(idAlbero) }, 327 );
										else if( perm == PackageManager.PERMISSION_GRANTED )
											faiBackup(idAlbero);
									} else if( id == 8 ) {	// Elimina albero
										new AlertDialog.Builder( Alberi.this ).setMessage( R.string.really_delete_tree )
												.setPositiveButton( R.string.delete, new DialogInterface.OnClickListener() {
													public void onClick( DialogInterface dialog, int id ) {
														eliminaAlbero( Alberi.this, idAlbero );
														aggiornaLista();
													}
												}).setNeutralButton( R.string.cancel, null ).show();
									} else {
										return false;
									}
									return true;
								}
							});
						}
					});
					return vistaAlbero;
				}
			};
			lista.setAdapter(adapter);
		}

		// Barra personalizzata
		ActionBar barra = getSupportActionBar();
		View barraAlberi = getLayoutInflater().inflate( R.layout.alberi_barra, null );
		barraAlberi.findViewById( R.id.alberi_opzioni ).setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View v ) {
				startActivity( new Intent( Alberi.this, Opzioni.class) );
			}
		});
		barra.setCustomView( barraAlberi );
		barra.setDisplayShowCustomEnabled( true );

		// Fab
		findViewById(R.id.fab).setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View v ) {
				startActivity( new Intent( Alberi.this, AlberoNuovo.class) );
			}
		});
	}

	@Override
	protected void onRestart() {
		super.onRestart();
		if( Globale.editato ) {
			recreate();
			Globale.editato = false;
		} else
			findViewById( R.id.alberi_circolo ).setVisibility( View.GONE );
	}

	void aggiornaLista() {
		alberelli.clear();
		for( Armadio.Cassetto alb : Globale.preferenze.alberi ) {
			Map<String,String> dato = new HashMap<>(3);
			dato.put( "id", String.valueOf( alb.id ) );
			dato.put( "titolo", alb.nome );
			// Se Gedcom già aperto aggiorna i dati
			if( Globale.gc != null && Globale.preferenze.idAprendo == alb.id && alb.individui < 100 )
				InfoAlbero.aggiornaDati( Globale.gc, alb );

			//s.l( alb.generazioni + " = " + alb.nome );
			dato.put( "dati", scriviDati( this, alb ) );
			alberelli.add( dato );
		}
		lista.invalidateViews();
	}

	static String scriviDati( Context contesto, Armadio.Cassetto alb ) {
		String dati = //"["+alb.grado+"] "+
				alb.individui + " " + contesto.getString(R.string.persons).toLowerCase();
		if( alb.generazioni > 0 )
			dati += " - " + alb.generazioni + " " + contesto.getString(R.string.generations).toLowerCase();
		if( alb.media > 0 )
			dati += " - " + alb.media + " " + contesto.getString(R.string.media).toLowerCase();
		return dati;
	}

	// Apertura del Gedcom temporaneo per estrarne info in Alberi
	static Gedcom apriGedcomTemporaneo( int idAlbero, boolean mettiInGlobale ) {
		Gedcom gc;
		if( Globale.gc != null && Globale.preferenze.idAprendo == idAlbero )
			gc = Globale.gc;
		else {
			gc = leggiJson( idAlbero );
			if( mettiInGlobale ) {
				Globale.gc = gc; // per poter usare ad esempio U.unaFoto()
				Globale.preferenze.idAprendo = idAlbero; // così Globale.gc e Globale.preferenze.idAprendo sono sincronizzati
			}
		}
		return gc;
	}

	// Apertura del Gedcom per editare tutto in Family Gem
	static boolean apriGedcom( int idAlbero, boolean salvaPreferenze ) {
		Globale.gc = leggiJson(idAlbero);
		if( Globale.gc == null )
			return false;
		if( salvaPreferenze ) {
			Globale.preferenze.idAprendo = idAlbero;
			Globale.preferenze.salva();
		}
		Globale.individuo = Globale.preferenze.alberoAperto().radice;
		Globale.daSalvare = false; // eventualmente lo resetta se era true
		return true;
	}

	// Legge il Json e restituisce un Gedcom
	static Gedcom leggiJson( int idAlbero ) {
		Gedcom gc;
		try {
			String contenuto = FileUtils.readFileToString( new File( Globale.contesto.getFilesDir(), idAlbero + ".json" ), "UTF-8" );
			gc = new JsonParser().fromJson( contenuto );
		} catch( IOException e ) {
			Toast.makeText( Globale.contesto, e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
			return null;
		}
		if( gc == null ) {
			Toast.makeText( Globale.contesto, R.string.no_useful_data, Toast.LENGTH_LONG ).show();
			return null;
		}
		return gc;
	}

	// Esporta un singolo file Gedcom oppure uno zip con il Gedcom + media
	void esportaGedcom( int idAlbero, String titolo ) {
		try {
			Gedcom gc = leggiJson( idAlbero );
			if( gc == null ) return;
			titolo = titolo.replaceAll( "[\\\\/:*?\"<>|]", "_" );
			String nomeFile = titolo + ".ged";
			Header testa = gc.getHeader();
			if( testa == null )
				gc.setHeader( AlberoNuovo.creaTestata( nomeFile ) );
			else
				testa.setFile( nomeFile );
			GedcomWriter scrittore = new GedcomWriter();
			File fileGc = new File( getCacheDir(), nomeFile );
			scrittore.write( gc, fileGc );
			ListaMedia visitaMedia = new ListaMedia( gc, 0 );
			gc.accept( visitaMedia );
			Map<File,Integer> files = new HashMap<>();
			for( Media med : visitaMedia.lista ) {
				String percorsoMedia = U.percorsoMedia( idAlbero, med );
				if( percorsoMedia != null )
					files.put( new File( percorsoMedia ), 2 );
			}
			File cartellaDocumenti = new File( Environment.getExternalStorageDirectory() + "/Documents" );
			String finalPath;
			if( files.isEmpty() ) {
				FileUtils.copyFileToDirectory( fileGc, cartellaDocumenti ); // Crea la cartella se non esiste
				finalPath = cartellaDocumenti.getAbsolutePath() + "/" + fileGc.getName();
			} else {
				// Crea la cartella Documents se per caso non esiste
				new File(cartellaDocumenti.getAbsolutePath()).mkdirs();
				files.put( fileGc, 0 );
				File fileZip = creaFileZip( this, files, cartellaDocumenti.getAbsolutePath()+"/"+ titolo + ".zip");
				finalPath = fileZip.getAbsolutePath();
			}
			// Rende il file visibile da Windows
			MediaScannerConnection.scanFile( Alberi.this, new String[]{ finalPath }, null, null );
			Toast.makeText( this, finalPath, Toast.LENGTH_LONG ).show();
		} catch( IOException e ) {
			Toast.makeText( this, e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
		}
	}

	// Fa il backup di un singolo albero in un file zip nella cartella '/storage/emulated/0/Documents'
	void faiBackup( int idAlbero ) {
		File cartellaDocumenti = new File( Environment.getExternalStorageDirectory() + "/Documents" );
		if( !cartellaDocumenti.exists() )
			cartellaDocumenti.mkdir();    // crea solo la cartella Documents, non tutto il percorso

		// Cerca di creare il file zip
		Armadio.Cassetto cassetto = Globale.preferenze.getAlbero( idAlbero );
		String titolo = cassetto.nome;
		File fileZip = zippaAlbero( getApplicationContext(), idAlbero, titolo, cassetto.radice, cassetto.grado,
				cartellaDocumenti.getAbsolutePath() + "/" + titolo.replaceAll( "[\\\\/:*?\"<>|]", "_" ) + ".zip" );
		MediaScannerConnection.scanFile( this, new String[]{ fileZip.getAbsolutePath() }, null, null );
		Toast.makeText( getBaseContext(), fileZip.getAbsolutePath(), Toast.LENGTH_LONG ).show();
	}

	// Crea un file zippato con l'albero, i settaggi e i media
	// Restituisce il file zip prodotto
	static File zippaAlbero( Context contesto, int idAlbero, String titoloAlbero, String radice, int grado, String percorsoZip ) {
		// Todo: il file zip diventa invalido se cerca di metterci dentro 2 file con lo stesso nome
		// ad es. 'percorsoA/img.jpg' 'percorsoB/img.jpg' diventano entrambi 'media/img.jpg'
		// bisogna verificare che i file aggiunti abbiano nomi univoci.

		// Crea la lista dei file: 2 json + i media
		Gedcom gc = Alberi.apriGedcomTemporaneo( idAlbero, false );
		if( gc == null ) return null;
		ListaMedia visitaMedia = new ListaMedia( gc, 0 );
		gc.accept( visitaMedia );
		Map<File,Integer> files = new HashMap<>();
		files.put( new File( contesto.getFilesDir(), idAlbero + ".json" ), 1 );

		// File preferenze dell'albero zippato
		Armadio.Cassetto cassetto = Globale.preferenze.getAlbero( idAlbero );
		Armadio.CassettoCondiviso settaggi = new Armadio.CassettoCondiviso(
				titoloAlbero, cassetto.individui, cassetto.generazioni, radice, cassetto.condivisioni, grado );
		settaggi.salva();
		files.put( new File( contesto.getCacheDir(), "settings.json" ), 0 );
		// in Map non possono esserci duplicati di File uguali (diversi Media che puntano allo stesso file)
		for( Media med : visitaMedia.lista ) {
			String percorsoMedia = U.percorsoMedia( idAlbero, med );
			if( percorsoMedia != null )
				files.put( new File( percorsoMedia ), 2 );
		} // todo file da cartelle sparse vengono copiati nello zip ma il percorso nel Media rimane quello originale...
		return creaFileZip(contesto, files, percorsoZip);
	}

	// Riceve una lista di file e produce il file zip
	static File creaFileZip(Context contesto, Map<File,Integer> files, String percorsoZip) {
		File fileZip = new File( percorsoZip );
		byte[] buffer = new byte[128];
		try {
			ZipOutputStream zos = new ZipOutputStream( new FileOutputStream( fileZip ) );
			for( Map.Entry<File, Integer> fileTipo : files.entrySet() ) {
				File file = fileTipo.getKey();
				FileInputStream fis = new FileInputStream( file );
				String nomeFile = file.getName();   // File che non vengono rinominati ('settings.json', 'Xxxx.ged')
				if( fileTipo.getValue().equals( 1 ) )
					nomeFile = "tree.json";
				else if( fileTipo.getValue().equals( 2 ) )
					nomeFile = "media/" + file.getName();
				zos.putNextEntry( new ZipEntry( nomeFile ) );
				int read;
				while( ( read = fis.read( buffer ) ) != -1 ) {
					zos.write( buffer, 0, read );
				}
				zos.closeEntry();
				fis.close();
			}
			zos.close();
		} catch( IOException e ) {
			Toast.makeText( contesto.getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
		}
		return fileZip;
	}

	static void eliminaAlbero( Context contesto, int idAlbero ) {
		File file = new File( contesto.getFilesDir(), idAlbero + ".json");
		file.delete();
		File cartella = new File( contesto.getExternalFilesDir(null), String.valueOf(idAlbero) );
		eliminaFileCartelle( cartella );
		if( Globale.preferenze.idAprendo == idAlbero ) {
			Globale.gc = null;
			Globale.editato = true;
		}
		Globale.preferenze.elimina( idAlbero );
	}

	static void eliminaFileCartelle( File fileOrDirectory ) {
		if( fileOrDirectory.isDirectory() )
			for( File child : fileOrDirectory.listFiles() )
				eliminaFileCartelle( child );
		fileOrDirectory.delete();
	}

	@Override
	public void onRequestPermissionsResult( int codice, String[] permessi, int[] accordi ) {
		if( accordi.length > 0 && accordi[0] == PackageManager.PERMISSION_GRANTED ) {
			if( codice == 6366 )
				esportaGedcom( Integer.parseInt(permessi[1]), permessi[2] );
			else if( codice == 327 )
				faiBackup( Integer.parseInt(permessi[1]) );
		}
	}

	Gedcom trovaErrori( final int idAlbero, final boolean correggi ) {
		Gedcom gc = leggiJson( idAlbero );
		if( gc == null ) {
			// todo fai qualcosa per recuperare un file introvabile..?
			return null;
		}
		int errori = 0;
		int num;
		// Radice in preferenze
		Armadio.Cassetto albero = Globale.preferenze.getAlbero( idAlbero );
		Person radica = gc.getPerson( albero.radice );
		// Radice punta ad una persona inesistente
		if( albero.radice != null && radica == null ) {
			if( !gc.getPeople().isEmpty() ) {
				if( correggi ) {
					albero.radice = U.trovaRadice( gc );
					Globale.preferenze.salva();
				} else errori++;
			} else { // albero senza persone
				if( correggi ) {
					albero.radice = null;
					Globale.preferenze.salva();
				} else errori++;
			}
		}
		// Oppure non è indicata una radice in preferenze pur essendoci persone nell'albero
		if( radica == null && !gc.getPeople().isEmpty() ) {
			if( correggi ) {
				albero.radice = U.trovaRadice( gc );
				Globale.preferenze.salva();
			} else errori++;
		}
		// O in preferenze è indicata una radiceCondivisione che non esiste
		Person radicaCondivisa = gc.getPerson( albero.radiceCondivisione );
		if( albero.radiceCondivisione != null && radicaCondivisa == null ) {
			if( correggi ) {
				albero.radiceCondivisione = null; // la elimina e basta
				Globale.preferenze.salva();
			} else errori++;
		}
		// Cerca famiglie vuote o con un solo membro per eliminarle
		for( Family f : gc.getFamilies() ) {
			num = 0;
			for( SpouseRef sr : f.getHusbandRefs() ) {
				num++;
			}
			for( SpouseRef sr : f.getWifeRefs() ) {
				num++;
			}
			for( ChildRef cr : f.getChildRefs() ) {
				num++;
			}
			if( num < 2 ) {
				if( correggi ) {
					gc.getFamilies().remove( f ); // così facendo lasci i ref negli individui orfani della famiglia a cui si riferiscono...
					// ma c'è il resto del correttore che li risolve
					break;
				} else errori++;
			}
		}
		// Riferimenti da una persona alla famiglia dei genitori e dei figli
		for( Person p : gc.getPeople() ) {
			for( ParentFamilyRef pfr : p.getParentFamilyRefs() ) {
				Family fam = gc.getFamily( pfr.getRef() );
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
			// Riferimenti a Media inesistenti
			// ok ma SOLO per le persone, forse andrebbe fatto col Visitor per tutti gli altri
			num = 0;
			for( MediaRef mr : p.getMediaRefs() ) {
				Media med = gc.getMedia( mr.getRef() );
				//s.l( mr.getRef() +"  > " + med);
				if( med == null ) {
					if( correggi ) {
						p.getMediaRefs().remove( mr );
						break;
					} else errori++;
				} else {
					if( mr.getRef().equals( med.getId() ) ) {
						num++;
						if( num > 1 )
							if( correggi ) {
								p.getMediaRefs().remove( mr );
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
		// Aggiunge un tag (FILE) ai Media che non l'hanno
		ListaMedia visitaMedia = new ListaMedia( gc, 0 );
		gc.accept( visitaMedia );
		for( Media med : visitaMedia.lista ) {
			if( med.getFileTag() == null ) {
				if( correggi ) med.setFileTag( "FILE" );
				else errori++;
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
						Globale.gc = null; // così se era aperto poi lo ricarica corretto
						trovaErrori( idAlbero, false );	// riapre per ammirere il risultato
						aggiornaLista();
					}
				});
			}
			dialog.setNeutralButton( android.R.string.cancel, null ).show();
		}
		return gc;
	}
}