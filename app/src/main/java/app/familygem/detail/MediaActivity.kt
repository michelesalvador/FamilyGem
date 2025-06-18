package app.familygem.detail

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import app.familygem.DetailActivity
import app.familygem.FileActivity
import app.familygem.FileUri
import app.familygem.Global
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

class MediaActivity : DetailActivity() {

    lateinit var media: Media
    lateinit var fileUri: FileUri
    private lateinit var imageLayout: View

    override fun onCreate(savedInstanceState: Bundle?) {
        // Creates new empty media
        val destination =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getSerializableExtra(Extra.DESTINATION, Destination::class.java)
            else intent.getSerializableExtra(Extra.DESTINATION) as Destination?
        if (destination != null) {
            val media: Media
            if (destination == Destination.SIMPLE_MEDIA) { // Simple media
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
        fileUri = FileUtil.showImage(media, imageView, 0, imageLayout.findViewById(R.id.image_progress))
        imageLayout.setOnClickListener {
            when (val fileType = imageView.getTag(R.id.tag_file_type) as Type) {
                Type.NONE, Type.PLACEHOLDER -> { // Placeholder instead of image, the media is loading or doesn't exist
                    FileUtil.displayFileChooser(this, chooseMediaLauncher)
                }
                else -> { // Opens the file in FileActivity
                    Global.editedMedia = `object` as Media
                    val intent = Intent(this@MediaActivity, FileActivity::class.java).putExtra(Extra.TYPE, fileType)
                    startActivity(intent)
                }
            }
        }
        imageLayout.setTag(R.id.tag_object, 43614 /* TODO: magic number */) // For the image context menu
        registerForContextMenu(imageLayout)
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
