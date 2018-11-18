// Attrezzi utili per tutto il programma
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
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.Pair;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.gson.JsonPrimitive;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.ExtensionContainer;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.GedcomTag;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.NoteRef;
import org.folg.gedcom.model.Person;


public class U {
	
	// restituisce l'id della Person iniziale di un Gedcom
	public static String trovaRadice( Gedcom gc ) {
		if( gc.getHeader() != null)
			if( valoreTag( gc.getHeader().getExtensions(), "_ROOT" ) != null )
				return valoreTag( gc.getHeader().getExtensions(), "_ROOT" );
		if( !gc.getPeople().isEmpty() )
				return gc.getPeople().get(0).getId();
		return null;
	}
	
	// riceve una Person e restituisce stringa con nome e cognome principale
	static String epiteto( Person p ) {
		if( !p.getNames().isEmpty() )
			return nomeCognome( p.getNames().get(0) );
		return "";
	}

	// riceve una Person e restituisce il titolo nobiliare
	static String titolo( Person p ) {
		// Gedcom standard INDI.TITL
		for( EventFact ef : p.getEventsFacts() )
			if( ef.getTag() != null )
				if( ef.getTag().equals( "TITL" ) )
					return ef.getValue();
		// Così invece prende INDI.NAME._TYPE.TITL, vecchio metodo di org.folg.gedcom
		for( Name n : p.getNames() )
			if( n.getType() != null )
				if( n.getType().equals( "TITL" ) )
					return  n.getValue();
		return "";
	}

	// Restituisce il nome e cognome addobbato di un Name
	public static String nomeCognome( Name n ) {
		String completo = "";
		String grezzo = n.getValue().trim();
		//s.l( grezzo +  "  indexOf('/') = " + grezzo.indexOf('/') + "  lastindexOf'/' = "+ grezzo.lastIndexOf('/') + "  length() = " + grezzo.length() );
		if( grezzo.indexOf('/') > -1 ) // Se c'è un cognome tra '/'
			completo = grezzo.substring( 0, grezzo.indexOf('/') ).trim();
		if (n.getNickname() != null)
			completo += " \"" + n.getNickname() + "\"";
		if( grezzo.indexOf('/') > -1 ) {
			completo += " " + grezzo.substring( grezzo.indexOf('/') + 1, grezzo.lastIndexOf('/') ).trim();
		}
		if( grezzo.length() - 1 > grezzo.lastIndexOf('/') )
			completo += " " + grezzo.substring( grezzo.lastIndexOf('/') + 1 ).trim();
		if( n.getPrefix() != null )
			completo = n.getPrefix().trim() + " " + completo;
		if( n.getSuffix() != null )
			completo += " " + n.getSuffix().trim();
		return completo.trim();
	}

	// Riceve una Person e restituisce il sesso: 0 ignoto, 1 maschio, 2 femmina
	public static int sesso( Person p ) {
		for( EventFact fatto : p.getEventsFacts() ) {
			if( fatto.getTag().equals( "SEX" ) ) {
				if( fatto.getValue() != null ) { // capita 'SEX' e poi vuoto
					if( fatto.getValue().equals( "M" ) )
						return 1;
					else if( fatto.getValue().equals( "F" ) )
						return 2;
				}
			}
		}
		return 0;
	}

	// Riceve una person e trova se è morto o seppellito
	static boolean morto( Person p ) {
		for( EventFact fatto : p.getEventsFacts() ) {
			if( fatto.getTag().equals( "DEAT" ) || fatto.getTag().equals( "BURI" ) )
				//s.l( p.getNames().get(0).getDisplayValue() +" > "+ fatto.getDisplayType() +": '"+ fatto.getValue() +"'" );
				//if( fatto.getValue().equals("Y") )
				return true;
		}
		return false;
	}



	// riceve una data in stile gedcom e restituisce l'annno semplificato alla mia maniera
	static String soloAnno( String data ) {
		String anno = data.substring( data.lastIndexOf(" ")+1 );	// prende l'anno che sta in fondo alla data
		if( anno.contains("/") )	// gli anni tipo '1711/12' vengono semplificati in '1712'
			anno = anno.substring(0,2).concat( anno.substring(anno.length()-2,anno.length()) );
		if( data.startsWith("ABT") || data.startsWith("EST") || data.startsWith("CAL") )
			anno = anno.concat("?");
		if( data.startsWith("BEF") )
			anno = "←".concat(anno);
		if( data.startsWith("AFT") )
			anno = anno.concat("→");
		if( data.startsWith("BET") ) {
			int pos = data.indexOf("AND") - 1;
			String annoPrima = data.substring( data.lastIndexOf(" ",pos-1)+1, pos );	// prende l'anno che sta prima di 'AND'
			if( !annoPrima.equals(anno) && anno.length()>3 ) {
				s.l( annoPrima +"  " + anno );
				if( annoPrima.substring(0,2).equals(anno.substring(0,2)) )		// se sono dello stesso secolo
					anno = anno.substring( anno.length()-2, anno.length() );	// prende solo gli anni
				anno = annoPrima.concat("~").concat(anno);
			}
		}
		return anno;
	}



	// Costruisce un testo con il contenuto ricorsivo dell'estensione
	public static String scavaEstensione( GedcomTag pacco ) {
		String testo = "";
		//testo += pacco.getTag() +": ";
		if( pacco.getValue() != null )
			testo += pacco.getValue() +"\n";
		else if( pacco.getId() != null )
			testo += pacco.getId() +"\n";
		else if( pacco.getRef() != null )
			testo += pacco.getRef() +"\n";
		for( GedcomTag unPezzo : pacco.getChildren() )
			testo += scavaEstensione( unPezzo );
		return testo;
	}

	public static void eliminaEstensione( GedcomTag estensione, Object contenitore, View vista ) {
		if( contenitore instanceof ExtensionContainer ) { // IndividuoEventi
			@SuppressWarnings("unchecked")
			List<GedcomTag> lista = (List<GedcomTag>) ((ExtensionContainer)contenitore).getExtension( "folg.more_tags" );
			lista.remove( estensione );
		} else if( contenitore instanceof GedcomTag ) { // Dettaglio
			((GedcomTag)contenitore).getChildren().remove( estensione );
		}
		if( vista != null )
			vista.setVisibility( View.GONE );
	}

	// Restituisce il valore di un determinato tag in una estensione (GedcomTag)
	@SuppressWarnings("unchecked")
	static String valoreTag( Map<String,Object> mappaEstensioni, String nomeTag ) {
		for( Map.Entry<String,Object> estensione : mappaEstensioni.entrySet() ) {
			List<GedcomTag> listaTag = (ArrayList<GedcomTag>) estensione.getValue();
			for( GedcomTag unPezzo : listaTag ) {
				//l( unPezzo.getTag() +" "+ unPezzo.getValue() );
				if( unPezzo.getTag().equals( nomeTag ) ) {
					if( unPezzo.getId() != null )
						return unPezzo.getId();
					else if( unPezzo.getRef() != null )
						return unPezzo.getRef();
					else
						return unPezzo.getValue();
				}
			}
		}
		return null;
	}

	// Aggiorna il REF di un tag nelle estensioni di un oggetto:  tag:"_ROOT"  ref:"I123"
	@SuppressWarnings("unchecked")
	static void aggiornaTag( Object obj, String nomeTag, String ref ) {
		String chiave = "gedcomy_tags";
		List<GedcomTag> listaTag = new ArrayList<>();
		boolean aggiungi = true;
		Map<String,Object> mappaEstensioni = ((ExtensionContainer) obj).getExtensions();	// ok
		//s.l( "Map<String,Object> <"+ mappaEstensioni +">" );
		if( !mappaEstensioni.isEmpty() ) {
			chiave = (String) mappaEstensioni.keySet().toArray()[0];	// chiave = 'folg.more_tags'
			listaTag = (ArrayList<GedcomTag>) mappaEstensioni.get( chiave );
			// Aggiorna tag esistente
			for( GedcomTag gct : listaTag ) {
				if( gct.getTag().equals(nomeTag) ) {
					gct.setRef( ref );
					aggiungi = false;
				}
			}
		}
		// Aggiunge nuovo tag
		if( aggiungi ) {
			GedcomTag tag = new GedcomTag( null, nomeTag, null );
			tag.setValue( ref );
			listaTag.add( tag );
		}
		((ExtensionContainer) obj).putExtension( chiave, listaTag );
	}


	// Imposta in un Media il file scelto da file manager. Restituisce l'uri dell'immagine salvata oppure null
	// In app questo metodo è in Dettaglio
	static File settaMedia( Context contesto, Intent data, Media media ) {
		File fileMedia = null;
		try {
			Uri uri = data.getData();
			String percorso = U.uriPercorsoFile( uri );

			if( percorso.lastIndexOf( '/' ) > 0 ) {    // se è un percorso completo del file
				// Apre direttamente il file
				fileMedia = new File( percorso );
			} else {    // è solo il nome del file 'pippo.png'
				// Copia il file (che può essere di qualsiasi tipo) nella memoria esterna della app
				// /mnt/shell/emulated/0/Android/data/lab.gedcomy/files/
				InputStream input = contesto.getContentResolver().openInputStream( uri );
				String percorsoMemoria = contesto.getExternalFilesDir(null) + "/" + Globale.preferenze.idAprendo;
				File dirMemoria = new File( percorsoMemoria );
				if( !dirMemoria.exists() )
					dirMemoria.mkdir();
				fileMedia = new File( percorsoMemoria, percorso );
				FileUtils.copyInputStreamToFile( input, fileMedia );
			}
			// Aggiunge il percorso della cartella nel Cassetto in preferenze
			//Globale.preferenze.alberoAperto().cartelle.add( fileMedia.getParent() );
			//Globale.preferenze.salva();
			media.setFile( fileMedia.getAbsolutePath() );
		} catch( Exception e ) {
			Toast.makeText( contesto, e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
		}
		return fileMedia;
	}


	// Riceve un Uri e cerca di restituire il percorso del file
	public static String uriPercorsoFile( Uri uri ) {
		if( uri.getScheme().equalsIgnoreCase( "file" )) {
			// file:///storage/emulated/0/DCIM/Camera/Simpsons.ged	  da File Manager
			// file:///storage/emulated/0/Android/data/com.dropbox.android/files/u1114176864/scratch/Simpsons.ged
			return uri.getPath();	// gli toglie  file://
		}
		String cosaCercare = OpenableColumns.DISPLAY_NAME;
		// Uri is different in versions after KITKAT (Android 4.4), we need to deal with different Uris
		//s.l( "uri Authority = " + uri.getAuthority() );
		//s.l( "isDocumentUri = " + DocumentsContract.isDocumentUri( Globale.contesto, uri) );	// false solo in G.Drive legacy
		// content://com.google.android.apps.docs.storage.legacy/enc%3DAPsNYqUd_MITZZJxxda1wvQP2ojY7f9xQCAPJoePEFIgSa-5%0A
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT 	// 21 sul mio cellu e 19
				&& DocumentsContract.isDocumentUri( Globale.contesto, uri )	// sempre true tranne con Google drive legacy
				) {
			//s.l( "Document Id = " + DocumentsContract.getDocumentId(uri) );
			switch( uri.getAuthority() ) {
				case "lab.gedcomy.localstorage.documents":	// ????? da testare
					return DocumentsContract.getDocumentId( uri );
				case "com.android.externalstorage.documents":	// memoria interna e scheda SD
					final String docId = DocumentsContract.getDocumentId(uri);
					// semplicemente prende l'ultima parte dell'uri
					// ad esempio 'primary:DCIM/Camera/Simpsons.ged'
					// oppure '3132-6232:famiglia/a_famigliotta_250.ged'
					final String[] split = docId.split(":");

					if( split[0].equalsIgnoreCase("primary")) {
						return Environment.getExternalStorageDirectory() + "/" + split[1];
						// Environment.getExternalStorageDirectory() restituisce sempre /storage/emulated/0 anche per la sd card
					} else {
						File[] luoghi = Globale.contesto.getExternalFilesDirs(null);
						for( File luogo : luoghi ) {
							if( luogo.getAbsolutePath().indexOf("/Android") > 0 ) {
								String dir = luogo.getAbsolutePath().substring(0, luogo.getAbsolutePath().indexOf("/Android"));
								File trovando = new File(dir, split[1]);
								//s.l( trovando );
								// potrebbe capitare che in due schede SD c'è lo stesso percorso e nome file
								// l'utente sceglie il secondo e gli arriva il primo.
								if( trovando.exists() )
									return trovando.getAbsolutePath();
							}
						}
					}
					break;
				case "com.android.providers.downloads.documents":	// file dalla cartella Download
					/* Gli arriva un uri tipo	content://com.android.providers.downloads.documents/document/2326
					   e lo ricostruisce tipo	content://downloads/public_downloads/2326
					   così il cursor può interpretarlo anziché restituire null    */
					final String id = DocumentsContract.getDocumentId(uri);	// un numero tipo '2326'
					uri = ContentUris.withAppendedId( Uri.parse("content://downloads/public_downloads"), Long.valueOf(id) );
					cosaCercare = MediaStore.Files.FileColumns.DATA;
					s.l( "uri ricostruito = " + uri );
				/*case "com.google.android.apps.docs.storage":	// Google drive 1
					// com.google.android.apps.docs.storage.legacy
				}*/
			}
		}
		String nomeFile = trovaNomeFile( uri, cosaCercare );
		if( nomeFile == null )
			nomeFile = trovaNomeFile( uri, OpenableColumns.DISPLAY_NAME );
		return nomeFile;
	}
	// Di default restituisce solo il nome del file 'famiglia.ged'
	// se il file è preso in downloads.documents restituisce il percorso completo
	private static String trovaNomeFile( Uri uri, String cosaCercare ) {
		String[] projection = { cosaCercare };
		Cursor cursore = Globale.contesto.getContentResolver().query( uri, projection, null, null, null);
		if( cursore != null && cursore.moveToFirst() ) {
			//int indice = cursore.getColumnIndexOrThrow( cosaCercare );
			String nomeFile = cursore.getString( 0 );
			cursore.close();
			s.l("cursore = " + nomeFile );
			return nomeFile;
		}
		return null;
	}

	// fa comparire l'immagine in una vista
	public static void mostraMedia( final ImageView vista, final Media med ) {
		vista.getViewTreeObserver().addOnPreDrawListener( new ViewTreeObserver.OnPreDrawListener() {
			public boolean onPreDraw() {
				vista.getViewTreeObserver().removeOnPreDrawListener(this);	// evita il ripetersi di questo metodo
				String percorso = percorsoMedia( med );	// Il file in locale
				s.l( percorso );
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
					/*if( bitmap == null 	// Il file esiste in locale ma senza un'immagine
							|| ( bitmap.getWidth()<10 && bitmap.getHeight()>200 ) ) {	// Giusto per gli strambi mpg e mov
						// Magari è un video
						bitmap = ThumbnailUtils.createVideoThumbnail( percorso,	MediaStore.Video.Thumbnails.MINI_KIND );
						if( bitmap == null ) {
							String formato = med.getFormat();
							if( formato == null )
								formato = MimeTypeMap.getFileExtensionFromUrl( percorso );
							bitmap = generaIcona( vista, R.layout.media_file, formato );
						}
						//vista.setTag( R.id.tag_file_senza_anteprima, true );    // così Immagine può aprire un'app cliccando l'icona
					}*/
					//vista.setTag( R.id.tag_percorso, percorso );    // usato da Immagine.java
					vista.setImageBitmap( bitmap );
				} //else if( med.getFile() != null )	// Cerca il file in internet
					//new U.zuppaMedia( vista ).execute( med.getFile() );
				return true;
			}
		});
	}


	// Riceve un Media, cerca il file in locale con diverse combinazioni di percorso e restituisce l'indirizzo
	public static String percorsoMedia( Media m ) {
		if( m.getFile() != null ) {
			String nome = m.getFile().replace("\\", "/");
			// Percorso FILE (quello nel gedcom)
			if( new File(nome).isFile() )
				return nome;
			String cartella =
					//Globale.preferenze.get( "main_dir", "/storage/external_SD/famiglia") + File.separator;
					//Globale.preferenze.getString( "cartella_principale", null ) + File.separator;
					Globale.preferenze.alberoAperto().cartella + '/';
			// Cartella del .ged + percorso FILE
			String percorsoRicostruito = cartella + nome;
			//s.l( "percorsoRicostruito: " + percorsoRicostruito );
			if( new File(percorsoRicostruito).isFile() )
				return percorsoRicostruito;
			// File nella stessa cartella del gedcom
			String percorsoFile = cartella + new File(nome).getName();
			//s.l( "percorsoFile: " + percorsoFile );
			if( new File(percorsoFile).isFile() )
				return percorsoFile;
		}
		return null;
	}

	// Propone una bella lista di app per acquisire immagini
	public static void appAcquisizioneImmagine( final Context contesto ) {
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
		Intent intentoCamera = new Intent( "android.media.action.IMAGE_CAPTURE" );
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
		dialog.setAdapter( faiAdattatore( contesto, listaRisolvi ),
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						Intent intent = listaIntenti.get(id);
						((AppCompatActivity)contesto).startActivityForResult(intent,14463 );
					}
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
	static void ritagliaImmagine( final Context contesto, Intent data, Media media ) {
		final File fileMedia = U.settaMedia( contesto, data, media );
		String tipoMime = URLConnection.guessContentTypeFromName( fileMedia.getName() );
		if( tipoMime.startsWith("image/") ) {
			ImageView vistaImmagine = new ImageView( contesto );
			//vistaImmagine.setImageURI( data.getData() ); // ok ma l'immagine non è ruotata Exif né ridimensionata
			U.mostraMedia( vistaImmagine, media );
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

	// Conclude la procedura di ritaglio di un'immagine
	static void fineRitaglioImmagine( int resultCode, Intent data ) {
		CropImage.ActivityResult risultato = CropImage.getActivityResult(data);
		if( resultCode == Activity.RESULT_OK ) {
			Uri uri = risultato.getUri();
			Globale.mediaCroppato.setFile( U.uriPercorsoFile( uri ) );
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