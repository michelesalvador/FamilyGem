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
	Exporter exporter;
	String submitterName;
	int accessible; // 0 = false, 1 = true //TODO why isn't this a boolean?
	String dataId;
	String submitterId;
	boolean uploadSuccessful;

	@Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.condivisione);

		final int treeId = getIntent().getIntExtra("idAlbero", 1);
		tree = Global.settings.getTree(treeId);

		// Title of the tree
		final EditText titleEditText = findViewById(R.id.condividi_titolo);
		titleEditText.setText(tree.title);

		if( tree.grade == 10 )
			((TextView)findViewById( R.id.condividi_tit_autore )).setText( R.string.changes_submitter );

		exporter = new Exporter( this );
		exporter.openTree( treeId );
		gc = Global.gc;
		if( gc != null ) {
			displayShareRoot();
			// Author name
			final Submitter[] submitters = new Submitter[1]; //needs to be final one-element array because it is captured by lambda. See https://stackoverflow.com/questions/34865383/variable-used-in-lambda-expression-should-be-final-or-effectively-final
			// tree in Italy with submitter referenced
			if( tree.grade == 0 && gc.getHeader() != null && gc.getHeader().getSubmitter(gc) != null )
				submitters[0] = gc.getHeader().getSubmitter( gc );
			// in Italy there are authors but none referenced, it takes the last one
			else if( tree.grade == 0 && !gc.getSubmitters().isEmpty() )
				submitters[0] = gc.getSubmitters().get(gc.getSubmitters().size()-1);
			// in Australia there are new authors, take one
			else if( tree.grade == 10 && U.newSubmitter(gc) != null )
				submitters[0] = U.newSubmitter(gc);
			final EditText authorEditText = findViewById(R.id.condividi_autore);
			submitterName = submitters[0] == null ? "" : submitters[0].getName();
			authorEditText.setText(submitterName);

			// Display an alert for the acknowledgment of sharing
			if( !Global.settings.shareAgreement ) {
				new AlertDialog.Builder(this).setTitle(R.string.share_sensitive)
						.setMessage(R.string.aware_upload_server)
						.setPositiveButton(android.R.string.ok, (dialog, id) -> {
							Global.settings.shareAgreement = true;
							Global.settings.save();
						}).setNeutralButton(R.string.remind_later, null).show();
			}

			// Collect share data and post to database
			findViewById( R.id.bottone_condividi ).setOnClickListener( v -> {
				if(uploadSuccessful)
					showLinkSharingChooserDialog();
				else {
					if( isFilledIn(titleEditText, R.string.please_title) || isFilledIn(authorEditText, R.string.please_name) )
						return;

					v.setEnabled(false);
					findViewById(R.id.condividi_circolo).setVisibility(View.VISIBLE);

					// Title of the tree
					String editedTitle = titleEditText.getText().toString();
					if( !tree.title.equals(editedTitle) ) {
						tree.title = editedTitle;
						Global.settings.save();
					}

					// Submitter update
					Header header = gc.getHeader();
					if( header == null ) {
						header = NewTree.createHeader(tree.id + ".json");
						gc.setHeader(header);
					} else
						header.setDateTime(U.actualDateTime());
					if( submitters[0] == null ) {
						submitters[0] = ListOfAuthorsFragment.newAuthor(null);
					}
					if( header.getSubmitterRef() == null ) {
						header.setSubmitterRef(submitters[0].getId());
					}
					String editedAuthorName = authorEditText.getText().toString();
					if( !editedAuthorName.equals(submitterName) ) {
						submitterName = editedAuthorName;
						submitters[0].setName(submitterName);
						U.updateChangeDate(submitters[0]);
					}
					submitterId = submitters[0].getId();
					U.saveJson(gc, treeId); // bypassing the preference not to save automatically

					// Tree accessibility for app developer
					CheckBox accessibleTree = findViewById(R.id.condividi_allow);
					accessible = accessibleTree.isChecked() ? 1 : 0;

					// Submit the data
					if( !BuildConfig.utenteAruba.isEmpty() )
						new PostDataShareAsyncTask().execute( this );
				}
			});
		} else
			findViewById( R.id.condividi_scatola ).setVisibility( View.GONE );
	}

	/**
	 * The person root of the tree
	 */
	View rootView;
	void displayShareRoot() {
		String rootId;
		if( tree.shareRoot != null && gc.getPerson(tree.shareRoot) != null )
			rootId = tree.shareRoot;
		else if( tree.root != null && gc.getPerson(tree.root) != null ) {
			rootId = tree.root;
			tree.shareRoot = rootId; // to be able to share the tree immediately without changing the root
		} else {
			rootId = U.trovaRadice(gc);
			tree.shareRoot = rootId;
		}
		Person person = gc.getPerson(rootId);
		if( person != null && tree.grade < 10 ) { // it is only shown on the first share, not on return
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

	/**
	 * Check that a field is filled in
	 */
	boolean isFilledIn(EditText campo, int msg) {
		String text = campo.getText().toString();
		if( text.isEmpty() ) {
			campo.requestFocus();
			InputMethodManager imm = (InputMethodManager) getSystemService( Context.INPUT_METHOD_SERVICE);
			imm.showSoftInput(campo, InputMethodManager.SHOW_IMPLICIT);
			Toast.makeText(this, msg, Toast.LENGTH_SHORT ).show();
			return true;
		}
		return false;
	}

	/**
	 * Inserts the summary of the share in the database of www.familygem.app
	 * If all goes well create the zip file with the tree and the images
	 */
	static class PostDataShareAsyncTask extends AsyncTask<SharingActivity,Void, SharingActivity> {
		@Override
		protected SharingActivity doInBackground(SharingActivity... contexts) {
			SharingActivity activity = contexts[0];
			try {
				URL url = new URL("https://www.familygem.app/inserisci.php");
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("POST");
				OutputStream out = new BufferedOutputStream( conn.getOutputStream() );
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
				String data = "password=" + URLEncoder.encode( BuildConfig.passwordAruba, "UTF-8") +
						"&titoloAlbero=" + URLEncoder.encode( activity.tree.title, "UTF-8") +
						"&nomeAutore=" + URLEncoder.encode( activity.submitterName, "UTF-8") +
						"&accessibile=" + activity.accessible;
				writer.write( data );
				writer.flush();
				writer.close();
				out.close();

				// Response
				BufferedReader reader = new BufferedReader( new InputStreamReader(conn.getInputStream()) );
				String line1 = reader.readLine();
				reader.close();
				conn.disconnect();
				if( line1.startsWith("20") ) {
					activity.dataId = line1.replaceAll( "[-: ]", "" );
					Settings.Share share = new Settings.Share( activity.dataId, activity.submitterId);
					activity.tree.aggiungiCondivisione(share);
					Global.settings.save();
				}
			} catch( Exception e ) {
				U.toast( activity, e.getLocalizedMessage() );
			}
			return activity;
		}

		@Override
		protected void onPostExecute(SharingActivity activity) {
			if( activity.dataId != null && activity.dataId.startsWith("20") ) {
				File fileTree = new File( activity.getCacheDir(), activity.dataId + ".zip" );
				if( activity.exporter.exportBackupZip(activity.tree.shareRoot, 9, Uri.fromFile(fileTree)) ) {
					new FTPUploadAsyncTask().execute( activity );
					return;
				} else
					Toast.makeText( activity, activity.exporter.errorMessage, Toast.LENGTH_LONG ).show();
			}
			// An error Toast here would replace the toast() message in catch()
			activity.findViewById( R.id.bottone_condividi ).setEnabled(true);
			activity.findViewById( R.id.condividi_circolo ).setVisibility(View.INVISIBLE);
		}
	}

	/**
	 * Upload the zip file with the shared tree by ftp.
	 */
	static class FTPUploadAsyncTask extends AsyncTask<SharingActivity, Void, SharingActivity> {
		protected SharingActivity doInBackground(SharingActivity... contesti) {
			SharingActivity activity = contesti[0];
			try {
				FTPClient ftpClient = new FTPClient();
				ftpClient.connect( "89.46.104.211", 21 );
				ftpClient.enterLocalPassiveMode();
				ftpClient.login( BuildConfig.utenteAruba, BuildConfig.passwordAruba );
				ftpClient.changeWorkingDirectory("/www.familygem.app/condivisi");
				ftpClient.setFileType( FTP.BINARY_FILE_TYPE );
				BufferedInputStream buffIn;
				String zipName = activity.dataId + ".zip";
				buffIn = new BufferedInputStream( new FileInputStream( activity.getCacheDir() + "/" + zipName ) );
				activity.uploadSuccessful = ftpClient.storeFile( zipName, buffIn );
				buffIn.close();
				ftpClient.logout();
				ftpClient.disconnect();
			} catch( Exception e ) {
				U.toast( activity, e.getLocalizedMessage() );
			}
			return activity;
		}
		protected void onPostExecute(SharingActivity activity) {
			if( activity.uploadSuccessful) {
				Toast.makeText( activity, R.string.correctly_uploaded, Toast.LENGTH_SHORT ).show();
				activity.showLinkSharingChooserDialog();
			} else {
				activity.findViewById( R.id.bottone_condividi ).setEnabled(true);
				activity.findViewById( R.id.condividi_circolo ).setVisibility( View.INVISIBLE );
			}
		}
	}

	/**
	 * Show apps to share the link
	 */
	void showLinkSharingChooserDialog() {
		Intent intent = new Intent( Intent.ACTION_SEND );
		intent.setType( "text/plain" );
		intent.putExtra( Intent.EXTRA_SUBJECT, getString( R.string.sharing_tree ) );
		intent.putExtra( Intent.EXTRA_TEXT, getString( R.string.click_this_link,
				"https://www.familygem.app/share.php?tree=" + dataId ) );
		//startActivity( Intent.createChooser( intent, "Condividi con" ) );
		/*
			Coming back from a messaging app the requestCode 35417 always arrives correct
			Instead the resultCode can be RESULT_OK or RESULT_CANCELED at head
			For example from Gmail it always comes back with RESULT_CANCELED whether the email has been sent or not
			also when sending an SMS it returns RESULT_CANCELED even if the SMS has been sent
			or from Whatsapp it is RESULT_OK whether the message was sent or not
			In practice, there is no way to know if the message has been sent in the messaging app
		*/
		startActivityForResult( Intent.createChooser(intent,getText(R.string.share_with)),35417 );
		findViewById( R.id.bottone_condividi ).setEnabled(true);
		findViewById( R.id.condividi_circolo ).setVisibility( View.INVISIBLE );
	}

	/**
	 * Update the preferences so as to show the new root chosen in the ListOfPeopleActivity.
	 * See links in comment in {@link #showLinkSharingChooserDialog()} for meaning of request codes
	 */
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
		// Return from any sharing app, whether the message was sent or not
		if( requestCode == 35417 ) {
			// Todo close keyboard
			Toast.makeText(getApplicationContext(), R.string.sharing_completed, Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem i ) {
		onBackPressed();
		return true;
	}
}