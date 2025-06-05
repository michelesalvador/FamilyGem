package app.familygem

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.familygem.Settings.ZippedTree
import app.familygem.util.ChangeUtil.actualDateTime
import app.familygem.util.FileUtil
import app.familygem.util.TreeUtil
import app.familygem.visitor.MediaList
import kotlinx.coroutines.yield
import org.apache.commons.io.FileUtils
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Media
import org.folg.gedcom.visitors.GedcomWriter
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Utility class to export a tree as GEDCOM or ZIP backup. */
class Exporter(private val context: Context, private val progressView: ProgressView? = null) {

    private var treeId = 0
    private var gedcom: Gedcom? = null
    private var targetUri: Uri? = null
    var errorMessage: String? = null // Message of possible error
    var successMessage: String? = null // Message of the obtained result

    private enum class Type { KEEP_NAME, JSON_TREE, MEDIA } // Types of collected DocumentFiles

    /** Opens the JSON tree and returns true if successful. */
    suspend fun openTree(treeId: Int): Boolean {
        this.treeId = treeId
        gedcom = TreeUtil.openGedcomTemporarily(treeId, true)
        return if (gedcom == null) {
            error(R.string.no_useful_data)
        } else true
    }

    /** Opens the JSON tree and returns true if successful, following more strict rules to obtain a valid tree. */
    suspend fun openTreeStrict(treeId: Int): Boolean {
        this.treeId = treeId
        val treeFile = File(context.filesDir, "$treeId.json")
        if (!treeFile.exists()) return error("Tree file doesn't exist")
        if (treeFile.length() == 0L) return error("Tree file is empty")
        gedcom = TreeUtil.openGedcomTemporarily(treeId, false)
        if (gedcom == null) return error(R.string.no_useful_data)
        if (gedcom!!.header == null) return error("GEDCOM without header")
        return true
    }

    /** Writes the GEDCOM file in the URI. */
    suspend fun exportGedcom(targetUri: Uri): Boolean {
        this.targetUri = targetUri
        updateHeader(FileUtil.extractFilename(context, targetUri, "tree.ged"))
        optimizeGedcom()
        val writer = GedcomWriter()
        val gedcomFile = File(context.cacheDir, "temp.ged")
        try {
            writer.write(gedcom, gedcomFile)
            val out = context.contentResolver.openOutputStream(targetUri)
            FileUtils.copyFile(gedcomFile, out)
            out!!.flush()
            out.close()
        } catch (e: Exception) {
            return error(e.localizedMessage)
        }
        makeFileVisible(targetUri)
        Global.gc = TreeUtil.readJson(treeId) // Resets the changes
        return success(R.string.gedcom_exported_ok)
    }

    /** Writes the GEDCOM with all the media in a ZIP file. */
    suspend fun exportGedcomToZip(targetUri: Uri): Boolean {
        this.targetUri = targetUri
        // Creates the GEDCOM file
        val title = Global.settings.getTree(treeId).title
        val gedcomFileName = title.replace("[\\\\/:*?\"<>|'$]".toRegex(), "_") + ".ged"
        updateHeader(gedcomFileName)
        optimizeGedcom()
        val writer = GedcomWriter()
        val gedcomFile = File(context.cacheDir, gedcomFileName)
        try {
            writer.write(gedcom, gedcomFile)
        } catch (e: Exception) {
            return error(e.localizedMessage)
        }
        val gedcomDocument = DocumentFile.fromFile(gedcomFile)
        // Adds the GEDCOM to the media file collection
        val collection = collectMedia()
        collection[gedcomDocument] = Type.KEEP_NAME
        if (!createZipFile(collection)) return false
        makeFileVisible(targetUri)
        Global.gc = TreeUtil.readJson(treeId)
        return success(R.string.zip_exported_ok)
    }

    /** Creates a zipped file with the tree, settings and media. */
    suspend fun exportZipBackup(root: String?, grade: Int, targetUri: Uri): Boolean {
        var root = root
        var grade = grade
        this.targetUri = targetUri
        // Media
        val files = collectMedia()
        // Tree JSON
        val fileTree = File(context.filesDir, "$treeId.json")
        files[DocumentFile.fromFile(fileTree)] = Type.JSON_TREE
        // Settings JSON
        // title, root and grade can be modified by SharingActivity
        val tree = Global.settings.getTree(treeId)
        if (root == null) root = tree.root
        if (grade < 0) grade = tree.grade
        val settings = ZippedTree(tree.title, tree.persons, tree.generations, root, tree.settings, tree.shares, grade)
        val settingsFile = settings.save()
        files[DocumentFile.fromFile(settingsFile)] = Type.KEEP_NAME
        if (!createZipFile(files)) return false
        makeFileVisible(targetUri)
        return success(R.string.zip_exported_ok)
    }

    /** @return The number of media files to attach into the ZIP file */
    fun countMediaFilesToAttach(): Int {
        val mediaList = MediaList(gedcom!!)
        gedcom!!.accept(mediaList)
        return mediaList.list.count { FileUri(context, it, treeId).exists() }
    }

    /** @return A DocumentFile map of all the media that can be found */
    private fun collectMedia(): MutableMap<DocumentFile?, Type> {
        val collection: MutableMap<DocumentFile?, Type> = HashMap()
        if (gedcom == null) { // gedcom is null if openTree() has failed
            // Collects media only from external files folder of the tree
            val mediaFolder = context.getExternalFilesDir(treeId.toString()) // Creates the folder if it doesn't exist
            mediaFolder!!.listFiles()?.forEach { collection[DocumentFile.fromFile(it)] = Type.MEDIA }
        } else { // Collects actually used files from the whole device
            val mediaList = MediaList(gedcom!!)
            gedcom!!.accept(mediaList)
            /* It happens that different Media point to the same file.
               And it could also happen that different paths end up with the same filenames,
               for example 'pathA/img.jpg' 'pathB/img.jpg'
               It's necessary to avoid that files with the same name end up in the ZIP.
               This loop creates a list of paths with unique filenames. */
            val selectedMedia: MutableList<Media> = mutableListOf()
            val filenames: MutableList<String> = mutableListOf() // Control filenames
            mediaList.list.forEach { media ->
                if (!media.file.isNullOrBlank()) {
                    val filename = media.file.replace('\\', '/').split('/').last()
                    if (!filenames.contains(filename)) selectedMedia.add(media)
                    filenames.add(filename)
                }
            }
            selectedMedia.forEach { media ->
                val fileUri = FileUri(context, media, treeId)
                fileUri.file?.let { collection[DocumentFile.fromFile(it)] = Type.MEDIA }
                fileUri.uri?.let { collection[DocumentFile.fromSingleUri(context, it)] = Type.MEDIA }
            }
        }
        return collection
    }

    private fun updateHeader(gedcomFilename: String) {
        val header = gedcom!!.header
        if (header == null) gedcom!!.header = TreeUtil.createHeader(gedcomFilename) else {
            header.file = gedcomFilename
            header.dateTime = actualDateTime()
        }
    }

    /** Enhances GEDCOM for export. */
    private fun optimizeGedcom() {
        // Value of names from given and surname
        for (person in gedcom!!.people) {
            for (n in person.names) {
                if (n.value == null && (n.prefix != null || n.given != null || n.surname != null || n.suffix != null)) {
                    val builder = StringBuilder()
                    if (n.prefix != null) builder.append(n.prefix)
                    if (n.given != null) builder.append(" ").append(n.given)
                    if (n.surname != null) builder.append(" /").append(n.surname).append("/")
                    if (n.suffix != null) builder.append(" ").append(n.suffix)
                    n.value = builder.toString().trim()
                }
            }
        }
    }

    /**
     * Receives a list of DocumentFiles and put them in a ZIP file written to the [targetUri].
     * @return Error message or true if all is well
     */
    private suspend fun createZipFile(files: Map<DocumentFile?, Type>): Boolean {
        try {
            val sortedFiles = files.toList().sortedBy { it.second } // Sorts files by type (any 'settings.json' goes first)
            val zipOutputStream = ZipOutputStream(context.contentResolver.openOutputStream(targetUri!!))
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            progressView?.displayBar("Zipping files", files.size.toLong())
            var count = 0L
            for ((file, type) in sortedFiles) {
                yield()
                val inputStream = context.contentResolver.openInputStream(file!!.uri)
                val filename = when (type) {
                    Type.JSON_TREE -> "tree.json"
                    Type.MEDIA -> "media/${file.name}"
                    else -> file.name // Files that are not renamed ('settings.json', 'family.ged')
                }
                zipOutputStream.putNextEntry(ZipEntry(filename))
                var read: Int
                while (inputStream!!.read(buffer).also { read = it } != -1) {
                    zipOutputStream.write(buffer, 0, read)
                }
                zipOutputStream.closeEntry()
                inputStream.close()
                progressView?.progress = count++
            }
            zipOutputStream.close()
        } catch (e: IOException) {
            return error(e.localizedMessage)
        } finally {
            progressView?.hideBar()
        }
        return true
    }

    /** Makes the just created file visible from Windows file explorer. */
    private fun makeFileVisible(uri: Uri) {
        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
    }

    private fun success(message: Int): Boolean {
        successMessage = context.getString(message)
        return true
    }

    fun error(error: Int): Boolean {
        return error(context.getString(error))
    }

    fun error(error: String?): Boolean {
        errorMessage = error
        return false
    }
}
