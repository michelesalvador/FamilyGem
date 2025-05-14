package app.familygem.detail

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.core.content.FileProvider
import app.familygem.BuildConfig
import app.familygem.DetailActivity
import app.familygem.Global
import app.familygem.ImageActivity
import app.familygem.Memory
import app.familygem.R
import app.familygem.constant.Destination
import app.familygem.constant.Extra
import app.familygem.constant.Type
import app.familygem.util.ChangeUtil
import app.familygem.util.FileUtil
import app.familygem.util.MediaUtil
import app.familygem.util.NoteUtil
import app.familygem.util.TreeUtil
import app.familygem.visitor.MediaReferences
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.MediaContainer
import java.io.File
import java.net.URLConnection

class MediaActivity : DetailActivity() {
    lateinit var media: Media
    private lateinit var imageLayout: View

    override fun onCreate(savedInstanceState: Bundle?) {
        // Creates new empty media
        val destination =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getSerializableExtra(Extra.DESTINATION, Destination::class.java)
            else intent.getSerializableExtra(Extra.DESTINATION) as Destination?
        if (destination != null) {
            val media: Media
            if (destination == Destination.LOCAL_MEDIA) { // Simple media
                media = Media()
                media.fileTag = "FILE"
                (Memory.getLastObject() as MediaContainer).addMedia(media)
                Memory.add(media)
            } else { // Shared media
                media = MediaUtil.newSharedMedia(Memory.getLeaderObject() as MediaContainer?)
                Memory.setLeader(media)
            }
            media.file = ""
            TreeUtil.save(true, Memory.getLeaderObject())
        }
        super.onCreate(savedInstanceState)
    }

    override fun format() {
        media = cast(Media::class.java) as Media
        if (media.id != null) { // Only shared Media have ID, inline Media don't
            setTitle(R.string.shared_media)
            placeSlug("OBJE", media.id)
        } else {
            setTitle(R.string.media)
            placeSlug("OBJE", null)
        }
        displayMedia(media, box.childCount)
        place(getString(R.string.title), "Title")
        place(getString(R.string.type), "Type", false, 0) // Tag '_TYPE' not GEDCOM standard
        place(getString(R.string.file), "File", true, InputType.TYPE_CLASS_TEXT) // File name
        // File format, e.g. 'JPEG'
        place(getString(R.string.format), "Format", Global.settings.expert, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        place(getString(R.string.primary), "Primary") // Tag '_PRIM' not GEDCOM standard, but used to select main media
        place(getString(R.string.scrapbook), "Scrapbook", false, 0) // Scrapbook that contains the Media record, not GEDCOM standard
        place(getString(R.string.slideshow), "SlideShow", false, 0) // Not GEDCOM standard
        place(getString(R.string.blob), "Blob", false, InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        //media.fileTag; // The tag, could be 'FILE' or '_FILE'
        placeExtensions(media)
        NoteUtil.placeNotes(box, media)
        ChangeUtil.placeChangeDate(box, media.change)
        // List of records in which the media is used
        val mediaReferences = MediaReferences(Global.gc, media, false)
        if (mediaReferences.leaders.isNotEmpty()) placeCabinet(mediaReferences.leaders.toTypedArray(), R.string.used_by)
        else if (intent.getBooleanExtra(Extra.ALONE, false)) placeCabinet(Memory.getLeaderObject(), R.string.into)
    }

    private fun displayMedia(media: Media, position: Int) {
        imageLayout = LayoutInflater.from(this).inflate(R.layout.image_layout, box, false)
        box.addView(imageLayout, position)
        val imageView = imageLayout.findViewById<ImageView>(R.id.image_picture)
        FileUtil.showImage(media, imageView, 0, imageLayout.findViewById(R.id.image_progress))
        imageLayout.setOnClickListener {
            val path = imageView.getTag(R.id.tag_path) as String?
            var uri = imageView.getTag(R.id.tag_uri) as Uri?
            val fileType = imageView.getTag(R.id.tag_file_type) as Type
            when (fileType) {
                Type.NONE, Type.PLACEHOLDER -> { // Placeholder instead of image, the media is loading or doesn't exist
                    FileUtil.displayFileChooser(this, chooseMediaLauncher)
                }
                Type.PREVIEW, Type.DOCUMENT -> { // Opens the media with some other app
                    if (path != null) {
                        val file = File(path)
                        uri = if (isOwnedDirectory(path)) // File provider of its own folders
                            FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", file)
                        else Uri.fromFile(file)
                    }
                    val mimeType = if (uri?.scheme == "content") contentResolver.getType(uri)
                    else {
                        // Hack to disable FileUriExposedException on file:// URIs
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            StrictMode::class.java.getMethod("disableDeathOnFileUriExposure").invoke(null)
                        }
                        URLConnection.guessContentTypeFromName(uri?.lastPathSegment)
                    }
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(uri, mimeType)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Necessary for folders owned by the app (provider)
                    val resolvers = packageManager.queryIntentActivities(intent, 0)
                    /* It's possible that the MIME type of some extension (e.g. '.pdf') is properly found,
                       but maybe there is no any predefined app to open the file */
                    if (mimeType == null || resolvers.isEmpty()) {
                        intent.setDataAndType(uri, "*/*") // Ugly list of generic apps
                    }
                    startActivity(intent)
                }
                else -> { // Proper image that can be zoomed
                    Global.croppedMedia = `object` as Media?
                    val intent = Intent(this@MediaActivity, ImageActivity::class.java)
                    intent.putExtra(Extra.PATH, path)
                    if (uri != null) intent.putExtra(Extra.URI, uri.toString())
                    startActivity(intent)
                }
            }
        }
        imageLayout.setTag(R.id.tag_object, 43614 /* TODO: magic number */) // For the image context menu
        registerForContextMenu(imageLayout)
    }

    /** Checks if a path points to an external storage folder owned by the app. */
    private fun isOwnedDirectory(path: String): Boolean {
        if (path.startsWith(getExternalFilesDir(null)!!.absolutePath)) return true
        for (mediaDir in externalMediaDirs) if (path.startsWith(mediaDir.absolutePath)) return true
        return false
    }

    fun updateImage() {
        val position = box.indexOfChild(imageLayout)
        box.removeView(imageLayout)
        displayMedia(media, position)
    }

    override fun delete() {
        val leaders = MediaUtil.deleteMedia(media)
        ChangeUtil.updateChangeDate(*leaders)
    }
}
