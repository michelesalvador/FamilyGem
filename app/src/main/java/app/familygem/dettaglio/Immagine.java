package app.familygem.dettaglio;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.StrictMode;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.ProgressBar;
import org.folg.gedcom.model.Media;
import java.io.File;
import java.util.List;
import app.familygem.Dettaglio;
import app.familygem.Galleria;
import app.familygem.Globale;
import app.familygem.Lavagna;
import app.familygem.Memoria;
import app.familygem.R;
import app.familygem.U;
import app.familygem.visita.RiferimentiMedia;
import static app.familygem.Globale.gc;

public class Immagine extends Dettaglio {

	Media m;

	@Override
	public void impagina() {
		m = (Media) casta( Media.class );
		if( m.getId() != null ) {
			setTitle( R.string.shared_media );
			mettiBava( "OBJE", m.getId() );	// 'O1' solo per Multimedia Records
		} else {
			setTitle( R.string.media );
			mettiBava( "OBJE", null );
		}
		immaginona( m );
		metti( getString(R.string.title), "Title" );
		metti( getString(R.string.type), "Type", false, false );	// _type
		if(Globale.preferenze.esperto) metti( getString(R.string.file), "File" );	// Angelina Guadagnoli.jpg
			// dovrebbe essere max 259 characters
		metti( getString(R.string.format), "Format", Globale.preferenze.esperto, false );	// jpeg
		metti( getString(R.string.primary), "Primary" );	// _prim
		metti( getString(R.string.scrapbook), "Scrapbook", false, false );	// _scbk the multimedia object should be in the scrapbook
		metti( getString(R.string.slideshow), "SlideShow", false, false );	//
		metti( getString(R.string.blob), "Blob", false, true );
		//s.l( m.getFileTag() );	// FILE o _FILE
		mettiEstensioni( m );
		U.mettiNote( box, m, true );
		U.cambiamenti( box, m.getChange() );
		// Lista dei record in cui è usato il media
		RiferimentiMedia riferiMedia = new RiferimentiMedia( gc, m, false );
		if( riferiMedia.capostipiti.size() > 0 )
			U.mettiDispensa( box, riferiMedia.capostipiti.toArray(), R.string.used_by );
		else if( ((Activity)box.getContext()).getIntent().getBooleanExtra("daSolo",false) )
			U.mettiDispensa( box, Memoria.oggettoCapo(), R.string.into );
	}

	void immaginona( final Media m ) {
		final View vistaMedia = LayoutInflater.from(this).inflate( R.layout.immagine_immagine, box, false );
		box.addView( vistaMedia );
		final ImageView vistaImg = vistaMedia.findViewById( R.id.immagine_foto );
		U.dipingiMedia( m, vistaImg, (ProgressBar)vistaMedia.findViewById(R.id.immagine_circolo) );
		vistaMedia.setOnClickListener( new View.OnClickListener() {
			public void onClick( View vista ) {
				String percorso = (String) vistaImg.getTag( R.id.tag_percorso );
				int tipoFile = (int)vistaImg.getTag(R.id.tag_tipo_file);
				if( tipoFile == 0 ) {	// Il file è da trovare
					U.appAcquisizioneImmagine( Immagine.this, null, 5173, null );
				} else if( tipoFile == 2 || tipoFile == 3 ) { // Apri file con altra app
					// Non male anche se la lista di app generiche fa schifo
					File file = new File( percorso );
					MimeTypeMap mappa = MimeTypeMap.getSingleton();
					String estensione = MimeTypeMap.getFileExtensionFromUrl( file.getName() );
					String tipo = mappa.getMimeTypeFromExtension( estensione.toLowerCase() ); // L'estensione deve essere tutta minuscola
						// Di parecchie estensioni come .pic restituisce null
					Intent intento = new Intent( Intent.ACTION_VIEW );
					Uri uri = Uri.fromFile(file);
					intento.setDataAndType( uri, tipo );
					//intento.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK );	inutile?
					List<ResolveInfo> resolvers = getPackageManager().queryIntentActivities( intento, 0 );
						// per un'estensione come .tex di cui ha trovato il tipo mime, non c'è nessuna app predefinita
					if( tipo == null || resolvers.isEmpty() ) {
						//Toast.makeText( getApplicationContext(), "Nessuna app trovata per aprire " + file.getName(), Toast.LENGTH_LONG ).show();
						//intento.setType( "*/*" ); poi il file non giunge alla app
						intento.setDataAndType( uri, "*/*" );	// La lista di app fa un po' cagare ma vabè
					}
					// Da android 7 (Nougat api 24) gli uri file:// sono banditi in favore di uri content:// perciò non riesce ad aprire i file
					//intento.addFlags( Intent.FLAG_GRANT_READ_URI_PERMISSION ); // proposta com soluzione ma nell'emulatore non funziona
					if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) { // ok funziona nell'emulatore con Android 9
						try {
							StrictMode.class.getMethod("disableDeathOnFileUriExposure" ).invoke(null);
						} catch( Exception e ) {}
					}
					startActivity( intento );
				} else {
					Intent intento = new Intent( Immagine.this, Lavagna.class );
					intento.putExtra( "percorso", percorso );
					startActivity( intento );
				}
			}
		});
		vistaMedia.setTag( R.id.tag_oggetto, 43614 );	// per il suo menu contestuale
		registerForContextMenu( vistaMedia );
	}

	@Override
	public void elimina() {
		U.aggiornaDate( Galleria.eliminaMedia( m, null ) );
	}
}