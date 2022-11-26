package app.familygem;

// Test di esportazione di un albero (con file media sparsi nello storage) verso GEDCOM e backup ZIP

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import androidx.documentfile.provider.DocumentFile;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.InputStream;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4ClassRunner.class)
public class ExportTest {

	Context testContext; // Contesto del test (per accedere alle risorse in /assets)
	Context appContext; // Contesto di app.familylab

	@Test
	public void vaiCoiTest() throws Exception {
		testContext = InstrumentationRegistry.getInstrumentation().getContext();
		appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

		// Eseguire questi 4 metodi uno alla volta:
		caricaFileGedcom(); // Dopodiché importa '/Gedcom/media.ged'
		//mettiFileMedia(); // Dopodiché aggiungi gli URI '/Uri' e '/SDCARD/Privata'
		//esportaGedcom();
		//esportaBackup();
	}

	// Carica /assets/media.ged in /GEDCOM/media.ged del device
	// Elimina l'albero 'media' esistente
	void caricaFileGedcom() throws Exception {
		InputStream inputStream = testContext.getAssets().open("media.ged");
		assertNotNull( inputStream );
		File gedcomFile = new File( Environment.getExternalStorageDirectory() + "/GEDCOM",
				"media.ged" );
		assertNotNull( gedcomFile );
		FileUtils.copyInputStreamToFile( inputStream, gedcomFile );
		assertTrue( gedcomFile.isFile() );

		for( Settings.Tree alb : Global.settings.trees ) {
			if( alb.title.equals("media") )
				Global.settings.deleteTree( alb.id );
		}
	}

	// Mette alcuni file in varie cartelle di Android
	// Aggiunge il path ai percorsi dell'albero
	void mettiFileMedia() throws Exception {
		Settings.Tree ultimoAlb = Global.settings.trees.get(Global.settings.trees.size() - 1);

		// PDF in external storage
		String percorso0 = appContext.getExternalFilesDir( String.valueOf(ultimoAlb.id) ).getPath();
		InputStream input = testContext.getAssets().open("È Carmelo.pdf");
		File external = new File( percorso0, "È Carmelo.pdf" );
		FileUtils.copyInputStreamToFile( input, external );
		assertTrue( external.isFile() );

		String percorso1 = Environment.getExternalStorageDirectory().getPath() + "/Percorso";
		File pathFile = new File( percorso1, "path.txt" );
		assertNotNull( pathFile );
		assertTrue( pathFile.canWrite() );
		FileUtils.writeStringToFile( pathFile, pathFile.getPath(), "UTF-8" );
		assertTrue( pathFile.isFile() );


		// Percorso di due txt omonimi in due sottocartelle
		String percorso2 = Environment.getExternalStorageDirectory().getPath() + "/Percorso Bis";

		// Txt da accedere con percorso, non riesce a scrivere in Android 10 e 11
		File pathFilePrimo = new File( percorso2 + "/primo", "omonimo.txt" );
		assertNotNull( pathFilePrimo );
		FileUtils.writeStringToFile( pathFilePrimo, pathFilePrimo.getPath(), "UTF-8" );
		assertTrue( pathFilePrimo.isFile() );

		// Altro file txt con lo stesso nome ma in un'altra sottocartella
		File pathFileSecondo = new File( percorso2 + "/secondo", "omonimo.txt" );
		assertNotNull( pathFileSecondo );
		FileUtils.writeStringToFile( pathFileSecondo, pathFileSecondo.getPath(), "UTF-8" );
		assertTrue( pathFileSecondo.isFile() );


		// Percorsi nelle preferenze
		assertEquals( ultimoAlb.title, "media" );
		//if( !ultimoAlb.cartelle.contains(percorso1) )
		ultimoAlb.dirs.add( percorso0 );
		assertTrue( ultimoAlb.dirs.contains(percorso0) );
		ultimoAlb.dirs.add( percorso1 );
		assertTrue( ultimoAlb.dirs.contains(percorso1) );
		ultimoAlb.dirs.add( percorso2 );
		assertTrue( ultimoAlb.dirs.contains(percorso2) );
		Global.settings.openTree = ultimoAlb.id;
		Global.settings.save();

		// File txt da prendere come Uri
		String percorso3 = Environment.getExternalStorageDirectory().getPath() + "/Uri";
		File uriFile = new File( percorso3, "uri.txt" );
		assertNotNull( uriFile );
		FileUtils.writeStringToFile( uriFile, DocumentFile.fromFile(uriFile).getUri().toString(), "UTF-8" );
		assertTrue( uriFile.isFile() );

		// Immagine nella scheda SD
		File[] luoghi = appContext.getExternalFilesDirs(null);
		for( File luogo : luoghi ) {
			if( !luogo.getPath().startsWith( Environment.getExternalStorageDirectory().getPath() ) ) {
				String sdFolder = luogo.getPath().substring(0, luogo.getPath().indexOf("Android/")) + "Privata";
				input = testContext.getAssets().open("anna.webp");
				assertNotNull( input );
				File file = new File( sdFolder, "anna.webp" );
				assertNotNull( file );
				FileUtils.copyInputStreamToFile( input, file );
				assertTrue( file.isFile() );
			}
		}
	}

	// Esporta in /Documents l'ultimo albero in 2 file: GEDCOM e ZIP coi media
	void esportaGedcom() {
		Settings.Tree ultimoAlb = Global.settings.trees.get(Global.settings.trees.size() - 1);
		//assertEquals( ultimoAlb.nome, "media" );
		int idAlbero = ultimoAlb.id;

		File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
		if( !documentsDir.exists() ) documentsDir.mkdir();

		File fileGedcom = new File( documentsDir, "Küçük ağaç.ged" );
		Exporter esp = new Exporter( appContext );
		assertTrue( esp.openTree( idAlbero ) );
		assertNull( esp.successMessage);
		assertNull( esp.errorMessage);
		assertTrue( esp.exportGedcom(Uri.fromFile(fileGedcom)) );
		assertTrue( fileGedcom.isFile() );
		assertEquals( esp.successMessage, appContext.getString(R.string.gedcom_exported_ok) );
		s.l( esp.successMessage);

		File fileGedcomZip = new File( documentsDir, "ਸੰਕੁਚਿਤ.zip" );
		Exporter esp2 = new Exporter( appContext );
		assertTrue( esp2.openTree( idAlbero ) );
		boolean result = esp2.exportGedcomToZip(Uri.fromFile(fileGedcomZip));
		s.l( esp2.errorMessage);
		assertTrue( result );
		assertEquals( esp2.successMessage, appContext.getString(R.string.zip_exported_ok) );
		assertTrue( fileGedcomZip.isFile() );
		s.l( esp2.successMessage);
	}

	// Esporta in /Documents l'ultimo albero come backup ZIP
	void esportaBackup() {
		File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
		if( !documentsDir.exists() ) documentsDir.mkdir();
		File fileBackup = new File( documentsDir, "Becàp olè.zip" );
		Exporter esp = new Exporter( appContext );
		Settings.Tree ultimoAlb = Global.settings.trees.get(Global.settings.trees.size()-1);
		assertTrue( esp.openTree( ultimoAlb.id ) );
		boolean result = esp.exportZipBackup( null, -1, Uri.fromFile(fileBackup) );
		s.l( esp.errorMessage);
		assertTrue( result );
		assertEquals( esp.successMessage, appContext.getString(R.string.zip_exported_ok) );
		assertTrue( fileBackup.isFile() );
		s.l( esp.successMessage);
	}
}
