package app.familygem;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

import com.google.gson.JsonPrimitive;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;
import org.folg.gedcom.model.Person;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import app.familygem.detail.MediaActivity;
import app.familygem.list.MediaFragment;
import app.familygem.visitor.MediaList;

/**
 * Static functions to manage files and media.
 */
public class F {

    /**
     * Wrapper of uriFilePath() to get a folder in KitKat.
     */
    static String uriPathFolderKitKat(Context context, Uri uri) {
        String path = uriFilePath(uri);
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
    static String uriFilePath(Uri uri) {
        if (uri == null) return null;
        if (uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("file")) {
            // Remove 'file://'
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
                        } catch (Exception e) {
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
    public static String uriFolderPath(Uri uri) {
        if (uri == null) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            String treeDocId = DocumentsContract.getTreeDocumentId(uri);
            switch (uri.getAuthority()) {
                case "com.android.externalstorage.documents": // internal memory and SD card
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
                case "com.android.providers.downloads.documents": // provider Downloads e sottocartelle
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
    static void saveDocument(Activity activity, Fragment fragment, int treeId, String mime, String ext, int requestCode) {
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

    // Methods for displaying images:

    /**
     * Receives a Person and chooses the main Media from which to get the image.
     */
    public static Media showMainImageForPerson(Gedcom gedcom, Person person, ImageView imageView) {
        MediaList mediaList = new MediaList(gedcom, 0);
        person.accept(mediaList);
        Media media = null;
        for (Media med : mediaList.list) { // Looks for a media with Primary value "Y"
            if (med.getPrimary() != null && med.getPrimary().equals("Y")) {
                showImage(med, imageView, null);
                media = med;
                break;
            }
        }
        if (media == null) { // Alternatively, returns the first one it finds
            for (Media med : mediaList.list) {
                showImage(med, imageView, null);
                media = med;
                break;
            }
        }
        imageView.setVisibility(media != null ? View.VISIBLE : View.GONE);
        return media;
    }

    /**
     * Shows a picture with Picasso.
     */
    public static void showImage(Media media, ImageView imageView, ProgressBar progressBar) {
        int treeId;
        // ProcessActivity needs the shared tree ID to search its folder
        View likely = null;
        if (imageView.getParent() != null && imageView.getParent().getParent() != null)
            likely = (View)imageView.getParent().getParent().getParent();
        if (likely != null && likely.getId() == R.id.confronto_nuovo)
            treeId = Global.treeId2;
        else treeId = Global.settings.openTree;
        String path = mediaPath(treeId, media);
        Uri[] uri = new Uri[1];
        if (path == null)
            uri[0] = mediaUri(treeId, media);
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        imageView.setTag(R.id.tag_file_type, 0);
        if (path != null || uri[0] != null) {
            RequestCreator creator;
            if (path != null)
                creator = Picasso.get().load("file://" + path);
            else
                creator = Picasso.get().load(uri[0]);
            creator.placeholder(R.drawable.image).fit().centerInside()
                    .into(imageView, new Callback() {
                        @Override
                        public void onSuccess() {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            imageView.setTag(R.id.tag_file_type, 1);
                            imageView.setTag(R.id.tag_percorso, path); // 'path' or 'uri' one of the 2 is valid, the other is null
                            imageView.setTag(R.id.tag_uri, uri[0]);
                            // On MediaActivity reloads the options menu to show the Crop command
                            if (imageView.getId() == R.id.immagine_foto) {
                                if (imageView.getContext() instanceof Activity) // In KitKat it is instance of TintContextWrapper
                                    ((Activity)imageView.getContext()).invalidateOptionsMenu();
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            // Maybe it's a video to make a thumbnail from
                            Bitmap bitmap = null;
                            try { // To avoid crashing of video thumbnail generators
                                bitmap = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND);
                                // Via the URI
                                if (bitmap == null && uri[0] != null) {
                                    MediaMetadataRetriever mMR = new MediaMetadataRetriever();
                                    mMR.setDataSource(Global.context, uri[0]);
                                    bitmap = mMR.getFrameAtTime();
                                }
                            } catch (Exception excpt) {
                            }
                            imageView.setTag(R.id.tag_file_type, 2);
                            if (bitmap == null) {
                                // A local file with no preview
                                String format = media.getFormat();
                                if (format == null)
                                    format = path != null ? MimeTypeMap.getFileExtensionFromUrl(path.replaceAll("[^a-zA-Z0-9./]", "_")) : "";
                                // Removes whitespace that does not make find the file extension
                                if (format.isEmpty() && uri[0] != null)
                                    format = MimeTypeMap.getFileExtensionFromUrl(uri[0].getLastPathSegment());
                                bitmap = generateIcon(imageView, R.layout.media_file, format);
                                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                                if (imageView.getParent() instanceof RelativeLayout && // Ugly but effective
                                        ((RelativeLayout)imageView.getParent()).findViewById(R.id.media_testo) != null) {
                                    RelativeLayout.LayoutParams param = new RelativeLayout.LayoutParams(
                                            RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
                                    param.addRule(RelativeLayout.ABOVE, R.id.media_testo);
                                    imageView.setLayoutParams(param);
                                }
                                imageView.setTag(R.id.tag_file_type, 3);
                            }
                            imageView.setImageBitmap(bitmap);
                            imageView.setTag(R.id.tag_percorso, path);
                            imageView.setTag(R.id.tag_uri, uri[0]);
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                        }
                    });
        } else if (media.getFile() != null && !media.getFile().isEmpty()) { // Maybe it's an image on the internet
            String filePath = media.getFile();
            Picasso.get().load(filePath).fit()
                    .placeholder(R.drawable.image).centerInside()
                    .into(imageView, new Callback() {
                        @Override
                        public void onSuccess() {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            imageView.setTag(R.id.tag_file_type, 1);
                            try {
                                new CacheImage(media).execute(new URL(filePath));
                            } catch (Exception e) {
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            // Let's try a web page
                            new DownloadImage(imageView, progressBar, media).execute(filePath);
                        }
                    });
        } else { // Media without a link to a file
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            imageView.setImageResource(R.drawable.image);
        }
    }

    /**
     * It receives a Media and looks for the file on the device with different path combinations.
     */
    public static String mediaPath(int treeId, Media m) {
        String file = m.getFile();
        if (file != null && !file.isEmpty()) {
            String name = file.replace("\\", "/");
            // File path as written in the Gedcom
            if (new File(name).canRead())
                return name;
            for (String dir : Global.settings.getTree(treeId).dirs) {
                // Media folder + File path
                String path = dir + '/' + name;
                File test = new File(path);
                /* TODO: Sometimes File.isFile() produces an ANR, like https://stackoverflow.com/questions/224756
                 *  I tried with various non-existent paths, such as the removed SD card, or with absurd characters,
                 *  but they all simply return false.
                 *  Probably the ANR is when the path points to an existing resource that waits indefinitely. */
                if (test.isFile() && test.canRead())
                    return path;
                // Media folder + filename
                path = dir + '/' + new File(name).getName();
                test = new File(path);
                if (test.isFile() && test.canRead())
                    return path;
            }
            Object string = m.getExtension("cache");
            // Sometimes it is String sometimes JsonPrimitive, I don't quite understand why
            if (string != null) {
                String cachePath;
                if (string instanceof String)
                    cachePath = (String)string;
                else
                    cachePath = ((JsonPrimitive)string).getAsString();
                if (new File(cachePath).isFile())
                    return cachePath;
            }
        }
        return null;
    }

    /**
     * Receives a Media, looks for the file in the device with any tree-URIs and returns the URI
     */
    public static Uri mediaUri(int treeId, Media m) {
        String file = m.getFile();
        if (file != null && !file.isEmpty()) {
            // OBJE.FILE is never a Uri, always a path (Windows or Android)
            String filename = new File(file.replace("\\", "/")).getName();
            for (String uri : Global.settings.getTree(treeId).uris) {
                DocumentFile documentDir = DocumentFile.fromTreeUri(Global.context, Uri.parse(uri));
                DocumentFile docFile = documentDir.findFile(filename);
                if (docFile != null && docFile.isFile())
                    return docFile.getUri();
            }
        }
        return null;
    }

    static Bitmap generateIcon(ImageView view, int icona, String testo) {
        LayoutInflater inflater = (LayoutInflater)view.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View inflated = inflater.inflate(icona, null);
        RelativeLayout frameLayout = inflated.findViewById(R.id.icona);
        ((TextView)frameLayout.findViewById(R.id.icona_testo)).setText(testo);
        frameLayout.setDrawingCacheEnabled(true);
        frameLayout.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        frameLayout.layout(0, 0, frameLayout.getMeasuredWidth(), frameLayout.getMeasuredHeight());
        frameLayout.buildDrawingCache(true);
        return frameLayout.getDrawingCache();
    }

    /**
     * Cache an image found on the internet for reuse
     * TODO: maybe it might not even be an asynchronous task but a simple function
     */
    static class CacheImage extends AsyncTask<URL, Void, String> {
        Media media;

        CacheImage(Media media) {
            this.media = media;
        }

        protected String doInBackground(URL... url) {
            try {
                File cacheFolder = new File(Global.context.getCacheDir().getPath() + "/" + Global.settings.openTree);
                if (!cacheFolder.exists()) {
                    // Delete the "cache" extension from all Media
                    MediaList mediaList = new MediaList(Global.gc, 0);
                    Global.gc.accept(mediaList);
                    for (Media media : mediaList.list)
                        if (media.getExtension("cache") != null)
                            media.putExtension("cache", null);
                    cacheFolder.mkdir();
                }
                String extension = FilenameUtils.getName(url[0].getPath());
                if (extension.lastIndexOf('.') > 0)
                    extension = extension.substring(extension.lastIndexOf('.') + 1);
                String ext;
                switch (extension) {
                    case "png":
                        ext = "png";
                        break;
                    case "gif":
                        ext = "gif";
                        break;
                    case "bmp":
                        ext = "bmp";
                        break;
                    case "jpg":
                    case "jpeg":
                    default:
                        ext = "jpg";
                }
                File cache = nextAvailableFileName(cacheFolder.getPath(), "img." + ext);
                FileUtils.copyURLToFile(url[0], cache);
                return cache.getPath();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        protected void onPostExecute(String path) {
            if (path != null)
                media.putExtension("cache", path);
        }
    }

    /**
     * Asynchronously downloads an image from an internet page.
     */
    static class DownloadImage extends AsyncTask<String, Integer, Bitmap> {
        ImageView imageView;
        ProgressBar progressBar;
        Media media;
        URL url;
        int fileTypeTag = 0; // setTag must be in the main thread, not in the doInBackground.
        int imageViewWidth; // ditto

        DownloadImage(ImageView imageView, ProgressBar progressBar, Media media) {
            this.imageView = imageView;
            this.progressBar = progressBar;
            this.media = media;
            imageViewWidth = imageView.getWidth();
        }

        @Override
        protected Bitmap doInBackground(String... params) {
            Bitmap bitmap;
            try {
                Connection connection = Jsoup.connect(params[0]);
                //if (connection.equals(bitmap)) {	// TODO: verify that an address is associated with the hostname
                Document doc = connection.get();
                List<Element> list = doc.select("img");
                if (list.isEmpty()) { // Web page found but without images
                    fileTypeTag = 3;
                    url = new URL(params[0]);
                    return generateIcon(imageView, R.layout.media_mondo, url.getProtocol());    // returns a bitmap
                }
                int maxDimensionsWithAlt = 1;
                int maxDimensions = 1;
                int maxLengthAlt = 0;
                int maxLengthSrc = 0;
                Element imgHeightWithAlt = null;
                Element imgHeight = null;
                Element imgLengthAlt = null;
                Element imgLengthSrc = null;
                for (Element img : list) {
                    int width, height;
                    if (img.attr("width").isEmpty()) width = 1;
                    else width = Integer.parseInt(img.attr("width"));
                    if (img.attr("height").isEmpty()) height = 1;
                    else height = Integer.parseInt(img.attr("height"));
                    if (width * height > maxDimensionsWithAlt && !img.attr("alt").isEmpty()) { // The largest image with alt
                        imgHeightWithAlt = img;
                        maxDimensionsWithAlt = width * height;
                    }
                    if (width * height > maxDimensions) { // The largest image even without alt
                        imgHeight = img;
                        maxDimensions = width * height;
                    }
                    if (img.attr("alt").length() > maxLengthAlt) { // The image with the longest alt
                        imgLengthAlt = img;
                        maxLengthAlt = img.attr("alt").length();
                    }
                    if (img.attr("src").length() > maxLengthSrc) { // The image with the longest src
                        imgLengthSrc = img;
                        maxLengthSrc = img.attr("src").length();
                    }
                }
                String percorso = null;
                if (imgHeightWithAlt != null)
                    percorso = imgHeightWithAlt.absUrl("src"); // Absolute URL on src
                else if (imgHeight != null)
                    percorso = imgHeight.absUrl("src");
                else if (imgLengthAlt != null)
                    percorso = imgLengthAlt.absUrl("src");
                else if (imgLengthSrc != null)
                    percorso = imgLengthSrc.absUrl("src");
                url = new URL(percorso);
                InputStream inputStream = url.openConnection().getInputStream();
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true; // it just takes the info of the image without downloading it
                BitmapFactory.decodeStream(inputStream, null, options);
                // Finally try to load the actual image by resizing it
                if (options.outWidth > imageViewWidth)
                    options.inSampleSize = options.outWidth / (imageViewWidth + 1);
                inputStream = url.openConnection().getInputStream();
                options.inJustDecodeBounds = false;    // Download the image
                bitmap = BitmapFactory.decodeStream(inputStream, null, options);
                fileTypeTag = 1;
            } catch (Exception e) {
                return null;
            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            imageView.setTag(R.id.tag_file_type, fileTypeTag);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                imageView.setTag(R.id.tag_percorso, url.toString()); // Used by MediaActivity
                if (fileTypeTag == 1)
                    new CacheImage(media).execute(url);
            }
            if (progressBar != null) // Maybe we have already leaved the activity and the ProgressBar of the image no longer exists
                progressBar.setVisibility(View.GONE);
        }
    }

    public static int checkMultiplePermissions(final Context context, final String... permissions) {
        int result = PackageManager.PERMISSION_GRANTED;
        for (String permission: permissions) {
            result |= ContextCompat.checkSelfPermission(context, permission);
        }

        return result;
    }

    // Methods for image acquisition:

    /**
     * Offers a nice list of apps for capturing images
     */
    public static void displayImageCaptureDialog(Context context, Fragment fragment, int code, MediaContainer container) {
        // Request permission to access device memory
        final String[] requiredPermissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions = new String[] {
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO,
            };
        } else {
            requiredPermissions = new String[] {
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
        // Collect intents useful to capture images
        List<ResolveInfo> resolveInfos = new ArrayList<>();
        final List<Intent> intents = new ArrayList<>();
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
            mimeTypes[0] = "*/*"; // Otherwise KitKat does not see the 'application / *' in Downloads
        galleryIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        for (ResolveInfo info : context.getPackageManager().queryIntentActivities(galleryIntent, 0)) {
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
                        File dir = context.getExternalFilesDir(String.valueOf(Global.settings.openTree));
                        if (!dir.exists())
                            dir.mkdir();
                        File photoFile = nextAvailableFileName(dir.getAbsolutePath(), "image.jpg");
                        Global.pathOfCameraDestination = photoFile.getAbsolutePath(); // Saves it to retake it after the photo is taken
                        Uri photoUri;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                            photoUri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", photoFile);
                        else // KitKat
                            photoUri = Uri.fromFile(photoFile);
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                    }
                    if (intent.getComponent().getPackageName().equals("app.familygem")) { // TODO: extract to build property
                        // Create an empty Media
                        Media med;
                        if (code == 4173 || code == 2173) { // Simple media
                            med = new Media();
                            med.setFileTag("FILE");
                            container.addMedia(med);
                            Memory.add(med);
                        } else { // Shared media
                            med = MediaFragment.newMedia(container);
                            Memory.setFirst(med);
                        }
                        med.setFile("");
                        context.startActivity(intent);
                        U.save(true, Memory.firstObject());
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
                if (info.activityInfo.packageName.equals("app.familygem")) {
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
     * Saves the retrieved file and proposes to crop it if it is an image.
     *
     * @return true if opens the dialog and therefore the updating of the activity must be blocked
     */
    public static boolean proposeCropping(Context context, Fragment fragment, Intent data, Media media) {
        // Finds the path of the image
        Uri uri = null;
        String path;
        // Content taken with SAF
        if (data != null && data.getData() != null) {
            uri = data.getData();
            path = uriFilePath(uri);
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
        } else { // It is just the file name 'myFile.ext' or more rarely null
            // External app storage: /storage/emulated/0/Android/data/app.familygem/files/12
            File externalFilesDir = context.getExternalFilesDir(String.valueOf(Global.settings.openTree));
            try { // We use the URI
                InputStream input = context.getContentResolver().openInputStream(uri);
                // TODO: if the file already exists, do not duplicate it but reuse it: as in ConfirmationActivity.copyFiles
                if (path == null) { // Null filename, must be created from scratch
                    String type = context.getContentResolver().getType(uri);
                    path = type.substring(0, type.indexOf('/')) + "."
                            + MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
                }
                fileMedia[0] = nextAvailableFileName(externalFilesDir.getAbsolutePath(), path);
                FileUtils.copyInputStreamToFile(input, fileMedia[0]); // Crea la cartella se non esiste
            } catch (Exception e) {
                String msg = e.getLocalizedMessage() != null ? e.getLocalizedMessage() : context.getString(R.string.something_wrong);
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
            }
        }
        // Adds the folder path in the Tree in settings
        if (Global.settings.getCurrentTree().dirs.add(fileMedia[0].getParent())) // true if it added the folder
            Global.settings.save();
        // Sets the found path in the Media
        media.setFile(fileMedia[0].getAbsolutePath());

        // If it is an image it opens the cropping proposal dialog
        String mimeType = URLConnection.guessContentTypeFromName(fileMedia[0].getName());
        if (mimeType != null && mimeType.startsWith("image/")) {
            ImageView imageView = new ImageView(context);
            showImage(media, imageView, null);
            Global.croppedMedia = media; // Media parked waiting to be updated with new file path
            Global.edited = false; // In order not to refresh activity which in recent Androids does not show the AlertDialog
            new AlertDialog.Builder(context)
                    .setView(imageView)
                    .setMessage(R.string.want_crop_image)
                    .setPositiveButton(R.string.yes, (dialog, id) -> cropImage(context, fileMedia[0], null, fragment))
                    .setNeutralButton(R.string.no, (dialog, which) -> {
                        finishProposeCropping(context, fragment);
                    }).setOnCancelListener(dialog -> { // Click out of the dialog
                        finishProposeCropping(context, fragment);
                    }).show();
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, U.dpToPx(320));
            imageView.setLayoutParams(params); // The size assignment must come AFTER creating the dialog
            return true;
        }
        return false;
    }

    /**
     * Negative conclusion of the image cropping proposal: simply refresh the page to show the image
     */
    static void finishProposeCropping(Context context, Fragment fragment) {
        if (fragment instanceof MediaFragment)
            ((MediaFragment)fragment).recreate();
        else if (context instanceof DetailActivity)
            ((DetailActivity)context).refresh();
        else if (context instanceof ProfileActivity) {
            ((ProfileActivity)context).refresh();
        }
        Global.edited = true; // To update previous pages
    }

    /**
     * Starts cropping an image with CropImage.
     * 'fileMedia' and 'uriMedia': one of the two is valid, the other is null.
     */
    static void cropImage(Context context, File fileMedia, Uri uriMedia, Fragment fragment) {
        // Origin
        if (uriMedia == null)
            uriMedia = Uri.fromFile(fileMedia);
        // Destination
        File externalFilesDir = context.getExternalFilesDir(String.valueOf(Global.settings.openTree));
        if (!externalFilesDir.exists())
            externalFilesDir.mkdir();
        File destinationFile;
        if (fileMedia != null && fileMedia.getAbsolutePath().startsWith(externalFilesDir.getAbsolutePath()))
            destinationFile = fileMedia; // Files already in the storage folder are overwritten
        else {
            String name;
            if (fileMedia != null)
                name = fileMedia.getName();
            else // URI
                name = DocumentFile.fromSingleUri(context, uriMedia).getName();
            destinationFile = nextAvailableFileName(externalFilesDir.getAbsolutePath(), name);
        }
        Intent intent = CropImage.activity(uriMedia)
                .setOutputUri(Uri.fromFile(destinationFile)) // Folder in external memory
                .setGuidelines(CropImageView.Guidelines.OFF)
                .setBorderLineThickness(1)
                .setBorderCornerThickness(6)
                .setBorderCornerOffset(-3)
                .setCropMenuCropButtonTitle(context.getText(R.string.done))
                .getIntent(context);
        if (fragment != null)
            fragment.startActivityForResult(intent, CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE);
        else
            ((AppCompatActivity)context).startActivityForResult(intent, CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE);
    }

    /**
     * If a file with that name already exists in that folder, increment it appending 1 2 3...
     */
    public static File nextAvailableFileName(String dir, String name) {
        File file = new File(dir, name);
        int increment = 0;
        while (file.exists()) {
            increment++;
            file = new File(dir, name.substring(0, name.lastIndexOf('.'))
                    + increment + name.substring(name.lastIndexOf('.')));
        }
        return file;
    }

    /**
     * Ends the cropping procedure of an image.
     */
    public static void endImageCropping(Intent data) {
        CropImage.ActivityResult risultato = CropImage.getActivityResult(data);
        Uri uri = risultato.getUri(); // Eg. 'file:///storage/emulated/0/Android/data/app.familygem/files/5/anna.webp'
        Picasso.get().invalidate(uri); // Clears from the cache any image that has the same path
        String path = uriFilePath(uri);
        Global.croppedMedia.setFile(path);
    }

    /**
     * Answering all permission requests for Android 6+.
     */
    public static void permissionsResult(Context context, Fragment fragment, int code, String[] permissions, int[] grantResults, MediaContainer container) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            displayImageCaptureDialog(context, fragment, code, container);
        } else {
            String permission = permissions[0].substring(permissions[0].lastIndexOf('.') + 1);
            Toast.makeText(context, context.getString(R.string.not_granted, permission), Toast.LENGTH_LONG).show();
        }
    }
}
