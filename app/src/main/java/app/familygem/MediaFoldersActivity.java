package app.familygem;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.List;

import app.familygem.constant.Extra;

// Activity where you can see the list of media folders, add them, delete them
public class MediaFoldersActivity extends BaseActivity {

    int treeId;
    List<String> dirs;
    List<String> uris;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.cartelle_media);
        treeId = getIntent().getIntExtra(Extra.TREE_ID, 0);
        dirs = new ArrayList<>(Global.settings.getTree(treeId).dirs);
        uris = new ArrayList<>(Global.settings.getTree(treeId).uris);
        updateList();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        findViewById(R.id.fab).setOnClickListener(v -> {
            final String[] requiredPermissions;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requiredPermissions = new String[]{
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_AUDIO,
                };
            } else {
                requiredPermissions = new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                };
            }

            final int perm = F.checkMultiplePermissions(this, requiredPermissions);
            if (perm == PackageManager.PERMISSION_DENIED)
                ActivityCompat.requestPermissions(this, requiredPermissions, 3517);
            else if (perm == PackageManager.PERMISSION_GRANTED)
                chooseFolder();
        });
        if (Global.settings.getTree(treeId).dirs.isEmpty() && Global.settings.getTree(treeId).uris.isEmpty())
            new SpeechBubble(this, R.string.add_device_folder).show();
    }

    void chooseFolder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, 123);
        } else {
            // KitKat uses the selection of a file to locate the container folder
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(intent, 456);
        }
    }

    void updateList() {
        LinearLayout layout = findViewById(R.id.cartelle_scatola);
        layout.removeAllViews();
        for (String dir : dirs) {
            if (dir == null) continue; // Sometimes a null dir is stored in settings
            View folderView = getLayoutInflater().inflate(R.layout.pezzo_cartella, layout, false);
            layout.addView(folderView);
            TextView nameView = folderView.findViewById(R.id.cartella_nome);
            TextView urlView = folderView.findViewById(R.id.cartella_url);
            urlView.setText(dir);
            if (Global.settings.expert)
                urlView.setSingleLine(false);
            View deleteButton = folderView.findViewById(R.id.cartella_elimina);
            // La cartella '/storage/.../Android/data/app.familygem/files/X' va preservata inquanto è quella di default dei media copiati
            // Oltretutto in Android 11 non è più raggiungibile dall'utente con SAF
            if (dir.equals(getExternalFilesDir(null) + "/" + treeId)) {
                nameView.setText(R.string.app_storage);
                deleteButton.setVisibility(View.GONE);
            } else {
                nameView.setText(folderName(dir));
                deleteButton.setOnClickListener(v -> {
                    new AlertDialog.Builder(this).setMessage(R.string.sure_delete)
                            .setPositiveButton(R.string.yes, (di, id) -> {
                                dirs.remove(dir);
                                save();
                            }).setNeutralButton(R.string.cancel, null).show();
                });
            }
            registerForContextMenu(folderView);
        }
        for (String uriString : uris) {
            if (uriString == null) continue;
            View uriView = getLayoutInflater().inflate(R.layout.pezzo_cartella, layout, false);
            layout.addView(uriView);
            DocumentFile documentDir = DocumentFile.fromTreeUri(this, Uri.parse(uriString));
            String name = null;
            if (documentDir != null)
                name = documentDir.getName();
            ((TextView)uriView.findViewById(R.id.cartella_nome)).setText(name);
            TextView urlView = uriView.findViewById(R.id.cartella_url);
            if (Global.settings.expert) urlView.setSingleLine(false);
            urlView.setText(Uri.decode(uriString)); // Shows it decoded, a little more readable
            uriView.findViewById(R.id.cartella_elimina).setOnClickListener(v -> {
                new AlertDialog.Builder(this).setMessage(R.string.sure_delete)
                        .setPositiveButton(R.string.yes, (di, id) -> {
                            // Revokes permission for this URI, if the URI is not used in any other tree
                            boolean uriExistsElsewhere = false;
                            outer:
                            for (Settings.Tree tree : Global.settings.trees) {
                                for (String uri : tree.uris) {
                                    if (uri != null && uri.equals(uriString) && tree.id != treeId) {
                                        uriExistsElsewhere = true;
                                        break outer;
                                    }
                                }
                            }
                            if (!uriExistsElsewhere)
                                revokeUriPermission(Uri.parse(uriString), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            uris.remove(uriString);
                            save();
                        }).setNeutralButton(R.string.cancel, null).show();
            });
            registerForContextMenu(uriView);
        }
    }

    String folderName(String url) {
        if (url.lastIndexOf('/') > 0)
            return url.substring(url.lastIndexOf('/') + 1);
        return url;
    }

    void save() {
        Global.settings.getTree(treeId).dirs.clear();
        for (String path : dirs)
            Global.settings.getTree(treeId).dirs.add(path);
        Global.settings.getTree(treeId).uris.clear();
        for (String uri : uris)
            Global.settings.getTree(treeId).uris.add(uri);
        Global.settings.save();
        updateList();
        Global.edited = true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        onBackPressed();
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            if (uri != null) {
                // In KitKat a file has been selected and we get the path of the folder
                if (requestCode == 456) {
                    String path = F.uriPathFolderKitKat(this, uri);
                    if (path != null) {
                        dirs.add(path);
                        save();
                    }
                } else if (requestCode == 123) {
                    String path = F.getFolderPathFromUri(uri);
                    if (path != null) {
                        dirs.add(path);
                        save();
                    } else {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        DocumentFile docDir = DocumentFile.fromTreeUri(this, uri);
                        if (docDir != null && docDir.canRead()) {
                            uris.add(uri.toString());
                            save();
                        } else Toast.makeText(this, "Could not read this position.", Toast.LENGTH_LONG).show();
                    }
                }
            } else Toast.makeText(this, R.string.cant_understand_uri, Toast.LENGTH_LONG).show();
        }
    }

    View selectedView;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo info) {
        selectedView = view;
        menu.add(0, 0, 0, R.string.copy);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == 0) { // Copia
            U.copyToClipboard(getText(android.R.string.copyUrl), ((TextView)selectedView.findViewById(R.id.cartella_url)).getText());
            return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED && code == 3517)
            chooseFolder();
    }
}
