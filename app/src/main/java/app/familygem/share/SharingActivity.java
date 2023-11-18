package app.familygem.share;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Header;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Submitter;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import app.familygem.BaseActivity;
import app.familygem.BuildConfig;
import app.familygem.Exporter;
import app.familygem.Global;
import app.familygem.Principal;
import app.familygem.R;
import app.familygem.Settings;
import app.familygem.U;
import app.familygem.constant.Choice;
import app.familygem.constant.Extra;
import app.familygem.constant.Json;
import app.familygem.list.SubmittersFragment;
import app.familygem.util.ChangeUtils;
import app.familygem.util.TreeUtils;

public class SharingActivity extends BaseActivity {

    Gedcom gc;
    Settings.Tree tree;
    Exporter exporter;
    String submitterName;
    int accessible; // 0 = false, 1 = true
    String dateId;
    String submitterId;
    boolean successfulUpload; // To avoid uploading twice

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.sharing_activity);
        final int treeId = getIntent().getIntExtra(Extra.TREE_ID, 1);
        tree = Global.settings.getTree(treeId);
        final EditText titleEdit = findViewById(R.id.share_title);
        titleEdit.setText(tree.title);

        if (tree.grade == 10)
            ((TextView)findViewById(R.id.share_submitter_title)).setText(R.string.changes_submitter);

        exporter = new Exporter(this);
        exporter.openTree(treeId);
        gc = Global.gc;
        if (gc != null) {
            displayShareRoot();
            // Submitter name
            final Submitter[] submitter = new Submitter[1];
            // albero in Italia con submitter referenziato
            if (tree.grade == 0 && gc.getHeader() != null && gc.getHeader().getSubmitter(gc) != null)
                submitter[0] = gc.getHeader().getSubmitter(gc);
                // in Italia ci sono autori ma nessuno referenziato, prende l'ultimo
            else if (tree.grade == 0 && !gc.getSubmitters().isEmpty())
                submitter[0] = gc.getSubmitters().get(gc.getSubmitters().size() - 1);
                // in Australia ci sono autori freschi, ne prende uno
            else if (tree.grade == 10 && U.autoreFresco(gc) != null)
                submitter[0] = U.autoreFresco(gc);
            final EditText editaAutore = findViewById(R.id.share_submitter);
            submitterName = submitter[0] == null ? "" : submitter[0].getName();
            editaAutore.setText(submitterName);

            // Display an alert for the acknowledgment of sharing
            if (!Global.settings.shareAgreement) {
                new AlertDialog.Builder(this).setTitle(R.string.share_sensitive)
                        .setMessage(R.string.aware_upload_server)
                        .setPositiveButton(android.R.string.ok, (dialog, id) -> {
                            Global.settings.shareAgreement = true;
                            Global.settings.save();
                        }).setNeutralButton(R.string.remind_later, null).show();
            }

            // Raccoglie i dati della condivisione e posta al database
            findViewById(R.id.share_button).setOnClickListener(button -> {
                if (successfulUpload)
                    concludeShare();
                else {
                    if (controlla(titleEdit, R.string.please_title) || controlla(editaAutore, R.string.please_name))
                        return;

                    button.setEnabled(false);
                    findViewById(R.id.share_wheel).setVisibility(View.VISIBLE);

                    // Titolo dell'albero
                    String titoloEditato = titleEdit.getText().toString();
                    if (!tree.title.equals(titoloEditato)) {
                        tree.title = titoloEditato;
                        Global.settings.save();
                    }

                    // Aggiornamento del submitter
                    Header header = gc.getHeader();
                    if (header == null) {
                        header = TreeUtils.INSTANCE.createHeader(tree.id + ".json");
                        gc.setHeader(header);
                    } else
                        header.setDateTime(ChangeUtils.INSTANCE.actualDateTime());
                    if (submitter[0] == null) {
                        submitter[0] = SubmittersFragment.createSubmitter(null);
                    }
                    if (header.getSubmitterRef() == null) {
                        header.setSubmitterRef(submitter[0].getId());
                    }
                    String nomeAutoreEditato = editaAutore.getText().toString();
                    if (!nomeAutoreEditato.equals(submitterName)) {
                        submitterName = nomeAutoreEditato;
                        submitter[0].setName(submitterName);
                        ChangeUtils.INSTANCE.updateChangeDate(submitter[0]);
                    }
                    submitterId = submitter[0].getId();
                    TreeUtils.INSTANCE.saveJsonAsync(gc, treeId); // baypassando la preferenza di non salvare in atomatico

                    // Tree accessibility for app developer
                    CheckBox accessibleTree = findViewById(R.id.share_allow);
                    accessible = accessibleTree.isChecked() ? 1 : 0;

                    // Sends
                    if (!BuildConfig.PASS_KEY.isEmpty()) {
                        new SendingShareData().execute(this);
                    } else {
                        restore(this);
                        Toast.makeText(this, R.string.something_wrong, Toast.LENGTH_LONG).show();
                    }
                }
            });
        } else
            findViewById(R.id.share_layout).setVisibility(View.GONE);
    }

    // The person root of the tree
    View rootView;

    void displayShareRoot() {
        String rootId;
        if (tree.shareRoot != null && gc.getPerson(tree.shareRoot) != null)
            rootId = tree.shareRoot;
        else if (tree.root != null && gc.getPerson(tree.root) != null) {
            rootId = tree.root;
            tree.shareRoot = rootId; // per poter condividere subito l'albero senza cambiare la radice
        } else {
            rootId = U.trovaRadice(gc);
            tree.shareRoot = rootId;
        }
        Person person = gc.getPerson(rootId);
        if (person != null && tree.grade < 10) { // viene mostrata solo alla prima condivisione, non al ritorno
            LinearLayout rootLayout = findViewById(R.id.share_root);
            rootLayout.removeView(rootView);
            rootLayout.setVisibility(View.VISIBLE);
            rootView = U.placePerson(rootLayout, person);
            rootView.setOnClickListener(v -> {
                Intent intent = new Intent(this, Principal.class);
                intent.putExtra(Choice.PERSON, true);
                startActivityForResult(intent, 5007);
            });
        }
    }

    // Verifica che un campo sia compilato
    boolean controlla(EditText campo, int msg) {
        String testo = campo.getText().toString();
        if (testo.isEmpty()) {
            campo.requestFocus();
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(campo, InputMethodManager.SHOW_IMPLICIT);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    /**
     * Inserts the share summary in the database of www.familygem.app.
     * If all goes well creates the ZIP file with the tree and images.
     */
    static class SendingShareData extends AsyncTask<SharingActivity, Void, SharingActivity> {
        @Override
        protected SharingActivity doInBackground(SharingActivity... contesti) {
            SharingActivity activity = contesti[0];
            try {
                String protocol = "https";
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) protocol = "http";
                URL url = new URL(protocol + "://www.familygem.app/insert_share.php");
                HttpURLConnection connection = (HttpURLConnection)url.openConnection();
                connection.setRequestMethod("POST");
                OutputStream stream = new BufferedOutputStream(connection.getOutputStream());
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8));
                String query = "passKey=" + URLEncoder.encode(BuildConfig.PASS_KEY, "UTF-8") +
                        "&treeTitle=" + URLEncoder.encode(activity.tree.title, "UTF-8") +
                        "&submitterName=" + URLEncoder.encode(activity.submitterName, "UTF-8") +
                        "&accessible=" + activity.accessible;
                writer.write(query);
                writer.flush();
                writer.close();
                stream.close();

                // Answer
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line = reader.readLine();
                reader.close();
                connection.disconnect();
                if (line.startsWith("20")) {
                    activity.dateId = line.replaceAll("[-: ]", "");
                    Settings.Share share = new Settings.Share(activity.dateId, activity.submitterId);
                    activity.tree.addShare(share);
                    Global.settings.save();
                } else U.toast(line);
            } catch (Exception e) {
                U.toast(e.getLocalizedMessage());
                e.printStackTrace();
            }
            return activity;
        }

        @Override
        protected void onPostExecute(SharingActivity activity) {
            if (activity.dateId != null && activity.dateId.startsWith("20")) {
                File treeFile = new File(activity.getCacheDir(), activity.dateId + ".zip");
                if (activity.exporter.exportZipBackup(activity.tree.shareRoot, 9, Uri.fromFile(treeFile))) {
                    new FtpUpload().execute(activity);
                    return;
                } else
                    Toast.makeText(activity, activity.exporter.errorMessage, Toast.LENGTH_LONG).show();
            }
            // Un Toast di errore qui sostituirebbe il messaggio di tosta() in catch()
            restore(activity);
        }
    }

    /**
     * Uploads via FTP the ZIP file with the shared tree.
     */
    static class FtpUpload extends AsyncTask<SharingActivity, Void, SharingActivity> {
        protected SharingActivity doInBackground(SharingActivity... activities) {
            SharingActivity activity = activities[0];
            JSONObject credential = U.getCredential(Json.FTP);
            if (credential != null) {
                try {
                    FTPClient ftpClient = new FTPClient();
                    ftpClient.connect(credential.getString(Json.HOST), credential.getInt(Json.PORT));
                    ftpClient.enterLocalPassiveMode();
                    ftpClient.login(credential.getString(Json.USER), credential.getString(Json.PASSWORD));
                    ftpClient.changeWorkingDirectory(credential.getString(Json.SHARED_PATH));
                    ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
                    BufferedInputStream buffIn;
                    String nomeZip = activity.dateId + ".zip";
                    buffIn = new BufferedInputStream(new FileInputStream(activity.getCacheDir() + "/" + nomeZip));
                    activity.successfulUpload = ftpClient.storeFile(nomeZip, buffIn);
                    buffIn.close();
                    ftpClient.logout();
                    ftpClient.disconnect();
                } catch (Exception e) {
                    U.toast(e.getLocalizedMessage());
                }
            }
            return activity;
        }

        protected void onPostExecute(SharingActivity activity) {
            if (activity.successfulUpload) {
                Toast.makeText(activity, R.string.correctly_uploaded, Toast.LENGTH_SHORT).show();
                activity.concludeShare();
            } else {
                restore(activity);
            }
        }
    }

    /**
     * Show apps to share the link.
     */
    private void concludeShare() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.sharing_tree));
        intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.click_this_link,
                "https://www.familygem.app/share.php?tree=" + dateId));
        //startActivity( Intent.createChooser( intent, "Condividi con" ) );
        /* Tornando indietro da una app di messaggistica il requestCode 35417 arriva sempre corretto
            Invece il resultCode può essere RESULT_OK o RESULT_CANCELED a capocchia
            Ad esempio da Gmail ritorna indietro sempre con RESULT_CANCELED sia che l'email è stata inviata o no
            anche inviando un Sms ritorna RESULT_CANCELED anche se l'sms è stato inviato
            oppure da Whatsapp è RESULT_OK sia che il messaggio è stato inviato o no
            In pratica non c'è modo di sapere se nella app di messaggistica il messaggio è stato inviato */
        startActivityForResult(Intent.createChooser(intent, getText(R.string.share_with)), 35417);
        restore(this);
    }

    private static void restore(SharingActivity activity) {
        activity.findViewById(R.id.share_button).setEnabled(true);
        activity.findViewById(R.id.share_wheel).setVisibility(View.GONE);
    }

    // Aggiorna le preferenze così da mostrare la nuova radice scelta in PersonsFragment
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == AppCompatActivity.RESULT_OK) {
            if (requestCode == 5007) {
                tree.shareRoot = data.getStringExtra(Extra.RELATIVE_ID);
                Global.settings.save();
                displayShareRoot();
            }
        }
        // Ritorno indietro da qualsiasi app di condivisione, nella quale il messaggio è stato inviato oppure no
        if (requestCode == 35417) {
            // Todo chiudi tastiera
            Toast.makeText(getApplicationContext(), R.string.sharing_completed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem i) {
        onBackPressed();
        return true;
    }
}
