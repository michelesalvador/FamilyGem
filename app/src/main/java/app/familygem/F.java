package app.familygem;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;

import java.io.File;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.familygem.detail.MediaActivity;
import app.familygem.main.MediaFragment;
import app.familygem.util.FileUtil;
import app.familygem.util.TreeUtil;

/**
 * Static functions to manage files and media.
 */
public class F {

    /**
     * Wrapper of uriFilePath() to get a folder in KitKat.
     */
    static String uriPathFolderKitKat(Context context, Uri uri) {
        String path = getFilePathFromUri(uri);
        if (path != null && path.lastIndexOf('/') > 0) {
            return path.substring(0, path.lastIndexOf('/'));
        } else {
            Toast.makeText(context, "Could not get this position.", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    /**
     * Receives a URI and tries to return the path to the file.
     */
    public static String getFilePathFromUri(Uri uri) {
        if (uri == null) return null;
        if (uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("file")) {
            // Removes 'file://'
            return uri.getPath();
        }
        switch (uri.getAuthority()) {
            case "com.android.externalstorage.documents": // Internal memory and SD card
                String[] split = uri.getLastPathSegment().split(":");
                if (split[0].equalsIgnoreCase("primary")) {
                    // Main storage
                    String path = Environment.getExternalStorageDirectory() + "/" + split[1];
                    if (new File(path).canRead())
                        return path;
                } else if (split[0].equalsIgnoreCase("home")) {
                    // 'Documents' folder in Android 9 and 10
                    return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/" + split[1];
                } else {
                    // All other cases including SD cards
                    File[] places = Global.context.getExternalFilesDirs(null);
                    for (File file : places) {
                        if (file.getAbsolutePath().indexOf("/Android") > 0) {
                            String dir = file.getAbsolutePath().substring(0, file.getAbsolutePath().indexOf("/Android"));
                            File maybeReadable = new File(dir, split[1]);
                            if (maybeReadable.canRead())
                                return maybeReadable.getAbsolutePath();
                        }
                    }
                }
                break;
            case "com.android.providers.downloads.documents": // Files from the Downloads folder
                String id = uri.getLastPathSegment();
                if (id.startsWith("raw:/"))
                    return id.replaceFirst("raw:", "");
                if (id.matches("\\d+")) {
                    String[] contentUriPrefixesToTry = new String[]{
                            "content://downloads/public_downloads",
                            "content://downloads/my_downloads"
                    };
                    for (String contentUriPrefix : contentUriPrefixesToTry) {
                        Uri rebuilt = ContentUris.withAppendedId(Uri.parse(contentUriPrefix), Long.parseLong(id));
                        try {
                            String filename = findFilename(rebuilt);
                            if (filename != null)
                                return filename;
                        } catch (Exception ignored) {
                        }
                    }
                }
        }
        return findFilename(uri);
    }

    /**
     * Gets the URI (possibly reconstructed) of a file taken with SAF.
     * If possible returns the full path, otherwise the single file name.
     */
    private static String findFilename(Uri uri) {
        Cursor cursor = Global.context.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int index = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA);
            if (index < 0)
                index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            String filename = cursor.getString(index);
            cursor.close();
            return filename;
        }
        return null;
    }

    /**
     * Receives a URI tree obtained with ACTION_OPEN_DOCUMENT_TREE and tries to return the path of the folder,
     * otherwise returns null.
     */
    public static String getFolderPathFromUri(Uri uri) {
        if (uri == null) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            String treeDocId = DocumentsContract.getTreeDocumentId(uri);
            switch (uri.getAuthority()) {
                case "com.android.externalstorage.documents": // Internal memory and SD card
                    String[] split = treeDocId.split(":");
                    String path = null;
                    // Main storage
                    if (split[0].equalsIgnoreCase("primary")) {
                        path = Environment.getExternalStorageDirectory().getAbsolutePath();
                    }
                    // Documents in Android 9 and 10
                    else if (split[0].equalsIgnoreCase("home")) {
                        path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath();
                    }
                    // All the others, like the SD card
                    else {
                        File[] filesDirs = Global.context.getExternalFilesDirs(null);
                        for (File dir : filesDirs) {
                            String other = dir.getAbsolutePath();
                            if (other.contains(split[0])) {
                                path = other.substring(0, other.indexOf("/Android"));
                                break;
                            }
                        }
                    }
                    if (path != null) {
                        if (split.length > 1 && !split[1].isEmpty())
                            path += "/" + split[1];
                        return path;
                    }
                    break;
                case "com.android.providers.downloads.documents": // Provider for Downloads (and sub-folders)
                    if (treeDocId.equals("downloads"))
                        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
                    if (treeDocId.startsWith("raw:/"))
                        return treeDocId.replaceFirst("raw:", "");
            }
        }
        return null;
    }

    /**
     * Saves a document (PDF, GEDCOM, ZIP) with SAF.
     */
    public static void saveDocument(Activity activity, Fragment fragment, int treeId, String mime, String ext, int requestCode) {
        String name = Global.settings.getTree(treeId).title;
        // A GEDCOM must specify the extension, other file types put it according to the mime type
        ext = ext.equals("ged") ? ".ged" : "";
        // Replaces dangerous characters for the Android filesystem that are not replaced by Android itself
        name = name.replaceAll("[$']", "_");
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mime)
                .putExtra(Intent.EXTRA_TITLE, name + ext);
        if (activity != null)
            activity.startActivityForResult(intent, requestCode);
        else
            fragment.startActivityForResult(intent, requestCode);
    }

    public static int checkMultiplePermissions(final Context context, final String... permissions) {
        int result = PackageManager.PERMISSION_GRANTED;
        for (String permission : permissions) {
            result |= ContextCompat.checkSelfPermission(context, permission);
        }
        return result;
    }

    // Methods for image acquisition:

    /**
     * Offers a nice list of apps for capturing images.
     */
    public static void displayImageCaptureDialog(Context context, Fragment fragment, int code, MediaContainer container) {
        // Requests permission to access device memory
        final String[] requiredPermissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions = new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
            };
        } else {
            requiredPermissions = new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
            };
        }
        final int perm = checkMultiplePermissions(context, requiredPermissions);
        if (perm == PackageManager.PERMISSION_DENIED) {
            if (fragment != null) { // MediaFragment
                fragment.requestPermissions(requiredPermissions, code);
            } else
                ActivityCompat.requestPermissions((AppCompatActivity)context, requiredPermissions, code);
            return;
        }
        // Collects intents useful to capture images
        List<ResolveInfo> resolveInfos = new ArrayList<>();
        List<Intent> intents = new ArrayList<>();
        // Cameras
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        for (ResolveInfo info : context.getPackageManager().queryIntentActivities(cameraIntent, 0)) {
            Intent finalIntent = new Intent(cameraIntent);
            finalIntent.setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
            intents.add(finalIntent);
            resolveInfos.add(info);
        }
        // Galleries
        Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
        galleryIntent.setType("image/*");
        String[] mimeTypes = {"image/*", "audio/*", "video/*", "application/*", "text/*"};
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT)
            mimeTypes[0] = "*/*"; // Otherwise KitKat does not see the 'application/*' in Downloads
        galleryIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        for (ResolveInfo info : context.getPackageManager().queryIntentActivities(galleryIntent, 0)) {
            if (info.activityInfo.packageName.equals("com.google.android.apps.docs"))
                continue; // Excludes the Google Drive app, because files there are not accessible
            Intent finalIntent = new Intent(galleryIntent);
            finalIntent.setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
            intents.add(finalIntent);
            resolveInfos.add(info);
        }
        // Empty Media
        // Doesn't appear when choosing a file in MediaActivity
        if (Global.settings.expert && code != 5173) {
            Intent intent = new Intent(context, MediaActivity.class);
            ResolveInfo info = context.getPackageManager().resolveActivity(intent, 0);
            intent.setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
            intents.add(intent);
            resolveInfos.add(info);
        }
        new AlertDialog.Builder(context).setAdapter(createAdapter(context, resolveInfos),
                (dialog, id) -> {
                    Intent intent = intents.get(id);
                    // Set up a URI in which to put the photo taken by the camera app
                    if (intent.getAction() != null && intent.getAction().equals(MediaStore.ACTION_IMAGE_CAPTURE)) {
                        File dir = context.getExternalFilesDir(String.valueOf(Global.settings.openTree)); // Creates dir if it doesn't exist
                        File photoFile = nextAvailableFileName(dir, "image.jpg");
                        Global.pathOfCameraDestination = photoFile.getAbsolutePath(); // Saves it to retake it after the photo is taken
                        Uri photoUri;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                            photoUri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", photoFile);
                        else // KitKat
                            photoUri = Uri.fromFile(photoFile);
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                    }
                    if (intent.getComponent().getPackageName().equals(BuildConfig.APPLICATION_ID)) {
                        // Create an empty Media
                        Media med;
                        if (code == 4173 || code == 2173) { // Simple media
                            med = new Media();
                            med.setFileTag("FILE");
                            container.addMedia(med);
                            Memory.add(med);
                        } else { // Shared media
                            med = MediaFragment.newSharedMedia(container);
                            Memory.setLeader(med);
                        }
                        med.setFile("");
                        context.startActivity(intent);
                        TreeUtil.INSTANCE.save(true, Memory.getLeaderObject());
                    } else if (fragment != null)
                        fragment.startActivityForResult(intent, code); // Thus the result returns to the fragment
                    else
                        ((AppCompatActivity)context).startActivityForResult(intent, code);
                }).show();
    }

    /**
     * Closely related to the one above.
     */
    private static ArrayAdapter<ResolveInfo> createAdapter(final Context context, final List<ResolveInfo> resolveInfos) {
        return new ArrayAdapter<ResolveInfo>(context, R.layout.piece_app, R.id.app_title, resolveInfos) {
            @Override
            public View getView(int position, View view1, ViewGroup parent) {
                View view = super.getView(position, view1, parent);
                ResolveInfo info = resolveInfos.get(position);
                ImageView image = view.findViewById(R.id.app_icon);
                TextView textview = view.findViewById(R.id.app_title);
                if (info.activityInfo.packageName.equals(BuildConfig.APPLICATION_ID)) {
                    image.setImageResource(R.drawable.image);
                    textview.setText(R.string.empty_media);
                } else {
                    image.setImageDrawable(info.loadIcon(context.getPackageManager()));
                    textview.setText(info.loadLabel(context.getPackageManager()).toString());
                }
                return view;
            }
        };
    }

    /**
     * Saves into 'media' the file retrieved from 'data' and optionally proposes to crop it whether is an image.
     *
     * @param data Contains the Uri of a file
     * @return True on successfully setting the file (croppable or not)
     */
    public static boolean setFileAndProposeCropping(Context context, Fragment fragment, Intent data, Media media) {
        // Finds the path of the image
        Uri uri = null;
        String path;
        // Content taken with SAF
        if (data != null && data.getData() != null) {
            uri = data.getData();
            path = getFilePathFromUri(uri);
        } // Photo from camera app
        else if (Global.pathOfCameraDestination != null) {
            path = Global.pathOfCameraDestination;
            Global.pathOfCameraDestination = null; // Resets it
        } // Nothing usable
        else {
            Toast.makeText(context, R.string.something_wrong, Toast.LENGTH_SHORT).show();
            return false;
        }

        // Creates the file
        File[] fileMedia = new File[1]; // Because must be final
        if (path != null && path.lastIndexOf('/') > 0) { // If it is a full path to the file
            // Directly points to the file
            fileMedia[0] = new File(path);
        } else { // 'path' is just the file name "myFile.ext", or a title "My file", or more rarely null
            // External app storage: /storage/emulated/0/Android/data/app.familygem/files/12
            File externalFilesDir = context.getExternalFilesDir(String.valueOf(Global.settings.openTree));
            try { // We use the URI
                InputStream input;
                ContentResolver resolver = context.getContentResolver();
                if (isVirtualFile(context, uri)) {
                    String[] openableMimeTypes = resolver.getStreamTypes(uri, "*/*");
                    input = resolver.openTypedAssetFileDescriptor(uri, openableMimeTypes[0], null).createInputStream();
                } else input = resolver.openInputStream(uri);
                // TODO: if the file already exists, do not duplicate it but reuse it: as in ConfirmationActivity.copyFiles
                String type = resolver.getType(uri); // E.g. "image/jpeg" or "application/vnd.google-apps.spreadsheet"
                String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
                if (extension == null) { // Last part of mime type becomes the extension
                    extension = type.lastIndexOf('.') > 0 ? type.substring(type.lastIndexOf('.') + 1) : type.substring(type.indexOf('/') + 1);
                    //media.setFormat(type);
                }
                if (path == null) { // Null filename, must be created from scratch
                    path = type.substring(0, type.indexOf('/')) + "." + extension;
                } else if (path.indexOf('.') < 0) { // File title without extension
                    path += "." + extension;
                }
                fileMedia[0] = nextAvailableFileName(externalFilesDir, path);
                FileUtils.copyInputStreamToFile(input, fileMedia[0]); // Creates the folder if doesn't exist
            } catch (Exception e) {
                String msg = e.getLocalizedMessage() != null ? e.getLocalizedMessage() : context.getString(R.string.something_wrong);
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
                return false;
            }
        }
        media.setFile(fileMedia[0].getAbsolutePath()); // Sets the found path in the media
        Global.mediaFolderPath = fileMedia[0].getParent();

        // If it is an image opens the cropping proposal dialog
        String mimeType = URLConnection.guessContentTypeFromName(fileMedia[0].getName());
        if (mimeType != null && mimeType.startsWith("image/")) {
            Global.croppedMedia = media; // Media parked waiting to be updated with new file path
            final Dialog alert = new AlertDialog.Builder(context).setView(R.layout.crop_image_dialog)
                    .setPositiveButton(R.string.yes, (dialog, id) -> cropImage(context, fileMedia[0], null, fragment))
                    .setNeutralButton(R.string.no, (dialog, which) -> finishProposeCropping(context, fragment))
                    .setOnCancelListener(dialog -> finishProposeCropping(context, fragment)) // Click out of the dialog
                    .show();
            FileUtil.INSTANCE.showImage(media, alert.findViewById(R.id.crop_image));
        } else {
            saveFolderInSettings();
        }
        return true;
    }

    /**
     * Files got from Google Drive are "virtual".
     */
    private static boolean isVirtualFile(Context context, Uri uri) {
        if (!DocumentsContract.isDocumentUri(context, uri)) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Cursor cursor = context.getContentResolver()
                    .query(uri, new String[]{DocumentsContract.Document.COLUMN_FLAGS}, null, null, null);
            int flags = 0;
            if (cursor != null && cursor.moveToFirst()) {
                flags = cursor.getInt(0);
                cursor.close();
            }
            return (flags & DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT) != 0;
        } else return false;
    }

    /**
     * Adds the folder path of the imported media to tree settings.
     */
    public static void saveFolderInSettings() {
        if (Global.settings.getCurrentTree().dirs.add(Global.mediaFolderPath)) // True if the folder was added
            Global.settings.save();
    }

    /**
     * Negative conclusion of the image cropping proposal: simply refreshes the page to show the image.
     * Usually a refresh is not needed, but in some cases yes (file downloaded from cloud).
     */
    static void finishProposeCropping(Context context, Fragment fragment) {
        if (fragment instanceof MediaFragment)
            ((MediaFragment)fragment).showContent();
        else if (context instanceof DetailActivity)
            ((DetailActivity)context).refresh();
        else if (context instanceof ProfileActivity)
            ((ProfileActivity)context).refresh();
        saveFolderInSettings();
    }

    /**
     * Starts cropping an image with CropImage.
     * 'fileMedia' and 'uriMedia': one of the two is valid, the other is null.
     */
    static void cropImage(Context context, File fileMedia, Uri uriMedia, Fragment fragment) {
        // Origin
        String path = fileMedia != null ? fileMedia.getAbsolutePath() : uriMedia.getPath();
        int cropped = Global.croppedPaths.containsKey(path) ? Global.croppedPaths.get(path) : 0;
        Global.croppedPaths.put(path, cropped + 1);
        if (uriMedia == null) uriMedia = Uri.fromFile(fileMedia);
        // Destination
        File externalFilesDir = context.getExternalFilesDir(String.valueOf(Global.settings.openTree));
        File destinationFile;
        if (fileMedia != null && fileMedia.getAbsolutePath().startsWith(externalFilesDir.getAbsolutePath()))
            destinationFile = fileMedia; // Files already in the storage folder are overwritten
        else {
            String name;
            if (fileMedia != null)
                name = fileMedia.getName();
            else // URI
                name = DocumentFile.fromSingleUri(context, uriMedia).getName();
            destinationFile = nextAvailableFileName(externalFilesDir, name);
        }
        // Cropping
        Intent intent = CropImage.activity(uriMedia)
                .setOutputUri(Uri.fromFile(destinationFile)) // Folder in external memory
                .setGuidelines(CropImageView.Guidelines.OFF)
                .setBorderLineThickness(U.dpToPx(1))
                .setBorderCornerThickness(U.dpToPx(6))
                .setBorderCornerOffset(U.dpToPx(-3))
                .setCropMenuCropButtonTitle(context.getText(R.string.done))
                .getIntent(context);
        if (fragment != null)
            fragment.startActivityForResult(intent, CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE);
        else
            ((AppCompatActivity)context).startActivityForResult(intent, CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE);
    }

    /**
     * If a file with that name already exists in that folder, increments it appending (1) (2) (3)...
     */
    public static File nextAvailableFileName(File folder, String fileName) {
        File file = new File(folder, fileName);
        while (file.exists()) {
            Pattern pattern = Pattern.compile("(.*)\\((\\d+)\\)\\s*(\\.\\w+$|$)");
            Matcher match = pattern.matcher(fileName);
            if (match.matches()) { // Filename terminates with digits between parenthesis e.g. "image (34).jpg"
                int number = Integer.parseInt(match.group(2)) + 1;
                fileName = match.group(1) + "(" + number + ")" + match.group(3);
            } else {
                pattern = Pattern.compile("(.+)(\\..+)");
                match = pattern.matcher(fileName);
                if (match.matches()) { // Filename with extension e.g. "image.jpg"
                    fileName = match.group(1) + " (1)" + match.group(2);

                } else fileName += " (1)"; // Filename without extension e.g. ".image"
            }
            file = new File(folder, fileName);
        }
        return file;
    }

    /**
     * Terminates the cropping procedure of an image.
     */
    public static void endImageCropping(Intent data) {
        CropImage.ActivityResult result = CropImage.getActivityResult(data);
        Uri uri = result.getUri(); // E.g. 'file:///storage/emulated/0/Android/data/app.familygem/files/5/anna.webp'
        String path = getFilePathFromUri(uri);
        Global.croppedMedia.setFile(path);
        Global.mediaFolderPath = path.substring(0, path.lastIndexOf('/'));
        saveFolderInSettings();
        // Clears any image from the Glide cache
        new Thread() {
            @Override
            public void run() {
                Glide.get(Global.context).clearDiskCache();
            }
        }.start();
    }

    /**
     * Answering all permission requests for Android 6+.
     */
    public static void permissionsResult(Context context, Fragment fragment, int code, String[] permissions, int[] grantResults, MediaContainer container) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            displayImageCaptureDialog(context, fragment, code, container);
        } else {
            String permission = "Read media";
            if (permissions.length > 0) // From Android 13 permissions is sometimes empty
                permission = permissions[0].substring(permissions[0].lastIndexOf('.') + 1);
            Toast.makeText(context, context.getString(R.string.not_granted, permission), Toast.LENGTH_LONG).show();
        }
    }
}
