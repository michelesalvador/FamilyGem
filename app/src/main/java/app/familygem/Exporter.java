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

import app.familygem.util.TreeUtils;
import app.familygem.visitor.MediaList;

/**
 * Utility class to export a tree as GEDCOM or ZIP backup.
 */
public class Exporter {

    private final Context context;
    private int treeId;
    private Gedcom gedcom;
    private Uri targetUri;
    public String errorMessage; // Message of possible error
    public String successMessage; // Message of the obtained result

    public Exporter(Context context) {
        this.context = context;
    }

    /**
     * Opens the Json tree and returns true if successful.
     */
    public boolean openTree(int treeId) {
        this.treeId = treeId;
        gedcom = TreeUtils.INSTANCE.openGedcomTemporarily(treeId, true);
        if (gedcom == null) {
            return error(R.string.no_useful_data);
        }
        return true;
    }

    /**
     * Writes the GEDCOM file in the URI.
     */
    public boolean exportGedcom(Uri targetUri) {
        this.targetUri = targetUri;
        updateHeader(extractFilename(targetUri));
        optimizeGedcom();
        GedcomWriter writer = new GedcomWriter();
        File gedcomFile = new File(context.getCacheDir(), "temp.ged");
        try {
            writer.write(gedcom, gedcomFile);
            OutputStream out = context.getContentResolver().openOutputStream(targetUri);
            FileUtils.copyFile(gedcomFile, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            return error(e.getLocalizedMessage());
        }

        // Makes the file visible from Windows
        // But it seems ineffective in KitKat where the file remains invisible
        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, targetUri));
        Global.gc = TreeUtils.INSTANCE.readJson(treeId); // Reset the changes
        return success(R.string.gedcom_exported_ok);
    }

    /**
     * Writes the GEDCOM with all the media in a ZIP file.
     */
    public boolean exportGedcomToZip(Uri targetUri) {
        this.targetUri = targetUri;
        // Create the GEDCOM file
        String title = Global.settings.getTree(treeId).title;
        String gedcomFileName = title.replaceAll("[\\\\/:*?\"<>|'$]", "_") + ".ged";
        updateHeader(gedcomFileName);
        optimizeGedcom();
        GedcomWriter writer = new GedcomWriter();
        File fileGc = new File(context.getCacheDir(), gedcomFileName);
        try {
            writer.write(gedcom, fileGc);
        } catch (Exception e) {
            return error(e.getLocalizedMessage());
        }
        DocumentFile gedcomDocument = DocumentFile.fromFile(fileGc);
        // Adds the GEDCOM to the media file collection
        Map<DocumentFile, Integer> collection = collectMedia();
        collection.put(gedcomDocument, 0);
        if (!createZipFile(collection))
            return false;
        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, targetUri));
        Global.gc = TreeUtils.INSTANCE.readJson(treeId);
        return success(R.string.zip_exported_ok);
    }

    /**
     * Creates a zipped file with the tree, settings and media.
     */
    public boolean exportZipBackup(String root, int grade, Uri targetUri) {
        this.targetUri = targetUri;
        // Media
        Map<DocumentFile, Integer> files = collectMedia();
        // Tree's JSON
        File fileTree = new File(context.getFilesDir(), treeId + ".json");
        files.put(DocumentFile.fromFile(fileTree), 1);
        // Settings' JSON
        // title, root and grade can be modified by SharingActivity
        Settings.Tree tree = Global.settings.getTree(treeId);
        if (root == null) root = tree.root;
        if (grade < 0) grade = tree.grade;
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
     * Returns the number of media files to attach.
     */
    public int countMediaFilesToAttach() {
        MediaList mediaList = new MediaList(gedcom, 0);
        gedcom.accept(mediaList);
        int numFiles = 0;
        for (Media med : mediaList.list) {
            if (F.mediaPath(treeId, med) != null || F.mediaUri(treeId, med) != null)
                numFiles++;
        }
        return numFiles;
    }

    /**
     * Returns a DocumentFile Map of the media that it can find.
     */
    private Map<DocumentFile, Integer> collectMedia() {
        MediaList mediaList = new MediaList(gedcom, 0);
        gedcom.accept(mediaList);
        /* It happens that different Media point to the same file.
           And it could also happen that different paths end up with the same filenames,
           eg. 'pathA/img.jpg' 'pathB/img.jpg'
           It's necessary to avoid that files with the same name end up in the ZIP.
           This loop creates a list of paths with unique filenames. */
        Set<String> paths = new HashSet<>();
        Set<String> onlyFileNames = new HashSet<>(); // Control file names
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
                Uri mediaUri = F.mediaUri(treeId, med);
                if (mediaUri != null)
                    collection.put(DocumentFile.fromSingleUri(context, mediaUri), 2);
            }
        }
        return collection;
    }

    private void updateHeader(String gedcomFilename) {
        Header header = gedcom.getHeader();
        if (header == null)
            gedcom.setHeader(NewTreeActivity.createHeader(gedcomFilename));
        else {
            header.setFile(gedcomFilename);
            header.setDateTime(U.actualDateTime());
        }
    }

    /**
     * Enhances GEDCOM for export.
     */
    void optimizeGedcom() {
        // Value of names from given and surname
        for (Person person : gedcom.getPeople()) {
            for (Name n : person.getNames())
                if (n.getValue() == null && (n.getPrefix() != null || n.getGiven() != null
                        || n.getSurname() != null || n.getSuffix() != null)) {
                    String epiteto = ""; // TODO: replace with stringbuilder
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
     * Extracts only the filename from a URI.
     */
    private String extractFilename(Uri uri) {
        // file://
        if (uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("file")) {
            return uri.getLastPathSegment();
        }
        // Cursor (usually it's used this)
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
     * Gets a list of DocumentFiles and put them in a ZIP file written to the targetUri.
     * Returns error message or true if all is well.
     */
    boolean createZipFile(Map<DocumentFile, Integer> files) {
        byte[] buffer = new byte[128];
        try {
            ZipOutputStream zos = new ZipOutputStream(context.getContentResolver().openOutputStream(targetUri));
            for (Map.Entry<DocumentFile, Integer> fileType : files.entrySet()) {
                DocumentFile file = fileType.getKey();
                InputStream input = context.getContentResolver().openInputStream(file.getUri());
                String filename = file.getName(); // Files that are not renamed ('settings.json', 'family.ged')
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
