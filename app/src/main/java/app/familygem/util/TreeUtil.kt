package app.familygem.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity.INPUT_METHOD_SERVICE
import app.familygem.BuildConfig
import app.familygem.Global
import app.familygem.Notifier
import app.familygem.ProgressView
import app.familygem.R
import app.familygem.Settings
import app.familygem.Settings.Tree
import app.familygem.U
import app.familygem.constant.Extra
import app.familygem.constant.Json
import app.familygem.share.CompareActivity
import app.familygem.util.Util.caseString
import app.familygem.util.Util.string
import app.familygem.visitor.MediaList
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.apache.commons.io.FileUtils
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.folg.gedcom.model.CharacterSet
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.GedcomVersion
import org.folg.gedcom.model.Generator
import org.folg.gedcom.model.Header
import org.folg.gedcom.model.Person
import org.folg.gedcom.parser.JsonParser
import org.folg.gedcom.parser.ModelParser
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.InputStream
import java.io.PrintWriter
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

fun Tree.getBasicData(): String {
    val builder = StringBuilder()
    builder.append("$persons ${caseString(if (persons == 1) R.string.person else R.string.persons)}")
    if (persons > 1 && generations > 0)
        builder.append(" - $generations ${caseString(if (generations == 1) R.string.generation else R.string.generations)}")
    if (media > 0) builder.append(" - $media ${caseString(R.string.media)}")
    return builder.toString()
}

/** Represents the importing of a tree with some missing media. */
class PartialSuccessException(message: String?) : Exception(message)

/** Functions about the general tree shared across many classes. */
object TreeUtil {

    /** Standard opening of a stored GEDCOM to edit it. */
    suspend fun openGedcom(treeId: Int, saveSettings: Boolean): Boolean {
        Global.gc = readJson(treeId)
        if (Global.gc == null) return false
        if (saveSettings) {
            Global.settings.openTree = treeId
            Global.settings.save()
        }
        Global.indi = Global.settings.currentTree.root
        Global.familyNum = 0 // Resets it in case was > 0
        Global.shouldSave = false // Resets it in case was true
        return true
    }

    /** Lightly opens a stored GEDCOM for different purposes. */
    suspend fun openGedcomTemporarily(treeId: Int, putInGlobal: Boolean): Gedcom? {
        val gedcom: Gedcom?
        if (Global.gc != null && Global.settings.openTree == treeId) gedcom = Global.gc
        else {
            gedcom = readJson(treeId)
            if (putInGlobal && gedcom != null) {
                Global.gc = gedcom // To be able to use for example F.oneImage()
                Global.settings.openTree = treeId // So Global.gc and Global.settings.openTree are consistent
            }
        }
        return gedcom
    }

    /** Reads the tree JSON and returns a GEDCOM or null. */
    suspend fun readJson(treeId: Int): Gedcom? {
        val gedcom: Gedcom?
        val file = File(Global.context.filesDir, "$treeId.json")
        val text = StringBuilder()
        try {
            val reader = BufferedReader(FileReader(file))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                text.append(line).append('\n')
            }
            reader.close()
            var json: String = text.toString()
            json = updateTreeLanguage(json)
            gedcom = JsonParser().fromJson(json)
            if (gedcom == null) throw Exception(string(R.string.no_useful_data))
            // This Notifier was introduced in version 0.9.1
            // TODO: Can be removed from here in the future because tree.birthdays will never more be null
            if (Global.settings.getTree(treeId).birthdays == null) {
                Notifier(Global.context, gedcom, treeId, Notifier.What.CREATE)
            }
        } catch (exception: Exception) {
            Util.toast(exception.localizedMessage)
            return null
        } catch (error: Error) {
            val message = if (error is OutOfMemoryError) string(R.string.not_memory_tree) else error.localizedMessage
            Util.toast(message)
            return null
        }
        return gedcom
    }

    /**
     * Replaces Italian with English in JSON tree data.
     * Introduced in Family Gem 0.8
     */
    private fun updateTreeLanguage(json: String): String {
        var engJson = json
        engJson = engJson.replace("\"zona\":", "\"zone\":")
        engJson = engJson.replace("\"famili\":", "\"kin\":")
        engJson = engJson.replace("\"passato\":", "\"passed\":")
        return engJson
    }

    /**
     * Returns true if [Global.gc] is not null. In case reloads it asynchronously.
     * @param refresh Optional function executed on main thread after reloading
     */
    fun isGlobalGedcomOk(refresh: Runnable?): Boolean {
        return if (Global.gc != null) true
        else {
            GlobalScope.launch(IO) {
                Global.gc = readJson(Global.settings.openTree)
                withContext(Main) { refresh?.run() }
            }
            false
        }
    }

    private var saveJob: Job? = null
    private var onlyOnce = true // To repeat saving function only once

    /**
     * Saves the currently opened tree.
     * @param refresh Will refresh also other activities
     * @param objects Record(s) to which update the change date
     */
    fun save(refresh: Boolean, vararg objects: Any?) = GlobalScope.launch {
        if (refresh) Global.edited = true
        ChangeUtil.updateChangeDate(*objects)
        // Avoids multiple concurrent saving
        if (saveJob?.isActive == true) {
            if (onlyOnce) {
                onlyOnce = false
                saveJob?.join()
            } else return@launch
        }
        // On the first save adds an extension to submitters
        if (Global.settings.currentTree.grade == 9) {
            Global.gc.submitters.forEach { it.putExtension("passed", true) }
            Global.settings.currentTree.grade = 10
            Global.settings.save()
        }
        if (Global.settings.autoSave) {
            saveJob = launch(IO) { saveJson(Global.gc, Global.settings.openTree) }
        } else {
            Global.shouldSave = true // The 'Save' button will be displayed
        }
        onlyOnce = true
    }

    /** Saves the GEDCOM tree as JSON. */
    suspend fun saveJson(gedcom: Gedcom, treeId: Int, makeBackup: Boolean = Global.settings.backup) {
        gedcom.header?.apply {
            if (generator?.value == "FAMILY_GEM") { // Only if header is by Family Gem
                dateTime = ChangeUtil.actualDateTime()
                // If necessary updates the version of Family Gem
                if (generator.version != BuildConfig.VERSION_NAME) generator.version = BuildConfig.VERSION_NAME
            }
        }
        try {
            FileUtils.writeStringToFile(File(Global.context.filesDir, "$treeId.json"), JsonParser().toJson(gedcom), "UTF-8")
        } catch (exception: Exception) {
            Util.toast(exception.localizedMessage)
        } catch (error: Error) {
            val message = if (error is OutOfMemoryError) string(R.string.not_memory_tree)
            else error.localizedMessage ?: string(R.string.something_wrong)
            Util.toast(message)
        }
        Notifier(Global.context, gedcom, treeId, Notifier.What.DEFAULT)
        if (makeBackup) Global.backupViewModel.backupDelayed(treeId)
    }

    // Temporary hack to call a suspend function from Java
    fun saveJsonAsync(gedcom: Gedcom, treeId: Int) = GlobalScope.launch(IO) { saveJson(gedcom, treeId) }

    /** Refreshes the data displayed below the tree title in TreesActivity list. */
    fun refreshData(gedcom: Gedcom, treeItem: Tree) {
        treeItem.persons = gedcom.people.size
        treeItem.generations = countGenerations(gedcom, U.getRootId(gedcom, treeItem))
        val mediaVisitor = MediaList(gedcom)
        gedcom.accept(mediaVisitor)
        treeItem.media = mediaVisitor.list.size
        Global.settings.save()
    }

    fun deleteTree(treeId: Int) {
        val treeFile = File(Global.context.filesDir, "$treeId.json")
        treeFile.delete()
        val mediaDir = Global.context.getExternalFilesDir(treeId.toString())
        if (mediaDir != null) FileUtil.deleteFilesAndDirs(mediaDir)
        if (Global.settings.openTree == treeId) Global.gc = null
        Notifier(Global.context, null, treeId, Notifier.What.DELETE)
        Global.settings.deleteTree(treeId)
    }

    /** Creates a GEDCOM header that represents this app. */
    fun createHeader(fileName: String): Header {
        val header = Header()
        val app = Generator()
        app.value = "FAMILY_GEM"
        app.name = Global.context.getString(R.string.app_name)
        app.version = BuildConfig.VERSION_NAME
        header.generator = app
        header.file = fileName
        val version = GedcomVersion()
        version.form = "LINEAGE-LINKED"
        version.version = "5.5.1"
        header.gedcomVersion = version
        val charSet = CharacterSet()
        charSet.value = "UTF-8"
        header.characterSet = charSet
        val locale = Locale(Locale.getDefault().language)
        //Resources.getSystem().getConfiguration().locale.getLanguage() // Returns the same 'en', 'it'...
        header.language = locale.getDisplayLanguage(Locale.ENGLISH) // The system language in English, not in the local language
        // A GEDCOM header has two date fields: TRANSMISSION_DATE can somewhat forcibly contain the last modification date
        header.dateTime = ChangeUtil.actualDateTime()
        return header
    }

    private var generationMin = 0
    private var generationMax = 0
    private const val GENERATION = "gen"

    /** @return Total number of generations of the tree starting from a root person */
    fun countGenerations(gedcom: Gedcom, rootId: String?): Int {
        if (gedcom.people.isEmpty()) return 0
        generationMin = 0
        generationMax = 0
        val root = gedcom.getPerson(rootId)
        ascendGenerations(root, gedcom, 0)
        descendGenerations(root, gedcom, 0)
        // Removes from persons the GENERATION extension to allow later counting
        gedcom.people.forEach {
            it.extensions.remove(GENERATION)
            if (it.extensions.isEmpty()) it.extensions = null
        }
        return 1 - generationMin + generationMax
    }

    /** Receives a person and finds the number of the earliest generation of ancestors. */
    private fun ascendGenerations(person: Person, gedcom: Gedcom, generation: Int) {
        if (generation < generationMin) generationMin = generation
        // Adds the extension to indicate that passed by this person
        person.putExtension(GENERATION, generation)
        // If person is a progenitor, goes to count the generations of descendants
        if (person.getParentFamilies(gedcom).isEmpty()) descendGenerations(person, gedcom, generation)
        person.getParentFamilies(gedcom).forEach { family ->
            // Intercepts also any siblings
            family.getChildren(gedcom).filter { it.getExtension(GENERATION) == null }.forEach { descendGenerations(it, gedcom, generation) }
            family.getHusbands(gedcom).filter { it.getExtension(GENERATION) == null }.forEach { ascendGenerations(it, gedcom, generation - 1) }
            family.getWives(gedcom).filter { it.getExtension(GENERATION) == null }.forEach { ascendGenerations(it, gedcom, generation - 1) }
        }
    }

    /** Receives a person and finds the number of the earliest generation of descendants. */
    private fun descendGenerations(person: Person, gedcom: Gedcom, generation: Int) {
        if (generation > generationMax) generationMax = generation
        person.putExtension(GENERATION, generation)
        person.getSpouseFamilies(gedcom).forEach { family ->
            // Identifies also other spouses
            family.getWives(gedcom).filter { it.getExtension(GENERATION) == null }.forEach { ascendGenerations(it, gedcom, generation) }
            family.getHusbands(gedcom).filter { it.getExtension(GENERATION) == null }.forEach { ascendGenerations(it, gedcom, generation) }
            family.getChildren(gedcom).filter { it.getExtension(GENERATION) == null }.forEach { descendGenerations(it, gedcom, generation + 1) }
        }
    }

    /** Imports a GEDCOM provided by an URI. */
    suspend fun importGedcom(context: Context, uri: Uri, onSuccess: () -> Unit, onFail: () -> Unit) {
        val onSuccessWithMessage = {
            Toast.makeText(context, R.string.tree_imported_ok, Toast.LENGTH_LONG).show()
            onSuccess()
        }
        try {
            // Reads the input
            val input = context.contentResolver.openInputStream(uri)
            val gedcom = ModelParser().parseGedcom(input)
            if (gedcom.header == null) {
                withContext(Main) {
                    Toast.makeText(context, R.string.invalid_gedcom, Toast.LENGTH_LONG).show()
                    onFail()
                }
                return
            }
            gedcom.createIndexes() // Necessary to calculate the generations
            // Saves the JSON file
            val id = Global.settings.max() + 1
            val printWriter = PrintWriter(context.filesDir.path + "/$id.json")
            val jsonParser = JsonParser()
            printWriter.print(jsonParser.toJson(gedcom))
            printWriter.close()
            // Tree name
            var name: String = FileUtil.extractFilename(context, uri, context.getString(R.string.tree) + " $id")
            if (name.lastIndexOf('.') > 0) // Removes the extension
                name = name.substring(0, name.lastIndexOf('.'))
            // Saves the settings
            val rootId = U.findRootId(gedcom)
            Global.settings.addTree(Tree(id, name, gedcom.people.size, countGenerations(gedcom, rootId), rootId, null, null, 0))
            Notifier(context, gedcom, id, Notifier.What.CREATE)
            withContext(Main) {
                // If necessary propose to show advanced tools
                if (gedcom.sources.isNotEmpty() && !Global.settings.expert) {
                    AlertDialog.Builder(context).setMessage(R.string.complex_tree_advanced_tools)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            Global.settings.expert = true
                            Global.settings.save()
                            onSuccessWithMessage()
                        }.setNegativeButton(android.R.string.cancel) { _, _ -> onSuccessWithMessage() }
                        .show()
                } else onSuccessWithMessage()
            }
        } catch (exception: Exception) {
            withContext(Main) {
                Toast.makeText(context, exception.localizedMessage, Toast.LENGTH_LONG).show()
                onFail()
            }
        } catch (error: Error) {
            withContext(Main) {
                val message = if (error is OutOfMemoryError) context.getString(R.string.not_memory_tree)
                else error.localizedMessage ?: context.getString(R.string.something_wrong)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                onFail()
            }
        }
    }

    /** Launches [downloadSharedTree] in a coroutine, managing the results.
     * @param onRefresh Action to display results
     * @param onReset Action to restore interface (usually hiding progress view)
     */
    fun launchDownloadSharedTree(
        scope: CoroutineScope, context: Context, dateId: String, progressView: ProgressView, onRefresh: () -> Unit, onReset: () -> Unit
    ) {
        scope.launch(IO) {
            val onRefreshEnriched = {
                // If the tree was downloaded with the install referrer from TreesActivity or NewTreeActivity
                if (Global.settings.referrer != null && Global.settings.referrer == dateId) {
                    Global.settings.referrer = null
                    Global.settings.save()
                }
                onRefresh()
            }
            downloadSharedTree(context, dateId, progressView).onFailure { exception ->
                when (exception) {
                    is ZipException -> {
                        withContext(Main) {
                            val error = if (Global.settings.expert) "\n${exception.localizedMessage}." else ""
                            AlertDialog.Builder(context)
                                .setMessage(context.getString(R.string.backup_invalid) + error + "\nDownload it again or unzip anyway?")
                                .setNeutralButton("Download again") { _, _ ->
                                    progressView.visibility = View.VISIBLE
                                    launchDownloadSharedTree(scope, context, dateId, progressView, onRefresh, onReset)
                                }.setPositiveButton("Unzip") { _, _ ->
                                    progressView.visibility = View.VISIBLE
                                    val file = File(context.externalCacheDir, "$dateId.zip")
                                    launchUnzipTree(scope, context, file, null, progressView, onRefreshEnriched, onReset)
                                }.setOnCancelListener { onRefresh() }.show()
                            onReset()
                        }
                    }
                    else -> {
                        Util.toast(exception.localizedMessage)
                        onRefresh()
                    }
                }
            }.onSuccess { launchUnzipTree(scope, context, it, null, progressView, onRefreshEnriched, onReset) }
        }
    }

    /**
     * Launches [unzipTree] in a coroutine.
     * @param onRefresh Displays the results (maybe reloading views)
     * @param onReset Restores interface (usually hiding progress view)
     */
    fun launchUnzipTree(
        scope: CoroutineScope, context: Context, zipFile: File?, zipUri: Uri?,
        progressView: ProgressView, onRefresh: () -> Unit, onReset: () -> Unit
    ) = scope.launch(IO) {
        unzipTree(context, zipFile, zipUri, progressView).onFailure { exception ->
            withContext(Main) {
                if (exception is PartialSuccessException) {
                    AlertDialog.Builder(context).setMessage(exception.message)
                        .setPositiveButton(android.R.string.ok) { _, _ -> onRefresh() }
                        .setOnCancelListener { onRefresh() }.show()
                    onReset()
                } else {
                    Util.toast(exception.localizedMessage)
                    onRefresh()
                }
            }
        }.onSuccess {
            Util.toast(R.string.tree_imported_ok)
            withContext(Main) { onRefresh() }
        }
    }

    /**
     * Connects to the server and downloads the ZIP file of the shared tree to import it.
     * @return Result with the downloaded ZIP file
     */
    private fun downloadSharedTree(context: Context, dateId: String, progressView: ProgressView): Result<File> {
        val client = FTPClient()
        return try {
            val credential = U.getCredential(Json.FTP) ?: throw Exception("Missing credentials.")
            client.connect(credential.getString(Json.HOST), credential.getInt(Json.PORT))
            client.login(credential.getString(Json.USER), credential.getString(Json.PASSWORD))
            val inputPath = credential.getString(Json.SHARED_PATH) + "/$dateId.zip"
            val fileSize = client.getSize(inputPath)?.toLongOrNull() ?: 0
            if (fileSize > 0) progressView.displayBar("Downloading", fileSize)
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            val inputStream = client.retrieveFileStream(inputPath)
            if (inputStream == null) { // Usually file not found
                val message = client.replyString.replace("550", "").replace("/www.familygem.app/condivisi/", "").trim()
                throw FileNotFoundException(message)
            }
            val outputZipFile = File(context.externalCacheDir, "$dateId.zip")
            inputStream.use {
                outputZipFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream) { progressView.progress = it }
                }
            }
            progressView.hideBar()
            if (!client.completePendingCommand()) throw Exception("Can not complete file transfer.")
            ZipFile(outputZipFile).close() // Checks ZIP file integrity and in case throws ZipException
            Result.success(outputZipFile)
        } catch (exception: Exception) {
            Result.failure(exception)
        } finally {
            if (client.isConnected) client.disconnect()
        }
    }

    /**
     * Unzips a ZIP tree file in the device storage.
     * Used equally by: Simpsons example, backup files and shared trees.
     * @return Result with the ID of the brand new tree (unused)
     */
    private suspend fun unzipTree(context: Context, zipFile: File?, zipUri: Uri?, progress: ProgressView? = null): Result<Int> =
        try {
            val treeId = Global.settings.max() + 1
            var mediaDir = context.getExternalFilesDir(treeId.toString())
            val sourceDir = context.applicationInfo.sourceDir
            if (!sourceDir.startsWith("/data/")) { // App installed not in internal memory (maybe moved to SD-card)
                val externalFilesDirs = context.getExternalFilesDirs(treeId.toString())
                if (externalFilesDirs.size > 1) {
                    mediaDir = externalFilesDirs[1]
                }
            }
            val inputStream = zipFile?.inputStream() ?: context.contentResolver.openInputStream(zipUri!!)
            val uri = zipUri ?: Uri.fromFile(zipFile)
            val fileSize = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }?.toLong() ?: 0
            val result = unzipBackupFile(inputStream!!, context, treeId, mediaDir!!, progress!!, fileSize)
            // PartialSuccessException is thrown after creating the tree entry
            if (result.isFailure && result.exceptionOrNull() !is PartialSuccessException) throw result.exceptionOrNull()!!
            // Reads cached settings and saves them in Global.settings
            val settingsFile = File(context.cacheDir, "settings.json")
            var json = FileUtils.readFileToString(settingsFile, "UTF-8")
            json = updateSettingsLanguage(json)
            val gson = Gson()
            val zipped = gson.fromJson(json, Settings.ZippedTree::class.java)
            val tree = Tree(treeId, zipped.title, zipped.persons, zipped.generations, zipped.root, zipped.settings, zipped.shares, zipped.grade)
            Global.settings.addTree(tree)
            settingsFile.delete()
            // Tree coming from sharing intended for comparison
            if (zipped.grade == 9 && compareTrees(context, tree, false)) {
                tree.grade = 20 // Marks it as derived
                tree.backup = false // No need to backup a derived tree
            }
            Global.settings.save()
            result.getOrThrow() // Maybe throws PartialSuccessException
            Result.success(treeId) // Complete success
        } catch (exception: Exception) {
            Result.failure(exception)
        }

    /** Decompress a ZIP file searching specifically for settings.json and tree.json files. */
    private suspend fun unzipBackupFile(
        inputStream: InputStream, context: Context, treeId: Int, mediaDir: File, progressView: ProgressView, fileSize: Long
    ): Result<Boolean> {
        var settingsOk = false;
        var treeOk = false
        var mediaCount = 0
        return try {
            ZipInputStream(inputStream).use { zipInputStream ->
                progressView.displayBar("Unzipping", fileSize)
                var copiedBytes = 0L
                generateSequence { zipInputStream.nextEntry }.filterNot { it.isDirectory }.forEach { zipEntry ->
                    yield()
                    val newFile = when (zipEntry.name) {
                        "settings.json" -> {
                            settingsOk = true
                            File(context.cacheDir, "settings.json")
                        }
                        "tree.json" -> {
                            treeOk = true
                            File(context.filesDir, "$treeId.json")
                        }
                        else -> { // It's a file from the 'media' folder
                            mediaCount++
                            File(mediaDir, zipEntry.name.replace("media/", ""))
                        }
                    }
                    newFile.outputStream().use { copiedBytes += zipInputStream.copyTo(it) }
                    progressView.progress = copiedBytes
                }
                progressView.hideBar()
                if (!settingsOk || !treeOk) throw Exception(context.getString(R.string.backup_invalid)) // Failure for missing fundamental files
            }
            Result.success(true)
        } catch (exception: Exception) {
            if (settingsOk && treeOk) { // Invalid ZIP file but with settings.json and tree.json files
                val errorMessage = if (Global.settings.expert) "Tree unzipped with error:\n${exception.localizedMessage}."
                else context.getString(R.string.backup_invalid)
                Result.failure(PartialSuccessException("$errorMessage\nOnly $mediaCount media files found."))
            } else Result.failure(exception)
        }
    }

    /**
     * Replaces Italian with English in the Json settings of ZIP backup.
     * @since Family Gem 0.8
     */
    private fun updateSettingsLanguage(originalJson: String): String {
        var json = originalJson
        json = json.replace("\"generazioni\":", "\"generations\":")
        json = json.replace("\"grado\":", "\"grade\":")
        json = json.replace("\"individui\":", "\"persons\":")
        json = json.replace("\"radice\":", "\"root\":")
        json = json.replace("\"titolo\":", "\"title\":")
        json = json.replace("\"condivisioni\":", "\"shares\":")
        json = json.replace("\"data\":", "\"dateId\":")
        return json
    }

    /**
     * Compares the sharing dates of existing trees.
     * Finding at least one original tree among the existing ones, returns true.
     * Optionally starts CompareActivity.
     */
    fun compareTrees(context: Context, tree2: Tree, startCompare: Boolean): Boolean {
        if (tree2.shares != null)
            for (tree in Global.settings.trees)
                if (tree.id != tree2.id && tree.shares != null && tree.grade != 20 && tree.grade != 30)
                    for (i in tree.shares.indices.reversed()) { // Shares from least to first
                        val share = tree.shares[i]
                        for (share2 in tree2.shares)
                            if (share.dateId != null && share.dateId == share2.dateId) {
                                if (startCompare) context.startActivity(
                                    Intent(context, CompareActivity::class.java)
                                        .putExtra(Extra.TREE_ID, tree.id)
                                        .putExtra(Extra.TREE_ID_2, tree2.id)
                                        .putExtra(Extra.DATE_ID, share.dateId)
                                )
                                return true
                            }
                    }
        return false
    }

    /** Displays a dialog to rename the tree. */
    fun renameTree(context: Context, treeId: Int, onSuccess: () -> Unit) {
        val renameView = LayoutInflater.from(context).inflate(R.layout.tree_title_dialog, null, false)
        val titleEdit = renameView.findViewById<EditText>(R.id.treeTitle_edit)
        titleEdit.setText(Global.settings.getTree(treeId).title)
        val dialog = AlertDialog.Builder(context)
            .setView(renameView).setTitle(R.string.title)
            .setPositiveButton(R.string.rename) { _, _ ->
                Global.settings.renameTree(treeId, titleEdit.text.toString().trim())
                onSuccess()
            }.setNeutralButton(R.string.cancel, null).create()
        titleEdit.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            false
        }
        dialog.show()
        renameView.postDelayed({
            titleEdit.requestFocus()
            titleEdit.setSelection(titleEdit.text.length)
            val inputMethodManager = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showSoftInput(titleEdit, InputMethodManager.SHOW_IMPLICIT)
        }, 300)
    }
}
