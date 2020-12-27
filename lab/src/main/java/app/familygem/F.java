package app.familygem;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.folg.gedcom.model.Media;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

public class F {

	// Riceve un Uri e cerca di restituire il percorso del file
	public static String uriPercorsoFile( Uri uri ) {
		if( uri == null ) return null;
		if( uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("file") ) {
			// file:///storage/emulated/0/DCIM/Camera/Simpsons.ged	  da File Manager
			// file:///storage/emulated/0/Android/data/com.dropbox.android/files/u1114176864/scratch/Simpsons.ged
			return uri.getPath();	// gli toglie  file://
		}
		//s.l( "isDocumentUri = " + DocumentsContract.isDocumentUri( Globale.contesto, uri) );
		// 'isDocumentUri' false solo in Google drive legacy e in app.familygem.provider
		//s.l( "Document Id = " + DocumentsContract.getDocumentId(uri) );
		s.l( "uri Authority = " + uri.getAuthority() );
		switch( uri.getAuthority() ) {
			/* Non più utilizzato (ELIMINABILE)
			case "app.familylab.provider": // Foto scattata con la fotocamera
				// Arriva un uri tipo 'content://app.familygem.provider/files/3/image2.jpg'
				s.l( uri.getPath(), uri.getPathSegments());
				List<String> segmenti = uri.getPathSegments();
				return Globale.contesto.getExternalFilesDir(null) // /storage/emulated/0/Android/data/app.familylab/files
						+ "/" + segmenti.get(1) // il numero dell'albero '3'
						+ "/" + segmenti.get(2); // il nome del file 'image2.jpg' */
			case "com.android.externalstorage.documents":	// memoria interna e scheda SD
				String segment = uri.getLastPathSegment();
				String docId = DocumentsContract.getDocumentId( uri );
				s.l("docId",docId);
				s.l("sgmnt", segment );
				// semplicemente prende l'ultima parte dell'uri
				// ad esempio 'primary:DCIM/Camera/Simpsons.ged'
				// oppure '3132-6232:famiglia/a_famigliotta_250.ged'
				if( segment.equals( docId ))
					s.l("ok");
				else
					throw new IllegalStateException("Allarme docId diverso da segment!");
				String[] split = segment.split(":");
				if( split[0].equalsIgnoreCase("primary") ) {
					// Storage principale
					String percorso = Environment.getExternalStorageDirectory() + "/" + split[1];
					// /storage/emulated/0 ...
					/* Il file può anche esistere ma ciò non è garanzia che si possa leggere.
					In particolare Android 11 con il suo cazzo di scope storage non consente di leggere i file in quasi tutte le cartelle */
					File trovato = new File( percorso );
					if( trovato.exists() ) { // Il file può anche esistere
						s.l( "Esiste", trovato );
						if( trovato.canRead() ) { // ma non essere leggibile (Android 11)
							s.l( "Posso leggere", trovato );
							return percorso;
						} else
							s.l( "Non posso leggere", trovato );
					} else
						s.l( "Non esiste", trovato );
				} else if( split[0].equalsIgnoreCase("home") ) {
					// Cartella 'Documents' in Android 9 e 10
					return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/" + split[1];
				} else {
					// Tutti gli altri casi, tra cui schede SD
					File[] luoghi = Globale.contesto.getExternalFilesDirs(null);
					for( File luogo : luoghi ) {
						if( luogo.getAbsolutePath().indexOf("/Android") > 0 ) {
							String dir = luogo.getAbsolutePath().substring(0, luogo.getAbsolutePath().indexOf("/Android"));
							File trovando = new File( dir, split[1] );
							s.l( "File trovando=", trovando );
							// potrebbe capitare che in due schede SD c'è lo stesso percorso e nome file
							// l'utente sceglie il secondo e gli arriva il primo.
							if( trovando.isFile() ) {
								if( trovando.canRead() )
									return trovando.getAbsolutePath();
								else
									s.l("Esiste ma non è leggibile", trovando);
							}
						}
					}
				}
				break;
			case "com.android.providers.media.documents": // da MediaStore (Immagini, Documenti, Video...)
				// 'content://com.android.providers.media.documents/document/document:73'
				// 'content://com.android.providers.media.documents/document/image:81'
				// C'è poco da ricostruire, bisogna affidarsi al cursore che comunque ho visto trova solo il _display_name
				break;
			case "com.android.providers.downloads.documents":	// file dalla cartella Download
				/* Gli arriva un uri tipo	content://com.android.providers.downloads.documents/document/2326
				   e lo ricostruisce tipo	content://downloads/public_downloads/2326
				   così il cursor può interpretarlo anziché restituire null */
				docId = DocumentsContract.getDocumentId( uri );	// un numero tipo '2326'
				String id = uri.getLastPathSegment();
				s.l( uri, "\n\t" + id, "\n\t" + docId );
				if( !id.equals( docId ))
					throw new IllegalStateException("Allarme docId diverso da id!");
				else
					s.l("ok");
				/* A partire da android 8 l'id può essere un percorso completo tipo 'raw:/storage/emulated/0/Download/miofile.zip'
				   oppure l'uri può avere un id non numerico: 'content://com.android.providers.downloads.documents/document/msf%3A287'
				   nel qual caso l'uri va bene così com'è */
				if( id.startsWith("raw:/") )
					return id.replaceFirst("raw:", "");
				if( id.matches("\\d+") ) { // && Build.VERSION.SDK_INT < Build.VERSION_CODES.P
					// L'idea era che solo negli android prima del 9 l'id numerico andasse ricostruito..
					// Poi ho scoperto che 'my_downloads' funziona nell'emulatore di Android 9 e 10!
					String[] contentUriPrefixesToTry = new String[] {
							"content://downloads/percorso_inesistente", // segnala l'errore e passa oltre
							"content://downloads/public_downloads", // ok percorso classico che ho sempre usato

							/* Aggiunto come percorso alternativo per alcuni device (Samsung)
							Non dà mai errore ma di solito non trova nessun file.
							Però misteriosamente in Android 9 e 10, SOLO IN FAMILY GEM, il cursore con l'uri 'content://downloads/my_downloads/59'
							trova una riga completa di colonna '_data: /storage/emulated/0/Download/the_Simpsons-1.zip'
							Invece nello stesso emulatore Android 10 qui in Family Lab il cursore trova 0 righe, anche se tutto è apparentemente identico!... */
							"content://downloads/my_downloads",

							/* il cursore trova il file con questo percorso, ma dà subito errore:
							requires android.permission.ACCESS_ALL_DOWNLOADS, or grantUriPermission()
							Credo riguardi solo app che vogliono essere provider di download.. */
							"content://downloads/all_downloads"
					};
					for( String contentUriPrefix : contentUriPrefixesToTry ) {
						s.l("URI prefix: ", contentUriPrefix );
						Uri uriRicostruito = ContentUris.withAppendedId( Uri.parse(contentUriPrefix), Long.parseLong(id) );
						try {
							String nomeFile = trovaNomeFile( uriRicostruito );
							if( nomeFile != null )
								return nomeFile;
						} catch( Exception e ) {
							e.printStackTrace();
						}
					}
				}
				/*case "com.google.android.apps.docs.storage":	// Google drive 1
					// com.google.android.apps.docs.storage.legacy
				}*/
		}
		// id non numerico e tutti gli altri casi non previsti sopra
		return trovaNomeFile( uri );
	}

	// Riceve l'URI di un file preso dal file manager
	// Restituisce il percorso completo '/storage/emulated/0/Download/famiglia.ged', oppure il singolo nome del file 'famiglia.ged'
	// Da Android 9 (DI SOLITO) il cursore non ha più la colonna '_data' (percorso completo),
	// ma solo il nome del file nella colonna '_display_name'
	private static String trovaNomeFile( Uri uri ) {
		s.l( "URI: ", uri );
		//String[] projection = { cosaCercare };
		// Se l'uri è sbagliato, query() interrompe con 'java.lang.IllegalArgumentException: Unknown URI: content://downloads/percorso_sbagliato_ai_downloads/1234'
		Cursor cursore = Globale.contesto.getContentResolver().query( uri, null, null, null, null);
		if( cursore != null ) s.l("cursore", cursore, "Cursore righe: " + cursore.getCount(),	" posizione: " + cursore.getPosition());
		else s.l("Cursore null");
		if( cursore != null && cursore.moveToFirst() ) {
			s.l( contenutoCursore(cursore) );
			// Cerca la colonna con il percorso completo del file
			int indice = cursore.getColumnIndex( MediaStore.Files.FileColumns.DATA ); // colonna '_data'
			if( indice < 0 ) // Altrimenti si accontenta del singolo nome del file
				indice = cursore.getColumnIndex( OpenableColumns.DISPLAY_NAME ); // colonna '_display_name'
			s.l( "colonna", cursore.getColumnName(indice) );
			String nomeFile = cursore.getString( indice );
			cursore.close();
			return nomeFile;
		}
		return null;
	}

	static String contenutoCursore( Cursor cursore ) {
		String txt = "Cursore righe: " + cursore.getCount() +
				" posizione: " + cursore.getPosition()  +
				" richiesta: " + cursore.getNotificationUri() +	"\n";
		for( String nomeColonna : cursore.getColumnNames() ) {
			int idColonna = cursore.getColumnIndexOrThrow(nomeColonna);
			//int tipoColonna = cursore.getType(idColonna);
			txt += ".\t\t" + nomeColonna + ": " + cursore.getString(idColonna) + "\n";
		}
		return txt;
	}

	// Riceve un tree Uri ricavato con ACTION_OPEN_DOCUMENT_TREE e cerca di restituire il percorso della cartella
	// altrimenti tranquillamente restituisce null
	public static String uriPercorsoCartella( Uri uri ) {
		if( uri == null ) return null;
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N )
			s.l("isTreeUri", DocumentsContract.isTreeUri(uri)); // min API 24
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ) {
			String treeDocId = DocumentsContract.getTreeDocumentId( uri ); // richiede API 21
			s.l( uri +" -> " + treeDocId);
			// semplicemente prende l'ultima parte dell'uri
			// ad esempio 'home:'
			// oppure 'primary:Pictures/sottocartella'
			switch( uri.getAuthority() ) {
				case "com.android.externalstorage.documents": // memoria interna e scheda SD
					// ad esempio   'content://com.android.externalstorage.documents/tree/home:' (ovvero 'Documents')
					// oppure       'content://com.android.externalstorage.documents/tree/primary:Pictures/sottocartella'
					// scheda SD    'content://com.android.externalstorage.documents/tree/1D0D-2818:Cartella esterna/sottocartella'
					String[] split = treeDocId.split(":");
					s.l( "split", split.length, split[0] );
					String percorso = null;
					// Storage principale
					if( split[0].equalsIgnoreCase("primary") ) {
						percorso = Environment.getExternalStorageDirectory().getAbsolutePath();
					}
					// Documents in Android 9 e 10
					else if( split[0].equalsIgnoreCase("home") ) {
						percorso = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath();
					}
					// Tutti gli altri, tipo la scheda SD
					else {
						File[] filesDirs = Globale.contesto.getExternalFilesDirs(null);
						// /storage/emulated/0/Android/data/app.familylab/files
						// /storage/external_SD/Android/data/app.familylab/files
						//File[] filesDirs = Globale.contesto.getExternalMediaDirs(); // Richiede API 21
						// /storage/emulated/0/Android/media/app.familylab
						// /storage/external_SD/Android/media/app.familylab
						for( File dir : filesDirs ) {
							s.l("dir",dir);
							String altro = dir.getAbsolutePath();
							if( altro.contains(split[0])) {
								percorso = altro.substring( 0, altro.indexOf("/Android") );
								break;
							}
						}
					}
					if( percorso != null ) {
						if( split.length > 1 && !split[1].isEmpty() )
							percorso += "/" + split[1];
						return percorso;
					}
					break;
				case "com.android.providers.downloads.documents": // provider Downloads e sottocartelle
					/* È stata selezionato il provider Downloads che restituisce
					   'content://com.android.providers.downloads.documents/tree/downloads' */
					if( treeDocId.equals("downloads") )
						return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();

					/* A partire da android 8 l'id può essere un percorso completo tipo
					   'raw:/storage/emulated/0/Download/Sottocartella' */
					if( treeDocId.startsWith("raw:/") )
						return treeDocId.replaceFirst("raw:", "");

					/* Oppure raramente arriva un uri tipo 'content://com.android.providers.downloads.documents/tree/msd:271'
					   per il quale sembra non esserci un modo di ricavare un cursor come per i file */
			}
		}
		// Schede SD irraggiungibili e tutti gli altri casi non previsti sopra
		return null;
	}

	// fa comparire l'immagine in una vista
	public static void mostraMedia( final ImageView vista, final Media med ) {
		vista.getViewTreeObserver().addOnPreDrawListener( new ViewTreeObserver.OnPreDrawListener() {
			public boolean onPreDraw() {
				vista.getViewTreeObserver().removeOnPreDrawListener(this);	// evita il ripetersi di questo metodo
				String percorso = percorsoMedia( med );	// Il file in locale
				if( percorso != null ) {
					BitmapFactory.Options opzioni = new BitmapFactory.Options();
					opzioni.inJustDecodeBounds = true;	// solo info
					BitmapFactory.decodeFile( percorso, opzioni );
					int largaOriginale = opzioni.outWidth;
					if( largaOriginale > vista.getWidth() && vista.getWidth() > 0 )
						opzioni.inSampleSize = largaOriginale / vista.getWidth();
					opzioni.inJustDecodeBounds = false;	// carica immagine
					Bitmap bitmap = BitmapFactory.decodeFile( percorso, opzioni );	// Riesce a ricavare un'immagine
					//bitmap = ThumbnailUtils.extractThumbnail( bitmap, 30, 60, ThumbnailUtils.OPTIONS_RECYCLE_INPUT );
					// Fico ma ritaglia l'immagine per farla stare nelle dimensioni date. La quarta opzione non l'ho capita
					try { // Rotazione Exif
						ExifInterface exif = new ExifInterface( percorso );
						int girata = exif.getAttributeInt( ExifInterface.TAG_ORIENTATION, 1 );
						int gradi = 0;
						switch( girata ) {
							//case ExifInterface.ORIENTATION_NORMAL:
							case ExifInterface.ORIENTATION_ROTATE_90:
								gradi = 90;
								break;
							case ExifInterface.ORIENTATION_ROTATE_180:
								gradi = 180;
								break;
							case ExifInterface.ORIENTATION_ROTATE_270:
								gradi = 270;
						}
						if( gradi > 0 ) {
							Matrix matrix = new Matrix();
							matrix.postRotate( gradi );
							bitmap = Bitmap.createBitmap( bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true );
						}
					} catch( Exception e ) {
						Toast.makeText( vista.getContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
						e.printStackTrace();
					}
					if( bitmap == null 	// Il file esiste in locale ma senza un'immagine
							|| ( bitmap.getWidth()<10 && bitmap.getHeight()>200 ) ) {	// Giusto per gli strambi mpg e mov
						// Magari è un video
						bitmap = ThumbnailUtils.createVideoThumbnail( percorso,	MediaStore.Video.Thumbnails.MINI_KIND );
						if( bitmap == null ) {
							String formato = med.getFormat();
							if( formato == null )
								formato = MimeTypeMap.getFileExtensionFromUrl( percorso );
							bitmap = generaIcona( vista, R.layout.media_file, formato );
						}
					}
					vista.setTag( R.id.tag_percorso, percorso );    // usato da Immagine.java
					vista.setImageBitmap( bitmap );
				} //else if( med.getFile() != null )	// Cerca il file in internet
				//new ZuppaMedia( vista ).execute( med.getFile() );
				return true;
			}
		});
	}

	// Caricatore asincrono di immagini locali, sostiusce il metodo mostraMedia()
	static class MostraMedia extends AsyncTask<Media,Void,Bitmap> {
		private ImageView vista;
		boolean dettagli;
		MostraMedia( ImageView vista, boolean dettagli ) {
			this.vista = vista;
			this.dettagli = dettagli;
		}
		@Override
		protected Bitmap doInBackground( Media... params) {
			// I vari getSize restituiscono 0 finché il layout non è completato
			//s.l(vista.getWidth() +"  "+ vista.getHeight() +"  "+ vista.getMeasuredWidth() +"  "+ vista.getMeasuredHeight());
			Media media = params[0];
			String percorso = percorsoMedia( media );
			Bitmap bitmap = null;
			vista.setTag( R.id.tag_tipo_file, 0 );
			if( percorso != null ) {
				BitmapFactory.Options opzioni = new BitmapFactory.Options();
				opzioni.inJustDecodeBounds = true;	// solo info
				BitmapFactory.decodeFile( percorso, opzioni );
				int largaOriginale = opzioni.outWidth;
				if( largaOriginale > vista.getWidth() && vista.getWidth() > 0 )
					opzioni.inSampleSize = largaOriginale / vista.getWidth();
				else if( largaOriginale > 300 ) // 300 una larghezza media approssimativa per una ImageView
					opzioni.inSampleSize = largaOriginale / 300;
				opzioni.inJustDecodeBounds = false;	// carica immagine
				bitmap = BitmapFactory.decodeFile( percorso, opzioni );	// Ricava un'immagine ridimensionata
				//bitmap = Bitmap.createScaledBitmap( bitmap, vista.getWidth(), vista.getHeight(), false ); // mmh no
				try { // Rotazione Exif
					ExifInterface exif = new ExifInterface( percorso );
					int girata = exif.getAttributeInt( ExifInterface.TAG_ORIENTATION, 1 );
					int gradi = 0;
					switch( girata ) {
						//case ExifInterface.ORIENTATION_NORMAL:
						case ExifInterface.ORIENTATION_ROTATE_90:
							gradi = 90;
							break;
						case ExifInterface.ORIENTATION_ROTATE_180:
							gradi = 180;
							break;
						case ExifInterface.ORIENTATION_ROTATE_270:
							gradi = 270;
					}
					if( gradi > 0 ) {
						Matrix matrix = new Matrix();
						matrix.postRotate( gradi );
						bitmap = Bitmap.createBitmap( bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true );
					}
				} catch( Exception e ) {
					Toast.makeText( vista.getContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
				}
				vista.setTag( R.id.tag_tipo_file, 1 );
				if( bitmap == null 	// Il file esiste in locale ma senza un'immagine
						|| ( bitmap.getWidth()<10 && bitmap.getHeight()>200 ) ) {	// Giusto per gli strambi mpg e mov
					// Magari è un video
					bitmap = ThumbnailUtils.createVideoThumbnail( percorso,	MediaStore.Video.Thumbnails.MINI_KIND );
					vista.setTag( R.id.tag_tipo_file, 2 );
					if( bitmap == null ) {
						String formato = media.getFormat();
						if( formato == null )
							formato = MimeTypeMap.getFileExtensionFromUrl( percorso );
						bitmap = generaIcona( vista, R.layout.media_file, formato );
						vista.setTag( R.id.tag_tipo_file, 3 );
					}
				}
				vista.setTag( R.id.tag_percorso, percorso );    // usato da Immagine.java
			} else if( media.getFile() != null )	// Cerca il file in internet
				new ZuppaMedia( vista, null, null ).execute( media.getFile() );
			return bitmap;
		}
		@Override
		protected void onPostExecute( Bitmap bitmap ) {
			//super.onPostExecute(bitmap); ?
			if( bitmap != null ) {
				vista.setImageBitmap( bitmap );
				// Icona di file
				if( vista.getTag(R.id.tag_tipo_file).equals(3) ) {
					vista.setScaleType( ImageView.ScaleType.FIT_CENTER );
					if( dettagli ) {
						//ViewGroup.LayoutParams parami = (RelativeLayout)vista.getLayoutParams(); cazzo non riesco a PRENDERLI
						RelativeLayout.LayoutParams parami = new RelativeLayout.LayoutParams(
								RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT );
						parami.addRule( RelativeLayout.ABOVE, R.id.media_testo );
						vista.setLayoutParams( parami );
					}
				}
			}
		}
	}

	// Mostra le immagini con Picasso
	// La versione in app è più aggiornata
	static void dipingiMedia( final Media media, final ImageView vistaImmagine, final ProgressBar circo ) {
		final String percorso = percorsoMedia(media);
		circo.setVisibility( View.VISIBLE );
		vistaImmagine.setTag( R.id.tag_tipo_file, 0 );
		if( percorso != null ) {
			Picasso.get().load( new File(percorso) )
					/*.resize(300, 300) ok
					.onlyScaleDown()*/
					.fit()  // necessita di aspettare che il layout è creato
					.centerCrop()
					//.placeholder( R.drawable.anna_salvador )
					//.into(vistaImmagine); ok
					.into( vistaImmagine, new Callback() {
						@Override
						public void onSuccess() {
							circo.setVisibility( View.GONE );
							vistaImmagine.setTag( R.id.tag_tipo_file, 1 );
						}
						@Override
						public void onError( Exception e ) {
							// Magari è un video
							Bitmap bitmap = ThumbnailUtils.createVideoThumbnail( percorso,	MediaStore.Video.Thumbnails.MINI_KIND );
							vistaImmagine.setTag( R.id.tag_tipo_file, 2 );
							if( bitmap == null ) {
								// un File locale senza anteprima
								String formato = media.getFormat();
								if( formato == null )
									formato = MimeTypeMap.getFileExtensionFromUrl( percorso );
								bitmap = generaIcona( vistaImmagine, R.layout.media_file, formato );
								vistaImmagine.setScaleType( ImageView.ScaleType.FIT_CENTER );
								if( vistaImmagine.getParent() instanceof RelativeLayout ) {
									RelativeLayout.LayoutParams parami = new RelativeLayout.LayoutParams(
											RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT );
									parami.addRule( RelativeLayout.ABOVE, R.id.media_testo );
									vistaImmagine.setLayoutParams( parami );
								}
								vistaImmagine.setTag( R.id.tag_tipo_file, 3 );
							}
							vistaImmagine.setImageBitmap( bitmap );
							circo.setVisibility( View.GONE );
						}
					});
		} else if( media.getFile() != null ) { // magari è un'immagine in internet
			final String percorsoFile = media.getFile();
			Picasso.get().load(percorsoFile).fit().centerCrop()
					.into(vistaImmagine, new Callback() {
						@Override
						public void onSuccess() {
							circo.setVisibility( View.GONE );
							vistaImmagine.setTag( R.id.tag_tipo_file, 1 );
							try {
								new ImboscaImmagine(media).execute(new URL(percorsoFile));
							} catch( Exception e ) {
								e.printStackTrace();
							}
						}
						@Override
						public void onError( Exception e ) {
							// Proviamo con una pagina web
							new ZuppaMedia( vistaImmagine, circo, media ).execute( percorsoFile );
						}
					});
		} else {
			circo.setVisibility( View.GONE );
			vistaImmagine.setImageResource( R.drawable.anna_salvador );
			vistaImmagine.setAlpha( 0.5f );
		}
	}

	static Bitmap generaIcona( ImageView vista, int icona, String testo ) {
		LayoutInflater inflater = (LayoutInflater) vista.getContext().getSystemService( Context.LAYOUT_INFLATER_SERVICE );
		View inflated = inflater.inflate( icona, null );
		RelativeLayout frameLayout = inflated.findViewById( R.id.icona );
		((TextView)frameLayout.findViewById( R.id.icona_testo ) ).setText( testo );
		frameLayout.setDrawingCacheEnabled( true );
		frameLayout.measure( View.MeasureSpec.makeMeasureSpec( 0, View.MeasureSpec.UNSPECIFIED ),
				View.MeasureSpec.makeMeasureSpec( 0, View.MeasureSpec.UNSPECIFIED ) );
		frameLayout.layout( 0, 0, frameLayout.getMeasuredWidth(), frameLayout.getMeasuredHeight() );
		frameLayout.buildDrawingCache( true );
		return frameLayout.getDrawingCache();
	}

	// Riceve un Media, cerca il file in locale con diverse combinazioni di percorso e restituisce l'indirizzo
	static String percorsoMedia( Media m ) {
		//Globale.preferenze.traghetta();
		if( m.getFile() != null ) {
			String nome = m.getFile().replace("\\", "/");
			// Percorso FILE (quello nel gedcom)
			if( new File(nome).isFile() )
				return nome;
			for( String dir : Globale.preferenze.alberoAperto().cartelle ) {
				// Cartella media + percorso FILE
				String percorsoRicostruito = dir + '/' + nome;
				if( new File(percorsoRicostruito).isFile() )
					return percorsoRicostruito;
				// Cartella media + nome del FILE
				String percorsoFile = dir + '/' + new File(nome).getName();
				if( new File(percorsoFile).isFile() )
					return percorsoFile;
			}
		}
		String percorsoCache = (String) m.getExtension("cache");
		if( percorsoCache != null ) {
			s.l("percorsoCache "+percorsoCache);
			if( new File(percorsoCache).isFile() )
				return percorsoCache;
		}
		return null;
	}

	// Salva in cache un'immagine trovabile in internet per poi riusarla
	static class ImboscaImmagine extends AsyncTask<URL,Void,String> {
		Media media;
		ImboscaImmagine( Media media ) {
			this.media = media;
		}
		protected String doInBackground( URL... url ) {
			s.l( "imboscaImmagine " + url[0].toString() + "  " + FilenameUtils.getName(url[0].getPath())+" "+
					url[0].getFile()+" "+FilenameUtils.getExtension(url[0].getFile()) );
			try {
				File cartellaCache = new File( Globale.contesto.getCacheDir().getPath() + "/" + Globale.preferenze.idAprendo );
				if( !cartellaCache.exists() ) {
					// todo elimina extension "cache" da tutti i Media
					cartellaCache.mkdir();
				}
				String estensione = FilenameUtils.getName( url[0].getPath() );
				if( estensione.lastIndexOf('.') > -1 )
					estensione = estensione.substring( estensione.lastIndexOf('.')+1 );
				String ext;
				switch( estensione ) {
					case "png":
						ext = "png";
						break;
					case "gif":
						ext = "gif";
						break;
					case "bmp":
						ext = "bmp";
						break;
					case "jpg":
					case "jpeg":
					default:
						ext = "jpg";
				}
				File cache = fileNomeProgressivo( cartellaCache.getPath(), "img." + ext );
				s.l("FILE " + cache.getPath() );
				FileUtils.copyURLToFile( url[0], cache );
				return cache.getPath();
				/*try {
					FileOutputStream fos = new FileOutputStream(vistaImmagine.getContext().getCacheDir() + "/" + url.getFile() );

					bitmap.compress( Bitmap.CompressFormat.JPEG, 90, fos );
					fos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}*/
			} catch( Exception e ) {
				e.printStackTrace();
			}
			return null;
		}
		protected void onPostExecute( String percorso) {
			if( percorso != null )
				media.putExtension( "cache", percorso );
		}
	}

	// Carica l'immagine più grande da una pagina internet
	static class ZuppaMedia extends AsyncTask<String, Integer, Bitmap> {
		ImageView vistaImmagine;
		ProgressBar circo;
		Media media;
		URL url;
		ZuppaMedia( ImageView vistaImmagine, ProgressBar circo, Media media ) {
			this.vistaImmagine = vistaImmagine;
			this.circo = circo;
			this.media = media;
		}
		@Override
		protected Bitmap doInBackground(String... params) {
			Bitmap bitmap = null;
			int i = 0;
			publishProgress( i );
			try {
				//Document doc = Jsoup.connect("http://www.antenati.san.beniculturali.it/v/Archivio+di+Stato+di+Firenze/Stato+civile+della+restaurazione/Borgo+San+Lorenzo/Morti/1842/1366/005186556_00447.jpg").get();
				//Document doc = Jsoup.connect("https://www.google.com").get();
				Document doc = Jsoup.connect( params[0] ).get();
				//Element immagine = doc.select("img").first();	// ok
				List<Element> lista = doc.select("img");
				if( lista.isEmpty() ) { // Pagina web trovata ma senza immagini
					vistaImmagine.setTag( R.id.tag_tipo_file, 3 );
					return null;	// dovrebbe ritornare una bitmap
				}
				int maxDimensioniConAlt = 1;
				int maxDimensioni = 1;
				int maxLunghezzaAlt = 0;
				int maxLunghezzaSrc = 0;
				Element imgGrandeConAlt = null;
				Element imgGrande = null;
				Element imgAltLungo = null;
				Element imgSrcLungo = null;
				for( Element img : lista ) {
					s.p( img.attr("src") + " \"" + img.attr("alt") +"\" "+ img.attr("width") + "x" + img.attr("height") );
					int larga, alta;
					if( img.attr("width").isEmpty() ) larga = 1;
					else larga = Integer.parseInt(img.attr("width"));
					if( img.attr("height").isEmpty() ) alta = 1;
					else alta = Integer.parseInt(img.attr("height"));
					s.l( " -> " + larga + "x" + alta );
					// Se in <img> mancano gli attributi "width" e "height", 'larga' e 'alta' rimangono a 1
					if( larga * alta > maxDimensioniConAlt  &&  !img.attr("alt").isEmpty() ) { // la più grande con alt
						imgGrandeConAlt = img;
						maxDimensioniConAlt = larga * alta;
					}
					if( larga * alta > maxDimensioni ) { // la più grande anche senza alt
						imgGrande = img;
						maxDimensioni = larga * alta;
					}
					if( img.attr("alt").length() > maxLunghezzaAlt ) { // quella con l'alt più lungo (ah ah!)
						imgAltLungo = img;
						maxLunghezzaAlt = img.attr( "alt" ).length();
					}
					if( img.attr("src").length() > maxLunghezzaSrc ) { // quella col src più lungo
						imgSrcLungo = img;
						maxLunghezzaSrc = img.attr("src").length();
					}
				}
				String percorso = null;
				if( imgGrandeConAlt != null ) {
					s.l( "imgGrandeConAlt = " + imgGrandeConAlt.attr( "alt" ) + "  " + imgGrandeConAlt.attr( "width" ) + "x" + imgGrandeConAlt.attr( "height" ) );
					percorso = imgGrandeConAlt.absUrl( "src" );  //absolute URL on src
				} else if( imgGrande != null ) {
					s.l( "imgGrande = " + imgGrande.attr("width") + "x" + imgGrande.attr("height") );
					percorso = imgGrande.absUrl( "src" );
				} else if( imgAltLungo != null ) {
					s.l( "imgAltLungo = "+imgAltLungo.attr("alt") +"  "+ imgAltLungo.attr("width") + "x" + imgAltLungo.attr("height") );
					percorso = imgAltLungo.absUrl( "src" );
				} else if( imgSrcLungo != null ) {
					s.l( "imgSrcLungo = "+imgSrcLungo.attr("src") +"  "+ imgSrcLungo.attr("width") + "x" + imgSrcLungo.attr("height") );
					percorso = imgSrcLungo.absUrl( "src" );
				}
				//String srcValue = imageElement.attr("src");  // exact content value of the attribute.
				s.l( "percorso " + percorso );
				url = new URL( percorso );
				InputStream inputStream = url.openConnection().getInputStream();
				bitmap = BitmapFactory.decodeStream(inputStream);
			} catch ( IOException e) {
				e.printStackTrace();
			}
			publishProgress( i );
			return bitmap;
		}
		@Override
		protected void onProgressUpdate(Integer... valori) { // Non dà molta soddisfazione
			//s.l( valori[0] );
		}
		@Override
		protected void onPostExecute( Bitmap bitmap ) {
			//s.l( "RISULTAtO = " + bitmap.getByteCount() );
			if( bitmap != null ) {
				vistaImmagine.setImageBitmap( bitmap );
				vistaImmagine.setTag( R.id.tag_tipo_file, 1 );
				new ImboscaImmagine( media ).execute( url );
			} else
				vistaImmagine.setImageResource( R.drawable.anna_salvador );
			circo.setVisibility( View.GONE );
		}
	}

	// Propone una bella lista di app per acquisire immagini
	public static void appAcquisizioneImmagine( Context contesto ) {
		// Richiesta permesso accesso file in memoria
		int perm = ContextCompat.checkSelfPermission( contesto, Manifest.permission.WRITE_EXTERNAL_STORAGE );
		if( perm == PackageManager.PERMISSION_DENIED ) {
			ActivityCompat.requestPermissions( (AppCompatActivity) contesto, new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE }, 5641 );
			return;
		} //else if( perm == PackageManager.PERMISSION_GRANTED )...
		// Colleziona gli intenti utili per acquisire immagini
		List<ResolveInfo> listaRisolvi = new ArrayList<>();
		final List<Intent> listaIntenti = new ArrayList<>();
		// Camere
		Intent intentoCamera = new Intent( MediaStore.ACTION_IMAGE_CAPTURE );
		for( ResolveInfo info : contesto.getPackageManager().queryIntentActivities( intentoCamera, 0 ) ) {
			Intent finalIntent = new Intent( intentoCamera );
			finalIntent.setComponent( new ComponentName( info.activityInfo.packageName, info.activityInfo.name ) );
			listaIntenti.add( finalIntent );
			listaRisolvi.add( info );
		}
		// Documenti e Gallerie
		Intent intentoGalleria = new Intent( Intent.ACTION_GET_CONTENT );
		intentoGalleria.setType("image/*");
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ) { // da KitKat: Android 4.4, api level 19
			String[] mimeTypes = {"image/*", "audio/*", "video/*", "application/*", "text/*"};
			intentoGalleria.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
		}
		for( ResolveInfo info : contesto.getPackageManager().queryIntentActivities(intentoGalleria,0) ) {
			Intent finalIntent = new Intent( intentoGalleria );
			finalIntent.setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
			listaIntenti.add( finalIntent );
			listaRisolvi.add( info );
		}
		AlertDialog.Builder dialog = new AlertDialog.Builder( contesto );
		dialog.setAdapter( faiAdattatore( contesto, listaRisolvi ), (dialg, id) -> {
			Intent intento = listaIntenti.get(id);
			int codice = 14463;
			s.l( intento.getAction());
			if( intento.getAction() != null && intento.getAction().equals(MediaStore.ACTION_IMAGE_CAPTURE) ) {
				// Crea un file dove sarà salvata la fotografia, direttamente nella sottocartella dell'albero
				// Non è necessario creare il file, ma la cartella deve esistere
				File dir = new File( contesto.getExternalFilesDir(null) + "/" + 3 );
				if( !dir.exists() )
					dir.mkdir();
				File fotoFile = fileNomeProgressivo( dir.getAbsolutePath(), "image.jpg" );
				s.l("fotoFile", fotoFile );
				Globale.fotoCamera = fotoFile.getAbsolutePath();
				Uri fotoUri;
				if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP )
					fotoUri = FileProvider.getUriForFile( contesto, BuildConfig.APPLICATION_ID + ".provider", fotoFile );
				else
					fotoUri = Uri.fromFile( fotoFile ); // In KitKat funziona così, semplice senza provider
				s.l("fotoUri", fotoUri );
				// Passa l'uri del file all'app della fotocamera
				intento.putExtra( MediaStore.EXTRA_OUTPUT, fotoUri );
				//intent.setData( imageUri ); // no rende l'intent invalido
				codice = 14051;
			}
			((AppCompatActivity)contesto).startActivityForResult( intento, codice );
		}).show();
	}
	// Strettamente legato a quello qui sopra
	private static ArrayAdapter<ResolveInfo> faiAdattatore( final Context contesto, final List<ResolveInfo> listaRisolvi) {
		return new ArrayAdapter<ResolveInfo>( contesto, R.layout.pezzo_intento, R.id.intento_titolo, listaRisolvi ){
			@Override
			public View getView(int posizione, View vista, ViewGroup genitore ) {
				View view = super.getView( posizione, vista, genitore );
				ResolveInfo info = listaRisolvi.get( posizione );
				ImageView image = view.findViewById(R.id.intento_icona);
				image.setImageDrawable( info.loadIcon(contesto.getPackageManager()) );
				TextView textview = view.findViewById(R.id.intento_titolo);
				textview.setText( info.loadLabel(contesto.getPackageManager()).toString() );
				return view;
			}
		};
	}

	// Salva il file acquisito e propone di ritagliarlo se è un'immagine
	static void proponiRitaglio( Context contesto, Intent data, Media media ) {
		File fileMedia = settaMedia( contesto, data, media );
		String tipoMime = URLConnection.guessContentTypeFromName( fileMedia.getName() );
		if( tipoMime.startsWith("image/") ) {
			ImageView vistaImmagine = new ImageView( contesto );
			//vistaImmagine.setImageURI( data.getData() ); // ok ma l'immagine non è ruotata Exif né ridimensionata
			mostraMedia( vistaImmagine, media );
			Globale.mediaCroppato = media; // Media in attesa di essere aggiornato col nuovo percorso file
			AlertDialog.Builder costruttore = new AlertDialog.Builder( contesto );
			costruttore.setMessage( "Vuoi ritagliare questa immagine?" )
					.setView(vistaImmagine)
					.setPositiveButton( android.R.string.yes, new DialogInterface.OnClickListener() {
						public void onClick( DialogInterface dialog, int id ) {
							File dirMemoria = new File( contesto.getExternalFilesDir(null) +"/"+ Globale.preferenze.idAprendo );
							if( !dirMemoria.exists() )
								dirMemoria.mkdir();
							File fileDestinazione = new File( dirMemoria.getAbsolutePath(), fileMedia.getName() );
							Intent intento = CropImage.activity( Uri.fromFile(fileMedia) )
									.setOutputUri( Uri.fromFile(fileDestinazione) ) // cartella in memoria esterna
									//.setActivityMenuIconColor(Color.GREEN)
									//.setAutoZoomEnabled( true )
									.setGuidelines( CropImageView.Guidelines.OFF )
									.setBorderLineThickness( 1 )
									.setBorderCornerThickness( 3 )
									//.setAllowRotation(true)
									//.setActivityTitle( "" )
									.setCropMenuCropButtonTitle( "Fatto" )
									//.setCropShape( CropImageView.CropShape.OVAL )
									//.setScaleType( CropImageView.ScaleType.CENTER_INSIDE )
									//.start( (AppCompatActivity)contesto );
									.getIntent( contesto );
							if( 1 > 0 )
								((AppCompatActivity)contesto).startActivityForResult( intento, CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE );
						}
					}).setNegativeButton( android.R.string.no, null )	.create().show();
			FrameLayout.LayoutParams params = new FrameLayout.LayoutParams( FrameLayout.LayoutParams.MATCH_PARENT, 300 );
			vistaImmagine.setLayoutParams( params ); // l'assegnazione delle dimensioni deve venire DOPO la creazione del dialogo
			//vistaImmagine.getLayoutParams().height = 300;
			//vistaImmagine.requestLayout();
		}
	}

	// Imposta in un Media il file scelto da file manager. Restituisce il File dell'immagine salvata oppure null
	static File settaMedia( Context contesto, Intent data, Media media ) {
		s.l( "DATA: "+data);
		if(data!=null)s.l(data.getData()+"\n"+data.getExtras() );
		File fileMedia = null;
		try {
			s.l( "data", data);
			Uri uri = null;
			String percorso = null;
			// Contenuto preso con SAF
			if( data != null && data.getData() != null ) {
				uri = data.getData();
				percorso = uriPercorsoFile( uri );
			} // Foto da app camera
			else if( Globale.fotoCamera != null )
				percorso = Globale.fotoCamera;
			if( percorso != null && percorso.lastIndexOf('/') > 0 ) {    // se è un percorso completo del file
				// Apre direttamente il file
				fileMedia = new File( percorso );
			} else if( uri != null ) {
				// Salva il file nella memoria esterna della app  /mnt/shell/emulated/0/Android/data/lab.gedcomy/files/
				File dirMemoria = new File( contesto.getExternalFilesDir(null) +"/"+ Globale.preferenze.idAprendo );
				if( !dirMemoria.exists() )
					dirMemoria.mkdir();
				// File di qualsiasi tipo
				if( percorso != null ) {    // è solo il nome del file 'pippo.png'
					InputStream input = contesto.getContentResolver().openInputStream( uri );
					fileMedia = fileNomeProgressivo( dirMemoria.getAbsolutePath(), percorso );
					FileUtils.copyInputStreamToFile( input, fileMedia );
					// Prima di usare il FileProvider di solito l'app camera non restituiva l'uri dell'immagine
					// in questi casi volendo c'era l'anteprima della foto negli extra, ma a bassa risoluzione
				} else if( data.getExtras() != null ) {
					Bitmap bitmap = (Bitmap) data.getExtras().get("data");
					fileMedia = fileNomeProgressivo( dirMemoria.getAbsolutePath(), "img.jpg" );
					OutputStream os = new BufferedOutputStream(new FileOutputStream(fileMedia));
					bitmap.compress( Bitmap.CompressFormat.JPEG, 99, os );
					os.close();
				}
			}
			// Aggiunge il percorso della cartella nel Cassetto in preferenze
			//Globale.preferenze.alberoAperto().cartelle.add( fileMedia.getParent() );
			//Globale.preferenze.salva();
			media.setFile( fileMedia.getAbsolutePath() );
		} catch( Exception e ) {
			String msg = e.getLocalizedMessage()!=null ? e.getLocalizedMessage() : "Qualcosa l'è andà stòrt";
			Toast.makeText( contesto, msg, Toast.LENGTH_LONG ).show();
			e.printStackTrace();
		}
		if( fileMedia != null ) s.l( "Percorso: "+fileMedia.getAbsolutePath() );
		else s.l("File è null");
		return fileMedia;
	}

	// Se in percorsoMemoria esiste già un file con quel nome lo incrementa con 1 2 3...
	static File fileNomeProgressivo( String dir, String nome ){
		File file = new File( dir, nome );
		int incremento = 0;
		while( file.exists() ) {
			incremento++;
			file = new File( dir, nome.substring(0,nome.lastIndexOf('.'))
					+ incremento + nome.substring(nome.lastIndexOf('.'),nome.length()));
		}
		return file;
	}

	// Conclude la procedura di ritaglio di un'immagine
	static void fineRitaglioImmagine( int resultCode, Intent data ) {
		CropImage.ActivityResult risultato = CropImage.getActivityResult(data);
		if( resultCode == Activity.RESULT_OK ) {
			Uri uri = risultato.getUri();
			Globale.mediaCroppato.setFile( uriPercorsoFile( uri ) );
			s.l(uri +"  >  "+ Globale.mediaCroppato.getFile() );
		} else if( resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
			s.l( "RRRRRRRRRIIIIIIIIIIIISULTAO  " + risultato.getError() );
		}
	}

	// Risposta a tutte le richieste di permessi
	static void risultatoPermessi( Context contesto, int codice, String[] permessi, int[] accordi ) {
		if( accordi.length > 0 && accordi[0] == PackageManager.PERMISSION_GRANTED ) {
			if( codice == 5641 ) {
				appAcquisizioneImmagine( contesto );
			}
		} else
			Toast.makeText( contesto, permessi[0] + " not granted", Toast.LENGTH_SHORT ).show();
		//for( String perm : permessi ) s.l(perm); c'è solo android.permission.WRITE_EXTERNAL_STORAGE
	}
}
