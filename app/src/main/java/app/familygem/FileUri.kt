package app.familygem

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import app.familygem.util.FileUtil
import org.folg.gedcom.model.Media
import java.io.File

/**
 * Manager of local file and URI of a Media.
 * @param fileOnly Searches only for file, not for URI
 */
class FileUri(val context: Context, val media: Media, val treeId: Int = Global.settings.openTree, fileOnly: Boolean = false) {

    private val mediaPath: String? = media.file?.replace('\\', '/')
    var file: File? = null
    var uri: Uri? = null
    var path: String? = null
    var name: String? = null
    var extension: String? = null // Always lowercase
    var treeDirFilename = false // File is found in external app storage using the last part of media link
    var relative = false // File was found in a subfolder
    val fileDescriptor: ParcelFileDescriptor?
        get() = if (file != null) ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        else if (uri != null) context.contentResolver.openFileDescriptor(uri!!, "r")
        else null

    init {
        if (!mediaPath.isNullOrBlank()) {
            file = getFileFromMedia()
            if (!fileOnly && file == null) uri = getUriFromMedia()
            path = file?.absolutePath ?: uri?.path
            name = file?.name ?: uri?.let { FileUtil.extractFilename(context, it) }
            extension = name?.let {
                val index = it.lastIndexOf('.')
                if (index >= 0) it.substring(index + 1).lowercase() else null
            }
        }
    }

    /** Looks for the file on the device with different path combinations. */
    private fun getFileFromMedia(): File? {
        // Original file path
        var test = File(mediaPath!!)
        if (test.isFile && test.canRead()) return test
        // File name in app tree storage
        val name = test.name
        test = File(context.getExternalFilesDir(treeId.toString()), name)
        if (test.isFile && test.canRead()) {
            if (name != mediaPath) treeDirFilename = true // Filename is different from the media link
            return test
        }
        // Media folders
        for (dir in Global.settings.getTree(treeId).dirs.filterNot { it == null }) {
            // Media folder + file path
            test = File("$dir/$mediaPath")
            if (test.isFile && test.canRead()) return test
            // Media folder + file name
            test = File("$dir/$name")
            if (test.isFile && test.canRead()) return test
        }
        return null
    }

    /** Looks for the file in the device with any tree-URIs and returns the URI. */
    private fun getUriFromMedia(): Uri? {
        // In Family Gem OBJE.FILE is never a URI: it's always a file path (Windows or Android) or a single filename
        val segments = mediaPath!!.split('/')
        for (uri in Global.settings.getTree(treeId).uris.filterNot { it == null }) {
            var documentDir = DocumentFile.fromTreeUri(context, Uri.parse(uri))
            // Relative path or filename only
            for (segment in segments) {
                val test = documentDir?.findFile(segment)
                if (test?.isFile == true) return test.uri
                else if (test?.isDirectory == true) {
                    relative = true
                    documentDir = test
                } else break
            }
            // Filename in last segment
            documentDir?.findFile(segments.last())?.let { if (it.isFile) return it.uri }
        }
        return null
    }

    /** One of [file] or [uri] is null, the other is valid. */
    fun exists(): Boolean = file != null || uri != null

    /** @return True on successfully renaming the file */
    fun rename(newName: String): Boolean {
        var renamed = false
        if (file != null) {
            renamed = file?.renameTo(File(file?.parentFile, newName)) == true
        } else if (uri != null) {
            // On Android 9 (emulator) renames the file but throws also FileNotFoundException
            DocumentsContract.renameDocument(context.contentResolver, uri!!, newName)?.let { renamed = true }
        }
        return renamed
    }

    /** @return True on successfully deleting the file */
    fun delete(): Boolean {
        var deleted = false
        if (file != null) {
            deleted = file?.delete() == true
        } else if (uri != null) {
            deleted = DocumentFile.fromSingleUri(context, uri!!)?.delete() == true
        }
        return deleted
    }
}
