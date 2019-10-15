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
import android.view.View;
import org.apache.commons.net.ftp.FTPClient;
import java.io.FileOutputStream;
import java.io.InputStream;

public class Facciata extends AppCompatActivity {

	Thread apertura;

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		setContentView( R.layout.facciata );
		findViewById( R.id.facciata_circolo ).setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View v ) {
				apertura.interrupt();
				// ToDo https://stackoverflow.com/questions/36843785
				startActivity( new Intent( Facciata.this, Alberi.class ) );
			}
		});
		new Thread( new Runnable() {
			public void run() {
				/* Apertura in seguito al click su due tipi di link:
					https://www.familygem.app/share.php?tree=20190802224208 Messaggio breve
					https://www.familygem.app/condivisi/20190802224208.zip  Pagina di condivisione del sito
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
						U.tosta( Facciata.this, R.string.cant_understand_uri );
						return;
					}
					if( !BuildConfig.utenteAruba.isEmpty() ) {
						int perm = ContextCompat.checkSelfPermission( getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
						if( perm == PackageManager.PERMISSION_DENIED )
							ActivityCompat.requestPermissions( Facciata.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,dataId}, 1457 );
						else if( perm == PackageManager.PERMISSION_GRANTED )
							scaricaCondiviso( dataId );
					}
				} else if( Globale.preferenze.idAprendo == 0 )	// cioè praticamente alla prima apertura
					startActivity( new Intent( Facciata.this, AlberoNuovo.class) );
				else if( Globale.preferenze.caricaAlbero ) {
					if( Globale.gc == null ) {
						if( !Alberi.apriGedcom( Globale.preferenze.idAprendo, false ) )
							// tentativo di aprire un file mancante
							U.tosta( Facciata.this, getString(R.string.cant_find_file) );
							// Todo qui non dovrebbe starci un return ?  Da provare...
					}
					startActivity( new Intent( Facciata.this, Principe.class ) );
				} else
					startActivity( new Intent( Facciata.this, Alberi.class ) );
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
				//U.tosta( this, R.string.download_completed ); // non occorre
				AlberoNuovo.decomprimiZip( this, percorsoZip, null );
			}
			client.logout();
			client.disconnect();
		} catch ( Exception e) {
			U.tosta( this, e.getLocalizedMessage() );
		}
	}

	@Override
	public void onRequestPermissionsResult( int codice, final String[] permessi, int[] accordi ) {
		if( accordi.length > 0 && accordi[0] == PackageManager.PERMISSION_GRANTED ) {
			if( codice == 1457 ) {
				new Thread( new Runnable() {
					public void run() {
						scaricaCondiviso( permessi[1] );
					}
				}).start();
			}
		}
	}
}
