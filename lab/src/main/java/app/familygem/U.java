// Attrezzi utili per tutto il programma
package app.familygem;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.v4.util.Pair;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.gson.JsonPrimitive;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

}