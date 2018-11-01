package app.familygem;

import android.app.Application;
import android.content.Context;
import android.support.v4.app.Fragment;
import android.view.View;
import android.widget.Toast;
import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.parser.JsonParser;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class Globale extends Application {
	public static Gedcom gc;
	static int ordineAnagrafe = 0;
	public static String individuo;	// "I1";
	static int ordineBiblioteca = 0;
	static int ordineMagazzino;
	public static Media media;
	public static Context contesto;
	public static Armadio preferenze;
	static View vistaPrincipe;
	public static Object contenitore;	// il padre del tag da modificare
	public static Object oggetto;	// il tag da modificare
	static boolean editato; // C'è stata un'editazione in EditaIndividuo o in Dettaglio e quindi il contenuto (ad es. di Individuo) va aggiornato

	// Viene chiamato all'avvio dell'applicazione, e anche quando viene riavviata
	public void onCreate() {
		super.onCreate();
		contesto = getApplicationContext();
		avvia( contesto );
	}

	public static void avvia( Context contesto ) {
		Gson gson = new Gson();
		String stringone = "{\"alberi\":[],\"idAprendo\":0}";	// preferenze vuote
		try {
			File filePreferenze = new File( contesto.getFilesDir(), "preferenze.json");
			if( filePreferenze.exists() )
				stringone = FileUtils.readFileToString( filePreferenze );
		} catch( IOException e ) {
			e.printStackTrace();
		}
		preferenze = gson.fromJson( stringone, Armadio.class );

		// (ri)apre il Gedcom
		if( gc == null && preferenze.idAprendo > 0 ) {
			try {
				File fileJson = new File( contesto.getFilesDir(), preferenze.idAprendo + ".json");
				String contenuto = FileUtils.readFileToString(fileJson);
				JsonParser jp = new JsonParser();
				gc = jp.fromJson(contenuto);
				individuo = preferenze.alberoAperto().radice;
			} catch( Exception e ) {
				Toast.makeText( contesto, e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
			}
		}
	}
}