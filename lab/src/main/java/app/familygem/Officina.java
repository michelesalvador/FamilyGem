package app.familygem;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.parser.ModelParser;
import org.folg.gedcom.tools.CountsCollector;
import org.folg.gedcom.tools.GedcomAnalyzer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.xml.sax.SAXParseException;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Officina extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.officina);

		findViewById(R.id.bottone_vario).setOnClickListener( new View.OnClickListener() {
			public void onClick(View v) {
				//new LoadImageFromURL().execute("http://www.cafleurebon.com/wp-content/uploads/2017/03/fica-mana-symbol-182x300.jpg");
				new beviZuppa().execute();
				//database();
				//mandaOggettoAttivita();
			}
		});

		findViewById(R.id.bottone_dati).setOnClickListener( new View.OnClickListener() {
			@SuppressLint("NewApi")
			public void onClick(View v) {
				// External storage
				File dirEsterna = getExternalFilesDir(null);	// da API 8
				// /storage/emulated/0/Android/data/app.familylab/files
				// che in realtà è  /mnt/shell/emulated/0/Android/data/app.familylab/files
				// viene cancellata con disinstallazione app
				s.l( "ExternalFilesDir= " + dirEsterna );
				//File nuovo = new File(dirEsterna.getAbsolutePath(), "creatoDaMe.txt"  );
				//try { nuovo.createNewFile(); } catch ( IOException e ){ e.printStackTrace(); }
				for( String nomeFile : dirEsterna.list() ) {
					s.l( "File in ExternalFilesDir= " + nomeFile );
				}

				File[] luoghi = getApplicationContext().getExternalFilesDirs(null);	// richiede API 19 o superiore
				// /storage/emulated/0/Android/data/lab.gedcomy/files
				// /storage/external_SD/Android/data/lab.gedcomy/files
				for( File luogo : luoghi ) {
					//String dir = luogo.getAbsolutePath().substring(0, luogo.getAbsolutePath().indexOf("/Android"));
					s.l("contesto.getExternalFilesDirs= " + luogo );
				}

				s.l( "Environment.getExternalStorageDirectory= " + Environment.getExternalStorageDirectory()
						+ "  stato= " + Environment.getExternalStorageState() );
				// /storage/emulated/0   mounted

				File dirDocumenti = Environment.getExternalStoragePublicDirectory( // da API 8
						Environment.DIRECTORY_DOCUMENTS ); // da API 19
				s.l( "Environment.getExternalStoragePublicDirectory= " + dirDocumenti );
				// /storage/emulated/0/Documents
				try {
					File nuovoFile = new File(dirDocumenti,"nuovo.txt" );
					FileUtils.write( nuovoFile, "ciao", "ASCII" );
					/*if( dirDocumenti.exists() )
						s.l( " esiste ");*/
				} catch( IOException e ) {
					e.printStackTrace();
				}

				s.l( "Environment.getDataDirectory= " + Environment.getDataDirectory() );
				// /data

				s.l( "OpenableColumns.DISPLAY_NAME= " + OpenableColumns.DISPLAY_NAME	// _display_name
						+"\nMediaStore.MediaColumns.DISPLAY_NAME= " + MediaStore.MediaColumns.DISPLAY_NAME	// _display_name
						+"\nMediaStore.MediaColumns.TITLE= " + MediaStore.MediaColumns.TITLE	// title
						+"\nMediaStore.Files.FileColumns.DATA= " + MediaStore.Files.FileColumns.DATA	// _data
						+"\nMediaStore.Images.ImageColumns.DATA= " + MediaStore.Images.ImageColumns.DATA	// _data
						+"\nMediaStore.Images.Media.DATA= " + MediaStore.Images.Media.DATA );	// _data
			}
		});

		findViewById(R.id.bottone_importa).setOnClickListener( new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent( Intent.ACTION_GET_CONTENT );
				//Intent intent = new Intent( Intent.ACTION_OPEN_DOCUMENT );
				// io non trovo nessuna differenza tra ACTION_GET_CONTENT e ACTION_OPEN_DOCUMENT
				// entrambi producono un simpatico uri
				//Intent intent = new Intent( Intent.ACTION_OPEN_DOCUMENT_TREE );
				// produce uri tipo content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FCamera
				//Intent intent = new Intent( Intent.ACTION_PICK );	// fa scegliere solo tra immagini, audio, video
				intent.setType( "*/*" );	// permette di aprire qualsiasi tipo di file
				//intent.setType( "application/octet-stream" );	// sarebbe quello corretto per abilitare solo i file .ged
				// ma disabilita i .ged in Download e in GoogleDrive (?!!??)
				//intent.setType( "application/*" );	// una giusta via di mezzo:
				// mostra solo i provider di file, ma abilita quasi tutti i file
				//intent.addCategory( Intent.CATEGORY_OPENABLE );	// dovrebbe mostrare solo i file apribili.. ma non vedo differenza
				startActivityForResult( intent,123 );
			}
		});

		findViewById(R.id.immagine).setOnClickListener( new View.OnClickListener() {
			public void onClick(View v) {
				startActivity( new Intent( Officina.this, Diagramma.class ) );
			}
		});
	}

	public class AiutoDatabaseImmagini extends SQLiteOpenHelper {
		AiutoDatabaseImmagini( Context contesto) {
			super( contesto, "immagini.db", null, 1);
		}
		public void onCreate( SQLiteDatabase db ) {
			db.execSQL("CREATE TABLE immagini (" +
					//"id INTEGER PRIMARY KEY," +
					"url_originale TEXT, nome_file TEXT)");
		}
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL("DROP TABLE IF EXISTS immagini");
			onCreate(db);
		}
		/*public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			onUpgrade(db, oldVersion, newVersion);
		}*/
	}

	@Override
	protected void onActivityResult( int requestCode, int resultCode, Intent data ) {
		//??? super.onActivityResult(requestCode, resultCode, data);    Inutile
		// Importa un file Gedcom
		if( resultCode == RESULT_OK && requestCode == 123 ) {
			try {
				// Ricava un content-Uri, che non è un percorso file
				Uri uri = data.getData();   // content:/percorso/nomefile.ged
				//File fileGedcom = new File( uri.getPath() );	// magari! Invece non è un percorso valido
				s.l( "uri= "+uri +"\nuri Path= "+ uri.getPath() +"\nuri EncodedPath= "+ uri.getEncodedPath() );

				// Seguono tentativi di ricavare il percorso del file .ged dall'uri

				// Trova sempre il nome del file anche quando non è esplicito nell'uri
				// funziona solo con i  content:/  non con i  file:///
				String[] proiez = { OpenableColumns.DISPLAY_NAME };
				Cursor cursore = getContentResolver().query( uri, proiez,null,null,null,null);
				int indice = 999;
				String nomeFile = null;
				if( cursore != null && cursore.moveToFirst() ) {
					//indice = cursore.getColumnIndex(OpenableColumns.DISPLAY_NAME);
					nomeFile = cursore.getString( 0 );
					cursore.close();
				}
				s.l("indice=" + indice 	// indice = 0
						+ "  Nome file cursore= " + nomeFile);    // solo il nome del file 'famiglia.ged'

				// i tre cursor funzionano sui file mediatici presi con ACTION_PICK, sugli altri restituisce null
				// questo primo con projection null funziona coi file:///
				String primo;
				String[] proiec = { MediaStore.Images.ImageColumns.DATA };
				Cursor cursor2 = this.getContentResolver().query( uri, proiec, null, null, null);
				if (cursor2 == null) {
					primo = uri.getPath();
				} else {
					cursor2.moveToFirst();
					//int idx = cursor2.getColumnIndexOrThrow( MediaStore.Images.ImageColumns.DATA );
					primo = cursor2.getString( 0 );
					cursor2.close();
				}

				String[] projectione = { MediaStore.Files.FileColumns.DATA };
				String terzo = null;
				Cursor cursorr = getContentResolver().query(uri, projectione, null, null, null);
				if (cursorr != null) {
					int column_indexe = cursorr.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA);
					cursorr.moveToFirst();
					terzo = cursorr.getString(column_indexe);
					cursorr.close();
				}

				Cursor cursor4 = null;
				String quarto = null;
				try {
					String[] proj = {MediaStore.Images.Media.DATA};
					cursor4 = getContentResolver().query(uri, proj, null, null, null);
					if (cursor4 != null) {
						int column_index4 = cursor4.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
						cursor4.moveToFirst();
						quarto = cursor4.getString(column_index4);
					}
				} finally {
					if (cursor4 != null) {
						cursor4.close();
					}
				}

				// FileDescriptor : NON se ne ricava niente
				ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor( uri, "r");
				FileDescriptor fd = pfd.getFileDescriptor();

				s.l( "cursori= "+ primo +"\n"+ terzo +"\n"+ quarto
						+"\n"+ fd );

				// Il percorso /storage/...
				String percorsoFile = U.uriPercorsoFile( uri );
						// trova  /storage/emulated/0/famiglia/a_famigliotta_250.ged
						// e anche  /storage/external_SD/famiglia/a_famigliotta_250.ged
						// invece restituisce null da Google drive
				File fileGedcom;
				if( percorsoFile.lastIndexOf('/') > 0 ) {
					fileGedcom = new File( percorsoFile );
				} else {
					/* da android uri a java uri, non funziona
					// IllegalArgumentException: Expected file scheme in URI: content://com.google.android.apps.docs.storage/document/acc%3D1%3Bdoc%3D890
					URI juri = new URI( uri.toString() );
					s.l( "juri: " + juri );
					fileGedcom = new File( juri );*/

					// fileGedcom da input stream
					InputStream input = getContentResolver().openInputStream(uri);
					fileGedcom = new File(getCacheDir(), "temp.ged");
					FileUtils.copyInputStreamToFile(input, fileGedcom);
				}

				ModelParser mp = new ModelParser();
				Gedcom gc = mp.parseGedcom( fileGedcom );
				gc.createIndexes();
				s.l( "percorsoFile= " + percorsoFile
						+"\nfile.getAbsolutePath= "+ fileGedcom.getAbsolutePath()	// restituisce lo stesso percorso
						+"\ntizio= "+ gc.getPerson(U.trovaRadice(gc)).getNames().get(0).getDisplayValue() );

				// Percorso della cartella da cui ha caricato il gedcom
				String percorsoCartella = fileGedcom.getParent();
				String[] nomiFile = new File( percorsoCartella ).list();
				s.l( percorsoCartella 	+"   "+ nomiFile[0] );

				File unFile = new File( percorsoCartella, nomiFile[ nomiFile.length-1 ] );
				if( unFile.exists() )
					s.l( unFile.getAbsolutePath() +" esiste!" );
				else
					s.l( unFile.getAbsolutePath() +" NON esiste" );
			} catch (  IOException | SAXParseException e) {	// |URISyntaxException | FileNotFoundException
				e.printStackTrace();
			}
		}
		// Esperimenti con GedcomAnalyzer
		if( resultCode == RESULT_OK && requestCode == 4747 ) {
			try {
				Uri uri = data.getData();
				String percorsoFile = U.uriPercorsoFile( uri );
				File fileGedcom = new File( percorsoFile );
				GedcomAnalyzer gcAnaliz = new GedcomAnalyzer();
				gcAnaliz.analyzeGedcom( fileGedcom );
				CountsCollector errori = gcAnaliz.getErrors();
				for( String err : errori.getKeys() )
					s.l( "Errore = " + err );  // ok
				CountsCollector warnings = gcAnaliz.getWarnings();  // in pratica esclusivamente i tag aggiunti come estensioni
				PrintWriter print = new PrintWriter( getFilesDir() + "/report.txt" );
				warnings.writeSorted( true, 0, print );

				for( String warn : warnings.getKeys() )
					System.out.println( "Avviso = " + warn  // chiavi tipo 'Tag added as extension: INDI OBJE _DATE' in disordine
							+ " -> " + warnings.getCount( warn ) ); // sempre 1 ???
				for( Map.Entry warn : warnings.getSortedSet( true, 0 ) ) {
					// true mette le chiavi in ordine alfabetico, false in ordine alfabetico inverso
					System.out.println( warn.getKey() + " : " + warn.getValue() );   // value è sempre 1 (?!?)
				}
			} catch( Exception e ) {
				e.printStackTrace();
			}
		}
	}

	void database() {
		AiutoDatabaseImmagini aiutoImmagini = new AiutoDatabaseImmagini( getBaseContext() );
		SQLiteDatabase db = aiutoImmagini.getWritableDatabase();	// in modalità scrittura
		// Create a new map of values, where column names are the keys
		ContentValues values = new ContentValues();
		values.put( "url_originale", "http://percorso/photo.jpg" );
		//values.put( "nome_file", "123.jpg" );
		// Insert the new row, returning the primary key value of the new row
		long newRowId = db.insert( "immagini", null, values );

		// Define a projection that specifies which columns from the database you will actually use after this query.
		String[] projection = { "id", "url_originale" };
		// Filter results WHERE "title" = 'My Title'
		String selection = "url_originale = ?";
		String[] selectionArgs = { "http://percorso/photo.jpg" };
		// How you want the results sorted in the resulting Cursor
		//String sortOrder = FeedEntry.COLUMN_NAME_SUBTITLE + " DESC";

		Cursor cursor = db.query(
				"immagini",   // The table to query
				projection,			// The array of columns to return (pass null to get all)
				selection,          // The columns for the WHERE clause
				selectionArgs,      // The values for the WHERE clause
				null,       // don't group the rows
				null,        // don't filter by row groups
				null           // The sort order
		);
		List indirizzi = new ArrayList<>();
		while(cursor.moveToNext()) {
			long itemId = cursor.getLong( cursor.getColumnIndexOrThrow("url_originale") );
			indirizzi.add(itemId);
		}
		cursor.close();
		//for( long id : indirizzi )
		//	s.l( item.toString() );
	}

	void gestisciArmadio() {
		try {
			/*List<String> preferiti = new ArrayList<>();
			preferiti.add( "I2" );
			preferiti.add( "I23" );
			preferiti.add( "I234" );
			Cassetto cass1 = new Cassetto(1,"the Simpsons","/storage/emulated/0/Documents",
					1234,123, "I1", preferiti );
			Cassetto cass2 = new Cassetto(2,"Bartolètti f.","/storage/emulated/0/Documents",
					555,89, "I1", preferiti );
			Cassetto cass3 = new Cassetto(3,"Jeronço f.","/storage/emulated/0/Documents",
					800,8, "I1", preferiti );
			Cassetto[] alberi = { cass1, cass2, cass3 };*/
			List<Armadio.Cassetto> alberi = new ArrayList<>();
			Gson gson = new Gson();
			String salvando = gson.toJson( new Armadio( alberi, 0) );
			s.l(salvando);
			FileUtils.writeStringToFile( new File(getFilesDir(),"preferenzeX.json"), salvando );

			/*String aprendo = FileUtils.readFileToString( new File(getFilesDir(), "preferenze.json") );
			//l(aprendo);
			Armadio armadio = gson.fromJson( aprendo, Armadio.class);
			s.l( armadio.alberi[0].nome +"\n"+ armadio.alberi[1].nome );

			//List<Cassetto> listaFrighi = armadio.alberi;
			for( Cassetto alb : armadio.alberi ) {
				s.l(alb.id +" "+ alb.nome );
			}/**/
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Carica un'immagine asincronicamente
	private class LoadImageFromURL extends AsyncTask<String, Void, Bitmap>{
		@Override
		protected Bitmap doInBackground(String... params) {
			Bitmap img = null;
			try {
				URL url = new URL( params[0] );
				InputStream inputStream = url.openConnection().getInputStream();
				img = BitmapFactory.decodeStream(inputStream);
			} catch( IOException e ) {
				e.printStackTrace();
			}
			return img;
		}
		@Override
		protected void onPostExecute(Bitmap bitmap){
			((ImageView)findViewById(R.id.immagine)).setImageBitmap( bitmap );
		}
	}

	// Carica l'immagine più grande da una pagina internet
	class beviZuppa extends AsyncTask<Void, Void, Bitmap> {
		@Override
		protected Bitmap doInBackground(Void... params) {
			Bitmap bitmap = null;
			try {
				//Document doc = Jsoup.connect("http://www.antenati.san.beniculturali.it/v/Archivio+di+Stato+di+Firenze/Stato+civile+della+restaurazione/Borgo+San+Lorenzo/Morti/1842/1366/005186556_00447.jpg").get();
				Document doc = Jsoup.connect("http://www.geditcom.com").get();
				//Element immagine = doc.select("img").first();	// ok
				List<Element> lista = doc.select("img");
				int maxDimensioniConAlt = 0;
				int maxDimensioni = 0;
				int maxLunghezzaAlt = 0;
				Element imgGrandeConAlt = null;
				Element imgGrande = null;
				Element imgAltLungo = null;
				for( Element img : lista ) {
					s.p( "\"" + img.attr("alt") +"\" "+ img.attr("width") + "x" + img.attr("height") );
					int larga, alta;
					if( img.attr("width").isEmpty() ) larga = 0;
					else larga = Integer.parseInt(img.attr("width"));
					if( img.attr("height").isEmpty() ) alta = 0;
					else alta = Integer.parseInt(img.attr("height"));
					s.l( " -> " + larga + "x" + alta );
					// Se in <img> mancano gli attributi "width" e "height", 'larga' e 'alta' rimangono a zero
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
				}
				String percorso = null;
				if( imgGrandeConAlt != null ) {
					s.l( "imgGrandeConAlt = " + imgGrandeConAlt.attr( "alt" ) + "  " + imgGrandeConAlt.attr( "width" ) + "x" + imgGrandeConAlt.attr( "height" ) );
					percorso = imgGrandeConAlt.absUrl( "src" );  //absolute URL on src
				} else if( imgGrande != null ) {
					s.l( "imgGrande = "+imgGrande.attr("alt") +"  "+ imgGrande.attr("width") + "x" + imgGrande.attr("height") );
					percorso = imgGrande.absUrl( "src" );
				} else if( imgAltLungo != null ) {
					s.l( "imgAltLungo = "+imgAltLungo.attr("alt") +"  "+ imgAltLungo.attr("width") + "x" + imgAltLungo.attr("height") );
					percorso = imgAltLungo.absUrl( "src" );
				}
				//String srcValue = imageElement.attr("src");  // exact content value of the attribute.
				s.l( "absoluteUrl " + percorso );
				URL url = new URL( percorso );
				InputStream inputStream = url.openConnection().getInputStream();
				bitmap = BitmapFactory.decodeStream(inputStream);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return bitmap;
		}
		@Override
		protected void onPostExecute( Bitmap bitmap ) {
			//((TextView)findViewById (R.id.myTextView)).setText (result);
			//s.l( "RISULTAtO = " + bitmap.getByteCount() );
			((ImageView)findViewById(R.id.immagine)).setImageBitmap( bitmap );
		}
	}
}