package app.familygem;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDexApplication;
import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import java.io.File;
import java.util.Locale;

public class Global extends MultiDexApplication {

	public static Gedcom gc;
	public static Context context;
	public static Settings settings;
	public static String indi; // Id of the selected person displayed across the app
	public static int familyNum; // Quale famiglia dei genitori mostrare in diagramma, normalmente la 0
	static View principalView;
	static int ordineMagazzino;
	public static boolean edited; // C'è stata un'editazione in EditaIndividuo o in Dettaglio e quindi il contenuto delle attività precedenti va aggiornato
	static boolean daSalvare; // Il contenuto del Gedcom è stato modificato e deve essere salvato
	/**
	 * path where the camera app puts the photo taken
	 * */
	public static String pathOfCameraDestination;
	public static Media croppedMedia; // parcheggio temporaneo del media in fase di croppaggio
	static Gedcom gc2; // per il confronto degli aggiornamenti
	static int treeId2; // id dell'albero2 con gli aggiornamenti

	// Viene chiamato all'avvio dell'applicazione, e anche quando viene riavviata
	@Override
	public void onCreate() {
		super.onCreate();
		context = getApplicationContext();
		start(context);
	}

	public static void start(Context context) {
		File settingsFile = new File(context.getFilesDir(), "settings.json");
		// Rename "preferenze.json" to "settings.json" (introduced in version 0.8)
		File preferenzeFile = new File(context.getFilesDir(), "preferenze.json");
		if( preferenzeFile.exists() && !settingsFile.exists()) {
			if( !preferenzeFile.renameTo(settingsFile) ) {
				Toast.makeText(context, R.string.something_wrong, Toast.LENGTH_LONG).show();
				settingsFile = preferenzeFile;
			}
		}
		try {
			String jsonString = FileUtils.readFileToString(settingsFile, "UTF-8");
			jsonString = updateSettings(jsonString);
			Gson gson = new Gson();
			settings = gson.fromJson(jsonString, Settings.class);
		} catch( Exception e ) {
			// At first boot avoid to show the toast saying that settings.json doesn't exist
			if( !(e instanceof java.io.FileNotFoundException) ) {
				Toast.makeText(context, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
			}
		}
		if( settings == null ) {
			settings = new Settings();
			settings.init();
			// Restore possibly lost trees
			for( File file : context.getFilesDir().listFiles() ) {
				String name = file.getName();
				if( file.isFile() && name.endsWith(".json") ) {
					try {
						int treeId = Integer.parseInt(name.substring(0, name.lastIndexOf(".json")));
						File mediaDir = new File(context.getExternalFilesDir(null), String.valueOf(treeId));
						settings.trees.add(new Settings.Tree(treeId, String.valueOf(treeId),
								mediaDir.exists() ? mediaDir.getPath() : null,
								0, 0, null, null, 0));
					} catch( Exception e ) {
					}
				}
			}
			// Some tree has been restored
			if( !settings.trees.isEmpty() )
				settings.referrer = null;
			settings.save();
		}
		// Diagram settings were (probably) introduced in version 0.7.4
		if( settings.diagram == null ) {
			settings.diagram = new Settings.Diagram().init();
			settings.save();
		}
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

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		// Keep the app locale if system language is changed while the app is running
		Locale appLocale = AppCompatDelegate.getApplicationLocales().get(0);
		if( appLocale != null ) {
			Locale.setDefault(appLocale); // Keep the gedcom.jar library locale
			newConfig.setLocale(appLocale);
			getApplicationContext().getResources().updateConfiguration(newConfig, null); // Keep global context
		}
		super.onConfigurationChanged(newConfig);
	}
}
