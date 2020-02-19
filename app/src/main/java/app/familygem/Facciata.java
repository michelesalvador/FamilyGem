package app.familygem;

import android.Manifest;
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
		// Non ricordo esattamente perché, ma un new Thread è necessario per intercettare gli alberi importati
		new Thread( () -> {
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
			if( uri != null ) {
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
						scaricaCondiviso( dataId );
				}
			} else {
				Intent intent = new Intent( this, Alberi.class );
				if( Globale.preferenze.caricaAlbero ) {
					intent.putExtra( "apriAlberoAutomaticamente", true );
					intent.setFlags( Intent.FLAG_ACTIVITY_NO_ANIMATION ); // forse inefficace ma tantè
				}
				startActivity( intent );
			}
		}).start();
	}

	// Si collega al server e scarica il file zip per importarlo
	void scaricaCondiviso( String idData ) {
		try {
			FTPClient client = new FTPClient();
			client.connect( "89.46.104.211" );
			client.enterLocalPassiveMode();
			client.login( BuildConfig.utenteAruba, BuildConfig.passwordAruba );
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
				AlberoNuovo.decomprimiZip( this, percorsoZip, null );
			}
			client.logout();
			client.disconnect();
		} catch ( Exception e) {
			U.tosta( this, e.getLocalizedMessage() );
			startActivity( new Intent( this, Alberi.class ) );
		}
	}

	@Override
	public void onRequestPermissionsResult( int codice, final String[] permessi, int[] accordi ) {
		if( accordi.length > 0 && accordi[0] == PackageManager.PERMISSION_GRANTED ) {
			if( codice == 1457 ) {
				new Thread( () -> scaricaCondiviso( permessi[1] ) ).start();
			}
		}
	}
}
