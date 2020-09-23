package app.familygem;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.os.Environment;
import org.apache.commons.net.ftp.FTPClient;
import java.io.FileOutputStream;
import java.io.InputStream;

public class Facciata extends AppCompatActivity {

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		setContentView( R.layout.facciata );

		/* Apertura in seguito al click su vari tipi di link:
		https://www.familygem.app/share.php?tree=20190802224208
			Messaggio breve
			funziona in Gmail, in SMS ma non in Chrome. (Whatsapp da provare)
		intent://www.familygem.app/condivisi/20200218134922.zip#Intent;scheme=https;end
			Link ufficiale nella pagina di condivisione del sito
			è l'unico che sembra avere certezza di funzionare, almeno in Chrome
		https://www.familygem.app/condivisi/20190802224208.zip
			URL diretto allo zip
			Funziona nei vecchi android, nei nuovi semplicemente il file viene scaricato
		*/
		Intent intento = getIntent();
		Uri uri = intento.getData();
		// Aprendo l'app da Task Manager, evita di re-importare un albero condiviso appena importato
		boolean fromHistory = (getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY;
		if( uri != null && !fromHistory ) {
			String dataId;
			if( uri.getPath().equals( "/share.php" ) ) // click sul primo messaggio ricevuto
				dataId = uri.getQueryParameter("tree");
			else if( uri.getLastPathSegment().endsWith( ".zip" ) ) // click sulla pagina di invito
				dataId = uri.getLastPathSegment().replace(".zip","");
			else {
				U.tosta( this, R.string.cant_understand_uri );
				return;
			}
			if( !BuildConfig.utenteAruba.isEmpty() ) {
				int perm = ContextCompat.checkSelfPermission( getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
				if( perm == PackageManager.PERMISSION_DENIED )
					ActivityCompat.requestPermissions( Facciata.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,dataId}, 1457 );
				else if( perm == PackageManager.PERMISSION_GRANTED )
					scaricaCondiviso( this, dataId );
			}
		} else {
			Intent intent = new Intent( this, Alberi.class );
			if( Globale.preferenze.caricaAlbero ) {
				intent.putExtra( "apriAlberoAutomaticamente", true );
				intent.setFlags( Intent.FLAG_ACTIVITY_NO_ANIMATION ); // forse inefficace ma tantè
			}
			startActivity( intent );
		}
	}

	// Si collega al server e scarica il file zip per importarlo
	static void scaricaCondiviso( Context contesto,  String idData ) {
		// Un nuovo Thread è necessario per scaricare asincronicamente un file
		new Thread( () -> {
			try {
				FTPClient client = new FTPClient();
				client.connect( "89.46.104.211" );
				client.enterLocalPassiveMode();
				client.login( BuildConfig.utenteAruba, BuildConfig.passwordAruba );
				// Todo: Forse si potrebbe usare il download manager così da avere il file anche elencato in 'Downloads'
				final String percorsoZip = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
						+ "/" + idData + ".zip";
				FileOutputStream fos = new FileOutputStream(percorsoZip);
				String percorso = "/www.familygem.app/condivisi/" + idData + ".zip";
				InputStream input = client.retrieveFileStream( percorso );
				byte[] data = new byte[1024];
				int count;
				while ((count = input.read(data)) != -1) {
					fos.write(data, 0, count);
				}
				fos.close();
				if( client.completePendingCommand() ) {
					AlberoNuovo.decomprimiZip( contesto, percorsoZip, null );
				}
				client.logout();
				client.disconnect();
			} catch ( Exception e) {
				e.printStackTrace();
				U.tosta( (Activity)contesto, e.getLocalizedMessage() );
				contesto.startActivity( new Intent( contesto, Alberi.class ) );
			}
		}).start();
	}

	@Override
	public void onRequestPermissionsResult( int codice, final String[] permessi, int[] accordi ) {
		if( accordi.length > 0 && accordi[0] == PackageManager.PERMISSION_GRANTED ) {
			if( codice == 1457 ) {
				scaricaCondiviso( this, permessi[1] );
			}
		}
	}
}
