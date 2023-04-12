package app.familygem.util

import android.widget.Toast
import app.familygem.Global
import app.familygem.Notifier
import app.familygem.R
import app.familygem.Settings.Tree
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.parser.JsonParser
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

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
        json = updateLanguage(json)
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
    private fun updateLanguage(json: String): String {
        var engJson = json
        engJson = engJson.replace("\"zona\":", "\"zone\":")
        engJson = engJson.replace("\"famili\":", "\"kin\":")
        engJson = engJson.replace("\"passato\":", "\"passed\":")
        return engJson
    }

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
}
