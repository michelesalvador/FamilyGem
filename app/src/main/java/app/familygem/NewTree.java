package app.familygem;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;

import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.CharacterSet;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.GedcomVersion;
import org.folg.gedcom.model.Generator;
import org.folg.gedcom.model.Header;
import org.folg.gedcom.parser.JsonParser;
import org.folg.gedcom.parser.ModelParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class NewTree extends BaseActivity {

    View wheel;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.albero_nuovo);
        wheel = findViewById(R.id.nuovo_circolo);
        String referrer = Global.settings.referrer; // Dataid from a share
        boolean existingDataId = referrer != null && referrer.matches("[0-9]{14}");

        // Download the shared tree
        Button downloadShared = findViewById(R.id.bottone_scarica_condiviso);
        if (existingDataId)
            // It doesn't need any permissions because it only downloads and unpacks to the app's external storage
            downloadShared.setOnClickListener(v -> FacadeActivity.downloadShared(this, referrer, wheel));
        else
            downloadShared.setVisibility(View.GONE);

        // Create an empty tree
        Button emptyTree = findViewById(R.id.bottone_albero_vuoto);
        if (existingDataId) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                emptyTree.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.primarioChiaro)));
        }
        emptyTree.setOnClickListener(v -> {
            View messageView = LayoutInflater.from(this).inflate(R.layout.albero_nomina, null);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setView(messageView).setTitle(R.string.title);
            TextView textView = messageView.findViewById(R.id.nuovo_nome_testo);
            textView.setText(R.string.modify_later);
            textView.setVisibility(View.VISIBLE);
            EditText newName = messageView.findViewById(R.id.nuovo_nome_albero);
            builder.setPositiveButton(R.string.create, (dialog, id) -> newTree(newName.getText().toString()))
                    .setNeutralButton(R.string.cancel, null).create().show();
            newName.setOnEditorActionListener((view, action, event) -> {
                if (action == EditorInfo.IME_ACTION_DONE) {
                    newTree(newName.getText().toString());
                    return true; // complete save() actions
                }
                return false; // Any other actions that do not exist
            });
            messageView.postDelayed(() -> {
                newName.requestFocus();
                InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.showSoftInput(newName, InputMethodManager.SHOW_IMPLICIT);
            }, 300);
        });

        Button downloadExample = findViewById(R.id.bottone_scarica_esempio);
        // It doesn't need permissions
        downloadExample.setOnClickListener(v -> downloadExample());

        Button importGedcom = findViewById(R.id.bottone_importa_gedcom);
        importGedcom.setOnClickListener(v -> {
            int perm = ContextCompat.checkSelfPermission(v.getContext(), Manifest.permission.READ_EXTERNAL_STORAGE);
            if (perm == PackageManager.PERMISSION_DENIED)
                ActivityCompat.requestPermissions((AppCompatActivity) v.getContext(), new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1390);
            else if (perm == PackageManager.PERMISSION_GRANTED)
                importGedcom();
        });

        Button fetchBackup = findViewById(R.id.bottone_recupera_backup);
        fetchBackup.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/zip");
            startActivityForResult(intent, 219);
        });
    }

    /**
     * Process response to permit requests
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) { // If request is cancelled, the result arrays are empty
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == 1390) {
                importGedcom();
            }
        }
    }

    /**
     * Create a brand new tree
     */
    void newTree(String title) {
        int num = Global.settings.max() + 1;
        File jsonFile = new File(getFilesDir(), num + ".json");
        Global.gc = new Gedcom();
        Global.gc.setHeader(createHeader(jsonFile.getName()));
        Global.gc.createIndexes();
        JsonParser jp = new JsonParser();
        try {
            FileUtils.writeStringToFile(jsonFile, jp.toJson(Global.gc), "UTF-8");
        } catch (Exception e) {
            Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        Global.settings.add(new Settings.Tree(num, title, null, 0, 0, null, null, 0));
        Global.settings.openTree = num;
        Global.settings.save();
        onBackPressed();
        Toast.makeText(this, R.string.tree_created, Toast.LENGTH_SHORT).show();
    }

    /**
     * Download the Simpsons zip file from Google Drive to the external cache of the app, therefore without the need for permissions
     */
    void downloadExample() {
        wheel.setVisibility(View.VISIBLE);
        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        // Avoid multiple downloads
        Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_RUNNING));
        if (cursor.moveToFirst()) {
            cursor.close();
            findViewById(R.id.bottone_scarica_esempio).setEnabled(false);
            return;
        }
        String url = "https://drive.google.com/uc?export=download&id=1FT-60avkxrHv6G62pxXs9S6Liv5WkkKf";
        String zipPath = getExternalCacheDir() + "/the_Simpsons.zip";
        File fileZip = new File(zipPath);
        if (fileZip.exists())
            fileZip.delete();
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setTitle(getString(R.string.simpsons_tree))
                .setDescription(getString(R.string.family_gem_example))
                .setMimeType("application/zip")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.parse("file://" + zipPath));
        downloadManager.enqueue(request);
        BroadcastReceiver onComplete = new BroadcastReceiver() {
            @Override
            public void onReceive(Context contesto, Intent intent) {
                unZip(contesto, zipPath, null);
                unregisterReceiver(this);
            }
        };
        registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        // ACTION_DOWNLOAD_COMPLETE means the completion of ANY download that is in progress, not just this one.
    }

    /**
     * Unzip a ZIP file in the device storage
     * Used equally by: Simpsons example, backup files and shared trees
     */
    static boolean unZip(Context context, String zipPath, Uri zipUri) {
        int treeNumber = Global.settings.max() + 1;
        File mediaDir = context.getExternalFilesDir(String.valueOf(treeNumber));
        String sourceDir = context.getApplicationInfo().sourceDir;
        if (!sourceDir.startsWith("/data/")) { // App installed not in internal memory (hopefully moved to SD-card)
            File[] externalFilesDirs = context.getExternalFilesDirs(String.valueOf(treeNumber));
            if (externalFilesDirs.length > 1) {
                mediaDir = externalFilesDirs[1];
            }
        }
        try {
            InputStream is;
            if (zipPath != null)
                is = new FileInputStream(zipPath);
            else
                is = context.getContentResolver().openInputStream(zipUri);
            ZipInputStream zis = new ZipInputStream(is);
            ZipEntry zipEntry;
            int len;
            byte[] buffer = new byte[1024];
            File newFile;
            while ((zipEntry = zis.getNextEntry()) != null) {
                if (zipEntry.getName().equals("tree.json"))
                    newFile = new File(context.getFilesDir(), treeNumber + ".json");
                else if (zipEntry.getName().equals("settings.json"))
                    newFile = new File(context.getCacheDir(), "settings.json");
                else // It's a file from the 'media' folder
                    newFile = new File(mediaDir, zipEntry.getName().replace("media/", ""));
                FileOutputStream fos = new FileOutputStream(newFile);
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }
            zis.closeEntry();
            zis.close();
            // Reads the settings and saves them in the preferences
            File settingsFile = new File(context.getCacheDir(), "settings.json");
            String json = FileUtils.readFileToString(settingsFile, "UTF-8");
            json = updateLanguage(json);
            Gson gson = new Gson();
            Settings.ZippedTree zipped = gson.fromJson(json, Settings.ZippedTree.class);
            Settings.Tree tree = new Settings.Tree(treeNumber, zipped.title, mediaDir.getPath(),
                    zipped.persons, zipped.generations, zipped.root, zipped.shares, zipped.grade);
            Global.settings.add(tree);
            settingsFile.delete();
            // Sharing tree intended for comparison
            if (zipped.grade == 9 && compare(context, tree, false)) {
                tree.grade = 20; // brands it as derivative
            }
            // The download was done from the referrer dialog in Trees
            if (context instanceof TreesActivity) {
                TreesActivity treesPage = (TreesActivity) context;
                treesPage.runOnUiThread(() -> {
                    treesPage.wheel.setVisibility(View.GONE);
                    treesPage.updateList();
                });
            } else // Example tree (Simpson) or backup (from FacadeActivity or NewTree)
                context.startActivity(new Intent(context, TreesActivity.class));
            Global.settings.save();
            U.toast((Activity) context, R.string.tree_imported_ok);
            return true;
        } catch (Exception e) {
            U.toast((Activity) context, e.getLocalizedMessage());
        }
        return false;
    }

    /**
     * Replace Italian with English in the Json settings of ZIP backup
     * Added in Family Gem 0.8
     */
    static String updateLanguage(String json) {
        return json.replace("\"generazioni\":", "\"generations\":")
                .replace("\"grado\":", "\"grade\":")
                .replace("\"individui\":", "\"persons\":")
                .replace("\"radice\":", "\"root\":")
                .replace("\"titolo\":", "\"title\":")
                .replace("\"condivisioni\":", "\"shares\":")
                .replace("\"data\":", "\"dateId\":");
    }

    /**
	 * Choose a Gedcom file to import
	 * */
    void importGedcom() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        // KitKat disables .ged files in Download folder if type is 'application/*'
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT)
            intent.setType("*/*");
        else
            intent.setType("application/*");
        startActivityForResult(intent, 630);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Import a chosen Gedcom file with SAF
        if (resultCode == RESULT_OK && requestCode == 630) {
            try {
                // Read the input
                Uri uri = data.getData();
                InputStream input = getContentResolver().openInputStream(uri);
                Gedcom gedcom = new ModelParser().parseGedcom(input);
                if (gedcom.getHeader() == null) {
                    Toast.makeText(this, R.string.invalid_gedcom, Toast.LENGTH_LONG).show();
                    return;
                }
                gedcom.createIndexes(); // necessary to then calculate the generations
                // Save the json file
                int newNumber = Global.settings.max() + 1;
                PrintWriter printWriter = new PrintWriter(getFilesDir() + "/" + newNumber + ".json");
                JsonParser jsonParser = new JsonParser();
                printWriter.print(jsonParser.toJson(gedcom));
                printWriter.close();
                // Folder tree name and path
                String path = F.uriFilePath(uri);
                String treeName;
                String folderPath = null;
                if (path != null && path.lastIndexOf('/') > 0) { // is a full path to the gedcom file
                    File fileGedcom = new File(path);
                    folderPath = fileGedcom.getParent();
                    treeName = fileGedcom.getName();
                } else if (path != null) { // It's just the name of the file 'family.ged'
                    treeName = path;
                } else // null path
                    treeName = getString(R.string.tree) + " " + newNumber;
                if (treeName.lastIndexOf('.') > 0) // Remove the extension
                    treeName = treeName.substring(0, treeName.lastIndexOf('.'));
                // Save the settings in preferences
                String idRadice = U.findRoot(gedcom);
                Global.settings.add(new Settings.Tree(newNumber, treeName, folderPath,
                        gedcom.getPeople().size(), TreeInfoActivity.countGenerations(gedcom, idRadice), idRadice, null, 0));
                new Notifier(this, gedcom, newNumber, Notifier.What.CREATE);
                // If necessary it proposes to show the advanced functions
                if (!gedcom.getSources().isEmpty() && !Global.settings.expert) {
                    new AlertDialog.Builder(this).setMessage(R.string.complex_tree_advanced_tools)
                            .setPositiveButton(android.R.string.ok, (dialog, i) -> {
                                Global.settings.expert = true;
                                Global.settings.save();
                                finishImportingGedcom();
                            }).setNegativeButton(android.R.string.cancel, (dialog, i) -> finishImportingGedcom())
                            .show();
                } else
                    finishImportingGedcom();
            } catch (Exception e) {
                Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        }

        // Try to unzip the retrieved backup ZIP file
        if (resultCode == RESULT_OK && requestCode == 219) {
            try {
                Uri uri = data.getData();
                boolean settingsFileExists = false;
                final ZipInputStream zis = new ZipInputStream(getContentResolver().openInputStream(uri));
                ZipEntry zipEntry;
                while ((zipEntry = zis.getNextEntry()) != null) {
                    if (zipEntry.getName().equals("settings.json")) {
                        settingsFileExists = true;
                        break;
                    }
                }
                zis.closeEntry();
                zis.close();
                if (settingsFileExists) {
                    unZip(this, null, uri);
					/* todo in the strange case that the same tree suggested by the referrer is imported with the ZIP backup
						   you should cancel the referrer:
						if( decomprimiZip( this, null, uri ) ){
						String idData = Esportatore.estraiNome(uri); // che però non è statico
						if( Global.preferenze.referrer.equals(idData) ) {
							Global.preferenze.referrer = null;
							Global.preferenze.salva();
						}}
					 */
                } else
                    Toast.makeText(NewTree.this, R.string.backup_invalid, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(NewTree.this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    void finishImportingGedcom() {
        onBackPressed();
        Toast.makeText(this, R.string.tree_imported_ok, Toast.LENGTH_SHORT).show();
    }

    /**
	 * Compare the posting dates of existing trees
	 * If it finds at least one original tree among the existing ones, it returns true
	 * and eventually open the comparator
	 * */
    static boolean compare(Context context, Settings.Tree tree2, boolean openCompareActivity) {
        if (tree2.shares != null)
            for (Settings.Tree alb : Global.settings.trees)
                if (alb.id != tree2.id && alb.shares != null && alb.grade != 20 && alb.grade != 30)
                    for (int i = alb.shares.size() - 1; i >= 0; i--) { // The shares from last to first
                        Settings.Share share = alb.shares.get(i);
                        for (Settings.Share share2 : tree2.shares)
                            if (share.dateId != null && share.dateId.equals(share2.dateId)) {
                                if (openCompareActivity)
                                    context.startActivity(new Intent(context, CompareActivity.class)
                                            .putExtra("idAlbero", alb.id)
                                            .putExtra("idAlbero2", tree2.id)
                                            .putExtra("idData", share.dateId)
                                    );
                                return true;
                            }
                    }
        return false;
    }

    /**
	 * Create the standard header for this app
	 * */
    public static Header createHeader(String filename) {
        Header text = new Header();
        Generator app = new Generator();
        app.setValue("FAMILY_GEM");
        app.setName("Family Gem");
        app.setVersion(BuildConfig.VERSION_NAME);
        text.setGenerator(app);
        text.setFile(filename);
        GedcomVersion version = new GedcomVersion();
        version.setForm("LINEAGE-LINKED");
        version.setVersion("5.5.1");
        text.setGedcomVersion(version);
        CharacterSet charSet = new CharacterSet();
        charSet.setValue("UTF-8");
        text.setCharacterSet(charSet);
        Locale loc = new Locale(Locale.getDefault().getLanguage());
        // There is also Resources.getSystem().getConfiguration().locale.getLanguage() which returns the same 'it'
        text.setLanguage(loc.getDisplayLanguage(Locale.ENGLISH));    // ok takes system language in english, not local language
        // in the header there are two data fields: TRANSMISSION DATE a bit forcibly can contain the date of the last modification
        text.setDateTime(U.actualDateTime());
        return text;
    }

    /**
	 * Back arrow in the toolbar like the hardware one
	 * */
    @Override
    public boolean onOptionsItemSelected(MenuItem i) {
        onBackPressed();
        return true;
    }
}