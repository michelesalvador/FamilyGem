package app.familygem

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import app.familygem.constant.Extra
import app.familygem.util.FileUtil
import app.familygem.util.Util
import java.io.File

/** Activity where user can set the list of media folders. */
class MediaFoldersActivity : BaseActivity() {

    private var treeId: Int = 0
    private lateinit var dirs: MutableList<String>
    private lateinit var uris: MutableList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.media_folders_activity)
        treeId = intent.getIntExtra(Extra.TREE_ID, 0)
        // Sometimes a null dir or uri is stored in settings
        dirs = ArrayList(Global.settings.getTree(treeId).dirs).filterNot { it == null }.toMutableList()
        uris = ArrayList(Global.settings.getTree(treeId).uris).filterNot { it == null }.toMutableList()
        updateList()
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        findViewById<View>(R.id.fab).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            chooseLauncher.launch(intent)
        }
        if (Global.settings.getTree(treeId).dirs.isEmpty() && Global.settings.getTree(treeId).uris.isEmpty())
            SpeechBubble(this, R.string.add_device_folder).show()
    }

    /** Saves a folder chosen with SAF in directory or URI list. */
    private val chooseLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                val path = getFolderPathFromUri(uri)
                if (path != null && FileUtil.isOwnedDirectory(this, File(path))) {
                    if (!dirs.contains(path)) {
                        dirs.add(path)
                        save()
                    } else Toast.makeText(this, "Already listed.", Toast.LENGTH_LONG).show()
                } else {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    val docDir = DocumentFile.fromTreeUri(this, uri)
                    if (docDir != null && docDir.canRead()) {
                        if (!uris.contains(uri.toString())) {
                            uris.add(uri.toString())
                            save()
                        } else {
                            Toast.makeText(this, "Already listed.", Toast.LENGTH_LONG).show()
                            // Maybe the URI is already listed, but was invalid
                            updateList()
                            Global.edited = true
                        }
                    } else Toast.makeText(this, "Could not read this position.", Toast.LENGTH_LONG).show()
                }
            } else Toast.makeText(this, R.string.cant_understand_uri, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * @param uri An URI tree obtained with ACTION_OPEN_DOCUMENT_TREE
     * @return The path of the folder or null
     */
    private fun getFolderPathFromUri(uri: Uri): String? {
        val treeDocId = DocumentsContract.getTreeDocumentId(uri)
        when (uri.authority) {
            "com.android.externalstorage.documents" -> {
                val split = treeDocId.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                var path: String? = null
                if (split[0].equals("primary", true)) { // Main storage
                    path = Environment.getExternalStorageDirectory().absolutePath
                } else if (split[0].equals("home", true)) { // Documents in Android 9 and 10
                    // Not Documents in Android 11, that is 'primary'
                    path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
                } else { // All the others, like the SD card
                    val filesDirs = Global.context.getExternalFilesDirs(null)
                    for (dir in filesDirs) {
                        val other = dir.absolutePath
                        if (other.contains(split[0])) {
                            path = other.substring(0, other.indexOf("/Android"))
                            break
                        }
                    }
                }
                if (path != null) {
                    if (split.size > 1 && split[1].isNotEmpty()) path += "/" + split[1]
                    return path
                }
            }
            "com.android.providers.downloads.documents" -> {
                if (treeDocId == "downloads") return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                if (treeDocId.startsWith("raw:/")) return treeDocId.replaceFirst("raw:".toRegex(), "")
            }
        }
        return null
    }

    private fun updateList() {
        val layout = findViewById<LinearLayout>(R.id.mediaFolders_list)
        layout.removeAllViews()
        dirs.forEach { dir ->
            val folderView = layoutInflater.inflate(R.layout.media_folder_item, layout, false)
            layout.addView(folderView)
            val name = if (dir.lastIndexOf('/') > 0) dir.substring(dir.lastIndexOf('/') + 1) else dir
            folderView.findViewById<TextView>(R.id.mediaFolder_name).text = name
            val urlView = folderView.findViewById<TextView>(R.id.mediaFolder_url)
            urlView.text = dir
            if (Global.settings.expert) urlView.isSingleLine = false
            folderView.findViewById<View>(R.id.mediaFolder_delete).setOnClickListener {
                Util.confirmDelete(this) {
                    dirs.remove(dir)
                    save()
                }
            }
            registerForContextMenu(folderView)
        }
        uris.forEach { uriString ->
            val uri = Uri.parse(uriString)
            DocumentFile.fromTreeUri(this, uri)?.let { documentDir ->
                val uriView = layoutInflater.inflate(R.layout.media_folder_item, layout, false)
                layout.addView(uriView)
                uriView.findViewById<TextView>(R.id.mediaFolder_name).text = documentDir.name ?: "[Invalid]"
                val urlView = uriView.findViewById<TextView>(R.id.mediaFolder_url)
                if (Global.settings.expert) urlView.isSingleLine = false
                urlView.text = Uri.decode(uriString) // Shows it decoded, a little more readable
                uriView.findViewById<View>(R.id.mediaFolder_delete).setOnClickListener {
                    Util.confirmDelete(this) {
                        // Revokes permission for this URI, if the URI is not used in any other tree
                        var uriExistsElsewhere = false
                        outer@ for (tree in Global.settings.trees) {
                            for (uri1 in tree.uris) {
                                if (uri1 != null && uri1 == uriString && tree.id != treeId) {
                                    uriExistsElsewhere = true
                                    break@outer
                                }
                            }
                        }
                        if (!uriExistsElsewhere) revokeUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        uris.remove(uriString)
                        save()
                    }
                }
                registerForContextMenu(uriView)
            }
        }
    }

    private fun save() {
        Global.settings.getTree(treeId).dirs.clear()
        for (path in dirs) Global.settings.getTree(treeId).dirs.add(path)
        Global.settings.getTree(treeId).uris.clear()
        for (uri in uris) Global.settings.getTree(treeId).uris.add(uri)
        Global.settings.save()
        updateList()
        Global.edited = true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private lateinit var selectedView: View

    override fun onCreateContextMenu(menu: ContextMenu, view: View, info: ContextMenuInfo?) {
        selectedView = view
        menu.add(0, 0, 0, R.string.copy)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 0) {
            U.copyToClipboard(getText(android.R.string.copyUrl), selectedView.findViewById<TextView>(R.id.mediaFolder_url).text)
            return true
        }
        return false
    }
}
