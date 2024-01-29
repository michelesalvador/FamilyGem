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
import app.familygem.constant.Extra;
import app.familygem.constant.Type;
import app.familygem.main.MediaFragment;
import app.familygem.util.ChangeUtil;
import app.familygem.util.FileUtil;
import app.familygem.visitor.MediaReferences;

public class MediaActivity extends DetailActivity {

    Media media;
    View imageLayout;

    @Override
    protected void format() {
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
        place(getString(R.string.file), "File", true, InputType.TYPE_CLASS_TEXT); // File name
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
        else if (((Activity)box.getContext()).getIntent().getBooleanExtra(Extra.ALONE, false))
            U.placeCabinet(box, Memory.getLeaderObject(), R.string.into);
    }

    private void displayMedia(Media media, int position) {
        imageLayout = LayoutInflater.from(this).inflate(R.layout.image_layout, box, false);
        box.addView(imageLayout, position);
        ImageView imageView = imageLayout.findViewById(R.id.image_picture);
        FileUtil.INSTANCE.showImage(media, imageView, 0, imageLayout.findViewById(R.id.image_progress));
        imageLayout.setOnClickListener(view -> {
            String path = (String)imageView.getTag(R.id.tag_path);
            Uri uri = (Uri)imageView.getTag(R.id.tag_uri);
            Type fileType = (Type)imageView.getTag(R.id.tag_file_type);
            if (fileType == Type.NONE || fileType == Type.PLACEHOLDER) { // Placeholder instead of image, the media is loading or doesn't exist
                F.displayImageCaptureDialog(this, null, 5173, null);
            } else if (fileType == Type.PREVIEW || fileType == Type.DOCUMENT) { // Opens the media with some other app
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
                intent.putExtra(Extra.PATH, path);
                if (uri != null)
                    intent.putExtra(Extra.URI, uri.toString());
                startActivity(intent);
            }
        });
        imageLayout.setTag(R.id.tag_object, 43614 /* TODO: magic number */); // For the image context menu
        registerForContextMenu(imageLayout);
    }

    public void updateImage() {
        int position = box.indexOfChild(imageLayout);
        box.removeView(imageLayout);
        displayMedia(media, position);
    }

    @Override
    public void delete() {
        ChangeUtil.INSTANCE.updateChangeDate(MediaFragment.deleteMedia(media, null));
    }
}
