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
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import app.familygem.BuildConfig
import app.familygem.CropImageActivity
import app.familygem.DetailActivity
import app.familygem.FileUri
import app.familygem.Global
import app.familygem.R
import app.familygem.U
import app.familygem.constant.Destination
import app.familygem.constant.Extra
import app.familygem.constant.FileType
import app.familygem.constant.Image
import app.familygem.constant.Type
import app.familygem.detail.MediaActivity
import app.familygem.main.MainActivity
import app.familygem.profile.ProfileActivity
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
import kotlinx.coroutines.withContext
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.Person
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.util.regex.Pattern

/** Functions to manage files and display images. */
object FileUtil {

    /** List of mime type to open and create GEDCOM files. */
    val gedcomMimeTypes = arrayOf("text/vnd.familysearch.gedcom", "application/octet-stream", "vnd.android.document/file", "text/plain")

    /** Extracts only the filename from a URI. */
    fun extractFilename(context: Context, uri: Uri?, fallback: String): String {
        return uri?.let { extractFilename(context, uri) } ?: fallback
    }

    fun extractFilename(context: Context, uri: Uri): String? {
        // file://
        if (uri.scheme?.equals("file", true) == true) {
            return uri.lastPathSegment
        }
        // Cursor (usually it's used this)
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                return cursor.getString(index)
            }
        }
        // DocumentFile
        val document = DocumentFile.fromSingleUri(context, uri)
        return document?.name
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
        return nextAvailableFileName(folder, fileName, null).first
    }

    /** @return Second false if [original] already exists in [folder], otherwise true */
    fun nextAvailableFileName(folder: File, fileName: String, original: File? = null): Pair<File, Boolean> {
        var newFileName = fileName
        var file = File(folder, newFileName)
        while (file.exists()) {
            if (original != null && file.length() == original.length()) return file to false // File with same name and size already exists
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
        return file to true
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
            val filename = extractFilename(context, uri, "media file")
            val twinFile = File(externalFilesDir, filename)
            val originFile = DocumentFile.fromSingleUri(context, uri)
            // If the file already exists proposes to reuse it and returns true
            if (twinFile.isFile && twinFile.length() == originFile?.length()) {
                media.file = filename // Sets the origin filename in the media
                AlertDialog.Builder(context).setTitle(filename).setMessage(R.string.file_exists_reuse)
                    .setNeutralButton(R.string.use_existing, null)
                    .setPositiveButton(R.string.make_copy) { _, _ ->
                        val newFile = twinFile.copyTo(nextAvailableFileName(externalFilesDir, filename))
                        media.file = newFile.name
                        TreeUtil.save(true)
                        when (context) { // Immediately refreshes UI to display new file name
                            is MainActivity -> context.frontFragment.showContent()
                            is ProfileActivity -> context.refresh()
                            is DetailActivity -> context.refresh()
                        }
                    }.show()
                return true
            } else {
                val newFile = nextAvailableFileName(externalFilesDir, filename)
                context.contentResolver.openInputStream(uri).use { inputStream ->
                    newFile.outputStream().use { inputStream?.copyTo(it) }
                }
                media.file = newFile.name
                newFile
            }
        } else null
        if (mediaFile != null) {
            // If is an image opens the cropping proposal dialog
            val mimeType = URLConnection.guessContentTypeFromName(mediaFile.name)
            if (mimeType != null && mimeType.startsWith("image/")) {
                Global.editedMedia = media
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
        person: Person, imageView: ImageView, options: Int = 0,
        gedcom: Gedcom = Global.gc, treeId: Int = Global.settings.openTree, show: Boolean = true
    ): Media? {
        val mediaList = MediaList(gedcom)
        person.accept(mediaList)
        // Looks for the "primary" media, or the first one
        val media = mediaList.list.firstOrNull { it.primary != null && it.primary == "Y" } ?: mediaList.list.firstOrNull()
        if (media != null) {
            if (show) showImage(media, imageView, options, null, null, treeId)
            imageView.visibility = View.VISIBLE
        } else {
            imageView.visibility = View.GONE
        }
        return media
    }

    /**
     * Shows a picture with Glide.
     * @param options Bitwise selection of [Image] constants
     * @param oldFileUri The same [FileUri] returned by this function, to speed up the process
     */
    @JvmOverloads
    fun showImage(
        media: Media, imageView: ImageView, options: Int = 0, progressWheel: ProgressBar? = null,
        oldFileUri: FileUri? = null, treeId: Int = Global.settings.openTree
    ): FileUri { // TODO Make it return Result<FileUri>
        fun applyOptions(builder: RequestBuilder<Drawable>) {
            if (options and Image.DARK != 0) {
                imageView.setColorFilter(ContextCompat.getColor(imageView.context, R.color.primary_grayed), PorterDuff.Mode.MULTIPLY)
            }
            if (options and Image.BLUR != 0) {
                builder.override(100, 100).apply(RequestOptions.bitmapTransform(BlurTransformation(4)))
            }
        }

        fun completeDisplay(fileType: Type) {
            imageView.setTag(R.id.tag_file_type, fileType)
            progressWheel?.visibility = View.GONE
            imageView.tag = R.id.tag_object // Used by DiagramFragment to check the image finish loading
        }

        imageView.setTag(R.id.tag_file_type, Type.NONE)
        progressWheel?.visibility = View.VISIBLE
        val fileUri = oldFileUri ?: FileUri(imageView.context, media, treeId)
        val glide = Glide.with(imageView)
        val coroutineScope = if (imageView.context is AppCompatActivity) (imageView.context as AppCompatActivity).lifecycleScope else GlobalScope
        if (fileUri.exists()) {
            previewPdf(imageView.context, fileUri).onSuccess { bitmap ->
                val builder = glide.load(bitmap)
                applyOptions(builder)
                builder.placeholder(R.drawable.image).into(imageView)
                completeDisplay(Type.PDF)
                return fileUri
            }
            val builder = glide.load(fileUri.file ?: fileUri.uri)
            applyOptions(builder)
            if (Global.croppedPaths.contains(fileUri.path)) { // A cropped image needs to be reloaded not from cache
                builder.signature(ObjectKey(Global.croppedPaths[fileUri.path]!!))
            }
            builder.placeholder(R.drawable.image).listener(object : RequestListener<Drawable> {
                override fun onResourceReady(
                    resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean
                ): Boolean {
                    // Maybe is a video
                    val videoExtensions = arrayOf("mp4", "3gp", "webm", "mkv", "mpg", "mov")
                    val fileType = if (videoExtensions.contains(fileUri.extension)) Type.VIDEO else Type.CROPPABLE
                    completeDisplay(fileType)
                    return false
                }

                // File or URI one is correct, but image can't be displayed (e.g. unsupported format)
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    // A local file with no preview
                    coroutineScope.launch(Dispatchers.Main) {
                        glide.load(generateIcon(imageView.context, fileUri)).placeholder(R.drawable.image).into(imageView)
                    }
                    completeDisplay(Type.DOCUMENT)
                    return false
                }
            }).into(imageView)
        } else if (!media.file.isNullOrBlank()) { // File and URI are both null
            // Maybe is an image online
            val builder = glide.load(media.file)
            applyOptions(builder)
            builder.placeholder(R.drawable.image).listener(object : RequestListener<Drawable> {
                override fun onResourceReady(
                    resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean
                ): Boolean {
                    completeDisplay(Type.WEB_IMAGE)
                    return false
                }

                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val connection = URL(media.file).openConnection() as HttpURLConnection
                            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                                withContext(Dispatchers.Main) {
                                    glide.load(generateIcon(imageView.context, fileUri)).placeholder(R.drawable.image).into(imageView)
                                    completeDisplay(Type.WEB_ANYTHING)
                                }
                            } else throw Exception()
                        } catch (exception: Exception) {
                            withContext(Dispatchers.Main) { completeDisplay(Type.PLACEHOLDER) }
                        }
                    }
                    return false
                }
            }).into(imageView)
        } else { // Media file field is null or blank
            glide.load(R.drawable.image).into(imageView)
            completeDisplay(Type.PLACEHOLDER)
        }
        return fileUri
    }

    /**
     * Previews the first page of a PDF file from file or URI.
     * @return Result with the PDF bitmap or exception
     */
    fun previewPdf(context: Context, fileUri: FileUri): Result<Bitmap> {
        return try {
            if (fileUri.extension == "pdf") {
                fileUri.fileDescriptor?.use descriptor@{ descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        renderer.openPage(0).use { page ->
                            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            // Paints bitmap on white background before rendering
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            canvas.drawBitmap(bitmap, 0F, 0F, null)
                            // Renders PDF page into bitmap
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            return Result.success(bitmap)
                        }
                    }
                }
            } else throw Exception("Not a PDF")
            Result.success(generateIcon(context, fileUri)) // Fallback bitmap
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    /** Creates a bitmap of a generic file icon with the format overwritten. */
    fun generateIcon(context: Context, fileUri: FileUri): Bitmap {
        val view = LayoutInflater.from(context).inflate(R.layout.media_file, null)
        val format = if (!fileUri.media.format.isNullOrBlank()) fileUri.media.format else fileUri.extension?.uppercase()
            ?: MimeTypeMap.getFileExtensionFromUrl(fileUri.media.file).uppercase()
        view.findViewById<TextView>(R.id.media_text).text = format
        view.measure(0, 0) // For View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val size = U.dpToPx(200F)
        view.layout(0, 0, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    /** Checks if a file points to an external storage folder owned by the app. */
    fun isOwnedDirectory(context: Context, file: File): Boolean {
        val path = file.absolutePath
        for (fileDir in context.getExternalFilesDirs(null).map { it.absolutePath }) if (path.startsWith(fileDir)) return true
        for (mediaDir in context.externalMediaDirs.map { it.absolutePath }) if (path.startsWith(mediaDir)) return true
        return false
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
