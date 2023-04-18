package app.familygem;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

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

import app.familygem.constant.Extra;
import app.familygem.share.CompareActivity;
import app.familygem.util.ChangeUtils;

public class NewTreeActivity extends BaseActivity {

    ProgressBar progress;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.new_tree);
        progress = findViewById(R.id.new_progress);
        String referrer = Global.settings.referrer; // Dataid proveniente da una condivisione
        boolean esisteDataId = referrer != null && referrer.matches("[0-9]{14}");

        // Scarica l'albero condiviso
        Button scaricaCondiviso = findViewById(R.id.new_download_shared);
        if (esisteDataId)
            // Non ha bisogno di permessi perché scarica e decomprime solo nello storage esterno dell'app
            scaricaCondiviso.setOnClickListener(v -> LauncherActivity.downloadShared(this, referrer, progress));
        else
            scaricaCondiviso.setVisibility(View.GONE);

        // Create an empty tree
        Button emptyTree = findViewById(R.id.new_empty_tree);
        if (esisteDataId) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                emptyTree.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.primary_light)));
        }
        emptyTree.setOnClickListener(v -> {
            View dialogView = LayoutInflater.from(this).inflate(R.layout.albero_nomina, null);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setView(dialogView).setTitle(R.string.title);
            TextView textView = dialogView.findViewById(R.id.nuovo_nome_testo);
            textView.setText(R.string.modify_later);
            textView.setVisibility(View.VISIBLE);
            EditText nuovoNome = dialogView.findViewById(R.id.nuovo_nome_albero);
            builder.setPositiveButton(R.string.create, (dialog, id) -> newTree(nuovoNome.getText().toString()))
                    .setNeutralButton(R.string.cancel, null).create().show();
            nuovoNome.setOnEditorActionListener((view, action, event) -> {
                if (action == EditorInfo.IME_ACTION_DONE) {
                    newTree(nuovoNome.getText().toString());
                    return true; // completa le azioni di salva()
                }
                return false; // Eventuali altri action che non esistono
            });
            dialogView.postDelayed(() -> {
                nuovoNome.requestFocus();
                InputMethodManager inputMethodManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.showSoftInput(nuovoNome, InputMethodManager.SHOW_IMPLICIT);
            }, 300);
        });

        Button downloadExample = findViewById(R.id.new_download_example);
        downloadExample.setOnClickListener(v -> downloadExample()); // It doesn't need permission

        // Let you choose a GEDCOM file to import
        Button importGedcom = findViewById(R.id.new_import_gedcom);
        importGedcom.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            // KitKat disables .ged files in the Download folder if the type is 'application/*'
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT)
                intent.setType("*/*");
            else
                intent.setType("application/*");
            startActivityForResult(intent, 630);
        });

        Button recoverBackup = findViewById(R.id.new_recover_backup);
        recoverBackup.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/zip");
            startActivityForResult(intent, 219);
        });
    }

    // Create a brand new tree
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
        Global.settings.addTree(new Settings.Tree(num, title, null, 0, 0, null, null, 0));
        Global.settings.openTree = num;
        Global.settings.save();
        onBackPressed();
        Toast.makeText(this, R.string.tree_created, Toast.LENGTH_SHORT).show();
    }

    // Download the Simpsons zip file from Google Drive into the app's external cache, so without permissions needed
    void downloadExample() {
        progress.setVisibility(View.VISIBLE);
        DownloadManager downloadManager = (DownloadManager)getSystemService(DOWNLOAD_SERVICE);
        // Avoid multiple downloads
        Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_RUNNING));
        if (cursor.moveToFirst()) {
            cursor.close();
            findViewById(R.id.new_download_example).setEnabled(false);
            return;
        }
        String url = "https://drive.google.com/uc?export=download&id=1FT-60avkxrHv6G62pxXs9S6Liv5WkkKf";
        String percorsoZip = getExternalCacheDir() + "/the_Simpsons.zip";
        File fileZip = new File(percorsoZip);
        if (fileZip.exists())
            fileZip.delete();
        DownloadManager.Request richiesta = new DownloadManager.Request(Uri.parse(url))
                .setTitle(getString(R.string.simpsons_tree))
                .setDescription(getString(R.string.family_gem_example))
                .setMimeType("application/zip")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.parse("file://" + percorsoZip));
        downloadManager.enqueue(richiesta);
        BroadcastReceiver alCompletamento = new BroadcastReceiver() {
            @Override
            public void onReceive(Context contesto, Intent intento) {
                unZip(contesto, percorsoZip, null);
                unregisterReceiver(this);
            }
        };
        registerReceiver(alCompletamento, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        // ACTION_DOWNLOAD_COMPLETE intende il completamento di QUALSIASI download che è in corso, non solo questo.
    }

    // Unzip a ZIP file in the device storage
    // Used equally by: Simpsons example, backup files and shared trees
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
            // Legge le impostazioni e le salva nelle preferenze
            File settingsFile = new File(context.getCacheDir(), "settings.json");
            String json = FileUtils.readFileToString(settingsFile, "UTF-8");
            json = updateLanguage(json);
            Gson gson = new Gson();
            Settings.ZippedTree zipped = gson.fromJson(json, Settings.ZippedTree.class);
            Settings.Tree tree = new Settings.Tree(treeNumber, zipped.title, mediaDir.getPath(),
                    zipped.persons, zipped.generations, zipped.root, zipped.shares, zipped.grade);
            Global.settings.addTree(tree);
            settingsFile.delete();
            // Albero proveniente da condivisione destinato al confronto
            if (zipped.grade == 9 && confronta(context, tree, false)) {
                tree.grade = 20; // lo marchia come derivato
            }
            // Il download è avvenuto dal dialogo del referrer in TreesActivity
            if (context instanceof TreesActivity) {
                TreesActivity treesPage = (TreesActivity)context;
                treesPage.runOnUiThread(() -> {
                    treesPage.progress.setVisibility(View.GONE);
                    treesPage.updateList();
                });
            } else // Example tree (Simpson) or backup tree (from LauncherActivity or from NewTreeActivity)
                context.startActivity(new Intent(context, TreesActivity.class));
            Global.settings.save();
            U.toast(R.string.tree_imported_ok);
            return true;
        } catch (Exception e) {
            U.toast(e.getLocalizedMessage());
        }
        return false;
    }

    // Replace Italian with English in the Json settings of ZIP backup
    // Added in Family Gem 0.8
    static String updateLanguage(String json) {
        json = json.replace("\"generazioni\":", "\"generations\":");
        json = json.replace("\"grado\":", "\"grade\":");
        json = json.replace("\"individui\":", "\"persons\":");
        json = json.replace("\"radice\":", "\"root\":");
        json = json.replace("\"titolo\":", "\"title\":");
        json = json.replace("\"condivisioni\":", "\"shares\":");
        json = json.replace("\"data\":", "\"dateId\":");
        return json;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Import a Gedcom file chosen with SAF
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
                gedcom.createIndexes(); // Necessary to calculate the generations
                // Save the JSON file
                int newNumber = Global.settings.max() + 1;
                PrintWriter printWriter = new PrintWriter(getFilesDir() + "/" + newNumber + ".json");
                JsonParser jsonParser = new JsonParser();
                printWriter.print(jsonParser.toJson(gedcom));
                printWriter.close();
                // Tree name and folder path
                String path = F.uriFilePath(uri);
                String treeName;
                String folderPath = null;
                if (path != null && path.lastIndexOf('/') > 0) { // It's a full path to the gedcom file
                    File fileGedcom = new File(path);
                    folderPath = fileGedcom.getParent();
                    treeName = fileGedcom.getName();
                } else if (path != null) { // It's just a file name, e.g. 'family.ged'
                    treeName = path;
                } else // Null path
                    treeName = getString(R.string.tree) + " " + newNumber;
                if (treeName.lastIndexOf('.') > 0) // Remove the extension
                    treeName = treeName.substring(0, treeName.lastIndexOf('.'));
                // Save the settings
                String rootId = U.trovaRadice(gedcom);
                Global.settings.addTree(new Settings.Tree(newNumber, treeName, folderPath,
                        gedcom.getPeople().size(), InfoActivity.quanteGenerazioni(gedcom, rootId), rootId, null, 0));
                new Notifier(this, gedcom, newNumber, Notifier.What.CREATE);
                // If necessary propose to show advanced tools
                if (!gedcom.getSources().isEmpty() && !Global.settings.expert) {
                    new AlertDialog.Builder(this).setMessage(R.string.complex_tree_advanced_tools)
                            .setPositiveButton(android.R.string.ok, (dialog, i) -> {
                                Global.settings.expert = true;
                                Global.settings.save();
                                concludiImportaGedcom();
                            }).setNegativeButton(android.R.string.cancel, (dialog, i) -> concludiImportaGedcom())
                            .show();
                } else
                    concludiImportaGedcom();
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
                    /* todo nello strano caso che viene importato col backup ZIP lo stesso albero suggerito dal referrer
                        bisognerebbe annullare il referrer:
                        if( decomprimiZip( this, null, uri ) ){
                        String idData = Esportatore.estraiNome(uri); // che però non è statico
                        if( Global.preferenze.referrer.equals(idData) ) {
                            Global.preferenze.referrer = null;
                            Global.preferenze.salva();
                        }}
                     */
                } else
                    Toast.makeText(NewTreeActivity.this, R.string.backup_invalid, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(NewTreeActivity.this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    void concludiImportaGedcom() {
        onBackPressed();
        Toast.makeText(this, R.string.tree_imported_ok, Toast.LENGTH_SHORT).show();
    }

    // Confronta le date di invio degli alberi esistenti
    // Se trova almeno un albero originario tra quelli esistenti restituisce true
    // ed eventualmente apre il comparatore
    static boolean confronta(Context contesto, Settings.Tree albero2, boolean apriCompara) {
        if (albero2.shares != null)
            for (Settings.Tree alb : Global.settings.trees)
                if (alb.id != albero2.id && alb.shares != null && alb.grade != 20 && alb.grade != 30)
                    for (int i = alb.shares.size() - 1; i >= 0; i--) { // Le condivisioni dall'ultima alla prima
                        Settings.Share share = alb.shares.get(i);
                        for (Settings.Share share2 : albero2.shares)
                            if (share.dateId != null && share.dateId.equals(share2.dateId)) {
                                if (apriCompara)
                                    contesto.startActivity(new Intent(contesto, CompareActivity.class)
                                            .putExtra(Extra.TREE_ID, alb.id)
                                            .putExtra(Extra.TREE_ID_2, albero2.id)
                                            .putExtra(Extra.DATE_ID, share.dateId)
                                    );
                                return true;
                            }
                    }
        return false;
    }

    // Crea l'intestazione standard per questa app
    public static Header createHeader(String fileName) {
        Header header = new Header();
        Generator app = new Generator();
        app.setValue("FAMILY_GEM");
        app.setName("Family Gem");
        app.setVersion(BuildConfig.VERSION_NAME);
        header.setGenerator(app);
        header.setFile(fileName);
        GedcomVersion version = new GedcomVersion();
        version.setForm("LINEAGE-LINKED");
        version.setVersion("5.5.1");
        header.setGedcomVersion(version);
        CharacterSet codifica = new CharacterSet();
        codifica.setValue("UTF-8");
        header.setCharacterSet(codifica);
        Locale loc = new Locale(Locale.getDefault().getLanguage());
        // C'è anche Resources.getSystem().getConfiguration().locale.getLanguage() che ritorna lo stesso 'it'
        header.setLanguage(loc.getDisplayLanguage(Locale.ENGLISH)); // ok prende la lingua di sistema in inglese, non nella lingua locale
        // in header ci sono due campi data: TRANSMISSION_DATE un po' forzatamente può contenere la data di ultima modifica
        header.setDateTime(ChangeUtils.INSTANCE.actualDateTime());
        return header;
    }

    // Freccia indietro nella toolbar come quella hardware
    @Override
    public boolean onOptionsItemSelected(MenuItem i) {
        onBackPressed();
        return true;
    }
}
