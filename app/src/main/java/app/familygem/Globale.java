package app.familygem;

import android.app.Application;
import android.content.Context;
import android.view.View;
import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import java.io.File;
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
	static boolean editato; // C'è stata un'editazione in EditaIndividuo o in Dettaglio e quindi il contenuto delle attività precedenti va aggiornato
	public static Media mediaCroppato;

	// Viene chiamato all'avvio dell'applicazione, e anche quando viene riavviata
	public void onCreate() {
		super.onCreate();
		contesto = getApplicationContext();
		avvia( contesto );
	}

	public static void avvia( Context contesto ) {
		Gson gson = new Gson();
		String stringone = "{\"alberi\":[],\"idAprendo\":0,\"autoSalva\":true,\"caricaAlbero\":true}";	// preferenze vuote
							// i boolean false non hanno bisogno di essere inizializzati
		try {
			File filePreferenze = new File( contesto.getFilesDir(), "preferenze.json");
			if( filePreferenze.exists() )
				stringone = FileUtils.readFileToString( filePreferenze );
		} catch( IOException e ) {
			e.printStackTrace();
		}
		preferenze = gson.fromJson( stringone, Armadio.class );
	}
}