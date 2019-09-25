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
	public static Context contesto;
	public static Armadio preferenze;
	public static String individuo;	// "I1";
	static View vistaPrincipe;
	static int ordineAnagrafe = 0;
	static int ordineBiblioteca = 0;
	static int ordineMagazzino;
	public static boolean editato; // C'è stata un'editazione in EditaIndividuo o in Dettaglio e quindi il contenuto delle attività precedenti va aggiornato
	static boolean daSalvare; // Il contenuto del Gedcom è stato modificato e deve essere salvato
	public static Media mediaCroppato; // parcheggio temporaneo del media in fase di croppaggio
	static Gedcom gc2; // per il confronto degli aggiornamenti
	static int idAlbero2; // id dell'albero2 con gli aggiornamenti

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
				stringone = FileUtils.readFileToString( filePreferenze, "UTF-8" );
		} catch( IOException e ) {}
		preferenze = gson.fromJson( stringone, Armadio.class );
	}
}