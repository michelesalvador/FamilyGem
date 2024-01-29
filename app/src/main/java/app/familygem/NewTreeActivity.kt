package app.familygem

import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.familygem.util.TreeUtil
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.apache.commons.io.FileUtils
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.parser.JsonParser
import java.io.File
import java.util.zip.ZipInputStream

class NewTreeActivity : BaseActivity() {

    private lateinit var progress: ProgressBar

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        setContentView(R.layout.new_tree_activity)
        progress = findViewById(R.id.new_progress)
        val referrer = Global.settings.referrer // DateID coming from a sharing
        val dateIdExists = referrer != null && referrer.matches("\\d{14}".toRegex())

        // Downloads the shared tree
        val downloadShared = findViewById<Button>(R.id.new_download_shared)
        if (dateIdExists) // Doesn't need any permissions because it unpacks only into the app's external storage
            downloadShared.setOnClickListener {
                progress.visibility = View.VISIBLE
                lifecycleScope.launch(IO) {
                    TreeUtil.downloadSharedTree(this@NewTreeActivity, referrer,
                            { startActivity(Intent(this@NewTreeActivity, TreesActivity::class.java)) },
                            { progress.visibility = View.GONE })
                }
            }
        else downloadShared.visibility = View.GONE

        // Creates an empty tree
        val emptyTree = findViewById<Button>(R.id.new_empty_tree)
        if (dateIdExists && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            emptyTree.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_light))
        }
        emptyTree.setOnClickListener {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.tree_title_dialog, null)
            val builder = AlertDialog.Builder(this)
            builder.setView(dialogView).setTitle(R.string.title)
            val suggestion = dialogView.findViewById<TextView>(R.id.treeTitle_suggestion)
            suggestion.setText(R.string.modify_later)
            suggestion.visibility = View.VISIBLE
            val editTitle = dialogView.findViewById<EditText>(R.id.treeTitle_edit)
            builder.setPositiveButton(R.string.create) { _, _ -> createNewTree(editTitle.text.toString()) }
                    .setNeutralButton(R.string.cancel, null).create().show()
            // Intercepts the DONE button on the keyboard
            editTitle.setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) {
                    createNewTree(editTitle.text.toString())
                    return@setOnEditorActionListener true // completa le azioni di salva()
                }
                false // Any other action (which don't exist)
            }
            dialogView.postDelayed({
                editTitle.requestFocus()
                val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.showSoftInput(editTitle, InputMethodManager.SHOW_IMPLICIT)
            }, 300)
        }

        // Downloads the Simpsons example tree
        findViewById<Button>(R.id.new_download_example).setOnClickListener {
            it.isEnabled = false
            progress.visibility = View.VISIBLE
            lifecycleScope.launch(IO) { downloadExample(it as Button) }
        }

        // Imports a GEDCOM file chosen with SAF
        val gedcomResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    progress.visibility = View.VISIBLE
                    lifecycleScope.launch(IO) {
                        TreeUtil.importGedcom(this@NewTreeActivity, uri, {
                            // Successful import
                            onBackPressedDispatcher.onBackPressed()
                        }, { // Unsuccessful import
                            progress.visibility = View.GONE
                        })
                    }
                }
            }
        }
        findViewById<Button>(R.id.new_import_gedcom).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            // KitKat disables .ged files in the Download folder if the type is 'application/*'
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) intent.type = "*/*"
            else intent.type = "application/*"
            gedcomResultLauncher.launch(intent)
        }

        // Tries to recover a ZIP backup chosen with SAF
        val backupResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    try {
                        val isValidBackup = ZipInputStream(contentResolver.openInputStream(uri)).use { zipInputStream ->
                            generateSequence { zipInputStream.nextEntry }.filterNot { it.isDirectory }.map { it.name }
                                    .toList().containsAll(listOf("settings.json", "tree.json"))
                        }
                        if (isValidBackup) {
                            lifecycleScope.launch(Default) {
                                TreeUtil.unZipTree(this@NewTreeActivity, null, uri,
                                        { startActivity(Intent(this@NewTreeActivity, TreesActivity::class.java)) },
                                        { progress.visibility = View.GONE })
                            }
                            /* TODO: nello strano caso che viene importato col backup ZIP lo stesso albero suggerito dal referrer
                                bisognerebbe annullare il referrer:
                                if( unZip( this, null, uri ) ){
                                String idData = Esportatore.estraiNome(uri); // che però non è statico
                                if( Global.preferenze.referrer.equals(idData) ) {
                                    Global.preferenze.referrer = null;
                                    Global.preferenze.salva();
                                }}
                             */
                        } else Toast.makeText(this@NewTreeActivity, R.string.backup_invalid, Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@NewTreeActivity, e.localizedMessage, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        findViewById<Button>(R.id.new_recover_backup).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "application/zip"
            backupResultLauncher.launch(intent)
        }
    }

    /**
     * Creates a brand new tree with the provided title.
     */
    private fun createNewTree(title: String) {
        val num = Global.settings.max() + 1
        val jsonFile = File(filesDir, "$num.json")
        Global.gc = Gedcom()
        Global.gc.header = TreeUtil.createHeader(jsonFile.name)
        Global.gc.createIndexes()
        val parser = JsonParser()
        try {
            FileUtils.writeStringToFile(jsonFile, parser.toJson(Global.gc), "UTF-8")
        } catch (e: Exception) {
            Toast.makeText(this, e.localizedMessage, Toast.LENGTH_LONG).show()
            return
        }
        Global.settings.addTree(Settings.Tree(num, title, null, 0, 0, null, null, null, 0))
        Global.settings.openTree = num
        Global.settings.save()
        onBackPressedDispatcher.onBackPressed()
        Toast.makeText(this, R.string.tree_created, Toast.LENGTH_LONG).show()
    }

    /**
     * Downloads the Simpsons ZIP file from Google Drive into the app's external cache, so without permissions needed.
     */
    private suspend fun downloadExample(downloadButton: Button) {
        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val url = "https://www.googleapis.com/drive/v3/files/1yMZgqhHx6_yP-_mjrUULDiNAaFTrAYQv?alt=media&key=AIzaSyDN3OS52Wxs58px8fPcPKOUdC0WBZFOCSY"
        val zipFile = File(externalCacheDir, "the Simpsons.zip")
        if (zipFile.exists()) zipFile.delete()
        val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(getString(R.string.simpsons_tree))
                .setDescription(getString(R.string.family_gem_example))
                .setMimeType("application/zip")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(zipFile))
        val downloadId = downloadManager.enqueue(request)
        var finishDownload = false
        while (!finishDownload) {
            yield()
            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_FAILED -> {
                            withContext(Main) {
                                progress.visibility = View.GONE
                                findViewById<Button>(R.id.new_download_example).isEnabled = true
                                Toast.makeText(this@NewTreeActivity, R.string.something_wrong, Toast.LENGTH_LONG).show()
                            }
                            finishDownload = true
                        }
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            finishDownload = true
                            TreeUtil.unZipTree(this, zipFile.path, null,
                                    { startActivity(Intent(this@NewTreeActivity, TreesActivity::class.java)) },
                                    { progress.visibility = View.GONE; downloadButton.isEnabled = true })
                        }
                    }
                }
                cursor.close();
            }
        }
    }

    // Back arrow in the toolbar like the hardware one
    override fun onOptionsItemSelected(i: MenuItem): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
