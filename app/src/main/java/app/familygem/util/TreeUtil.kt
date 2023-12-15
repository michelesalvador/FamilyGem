package app.familygem.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import app.familygem.BuildConfig
import app.familygem.F
import app.familygem.Global
import app.familygem.Notifier
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
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import java.io.PrintWriter
import java.util.Locale
import java.util.zip.ZipInputStream

fun Tree.getBasicData(): String {
    val builder = StringBuilder()
    builder.append("$persons ${caseString(if (persons == 1) R.string.person else R.string.persons)}")
    if (persons > 1 && generations > 0)
        builder.append(" - $generations ${caseString(if (generations == 1) R.string.generation else R.string.generations)}")
    if (media > 0) builder.append(" - $media ${caseString(R.string.media)}")
    return builder.toString()
}

/**
 * Functions about the general tree shared across many classes.
 */
object TreeUtils {
    /**
     * Standard opening of a stored GEDCOM to edit it.
     */
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

    // Temporary hack for Java
    fun openGedcomAsync(treeId: Int, saveSettings: Boolean) = GlobalScope.launch(IO) { openGedcom(treeId, saveSettings) }

    /**
     *  Lightly opens a stored GEDCOM for different purposes.
     */
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

    /**
     * Reads the tree JSON and returns a GEDCOM or null.
     */
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
     * Checks if [Global.gc] is not null and in case reloads it asynchronously.
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

    /**
     * Saves the currently opened tree.
     *
     * @param refresh Will refresh also other activities
     * @param objects Record(s) to which update the change date
     */
    fun save(refresh: Boolean, vararg objects: Any) {
        if (refresh) Global.edited = true
        objects.forEach { ChangeUtil.updateChangeDate(it) }
        // On the first save adds an extension to submitters
        if (Global.settings.currentTree.grade == 9) {
            Global.gc.submitters.forEach { it.putExtension("passed", true) }
            Global.settings.currentTree.grade = 10
            Global.settings.save()
        }
        if (Global.settings.autoSave) {
            GlobalScope.launch(IO) { saveJson(Global.gc, Global.settings.openTree) }
        } else {
            Global.shouldSave = true // The 'Save' button will be displayed
        }
    }

    /**
     * Saves the GEDCOM tree as JSON.
     */
    suspend fun saveJson(gedcom: Gedcom, treeId: Int) {
        val h = gedcom.header
        // Only if header is by Family Gem
        if (h != null && h.generator != null && h.generator.value != null && h.generator.value == "FAMILY_GEM") {
            // Updates date and time
            h.dateTime = ChangeUtil.actualDateTime()
            // If necessary updates the version of Family Gem
            if (h.generator.version != null && h.generator.version != BuildConfig.VERSION_NAME || h.generator.version == null)
                h.generator.version = BuildConfig.VERSION_NAME
        }
        try {
            FileUtils.writeStringToFile(File(Global.context.filesDir, "$treeId.json"), JsonParser().toJson(gedcom), "UTF-8")
        } catch (e: IOException) {
            Util.toast(e.localizedMessage)
        }
        Notifier(Global.context, gedcom, treeId, Notifier.What.DEFAULT)
    }

    // Temporary hack to call a suspend function from Java
    fun saveJsonAsync(gedcom: Gedcom, treeId: Int) = GlobalScope.launch(IO) { saveJson(gedcom, treeId) }

    /**
     * Refreshes the data displayed below the tree title in TreesActivity list.
     */
    fun refreshData(gedcom: Gedcom, treeItem: Tree) {
        treeItem.persons = gedcom.people.size
        treeItem.generations = countGenerations(gedcom, U.getRootId(gedcom, treeItem))
        val mediaVisitor = MediaList(gedcom, 0)
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

    /**
     * Creates a GEDCOM header that represents this app.
     */
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

    /**
     * @return Total number of generations of the tree starting from a root person
     */
    private fun countGenerations(gedcom: Gedcom, root: String?): Int {
        if (gedcom.people.isEmpty()) return 0
        generationMin = 0
        generationMax = 0
        ascendGenerations(gedcom.getPerson(root), gedcom, 0)
        descendGenerations(gedcom.getPerson(root), gedcom, 0)
        // Removes from persons the GENERATION extension to allow later counting
        for (person in gedcom.people) {
            person.extensions.remove(GENERATION)
            if (person.extensions.isEmpty()) person.extensions = null
        }
        return 1 - generationMin + generationMax
    }

    // Receives a person and finds the number of the earliest generation of ancestors
    private fun ascendGenerations(person: Person, gedcom: Gedcom, generation: Int) {
        if (generation < generationMin) generationMin = generation
        // Adds the extension to indicate that passed by this person
        person.putExtension(GENERATION, generation)
        // If person is a progenitor, goes to count the generations of descendants
        if (person.getParentFamilies(gedcom).isEmpty()) descendGenerations(person, gedcom, generation)
        for (family in person.getParentFamilies(gedcom)) {
            for (sibling in family.getChildren(gedcom)) // Intercepts also any siblings
                if (sibling.getExtension(GENERATION) == null) descendGenerations(sibling, gedcom, generation)
            for (father in family.getHusbands(gedcom))
                if (father.getExtension(GENERATION) == null) ascendGenerations(father, gedcom, generation - 1)
            for (mother in family.getWives(gedcom))
                if (mother.getExtension(GENERATION) == null) ascendGenerations(mother, gedcom, generation - 1)
        }
    }

    // Receives a person and finds the number of the earliest generation of descendants
    private fun descendGenerations(person: Person, gedcom: Gedcom, generation: Int) {
        if (generation > generationMax) generationMax = generation
        person.putExtension(GENERATION, generation)
        for (family in person.getSpouseFamilies(gedcom)) {
            // Identifies also other spouses
            for (wife in family.getWives(gedcom))
                if (wife.getExtension(GENERATION) == null) ascendGenerations(wife, gedcom, generation)
            for (husband in family.getHusbands(gedcom))
                if (husband.getExtension(GENERATION) == null) ascendGenerations(husband, gedcom, generation)
            for (child in family.getChildren(gedcom))
                if (child.getExtension(GENERATION) == null) descendGenerations(child, gedcom, generation + 1)
        }
    }

    /**
     * Imports a GEDCOM provided by an URI.
     */
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
            val newNumber = Global.settings.max() + 1
            val printWriter = PrintWriter(context.filesDir.path + "/$newNumber.json")
            val jsonParser = JsonParser()
            printWriter.print(jsonParser.toJson(gedcom))
            printWriter.close()
            // Tree name and folder path
            val path = F.getFilePathFromUri(uri)
            var treeName: String
            var folderPath: String? = null
            if (path != null && path.lastIndexOf('/') > 0) { // It's a full path to the gedcom file
                val fileGedcom = File(path)
                folderPath = fileGedcom.parent
                treeName = fileGedcom.name
            } else if (path != null) { // It's just a file name, e.g. 'family.ged'
                treeName = path
            } else // Null path
                treeName = context.getString(R.string.tree) + " $newNumber"
            if (treeName.lastIndexOf('.') > 0) // Removes the extension
                treeName = treeName.substring(0, treeName.lastIndexOf('.'))
            // Saves the settings
            val rootId = U.findRootId(gedcom)
            Global.settings.addTree(Tree(newNumber, treeName, folderPath,
                    gedcom.people.size, countGenerations(gedcom, rootId), rootId, null, null, 0))
            Notifier(context, gedcom, newNumber, Notifier.What.CREATE)
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
                else if (error.localizedMessage != null) error.localizedMessage
                else context.getString(R.string.something_wrong)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                onFail()
            }
        }
    }

    /**
     * Connects to the server and downloads the ZIP file to import it.
     */
    suspend fun downloadSharedTree(context: Context, dateId: String, onSuccess: () -> Unit, onFail: () -> Unit) {
        val credential = U.getCredential(Json.FTP)
        if (credential != null) {
            try {
                val client = FTPClient() // TODO: refactor to use Retrofit
                client.connect(credential.getString(Json.HOST), credential.getInt(Json.PORT))
                client.enterLocalPassiveMode()
                client.login(credential.getString(Json.USER), credential.getString(Json.PASSWORD))
                val zipPath = context.externalCacheDir?.path + "/$dateId.zip"
                val fos = FileOutputStream(zipPath)
                val path = credential.getString(Json.SHARED_PATH) + "/" + dateId + ".zip"
                val input = client.retrieveFileStream(path)
                if (input != null) {
                    val data = ByteArray(1024)
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        fos.write(data, 0, count)
                    }
                    fos.close()
                    if (client.completePendingCommand()) {
                        unZipTree(context, zipPath, null, {
                            // If the tree was downloaded with the install referrer from TreesActivity or NewTreeActivity
                            if (Global.settings.referrer != null && Global.settings.referrer == dateId) {
                                Global.settings.referrer = null
                                Global.settings.save()
                            }
                            onSuccess()
                        }, onFail)
                    } else // Failed decompression of downloaded ZIP (eg. corrupted file)
                        downloadFailed(context.getString(R.string.backup_invalid), onFail)
                } else // Did not find the file on the server
                    downloadFailed(context.getString(R.string.something_wrong), onFail)
                client.logout()
                client.disconnect()
            } catch (e: java.lang.Exception) {
                downloadFailed(e.localizedMessage, onFail)
            }
        } else // Credentials are null
            downloadFailed(context.getString(R.string.something_wrong), onFail)
    }

    /**
     * Negative conclusion of the above method.
     */
    private suspend fun downloadFailed(message: String?, onFail: () -> Unit?) {
        Util.toast(message)
        withContext(Main) { onFail() }
    }

    /**
     * Unzips a ZIP tree file in the device storage.
     * Used equally by: Simpsons example, backup files and shared trees.
     */
    suspend fun unZipTree(context: Context, zipPath: String?, zipUri: Uri?, onSuccess: () -> Unit, onFail: () -> Unit) {
        val treeNumber = Global.settings.max() + 1
        var mediaDir = context.getExternalFilesDir(treeNumber.toString())
        val sourceDir = context.applicationInfo.sourceDir
        if (!sourceDir.startsWith("/data/")) { // App installed not in internal memory (hopefully moved to SD-card)
            val externalFilesDirs = context.getExternalFilesDirs(treeNumber.toString())
            if (externalFilesDirs.size > 1) {
                mediaDir = externalFilesDirs[1]
            }
        }
        try {
            val inputStream = if (zipPath != null) FileInputStream(zipPath)
            else context.contentResolver.openInputStream(zipUri!!)
            var len: Int
            val buffer = ByteArray(1024)
            ZipInputStream(inputStream).use { zipInputStream ->
                generateSequence { zipInputStream.nextEntry }.filterNot { it.isDirectory }.forEach { zipEntry ->
                    val newFile = when (zipEntry.name) {
                        "tree.json" -> File(context.filesDir, "$treeNumber.json")
                        "settings.json" -> File(context.cacheDir, "settings.json")
                        // It's a file from the 'media' folder
                        else -> File(mediaDir, zipEntry.name.replace("media/", ""))
                    }
                    val outputStream = FileOutputStream(newFile)
                    while (zipInputStream.read(buffer).also { len = it } > 0) {
                        outputStream.write(buffer, 0, len)
                    }
                    outputStream.close()
                }
            }
            // Reads cached settings and saves them in Global.settings
            val settingsFile = File(context.cacheDir, "settings.json")
            var json = FileUtils.readFileToString(settingsFile, "UTF-8")
            json = updateSettingsLanguage(json)
            val gson = Gson()
            val zipped = gson.fromJson(json, Settings.ZippedTree::class.java)
            val tree = Tree(treeNumber, zipped.title, mediaDir!!.path,
                    zipped.persons, zipped.generations, zipped.root, zipped.settings, zipped.shares, zipped.grade)
            Global.settings.addTree(tree)
            settingsFile.delete()
            // Tree coming from sharing intended for comparison
            if (zipped.grade == 9 && compareTrees(context, tree, false)) {
                tree.grade = 20 // Marks it as derived
            }
            Global.settings.save()
            Util.toast(R.string.tree_imported_ok)
            withContext(Main) { onSuccess() }
            return
        } catch (e: Exception) {
            Util.toast(e.localizedMessage)
        }
        withContext(Main) { onFail() }
    }

    /**
     * Replaces Italian with English in the Json settings of ZIP backup.
     * Added in Family Gem 0.8.
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
                                if (startCompare) context.startActivity(Intent(context, CompareActivity::class.java)
                                        .putExtra(Extra.TREE_ID, tree.id)
                                        .putExtra(Extra.TREE_ID_2, tree2.id)
                                        .putExtra(Extra.DATE_ID, share.dateId))
                                return true
                            }
                    }
        return false
    }
}
