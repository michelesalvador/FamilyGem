package app.familygem;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.documentfile.provider.DocumentFile;

import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Header;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.visitors.GedcomWriter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import app.familygem.visitor.MediaList;

public class Exporter {

    private final Context context;
    private int treeId;
    private Gedcom gc;
    private Uri targetUri;
    public String errorMessage;  // Message of any error
    public String successMessage;// Message of the result obtained

    Exporter(Context context) {
        this.context = context;
    }

    /**
     * Opens the Json tree and returns true if successful
     */
    public boolean openTree(int treeId) {
        this.treeId = treeId;
        gc = TreesActivity.openGedcomTemporarily(treeId, true);
        if (gc == null) {
            return error(R.string.no_useful_data);
        }
        return true;
    }

    /**
     * Writes only GEDCOM in the URI
     * Scrive il solo GEDCOM nell'URI
     */
    public boolean exportGedcom(Uri targetUri) {
        this.targetUri = targetUri;
        updateHeader(extractFilename(targetUri));
        optimizeGedcom();
        GedcomWriter writer = new GedcomWriter();
        File fileGc = new File(context.getCacheDir(), "temp.ged");
        try {
            writer.write(gc, fileGc);
            OutputStream out = context.getContentResolver().openOutputStream(targetUri);
            FileUtils.copyFile(fileGc, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            return error(e.getLocalizedMessage());
        }

		// Make the file visible from Windows // Rende il file visibile da Windows
		// But it seems ineffective in KitKat where the file remains invisible // Ma pare inefficace in KitKat in cui il file rimane invisibile
        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, targetUri));
        Global.gc = TreesActivity.readJson(treeId); // Reset the changes
        return success(R.string.gedcom_exported_ok);
    }

    /**
     * Writes the GEDCOM with the media in a ZIP file
     */
    public boolean exportGedcomToZip(Uri targetUri) {
        this.targetUri = targetUri;
        // Create the GEDCOM file
        String title = Global.settings.getTree(treeId).title;
        String filename = title.replaceAll("[\\\\/:*?\"<>|'$]", "_") + ".ged";
        updateHeader(filename);
        optimizeGedcom();
        GedcomWriter writer = new GedcomWriter();
        File fileGc = new File(context.getCacheDir(), filename);
        try {
            writer.write(gc, fileGc);
        } catch (Exception e) {
            return error(e.getLocalizedMessage());
        }
        DocumentFile gedcomDocument = DocumentFile.fromFile(fileGc);
        // Add the GEDCOM to the media file collection
        Map<DocumentFile, Integer> collection = collectMedia();
        collection.put(gedcomDocument, 0);
        if (!createZipFile(collection))
            return false;
        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, targetUri));
        Global.gc = TreesActivity.readJson(treeId);
        return success(R.string.zip_exported_ok);
    }

    /**
     * Create a zipped file with the tree, settings and media
     */
    public boolean exportBackupZip(String root, int grade, Uri targetUri) {
        this.targetUri = targetUri;
        // Media
        Map<DocumentFile, Integer> files = collectMedia();
        // Tree's json
        File fileTree = new File(context.getFilesDir(), treeId + ".json");
        files.put(DocumentFile.fromFile(fileTree), 1);
        // Preference's json
        Settings.Tree tree = Global.settings.getTree(treeId);
        if (root == null) root = tree.root;
        if (grade < 0) grade = tree.grade;
        // String titleTree, String root, int degree can arrive other than Share // String titoloAlbero, String radice, int grado possono arrivare diversi da Condividi
        Settings.ZippedTree settings = new Settings.ZippedTree(
                tree.title, tree.persons, tree.generations, root, tree.shares, grade);
        File fileSettings = settings.save();
        files.put(DocumentFile.fromFile(fileSettings), 0);
        if (!createZipFile(files))
            return false;
        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, targetUri));
        return success(R.string.zip_exported_ok);
    }

    /**
     * Returns the number of media files to attach
     */
    public int numMediaFilesToAttach() {
        MediaList mediaList = new MediaList(gc, 0);
        gc.accept(mediaList);
        int numFiles = 0;
        for (Media med : mediaList.list) {
            if (F.mediaPath(treeId, med) != null || F.mediaUri(treeId, med) != null)
                numFiles++;
        }
        return numFiles;
    }

    /**
     * Receives the id of a tree and gets a DocumentFile Map of the media that it manages to find
     * <p>
     * Riceve l'id di un albero e arriva una Map di DocumentFile dei media che riesce a rastrellare
     */
    private Map<DocumentFile, Integer> collectMedia() {
        MediaList mediaList = new MediaList(gc, 0);
        gc.accept(mediaList);

        /* It happens that different Media point to the same file.
         * And it could also happen that different paths end up with the same filenames,
         * eg. 'pathA / img.jpg' 'pathB / img.jpg'
         * You must avoid that files with the same name end up in the ZIP media.
         * This loop creates a list of paths with unique filenames */

        /*  Capita che diversi Media puntino allo stesso file.
         *   E potrebbe anche capitare che diversi percorsi finiscano con nomi di file uguali,
         *   ad es. 'percorsoA/img.jpg' 'percorsoB/img.jpg'
         *   Bisogna evitare che nei media dello ZIP finiscano file con lo stesso nome.
         *   Questo loop crea una lista di percorsi con nome file univoci */

        Set<String> paths = new HashSet<>();
        Set<String> onlyFileNames = new HashSet<>(); // Control file names //Nomi file di controllo
        for (Media med : mediaList.list) {
            String path = med.getFile();
            if (path != null && !path.isEmpty()) {
                String fileName = path.replace('\\', '/');
                if (fileName.lastIndexOf('/') > -1)
                    fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
                if (!onlyFileNames.contains(fileName))
                    paths.add(path);
                onlyFileNames.add(fileName);
            }
        }
        Map<DocumentFile, Integer> collection = new HashMap<>();
        for (String path : paths) {
            Media med = new Media();
            med.setFile(path);
            // Paths
            String mediaPath = F.mediaPath(treeId, med);
            if (mediaPath != null)
                collection.put(DocumentFile.fromFile(new File(mediaPath)), 2); // todo canRead() ?
            else { // URIs
                Uri uriMedia = F.mediaUri(treeId, med);
                if (uriMedia != null)
                    collection.put(DocumentFile.fromSingleUri(context, uriMedia), 2);
            }
        }
        return collection;
    }

    private void updateHeader(String gedcomFilename) {
        Header header = gc.getHeader();
        if (header == null)
            gc.setHeader(NewTree.createHeader(gedcomFilename));
        else {
            header.setFile(gedcomFilename);
            header.setDateTime(U.actualDateTime());
        }
    }

    /**
	 * Enhance GEDCOM for export
	 * */
    void optimizeGedcom() {
		// Value of names from given and surname
        for (Person pers : gc.getPeople()) {
            for (Name n : pers.getNames())
                if (n.getValue() == null && (n.getPrefix() != null || n.getGiven() != null
                        || n.getSurname() != null || n.getSuffix() != null)) {
                    String epiteto = ""; //TODO replace with stringbuilder
                    if (n.getPrefix() != null)
                        epiteto = n.getPrefix();
                    if (n.getGiven() != null)
                        epiteto += " " + n.getGiven();
                    if (n.getSurname() != null)
                        epiteto += " /" + n.getSurname() + "/";
                    if (n.getSuffix() != null)
                        epiteto += " " + n.getSuffix();
                    n.setValue(epiteto.trim());
                }
        }
    }

    /**
	 * Extracts only the filename from a URI
	 * */
    private String extractFilename(Uri uri) {
        // file://
        if (uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("file")) {
            return uri.getLastPathSegment();
        }
        // Cursor (this usually works)
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            String filename = cursor.getString(index);
            cursor.close();
            if (filename != null) return filename;
        }
        // DocumentFile
        DocumentFile document = DocumentFile.fromSingleUri(context, targetUri);
        String filename = document.getName();
        if (filename != null) return filename;
        // Not much else to do
        return "tree.ged";
    }

	/**
	 * Get the list of DocumentFiles and put them in a ZIP file written to the targetUri
	 * Return error message or null if all is well
	 * */
	boolean createZipFile(Map<DocumentFile, Integer> files) {
        byte[] buffer = new byte[128];
        try {
            ZipOutputStream zos = new ZipOutputStream(context.getContentResolver().openOutputStream(targetUri));
            for (Map.Entry<DocumentFile, Integer> fileType : files.entrySet()) {
                DocumentFile file = fileType.getKey();
                InputStream input = context.getContentResolver().openInputStream(file.getUri());
                String filename = file.getName();   //Files that are not renamed ('settings.json', 'family.ged') // File che non vengono rinominati ('settings.json', 'famiglia.ged')
                if (fileType.getValue() == 1)
                    filename = "tree.json";
                else if (fileType.getValue() == 2)
                    filename = "media/" + file.getName();
                zos.putNextEntry(new ZipEntry(filename));
                int read;
                while ((read = input.read(buffer)) != -1) {
                    zos.write(buffer, 0, read);
                }
                zos.closeEntry();
                input.close();
            }
            zos.close();
        } catch (IOException e) {
            return error(e.getLocalizedMessage());
        }
        return true;
    }

    public boolean success(int message) {
        successMessage = context.getString(message);
        return true;
    }

    public boolean error(int error) {
        return error(context.getString(error));
    }

    public boolean error(String error) {
        errorMessage = error;
        return false;
    }
}
