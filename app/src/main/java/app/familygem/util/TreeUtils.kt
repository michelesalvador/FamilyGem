package app.familygem.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import app.familygem.*
import app.familygem.Settings.Tree
import app.familygem.constant.Extra
import app.familygem.share.CompareActivity
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import org.folg.gedcom.model.*
import org.folg.gedcom.parser.JsonParser
import org.folg.gedcom.parser.ModelParser
import java.io.*
import java.util.*
import java.util.zip.ZipInputStream

fun Tree.getBasicData(): String {
    var data = "$persons ${Global.context.getString(if (persons == 1) R.string.person else R.string.persons).lowercase()}"
    if (persons > 1 && generations > 0) data += " - $generations " +
            Global.context.getString(if (generations == 1) R.string.generation else R.string.generations).lowercase()
    if (media > 0) data += " - $media ${Global.context.getString(R.string.media).lowercase()}"
    return data
}

object TreeUtils {
    /**
     * Standard opening of a stored GEDCOM to edit it.
     */
    fun openGedcom(treeId: Int, saveSettings: Boolean): Boolean {
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

    /**
     *  Lightly opens a stored GEDCOM for different purposes.
     */
    fun openGedcomTemporarily(treeId: Int, putInGlobal: Boolean): Gedcom? {
        val gedcom: Gedcom?
        if (Global.gc != null && Global.settings.openTree == treeId) gedcom = Global.gc else {
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
    fun readJson(treeId: Int): Gedcom? {
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
        } catch (e: Exception) {
            Toast.makeText(Global.context, e.localizedMessage, Toast.LENGTH_LONG).show()
            return null
        } catch (e: Error) {
            val message = if (e is OutOfMemoryError) Global.context.getString(R.string.not_memory_tree) else e.localizedMessage
            Toast.makeText(Global.context, message, Toast.LENGTH_LONG).show()
            return null
        }
        var json: String = text.toString()
        json = updateTreeLanguage(json)
        gedcom = JsonParser().fromJson(json)
        if (gedcom == null) {
            Toast.makeText(Global.context, R.string.no_useful_data, Toast.LENGTH_LONG).show()
            return null
        }
        // This Notifier was introduced in version 0.9.1
        // TODO: Can be removed from here in the future because tree.birthdays will never more be null
        if (Global.settings.getTree(treeId).birthdays == null) {
            Notifier(Global.context, gedcom, treeId, Notifier.What.CREATE)
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
     * Saves the currently opened tree.
     *
     * @param refresh Will refresh also other activities
     * @param objects Record(s) to which update the change date
     */
    fun save(refresh: Boolean, vararg objects: Any) {
        if (refresh) Global.edited = true
        objects.forEach { ChangeUtils.updateChangeDate(it) }
        // On the first save adds an extension to submitters
        if (Global.settings.currentTree.grade == 9) {
            Global.gc.submitters.forEach { it.putExtension("passed", true) }
            Global.settings.currentTree.grade = 10
            Global.settings.save()
        }
        if (Global.settings.autoSave) {
            GlobalScope.launch(Dispatchers.IO) { saveJson(Global.gc, Global.settings.openTree) }
        } else { // Displays the 'Save' button
            Global.shouldSave = true
            if (Global.mainView != null) {
                val menu = Global.mainView.findViewById<NavigationView>(R.id.menu)
                menu.getHeaderView(0).findViewById<View>(R.id.menu_salva).visibility = View.VISIBLE
            }
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
            h.dateTime = ChangeUtils.actualDateTime()
            // If necessary updates the version of Family Gem
            if (h.generator.version != null && h.generator.version != BuildConfig.VERSION_NAME || h.generator.version == null)
                h.generator.version = BuildConfig.VERSION_NAME
        }
        try {
            FileUtils.writeStringToFile(File(Global.context.filesDir, "$treeId.json"), JsonParser().toJson(gedcom), "UTF-8")
        } catch (e: IOException) {
            Utils.toast(e.localizedMessage)
        }
        Notifier(Global.context, gedcom, treeId, Notifier.What.DEFAULT)
    }

    // Temporary hack to call a suspend function from Java
    fun saveJsonAsync(gedcom: Gedcom, treeId: Int) = GlobalScope.launch(Dispatchers.IO) { saveJson(gedcom, treeId) }

    fun deleteTree(treeId: Int) {
        val treeFile = File(Global.context.filesDir, "$treeId.json")
        treeFile.delete()
        val mediaDir = Global.context.getExternalFilesDir(treeId.toString())
        if (mediaDir != null) deleteFilesAndDirs(mediaDir)
        if (Global.settings.openTree == treeId) Global.gc = null
        Notifier(Global.context, null, treeId, Notifier.What.DELETE)
        Global.settings.deleteTree(treeId)
    }

    private fun deleteFilesAndDirs(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) {
            for (child in fileOrDirectory.listFiles()!!) deleteFilesAndDirs(child)
        }
        fileOrDirectory.delete()
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
        header.dateTime = ChangeUtils.actualDateTime()
        return header
    }

    /**
     * Imports a GEDCOM provided by an URI.
     */
    suspend fun importGedcom(context: Context, uri: Uri, onSuccess: () -> Unit, onFail: () -> Unit) {
        try {
            // Reads the input
            val input = context.contentResolver.openInputStream(uri)
            val gedcom = ModelParser().parseGedcom(input)
            if (gedcom.header == null) {
                withContext(Dispatchers.Main) {
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
            val path = F.uriFilePath(uri)
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
            val rootId = U.trovaRadice(gedcom)
            Global.settings.addTree(Tree(newNumber, treeName, folderPath,
                    gedcom.people.size, InfoActivity.countGenerations(gedcom, rootId), rootId, null, 0))
            Notifier(context, gedcom, newNumber, Notifier.What.CREATE)
            withContext(Dispatchers.Main) {
                // If necessary propose to show advanced tools
                if (gedcom.sources.isNotEmpty() && !Global.settings.expert) {
                    AlertDialog.Builder(context).setMessage(R.string.complex_tree_advanced_tools)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                Global.settings.expert = true
                                Global.settings.save()
                                onSuccess()
                            }.setNegativeButton(android.R.string.cancel) { _, _ -> onSuccess() }
                            .show()

                } else onSuccess()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, e.localizedMessage, Toast.LENGTH_LONG).show()
                onFail()
            }
        } catch (e: Error) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.something_wrong, Toast.LENGTH_LONG).show()
                onFail()
            }
        }
    }

    /**
     * Unzips a ZIP tree file in the device storage.
     * Used equally by: Simpsons example, backup files and shared trees.
     */
    suspend fun unZipTree(context: Context, zipPath: String?, zipUri: Uri?): Boolean { // TODO: sostituisci Boolean con callback
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
                    zipped.persons, zipped.generations, zipped.root, zipped.shares, zipped.grade)
            Global.settings.addTree(tree)
            settingsFile.delete()
            // Tree coming from sharing intended for comparison
            if (zipped.grade == 9 && compareTrees(context, tree, false)) {
                tree.grade = 20 // Marks it as derived
            }
            // The download was done from the referrer dialog in TreesActivity
            if (context is TreesActivity) {
                withContext(Dispatchers.Main) {
                    context.progress.visibility = View.GONE
                    context.updateList()
                }
            } else // Example tree (Simpson) or backup tree (from LauncherActivity or from NewTreeActivity)
                context.startActivity(Intent(context, TreesActivity::class.java))
            Global.settings.save()
            Utils.toast(R.string.tree_imported_ok)
            return true
        } catch (e: Exception) {
            Utils.toast(e.localizedMessage)
            // TODO: onFail()
        }
        return false
    }

    // Temporary hack to call a suspend function from Java
    fun unZipTreeAsync(context: Context, zipPath: String?, zipUri: Uri?) =
            GlobalScope.launch(Dispatchers.Default) { unZipTree(context, zipPath, zipUri) }

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
