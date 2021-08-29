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
	//static String fonte;
	static int ordineMagazzino;
	public static String fotoCamera; // percorso della foto scattata da un'app fotocamera
	public static Media media;
	public static Context contesto;
	//static Preferences preferenze = Preferences.userRoot().node("/lab/gedcomy");
	//static SharedPreferences preferenze;
	//static SharedPreferences.Editor salvatore;
	public static Armadio preferenze;
	static View vistaPrincipe;
	//static Fragment frammentoPrecedente;
	//public static Object contenitore;	// il padre del tag da modificare
	//public static Object oggetto;	// il tag da modificare
	public static Media mediaCroppato;
	//public static Note nota;	// Note e citazioniFonti meritano
	//public static SourceCitation citazioneFonte;
	static boolean editato; // C'è stata un'editazione in EditaIndividuo o in Dettaglio e quindi il contenuto (ad es. di Individuo) va aggiornato

	// Viene chiamato all'avvio dell'applicazione, e anche quando viene riavviata
	public void onCreate() {
		super.onCreate();
		contesto = getApplicationContext();
		avvia( contesto );
	}

	public static void avvia( Context contesto ) {
		//preferenze = getSharedPreferences("leMieBellePreferenze", MODE_PRIVATE );
		//salvatore = preferenze.edit();
		//preferenze = new Armadio();
		Gson gson = new Gson();
		String stringone = "{\"alberi\":[],\"idAprendo\":0,\"autoSalva\":true,\"caricaAlbero\":true}";	// preferenze vuote
							// i boolean false non hanno bisogno di essere inizializzati
		try {
			File filePreferenze = new File( contesto.getFilesDir(), "settings.json");
			if( filePreferenze.exists() )
				stringone = FileUtils.readFileToString( filePreferenze );
		} catch( IOException e ) {
			e.printStackTrace();
		}
		preferenze = gson.fromJson( stringone, Armadio.class );

		/* (ri)apre il Gedcom
		if( gc == null && preferenze.idAprendo > 0 ) {
			try {
				//s.l( "Globale.preferenze.idAprendo " + preferenze.idAprendo  );
				File fileJson = new File( contesto.getFilesDir(), preferenze.idAprendo + ".json");
				String contenuto = FileUtils.readFileToString(fileJson);
				JsonParser jp = new JsonParser();
				gc = jp.fromJson(contenuto);
				individuo = preferenze.alberoAperto().radice;
				//s.l("Riapertura file " + fileJson);
			} catch( Exception e ) {
				Toast.makeText( contesto, e.getLocalizedMessage(), Toast.LENGTH_LONG ).show();
			}
		}*/
	}
}