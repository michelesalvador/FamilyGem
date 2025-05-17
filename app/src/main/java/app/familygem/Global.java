package app.familygem;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.multidex.MultiDexApplication;

import com.google.gson.Gson;

import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Global extends MultiDexApplication {

    private Thread.UncaughtExceptionHandler defaultExceptionHandler;
    public static Gedcom gc;
    public static Context context;
    public static Application application;
    public static Settings settings;
    public static String indi; // ID of the selected person displayed across the app
    /**
     * Which parents' family to show in the diagram, usually 0.
     */
    public static int familyNum;
    /**
     * There has been an editing in ProfileActivity, DetailActivity or CropImageActivity
     * and therefore the content of the previous pages must be updated.
     */
    public static boolean edited;
    /**
     * The Gedcom content has been changed and needs to be saved.
     */
    public static boolean shouldSave;
    /**
     * File of the photo taken by a camera app.
     */
    public static File cameraDestination;
    /**
     * Media opened by FileActivity or by CropImageActivity to be viewed and edited.
     */
    public static Media editedMedia;
    /**
     * File path of cropped files to invalidate Glide cache.
     */
    public static Map<String, Integer> croppedPaths = new HashMap<>();
    public static Gedcom gc2; // A shared tree, for comparison of updates
    public static int treeId2; // ID of the shared tree
    public static BackupViewModel backupViewModel;

    /**
     * This is called when the application starts, and also when it is restarted.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        // Exception handler
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(exceptionHandler);
        // App context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) context = getApplicationContext();
        else context = ContextCompat.getContextForLanguage(getApplicationContext()); // Context with app locale
        application = this;
        // App settings
        File settingsFile = new File(context.getFilesDir(), "settings.json");
        // Renames "preferenze.json" to "settings.json" (introduced in version 0.8)
        File preferencesFile = new File(context.getFilesDir(), "preferenze.json");
        if (preferencesFile.exists() && !settingsFile.exists()) {
            if (!preferencesFile.renameTo(settingsFile)) {
                Toast.makeText(context, R.string.something_wrong, Toast.LENGTH_LONG).show();
                settingsFile = preferencesFile;
            }
        }
        try {
            String jsonString = FileUtils.readFileToString(settingsFile, "UTF-8");
            jsonString = updateSettings(jsonString);
            Gson gson = new Gson();
            settings = gson.fromJson(jsonString, Settings.class);
        } catch (Exception e) {
            // At first boot avoids to show the toast saying that settings.json doesn't exist
            if (!(e instanceof java.io.FileNotFoundException)) {
                Toast.makeText(context, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        }
        if (settings == null) {
            settings = new Settings();
            settings.init();
            // Restores possibly lost trees
            for (File file : context.getFilesDir().listFiles()) {
                String name = file.getName();
                if (file.isFile() && name.endsWith(".json")) {
                    try {
                        int treeId = Integer.parseInt(name.substring(0, name.lastIndexOf(".json")));
                        settings.trees.add(new Settings.Tree(treeId, String.valueOf(treeId),
                                0, 0, null, null, null, 0));
                    } catch (Exception ignored) {
                    }
                }
            }
            // Some tree has been restored
            if (!settings.trees.isEmpty())
                settings.referrer = null;
            settings.save();
        }
        boolean toBeSaved = false;
        // Diagram settings were (probably) introduced in version 0.7.4
        if (settings.diagram == null) {
            settings.diagram = new Settings.DiagramSettings().init();
            toBeSaved = true;
        }
        // Tree settings were introduced in version 1.0.1
        for (Settings.Tree tree : settings.trees) {
            if (tree.settings == null) {
                tree.settings = new Settings.TreeSettings();
                toBeSaved = true;
            }
        }
        // Birthday notification time was introduced in version 1.1
        if (settings.notifyTime == null) {
            settings.notifyTime = "12:00";
            toBeSaved = true;
        }
        // Local backup was introduced in version 1.1
        if (settings.backupUri == null) {
            settings.backup = true;
            settings.backupUri = BackupViewModel.NO_URI;
            for (Settings.Tree tree : settings.trees) tree.backup = true;
            toBeSaved = true;
        }
        if (toBeSaved) settings.save();

        backupViewModel = new BackupViewModel(this);
    }

    /**
     * Intercepts crashes on the main thread.
     */
    private final Thread.UncaughtExceptionHandler exceptionHandler = (thread, exception) -> {
        // Disables auto-load of last opened tree
        if (Global.settings.loadTree) {
            Global.settings.loadTree = false;
            Global.settings.save();
        }
        defaultExceptionHandler.uncaughtException(thread, exception);
    };

    /**
     * Modifications to the text coming from files/settings.json
     */
    private static String updateSettings(String json) {
        // Little numbers in diagram settings (introduced in version 1.1)
        if (!(json.contains("\"numbers\":true") || json.contains("\"numbers\":false"))) {
            json = json.replace("\"diagram\":{", "\"diagram\":{\"numbers\":true,");
        }
        return json // Version 0.8 added new settings for the diagram
                .replace("\"siblings\":true", "siblings:2,cousins:2,spouses:true")
                .replace("\"siblings\":false", "siblings:0,cousins:0,spouses:true")

                // Italian translated to English (version 0.8)
                .replace("\"alberi\":", "\"trees\":")
                .replace("\"idAprendo\":", "\"openTree\":")
                .replace("\"autoSalva\":", "\"autoSave\":")
                .replace("\"caricaAlbero\":", "\"loadTree\":")
                .replace("\"esperto\":", "\"expert\":")
                .replace("\"nome\":", "\"title\":")
                .replace("\"cartelle\":", "\"dirs\":")
                .replace("\"individui\":", "\"persons\":")
                .replace("\"generazioni\":", "\"generations\":")
                .replace("\"radice\":", "\"root\":")
                .replace("\"condivisioni\":", "\"shares\":")
                .replace("\"radiceCondivisione\":", "\"shareRoot\":")
                .replace("\"grado\":", "\"grade\":")
                .replace("\"data\":", "\"dateId\":");
    }
}
