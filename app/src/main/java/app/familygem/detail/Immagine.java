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
import app.familygem.BuildConfig;
import app.familygem.Dettaglio;
import app.familygem.F;
import app.familygem.Galleria;
import app.familygem.Global;
import app.familygem.Lavagna;
import app.familygem.Memoria;
import app.familygem.R;
import app.familygem.U;
import app.familygem.visitor.RiferimentiMedia;
import static app.familygem.Global.gc;

public class Immagine extends Dettaglio {

	Media m;
	View vistaMedia;

	@Override
	public void impagina() {
		m = (Media)cast(Media.class);
		if( m.getId() != null ) {
			setTitle(R.string.shared_media);
			placeSlug("OBJE", m.getId());    // 'O1' solo per Multimedia Records
		} else {
			setTitle(R.string.media);
			placeSlug("OBJE", null);
		}
		immaginona(m, box.getChildCount());
		place(getString(R.string.title), "Title");
		place(getString(R.string.type), "Type", false, false);    // _type
		if( Global.settings.expert ) place(getString(R.string.file), "File");    // 'Angelina Guadagnoli.jpg' visibile solo agli esperti
			// todo dovrebbe essere max 259 characters
		place(getString(R.string.format), "Format", Global.settings.expert, false);    // jpeg
		place(getString(R.string.primary), "Primary");    // _prim
		place(getString(R.string.scrapbook), "Scrapbook", false, false);    // _scbk the multimedia object should be in the scrapbook
		place(getString(R.string.slideshow), "SlideShow", false, false);    //
		place(getString(R.string.blob), "Blob", false, true);
		//s.l( m.getFileTag() );	// FILE o _FILE
		placeExtensions(m);
		U.placeNotes(box, m, true);
		U.placeChangeDate(box, m.getChange());
		// Lista dei record in cui è usato il media
		RiferimentiMedia riferiMedia = new RiferimentiMedia(gc, m, false);
		if( riferiMedia.capostipiti.size() > 0 )
			U.mettiDispensa(box, riferiMedia.capostipiti.toArray(), R.string.used_by);
		else if( ((Activity)box.getContext()).getIntent().getBooleanExtra("daSolo", false) )
			U.mettiDispensa(box, Memoria.oggettoCapo(), R.string.into);
	}

	void immaginona(Media m, int posizione) {
		vistaMedia = LayoutInflater.from(this).inflate(R.layout.immagine_immagine, box, false);
		box.addView(vistaMedia, posizione);
		ImageView vistaImg = vistaMedia.findViewById(R.id.immagine_foto);
		F.dipingiMedia(m, vistaImg, vistaMedia.findViewById(R.id.immagine_circolo));
		vistaMedia.setOnClickListener(vista -> {
			String percorso = (String)vistaImg.getTag(R.id.tag_percorso);
			Uri uri = (Uri)vistaImg.getTag(R.id.tag_uri);
			int tipoFile = (int)vistaImg.getTag(R.id.tag_tipo_file);
			if( tipoFile == 0 ) {    // Il file è da trovare
				F.appAcquisizioneImmagine(this, null, 5173, null);
			} else if( tipoFile == 2 || tipoFile == 3 ) { // Apre file con altra app
				// todo se il tipo è 3 ma è un url (pagina web senza immagini) cerca di aprirlo come un file://
				if( percorso != null ) {
					File file = new File(percorso);
					if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
							&& percorso.startsWith(getExternalFilesDir(null).getPath()) )
							// Un'app può essere file provider solo delle SUE cartelle
						uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", file);
					else // KitKat e tutte le altre cartelle
						uri = Uri.fromFile(file);
				}
				String mimeType = getContentResolver().getType(uri);
				Intent intento = new Intent(Intent.ACTION_VIEW);
				intento.setDataAndType(uri, mimeType);
				intento.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Serve per le cartelle di proprietà dell'app (provider)
				List<ResolveInfo> resolvers = getPackageManager().queryIntentActivities(intento, 0);
					// per un'estensione come .tex di cui ha trovato il tipo mime, non c'è nessuna app predefinita
				if( mimeType == null || resolvers.isEmpty() ) {
					intento.setDataAndType(uri, "*/*");    // Brutta lista di app generiche
				}
				// Da android 7 (Nougat api 24) gli uri file:// sono banditi in favore di uri content:// perciò non riesce ad aprire i file
				if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) { // ok funziona nell'emulatore con Android 9
					try {
						StrictMode.class.getMethod("disableDeathOnFileUriExposure").invoke(null);
					} catch( Exception e ) {}
				}
				startActivity( intento );
			} else { // Immagine vera e propria
				Intent intento = new Intent( Immagine.this, Lavagna.class );
				intento.putExtra( "percorso", percorso );
				if( uri != null )
					intento.putExtra( "uri", uri.toString() );
				startActivity( intento );
			}
		});
		vistaMedia.setTag( R.id.tag_oggetto, 43614 );	// per il suo menu contestuale
		registerForContextMenu( vistaMedia );
	}

	public void aggiornaImmagine() {
		int posizione = box.indexOfChild( vistaMedia );
		box.removeView( vistaMedia );
		immaginona( m, posizione );
	}

	@Override
	public void elimina() {
		U.updateChangeDate(Galleria.eliminaMedia(m, null));
	}
}