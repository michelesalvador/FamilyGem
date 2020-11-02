package app.familygem;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
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
				// Non ha bisogno di richiedere permessi
				scaricaCondiviso( this, dataId, null );
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
	static void scaricaCondiviso( Context contesto, String idData, View rotella ) {
		if( rotella != null )
			rotella.setVisibility( View.VISIBLE );
		// Un nuovo Thread è necessario per scaricare asincronicamente un file
		new Thread( () -> {
			try {
				FTPClient client = new FTPClient();
				client.connect( "89.46.104.211" );
				client.enterLocalPassiveMode();
				client.login( BuildConfig.utenteAruba, BuildConfig.passwordAruba );
				// Todo: Forse si potrebbe usare il download manager così da avere il file anche elencato in 'Downloads'
				String percorsoZip = contesto.getExternalCacheDir() + "/" + idData + ".zip";
				FileOutputStream fos = new FileOutputStream( percorsoZip );
				String percorso = "/www.familygem.app/condivisi/" + idData + ".zip";
				InputStream input = client.retrieveFileStream( percorso );
				if( input != null ) {
					byte[] data = new byte[1024];
					int count;
					while ((count = input.read(data)) != -1) {
						fos.write(data, 0, count);
					}
					fos.close();
					if( client.completePendingCommand() ) {
						AlberoNuovo.decomprimiZip( contesto, percorsoZip, null );
					}
				} else // Non ha trovato il file sul server
					scaricamentoFallito( contesto, contesto.getString(R.string.something_wrong), rotella );
				client.logout();
				client.disconnect();
			} catch( Exception e ) {
				scaricamentoFallito( contesto, e.getLocalizedMessage(), rotella );
			}
		}).start();
	}

	// Conclusione negativa del metodo qui sopra
	static void scaricamentoFallito( Context contesto, String messaggio, View rotella ) {
		U.tosta( (Activity)contesto, messaggio );
		if( rotella != null )
			((Activity)contesto).runOnUiThread( () -> rotella.setVisibility( View.GONE ) );
		else
			contesto.startActivity( new Intent(contesto, Alberi.class) );
	}
}
