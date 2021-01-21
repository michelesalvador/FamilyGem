// Instrumented test, which will execute on an Android device.

package app.familygem;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Environment;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.parser.ModelParser;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.InputStream;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4ClassRunner.class)
public class InstrumentedTest {

	Context testContext; // Contesto del test (per accedere alle risorse in /assets)
	Context appContext; // Contesto di app.familylab

	@Test
	public void useAppContext() throws Exception {
		testContext = InstrumentationRegistry.getInstrumentation().getContext();
		appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
		assertEquals( "app.familylab", appContext.getPackageName() );

		creaFile();
		//apriGedcom();
	}

	void creaFile() throws Exception {
		//InputStream is = androidTestContext.getResources().getAssets().open("ciao.txt");
		//assertEquals( is.toString(), "Caio " );

		// Txt
		File external = appContext.getExternalFilesDir( "external.txt" );
		FileUtils.writeStringToFile( external, "Ehilà bello.", "UTF-8" );

		AssetManager assets = testContext.getAssets();
		
		// Jpeg
		InputStream inputStreamJpeg = assets.open("elefante.jpg");
		assertNotNull( inputStreamJpeg );
		File immagine = new File( Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
				"elefante.jpg" );
		assertNotNull( immagine );
		FileUtils.copyInputStreamToFile( inputStreamJpeg, immagine );
		assertTrue( immagine.canWrite() );
		assertTrue( immagine.isFile() );

		// PDF nella scheda SD
		File[] luoghi = appContext.getExternalFilesDirs(null);
		// /storage/sdcard/Android/data/app.familylab/files   (KitKat)
		// /storage/emulated/0/Android/data/app.familylab/files   (successivi)
		// /storage/12F3-3214/Android/data/app.familylab/files
		for( File luogo : luoghi ) {
			if( !luogo.getPath().startsWith( Environment.getExternalStorageDirectory().getPath() ) ) {
				String dir = luogo.getPath().substring(0, luogo.getPath().indexOf("Android/")) + "Privata";
				s.l(dir);
				InputStream inputPDF = assets.open("sample.pdf");
				File filePdf = new File( dir, "sample.pdf" );
				assertNotNull( filePdf );
				FileUtils.copyInputStreamToFile( inputPDF, filePdf );
				assertTrue( filePdf.isFile() );
			}
		}
	}

	void apriGedcom() throws Exception {
		InputStream inputStream = testContext.getAssets().open("media.ged");
		assertNotNull( inputStream );

		File gedcomFile = new File( appContext.getCacheDir(), "temp.ged" );
		assertNotNull( gedcomFile );
		FileUtils.copyInputStreamToFile( inputStream, gedcomFile );
		assertTrue( gedcomFile.isFile() );

		ModelParser modelParser = new ModelParser();
		Gedcom gedcom = Globale.gc = modelParser.parseGedcom( gedcomFile );
		assertNotNull( gedcom );
		assertNotNull( gedcom.getPeople() );
		Person person = gedcom.getPeople().get(0);
		assertNotNull( person );
		assertEquals( person.getId(), "I1" );

	}
}
