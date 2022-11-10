// Activity in cui è possibile vedere la lista delle cartelle, aggiungerne, eliminarne

package app.familygem;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import java.util.ArrayList;
import java.util.List;

public class MediaFolders extends BaseActivity {

	int idAlbero;
	List<String> cartelle;
	List<String> uris;

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		setContentView( R.layout.cartelle_media );
		idAlbero = getIntent().getIntExtra( "idAlbero", 0 );
		cartelle = new ArrayList<>( Global.settings.getTree(idAlbero).dirs);
		uris = new ArrayList<>( Global.settings.getTree(idAlbero).uris );
		aggiornaLista();
		getSupportActionBar().setDisplayHomeAsUpEnabled( true );
		findViewById( R.id.fab ).setOnClickListener( v -> {
			int perm = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
			if( perm == PackageManager.PERMISSION_DENIED )
				ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE }, 3517);
			else if( perm == PackageManager.PERMISSION_GRANTED )
				faiScegliereCartella();
		});
		if( Global.settings.getTree(idAlbero).dirs.isEmpty() && Global.settings.getTree(idAlbero).uris.isEmpty() )
			new SpeechBubble( this, R.string.add_device_folder ).show();
	}

	void faiScegliereCartella() {
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
			Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
			intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
			intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			startActivityForResult( intent, 123 );
		} else {
			// KitKat utilizza la selezione di un file per risalire alla cartella
			Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.setType( "*/*");
			startActivityForResult( intent, 456 );
		}
	}

	void aggiornaLista() {
		LinearLayout scatola = findViewById( R.id.cartelle_scatola );
		scatola.removeAllViews();
		for( String cart : cartelle ) {
			View vistaCartella = getLayoutInflater().inflate( R.layout.pezzo_cartella, scatola, false );
			scatola.addView( vistaCartella );
			TextView vistaNome = vistaCartella.findViewById( R.id.cartella_nome );
			TextView vistaUrl = vistaCartella.findViewById( R.id.cartella_url );
			vistaUrl.setText( cart );
			if( Global.settings.expert )
				vistaUrl.setSingleLine( false );
			View bottoneElimina = vistaCartella.findViewById( R.id.cartella_elimina );
			// La cartella '/storage/.../Android/data/app.familygem/files/X' va preservata inquanto è quella di default dei media copiati
			// Oltretutto in Android 11 non è più raggiungibile dall'utente con SAF
			if( cart.equals(getExternalFilesDir(null) + "/" + idAlbero) ) {
				vistaNome.setText( R.string.app_storage );
				bottoneElimina.setVisibility( View.GONE );
			} else {
				vistaNome.setText( nomeCartella(cart) );
				bottoneElimina.setOnClickListener( v -> {
					new AlertDialog.Builder(this).setMessage( R.string.sure_delete )
							.setPositiveButton( R.string.yes, (di,id) -> {
								cartelle.remove( cart );
								salva();
							}).setNeutralButton( R.string.cancel, null ).show();
				});
			}
			registerForContextMenu( vistaCartella );
		}
		for( String stringUri : uris ) {
			View vistaUri = getLayoutInflater().inflate( R.layout.pezzo_cartella, scatola, false );
			scatola.addView( vistaUri );
			DocumentFile documentDir = DocumentFile.fromTreeUri( this, Uri.parse(stringUri) );
			String nome = null;
			if( documentDir != null )
				nome = documentDir.getName();
			((TextView)vistaUri.findViewById(R.id.cartella_nome)).setText( nome );
			TextView vistaUrl = vistaUri.findViewById( R.id.cartella_url );
			if( Global.settings.expert ) {
				vistaUrl.setSingleLine( false );
				vistaUrl.setText( stringUri );
			} else
				vistaUrl.setText( Uri.decode(stringUri) ); // lo mostra decodificato cioè un po' più leggibile
			vistaUri.findViewById( R.id.cartella_elimina ).setOnClickListener( v -> {
				new AlertDialog.Builder(this).setMessage( R.string.sure_delete )
						.setPositiveButton( R.string.yes, (di,id) -> {
							// Revoca il permesso per questo uri, se l'uri non è usato in nessun altro albero
							boolean uriEsisteAltrove = false;
							for( Settings.Tree albero : Global.settings.trees ) {
								for( String uri : albero.uris )
									if( uri.equals(stringUri) && albero.id != idAlbero ) {
										uriEsisteAltrove = true;
										break;
									}
							}
							if( !uriEsisteAltrove )
								revokeUriPermission( Uri.parse(stringUri), Intent.FLAG_GRANT_READ_URI_PERMISSION );
							uris.remove( stringUri );
							salva();
						}).setNeutralButton( R.string.cancel, null ).show();
			});
			registerForContextMenu( vistaUri );
		}
	}

	String nomeCartella( String url ) {
		if( url.lastIndexOf('/') > 0 )
			return url.substring( url.lastIndexOf('/') + 1 );
		return url;
	}

	void salva() {
		Global.settings.getTree(idAlbero).dirs.clear();
		for( String path : cartelle )
			Global.settings.getTree(idAlbero).dirs.add( path );
		Global.settings.getTree(idAlbero).uris.clear();
		for( String uri : uris )
			Global.settings.getTree(idAlbero).uris.add( uri );
		Global.settings.save();
		aggiornaLista();
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
		onBackPressed();
		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if( resultCode == Activity.RESULT_OK ) {
			Uri uri = data.getData();
			if( uri != null ) {
				// in KitKat è stato selezionato un file e ne ricaviamo il percorso della cartella
				if( requestCode == 456 ) {
					String percorso = F.uriPathFolderKitKat( this, uri );
					if( percorso != null ) {
						cartelle.add( percorso );
						salva();
					}
				} else if( requestCode == 123 ) {
					String percorso = F.uriFolderPath( uri );
					if( percorso != null ) {
						cartelle.add( percorso );
						salva();
					} else {
						getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
						DocumentFile docDir = DocumentFile.fromTreeUri( this, uri );
						if( docDir != null && docDir.canRead() ) {
							uris.add( uri.toString() );
							salva();
						} else
							Toast.makeText( this, "Could not read this position.", Toast.LENGTH_SHORT ).show();
					}
				}
			} else
				Toast.makeText( this, R.string.something_wrong, Toast.LENGTH_SHORT ).show();
		}
	}

	View vistaScelta;
	@Override
	public void onCreateContextMenu( ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info ) {
		vistaScelta = vista;
		menu.add(0, 0, 0, R.string.copy );
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		if( item.getItemId() == 0 ) { // Copia
			U.copyToClipboard( getText(android.R.string.copyUrl), ((TextView)vistaScelta.findViewById(R.id.cartella_url)).getText() );
			return true;
		}
		return false;
	}

	@Override
	public void onRequestPermissionsResult( int codice, String[] permessi, int[] accordi ) {
		if( accordi.length > 0 && accordi[0] == PackageManager.PERMISSION_GRANTED && codice == 3517 )
			faiScegliereCartella();
	}
}