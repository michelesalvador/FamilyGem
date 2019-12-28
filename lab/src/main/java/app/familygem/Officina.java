package app.familygem;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
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
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import com.theartofdev.edmodo.cropper.CropImage;
import com.yalantis.ucrop.UCrop;
import com.yalantis.ucrop.UCropActivity;
import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.parser.ModelParser;
import org.folg.gedcom.tools.CountsCollector;
import org.folg.gedcom.tools.GedcomAnalyzer;
import org.xml.sax.SAXParseException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import javax.net.ssl.HttpsURLConnection;

public class Officina extends AppCompatActivity {

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		setContentView(R.layout.officina);
		//s.l( Globale.preferenze+"  "+ Globale.preferenze.idAprendo+"  "+ Globale.preferenze.alberoAperto() );

		Intent intento = getIntent();
		String action = intento.getAction();
		Uri data = intento.getData();
		s.l( action );
		if( data != null )
			s.l( data.getQuery() );

		findViewById(R.id.bottone_vario).setOnClickListener( new View.OnClickListener() {
			public void onClick(View v) {
				percorsi_di_Sistema();
				//new LoadImageFromURL().execute("http://www.cafleurebon.com/wp-content/uploads/2017/03/fica-mana-symbol-182x300.jpg");
				//new U.ZuppaMedia( (ImageView)findViewById(R.id.immagine), (ProgressBar)findViewById(R.id.circolo), null ).execute("https://www.google.com");
				//database();
				//mandaOggettoAttivita();
				//prendi_file_da_FileManager();
				/*if( BuildConfig.utenteAruba != null )
					new InvioFTP().execute( getCacheDir() + "/Notepad3_4.18.512.992_Setup.zip" );*/
				//new PostaDatiCondivisione().execute( BuildConfig.passwordAruba, "Tìtolo Albèro pròtto", "nòòme Mìtto Gròsso", "Bèllo Ciìao" );
				//creaZipConCartella();
				//listaAttivita( Officina.this );
				//apriFileConApp( getExternalFilesDir(null)+"/Document.pdf" ); //Environment.getExternalStorageDirectory()+"/Documents/nuovo.txt"
			}
		});

		findViewById(R.id.bottone_libero).setOnClickListener( new View.OnClickListener() {
			public void onClick(View v) {
				U.appAcquisizioneImmagine( Officina.this );

				/* Condivisione di un file attraverso le app esistenti
				Intent sendIntent = new Intent(Intent.ACTION_SEND);
				sendIntent.setType("application/*");
				Uri uri = Uri.fromFile(new File( "ciccio.txt" ));
				sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
				Intent chooser = Intent.createChooser(sendIntent, "title");
				if (sendIntent.resolveActivity(getPackageManager()) != null) {
					startActivity(chooser);
				} else s.l("Nessuna app per questa operazione");*/
			}
		});

		findViewById(R.id.immagine).setOnClickListener( new View.OnClickListener() {
			public void onClick(View v) {
				startActivity( new Intent( Officina.this, Diagramma.class ) );
			}
		});
	}

	static void listaAttivita( Context contesto ) {
		ActivityManager am = (ActivityManager)contesto.getSystemService(ACTIVITY_SERVICE);
		List<ActivityManager.RunningTaskInfo> runningTaskInfoList = am.getRunningTasks(10);
		//Iterator<ActivityManager.RunningTaskInfo> itr = runningTaskInfoList.iterator();
		for ( ActivityManager.RunningTaskInfo info : runningTaskInfoList ) {
			s.l( info.id +" "+ info.description +"\n"+ info.numActivities +" "+ info.topActivity.getClassName() );
		}
		am.getRunningAppProcesses();
	}

	public static void unzip(File source, String out) throws IOException {
		try {
			ZipInputStream zis = new ZipInputStream(new FileInputStream(source));
			ZipEntry entry = zis.getNextEntry();
			while (entry != null) {
				File file = new File( out, entry.getName());
				if (entry.isDirectory()) {
					file.mkdirs();
				} else {
					File parent = file.getParentFile();
					if (!parent.exists()) {
						parent.mkdirs();
					}
					BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
					byte[] buffer = new byte[1024];
					int location;
					while ((location = zis.read(buffer)) != -1) {
						bos.write(buffer, 0, location);
					}
				}
				entry = zis.getNextEntry();
			}
		} catch( Exception e ){
			e.printStackTrace();
		}
	}

	// Crea un file zip con sottocartella
	void creaZipConCartella() {
			try {
				FileOutputStream f = new FileOutputStream(getCacheDir() + "/cartellato.zip" );
				ZipOutputStream zip = new ZipOutputStream( new BufferedOutputStream(f) );
				zip.putNextEntry( new ZipEntry("xml/") );
				zip.putNextEntry( new ZipEntry("xml/xml") );
				zip.close();
			} catch(Exception e) {
				e.printStackTrace();
			}
	}

	// si connette a una pagina php e gli posta dei dati (da inserire in un databse)
	static class PostaDatiCondivisione extends AsyncTask<String,Void,String> {
		@Override
		protected String doInBackground(String... stringhe) {
			String risposta = null;
			if( stringhe[0] != null )
				try {
					URL url = new URL("https://www.familygem.app/inserisci.php");
					HttpURLConnection conn = (HttpURLConnection) url.openConnection();
					conn.setRequestMethod("POST");
					/*conn.setDoInput(true);
					conn.setDoOutput(true);
					conn.setReadTimeout(10000);
					conn.setConnectTimeout(15000);
					conn.setChunkedStreamingMode(0);*/
					OutputStream out = new BufferedOutputStream( conn.getOutputStream() );
					BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));
					String dati = "password=" + URLEncoder.encode( stringhe[0], "UTF-8") +
							//"&idData=" + stringhe[1] +
							"&titoloAlbero=" + URLEncoder.encode( stringhe[1], "UTF-8") +
							"&nomeMittente=" + URLEncoder.encode(stringhe[2], "UTF-8") +
							"&nomeDestinatario=" + URLEncoder.encode(stringhe[3], "UTF-8");
					s.l( dati );
					writer.write( dati );
					writer.flush();
					writer.close();
					out.close();

					// Risposta
					BufferedReader in = new BufferedReader( new InputStreamReader(conn.getInputStream()) );
					/*String inputLine;
					StringBuffer risposta = new StringBuffer();
					while ((inputLine = in.readLine()) != null) { risposta.append(inputLine); }
					in.close();*/
					risposta = in.readLine();
					in.close();
					conn.disconnect();
				} catch( Exception e ) {
					e.printStackTrace();
				}
			return risposta;
		}

		@Override
		protected void onPostExecute(String risposta) {
			if( risposta.startsWith("20") )
				s.l("OK " + risposta);
			else
				s.l( "KO " + risposta );
		}
	}

	static class InvioFTP extends AsyncTask<String, Void, FTPClient> {
		protected FTPClient doInBackground(String... percorsi) {
			FTPClient ftpClient = new FTPClient();
			try {
				ftpClient.connect( "89.46.104.211", 21 );
				ftpClient.enterLocalPassiveMode();
				ftpClient.login( BuildConfig.utenteAruba, BuildConfig.passwordAruba );
				ftpClient.changeWorkingDirectory("/www.familygem.app/condivisi");
				s.l( "dir " + ftpClient.printWorkingDirectory() );
				/*for( FTPFile dir : ftpClient.listDirectories() ) {
					s.l ( ">>>>> " + dir.getName());
				}*/
				ftpClient.setFileType( FTP.BINARY_FILE_TYPE ); // FTP.ASCII_FILE_TYPE
				BufferedInputStream buffIn;
				buffIn = new BufferedInputStream( new FileInputStream( percorsi[0] ) );
				//ftpClient.getModificationTime(  );
				String dataId = new SimpleDateFormat("yyyyMMddHHmmss").format(Calendar.getInstance().getTime());
				ftpClient.storeFile( dataId +".zip", buffIn );
				buffIn.close();
				ftpClient.logout();
				ftpClient.disconnect();
			} catch( Exception e ) {
				e.printStackTrace();
				//Toast.makeText( Globale.contesto, e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
					// Can't create handler inside thread that has not called Looper.prepare()
			}
			return ftpClient;
		}
		protected void onPostExecute(FTPClient result) {
			s.l("FTP connection complete");
			//ftpClient = result;
			//Where ftpClient is a instance variable in the main activity
		}
	}

	@Override
	public void onRequestPermissionsResult( int codice, String[] permessi, int[] accordi ) {
		U.risultatoPermessi( this, codice, permessi, accordi );
	}

	// Venendo da Windows, cercavo una semplice finestra di apertura file... ma qui siamo nel mondo degli uri
	void prendi_file_da_FileManager() {
		Intent intent = new Intent( Intent.ACTION_GET_CONTENT );
		//Intent intent = new Intent( Intent.ACTION_OPEN_DOCUMENT );
		// uno differenza tra i 2 è che ACTION_OPEN_DOCUMENT (da api 19) consente multeplici mimetype
		// entrambi producono un simpatico uri, ACTION_OPEN_DOCUMENT forse solo content://
		//Intent intent = new Intent( Intent.ACTION_OPEN_DOCUMENT_TREE );
		// produce uri tipo content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FCamera
		//Intent intent = new Intent( Intent.ACTION_PICK );	// fa scegliere in una lista con alcuni provider di immagini, audio, video
		//Intent intent = new Intent( Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI );
			// riduce la lista di provider solo a quelli che dispensano immagini: Galleria e Foto
		intent.setType( "*/*" );	// permette di aprire qualsiasi tipo di file
		//intent.setDataAndType( android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "*/*" ); // bho non cambia niente
		//intent.setType( "application/octet-stream" );	// sarebbe quello corretto per abilitare solo i file .ged
		// ma disabilita i .ged in Download e in GoogleDrive (?!!??)
		//intent.setType( "application/*" );	// una giusta via di mezzo:
		// mostra solo i provider di file, ma abilita quasi tutti i file
		//intent.addCategory( Intent.CATEGORY_OPENABLE );	// dovrebbe mostrare solo i file apribili.. ma non vedo differenza
		startActivityForResult( intent,123 );
	}

	// Aprire un'immagine con la libreria Android Image Cropper
	void apri_Immagine_da_Croppare() {
		// il classico cercatore di file di Family Gem
		/*Intent intent = new Intent( Intent.ACTION_GET_CONTENT );
		intent.setType( "image/*" );
		startActivityForResult( intent,14463 );*/

		// Apre direttamente Android Image Cropper con le opzioni per importare un'immagine
		// Molto interessante ma vorrei implementarlo io
		/*CropImage.activity()
				//.quiciVannoleOpzioni()
				.start(Officina.this );*/

		// Cerca di sfruttare la bellissima lista di fonti di immagini fornita da Android Image Cropper
		// funziona TRANNE la foto scattata con la fotocamera restituisce 'Intent data' = null
		//startActivityForResult( CropImage.getPickImageChooserIntent( Officina.this,"Choose the source", false, true ), 14463 );
		CropImage.startPickImageActivity( Officina.this );
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
		s.l( "requestCode: " + requestCode +"  resultCode: " + resultCode + "   " + data );
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

		/* Riceve un'immagine e la ritaglia con uCrop. ok funziona*/
		if( resultCode == RESULT_OK && requestCode == 1234 ) {
			File destino = new File( getFilesDir(), "prova.jpg" );
			Uri uriDestinazione = Uri.fromFile( destino );
			UCrop.Options opzioni = new UCrop.Options();
			//opzioni.setHideBottomControls( true );
			opzioni.setShowCropGrid( false );
			opzioni.setFreeStyleCropEnabled( true );
			opzioni.setAllowedGestures( UCropActivity.ALL,UCropActivity.ALL,UCropActivity.ALL ); // non ho capito bene...
			opzioni.setToolbarColor( getResources().getColor(R.color.primario) );
			opzioni.setStatusBarColor( getResources().getColor(R.color.primarioScuro) );
			opzioni.setCompressionQuality( 90 );
			UCrop.of( data.getData(), uriDestinazione )
					//.withAspectRatio(1, 1 )
					.withOptions( opzioni )
					.withMaxResultSize( 200, 200 ) // max Width, max Height Todo: larghezza e altezza dello schermo?
					.start( this );
		}
		// Riprende l'immagine ritagliata da uCrop
		if( resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP ) {
			Uri resultUri = UCrop.getOutput( data );
			s.l( resultUri );	// file:///data/data/app.familylab/files/prova.jpg
			try {
				Bitmap bitmap = MediaStore.Images.Media.getBitmap( this.getContentResolver(), resultUri );
				((ImageView)findViewById(R.id.immagine)).setImageBitmap( bitmap );
			} catch( IOException e ) {
				e.printStackTrace();
			}
		} else if( resultCode == UCrop.RESULT_ERROR ) {
			final Throwable cropError = UCrop.getError(data);
			s.l( cropError );
		}

		// Riceve un'immagine e la ritaglia con Android Image Cropper
		if( resultCode == RESULT_OK && requestCode == 14463 ) { //CropImage.PICK_IMAGE_CHOOSER_REQUEST_CODE
			if( data != null ) {
				Media media = new Media();
				U.ritagliaImmagine( Officina.this, data, media );
			} else
				s.l( "data è null" );
		}
		// Ottiene l'immagine ritagliata da Android Image Cropper
		if( requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE ) {
			U.fineRitaglioImmagine( resultCode, data );
			if( data != null )
				((ImageView)findViewById(R.id.immagine)).setImageURI(  // come ho fatto a ignorarlo fin'ora?????
						CropImage.getActivityResult(data).getUri() );
		}
	}

	@SuppressLint("NewApi")
	void percorsi_di_Sistema() {
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

	void apriFileConApp( String percorso ) {

		// Un modo per avere la lista delle app con cui aprire un file
		/* Non funziona negli android moderni: https://stackoverflow.com/questions/38200282
		try {
			File file = new File( percorso );
			Intent intento = new Intent(Intent.ACTION_VIEW);
			String tipo = URLConnection.guessContentTypeFromStream( new FileInputStream(file) );
			if( tipo == null)
				tipo = URLConnection.guessContentTypeFromName( file.getName() );
			intento.setDataAndType( Uri.fromFile(file), tipo );
			s.l( "<" + file.getName() + "> \"" + tipo + "\"  " + percorso );
			startActivity( intento );
		} catch( IOException e ) {
			e.printStackTrace();
		}*/

		// Lista completa delle app installate
		File file = new File( percorso );
		MimeTypeMap mappa = MimeTypeMap.getSingleton();
		String estensione = MimeTypeMap.getFileExtensionFromUrl( file.getName() );
		String tipo = mappa.getMimeTypeFromExtension( estensione.toLowerCase() );
		Intent mainIntent = new Intent( Intent.ACTION_MAIN );	// non trovo altro uso che questi due messi Così
		mainIntent.addCategory( Intent.CATEGORY_LAUNCHER );
		//mainIntent.setDataAndType( Uri.fromFile(file), tipo );	// no l'INTENTO diventa NON GESTIBILE
		s.l( file.getAbsolutePath() +" "+estensione);
		startActivity( mainIntent );

		/* Tentativo di avviare il chooser che però non parte
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
	}
}