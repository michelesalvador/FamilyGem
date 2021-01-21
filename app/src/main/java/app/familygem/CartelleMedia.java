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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import java.util.ArrayList;
import java.util.List;

public class CartelleMedia extends AppCompatActivity {

	int idAlbero;
	List<String> cartelle;
	List<String> uris;

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		setContentView( R.layout.cartelle_media );
		idAlbero = getIntent().getIntExtra( "idAlbero", 0 );
		cartelle = new ArrayList<>( Globale.preferenze.getAlbero(idAlbero).cartelle );
		uris = new ArrayList<>( Globale.preferenze.getAlbero(idAlbero).uris );
		aggiornaLista();
		getSupportActionBar().setDisplayHomeAsUpEnabled( true );
		findViewById( R.id.fab ).setOnClickListener( v -> {
			int perm = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
			if( perm == PackageManager.PERMISSION_DENIED )
				ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE }, 3517);
			else if( perm == PackageManager.PERMISSION_GRANTED )
				faiScegliereCartella();
		});
		if( Globale.preferenze.getAlbero(idAlbero).cartelle.isEmpty() && Globale.preferenze.getAlbero(idAlbero).uris.isEmpty() )
			new Fabuloso( this, "Add a folder from this device." ).show(); // todo traduci
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
			if( Globale.preferenze.esperto )
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
			if( Globale.preferenze.esperto ) {
				vistaUrl.setSingleLine( false );
				vistaUrl.setText( stringUri );
			} else
				vistaUrl.setText( Uri.decode(stringUri) ); // lo mostra decodificato cioè un po' più leggibile
			vistaUri.findViewById( R.id.cartella_elimina ).setOnClickListener( v -> {
				new AlertDialog.Builder(this).setMessage( R.string.sure_delete )
						.setPositiveButton( R.string.yes, (di,id) -> {
							// Revoca il permesso per questo uri, se l'uri non è usato in nessun altro albero
							boolean uriEsisteAltrove = false;
							for( Armadio.Cassetto albero : Globale.preferenze.alberi ) {
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
		Globale.preferenze.getAlbero(idAlbero).cartelle.clear();
		for( String path : cartelle )
			Globale.preferenze.getAlbero(idAlbero).cartelle.add( path );
		Globale.preferenze.getAlbero(idAlbero).uris.clear();
		for( String uri : uris )
			Globale.preferenze.getAlbero(idAlbero).uris.add( uri );
		Globale.preferenze.salva();
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
					String percorso = F.uriPercorsoCartellaKitKat( this, uri );
					if( percorso != null ) {
						cartelle.add( percorso );
						salva();
					}
				} else if( requestCode == 123 ) {
					String percorso = F.uriPercorsoCartella( uri );
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
							Toast.makeText( this, "Could not read this position.", Toast.LENGTH_SHORT ).show(); // todo traduci?
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
			U.copiaNegliAppunti( getText(android.R.string.copyUrl), ((TextView)vistaScelta.findViewById(R.id.cartella_url)).getText() );
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