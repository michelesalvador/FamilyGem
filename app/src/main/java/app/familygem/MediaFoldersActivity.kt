package app.familygem

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import app.familygem.constant.Extra
import app.familygem.util.Util

/** Here user can set the list of media folders. */
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) checkPermissions() else chooseFolder()
        }
        if (Global.settings.getTree(treeId).dirs.isEmpty() && Global.settings.getTree(treeId).uris.isEmpty())
            SpeechBubble(this, R.string.add_device_folder).show()
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        var granted = true
        permissions.entries.forEach {
            granted = it.value and granted
        }
        if (granted) chooseFolder()
        else AlertDialog.Builder(this).setMessage(getString(R.string.not_granted_add_folder))
            .setPositiveButton(R.string.yes) { _, _ -> chooseFolder() }
            .setNeutralButton(R.string.cancel, null).show()
    }

    private fun checkPermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        var result = PackageManager.PERMISSION_GRANTED
        for (permission in requiredPermissions) {
            result = result or ContextCompat.checkSelfPermission(this, permission)
        }
        if (result == PackageManager.PERMISSION_GRANTED) chooseFolder()
        else if (result == PackageManager.PERMISSION_DENIED) permissionLauncher.launch(requiredPermissions)
    }

    /** Manages a folder chosen with SAF. */
    private val chooseLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                val path = getFolderPathFromUri(uri)
                if (path != null) {
                    if (!dirs.contains(path)) {
                        dirs.add(path)
                        save()
                    } else Toast.makeText(this, "Already listed.", Toast.LENGTH_LONG).show()
                } else {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val docDir = DocumentFile.fromTreeUri(this, uri)
                    if (docDir != null && docDir.canRead()) {
                        if (!uris.contains(uri.toString())) {
                            uris.add(uri.toString())
                            save()
                        } else Toast.makeText(this, "Already listed.", Toast.LENGTH_LONG).show()
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
                val split = treeDocId.split(":".toRegex()).dropLastWhile { it.isEmpty() } //.toTypedArray()
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

    /** Launches SAF to choose a folder. */
    private fun chooseFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        chooseLauncher.launch(intent)
    }

    private fun updateList() {
        val layout = findViewById<LinearLayout>(R.id.mediaFolders_list)
        layout.removeAllViews()
        dirs.forEach { dir ->
            val folderView = layoutInflater.inflate(R.layout.media_folder_item, layout, false)
            layout.addView(folderView)
            folderView.findViewById<TextView>(R.id.mediaFolder_name).text = getFolderName(dir)
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
                uriView.findViewById<TextView>(R.id.mediaFolder_name).text = documentDir.name
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
                        if (!uriExistsElsewhere) revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        uris.remove(uriString)
                        save()
                    }
                }
                registerForContextMenu(uriView)
            }
        }
    }

    private fun getFolderName(url: String): String {
        return if (url.lastIndexOf('/') > 0) url.substring(url.lastIndexOf('/') + 1)
        else url
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

    override fun onCreateContextMenu(menu: ContextMenu, view: View, info: ContextMenuInfo) {
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
