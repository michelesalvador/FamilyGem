package app.familygem;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;

import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;

import org.folg.gedcom.model.ChildRef;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaRef;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.ParentFamilyRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SpouseFamilyRef;
import org.folg.gedcom.model.SpouseRef;
import org.folg.gedcom.parser.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.familygem.constant.Code;
import app.familygem.constant.Extra;
import app.familygem.share.SharingActivity;
import app.familygem.visitor.MediaList;

public class TreesActivity extends AppCompatActivity {

    List<Map<String, String>> treeList;
    SimpleAdapter adapter;
    View progress;
    SpeechBubble welcome;
    Exporter exporter;
    private boolean autoOpenedTree; // To open automatically the tree at startup only once
    // The birthday notification IDs are stored to display the corresponding person only once
    private ArrayList<Integer> consumedNotifications = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.trees);
        ListView listView = findViewById(R.id.trees_list);
        progress = findViewById(R.id.trees_progress);
        welcome = new SpeechBubble(this, R.string.tap_add_tree);
        exporter = new Exporter(this);

        // Al primissimo avvio
        String referrer = Global.settings.referrer;
        if (referrer != null && referrer.equals("start"))
            recuperaReferrer();
            // Se è stato memorizzato un dataid (che appena usato sarà cancellato)
        else if (referrer != null && referrer.matches("[0-9]{14}")) {
            new AlertDialog.Builder(this).setTitle(R.string.a_new_tree)
                    .setMessage(R.string.you_can_download)
                    .setPositiveButton(R.string.download, (dialog, id) -> {
                        LauncherActivity.downloadShared(this, referrer, progress);
                    }).setNeutralButton(R.string.cancel, null).show();
        } // If there is no tree
        else if (Global.settings.trees.isEmpty())
            welcome.show();

        if (savedState != null) {
            autoOpenedTree = savedState.getBoolean("autoOpenedTree");
            consumedNotifications = savedState.getIntegerArrayList("consumedNotifications");
        }

        if (Global.settings.trees != null) {

            // Lista degli alberi genealogici
            treeList = new ArrayList<>();

            // Dà i dati in pasto all'adattatore
            adapter = new SimpleAdapter(this, treeList,
                    R.layout.pezzo_albero,
                    new String[]{"titolo", "dati"},
                    new int[]{R.id.albero_titolo, R.id.albero_dati}) {
                // Individua ciascuna vista dell'elenco
                @Override
                public View getView(final int position, View convertView, ViewGroup parent) {
                    View treeView = super.getView(position, convertView, parent);
                    TextView titleView = treeView.findViewById(R.id.albero_titolo);
                    titleView.setTextColor(getResources().getColor(R.color.text));
                    TextView detailView = treeView.findViewById(R.id.albero_dati);
                    detailView.setTextColor(getResources().getColor(R.color.gray_text));
                    int treeId = Integer.parseInt(treeList.get(position).get("id"));
                    Settings.Tree tree = Global.settings.getTree(treeId);
                    boolean derived = tree.grade == 20;
                    boolean exhausted = tree.grade == 30;
                    if (derived) {
                        treeView.setBackgroundColor(getResources().getColor(R.color.accent_medium));
                        detailView.setTextColor(getResources().getColor(R.color.text));
                        treeView.setOnClickListener(v -> {
                            if (!NewTreeActivity.confronta(TreesActivity.this, tree, true)) {
                                tree.grade = 10; // viene retrocesso
                                Global.settings.save();
                                aggiornaLista();
                                Toast.makeText(TreesActivity.this, R.string.something_wrong, Toast.LENGTH_LONG).show();
                            }
                        });
                    } else if (exhausted) {
                        treeView.setBackgroundColor(getResources().getColor(R.color.consumed));
                        titleView.setTextColor(getResources().getColor(R.color.gray_text));
                        treeView.setOnClickListener(v -> {
                            if (!NewTreeActivity.confronta(TreesActivity.this, tree, true)) {
                                tree.grade = 10; // viene retrocesso
                                Global.settings.save();
                                aggiornaLista();
                                Toast.makeText(TreesActivity.this, R.string.something_wrong, Toast.LENGTH_LONG).show();
                            }
                        });
                    } else {
                        treeView.setBackgroundColor(getResources().getColor(R.color.back_element));
                        treeView.setOnClickListener(v -> {
                            progress.setVisibility(View.VISIBLE);
                            if (!(Global.gc != null && treeId == Global.settings.openTree)) { // se non è già aperto
                                if (!openGedcom(treeId, true)) {
                                    progress.setVisibility(View.GONE);
                                    return;
                                }
                            }
                            startActivity(new Intent(TreesActivity.this, Principal.class));
                        });
                    }
                    treeView.findViewById(R.id.albero_menu).setOnClickListener(view -> {
                        boolean exists = new File(getFilesDir(), treeId + ".json").exists();
                        PopupMenu popup = new PopupMenu(TreesActivity.this, view);
                        Menu menu = popup.getMenu();
                        if (treeId == Global.settings.openTree && Global.shouldSave)
                            menu.add(0, -1, 0, R.string.save);
                        if ((Global.settings.expert && derived) || (Global.settings.expert && exhausted))
                            menu.add(0, 0, 0, R.string.open);
                        if (!exhausted || Global.settings.expert)
                            menu.add(0, 1, 0, R.string.tree_info);
                        if ((!derived && !exhausted) || Global.settings.expert)
                            menu.add(0, 2, 0, R.string.rename);
                        if (exists && (!derived || Global.settings.expert) && !exhausted)
                            menu.add(0, 3, 0, R.string.media_folders);
                        if (!exhausted)
                            menu.add(0, 4, 0, R.string.find_errors);
                        if (exists && !derived && !exhausted) // non si può ri-condividere un albero ricevuto indietro, anche se sei esperto..
                            menu.add(0, 5, 0, R.string.share_tree);
                        if (exists && !derived && !exhausted && Global.settings.expert && Global.settings.trees.size() > 1)
                            menu.add(0, 6, 0, R.string.merge_tree);
                        if (exists && !derived && !exhausted && Global.settings.expert && Global.settings.trees.size() > 1
                                && tree.shares != null && tree.grade != 0) // cioè dev'essere 9 o 10
                            menu.add(0, 7, 0, R.string.compare);
                        if (exists && Global.settings.expert && !exhausted)
                            menu.add(0, 8, 0, R.string.export_gedcom);
                        if (exists && Global.settings.expert)
                            menu.add(0, 9, 0, R.string.make_backup);
                        menu.add(0, 10, 0, R.string.delete);
                        popup.show();
                        popup.setOnMenuItemClickListener(item -> {
                            int id = item.getItemId();
                            if (id == -1) { // Salva
                                U.saveJson(Global.gc, treeId);
                                Global.shouldSave = false;
                            } else if (id == 0) { // Apre un albero derivato
                                openGedcom(treeId, true);
                                startActivity(new Intent(TreesActivity.this, Principal.class));
                            } else if (id == 1) { // Info Gedcom
                                Intent intent = new Intent(TreesActivity.this, InfoActivity.class);
                                intent.putExtra(Extra.TREE_ID, treeId);
                                startActivity(intent);
                            } else if (id == 2) { // Rinomina albero
                                AlertDialog.Builder builder = new AlertDialog.Builder(TreesActivity.this);
                                View vistaMessaggio = getLayoutInflater().inflate(R.layout.albero_nomina, listView, false);
                                builder.setView(vistaMessaggio).setTitle(R.string.title);
                                EditText editaNome = vistaMessaggio.findViewById(R.id.nuovo_nome_albero);
                                editaNome.setText(treeList.get(position).get("titolo"));
                                AlertDialog dialogo = builder.setPositiveButton(R.string.rename, (dialog, i1) -> {
                                    Global.settings.renameTree(treeId, editaNome.getText().toString());
                                    aggiornaLista();
                                }).setNeutralButton(R.string.cancel, null).create();
                                editaNome.setOnEditorActionListener((textView, action, event) -> {
                                    if (action == EditorInfo.IME_ACTION_DONE)
                                        dialogo.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                                    return false;
                                });
                                dialogo.show();
                                vistaMessaggio.postDelayed(() -> {
                                    editaNome.requestFocus();
                                    editaNome.setSelection(editaNome.getText().length());
                                    InputMethodManager inputMethodManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                                    inputMethodManager.showSoftInput(editaNome, InputMethodManager.SHOW_IMPLICIT);
                                }, 300);
                            } else if (id == 3) { // Media folders
                                startActivity(new Intent(TreesActivity.this, MediaFoldersActivity.class)
                                        .putExtra(Extra.TREE_ID, treeId)
                                );
                            } else if (id == 4) { // Find errors
                                findErrors(treeId, false);
                            } else if (id == 5) { // Share tree
                                startActivity(new Intent(TreesActivity.this, SharingActivity.class)
                                        .putExtra(Extra.TREE_ID, treeId)
                                );
                            } else if (id == 6) { // Merge with another tree
                                startActivity(new Intent(TreesActivity.this, MergeActivity.class)
                                        .putExtra(Extra.TREE_ID, treeId));
                            } else if (id == 7) { // Compare with existing trees
                                if (NewTreeActivity.confronta(TreesActivity.this, tree, false)) {
                                    tree.grade = 20;
                                    aggiornaLista();
                                } else
                                    Toast.makeText(TreesActivity.this, R.string.no_results, Toast.LENGTH_LONG).show();
                            } else if (id == 8) { // Export GEDCOM
                                if (exporter.openTree(treeId)) {
                                    final String[] mime = {"application/octet-stream"};
                                    final String[] ext = {"ged"};
                                    final int[] code = {Code.GEDCOM_FILE};
                                    int totMedia = exporter.countMediaFilesToAttach();
                                    if (totMedia > 0) {
                                        String[] choices = {getString(R.string.gedcom_media_zip, totMedia),
                                                getString(R.string.gedcom_only)};
                                        new AlertDialog.Builder(TreesActivity.this)
                                                .setTitle(R.string.export_gedcom)
                                                .setSingleChoiceItems(choices, -1, (dialog, selected) -> {
                                                    if (selected == 0) {
                                                        mime[0] = "application/zip";
                                                        ext[0] = "zip";
                                                        code[0] = Code.ZIPPED_GEDCOM_FILE;
                                                    }
                                                    F.saveDocument(TreesActivity.this, null, treeId, mime[0], ext[0], code[0]);
                                                    dialog.dismiss();
                                                }).show();
                                    } else {
                                        F.saveDocument(TreesActivity.this, null, treeId, mime[0], ext[0], code[0]);
                                    }
                                }
                            } else if (id == 9) { // Export ZIP backup
                                if (exporter.openTree(treeId))
                                    F.saveDocument(TreesActivity.this, null, treeId, "application/zip", "zip", Code.ZIP_BACKUP);
                            } else if (id == 10) { // Delete tree
                                new AlertDialog.Builder(TreesActivity.this).setMessage(R.string.really_delete_tree)
                                        .setPositiveButton(R.string.delete, (dialog, id1) -> {
                                            deleteTree(TreesActivity.this, treeId);
                                            aggiornaLista();
                                        }).setNeutralButton(R.string.cancel, null).show();
                            } else {
                                return false;
                            }
                            return true;
                        });
                    });
                    return treeView;
                }
            };
            listView.setAdapter(adapter);
            aggiornaLista();
        }

        // Barra personalizzata
        ActionBar bar = getSupportActionBar();
        View treesBar = getLayoutInflater().inflate(R.layout.trees_bar, null);
        treesBar.findViewById(R.id.trees_settings).setOnClickListener(v -> startActivity(
                new Intent(this, SettingsActivity.class))
        );
        bar.setCustomView(treesBar);
        bar.setDisplayShowCustomEnabled(true);

        // FAB
        findViewById(R.id.fab).setOnClickListener(v -> {
            welcome.hide();
            startActivity(new Intent(this, NewTreeActivity.class));
        });

        // Automatic load of last opened tree of previous session
        if (!birthdayNotifyTapped(getIntent()) && !autoOpenedTree
                && getIntent().getBooleanExtra(Extra.AUTO_LOAD_TREE, false) && Global.settings.openTree > 0) {
            listView.post(() -> {
                if (openGedcom(Global.settings.openTree, false)) {
                    progress.setVisibility(View.VISIBLE);
                    autoOpenedTree = true;
                    startActivity(new Intent(this, Principal.class));
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Nasconde la rotella, in particolare quando si ritorna indietro a questa activity
        progress.setVisibility(View.GONE);
    }

    // Essendo TreesActivity launchMode=singleTask, onRestart viene chiamato anche con startActivity (tranne il primo)
    // però ovviamente solo se TreesActivity ha chiamato onStop (facendo veloce chiama solo onPause)
    @Override
    protected void onRestart() {
        super.onRestart();
        aggiornaLista();
    }

    // New intent coming from a tapped notification
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        birthdayNotifyTapped(intent);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean("autoOpenedTree", autoOpenedTree);
        outState.putIntegerArrayList("consumedNotifications", consumedNotifications);
        super.onSaveInstanceState(outState);
    }

    // If a birthday notification was tapped loads the relative tree and returns true
    private boolean birthdayNotifyTapped(Intent intent) {
        int treeId = intent.getIntExtra(Notifier.TREE_ID_KEY, 0);
        int notifyId = intent.getIntExtra(Notifier.NOTIFY_ID_KEY, 0);
        if (treeId > 0 && !consumedNotifications.contains(notifyId)) {
            new Handler().post(() -> {
                if (openGedcom(treeId, true)) {
                    progress.setVisibility(View.VISIBLE);
                    Global.indi = intent.getStringExtra(Notifier.INDI_ID_KEY);
                    consumedNotifications.add(notifyId);
                    startActivity(new Intent(this, Principal.class));
                    new Notifier(this, Global.gc, treeId, Notifier.What.DEFAULT); // Actually delete present notification
                }
            });
            return true;
        }
        return false;
    }

    // Cerca di recuperare dal Play Store il dataID casomai l'app sia stata installata in seguito ad una condivisione
    // Se trova il dataid propone di scaricare l'albero condiviso
    void recuperaReferrer() {
        InstallReferrerClient irc = InstallReferrerClient.newBuilder(this).build();
        irc.startConnection(new InstallReferrerStateListener() {
            @Override
            public void onInstallReferrerSetupFinished(int risposta) {
                switch (risposta) {
                    case InstallReferrerClient.InstallReferrerResponse.OK:
                        try {
                            ReferrerDetails dettagli = irc.getInstallReferrer();
                            // Normalmente 'referrer' è una stringa type 'utm_source=google-play&utm_medium=organic'
                            // Ma se l'app è stata installata dal link nella pagina di condivisione sarà un data-id come '20191003215337'
                            String referrer = dettagli.getInstallReferrer();
                            if (referrer != null && referrer.matches("[0-9]{14}")) { // It's a dateId
                                Global.settings.referrer = referrer;
                                new AlertDialog.Builder(TreesActivity.this).setTitle(R.string.a_new_tree)
                                        .setMessage(R.string.you_can_download)
                                        .setPositiveButton(R.string.download, (dialog, id) -> {
                                            LauncherActivity.downloadShared(TreesActivity.this, referrer, progress);
                                        }).setNeutralButton(R.string.cancel, (di, id) -> welcome.show())
                                        .setOnCancelListener(d -> welcome.show()).show();
                            } else { // È qualunque altra cosa
                                Global.settings.referrer = null; // lo annulla così non lo cercherà più
                                welcome.show();
                            }
                            Global.settings.save();
                            irc.endConnection();
                        } catch (Exception e) {
                            U.toast(TreesActivity.this, e.getLocalizedMessage());
                        }
                        break;
                    // App Play Store inesistente sul device o comunque risponde in modo errato
                    case InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED:
                        // Questo non l'ho mai visto comparire
                    case InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE:
                        Global.settings.referrer = null; // così non torniamo più qui
                        Global.settings.save();
                        welcome.show();
                }
            }

            @Override
            public void onInstallReferrerServiceDisconnected() {
                // Mai visto comparire
                U.toast(TreesActivity.this, "Install Referrer Service Disconnected");
            }
        });
    }

    void aggiornaLista() {
        treeList.clear();
        for (Settings.Tree alb : Global.settings.trees) {
            Map<String, String> dato = new HashMap<>(3);
            dato.put("id", String.valueOf(alb.id));
            dato.put("titolo", alb.title);
            // Se Gedcom già aperto aggiorna i dati
            if (Global.gc != null && Global.settings.openTree == alb.id && alb.persons < 100)
                InfoActivity.refreshData(Global.gc, alb);
            dato.put("dati", writeData(this, alb));
            treeList.add(dato);
        }
        adapter.notifyDataSetChanged();
    }

    public static String writeData(Context contesto, Settings.Tree alb) {
        String dati = alb.persons + " " +
                contesto.getString(alb.persons == 1 ? R.string.person : R.string.persons).toLowerCase();
        if (alb.persons > 1 && alb.generations > 0)
            dati += " - " + alb.generations + " " +
                    contesto.getString(alb.generations == 1 ? R.string.generation : R.string.generations).toLowerCase();
        if (alb.media > 0)
            dati += " - " + alb.media + " " + contesto.getString(R.string.media).toLowerCase();
        return dati;
    }

    // Lightly open a Gedcom tree for different purposes
    public static Gedcom openGedcomTemporarily(int treeId, boolean putInGlobal) {
        Gedcom gc;
        if (Global.gc != null && Global.settings.openTree == treeId)
            gc = Global.gc;
        else {
            gc = readJson(treeId);
            if (putInGlobal) {
                Global.gc = gc; // To be able to use for example F.oneImage()
                Global.settings.openTree = treeId; // So Global.gc and Global.settings.openTree are consistent
            }
        }
        return gc;
    }

    // Apertura del Gedcom per editare tutto in Family Gem
    static boolean openGedcom(int treeId, boolean savePreferences) {
        Global.gc = readJson(treeId);
        if (Global.gc == null)
            return false;
        if (savePreferences) {
            Global.settings.openTree = treeId;
            Global.settings.save();
        }
        Global.indi = Global.settings.getCurrentTree().root;
        Global.familyNum = 0; // eventualmente lo resetta se era > 0
        Global.shouldSave = false; // eventualmente lo resetta se era true
        return true;
    }

    // Read the Json and return a Gedcom
    static Gedcom readJson(int treeId) {
        Gedcom gedcom;
        File file = new File(Global.context.getFilesDir(), treeId + ".json");
        StringBuilder text = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
            br.close();
        } catch (Exception | Error e) {
            String message = e instanceof OutOfMemoryError ? Global.context.getString(R.string.not_memory_tree) : e.getLocalizedMessage();
            Toast.makeText(Global.context, message, Toast.LENGTH_LONG).show();
            return null;
        }
        String json = text.toString();
        json = updateLanguage(json);
        gedcom = new JsonParser().fromJson(json);
        if (gedcom == null) {
            Toast.makeText(Global.context, R.string.no_useful_data, Toast.LENGTH_LONG).show();
            return null;
        }
        // This Notifier was introduced in version 0.9.1
        // Todo: Can be removed from here in the future because tree.birthdays will never more be null
        if (Global.settings.getTree(treeId).birthdays == null) {
            new Notifier(Global.context, gedcom, treeId, Notifier.What.CREATE);
        }
        return gedcom;
    }

    // Replace Italian with English in Json tree data
    // Introduced in Family Gem 0.8
    static String updateLanguage(String json) {
        json = json.replace("\"zona\":", "\"zone\":");
        json = json.replace("\"famili\":", "\"kin\":");
        json = json.replace("\"passato\":", "\"passed\":");
        return json;
    }

    public static void deleteTree(Context context, int treeId) {
        File treeFile = new File(context.getFilesDir(), treeId + ".json");
        treeFile.delete();
        File mediaDir = context.getExternalFilesDir(String.valueOf(treeId));
        deleteFilesAndDirs(mediaDir);
        if (Global.settings.openTree == treeId) {
            Global.gc = null;
        }
        new Notifier(context, null, treeId, Notifier.What.DELETE);
        Global.settings.deleteTree(treeId);
    }

    static void deleteFilesAndDirs(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles())
                deleteFilesAndDirs(child);
        }
        fileOrDirectory.delete();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == AppCompatActivity.RESULT_OK) {
            Uri uri = data.getData();
            boolean result = false;
            if (requestCode == Code.GEDCOM_FILE) { // Export GEDCOM file only
                result = exporter.exportGedcom(uri);
            } else if (requestCode == Code.ZIPPED_GEDCOM_FILE) { // Export GEDCOM with media in a ZIP file
                result = exporter.exportGedcomToZip(uri);
            } else if (requestCode == Code.ZIP_BACKUP) { // Export ZIP backup
                result = exporter.exportZipBackup(null, -1, uri);
            }
            if (result)
                Toast.makeText(TreesActivity.this, exporter.successMessage, Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(TreesActivity.this, exporter.errorMessage, Toast.LENGTH_LONG).show();
        }
    }

    Gedcom findErrors(final int treeId, final boolean correct) {
        Gedcom gc = readJson(treeId);
        if (gc == null) {
            // todo fai qualcosa per recuperare un file introvabile..?
            return null;
        }
        int errors = 0;
        int num;
        // Radice in preferenze
        Settings.Tree albero = Global.settings.getTree(treeId);
        Person radica = gc.getPerson(albero.root);
        // Radice punta ad una persona inesistente
        if (albero.root != null && radica == null) {
            if (!gc.getPeople().isEmpty()) {
                if (correct) {
                    albero.root = U.trovaRadice(gc);
                    Global.settings.save();
                } else errors++;
            } else { // albero senza persone
                if (correct) {
                    albero.root = null;
                    Global.settings.save();
                } else errors++;
            }
        }
        // Oppure non è indicata una radice in preferenze pur essendoci persone nell'albero
        if (radica == null && !gc.getPeople().isEmpty()) {
            if (correct) {
                albero.root = U.trovaRadice(gc);
                Global.settings.save();
            } else errors++;
        }
        // O in preferenze è indicata una radiceCondivisione che non esiste
        Person radicaCondivisa = gc.getPerson(albero.shareRoot);
        if (albero.shareRoot != null && radicaCondivisa == null) {
            if (correct) {
                albero.shareRoot = null; // la elimina e basta
                Global.settings.save();
            } else errors++;
        }
        // Cerca famiglie vuote o con un solo membro per eliminarle
        for (Family f : gc.getFamilies()) {
            if (f.getHusbandRefs().size() + f.getWifeRefs().size() + f.getChildRefs().size() <= 1) {
                if (correct) {
                    gc.getFamilies().remove(f); // così facendo lasci i ref negli individui orfani della famiglia a cui si riferiscono...
                    // ma c'è il resto del correttore che li risolve
                    break;
                } else errors++;
            }
        }
        // Silently delete empty list of families
        if (gc.getFamilies().isEmpty() && correct) {
            gc.setFamilies(null);
        }
        // Riferimenti da una persona alla famiglia dei genitori e dei figli
        for (Person p : gc.getPeople()) {
            for (ParentFamilyRef pfr : p.getParentFamilyRefs()) {
                Family fam = gc.getFamily(pfr.getRef());
                if (fam == null) {
                    if (correct) {
                        p.getParentFamilyRefs().remove(pfr);
                        break;
                    } else errors++;
                } else {
                    num = 0;
                    for (ChildRef cr : fam.getChildRefs())
                        if (cr.getRef() == null) {
                            if (correct) {
                                fam.getChildRefs().remove(cr);
                                break;
                            } else errors++;
                        } else if (cr.getRef().equals(p.getId())) {
                            num++;
                            if (num > 1 && correct) {
                                fam.getChildRefs().remove(cr);
                                break;
                            }
                        }
                    if (num != 1) {
                        if (correct && num == 0) {
                            p.getParentFamilyRefs().remove(pfr);
                            break;
                        } else errors++;
                    }
                }
            }
            // Remove empty list of parent family refs
            if (p.getParentFamilyRefs().isEmpty() && correct) {
                p.setParentFamilyRefs(null);
            }
            for (SpouseFamilyRef sfr : p.getSpouseFamilyRefs()) {
                Family fam = gc.getFamily(sfr.getRef());
                if (fam == null) {
                    if (correct) {
                        p.getSpouseFamilyRefs().remove(sfr);
                        break;
                    } else errors++;
                } else {
                    num = 0;
                    for (SpouseRef sr : fam.getHusbandRefs())
                        if (sr.getRef() == null) {
                            if (correct) {
                                fam.getHusbandRefs().remove(sr);
                                break;
                            } else errors++;
                        } else if (sr.getRef().equals(p.getId())) {
                            num++;
                            if (num > 1 && correct) {
                                fam.getHusbandRefs().remove(sr);
                                break;
                            }
                        }
                    for (SpouseRef sr : fam.getWifeRefs()) {
                        if (sr.getRef() == null) {
                            if (correct) {
                                fam.getWifeRefs().remove(sr);
                                break;
                            } else errors++;
                        } else if (sr.getRef().equals(p.getId())) {
                            num++;
                            if (num > 1 && correct) {
                                fam.getWifeRefs().remove(sr);
                                break;
                            }
                        }
                    }
                    if (num != 1) {
                        if (num == 0 && correct) {
                            p.getSpouseFamilyRefs().remove(sfr);
                            break;
                        } else errors++;
                    }
                }
            }
            // Remove empty list of spouse family refs
            if (p.getSpouseFamilyRefs().isEmpty() && correct) {
                p.setSpouseFamilyRefs(null);
            }
            // Riferimenti a Media inesistenti
            // ok ma SOLO per le persone, forse andrebbe fatto col Visitor per tutti gli altri
            num = 0;
            for (MediaRef mr : p.getMediaRefs()) {
                Media med = gc.getMedia(mr.getRef());
                if (med == null) {
                    if (correct) {
                        p.getMediaRefs().remove(mr);
                        break;
                    } else errors++;
                } else {
                    if (mr.getRef().equals(med.getId())) {
                        num++;
                        if (num > 1)
                            if (correct) {
                                p.getMediaRefs().remove(mr);
                                break;
                            } else errors++;
                    }
                }
            }
        }
        // References from each family to the persons belonging to it
        for (Family f : gc.getFamilies()) {
            // Husbands refs
            for (SpouseRef sr : f.getHusbandRefs()) {
                Person husband = gc.getPerson(sr.getRef());
                if (husband == null) {
                    if (correct) {
                        f.getHusbandRefs().remove(sr);
                        break;
                    } else errors++;
                } else {
                    num = 0;
                    for (SpouseFamilyRef sfr : husband.getSpouseFamilyRefs())
                        if (sfr.getRef() == null) {
                            if (correct) {
                                husband.getSpouseFamilyRefs().remove(sfr);
                                break;
                            } else errors++;
                        } else if (sfr.getRef().equals(f.getId())) {
                            num++;
                            if (num > 1 && correct) {
                                husband.getSpouseFamilyRefs().remove(sfr);
                                break;
                            }
                        }
                    if (num != 1) {
                        if (num == 0 && correct) {
                            f.getHusbandRefs().remove(sr);
                            break;
                        } else errors++;
                    }

                }
            }
            // Remove empty list of husband refs
            if (f.getHusbandRefs().isEmpty() && correct) {
                f.setHusbandRefs(null);
            }
            // Wives refs
            for (SpouseRef sr : f.getWifeRefs()) {
                Person wife = gc.getPerson(sr.getRef());
                if (wife == null) {
                    if (correct) {
                        f.getWifeRefs().remove(sr);
                        break;
                    } else errors++;
                } else {
                    num = 0;
                    for (SpouseFamilyRef sfr : wife.getSpouseFamilyRefs())
                        if (sfr.getRef() == null) {
                            if (correct) {
                                wife.getSpouseFamilyRefs().remove(sfr);
                                break;
                            } else errors++;
                        } else if (sfr.getRef().equals(f.getId())) {
                            num++;
                            if (num > 1 && correct) {
                                wife.getSpouseFamilyRefs().remove(sfr);
                                break;
                            }
                        }
                    if (num != 1) {
                        if (num == 0 && correct) {
                            f.getWifeRefs().remove(sr);
                            break;
                        } else errors++;
                    }
                }
            }
            // Remove empty list of wife refs
            if (f.getWifeRefs().isEmpty() && correct) {
                f.setWifeRefs(null);
            }
            // Children refs
            for (ChildRef cr : f.getChildRefs()) {
                Person child = gc.getPerson(cr.getRef());
                if (child == null) {
                    if (correct) {
                        f.getChildRefs().remove(cr);
                        break;
                    } else errors++;
                } else {
                    num = 0;
                    for (ParentFamilyRef pfr : child.getParentFamilyRefs())
                        if (pfr.getRef() == null) {
                            if (correct) {
                                child.getParentFamilyRefs().remove(pfr);
                                break;
                            } else errors++;
                        } else if (pfr.getRef().equals(f.getId())) {
                            num++;
                            if (num > 1 && correct) {
                                child.getParentFamilyRefs().remove(pfr);
                                break;
                            }
                        }
                    if (num != 1) {
                        if (num == 0 && correct) {
                            f.getChildRefs().remove(cr);
                            break;
                        } else errors++;
                    }
                }
            }
            // Remove empty list of child refs
            if (f.getChildRefs().isEmpty() && correct) {
                f.setChildRefs(null);
            }
        }
        // Null references in source towards media
        for (Source source : gc.getSources()) {
            for (MediaRef mediaRef : source.getMediaRefs()) {
                if (mediaRef.getRef() == null) {
                    if (correct) {
                        source.getMediaRefs().remove(mediaRef);
                        break;
                    } else errors++;
                }
            }
            // Removes empty list of media refs
            if (source.getMediaRefs().isEmpty() && correct) {
                source.setMediaRefs(null);
            }
        }

        // Aggiunge un tag 'TYPE' ai name type che non l'hanno
        for (Person person : gc.getPeople()) {
            for (Name name : person.getNames()) {
                if (name.getType() != null && name.getTypeTag() == null) {
                    if (correct) name.setTypeTag("TYPE");
                    else errors++;
                }
            }
        }

        // Aggiunge un tag 'FILE' ai Media che non l'hanno
        MediaList visitaMedia = new MediaList(gc, 0);
        gc.accept(visitaMedia);
        for (Media med : visitaMedia.list) {
            if (med.getFileTag() == null) {
                if (correct) med.setFileTag("FILE");
                else errors++;
            }
        }

        if (!correct) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setMessage(errors == 0 ? getText(R.string.all_ok) : getString(R.string.errors_found, errors));
            if (errors > 0) {
                dialog.setPositiveButton(R.string.correct, (dialogo, i) -> {
                    dialogo.cancel();
                    Gedcom gcCorretto = findErrors(treeId, true);
                    U.saveJson(gcCorretto, treeId);
                    Global.gc = null; // così se era aperto poi lo ricarica corretto
                    findErrors(treeId, false);    // riapre per ammirere il risultato
                    aggiornaLista();
                });
            }
            dialog.setNeutralButton(android.R.string.cancel, null).show();
        }
        return gc;
    }
}
