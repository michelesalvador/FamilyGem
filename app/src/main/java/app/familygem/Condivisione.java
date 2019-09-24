package app.familygem;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Header;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Submitter;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class Condivisione extends AppCompatActivity {

	Gedcom gc;
	Armadio.Cassetto casso;
	String nomeAutore;
	String dataId;
	String idAutore;
	boolean uploadSuccesso;

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		setContentView(R.layout.condivisione );

		final int idAlbero = getIntent().getIntExtra( "idAlbero", 1 );
		casso = Globale.preferenze.getAlbero( idAlbero );

		// Titolo dell'albero
		final EditText editaTitolo = findViewById( R.id.condividi_titolo );
		editaTitolo.setText( casso.nome );

		if( casso.grado == 10 )
			((TextView)findViewById( R.id.condividi_tit_autore )).setText( R.string.changes_submitter );

		gc = Alberi.apriGedcomTemporaneo( idAlbero, true );
		if( gc != null ) {
			// Radice dell'albero
			String idRadice;
			if( casso.radiceCondivisione != null && gc.getPerson(casso.radiceCondivisione) != null )
				idRadice = casso.radiceCondivisione;
			else if( casso.radice != null && gc.getPerson(casso.radice) != null ) {
				idRadice = casso.radice;
				casso.radiceCondivisione = idRadice; // per poter condividere subito l'albero senza cambiare la radice
			} else {
				idRadice = U.trovaRadice( gc );
				casso.radiceCondivisione = idRadice;
			}
			Person radice = gc.getPerson(idRadice);
			if( radice != null && casso.grado < 10 ) { // viene mostrata solo alla prima condivisione, non al ritorno
				LinearLayout scatolaRadice = findViewById(R.id.condividi_radice);
				scatolaRadice.setVisibility( View.VISIBLE );
				View vistaRadice = U.linkaPersona( scatolaRadice, radice, 1 );
				vistaRadice.setOnClickListener( new View.OnClickListener() {
					@Override
					public void onClick( View v ) {
						Intent intento = new Intent( Condivisione.this, Principe.class );
						intento.putExtra( "anagrafeScegliParente", true );
						startActivityForResult( intento,5007 );
					}
				});
			}
			// Nome autore
			final Submitter[] autore = new Submitter[1];
			// albero in Italia con submitter referenziato
			if( casso.grado == 0 && gc.getHeader() != null && gc.getHeader().getSubmitter(gc) != null )
				autore[0] = gc.getHeader().getSubmitter( gc );
			// in Italia ci sono autori ma nessuno referenziato, prende l'ultimo
			else if( casso.grado == 0 && !gc.getSubmitters().isEmpty() )
				autore[0] = gc.getSubmitters().get(gc.getSubmitters().size()-1);
			// in Australia ci sono autori freschi, ne prende uno
			else if( casso.grado == 10 && U.autoreFresco(gc) != null )
				autore[0] = U.autoreFresco(gc);
			final EditText editaAutore = findViewById(R.id.condividi_autore);
			nomeAutore = autore[0] == null ? "" : autore[0].getName();
			editaAutore.setText( nomeAutore );

			// Raccoglie i dati della condivisione e posta al database
			findViewById( R.id.bottone_condividi ).setOnClickListener( new View.OnClickListener() {
				@Override
				public void onClick( View v ) {
					if( uploadSuccesso )
						concludi();
					else {
						v.setEnabled( false );
						findViewById( R.id.condividi_circolo ).setVisibility(View.VISIBLE);

						// Titolo dell'albero
						String titoloEditato = editaTitolo.getText().toString();
						if( !casso.nome.equals(titoloEditato) ) {
							casso.nome = titoloEditato;
							Globale.preferenze.salva();
						}

						// Aggiornamento del submitter
						boolean gcModificato = false;
						Header testata = gc.getHeader();
						if( testata == null ) {
							testata = AlberoNuovo.creaTestata( casso.id + ".json" );
							gc.setHeader( testata );
						}
						if( autore[0] == null ) {
							autore[0] = Podio.nuovoAutore( null );
							gcModificato = true;
						}
						if( testata.getSubmitterRef() == null ) {
							testata.setSubmitterRef( autore[0].getId() );
							gcModificato = true;
						}
						String nomeAutoreEditato = editaAutore.getText().toString();
						if( !nomeAutoreEditato.equals(nomeAutore) ) {
							nomeAutore = nomeAutoreEditato;
							autore[0].setName( nomeAutore );
							U.aggiornaDate( autore[0] );
							gcModificato = true;
						}
						idAutore = autore[0].getId();
						if( gcModificato )
							U.salvaJson( gc, idAlbero ); // baypassando la preferenza di non salvare in atomatico

						// Invia i dati
						if( BuildConfig.utenteAruba != null )
							new PostaDatiCondivisione().execute( Condivisione.this );
					}
				}
			});
		} else
			findViewById( R.id.condividi_scatola ).setVisibility( View.GONE );
	}

	// Inserisce il sommario della condivisione nel database di www.familygem.app
	// Se tutto va bene crea il file zip con l'albero e le immagini
	static class PostaDatiCondivisione extends AsyncTask<Condivisione,Void,Condivisione> {
		@Override
		protected Condivisione doInBackground(Condivisione... contesti) {
			Condivisione questo = contesti[0];
			try {
				URL url = new URL("https://www.familygem.app/inserisci.php");
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("POST");
				OutputStream out = new BufferedOutputStream( conn.getOutputStream() );
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
				String dati = "password=" + URLEncoder.encode( BuildConfig.passwordAruba, "UTF-8") +
						"&titoloAlbero=" + URLEncoder.encode( questo.casso.nome, "UTF-8") +
						"&nomeAutore=" + URLEncoder.encode( questo.nomeAutore, "UTF-8");
				writer.write( dati );
				writer.flush();
				writer.close();
				out.close();

				// Risposta
				BufferedReader lettore = new BufferedReader( new InputStreamReader(conn.getInputStream()) );
				String linea1 = lettore.readLine();
				lettore.close();
				conn.disconnect();
				if( linea1.startsWith("20") ) {
					questo.dataId = linea1.replaceAll( "[-: ]", "" );
					Armadio.Invio invio = new Armadio.Invio( questo.dataId, questo.idAutore );
					questo.casso.aggiungiCondivisione(invio);
					Globale.preferenze.salva();
				}
			} catch( Exception e ) {
				//e.printStackTrace();
				U.tosta( questo, e.getLocalizedMessage() );
			}
			return questo;
		}

		@Override
		protected void onPostExecute(Condivisione questo) {
			if( questo.dataId != null && questo.dataId.startsWith("20") ) {
				//Toast.makeText( questo, "Dati inseriti nel database.", Toast.LENGTH_SHORT ).show(); // ridondante
				Alberi.zippaAlbero( questo, questo.casso.id, questo.casso.nome,
						questo.casso.radiceCondivisione, 9, questo.getCacheDir()+"/"+questo.dataId+".zip" );
				new InvioFTP().execute( questo );
			} else {
				//Toast.makeText( questo, questo.getText(R.string.something_wrong), Toast.LENGTH_LONG ).show();
					// ok ma sostituisce il messaggio di tosta() in catch()
				questo.findViewById( R.id.bottone_condividi ).setEnabled(true);
				questo.findViewById( R.id.condividi_circolo ).setVisibility(View.INVISIBLE);
			}
		}
	}

	// Carica in ftp il file zip con l'albero condiviso
	static class InvioFTP extends AsyncTask<Condivisione, Void, Condivisione> {
		protected Condivisione doInBackground(Condivisione... contesti) {
			Condivisione questo = contesti[0];
			try {
				FTPClient ftpClient = new FTPClient();
				ftpClient.connect( "89.46.104.211", 21 );
				ftpClient.enterLocalPassiveMode();
				ftpClient.login( BuildConfig.utenteAruba, BuildConfig.passwordAruba );
				ftpClient.changeWorkingDirectory("/www.familygem.app/condivisi");
				ftpClient.setFileType( FTP.BINARY_FILE_TYPE );
				BufferedInputStream buffIn;
				String nomeZip = questo.dataId + ".zip";
				buffIn = new BufferedInputStream( new FileInputStream( questo.getCacheDir() + "/" + nomeZip ) );
				questo.uploadSuccesso = ftpClient.storeFile( nomeZip, buffIn );
				buffIn.close();
				ftpClient.logout();
				ftpClient.disconnect();
			} catch( Exception e ) {
				U.tosta( questo, e.getLocalizedMessage() );
			}
			return questo;
		}
		protected void onPostExecute(Condivisione questo) {
			if( questo.uploadSuccesso ) {
				Toast.makeText( questo, R.string.correctly_uploaded, Toast.LENGTH_SHORT ).show();
				questo.concludi();
			} else {
				questo.findViewById( R.id.bottone_condividi ).setEnabled(true);
				questo.findViewById( R.id.condividi_circolo ).setVisibility( View.INVISIBLE );
			}
		}
	}

	// Mostra le app per condividere il link
	void concludi() {
		Intent intento = new Intent( Intent.ACTION_SEND );
		intento.setType( "text/plain" );
		intento.putExtra( Intent.EXTRA_SUBJECT, getString( R.string.sharing_tree ) );
		intento.putExtra( Intent.EXTRA_TEXT, getString( R.string.click_this_link,
				"https://www.familygem.app/share.php?tree=" + dataId ) );
		//startActivity( Intent.createChooser( intento, "Condividi con" ) );
		/* Tornando indietro da una app di messaggistica il requestCode 35417 arriva sempre corretto
			Invece il resultCode può essere RESULT_OK o RESULT_CANCELED a capocchia
			Ad esempio da Gmail ritorna indietro sempre con RESULT_CANCELED sia che l'email è stata inviata o no
			anche inviando un Sms ritorna RESULT_CANCELED anche se l'sms è stato inviato
			oppure da Whatsapp è RESULT_OK sia che il messaggio è stato inviato o no
			In pratica non c'è modo di sapere se nella app di messaggistica il messaggio è stato inviato */
		startActivityForResult( Intent.createChooser(intento,getText(R.string.share_with)),35417 );
		findViewById( R.id.bottone_condividi ).setEnabled(true);
		findViewById( R.id.condividi_circolo ).setVisibility( View.INVISIBLE );
	}

	// Aggiorna le preferenze così da mostrare la nuova radice scelta in Anagrafe
	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data ) {
		super.onActivityResult( requestCode, resultCode, data );
		if( resultCode == AppCompatActivity.RESULT_OK ) {
			if( requestCode == 5007 ) {
				casso.radiceCondivisione = data.getStringExtra( "idParente" );
				Globale.preferenze.salva();
				recreate();
			}
		}
		// Ritorno indietro da qualsiasi app di condivisione, nella quale il messaggio è stato inviato oppure no
		if ( requestCode == 35417 ) {
			// Todo chiudi tastiera
			Toast.makeText( getApplicationContext(), R.string.sharing_completed, Toast.LENGTH_LONG ).show();
		}
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem i ) {
		onBackPressed();
		return true;
	}
}