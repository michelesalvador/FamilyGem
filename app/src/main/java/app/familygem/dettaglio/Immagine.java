package app.familygem.dettaglio;

import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.TextView;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Source;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import app.familygem.Chiesa;
import app.familygem.Dettaglio;
import app.familygem.Galleria;
import app.familygem.Globale;
import app.familygem.Lavagna;
import app.familygem.Ponte;
import app.familygem.R;
import app.familygem.U;
import app.familygem.s;
import static app.familygem.Globale.gc;

public class Immagine extends Dettaglio {

	Media m = (Media) Ponte.ricevi( "oggetto" );

	@Override
	public void impagina() {
		//Media m = gc.getMedia( getIntent().getStringExtra("idMedia") );
		//Media m = Globale.media;
		if( m != null ) {
			/*String tit;
			if( m.getTitle() != null ) tit = m.getTitle();
			else if( m.getFile() != null ) tit = m.getFile();
			else tit = "Media";
			setTitle( tit );*/
			if( m.getId() != null ) {
				setTitle( R.string.media );
				vistaId.setText( m.getId() );    // 'O1' solo per Multimedia Records
			} else {
				setTitle( R.string.local_media );
				vistaId.setText( "OBJE" );
			}
			occorrenze = String.valueOf( Galleria.popolarita( m ) );
			oggetto = m;
			//if( m.getFile() != null )
			immaginona( m );
			metti( getString(R.string.title), "Title" );
			metti( getString(R.string.type), "Type" );	// _type
			metti( getString(R.string.file), "File" );	// Angelina Guadagnoli.jpg
				// dovrebbe essere max 259 characters
			metti( getString(R.string.format), "Format" );	// jpeg
			metti( getString(R.string.primary), "Primary" );	// _prim
			metti( getString(R.string.scrapbook), "Scrapbook", false, false );	// _scbk the multimedia object should be in the scrapbook
			metti( getString(R.string.slideshow), "SlideShow", false, false );	//
			metti( getString(R.string.blob), "Blob", false, true );
			//s.l( m.getFileTag() );	// FILE o _FILE
			mettiEstensioni( m );
			U.mettiNote( box, m, true );
			U.cambiamenti( box, m.getChange() );

			// Lista delle persone e delle fonti in cui è usato il media
			List<Person> listaPersone = new ArrayList<>();
			for( Person p : gc.getPeople() ) {
				for( Media med : p.getAllMedia( gc ) )    // tutti i media in questo individuo
					if( med.equals( m ) )
						listaPersone.add( p );
			}
			List<Source> listaFonti = new ArrayList<>();
			for( Source s : gc.getSources() ) {
				for( Media med : s.getAllMedia( gc ) )    // tutti i media in questa fonte
					if( med.equals( m ) )
						listaFonti.add( s );
			}
			List<Family> listaFamiglie = new ArrayList<>();
			for( Family f : gc.getFamilies() ) {
				for( Media med : f.getAllMedia( gc ) )
					if( med.equals( m ) )
						listaFamiglie.add( f );
			}
			if( !listaPersone.isEmpty() || !listaFonti.isEmpty() || !listaFamiglie.isEmpty() ) {
				TextView usato = new TextView( box.getContext() );
				usato.setText( R.string.used_by );
				usato.setPadding( 0,10,0,0 );
				box.addView( usato );
			}
			for( Person p : listaPersone )
				U.linkaPersona( box, p, 0 );
			for( Source s : listaFonti )
				U.linkaFonte( box, s );
			for( Family f : listaFamiglie )
				Chiesa.mettiFamiglia( box, f );
		}
	}

	void immaginona( final Media m ) {
		//s.l( m.getFile() );
		final View vistaMedia = LayoutInflater.from(box.getContext()).inflate( R.layout.immagine_immagine, box, false );
		box.addView( vistaMedia );
		//String percorso = U.percorsoMedia( m );
		final ImageView vistaImg = vistaMedia.findViewById( R.id.fonte_foto );
		/*if( percorso != null ) {
			Bitmap immagine = BitmapFactory.decodeFile( percorso );
			vistaImg.setImageBitmap( immagine );
		} else
			new U.zuppaMedia( vistaImg ).execute( m.getFile() );*/
		U.mostraMedia( vistaImg, m );
		//((TextView)vistaMedia.findViewById( R.id.fonte_link )).setText( percorso );
		vistaMedia.setOnClickListener( new View.OnClickListener() {
			public void onClick( View vista ) {
				/* Tentativi di rilevare se nell'immagine c'è il manichino o altro drawable
				s.l( vistaImg.getDrawable().getConstantState() +"  =  "+  getResources().getDrawable( R.drawable.manichino ).getConstantState()
				+"  =  "+ ContextCompat.getDrawable( getApplicationContext(), R.drawable.manichino).getConstantState() );
				if( vistaImg.getDrawable().equals( getResources().getDrawable( R.drawable.manichino ) ) ){
				}
				try {
					Field f = ImageView.class.getDeclaredField("mResource");
					f.setAccessible(true);
					int id = (int) f.get(vistaImg);
					s.l( id +" = "+ R.drawable.manichino );
				} catch( NoSuchFieldException e ) {
					e.printStackTrace();
				} catch( IllegalAccessException e ) {
					e.printStackTrace();
				}*/


				String percorso = (String) vistaImg.getTag( R.id.tag_percorso );

				if( percorso == null  ) {	// Il file è da trovare
					Intent intent = new Intent( Intent.ACTION_GET_CONTENT );
					//intent.setType( "image/*" );
					intent.setType( "*/*" );
					startActivityForResult( intent,5173 );
				} else if( vistaImg.getTag( R.id.tag_file_senza_anteprima ) != null ) { // Apri file con altra app

					// Non male anche se la lista di app generiche fa schifo
					File file = new File( percorso );
					MimeTypeMap mappa = MimeTypeMap.getSingleton();
					String estensione = MimeTypeMap.getFileExtensionFromUrl( file.getName() );
					String tipo = mappa.getMimeTypeFromExtension( estensione.toLowerCase() ); // L'estensione deve essere tutta minuscola
						// Di parecchie estensioni come .pic restituisce null
					Intent intento = new Intent( Intent.ACTION_VIEW );
					Uri uri = Uri.fromFile(file);
					intento.setDataAndType( uri, tipo );
					s.l( "<" + estensione + "> \"" + tipo + "\"  " + percorso );
					//intento.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK );	inutile?
					List<ResolveInfo> resolvers = getPackageManager().queryIntentActivities( intento, 0 );
						// per un'estensione come .tex di cui ha trovato il tipo mime, non c'è nessuna app predefinita
					if( tipo == null || resolvers.isEmpty() ) {
						//Toast.makeText( getApplicationContext(), "Nessuna app trovata per aprire " + file.getName(), Toast.LENGTH_LONG ).show();
						//intento.setType( "*/*" ); poi il file non giunge alla app
						intento.setDataAndType( uri, "*/*" );	// La lista di app fa un po' cagare ma vabè
					}
					startActivity( intento );


					/*	Un altro modo per avere lo stesso risultato
					try {
						File file = new File( percorso );
						Intent intento = new Intent(Intent.ACTION_VIEW);
						String tipo = URLConnection.guessContentTypeFromStream( new FileInputStream(file) );
						if( tipo == null)
							tipo = URLConnection.guessContentTypeFromName( file.getName() );
						intento.setDataAndType( Uri.fromFile(file), tipo );
						s.l( "<<<" + file.getName() + ">>> \"" + tipo + "\"  " + percorso );
						startActivity( intento );
					} catch( IOException e ) {
						e.printStackTrace();
					}*/

					// Lista migliore delle app installate
					/*File file = new File( percorso );
					MimeTypeMap mappa = MimeTypeMap.getSingleton();
					String estensione = MimeTypeMap.getFileExtensionFromUrl( file.getName() );
					String tipo = mappa.getMimeTypeFromExtension( estensione.toLowerCase() );
					Intent mainIntent = new Intent( Intent.ACTION_MAIN );	// non trovo altro uso che questi due messi Così
					mainIntent.addCategory( Intent.CATEGORY_LAUNCHER );
					//mainIntent.setDataAndType( Uri.fromFile(file), tipo );	// no l'INTENTO diventa NON GESTIBILE
					startActivity( mainIntent );

					// Tentativo di avviare il chooser che però non parte
					File file = new File( percorso );
					MimeTypeMap mappa = MimeTypeMap.getSingleton();
					String estensione = MimeTypeMap.getFileExtensionFromUrl( file.getName() );
					String tipo = mappa.getMimeTypeFromExtension( estensione.toLowerCase() );
					Intent intento = new Intent( Intent.ACTION_VIEW );
					intento.setDataAndType( Uri.fromFile(file), tipo );
					intento.addFlags( Intent.FLAG_GRANT_READ_URI_PERMISSION );
					Intent chooser = Intent.createChooser( intento, "Open with" );
					if( intento.resolveActivity( getPackageManager() ) != null)
						startActivity(chooser);
					else
						Toast.makeText( getApplicationContext(), "Nessuna app trovata per aprire.",
								Toast.LENGTH_LONG ).show();*/

				} else {
					Globale.media = m;    // potrebbe essere evitato, perchè arrivando in Immagine già è impostato
					startActivity( new Intent( Immagine.this, Lavagna.class ) );
				}


			}
		} );
		//vistaMedia.setTag( R.id.tag_oggetto, "immaginona" );	// per il suo menu contestuale
		vistaMedia.setTag( R.id.tag_oggetto, 43614 );
		registerForContextMenu( vistaMedia );
		// Caricando l'immagine il layout cambia
		box.addOnLayoutChangeListener( new View.OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(View v, int l, int t, int r, int b, int oL, int oT, int oR, int oB) {
				if( b != oB )
					((TextView)vistaMedia.findViewById( R.id.fonte_link )).setText( (String)vistaImg.getTag( R.id.tag_percorso ) );
			}
		});
	}

	/* SPOSTATO IN Dettaglio
	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data ) {
		s.l( "onActivityResult " + requestCode );
		if( resultCode == RESULT_OK && requestCode == 5173 ) {
			try {
				Uri uri = data.getData();
				s.l( "uri= " + uri );
				String percorso = PathUtil.getPath( uri );    // in Google drive trova solo il nome del file
				s.l( "percorso= " + percorso );
				File fileMedia;
				if( percorso.lastIndexOf( '/' ) > 0 ) {    // se è un percorso completo del file
					// Apre direttamente il file
					fileMedia = new File( percorso );
					s.l("percorsoCartella= " + fileMedia.getParent() );
					if( fileMedia.exists() ) {
						Globale.preferenze.alberoAperto().cartella = fileMedia.getParent();
						Globale.preferenze.salva();
					}
				} else {    // è solo il nome del file 'pippo.png'
					// Copia il contenuto in un file temporaneo
					InputStream input = getContentResolver().openInputStream( uri );
					fileMedia = new File( getCacheDir(), "temp.png" );
					FileUtils.copyInputStreamToFile( input, fileMedia );
				}
				m.setFile( fileMedia.getAbsolutePath() );
				U.salvaJson();
			} catch( IOException e ) {	//URISyntaxException | SAXParseException |FileNotFoundException |
				Toast.makeText( Immagine.this, e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
			}
		}
	}*/

	@Override
	public void elimina() {
		//((MediaContainer)contenitore).getAllMedia(gc).remove( m );	// Non fa niente
		if( m.getId() != null ) {	// Object media
			gc.getMedia().remove( m );
			gc.createIndexes();
		} else    // media locali
			((MediaContainer)contenitore).getMedia().remove( m );
	}
}
