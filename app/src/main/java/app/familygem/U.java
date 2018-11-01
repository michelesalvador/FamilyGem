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
import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.Change;
import org.joda.time.Days;
import org.joda.time.LocalDate;
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
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;
import org.folg.gedcom.parser.JsonParser;
import org.joda.time.Months;
import org.joda.time.Years;
import org.joda.time.format.DateTimeFormat;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import app.familygem.dettaglio.CitazioneFonte;
import app.familygem.dettaglio.Fonte;
import app.familygem.dettaglio.Nota;

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

	// Riceve una Person e restituisce il sesso: 0 senza SEX, 1 Maschio, 2 Femmina, 3 Undefinito, 4 altro
	public static int sesso( Person p ) {
		for( EventFact fatto : p.getEventsFacts() ) {
			if( fatto.getTag()!=null && fatto.getTag().equals("SEX") ) {
				if( fatto.getValue() == null )
					return 4;  // c'è 'SEX' ma il valore è vuoto
				else {
					switch( fatto.getValue() ) {
						case "M": return 1;
						case "F": return 2;
						case "U": return 3;
						default: return 4; // altro valore
					}
				}
			}
		}
		return 0; // SEX non c'è
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

	// riceve una Person e restituisce una stringa con gli anni di nascita e morte 
	static String dueAnni( Person p, boolean conEta ) {
		String anni = "";
		LocalDate nascita = null,
				fine = null;
		for( EventFact unFatto : p.getEventsFacts() ) {
			if( unFatto.getTag() != null && unFatto.getTag().equals("BIRT") && unFatto.getDate() != null ) {
				anni = soloAnno( unFatto.getDate() );
				nascita = data( unFatto.getDate() );
				break;
			}
		}
		for( EventFact unFatto : p.getEventsFacts() ) {
			if( unFatto.getTag() != null && unFatto.getTag().equals("DEAT") && unFatto.getDate() != null ) {
				if( !anni.isEmpty() )
					anni += " – ";
				anni += soloAnno( unFatto.getDate() );
				fine = data( unFatto.getDate() );
				break;
			}
		}
		if( conEta && nascita != null ) {
			if( fine == null && nascita.isBefore(LocalDate.now()) && Years.yearsBetween(nascita,LocalDate.now()).getYears() < 100 ) {
				fine = LocalDate.now();
			}
			if( fine != null ) {
				String misura = "";
				int eta = Years.yearsBetween( nascita, fine ).getYears();
				if( eta < 2 ) {
					eta = Months.monthsBetween( nascita, fine ).getMonths();
					misura = " mesi";
					if( eta < 2 ) {
						eta = Days.daysBetween( nascita, fine ).getDays();
						misura = " giorni";
					}
				}
				//anni += "\n" + nascita.toString() +" "+ fine.toString();
				if( eta >= 0 )
					anni += "  (" + eta + misura + ")";
				else
					anni += "  (?)";
			}

		}
		return anni;
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

	// Riceve una data stringa Gedcom e restituisce una singola LocalDate joda oppure null
	static LocalDate data( String dataGc ) {
		if( dataGc.contains("BEF") || dataGc.contains("AFT") || dataGc.contains("BET")
				|| dataGc.contains("FROM") || dataGc.contains("TO") )	// date incalcolabili
			return null;
		if( dataGc.contains("ABT") || dataGc.contains("EST") || dataGc.contains("CAL") || dataGc.contains("INT") )  // rimuove i tre porcellini
			dataGc = dataGc.substring( dataGc.indexOf(' ')+1, dataGc.length() );
		if( dataGc.contains("(") && dataGc.contains(")") )
			dataGc = dataGc.replaceAll("\\(.*?\\)", "").trim();
		if( dataGc.isEmpty() ) return null;
		String annoStr = dataGc.substring( dataGc.lastIndexOf(" ") + 1 );
		if( annoStr.contains("/") )	// gli anni tipo '1711/12' o '1711/1712' vengono semplificati in '1712'
			annoStr = annoStr.substring(0,2).concat( annoStr.substring(annoStr.length()-2,annoStr.length()) ); // TODO: E l'anno 1799/00 diventa 1700
		int anno = Anagrafe.idNumerico( annoStr );
		if( anno == 0 ) return null;
		int mese = 1;
		if( dataGc.length() > 4 && dataGc.indexOf(' ') > 0 ) {
			try {
				String meseStr = dataGc.substring( dataGc.lastIndexOf( ' ' ) - 3, dataGc.lastIndexOf( ' ' ) );
				mese = DateTimeFormat.forPattern( "MMM" ).withLocale( Locale.ENGLISH ).parseDateTime( meseStr ).getMonthOfYear();
			} catch( Exception e ) { return null; }
		}
		int giorno = 1;
		if( dataGc.length() > 8 ) {
			giorno = Anagrafe.idNumerico( dataGc.substring( 0, dataGc.indexOf( ' ' ) ) );    // estrae i soli numeri
			if( giorno < 1 || giorno > 31)
				giorno = 1;
		}
		//System.out.printf( "%s %s %s\n", giorno, mese, anno);
		LocalDate data = null;
		try {
			data = new LocalDate( anno, mese, giorno ); // ad esempio '29 febbraio 1635' dà errore
		} catch( Exception e ) {
			e.printStackTrace();
		}
		return data;
	}

	/* ELIMINABILE
	static String tuttiTag;
	@Deprecated
	public static String trovaEstensioni( Map<String,Object> mappaEstensioni ) {
		tuttiTag = "";
		for( Map.Entry<String,Object> estensione : mappaEstensioni.entrySet() ) {
			if( estensione.getKey().equals("folg.more_tags") ) {
				@SuppressWarnings("unchecked")
				List<GedcomTag> listaTag = (ArrayList<GedcomTag>) estensione.getValue();
				for (GedcomTag tag : listaTag) {
					scriviTag(tag);
				}
			}
		}
		if( tuttiTag.endsWith( "\n" ) )
			tuttiTag = tuttiTag.substring( 0, tuttiTag.length()-1 );
		return tuttiTag;
	}
	// Costruisce un testo con tutti i tag
	@Deprecated
	static void scriviTag( GedcomTag pacco ) {
		tuttiTag += pacco.getTag() +": ";
		if( pacco.getValue() != null )
			tuttiTag += pacco.getValue() +"\n";
		else if( pacco.getId() != null )
			tuttiTag += pacco.getId() +"\n";
		else if( pacco.getRef() != null )
			tuttiTag += pacco.getRef() +"\n";
		for( GedcomTag unPezzo : pacco.getChildren() )
			scriviTag( unPezzo );
	}*/

	// Restituisce la lista di estensioni
	@SuppressWarnings("unchecked")
	public static List<Estensione> trovaEstensioni( ExtensionContainer contenitore ) {
		if( contenitore.getExtension( "folg.more_tags" ) != null ) {
			List<Estensione> lista = new ArrayList<>();
			for( GedcomTag est : (List<GedcomTag>)contenitore.getExtension("folg.more_tags") ) {
				String testo = scavaEstensione(est);
				if( testo.endsWith("\n") )
					testo = testo.substring( 0, testo.length()-1 );
				lista.add( new Estensione( est.getTag(), testo, est ) );
			}
			return lista;
		}
		return Collections.emptyList();
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

	/* Deprecabile in favore di object.getAllNotes(gc)
	@SuppressWarnings("unchecked")
	static List<Note> trovaNote( Object cosa, Gedcom gc ) {
		List<Note> listaNote = new ArrayList<>();
		try { listaNote = (List<Note>) cosa.getClass().getMethod("getNotes").invoke(cosa);
		} catch (IllegalAccessException|IllegalArgumentException|InvocationTargetException|NoSuchMethodException|SecurityException e)
		{ e.printStackTrace(); }
		for( Note nota : listaNote ) {
			//s.l( nota.getValue() );
			trovaCitazFonti( nota );
		}
		return listaNote;
	}*/

	/* Deprecato in favore di object.getSourceCitations();
	@SuppressWarnings("unchecked")
	static List<SourceCitation> trovaCitazFonti( Object cosa ) {
		List<SourceCitation> listaFonti = null;
		try {
			listaFonti = (List<SourceCitation>) cosa.getClass().getMethod("getSourceCitations").invoke(cosa);
		} catch (NoSuchMethodException|SecurityException|IllegalAccessException|IllegalArgumentException|InvocationTargetException e) 
			{ e.printStackTrace(); }
		/*for( SourceCitation citazione : listaFonti ) {
			if( citazione.getSource(gc) != null )
				s.l( "\t"+ citazione.getSource(gc).getTitle() );
			else
				s.l( "\t"+ citazione.getValue() );	// per SOUR note
		}* /
		return listaFonti;
	}*/

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

	/* Carica asincronicamente una immagine da internet
	static class caricaMedia extends AsyncTask<String, ImageView, Bitmap>{
		ImageView vistaImmagine;
		caricaMedia(ImageView vistaImmagine) {
			this.vistaImmagine = vistaImmagine;
		}
		@Override
		protected Bitmap doInBackground(String... params) {
			Bitmap bitmap = null;
			try {
				URL url = new URL( params[0] );
				InputStream inputStream = url.openConnection().getInputStream();
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inJustDecodeBounds = true;
				BitmapFactory.decodeStream( inputStream, null, options );
				if( options.outHeight*options.outWidth > 90*90 )
					options.inSampleSize = options.outHeight / 90;
				inputStream = url.openConnection().getInputStream();
				options.inJustDecodeBounds = false;
				bitmap = BitmapFactory.decodeStream( inputStream, null, options );
			} catch( IOException e ) {
				e.printStackTrace();
			}
			return bitmap;
		}
		@Override
		protected void onPostExecute(Bitmap bitmap){
			if( bitmap != null ) {
				vistaImmagine.setImageBitmap(bitmap);
			}
		}
	}*/

	// Scarica asincronicamente l'immagine da internet
	static class zuppaMedia extends AsyncTask<String, ImageView, Bitmap> {
		ImageView vistaImmagine;
		URL url;
		zuppaMedia(ImageView vistaImmagine) {
			this.vistaImmagine = vistaImmagine;
		}
		@Override
		protected Bitmap doInBackground(String... params) {
			Bitmap bitmap;
			try {
				// Prima prova con l'url diretto a un'immagine
				url = new URL( params[0] );
				InputStream inputStream = url.openConnection().getInputStream();
				BitmapFactory.Options opzioni = new BitmapFactory.Options();
				opzioni.inJustDecodeBounds = true;	// prende solo le info dell'immagine senza scaricarla
				BitmapFactory.decodeStream( inputStream, null, opzioni );
				//s.l( params[0] +"\n"+ "OPZIONI = "+ opzioni.outMimeType +"  "+ opzioni.outWidth );
				// Se non lo trova cerca l'immagine principale in una pagina internet
				if( opzioni.outWidth == -1 ) {
					Connection connessione = Jsoup.connect(params[0]);
					//if (connessione.equals(bitmap)) {    // TODO: verifica che un address sia associato all'hostname
					Document doc;
					//try {
						doc = connessione.get();
					/*} catch( MalformedURLException | UnknownHostException | SocketTimeoutException e ) {
						System.err.println("MalformedURLException | UnknownHostException | SocketTimeoutException");
						e.printStackTrace(); // I'd rather (re)throw it though.
						vistaImmagine.setRotation( 90 );
						return null;
					}*/
					List<Element> lista = doc.select("img");
					if( lista.isEmpty() ) { // Pagina web trovata ma senza immagini
						//vistaImmagine.setImageResource( 0 );
						//vistaImmagine.setBackgroundResource( R.drawable.icona_mondo );
						/* ELIMINABILE
						LayoutInflater inflater = (LayoutInflater)vistaImmagine.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
						View inflated = inflater.inflate( R.layout.media_mondo, null );
						RelativeLayout frameLayout = inflated.findViewById( R.id.icona) ;
						((TextView)frameLayout.findViewById( R.id.icona_testo )).setText( url.getProtocol() );
						frameLayout.setDrawingCacheEnabled(true);
						frameLayout.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
								View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
						frameLayout.layout(0, 0, frameLayout.getMeasuredWidth(), frameLayout.getMeasuredHeight() );
						frameLayout.buildDrawingCache(true );
						return frameLayout.getDrawingCache();*/
						vistaImmagine.setTag( R.id.tag_file_senza_anteprima, true );	// Usato da Immagine.java
						return generaIcona( vistaImmagine, R.layout.media_mondo, url.getProtocol() );	// ritorna una bitmap
					}
					int maxDimensioniConAlt = 0;
					int maxDimensioni = 0;
					int maxLunghezzaAlt = 0;
					Element imgGrandeConAlt = null;
					Element imgGrande = null;
					Element imgAltLungo = null;
					for( Element img : lista ) {
						//s.l( img.attr("alt") +"  "+ img.attr("width") + "x" + img.attr("height") );
						int larga, alta;
						if (img.attr("width").isEmpty()) larga = 0;
						else larga = Integer.parseInt(img.attr("width"));
						if (img.attr("height").isEmpty()) alta = 0;
						else alta = Integer.parseInt(img.attr("height"));
						if( larga * alta > maxDimensioniConAlt  &&  !img.attr("alt").isEmpty() ) {    // la più grande con alt
							imgGrandeConAlt = img;
							maxDimensioniConAlt = larga * alta;
						}
						if( larga * alta > maxDimensioni ) {    // la più grande anche senza alt
							imgGrande = img;
							maxDimensioni = larga * alta;
						}
						if( img.attr("alt").length() > maxLunghezzaAlt )  {	// quella con l'alt più lungo (ah ah!)
							imgAltLungo = img;
							maxLunghezzaAlt = img.attr( "alt" ).length();
						}
						//s.l( img.attr("alt") +"  "+ larga + "x" + alta );
					}
					String percorso = null;
					if( imgGrandeConAlt != null ) {
						//s.l( "imgGrandeConAlt = " + imgGrandeConAlt.attr( "alt" ) + "  " + imgGrandeConAlt.attr( "width" ) + "x" + imgGrandeConAlt.attr( "height" ) );
						percorso = imgGrandeConAlt.absUrl( "src" );  //absolute URL on src
					} else if( imgGrande != null ) {
						//s.l( "imgGrande = "+imgGrande.attr("alt") +"  "+ imgGrande.attr("width") + "x" + imgGrande.attr("height") );
						percorso = imgGrande.absUrl( "src" );
					} else if( imgAltLungo != null ) {
						//s.l( "imgAltLungo = "+imgAltLungo.attr("alt") +"  "+ imgAltLungo.attr("width") + "x" + imgAltLungo.attr("height") );
						percorso = imgAltLungo.absUrl( "src" );
					}
					//s.l( "absoluteUrl " + percorso );
					url = new URL(percorso);
					inputStream = url.openConnection().getInputStream();
					BitmapFactory.decodeStream(inputStream, null, opzioni);
				}
				// Infine cerca di caricare l'immagine vera e propria ridimensionandola
				if( opzioni.outWidth > vistaImmagine.getWidth() )
					opzioni.inSampleSize = opzioni.outWidth / (vistaImmagine.getWidth()+1);
				inputStream = url.openConnection().getInputStream();
				opzioni.inJustDecodeBounds = false;	// Scarica l'immagine
				bitmap = BitmapFactory.decodeStream( inputStream, null, opzioni );
			} catch( IOException | IllegalArgumentException e ) {
				/*s.l( ">>>>>>>>>>>>>>>>>>>>>>>>>> IOException | IllegalArgumentException : " );
				if( url != null) s.l( ">>>>>>>>>>>>>>>>>> " + url.toString() );
				e.printStackTrace();*/
				return null;
			}
			return bitmap;
		}
		@Override
		protected void onPostExecute( Bitmap bitmap ) {
			//((TextView)findViewById (R.id.myTextView)).setText (result);
			//s.l( "RISULTAtO = " + bitmap.getByteCount() );
			//((ImageView)findViewById(R.id.immagine)).setImageBitmap( bitmap );
			if( bitmap != null ) {
				vistaImmagine.setImageBitmap(bitmap);
				vistaImmagine.setTag( R.id.tag_percorso, url.toString() );	// lo usa Immagine.java
				try { // Abbozzo per salvare l'immagine scaricata da internet in una cache per poi riusarla
					FileOutputStream fos = new FileOutputStream(Globale.contesto.getCacheDir() + "/img.jpg");
					bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
					fos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	// fa comparire l'immagine in una vista
	public static void mostraMedia( final ImageView vista, final Media med ) {
		vista.getViewTreeObserver().addOnPreDrawListener( new ViewTreeObserver.OnPreDrawListener() {
			public boolean onPreDraw() {
				vista.getViewTreeObserver().removeOnPreDrawListener(this);	// evita il ripetersi di questo metodo
				String percorso = U.percorsoMedia( med );	// Il file in locale
				if( percorso != null ) {
					BitmapFactory.Options opzioni = new BitmapFactory.Options();
					opzioni.inJustDecodeBounds = true;	// solo info
					BitmapFactory.decodeFile( percorso, opzioni );
					int largaOriginale = opzioni.outWidth;
					if( largaOriginale > vista.getWidth() && vista.getWidth() > 0 )
						opzioni.inSampleSize = largaOriginale / vista.getWidth();
					//if( opzioni.outHeight > vista.getWidth() )
						//opzioni.inSampleSize = opzioni.outHeight / vista.getWidth();
					opzioni.inJustDecodeBounds = false;	// carica immagine
					Bitmap bitmap = BitmapFactory.decodeFile( percorso, opzioni );	// Riesce a ricavare un'immagine
					//bitmap = ThumbnailUtils.extractThumbnail( bitmap, 30, 60, ThumbnailUtils.OPTIONS_RECYCLE_INPUT );
						// Fico ma ritaglia l'immagine per farla stare nelle dimensioni date. La quarta opzione non l'ho capita
					if( bitmap == null 	// Il file esiste in locale ma senza un'immagine
						|| ( bitmap.getWidth()<10 && bitmap.getHeight()>200 ) ) {	// Giusto per gli strambi mpg e mov
						//vista.setImageResource( R.drawable.anna_salvador );	// ok
						/*vista.setMaxWidth( vista.getHeight() );	// inefficace
						vista.setImageResource( 0 );	// ok nasconde il manichino
						vista.setBackgroundResource( R.drawable.icona_file );*/	// ok
						/* Tentativo di mettere un layout nell'imageView
						//RelativeLayout relativeLayout = new RelativeLayout( Globale.contesto );
						RelativeLayout relativeLayout = (RelativeLayout)vista.getParent();
						LayoutInflater inflater = (LayoutInflater)Globale.contesto.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
						View iconaLayout = inflater.inflate(R.layout.media_icona, relativeLayout, true );
						Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
						Canvas canvas = new Canvas(bitmap);
						relativeLayout.draw(canvas);
						vista.setImageBitmap( bitmap );*/
						// Magari è un video
						bitmap = ThumbnailUtils.createVideoThumbnail( percorso,	MediaStore.Video.Thumbnails.MINI_KIND );
						if( bitmap == null ) {
							String formato = med.getFormat();
							if( formato == null )
								//formato = med.getFile().substring( med.getFile().lastIndexOf( '.' )+1, med.getFile().length() );
								formato = MimeTypeMap.getFileExtensionFromUrl( percorso );
							bitmap = generaIcona( vista, R.layout.media_file, formato );
						}
						vista.setTag( R.id.tag_file_senza_anteprima, true );    // così Immagine può aprire un'app cliccando l'icona
					}
					vista.setTag( R.id.tag_percorso, percorso );    // usato da Immagine.java
					vista.setImageBitmap( bitmap );
					/*s.l( "-----------------------\n"
						+ percorso
						+ "\nopzioni.outMimeType = " + opzioni.outMimeType
						+ "\nbitmap.getWidth() = " + bitmap.getWidth()
						+ "\nbitmap.getHeight() = " + bitmap.getHeight()
						+ "\nopzioni.outWidth = "+ largaOriginale
						+ "\nvista.getWidth() = " + vista.getWidth()
						+ "\nopzioni.inSampleSize = " + opzioni.inSampleSize );*/
				} else if( med.getFile() != null )	// Cerca il file in internet
					new U.zuppaMedia( vista ).execute( med.getFile() );
				return true;
			}
		});
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

	// Riceve una Person e sceglie la foto principale
	static void unaFoto( Person p, ImageView img ) {
		boolean trovatoQualcosa = false;
		for( Media med : p.getAllMedia(Globale.gc) ) {	// Cerca un media contrassegnato Primario Y
			if( med.getPrimary() != null ) {
				if( med.getPrimary().equals( "Y" ) ) {
					mostraMedia( img, med );
					trovatoQualcosa = true;
					break;
				}
			}
		}
		if( !trovatoQualcosa )	// In alternativa restituisce il primo che trova
			for( Media med : p.getAllMedia(Globale.gc) ) {
				mostraMedia( img, med );
				trovatoQualcosa = true;
				break;
			}
		if( !trovatoQualcosa )
			img.setVisibility( View.GONE );
		//img.setImageIcon(R.drawable.defunto);
	}

	 @Deprecated // ?
	static void editaIndividuo( Context contesto, String id, int relazione ) {
		//Globale.individuo = id;
		Intent intento = new Intent( contesto, EditaIndividuo.class );
		intento.putExtra( "idIndividuo", id );
		intento.putExtra( "relazione", relazione );
		//intento.putExtra( "frammento", (Serializable)frammento );
		//Globale.frammentoPrecedente = frammento;
		/*Bundle bundle = new Bundle();
		bundle.putString( "idIndividuo", id );
		bundle.putInt( "relazione", relazione );
		bundle.putSerializable( "frammento", (Serializable)frammento );*/
		contesto.startActivity( intento );
	}

	// aggiunge a un Layout una generica voce titolo-testo
	// TODO: DEPRECARLO prima o poi
	public static void metti( LinearLayout scatola, String tit, String cosa ) {
		View vistaPezzo = LayoutInflater.from(scatola.getContext()).inflate( R.layout.pezzo_fatto, scatola, false );
		scatola.addView( vistaPezzo );
		((TextView)vistaPezzo.findViewById( R.id.fatto_titolo )).setText( tit );
		TextView vistaTesto = vistaPezzo.findViewById( R.id.fatto_testo );
		if( cosa == null ) vistaTesto.setVisibility( View.GONE );
		//else if( cosa.isEmpty() ) vistaTesto.setVisibility( View.GONE );
		else {
			vistaTesto.setText( cosa );
			((TextView)vistaPezzo.findViewById( R.id.fatto_edita )).setText( cosa );
		}
		((Activity)scatola.getContext()).registerForContextMenu( vistaPezzo );
	}

	/*Sostituito da mettiIndividuo
	static void arredaIndi( View scheda, Person persona, String ruolo, String anni, String luoghi ) {
		TextView vistaRuolo = scheda.findViewById( R.id.indi_ruolo );
		if( ruolo.isEmpty() ) vistaRuolo.setVisibility( View.GONE );
		else vistaRuolo.setText( ruolo );
		TextView vistaNome = scheda.findViewById( R.id.indi_nome );
		String nome = epiteto(persona);
		if( nome.isEmpty() ) vistaNome.setVisibility( View.GONE );
		else vistaNome.setText( nome );
		TextView vistaAnni = scheda.findViewById( R.id.indi_dettagli );
		if( anni.isEmpty() ) vistaAnni.setVisibility( View.GONE );
		else vistaAnni.setText( anni );
		TextView vistaLuoghi = scheda.findViewById( R.id.indi_dettagli );
		if( luoghi.isEmpty() ) vistaLuoghi.setVisibility( View.GONE );
		else vistaLuoghi.setText( luoghi );
		U.unaFoto( persona, (ImageView)scheda.findViewById(R.id.indi_foto) );
		if( !U.morto(persona) )
			scheda.findViewById( R.id.indi_lutto ).setVisibility( View.GONE );
	}*/

	static void dettagli( Person tizio, TextView vistaDettagli ) {
		String anni = U.dueAnni( tizio, true );
		String luoghi = Anagrafe.dueLuoghi( tizio );
		if( anni.isEmpty() && luoghi.isEmpty() ) {
			vistaDettagli.setVisibility( View.GONE );
		} else {
			if( ( anni.length() > 10 || luoghi.length() > 20 ) && ( !anni.isEmpty() && !luoghi.isEmpty() ) )
				anni += "\n" + luoghi;
			else
				anni += "   " + luoghi;
			vistaDettagli.setText( anni.trim() );
		}
	}

	public static View mettiIndividuo( LinearLayout scatola, Person persona, String ruolo ) {
		View vistaIndi = LayoutInflater.from(scatola.getContext()).inflate( R.layout.pezzo_individuo, scatola, false);
		scatola.addView( vistaIndi );
		TextView vistaRuolo = vistaIndi.findViewById( R.id.indi_ruolo );
		if( ruolo == null ) vistaRuolo.setVisibility( View.GONE );
		else vistaRuolo.setText( ruolo );
		TextView vistaNome = vistaIndi.findViewById( R.id.indi_nome );
		String nome = epiteto(persona);
		if( nome.isEmpty() && ruolo != null ) vistaNome.setVisibility( View.GONE );
		else vistaNome.setText( nome );
		TextView vistaTitolo = vistaIndi.findViewById(R.id.indi_titolo);
		String titolo = U.titolo( persona );
		if( titolo.isEmpty() ) vistaTitolo.setVisibility( View.GONE );
		else vistaTitolo.setText( titolo );
		dettagli( persona, (TextView) vistaIndi.findViewById( R.id.indi_dettagli ) );
		unaFoto( persona, (ImageView)vistaIndi.findViewById(R.id.indi_foto) );
		if( !U.morto(persona) )
			vistaIndi.findViewById( R.id.indi_lutto ).setVisibility( View.GONE );
		if( U.sesso(persona) == 1 )
			vistaIndi.findViewById(R.id.indi_carta).setBackgroundResource( R.drawable.casella_maschio );
		if( U.sesso(persona) == 2 )
			vistaIndi.findViewById(R.id.indi_carta).setBackgroundResource( R.drawable.casella_femmina );
		vistaIndi.setTag( persona.getId() );
		//registerForContextMenu(vistaIndi);    // non qui, famolo fuori
		return vistaIndi;
	}

	/*@Deprecated
	public static void mettiNota( final LinearLayout scatola, final Note nota, boolean dettagliato ) {
		View vistaNota = LayoutInflater.from(scatola.getContext()).inflate( R.layout.pezzo_nota, scatola, false);
		scatola.addView( vistaNota );
		((TextView)vistaNota.findViewById( R.id.nota_testo )).setText( nota.getValue() );
		int quanteCitaFonti = nota.getSourceCitations().size();
		TextView vistaCitaFonti = vistaNota.findViewById( R.id.nota_fonti );
		if( quanteCitaFonti > 0 && dettagliato ) vistaCitaFonti.setText( String.valueOf(quanteCitaFonti) );
		else vistaCitaFonti.setVisibility( View.GONE );
		if( dettagliato )
			vistaNota.setOnClickListener( new View.OnClickListener() {
				public void onClick( View v ) {
					//Globale.nota = nota;
					//apriDettaglio( scatola.getContext(), Nota.class, nota );
					Ponte.manda( nota );
					scatola.getContext().startActivity( new Intent( scatola.getContext(), Nota.class) );
				}
			} );
	}*/

	// Tutte le note di un oggetto
	public static void mettiNote( final LinearLayout scatola, final Object contenitore, boolean dettagli ) {
		for( final Note nota : ((NoteContainer)contenitore).getAllNotes( Globale.gc ) ) {
			View vistaNota = LayoutInflater.from(scatola.getContext()).inflate( R.layout.pezzo_nota, scatola, false);
			scatola.addView( vistaNota );
			((TextView)vistaNota.findViewById( R.id.nota_testo )).setText( nota.getValue() );
			int quanteCitaFonti = nota.getSourceCitations().size();
			TextView vistaCitaFonti = vistaNota.findViewById( R.id.nota_fonti );
			if( quanteCitaFonti > 0 && dettagli ) vistaCitaFonti.setText( String.valueOf(quanteCitaFonti) );
			else vistaCitaFonti.setVisibility( View.GONE );
			if( dettagli ) {
				vistaNota.setTag( R.id.tag_oggetto, nota );
				vistaNota.setTag( R.id.tag_contenitore, contenitore );	// inutile. da tenere per un'eventuale Quaderno delle note
				//if( scatola.getContext().getClass().getName().equals( "lab.gedcomy.Individuo" ) ) {
				if( scatola.getContext() instanceof Individuo ) { // Fragment individuoEventi
					((AppCompatActivity)scatola.getContext()).getSupportFragmentManager()
							.findFragmentByTag( "android:switcher:" + R.id.schede_persona + ":1" )	// non garantito in futuro
							.registerForContextMenu( vistaNota );
				} else	// ok nelle AppCompatActivity
					((AppCompatActivity)scatola.getContext()).registerForContextMenu( vistaNota );
				vistaNota.setOnClickListener( new View.OnClickListener() {
					public void onClick( View v ) {
						Ponte.manda( nota, "oggetto" );
						Ponte.manda( contenitore, "contenitore" );
						scatola.getContext().startActivity( new Intent( scatola.getContext(), Nota.class ) );
					}
				} );
			}
		}
	}

	public static void scollegaNota( Note nota, Object contenitore, View vista ) {
		s.l("Scollego " + nota + " da " + contenitore );
		List<NoteRef> lista = ((NoteContainer)contenitore).getNoteRefs();
		for( NoteRef ref : lista )
			if( ref.getNote(Globale.gc).equals( nota ) ) {
				lista.remove( ref );
				break;
			}
		((NoteContainer)contenitore).setNoteRefs( lista );
		vista.setVisibility( View.GONE );
	}

	// todo unifica con Quaderno.elimina()
	public static void eliminaNota( Note nota, Object contenitore, View vista ) {
		s.l( "Elimina Media " + "  " + nota + " da " + contenitore );
		if( nota.getId() != null ) {	// nota OBJECT
			s.l( "OBJECT " );
			Globale.gc.getNotes().remove( nota );	// ok
			Globale.gc.createIndexes();	// necessario per farlo scomparire anche dall'oggetto contenitore
		} else	// nota LOCALE
			((NoteContainer)contenitore).getNotes().remove( nota );
		vista.setVisibility( View.GONE );
		//vista.getParent().removeView( vista ); non così
	}


	// Elenca tutti i media di un oggetto contenitore
	public static void mettiMedia( LinearLayout scatola, Object contenitore, boolean dettagli ) {
		//View vista;
		for( Media media : ((MediaContainer)contenitore).getAllMedia( Globale.gc ) )
			Galleria.poniMedia( scatola, contenitore, media, dettagli );
			//if( dettagli )
			//	((AppCompatActivity)scatola.getContext()).registerForContextMenu( vista );
	}

	// Di un oggetto inserisce le citazioni alle fonti
	public static void citaFonti( final LinearLayout scatola, final Object contenitore  ) {
		List<SourceCitation> listaCitaFonti;
		if( contenitore instanceof Note )	// Note non estende SourceCitationContainer
			listaCitaFonti = ( (Note) contenitore ).getSourceCitations();
		else listaCitaFonti = ((SourceCitationContainer)contenitore).getSourceCitations();
		for( final SourceCitation citaz : listaCitaFonti ) {
			View vistaCita = LayoutInflater.from( scatola.getContext() ).inflate( R.layout.pezzo_citazione_fonte, scatola, false );
			scatola.addView( vistaCita );
			if( citaz.getSource(Globale.gc) != null )    // source CITATION
				( (TextView) vistaCita.findViewById( R.id.fonte_titolo ) ).setText( Biblioteca.titoloFonte( citaz.getSource( Globale.gc ) ) );
			else // source NOTE, oppure Citazione di fonte che è stata eliminata
				vistaCita.findViewById( R.id.citazione_fonte ).setVisibility( View.GONE );
			String t = "";
			if( citaz.getValue() != null ) t += citaz.getValue() + "\n";
			if( citaz.getPage() != null ) t += citaz.getPage() + "\n";
			if( citaz.getDate() != null ) t += citaz.getDate() + "\n";
			if( citaz.getText() != null ) t += citaz.getText() + "\n";    // vale sia per sourceNote che per sourceCitation
			TextView vistaTesto = vistaCita.findViewById( R.id.citazione_testo );
			if( t.isEmpty() ) vistaTesto.setVisibility( View.GONE );
			else vistaTesto.setText( t.substring( 0, t.length() - 1 ) );
			// Tutto il resto
			LinearLayout scatolaAltro = vistaCita.findViewById( R.id.citazione_note );
			//for( Note nota : citaz.getAllNotes(gc) )
			mettiNote( scatolaAltro, citaz, false );
			//for( Media med : citaz.getAllMedia( Globale.gc ) ) Galleria.poniMedia( scatolaAltro, med );
			mettiMedia( scatolaAltro, citaz, false );
			vistaCita.setTag( R.id.tag_oggetto, citaz );
			//vistaCita.setTag( R.id.tag_contenitore, contenitore ); inutile
			if( scatola.getContext() instanceof Individuo ) { // Fragment individuoEventi
				( (AppCompatActivity) scatola.getContext() ).getSupportFragmentManager()
						.findFragmentByTag( "android:switcher:" + R.id.schede_persona + ":1" )
						.registerForContextMenu( vistaCita );
			} else	// AppCompatActivity
				((AppCompatActivity)scatola.getContext()).registerForContextMenu( vistaCita );

			vistaCita.setOnClickListener( new View.OnClickListener() {
				public void onClick( View v ) {
					//Globale.citazioneFonte = citaz;
				/*Intent intento = new Intent( scatola.getContext(), CitazioneFonte.class );
				intento.putExtra("o", new Gson().toJson( citaz ) );
				scatola.getContext().startActivity( intento );*/
					//apriDettaglio( scatola.getContext(), CitazioneFonte.class, citaz );
					Ponte.manda( citaz, "oggetto" );
					Ponte.manda( contenitore, "contenitore" );
					scatola.getContext().startActivity( new Intent( scatola.getContext(), CitazioneFonte.class ) );
				}
			} );
		}
	}

	/*@Deprecated
	static void apriDettaglio( Context contesto, Class classe, Object oggetto ) {
		Intent intento = new Intent( contesto, classe );
		intento.putExtra("o", new Gson().toJson( oggetto ) );
		contesto.startActivity( intento );
	}*/

	// usato da dettaglio.CitazioneFonte e dettaglio.Immagine
	public static void linkaFonte( final LinearLayout scatola, final Source fonte ) {
		View vistaFonte = LayoutInflater.from(scatola.getContext()).inflate( R.layout.pezzo_fonte, scatola, false );
		scatola.addView( vistaFonte );
		((TextView)vistaFonte.findViewById( R.id.fonte_titolo )).setText( Biblioteca.titoloFonte(fonte) );
		vistaFonte.setTag( R.id.tag_oggetto, fonte );
		//vistaFonte.setTag( R.id.tag_contenitore, contenitore );
		((AppCompatActivity)scatola.getContext()).registerForContextMenu( vistaFonte );
		vistaFonte.setOnClickListener( new View.OnClickListener() {
			public void onClick( View v ) {
				//Intent intento = new Intent( scatola.getContext(), Archivio.class);
				//intento.putExtra( "oggetto", "Source" );
				//intento.putExtra( "id", f.getId() );
				//scatola.getContext().startActivity( intento );
				Ponte.manda( fonte, "oggetto" );
				//Ponte.manda( Globale.gc, "contenitore" );
				scatola.getContext().startActivity( new Intent( scatola.getContext(), Fonte.class) );
			}
		} );
	}

	public static void linkaPersona( final LinearLayout scatola, final Person p, final int scheda ) {
		View vistaPersona = LayoutInflater.from(scatola.getContext()).inflate( R.layout.pezzo_individuo_piccolo, scatola, false );
		scatola.addView( vistaPersona );
		U.unaFoto( p, (ImageView)vistaPersona.findViewById(R.id.collega_foto) );
		((TextView)vistaPersona.findViewById( R.id.collega_nome )).setText( U.epiteto(p) );
		String dati = U.dueAnni(p,false);	// +"   "+ Anagrafe.dueLuoghi(p)
		TextView vistaDettagli = vistaPersona.findViewById( R.id.collega_dati );
		if( dati.isEmpty() ) vistaDettagli.setVisibility( View.GONE );
		else vistaDettagli.setText( dati );
		if( !U.morto( p ) )
			vistaPersona.findViewById( R.id.collega_lutto ).setVisibility( View.GONE );
		if( U.sesso(p) == 1 )
			vistaPersona.findViewById(R.id.collega_carta).setBackgroundResource( R.drawable.casella_maschio );
		if( U.sesso(p) == 2 )
			vistaPersona.findViewById(R.id.collega_carta).setBackgroundResource( R.drawable.casella_femmina );
		vistaPersona.setOnClickListener( new View.OnClickListener() {
			public void onClick( View v ) {
				//Globale.individuo = p.getId();
				//scatola.getContext().startActivity( new Intent( scatola.getContext(), Individuo.class) );
				Intent intento = new Intent( scatola.getContext(), Individuo.class );
				intento.putExtra( "idIndividuo", p.getId() );
				intento.putExtra( "scheda", scheda );
				scatola.getContext().startActivity( intento );
			}
		} );
	}

	public static void cambiamenti( final LinearLayout scatola, Change cambi ) {
		if( cambi != null ) {
			View vistaPezzo = LayoutInflater.from( scatola.getContext() ).inflate( R.layout.pezzo_fatto, scatola, false );
			scatola.addView( vistaPezzo );
			( (TextView) vistaPezzo.findViewById( R.id.fatto_titolo ) ).setText( R.string.change_date );
			TextView vistaTesto = vistaPezzo.findViewById( R.id.fatto_testo );
			String dataOra = cambi.getDateTime().getValue() + " - " + cambi.getDateTime().getTime();
			if( dataOra.isEmpty() ) vistaTesto.setVisibility( View.GONE );
			else vistaTesto.setText( dataOra );
			//mettiEstensioni( scatola, cambi );
			LinearLayout scatolaNote = vistaPezzo.findViewById( R.id.fatto_note );
			for( Estensione altroTag : trovaEstensioni( cambi ) )
				metti( scatolaNote, altroTag.nome, altroTag.testo );
			// Grazie al mio contributo la data cambiamento può avere delle note
			U.mettiNote( scatolaNote, cambi, true );
		}
	}

	// Chiede conferma di eliminare un elemento
	public static boolean preserva( Object cosa ) {
		// todo Conferma elimina
		return false;
	}


	public static void salvaJson() {
		salvaJson( Globale.gc, Globale.preferenze.idAprendo );
	}

	static void salvaJson( Gedcom gc, int idAlbero ) {
		try {
			FileUtils.writeStringToFile(
					new File( Globale.contesto.getFilesDir(), idAlbero + ".json" ),
					new JsonParser().toJson( gc )
			);
		} catch (IOException e) {
			Toast.makeText( Globale.contesto, e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
		}
	}

	static int castaJsonInt( Object ignoto ) {
		if( ignoto instanceof Integer ) return (int) ignoto;
		else return ((JsonPrimitive)ignoto).getAsInt() ;
	}

	// Valuta se ci sono individui collegabili a un individuo
	static boolean ciSonoIndividuiCollegabili( Person piolo ) {
		int numTotali = Globale.gc.getPeople().size();
		int numFamili = Anagrafe.quantiFamiliari( piolo );
		//s.l( "num Totali " + numTotali +"  num Famili " + numFamili + "   "+ (numTotali > numFamili+1)  );
		return numTotali > numFamili+1;
	}

}