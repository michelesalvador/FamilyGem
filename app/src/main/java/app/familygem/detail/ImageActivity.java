package app.familygem.detail;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.StrictMode;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import androidx.core.content.FileProvider;
import org.folg.gedcom.model.Media;
import java.io.File;
import java.util.List;
import androidx.multidex.BuildConfig;
import app.familygem.DetailActivity;
import app.familygem.F;
import app.familygem.GalleryFragment;
import app.familygem.Global;
import app.familygem.BlackboardActivity;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import app.familygem.visitor.MediaReferences;
import static app.familygem.Global.gc;

public class ImageActivity extends DetailActivity {

	Media m;
	View imageView;

	@Override
	public void format() {
		m = (Media)cast(Media.class);
		if( m.getId() != null ) {
			setTitle(R.string.shared_media);
			placeSlug("OBJE", m.getId());    // 'O1' for Multimedia Records only//'O1' solo per Multimedia Records
		} else {
			setTitle(R.string.media);
			placeSlug("OBJE", null);
		}
		displayMedia(m, box.getChildCount());
		place(getString(R.string.title), "Title");
		place(getString(R.string.type), "Type", false, false);    // _type
		if( Global.settings.expert ) place(getString(R.string.file), "File");    // 'Angelina Guadagnoli.jpg' visible only to experts //'Angelina Guadagnoli.jpg' visibile solo agli esperti
			// TODO should be max 259 characters
		place(getString(R.string.format), "Format", Global.settings.expert, false);    // jpeg
		place(getString(R.string.primary), "Primary");    // _prim
		place(getString(R.string.scrapbook), "Scrapbook", false, false);    // _scbk the multimedia object should be in the scrapbook
		place(getString(R.string.slideshow), "SlideShow", false, false);    //
		place(getString(R.string.blob), "Blob", false, true);
		//s.l( m.getFileTag() );	// FILE o _FILE
		placeExtensions(m);
		U.placeNotes(box, m, true);
		U.placeChangeDate(box, m.getChange());
		// List of records in which the media is used
		MediaReferences mediaReferences = new MediaReferences(gc, m, false);
		if( mediaReferences.founders.size() > 0 )
			U.putContainer(box, mediaReferences.founders.toArray(), R.string.used_by);
		else if( ((Activity)box.getContext()).getIntent().getBooleanExtra("daSolo", false) )
			U.putContainer(box, Memory.firstObject(), R.string.into);
	}

	void displayMedia(Media m, int position) {
		imageView = LayoutInflater.from(this).inflate(R.layout.immagine_immagine, box, false);
		box.addView(imageView, position);
		ImageView imageView = this.imageView.findViewById(R.id.immagine_foto);
		F.showImage(m, imageView, this.imageView.findViewById(R.id.immagine_circolo));
		this.imageView.setOnClickListener(vista -> {
			String path = (String)imageView.getTag(R.id.tag_percorso);
			Uri uri = (Uri)imageView.getTag(R.id.tag_uri);
			int fileType = (int)imageView.getTag(R.id.tag_tipo_file);
			if( fileType == 0 ) {    // The file is to be found //Il file è da trovare
				F.displayImageCaptureDialog(this, null, 5173, null);
			} else if( fileType == 2 || fileType == 3 ) { // Open files with another app //Apre file con altra app
				// TODO if the type is 3 but it is a url (web page without images) try to open it as a file: //
				if( path != null ) {
					File file = new File(path);
					if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
							&& path.startsWith(getExternalFilesDir(null).getPath()) )
							// An app can be a file provider of only ITS folders
						uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", file);
					else // KitKat and all other folders //KitKat e tutte le altre cartelle
						uri = Uri.fromFile(file);
				}
				String mimeType = getContentResolver().getType(uri);
				Intent intent = new Intent(Intent.ACTION_VIEW);
				intent.setDataAndType(uri, mimeType);
				intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // It is for app properties folders (provider) //Serve per le cartelle di proprietà dell'app (provider)
				List<ResolveInfo> resolvers = getPackageManager().queryIntentActivities(intent, 0);
					// for an extension like .tex that found the mime type, there is no default app //per un'estensione come .tex di cui ha trovato il tipo mime, non c'è nessuna app predefinita
				if( mimeType == null || resolvers.isEmpty() ) {
					intent.setDataAndType(uri, "*/*");    // Brutta lista di app generiche //Brutta lista di app generiche
				}
				// From android 7 (Nougat api 24) uri file: // are banned in favor of uri content: // so it can't open files
				if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) { // ok works in the emulator with Android 9 //ok funziona nell'emulatore con Android 9
					try {
						StrictMode.class.getMethod("disableDeathOnFileUriExposure").invoke(null); //TODO don't use reflection to use functions you shouldn't!
					} catch( Exception e ) {}
				}
				startActivity( intent );
			} else { // Real image //Immagine vera e propria
				Intent intent = new Intent( ImageActivity.this, BlackboardActivity.class );
				intent.putExtra( "path", path );
				if( uri != null )
					intent.putExtra( "uri", uri.toString() );
				startActivity( intent );
			}
		});
		this.imageView.setTag( R.id.tag_oggetto, 43614 /*TODO Magic Number*/);	// for its context menu //per il suo menu contestuale
		registerForContextMenu(this.imageView);
	}

	public void updateImage() {
		int position = box.indexOfChild(imageView);
		box.removeView(imageView);
		displayMedia( m, position );
	}

	@Override
	public void delete() {
		U.updateChangeDate(GalleryFragment.deleteMedia(m, null));
	}
}