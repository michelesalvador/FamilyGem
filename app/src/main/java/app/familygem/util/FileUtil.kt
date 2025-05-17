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
import app.familygem.DetailActivity
import app.familygem.Global
import app.familygem.R
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
        person: Person, imageView: ImageView, options: Int = 0, gedcom: Gedcom? = null, treeId: Int? = null, show: Boolean = true
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

    /**
     * Shows a picture with Glide.
     * @param options Bitwise selection of [Image] constants
     */
    @JvmOverloads
    fun showImage(media: Media, imageView: ImageView, options: Int = 0, progressWheel: ProgressBar? = null, treeId: Int? = null) {
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
        if (options and Image.GALLERY != 0) { // Regular image inside MediaAdapter
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            (imageView.layoutParams as RelativeLayout.LayoutParams).removeRule(RelativeLayout.ABOVE)
        }
        val file = getPathFromMedia(media, treeId ?: Global.settings.openTree)?.let { File(it) }
        val uri = if (file == null) getUriFromMedia(media, treeId ?: Global.settings.openTree) else null
        val glide = Glide.with(imageView)
        if (file != null || uri != null) {
            previewPdf(imageView.context, file, uri).onSuccess { bitmap ->
                val builder = glide.load(bitmap)
                applyOptions(builder)
                builder.placeholder(R.drawable.image).into(imageView)
                completeDisplay(Type.PDF)
                return
            }
            val builder = if (file != null) glide.load(file) else glide.load(uri)
            applyOptions(builder)
            val pathOrUri = file?.path ?: uri!!.path!! // 'file' or 'uri' one of the 2 is valid, the other is null
            if (Global.croppedPaths.contains(pathOrUri)) { // A cropped image needs to be reloaded not from cache
                builder.signature(ObjectKey(Global.croppedPaths[pathOrUri]!!))
            }
            builder.placeholder(R.drawable.image).listener(object : RequestListener<Drawable> {
                override fun onResourceReady(
                    resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean
                ): Boolean {
                    // Maybe is a video
                    val videoExtensions = arrayOf(".mp4", ".3gp", ".webm", ".mkv", ".mpg", ".mov")
                    val fileType = if (videoExtensions.any { pathOrUri.endsWith(it, true) }) Type.VIDEO else Type.CROPPABLE
                    completeDisplay(fileType)
                    return false
                }

                // Path or Uri are correct, but image can't be displayed (e.g. unsupported format)
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    // A local file with no preview
                    GlobalScope.launch(Dispatchers.Main) {
                        glide.load(generateIcon(imageView.context, media, pathOrUri)).into(imageView)
                    }
                    if (options and Image.GALLERY != 0) { // File icon inside MediaAdapter
                        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                        (imageView.layoutParams as RelativeLayout.LayoutParams).addRule(RelativeLayout.ABOVE, R.id.media_caption)
                    }
                    completeDisplay(Type.DOCUMENT)
                    return false
                }
            }).into(imageView)
        } else if (media.file != null && media.file.isNotBlank()) { // File and URI are both null
            // Maybe is an image online
            val filePath = media.file
            val builder = glide.load(filePath)
            applyOptions(builder)
            builder.placeholder(R.drawable.image).listener(object : RequestListener<Drawable> {
                override fun onResourceReady(
                    resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean
                ): Boolean {
                    completeDisplay(Type.WEB)
                    return false
                }

                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    completeDisplay(Type.PLACEHOLDER)
                    return false
                }
            }).into(imageView)
        } else { // Media file field is null or blank
            glide.load(R.drawable.image).into(imageView)
            completeDisplay(Type.PLACEHOLDER)
        }
    }

    /**
     * Previews the first page of a PDF file from file or URI.
     * @return Result with the PDF bitmap or exception
     */
    fun previewPdf(context: Context, file: File?, uri: Uri?): Result<Bitmap> {
        return try {
            val pathOrUri = file?.path ?: uri!!.path!!
            val bitmap = if (pathOrUri.endsWith(".pdf", true)) {
                val fileDescriptor = if (file != null) ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                else context.contentResolver.openFileDescriptor(uri!!, "r")
                fileDescriptor?.use descriptor@{
                    PdfRenderer(fileDescriptor).use { renderer ->
                        renderer.openPage(0).use { page ->
                            val pdfBitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            // Paints bitmap on white background before rendering
                            val canvas = Canvas(pdfBitmap)
                            canvas.drawColor(Color.WHITE)
                            canvas.drawBitmap(pdfBitmap, 0F, 0F, null)
                            // Renders PDF page into bitmap
                            page.render(pdfBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            return@descriptor pdfBitmap
                        }
                    }
                }
                generateIcon(context, null, pathOrUri) // Fallback bitmap
            } else throw Exception("Not a PDF")
            Result.success(bitmap)
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    /** Creates a bitmap of a file icon with the format overwritten. */
    fun generateIcon(context: Context, media: Media?, pathOrUri: String): Bitmap {
        var format = media?.format
        if (format == null) // Removes any character that does not make find the file extension
            format = MimeTypeMap.getFileExtensionFromUrl(pathOrUri.replace("[^a-zA-Z0-9./]".toRegex(), "_"))
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
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
            // In Family Gem OBJE.FILE is never a Uri: it's always a file path (Windows or Android) or a single filename
            val segments = file.replace('\\', '/').split('/')
            for (uri in Global.settings.getTree(treeId).uris.filterNot { it == null }) {
                var documentDir = DocumentFile.fromTreeUri(Global.context, Uri.parse(uri))
                for (segment in segments) {
                    val test = documentDir?.findFile(segment)
                    if (test?.isDirectory == true) documentDir = test
                    else if (test?.isFile == true) return test.uri
                    else break
                }
            }
        }
        return null
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
