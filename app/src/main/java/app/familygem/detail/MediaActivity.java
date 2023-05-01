package app.familygem.detail;

import static app.familygem.Global.gc;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.StrictMode;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;

import androidx.core.content.FileProvider;

import org.folg.gedcom.model.Media;

import java.io.File;
import java.util.List;

import app.familygem.BuildConfig;
import app.familygem.DetailActivity;
import app.familygem.F;
import app.familygem.Global;
import app.familygem.ImageActivity;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import app.familygem.list.MediaFragment;
import app.familygem.util.ChangeUtils;
import app.familygem.visitor.MediaReferences;

public class MediaActivity extends DetailActivity {

    Media media;
    View imageView;

    @Override
    public void format() {
        media = (Media)cast(Media.class);
        if (media.getId() != null) { // Only shared Media have ID, inline Media don't
            setTitle(R.string.shared_media);
            placeSlug("OBJE", media.getId());
        } else {
            setTitle(R.string.media);
            placeSlug("OBJE", null);
        }
        displayMedia(media, box.getChildCount());
        place(getString(R.string.title), "Title");
        place(getString(R.string.type), "Type", false, 0); // Tag '_TYPE' not GEDCOM standard
        // TODO: File string should be max 259 characters according to GEDCOM 5.5.5 specs
        place(getString(R.string.file), "File", Global.settings.expert, InputType.TYPE_CLASS_TEXT); // File name, visible only with advanced tools
        place(getString(R.string.format), "Format", Global.settings.expert, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS); // File format, e.g. 'JPEG'
        place(getString(R.string.primary), "Primary"); // Tag '_PRIM' not GEDCOM standard, but used to select main media
        place(getString(R.string.scrapbook), "Scrapbook", false, 0); // Scrapbook that contains the Media record, not GEDCOM standard
        place(getString(R.string.slideshow), "SlideShow", false, 0); // Not GEDCOM standard
        place(getString(R.string.blob), "Blob", false, InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        //m.getFileTag(); // The tag, could be 'FILE' or '_FILE'
        placeExtensions(media);
        U.placeNotes(box, media, true);
        U.placeChangeDate(box, media.getChange());
        // List of records in which the media is used
        MediaReferences mediaReferences = new MediaReferences(gc, media, false);
        if (mediaReferences.leaders.size() > 0)
            U.placeCabinet(box, mediaReferences.leaders.toArray(), R.string.used_by);
        else if (((Activity)box.getContext()).getIntent().getBooleanExtra("daSolo", false))
            U.placeCabinet(box, Memory.getLeaderObject(), R.string.into);
    }

    void displayMedia(Media media, int position) {
        imageView = LayoutInflater.from(this).inflate(R.layout.immagine_immagine, box, false);
        box.addView(imageView, position);
        ImageView imageView = this.imageView.findViewById(R.id.immagine_foto);
        F.showImage(media, imageView, this.imageView.findViewById(R.id.immagine_circolo));
        this.imageView.setOnClickListener(vista -> {
            String path = (String)imageView.getTag(R.id.tag_percorso);
            Uri uri = (Uri)imageView.getTag(R.id.tag_uri);
            int fileType = (int)imageView.getTag(R.id.tag_file_type);
            if (fileType == 0) { // Placeholder instead of image, the media has to be found
                F.displayImageCaptureDialog(this, null, 5173, null);
            } else if (fileType == 2 || fileType == 3) { // Open the media with some other app
                // TODO: if the type is 3 but it is a url (web page without images) tries to open it as a file://
                if (path != null) {
                    File file = new File(path);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                            && path.startsWith(getExternalFilesDir(null).getPath())) // An app can be a file provider only of its folders
                        uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", file);
                    else // Under KitKat all folders can be accessed
                        uri = Uri.fromFile(file);
                }
                String mimeType = getContentResolver().getType(uri);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, mimeType);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Necessary for folders owned by the app (provider)
                List<ResolveInfo> resolvers = getPackageManager().queryIntentActivities(intent, 0);
                /* It's possible that the mime type of some extension (e.g. '.pdf') is properly found,
                   but maybe there is no any predefined app to open the file */
                if (mimeType == null || resolvers.isEmpty()) {
                    intent.setDataAndType(uri, "*/*"); // Ugly list of generic apps
                }
                // From android 7 (Nougat API 24) uri file:// are banned in favor of uri content:// so the file can't be opened
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try {
                        StrictMode.class.getMethod("disableDeathOnFileUriExposure").invoke(null); // TODO: don't use reflection to use functions you shouldn't!
                    } catch (Exception e) {
                    }
                }
                startActivity(intent);
            } else { // Proper image that can be zoomed
                Intent intent = new Intent(MediaActivity.this, ImageActivity.class);
                intent.putExtra("path", path);
                if (uri != null)
                    intent.putExtra("uri", uri.toString());
                startActivity(intent);
            }
        });
        this.imageView.setTag(R.id.tag_object, 43614 /* TODO: magic number */); // For the image context menu
        registerForContextMenu(this.imageView);
    }

    public void updateImage() {
        int position = box.indexOfChild(imageView);
        box.removeView(imageView);
        displayMedia(media, position);
    }

    @Override
    public void delete() {
        ChangeUtils.INSTANCE.updateChangeDate(MediaFragment.deleteMedia(media, null));
    }
}
