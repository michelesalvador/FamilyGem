package app.familygem.util

import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import app.familygem.BuildConfig
import app.familygem.CropImageActivity
import app.familygem.Global
import app.familygem.R
import app.familygem.constant.Destination
import app.familygem.constant.Extra
import app.familygem.constant.FileType
import app.familygem.constant.Image
import app.familygem.constant.Type
import app.familygem.detail.MediaActivity
import app.familygem.visitor.MediaList
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import jp.wasabeef.glide.transformations.BlurTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.Person
import java.io.File
import java.net.URLConnection
import java.util.regex.Pattern

/** Functions to manage files and display images. */
object FileUtil {

    /** List of mime type to open and create GEDCOM files. */
    val gedcomMimeTypes = arrayOf("text/vnd.familysearch.gedcom", "application/octet-stream", "vnd.android.document/file", "text/plain")

    /** Extracts only the filename from a URI. */
    fun extractFilename(context: Context, uri: Uri, fallback: String): String {
        var filename: String? = null
        // file://
        if (uri.scheme != null && uri.scheme.equals("file", true)) {
            filename = uri.lastPathSegment
        }
        // Cursor (usually it's used this)
        if (filename == null) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                filename = cursor.getString(index)
                cursor.close()
            }
        }
        // DocumentFile
        if (filename == null) {
            val document = DocumentFile.fromSingleUri(context, uri)
            filename = document?.name
        }
        return filename ?: fallback
    }

    /**
     * Displays the chooser with all apps for capturing media files.
     * @param destination Can be LOCAL_MEDIA, SHARED_MEDIA or null
     */
    @JvmOverloads
    fun displayFileChooser(context: Context, launcher: ActivityResultLauncher<Intent>, destination: Destination? = null) {
        val intents = mutableListOf<Intent>()
        // Camera intents
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile = nextAvailableFileName(context.getExternalFilesDir(Global.settings.openTree.toString())!!, "photo.jpg")
        Global.cameraDestination = photoFile // Saves it to get it after the photo is taken
        val imageUri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", photoFile)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
        intents.add(cameraIntent)
        // Gallery intents
        val galleryIntent = Intent(Intent.ACTION_GET_CONTENT)
        galleryIntent.setType("*/*")
        galleryIntent.addCategory(Intent.CATEGORY_OPENABLE)
        context.packageManager.queryIntentActivities(galleryIntent, 0).forEach { info ->
            val finalIntent = Intent(galleryIntent)
            finalIntent.setComponent(ComponentName(info.activityInfo.packageName, info.activityInfo.name))
            intents.add(finalIntent)
        }
        // Empty media intent
        if (Global.settings.expert && destination != null) {
            val emptyMediaIntent = Intent(context, MediaActivity::class.java)
            emptyMediaIntent.putExtra(Extra.DESTINATION, destination)
            intents.add(emptyMediaIntent)
        }
        // File chooser
        val chooser = Intent.createChooser(intents[0], null)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            intents.removeAt(0)
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.toTypedArray())
        } else {
            chooser.putExtra(Intent.EXTRA_ALTERNATE_INTENTS, intents.toTypedArray())
        }
        launcher.launch(chooser)
    }

    /** If a file with that name already exists in that folder, increments it appending (1) (2) (3)... */
    fun nextAvailableFileName(folder: File, fileName: String): File {
        var newFileName = fileName
        var file = File(folder, newFileName)
        while (file.exists()) {
            var pattern = Pattern.compile("(.*)\\((\\d+)\\)\\s*(\\.\\w+$|$)")
            var match = pattern.matcher(newFileName)
            if (match.matches()) { // Filename terminates with digits between parenthesis e.g. "image (34).jpg"
                val number = match.group(2)!!.toInt() + 1
                newFileName = match.group(1)!! + "(" + number + ")" + match.group(3)
            } else {
                pattern = Pattern.compile("(.+)(\\..+)")
                match = pattern.matcher(newFileName)
                if (match.matches()) { // Filename with extension e.g. "image.jpg"
                    newFileName = match.group(1)!! + " (1)" + match.group(2)
                } else newFileName += " (1)" // Filename without extension e.g. ".image"
            }
            file = File(folder, newFileName)
        }
        return file
    }

    /**
     * Saves into [media] the file retrieved from [intent] and optionally proposes to crop it whether is an image.
     * @param intent Contains the Uri of a file
     * @return True on successfully setting the file (croppable or not)
     */
    fun setFileAndProposeCropping(context: Context, intent: Intent?, media: Media): Boolean {
        val mediaFile = if (intent?.data == null && Global.cameraDestination.isFile) {
            media.file = Global.cameraDestination.name
            Global.cameraDestination
        } else if (intent?.data != null) {
            val uri = intent.data!!
            val externalFilesDir = context.getExternalFilesDir(Global.settings.openTree.toString())!!
            val newFile = nextAvailableFileName(externalFilesDir, extractFilename(context, uri, "media file"))
            context.contentResolver.openInputStream(uri).use { inputStream ->
                newFile.outputStream().use { inputStream?.copyTo(it) }
            }
            media.file = newFile.name // Sets the file name in the media
            newFile
        } else null
        if (mediaFile != null) {
            // If is an image opens the cropping proposal dialog
            val mimeType = URLConnection.guessContentTypeFromName(mediaFile.name)
            if (mimeType != null && mimeType.startsWith("image/")) {
                Global.croppedMedia = media // Media parked waiting to be updated with new file path
                val alert: Dialog = AlertDialog.Builder(context).setView(R.layout.crop_image_dialog)
                    .setPositiveButton(R.string.yes) { _, _ -> cropImage(context, mediaFile, null) }
                    .setNeutralButton(R.string.no, null).show()
                showImage(media, alert.findViewById(R.id.crop_image))
            }
            return true
        }
        return false
    }

    /**
     * Starts cropping an image with CropImage.
     * [fileMedia] and [uriMedia]: one of the two is valid, the other is null.
     */
    fun cropImage(context: Context, fileMedia: File?, uriMedia: Uri?, launcher: ActivityResultLauncher<Intent>? = null) {
        // Media path key is stored to clear Glide cache after cropping
        val path = fileMedia?.absolutePath ?: uriMedia!!.path!!
        val cropped = if (Global.croppedPaths.containsKey(path)) Global.croppedPaths[path]!! else 0
        Global.croppedPaths[path] = cropped + 1
        // Launches the cropping activity
        val finalUri = uriMedia ?: Uri.fromFile(fileMedia)
        val intent = Intent(context, CropImageActivity::class.java)
        intent.data = finalUri
        if (launcher != null) launcher.launch(intent)
        else context.startActivity(intent)
    }

    /**
     * Receives a Person and returns the primary media from which to get the image, or the first media, or null.
     * @param imageView Where the image will appear
     * @param options Bitwise selection of [Image] constants
     * @param show Calls showImage() or not
     */
    @JvmOverloads
    fun selectMainImage(
        person: Person, imageView: ImageView, options: Int = 0, gedcom: Gedcom? = null, treeId: Int = 0, show: Boolean = true
    ): Media? {
        val mediaList = MediaList(gedcom ?: Global.gc, 0)
        person.accept(mediaList)
        var media: Media? = null
        // Looks for a media with Primary value "Y"
        for (media1 in mediaList.list) {
            if (media1.primary != null && media1.primary == "Y") {
                if (show) showImage(media1, imageView, options, null, treeId)
                media = media1
                break
            }
        }
        // Alternatively, uses the first one
        if (media == null && mediaList.list.isNotEmpty()) {
            media = mediaList.list.first()
            if (show) showImage(media, imageView, options, null, treeId)
        }
        imageView.visibility = if (media != null) View.VISIBLE else View.GONE
        return media
    }

    /** Shows a picture with Glide. */
    @JvmOverloads
    fun showImage(media: Media, imageView: ImageView, options: Int = 0, progressWheel: ProgressBar? = null, treeId: Int = 0) {
        fun applyOptions(builder: RequestBuilder<Drawable>) {
            if (options and Image.DARK != 0) {
                imageView.setColorFilter(ContextCompat.getColor(imageView.context, R.color.primary_grayed), PorterDuff.Mode.MULTIPLY)
            }
            if (options and Image.BLUR != 0) {
                builder.override(100, 100).apply(RequestOptions.bitmapTransform(BlurTransformation(4)))
            }
        }

        fun completeDisplay() {
            if (progressWheel != null) progressWheel.visibility = View.GONE
            imageView.tag = R.id.tag_object // Used by DiagramFragment to check the image finish loading
        }

        imageView.setTag(R.id.tag_file_type, Type.NONE)
        if (progressWheel != null) progressWheel.visibility = View.VISIBLE
        if (options and Image.GALLERY != 0) { // Regular image inside MediaAdapter
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            (imageView.layoutParams as RelativeLayout.LayoutParams).removeRule(RelativeLayout.ABOVE)
        }
        val treeId = if (treeId == 0) Global.settings.openTree else treeId
        val path: String? = getPathFromMedia(media, treeId)
        val uri: Uri? = if (path == null) getUriFromMedia(media, treeId) else null
        val glide = Glide.with(imageView.context)
        if (path != null || uri != null) {
            val pathOrUri = path ?: uri!!.path!! // 'path' or 'uri' one of the 2 is valid, the other is null
            // PDF preview
            if (pathOrUri.endsWith(".pdf", true)) {
                val fileDescriptor = if (path != null) ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
                else imageView.context.contentResolver.openFileDescriptor(uri!!, "r")
                var renderer: PdfRenderer? = null
                var page: PdfRenderer.Page? = null
                try {
                    renderer = PdfRenderer(fileDescriptor!!)
                    page = renderer.openPage(0)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    // Paints bitmap on white background before rendering
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    canvas.drawBitmap(bitmap, 0F, 0F, null)
                    // Renders PDF page into bitmap
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val builder = glide.load(bitmap)
                    applyOptions(builder)
                    builder.placeholder(R.drawable.image).into(imageView)
                    imageView.setTag(R.id.tag_file_type, Type.PREVIEW)
                    imageView.setTag(R.id.tag_path, path)
                    imageView.setTag(R.id.tag_uri, uri)
                    return
                } catch (_: Exception) {
                } finally {
                    page?.close()
                    renderer?.close()
                    fileDescriptor?.close()
                    completeDisplay()
                }
            }
            val builder = if (path != null) glide.load(path) else glide.load(uri)
            applyOptions(builder)
            if (Global.croppedPaths.contains(pathOrUri)) { // A cropped image needs to be reloaded not from cache
                builder.signature(ObjectKey(Global.croppedPaths[pathOrUri]!!))
            }
            builder.placeholder(R.drawable.image).listener(object : RequestListener<Drawable> {
                override fun onResourceReady(
                    resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean
                ): Boolean {
                    // Maybe is a video
                    val videoExts = arrayOf(".mp4", ".3gp", ".webm", ".mkv")
                    val imageType = if (videoExts.any { pathOrUri.endsWith(it, true) }) Type.PREVIEW else Type.CROPPABLE
                    imageView.setTag(R.id.tag_file_type, imageType)
                    imageView.setTag(R.id.tag_path, path)
                    imageView.setTag(R.id.tag_uri, uri)
                    completeDisplay()
                    return false
                }

                // Path or Uri are correct, but image can't be displayed (e.g. unsupported format)
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    // A local file with no preview
                    GlobalScope.launch(Dispatchers.Main) {
                        glide.load(generateIcon(media, pathOrUri, imageView)).into(imageView)
                    }
                    if (options and Image.GALLERY != 0) { // File icon inside MediaAdapter
                        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                        (imageView.layoutParams as RelativeLayout.LayoutParams).addRule(RelativeLayout.ABOVE, R.id.media_caption)
                    }
                    imageView.setTag(R.id.tag_file_type, Type.DOCUMENT)
                    imageView.setTag(R.id.tag_path, path)
                    imageView.setTag(R.id.tag_uri, uri)
                    completeDisplay()
                    return false
                }
            }).into(imageView)
        } else if (media.file != null) { // Path and Uri are both null
            // Maybe is an image online
            val filePath = media.file
            val builder = glide.load(filePath)
            applyOptions(builder)
            builder.placeholder(R.drawable.image).listener(object : RequestListener<Drawable> {
                override fun onResourceReady(
                    resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean
                ): Boolean {
                    imageView.setTag(R.id.tag_file_type, Type.CROPPABLE)
                    imageView.setTag(R.id.tag_path, filePath) // TODO: but CropImage doesn't handle a web URL
                    completeDisplay()
                    return false
                }

                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    imageView.setTag(R.id.tag_file_type, Type.PLACEHOLDER)
                    completeDisplay()
                    return false
                }
            }).into(imageView)
        } else { // Media file field is null
            glide.load(R.drawable.image).into(imageView)
            imageView.setTag(R.id.tag_file_type, Type.PLACEHOLDER)
            completeDisplay()
        }
    }

    /** Creates a bitmap of a file icon with the format overwritten. */
    private fun generateIcon(media: Media, pathOrUri: String, imageView: ImageView): Bitmap {
        var format = media.format
        if (format == null) // Removes any character that does not make find the file extension
            format = MimeTypeMap.getFileExtensionFromUrl(pathOrUri.replace("[^a-zA-Z0-9./]".toRegex(), "_"))
        val inflater = imageView.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val inflated = inflater.inflate(R.layout.media_file, null)
        val frameLayout = inflated.findViewById<RelativeLayout>(R.id.icona)
        frameLayout.findViewById<TextView>(R.id.media_text).text = format
        frameLayout.isDrawingCacheEnabled = true
        frameLayout.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        frameLayout.layout(0, 0, frameLayout.measuredWidth, frameLayout.measuredHeight)
        return frameLayout.drawingCache
    }

    /** Receives a Media and looks for the file on the device with different path combinations. */
    fun getPathFromMedia(media: Media, treeId: Int): String? {
        val file = media.file
        if (file != null && file.isNotBlank()) {
            // Original file path
            val path = file.replace("\\", "/")
            var test = File(path)
            if (test.isFile && test.canRead()) return path
            // File name in app tree storage
            val name = File(path).name
            test = File(Global.context.getExternalFilesDir(treeId.toString()), name)
            if (test.isFile && test.canRead()) return test.absolutePath
            // Media folders
            for (dir in Global.settings.getTree(treeId).dirs.filterNot { it == null }) {
                // Media folder + file path
                var dirPath = "$dir/$path"
                test = File(dirPath)
                if (test.isFile && test.canRead()) return dirPath
                // Media folder + file name
                dirPath = "$dir/$name"
                test = File(dirPath)
                if (test.isFile && test.canRead()) return dirPath
            }
        }
        return null
    }

    /** Receives a Media, looks for the file in the device with any tree-URIs and returns the URI. */
    fun getUriFromMedia(media: Media, treeId: Int): Uri? {
        val file = media.file
        if (file != null && file.isNotBlank()) {
            // OBJE.FILE is never a Uri, always a file path (Windows or Android) or a single filename
            val filename = File(file.replace("\\", "/")).name
            for (uri in Global.settings.getTree(treeId).uris.filterNot { it == null }) {
                val documentDir = DocumentFile.fromTreeUri(Global.context, Uri.parse(uri))
                val docFile = documentDir!!.findFile(filename)
                if (docFile != null && docFile.isFile) return docFile.uri
            }
        }
        return null
    }

    /** Opens the Storage Access Framework to save a document (PNG, PDF, GEDCOM, ZIP). */
    fun openSaf(treeId: Int, fileType: FileType, launcher: ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).setType(fileType.mimeType)
        // Replaces dangerous characters for the Android filesystem that are not replaced by Android itself
        var name = Global.settings.getTree(treeId).title.replace("[$']".toRegex(), "_")
        if (fileType == FileType.GEDCOM) {
            name += ".ged" // GEDCOM must specify the extension, because is generally not a known file type
            intent.putExtra(Intent.EXTRA_MIME_TYPES, gedcomMimeTypes)
        }
        intent.putExtra(Intent.EXTRA_TITLE, name)
        launcher.launch(intent)
    }

    fun deleteFilesAndDirs(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) {
            for (child in fileOrDirectory.listFiles()!!) deleteFilesAndDirs(child)
        }
        fileOrDirectory.delete()
    }
}
