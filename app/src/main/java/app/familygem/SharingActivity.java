package app.familygem;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class SharingActivity extends BaseActivity {

	Gedcom gc;
	Settings.Tree tree;
	Exporter esporter;
	String nomeAutore;
	int accessible; // 0 = false, 1 = true
	String dataId;
	String idAutore;
	boolean uploadSuccesso;

	@Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.condivisione);

		final int treeId = getIntent().getIntExtra("idAlbero", 1);
		tree = Global.settings.getTree(treeId);

		// Titolo dell'albero
		final EditText editaTitolo = findViewById(R.id.condividi_titolo);
		editaTitolo.setText(tree.title);

		if( tree.grade == 10 )
			((TextView)findViewById( R.id.condividi_tit_autore )).setText( R.string.changes_submitter );

		esporter = new Exporter( this );
		esporter.apriAlbero( treeId );
		gc = Global.gc;
		if( gc != null ) {
			displayShareRoot();
			// Nome autore
			final Submitter[] autore = new Submitter[1];
			// albero in Italia con submitter referenziato
			if( tree.grade == 0 && gc.getHeader() != null && gc.getHeader().getSubmitter(gc) != null )
				autore[0] = gc.getHeader().getSubmitter( gc );
			// in Italia ci sono autori ma nessuno referenziato, prende l'ultimo
			else if( tree.grade == 0 && !gc.getSubmitters().isEmpty() )
				autore[0] = gc.getSubmitters().get(gc.getSubmitters().size()-1);
			// in Australia ci sono autori freschi, ne prende uno
			else if( tree.grade == 10 && U.autoreFresco(gc) != null )
				autore[0] = U.autoreFresco(gc);
			final EditText editaAutore = findViewById(R.id.condividi_autore);
			nomeAutore = autore[0] == null ? "" : autore[0].getName();
			editaAutore.setText( nomeAutore );

			// Display an alert for the acknowledgment of sharing
			if( !Global.settings.shareAgreement ) {
				new AlertDialog.Builder(this).setTitle(R.string.share_sensitive)
						.setMessage(R.string.aware_upload_server)
						.setPositiveButton(android.R.string.ok, (dialog, id) -> {
							Global.settings.shareAgreement = true;
							Global.settings.save();
						}).setNeutralButton(R.string.remind_later, null).show();
			}

			// Raccoglie i dati della condivisione e posta al database
			findViewById( R.id.bottone_condividi ).setOnClickListener( v -> {
				if( uploadSuccesso )
					concludi();
				else {
					if( controlla(editaTitolo, R.string.please_title) || controlla(editaAutore, R.string.please_name) )
						return;

					v.setEnabled(false);
					findViewById(R.id.condividi_circolo).setVisibility(View.VISIBLE);

					// Titolo dell'albero
					String titoloEditato = editaTitolo.getText().toString();
					if( !tree.title.equals(titoloEditato) ) {
						tree.title = titoloEditato;
						Global.settings.save();
					}

					// Aggiornamento del submitter
					Header header = gc.getHeader();
					if( header == null ) {
						header = NewTree.creaTestata(tree.id + ".json");
						gc.setHeader(header);
					} else
						header.setDateTime(U.actualDateTime());
					if( autore[0] == null ) {
						autore[0] = ListOfAuthorsFragment.nuovoAutore(null);
					}
					if( header.getSubmitterRef() == null ) {
						header.setSubmitterRef(autore[0].getId());
					}
					String nomeAutoreEditato = editaAutore.getText().toString();
					if( !nomeAutoreEditato.equals(nomeAutore) ) {
						nomeAutore = nomeAutoreEditato;
						autore[0].setName(nomeAutore);
						U.updateChangeDate(autore[0]);
					}
					idAutore = autore[0].getId();
					U.saveJson(gc, treeId); // baypassando la preferenza di non salvare in atomatico

					// Tree accessibility for app developer
					CheckBox accessibleTree = findViewById(R.id.condividi_allow);
					accessible = accessibleTree.isChecked() ? 1 : 0;

					// Invia i dati
					if( !BuildConfig.utenteAruba.isEmpty() )
						new PostaDatiCondivisione().execute( this );
				}
			});
		} else
			findViewById( R.id.condividi_scatola ).setVisibility( View.GONE );
	}

	// The person root of the tree
	View rootView;
	void displayShareRoot() {
		String rootId;
		if( tree.shareRoot != null && gc.getPerson(tree.shareRoot) != null )
			rootId = tree.shareRoot;
		else if( tree.root != null && gc.getPerson(tree.root) != null ) {
			rootId = tree.root;
			tree.shareRoot = rootId; // per poter condividere subito l'albero senza cambiare la radice
		} else {
			rootId = U.trovaRadice(gc);
			tree.shareRoot = rootId;
		}
		Person person = gc.getPerson(rootId);
		if( person != null && tree.grade < 10 ) { // viene mostrata solo alla prima condivisione, non al ritorno
			LinearLayout rootLayout = findViewById(R.id.condividi_radice);
			rootLayout.removeView(rootView);
			rootLayout.setVisibility(View.VISIBLE);
			rootView = U.linkaPersona(rootLayout, person, 1);
			rootView.setOnClickListener(v -> {
				Intent intent = new Intent(this, Principal.class);
				intent.putExtra("anagrafeScegliParente", true);
				startActivityForResult(intent, 5007);
			});
		}
	}

	// Verifica che un campo sia compilato
	boolean controlla(EditText campo, int msg) {
		String testo = campo.getText().toString();
		if( testo.isEmpty() ) {
			campo.requestFocus();
			InputMethodManager imm = (InputMethodManager) getSystemService( Context.INPUT_METHOD_SERVICE);
			imm.showSoftInput(campo, InputMethodManager.SHOW_IMPLICIT);
			Toast.makeText(this, msg, Toast.LENGTH_SHORT ).show();
			return true;
		}
		return false;
	}

	// Inserisce il sommario della condivisione nel database di www.familygem.app
	// Se tutto va bene crea il file zip con l'albero e le immagini
	static class PostaDatiCondivisione extends AsyncTask<SharingActivity,Void, SharingActivity> {
		@Override
		protected SharingActivity doInBackground(SharingActivity... contesti) {
			SharingActivity questo = contesti[0];
			try {
				URL url = new URL("https://www.familygem.app/inserisci.php");
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("POST");
				OutputStream out = new BufferedOutputStream( conn.getOutputStream() );
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
				String dati = "password=" + URLEncoder.encode( BuildConfig.passwordAruba, "UTF-8") +
						"&titoloAlbero=" + URLEncoder.encode( questo.tree.title, "UTF-8") +
						"&nomeAutore=" + URLEncoder.encode( questo.nomeAutore, "UTF-8") +
						"&accessibile=" + questo.accessible;
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
					Settings.Share share = new Settings.Share( questo.dataId, questo.idAutore );
					questo.tree.aggiungiCondivisione(share);
					Global.settings.save();
				}
			} catch( Exception e ) {
				U.toast( questo, e.getLocalizedMessage() );
			}
			return questo;
		}

		@Override
		protected void onPostExecute(SharingActivity questo) {
			if( questo.dataId != null && questo.dataId.startsWith("20") ) {
				File fileTree = new File( questo.getCacheDir(), questo.dataId + ".zip" );
				if( questo.esporter.esportaBackupZip(questo.tree.shareRoot, 9, Uri.fromFile(fileTree)) ) {
					new InvioFTP().execute( questo );
					return;
				} else
					Toast.makeText( questo, questo.esporter.messaggioErrore, Toast.LENGTH_LONG ).show();
			}
			// Un Toast di errore qui sostituirebbe il messaggio di tosta() in catch()
			questo.findViewById( R.id.bottone_condividi ).setEnabled(true);
			questo.findViewById( R.id.condividi_circolo ).setVisibility(View.INVISIBLE);
		}
	}

	// Carica in ftp il file zip con l'albero condiviso
	static class InvioFTP extends AsyncTask<SharingActivity, Void, SharingActivity> {
		protected SharingActivity doInBackground(SharingActivity... contesti) {
			SharingActivity questo = contesti[0];
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
				U.toast( questo, e.getLocalizedMessage() );
			}
			return questo;
		}
		protected void onPostExecute(SharingActivity questo) {
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
		Intent intent = new Intent( Intent.ACTION_SEND );
		intent.setType( "text/plain" );
		intent.putExtra( Intent.EXTRA_SUBJECT, getString( R.string.sharing_tree ) );
		intent.putExtra( Intent.EXTRA_TEXT, getString( R.string.click_this_link,
				"https://www.familygem.app/share.php?tree=" + dataId ) );
		//startActivity( Intent.createChooser( intent, "Condividi con" ) );
		/* Tornando indietro da una app di messaggistica il requestCode 35417 arriva sempre corretto
			Invece il resultCode può essere RESULT_OK o RESULT_CANCELED a capocchia
			Ad esempio da Gmail ritorna indietro sempre con RESULT_CANCELED sia che l'email è stata inviata o no
			anche inviando un Sms ritorna RESULT_CANCELED anche se l'sms è stato inviato
			oppure da Whatsapp è RESULT_OK sia che il messaggio è stato inviato o no
			In pratica non c'è modo di sapere se nella app di messaggistica il messaggio è stato inviato */
		startActivityForResult( Intent.createChooser(intent,getText(R.string.share_with)),35417 );
		findViewById( R.id.bottone_condividi ).setEnabled(true);
		findViewById( R.id.condividi_circolo ).setVisibility( View.INVISIBLE );
	}

	// Aggiorna le preferenze così da mostrare la nuova radice scelta in Anagrafe
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if( resultCode == AppCompatActivity.RESULT_OK ) {
			if( requestCode == 5007 ) {
				tree.shareRoot = data.getStringExtra("idParente");
				Global.settings.save();
				displayShareRoot();
			}
		}
		// Ritorno indietro da qualsiasi app di condivisione, nella quale il messaggio è stato inviato oppure no
		if( requestCode == 35417 ) {
			// Todo chiudi tastiera
			Toast.makeText(getApplicationContext(), R.string.sharing_completed, Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem i ) {
		onBackPressed();
		return true;
	}
}