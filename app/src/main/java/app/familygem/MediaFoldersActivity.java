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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity where you can see the list of folders, add, delete
 * */
public class MediaFoldersActivity extends BaseActivity {

	int treeId;
	List<String> folders;
	List<String> uris;

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		setContentView( R.layout.cartelle_media );
		treeId = getIntent().getIntExtra( "idAlbero", 0 );
		folders = new ArrayList<>( Global.settings.getTree(treeId).dirs);
		uris = new ArrayList<>( Global.settings.getTree(treeId).uris );
		updateList();
		getSupportActionBar().setDisplayHomeAsUpEnabled( true );
		findViewById( R.id.fab ).setOnClickListener( v -> {
			int perm = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
			if( perm == PackageManager.PERMISSION_DENIED )
				ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE }, 3517);
			else if( perm == PackageManager.PERMISSION_GRANTED )
				doChooseFolder();
		});
		if( Global.settings.getTree(treeId).dirs.isEmpty() && Global.settings.getTree(treeId).uris.isEmpty() )
			new SpeechBubble( this, R.string.add_device_folder ).show();
	}

	void doChooseFolder() {
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
			Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
			intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
			intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			startActivityForResult( intent, 123 );
		} else {
			// KitKat uses the selection of a file to find the folder
			Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.setType( "*/*");
			startActivityForResult( intent, 456 );
		}
	}

	void updateList() {
		LinearLayout layout = findViewById( R.id.cartelle_scatola );
		layout.removeAllViews();
		for( String cart : folders) {
			View folderView = getLayoutInflater().inflate( R.layout.pezzo_cartella, layout, false );
			layout.addView( folderView );
			TextView nameView = folderView.findViewById( R.id.cartella_nome );
			TextView urlView = folderView.findViewById( R.id.cartella_url );
			urlView.setText( cart );
			if( Global.settings.expert )
				urlView.setSingleLine( false );
			View deleteButton = folderView.findViewById( R.id.cartella_elimina );
			// The '/storage/.../Android/data/app.familygem/files/X' folder should be preserved as it is the default copied media
			// Also in Android 11 it is no longer reachable by the user with SAF
			if( cart.equals(getExternalFilesDir(null) + "/" + treeId) ) {
				nameView.setText( R.string.app_storage );
				deleteButton.setVisibility( View.GONE );
			} else {
				nameView.setText( folderName(cart) );
				deleteButton.setOnClickListener( v -> {
					new AlertDialog.Builder(this).setMessage( R.string.sure_delete )
							.setPositiveButton( R.string.yes, (di,id) -> {
								folders.remove( cart );
								save();
							}).setNeutralButton( R.string.cancel, null ).show();
				});
			}
			registerForContextMenu( folderView );
		}
		for( String stringUri : uris ) {
			View uriView = getLayoutInflater().inflate( R.layout.pezzo_cartella, layout, false );
			layout.addView( uriView );
			DocumentFile documentDir = DocumentFile.fromTreeUri( this, Uri.parse(stringUri) );
			String name = null;
			if( documentDir != null )
				name = documentDir.getName();
			((TextView)uriView.findViewById(R.id.cartella_nome)).setText( name );
			TextView urlView = uriView.findViewById( R.id.cartella_url );
			if( Global.settings.expert ) {
				urlView.setSingleLine( false );
				urlView.setText( stringUri );
			} else
				urlView.setText( Uri.decode(stringUri) ); // lo mostra decodificato cioè un po' più leggibile
			uriView.findViewById( R.id.cartella_elimina ).setOnClickListener( v -> {
				new AlertDialog.Builder(this).setMessage( R.string.sure_delete )
						.setPositiveButton( R.string.yes, (di,id) -> {
							// Revoke permission for this uri, if the uri is not used in any other tree
							boolean uriExistsElsewhere = false;
							for( Settings.Tree tree : Global.settings.trees ) {
								for( String uri : tree.uris )
									if( uri.equals(stringUri) && tree.id != treeId) {
										uriExistsElsewhere = true;
										break;
									}
							}
							if( !uriExistsElsewhere )
								revokeUriPermission( Uri.parse(stringUri), Intent.FLAG_GRANT_READ_URI_PERMISSION );
							uris.remove( stringUri );
							save();
						}).setNeutralButton( R.string.cancel, null ).show();
			});
			registerForContextMenu( uriView );
		}
	}

	String folderName(String url ) {
		if( url.lastIndexOf('/') > 0 )
			return url.substring( url.lastIndexOf('/') + 1 );
		return url;
	}

	void save() {
		Global.settings.getTree(treeId).dirs.clear();
		for( String path : folders)
			Global.settings.getTree(treeId).dirs.add( path );
		Global.settings.getTree(treeId).uris.clear();
		for( String uri : uris )
			Global.settings.getTree(treeId).uris.add( uri );
		Global.settings.save();
		updateList();
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
				// in KitKat a file has been selected and we get the path to the folder
				if( requestCode == 456 ) {
					String path = F.uriPathFolderKitKat( this, uri );
					if( path != null ) {
						folders.add( path );
						save();
					}
				} else if( requestCode == 123 ) {
					String path = F.uriFolderPath( uri );
					if( path != null ) {
						folders.add( path );
						save();
					} else {
						getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
						DocumentFile docDir = DocumentFile.fromTreeUri( this, uri );
						if( docDir != null && docDir.canRead() ) {
							uris.add( uri.toString() );
							save();
						} else
							Toast.makeText( this, "Could not read this position.", Toast.LENGTH_SHORT ).show();
					}
				}
			} else
				Toast.makeText( this, R.string.something_wrong, Toast.LENGTH_SHORT ).show();
		}
	}

	View menu;
	@Override
	public void onCreateContextMenu( ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info ) {
		this.menu = vista;
		menu.add(0, 0, 0, R.string.copy );
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		if( item.getItemId() == 0 ) { // Copy
			U.copyToClipboard( getText(android.R.string.copyUrl), ((TextView) menu.findViewById(R.id.cartella_url)).getText() );
			return true;
		}
		return false;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults ) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && requestCode == 3517)
			doChooseFolder();
	}
}