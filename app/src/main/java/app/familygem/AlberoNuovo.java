package app.familygem;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.CharacterSet;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.GedcomVersion;
import org.folg.gedcom.model.Generator;
import org.folg.gedcom.model.Header;
import org.folg.gedcom.model.Submitter;
import org.folg.gedcom.parser.JsonParser;
import org.folg.gedcom.parser.ModelParser;

public class AlberoNuovo extends AppCompatActivity {

	boolean permessoContatti;

    @Override
    protected void onCreate( Bundle stato ) {
        super.onCreate( stato );
        setContentView(R.layout.albero_nuovo);

	    // Alla prima apertura nasconde la freccia indietro
	    if( Globale.preferenze.idAprendo == 0 )
		    getSupportActionBar().setDisplayHomeAsUpEnabled( false );

	    // Parte con un albero vuoto
        findViewById( R.id.bottone_albero_vuoto ).setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick( View vista ) {
              	int perm = ContextCompat.checkSelfPermission(vista.getContext(),Manifest.permission.READ_CONTACTS);
	            if( perm == PackageManager.PERMISSION_DENIED )
		            ActivityCompat.requestPermissions( (AppCompatActivity)vista.getContext(), new String[]{Manifest.permission.READ_CONTACTS}, 6047 );
	            else if( perm == PackageManager.PERMISSION_GRANTED )
		            permessoContatti = true;
	            View vistaMessaggio = LayoutInflater.from( vista.getContext() ).inflate(R.layout.albero_nomina, null);
	            AlertDialog.Builder builder = new AlertDialog.Builder( vista.getContext() );
	            builder.setView( vistaMessaggio ).setTitle( R.string.tree_name );
	            TextView vistaTesto = vistaMessaggio.findViewById( R.id.nuovo_nome_testo );
	            vistaTesto.setText( R.string.modify_later );
	            vistaTesto.setVisibility( View.VISIBLE );
	            final EditText nuovoNome = vistaMessaggio.findViewById( R.id.nuovo_nome_albero );
	            builder.setPositiveButton( R.string.create, new DialogInterface.OnClickListener() {
		            public void onClick( DialogInterface dialog, int id ) {
			            int num = Globale.preferenze.max() + 1;
			            File fileJson = new File( getFilesDir(), num + ".json" );
			            Globale.gc = new Gedcom();
						Globale.gc.setHeader( creaTestata( fileJson.getName(), permessoContatti ) ); //.getAbsolutePath()
			            Globale.gc.createIndexes();
			            JsonParser jp = new JsonParser();
			            try {
				            FileUtils.writeStringToFile( fileJson, jp.toJson(Globale.gc), "UTF-8" );
			            } catch (IOException e) {
				            Toast.makeText( AlberoNuovo.this, e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
				            e.printStackTrace();
			            }
			            Globale.preferenze.aggiungi( new Armadio.Cassetto(
					            num, nuovoNome.getText().toString(), null, 0, 0, null, null ));
			            Globale.preferenze.idAprendo = num;
			            Globale.preferenze.salva();
			            startActivity( new Intent( AlberoNuovo.this, Principe.class ) );
		            }
	            }).setNeutralButton( R.string.cancel, null );
	            AlertDialog dialog = builder.create();
	            dialog.show();
	            dialog.getWindow().setSoftInputMode( WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE );
            }
        });

	    findViewById(R.id.bottone_scarica_esempio).setOnClickListener( new View.OnClickListener() {
		    public void onClick( View v ) {
			    int perm = ContextCompat.checkSelfPermission(v.getContext(),Manifest.permission.WRITE_EXTERNAL_STORAGE);
			    if( perm == PackageManager.PERMISSION_DENIED )
				    ActivityCompat.requestPermissions( (AppCompatActivity)v.getContext(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 5641 );
			    else if( perm == PackageManager.PERMISSION_GRANTED )
				    scaricaEsempio();
		    }
	    });

		findViewById(R.id.bottone_importa_gedcom).setOnClickListener( new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent( Intent.ACTION_GET_CONTENT );
				intent.setType( "application/*" );
				//intent.addCategory( Intent.CATEGORY_OPENABLE );
				startActivityForResult( intent,630 );
			}
		});

	    findViewById(R.id.bottone_recupera_backup).setOnClickListener( new View.OnClickListener() {
		    public void onClick(View v) {
			    AlertDialog.Builder builder = new AlertDialog.Builder( AlberoNuovo.this );
			    builder.setMessage( "Search for 'Documents/Family Gem backup.zip'\nWarning: existing trees will be overwriten." );
			    builder.setPositiveButton( android.R.string.ok, new DialogInterface.OnClickListener() {
				    public void onClick( DialogInterface dialog, int id ) {
					    Intent intent = new Intent( Intent.ACTION_GET_CONTENT );
					    intent.setType( "application/zip" );
					    startActivityForResult( intent, 219 );
				    }
			    }).setNeutralButton( android.R.string.cancel, null )
					    .create().show();
		    }
	    });
    }

	// Elabora la risposta alle richieste di permesso
	@Override
	public void onRequestPermissionsResult( int codice, String[] permessi, int[] accordi ) { // If request is cancelled, the result arrays are empty
		if( accordi.length > 0 && accordi[0] == PackageManager.PERMISSION_GRANTED ) {
			if( codice == 6047 ) {
				permessoContatti = true;
			} else if( codice == 5641 ) {
				scaricaEsempio();
			}
		}
	}

	// Scarica da internet un file zip nella cartella Download e lo decomprime
    void scaricaEsempio() {
	    DownloadManager gestoreScarico = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
	    // Evita download multipli
	    Cursor curso = gestoreScarico.query( new DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_RUNNING) );
	    if( curso.moveToFirst() ) {
		    curso.close();
		    findViewById(R.id.bottone_scarica_esempio).setEnabled(false);
		    return;
	    }
	    String url = "https://drive.google.com/uc?export=download&id=19AR8RvROkxPwfdk1hCjNlyhnKGNfEin7";
	    //String url = "https://www.familygem.app/trees/the_simpsons.zip"; // singhiozzante e fallimentare
	    final String percorsoZip = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
			    + "/the_Simpsons.zip";
	    DownloadManager.Request richiesta = new DownloadManager.Request( Uri.parse( url ) )
			    .setTitle("The Simpsons Family Tree")
			    .setDescription("Downloading")
			    .setNotificationVisibility( DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
			    .setVisibleInDownloadsUi(false)
			    .setDestinationUri( Uri.parse( "file://" + percorsoZip ) );
	    gestoreScarico.enqueue( richiesta );
	    BroadcastReceiver alCompletamento = new BroadcastReceiver() {
		    @Override
		    public void onReceive( Context contesto, Intent intento ) {
			    File zipScaricato = new File( percorsoZip );
			    if( zipScaricato.exists() ){
				    // Decomprime il file zip nella memoria esterna e cartella col numero dell'albero
				    int numAlbero = Globale.preferenze.max() + 1;
				    String percorsoImmagini = getExternalFilesDir(null) + "/" + numAlbero;
				    File dirImmagini = new File( percorsoImmagini );
				    if( !dirImmagini.exists() )
					    dirImmagini.mkdir();
				    try {
					    ZipInputStream zis = new ZipInputStream( new FileInputStream( percorsoZip ));
					    ZipEntry zipEntry;
					    int len;
					    byte[] buffer = new byte[1024];
					    while( (zipEntry = zis.getNextEntry()) != null ) {
						    File newFile = new File( percorsoImmagini, zipEntry.getName() );
						    FileOutputStream fos = new FileOutputStream( newFile );
						    while( (len = zis.read(buffer)) > 0 ) {
							    fos.write(buffer, 0, len);
						    }
						    fos.close();
					    }
					    zis.closeEntry();
					    zis.close();
					    // Sposta il file 'tree.json' nella memoria interna rinominandolo
					    FileUtils.moveFile( new File( percorsoImmagini,"tree.json" ),
							    new File( getFilesDir(), numAlbero + ".json" )  );
					    zipScaricato.delete();
				    } catch( Exception e ) {
					    Toast.makeText( AlberoNuovo.this, e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
				    }
				    // Crea la voce nelle preferenze e apre l'albero
				    Globale.preferenze.aggiungi( new Armadio.Cassetto( numAlbero, "The Simpsons", percorsoImmagini, 41, 11, "I1",null ) );
				    if( Globale.preferenze.caricaAlbero ) {
					    if( Alberi.apriJson( numAlbero, true ) )
					        startActivity( new Intent( AlberoNuovo.this, Principe.class ) );
				    } else
					    startActivity( new Intent( AlberoNuovo.this, Alberi.class ) );
			    } else
				    Toast.makeText( AlberoNuovo.this, percorsoZip + " not found", Toast.LENGTH_LONG ).show();
			    unregisterReceiver( this );
		    }
	    };
	    registerReceiver( alCompletamento, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE) );
	    // ACTION_DOWNLOAD_COMPLETE intende il completamento di QUALSIASI download che è in corso, non solo questo.
    }

    // Importa un file Gedcom scelto col file manager
	// ToDo: aspetta un attimo, non sarebbe meglio usare Gedcom2Json ?
    @Override
    protected void onActivityResult( int requestCode, int resultCode, Intent data ) {
        if( resultCode == RESULT_OK && requestCode == 630 ){
        	try {
				Uri uri = data.getData();
				//s.l( "uri= " + uri );
				String percorso = U.uriPercorsoFile( uri );	// in Google drive trova solo il nome del file
				//s.l( "percorso= " + percorso );
				File fileGedcom;
				String nomeAlbero;
				String percorsoCartella = null;
		        int nuovoNum = Globale.preferenze.max() + 1;
				if( percorso.lastIndexOf('/') > 0 ) {	// se è un percorso completo del file gedcom
					// Apre direttamente il file ged
					fileGedcom = new File( percorso );
					// Percorso della cartella da cui ha caricato il gedcom
					percorsoCartella = fileGedcom.getParent();
					nomeAlbero = fileGedcom.getName();
				}
				else {	// è solo il nome del file 'famiglia.ged'
					// Copia il contenuto del Gedcom in un file temporaneo
					InputStream input = getContentResolver().openInputStream(uri);
					fileGedcom = new File( getCacheDir(), "temp.ged" );
					FileUtils.copyInputStreamToFile( input, fileGedcom );
					if( percorso == null ) nomeAlbero = getString( R.string.tree ) + " " + nuovoNum;
					else nomeAlbero = percorso;
				}
				//s.l( "fileGedcom = " + fileGedcom );
				// Crea l'oggetto Gedcom
				ModelParser mp = new ModelParser();
				Gedcom gc = mp.parseGedcom( fileGedcom );
				if( gc.getHeader() == null ) {
					Toast.makeText( this, R.string.invalid_gedcom, Toast.LENGTH_LONG ).show();
					return;
				}
				gc.createIndexes();  // ma dai qui non è necessario
				// Salva il file Json
				if( nomeAlbero.lastIndexOf('.') > 0 )
					nomeAlbero = nomeAlbero.substring(0, nomeAlbero.lastIndexOf('.'));
				PrintWriter pw = new PrintWriter( getFilesDir() + "/" + nuovoNum + ".json" );
				JsonParser jp = new JsonParser();
				pw.print( jp.toJson(gc) );
				pw.close();
				// Salva le impostazioni in Armadio
		        String idRadice = U.trovaRadice(gc);
				Globale.preferenze.aggiungi( new Armadio.Cassetto( nuovoNum, nomeAlbero, percorsoCartella,
						gc.getPeople().size(), InfoAlbero.quanteGenerazioni(gc,idRadice), idRadice, null ) );
		        // Se necessario propone di mostrare le funzioni avanzate TODO il dialogo va pensato meglio quando farlo comparire
		        if( !gc.getSources().isEmpty() && !Globale.preferenze.esperto ) {
			        /*AlertDialog.Builder dialog = new AlertDialog.Builder( this );
			        dialog.setMessage( "L'albero che hai importato sembra piuttosto complesso.\nPer gestirlo vuoi mostrare le funzioni avanzate di Family Ged?" );
			        dialog.setPositiveButton( android.R.string.yes, new DialogInterface.OnClickListener() {
				        public void onClick( DialogInterface dialogo, int i ) {
					        dialogo.cancel();
					        Globale.preferenze.esperto = true;
					        Globale.preferenze.salva();
				        }
			        });
			        dialog.setNegativeButton( android.R.string.no, new DialogInterface.OnClickListener() {
				        public void onClick( DialogInterface dialogo, int i ) {
					        dialogo.cancel();
				        }
			        }).show();*/
			        Globale.preferenze.esperto = true;
			        Globale.preferenze.salva();
		        }
		        if( Globale.preferenze.caricaAlbero ) {
			        // Apre il nuovo albero in diagramma
			        if( Alberi.apriJson( nuovoNum, true ) )
			            startActivity( new Intent( this, Principe.class ) );
		        } else
			        startActivity( new Intent( this, Alberi.class ) );
            } catch( Exception e ) {	//IOException | SAXParseException | URISyntaxException |FileNotFoundException |
                Toast.makeText( AlberoNuovo.this, e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
            }
        }

        // Importa il file ZIP di backup SOVRASCRIVENDO gli alberi esistenti
	    if( resultCode == RESULT_OK && requestCode == 219 ){
        	//s.l( "data.getDataString() = " + data.getDataString() );
			try {
				ZipInputStream zis = new ZipInputStream( getContentResolver().openInputStream( data.getData() ) );
			    ZipEntry zipEntry;
				int len;
				byte[] buffer = new byte[1024];
				while( (zipEntry = zis.getNextEntry()) != null ) {
				    File newFile = new File( getFilesDir(), zipEntry.getName() );
				    FileOutputStream fos = new FileOutputStream(newFile);
				    while ((len = zis.read(buffer)) > 0) {
					    fos.write(buffer, 0, len);
				    }
				    fos.close();
			    }
			    zis.closeEntry();
			    zis.close();
			} catch( Exception e ) {
				Toast.makeText( AlberoNuovo.this, e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
			}
			Globale.avvia( getApplicationContext() );
			startActivity( new Intent( AlberoNuovo.this, Alberi.class ));
	    }
    }

	// Crea l'intestazione standard per questa app
	static Header creaTestata( String nomeFile, boolean permessoContatti ) {
		Header testa = new Header();
		Generator app = new Generator();
		app.setValue( "FAMILY_GEM" );
		app.setName( "Family Gem" );
		app.setVersion( BuildConfig.VERSION_NAME );
		testa.setGenerator( app );
		testa.setFile( nomeFile );
		GedcomVersion versione = new GedcomVersion();
		versione.setForm( "LINEAGE-LINKED" );
		versione.setVersion( "5.5.1" );
		testa.setGedcomVersion( versione );
		CharacterSet codifica = new CharacterSet();
		codifica.setValue( "UTF-8" );
		testa.setCharacterSet( codifica );
		// Submitter
		if( permessoContatti )	{
			Submitter autore = Podio.nuovoAutore( null );
			Cursor c = Globale.contesto.getContentResolver().query( ContactsContract.Profile.CONTENT_URI, null, null, null, null );
			if( c != null && c.moveToFirst() ) {    // Prende il profilo 'io' in rubrica
				autore.setName( c.getString( c.getColumnIndex( "display_name" ) ) );
				c.close();
			}
			testa.setSubmitterRef( autore.getId() );
		}
		Locale loc = new Locale( Locale.getDefault().getLanguage() );
		// C'è anche   Resources.getSystem().getConfiguration().locale.getLanguage() che ritorna lo stesso 'it'
		testa.setLanguage( loc.getDisplayLanguage( Locale.ENGLISH ) );    // ok prende la lingua di sistema in inglese, non nella lingua locale
		return testa;
	}

	// Freccia indietro nella toolbar come quella hardware
	@Override
	public boolean onOptionsItemSelected( MenuItem i ) {
		onBackPressed();
		return true;
	}
}