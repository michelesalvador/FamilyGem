package app.familygem.util

import android.app.Activity
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
import android.view.LayoutInflater
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import app.familygem.Global
import app.familygem.R
import app.familygem.constant.Image
import app.familygem.constant.Type
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


/**
 * Methods for displaying images.
 */
object FileUtil {

    /**
     * Receives a Person and returns the primary media from which to get the image, or a random media, or null.
     * @param imageView Where the image will appear
     * @param options Bitwise selection of [Image] constants
     * @param show Calls showImage() or not
     */
    @JvmOverloads
    fun selectMainImage(person: Person, imageView: ImageView, options: Int = 0, gedcom: Gedcom? = null, treeId: Int = 0, show: Boolean = true): Media? {
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
     */
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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
            }
            val builder = if (path != null) glide.load(path) else glide.load(uri)
            applyOptions(builder)
            if (Global.croppedPaths.contains(pathOrUri)) { // A cropped image needs to be reloaded not from cache
                builder.signature(ObjectKey(Global.croppedPaths[pathOrUri]!!))
            }
            builder.placeholder(R.drawable.image).listener(object : RequestListener<Drawable> {
                override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                    // Maybe is a video
                    val videoExts = arrayOf(".mp4", ".3gp", ".webm", ".mkv")
                    val imageType = if (videoExts.any { pathOrUri.endsWith(it, true) }) Type.PREVIEW else Type.CROPPABLE
                    imageView.setTag(R.id.tag_file_type, imageType)
                    imageView.setTag(R.id.tag_path, path)
                    imageView.setTag(R.id.tag_uri, uri)
                    completeDisplay()
                    // On MediaActivity reloads the options menu to show the Crop command
                    if (imageView.id == R.id.image_picture) {
                        if (imageView.context is Activity) // In KitKat is instance of TintContextWrapper
                            (imageView.context as Activity).invalidateOptionsMenu()
                    }
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
            var filePath = media.file
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) filePath = filePath.replace("https://", "http://")
            val builder = glide.load(filePath)
            applyOptions(builder)
            builder.placeholder(R.drawable.image).listener(object : RequestListener<Drawable> {
                override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
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

    /**
     * Create a bitmap of a file icon with the format overwritten.
     */
    private fun generateIcon(media: Media, pathOrUri: String, imageView: ImageView): Bitmap {
        var format = media.format
        if (format == null) // Removes any character that does not make find the file extension
            format = MimeTypeMap.getFileExtensionFromUrl(pathOrUri.replace("[^a-zA-Z0-9./]".toRegex(), "_"))
        val inflater = imageView.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val inflated = inflater.inflate(R.layout.media_file, null)
        val frameLayout = inflated.findViewById<RelativeLayout>(R.id.icona)
        frameLayout.findViewById<TextView>(R.id.media_text).text = format
        frameLayout.isDrawingCacheEnabled = true
        frameLayout.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        frameLayout.layout(0, 0, frameLayout.measuredWidth, frameLayout.measuredHeight)
        return frameLayout.drawingCache
    }

    /**
     * Receives a Media and looks for the file on the device with different path combinations.
     */
    fun getPathFromMedia(media: Media, treeId: Int): String? {
        val file = media.file
        if (file != null && file.isNotEmpty()) {
            val name = file.replace("\\", "/")
            // File path as written in the Media
            if (File(name).canRead()) return name
            for (dir in Global.settings.getTree(treeId).dirs.filterNot { it == null }) {
                // Media folder + File path
                var path = "$dir/$name"
                var test = File(path)
                if (test.isFile && test.canRead()) return path
                // Media folder + filename
                path = "$dir/${File(name).name}"
                test = File(path)
                if (test.isFile && test.canRead()) return path
            }
        }
        return null
    }

    /**
     * Receives a Media, looks for the file in the device with any tree-URIs and returns the URI.
     */
    fun getUriFromMedia(media: Media, treeId: Int): Uri? {
        val file = media.file
        if (file != null && file.isNotEmpty()) {
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

    /**
     * Opens the Storage Access Framework to save a document (PDF, GEDCOM, ZIP).
     */
    fun openSaf(treeId: Int, mimeType: String, extension: String, launcher: ActivityResultLauncher<Intent>) {
        // Replaces dangerous characters for the Android filesystem that are not replaced by Android itself
        val name = Global.settings.getTree(treeId).title.replace("[$']".toRegex(), "_")
        // A GEDCOM must specify the extension, other file types put it according to the mime type
        val extension = if (extension == "ged") ".ged" else ""
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mimeType)
                .putExtra(Intent.EXTRA_TITLE, name + extension)
        launcher.launch(intent)
    }

    fun deleteFilesAndDirs(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) {
            for (child in fileOrDirectory.listFiles()!!) deleteFilesAndDirs(child)
        }
        fileOrDirectory.delete()
    }
}
