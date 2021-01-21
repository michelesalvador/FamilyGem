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
	public static int numFamiglia; // Quale famiglia dei genitori mostrare in diagramma, normalmente la 0
	static View vistaPrincipe;
	static int ordineMagazzino;
	public static boolean editato; // C'è stata un'editazione in EditaIndividuo o in Dettaglio e quindi il contenuto delle attività precedenti va aggiornato
	static boolean daSalvare; // Il contenuto del Gedcom è stato modificato e deve essere salvato
	public static String fotoCamera; // percorso in cui l'app fotocamera mette la foto scattata
	public static Media mediaCroppato; // parcheggio temporaneo del media in fase di croppaggio
	static Gedcom gc2; // per il confronto degli aggiornamenti
	static int idAlbero2; // id dell'albero2 con gli aggiornamenti

	// Viene chiamato all'avvio dell'applicazione, e anche quando viene riavviata
	@Override
	public void onCreate() {
		super.onCreate();
		contesto = getApplicationContext();
		avvia( contesto );
	}

	public static void avvia( Context contesto ) {
		Gson gson = new Gson();
		String stringone = "{ referrer:start, alberi:[], autoSalva:true }"; // preferenze vuote
							// i boolean false non hanno bisogno di essere inizializzati
		try {
			File filePreferenze = new File( contesto.getFilesDir(), "preferenze.json");
			if( filePreferenze.exists() )
				stringone = FileUtils.readFileToString( filePreferenze, "UTF-8" );
		} catch( IOException e ) {}
		preferenze = gson.fromJson( stringone, Armadio.class );
		if( preferenze.diagram == null )
			preferenze.defaultDiagram();
	}
}