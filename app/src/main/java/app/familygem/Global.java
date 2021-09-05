package app.familygem;

import android.app.Application;
import android.content.Context;
import android.view.View;
import android.widget.Toast;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;
import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import java.io.File;

public class Global extends Application {

	public static Gedcom gc;
	public static Context context;
	public static Settings settings;
	public static String indi; // Id of the selected person displayed across the app
	public static int familyNum; // Quale famiglia dei genitori mostrare in diagramma, normalmente la 0
	static View principalView;
	static int ordineMagazzino;
	public static boolean edited; // C'è stata un'editazione in EditaIndividuo o in Dettaglio e quindi il contenuto delle attività precedenti va aggiornato
	static boolean daSalvare; // Il contenuto del Gedcom è stato modificato e deve essere salvato
	public static String fotoCamera; // percorso in cui l'app fotocamera mette la foto scattata
	public static Media mediaCroppato; // parcheggio temporaneo del media in fase di croppaggio
	static Gedcom gc2; // per il confronto degli aggiornamenti
	static int treeId2; // id dell'albero2 con gli aggiornamenti

	// Viene chiamato all'avvio dell'applicazione, e anche quando viene riavviata
	@Override
	public void onCreate() {
		super.onCreate();
		context = getApplicationContext();
		start(context);
		ProcessLifecycleOwner.get().getLifecycle().addObserver(new LifecycleListener());
	}

	public static void start(Context context) {
		Gson gson = new Gson();
		String jsonString = "{referrer:start, trees:[], autoSave:true}"; // Empty settings
		                    // i boolean false non hanno bisogno di essere inizializzati
		File settingsFile = new File(context.getFilesDir(), "settings.json");
		// Rename "preferenze.json" to "settings.json" (introduced in version 0.8)
		File preferenzeFile = new File(context.getFilesDir(), "preferenze.json");
		if( preferenzeFile.exists() && !settingsFile.exists()) {
			if( !preferenzeFile.renameTo(settingsFile) ) {
				Toast.makeText(context, R.string.something_wrong, Toast.LENGTH_LONG).show();
				settingsFile = preferenzeFile;
			}
		}
		if( settingsFile.exists() ) {
			try {
				jsonString = FileUtils.readFileToString(settingsFile, "UTF-8");
				jsonString = updateSettings(jsonString);
			} catch( Exception e ) {
				Toast.makeText(context, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
			}
		}
		settings = gson.fromJson( jsonString, Settings.class );
		if( settings.diagram == null )
			settings.defaultDiagram();
	}

	// Modifications to the text coming from files/settings.json
	private static String updateSettings(String json) {
		// Version 0.8 added new settings for the diagram
		json = json.replace("\"siblings\":true", "siblings:2,cousins:2,spouses:true");
		json = json.replace("\"siblings\":false", "siblings:0,cousins:0,spouses:true");
		// Italian translated to English (version 0.8)
		json = json.replace("\"alberi\":", "\"trees\":");
		json = json.replace("\"idAprendo\":", "\"openTree\":");
		json = json.replace("\"autoSalva\":", "\"autoSave\":");
		json = json.replace("\"caricaAlbero\":", "\"loadTree\":");
		json = json.replace("\"esperto\":", "\"expert\":");
		json = json.replace("\"nome\":", "\"title\":");
		json = json.replace("\"cartelle\":", "\"dirs\":");
		json = json.replace("\"individui\":", "\"persons\":");
		json = json.replace("\"generazioni\":", "\"generations\":");
		json = json.replace("\"radice\":", "\"root\":");
		json = json.replace("\"condivisioni\":", "\"shares\":");
		json = json.replace("\"radiceCondivisione\":", "\"shareRoot\":");
		json = json.replace("\"grado\":", "\"grade\":");
		json = json.replace("\"data\":", "\"dateId\":");
		return json;
	}

	// The birthdays notifier is activated when the app goes to background
	static class LifecycleListener implements LifecycleObserver {
		@OnLifecycleEvent(Lifecycle.Event.ON_STOP)
		void onMoveToBackground() {
			if( gc != null && settings.openTree > 0 ) {
				new Notifier(context);
			}
		}
	}
}
