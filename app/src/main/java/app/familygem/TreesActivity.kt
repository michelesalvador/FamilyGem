package app.familygem

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.DragEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.SimpleAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import app.familygem.constant.Code
import app.familygem.constant.Extra
import app.familygem.constant.Gender
import app.familygem.main.MainActivity
import app.familygem.merge.MergeActivity
import app.familygem.share.SharingActivity
import app.familygem.util.TreeUtil
import app.familygem.util.Util
import app.familygem.util.getBasicData
import app.familygem.visitor.MediaList
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.folg.gedcom.model.ChildRef
import org.folg.gedcom.model.Family
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.Note
import org.folg.gedcom.model.ParentFamilyRef
import org.folg.gedcom.model.Person
import org.folg.gedcom.model.Repository
import org.folg.gedcom.model.Source
import org.folg.gedcom.model.SpouseFamilyRef
import org.folg.gedcom.model.SpouseRef
import org.folg.gedcom.model.Submitter
import java.io.File

/**
 * First activity visible in the app, listing all available trees.
 */
class TreesActivity : AppCompatActivity() {
    private lateinit var treeList: MutableList<Map<String, String>>
    private lateinit var adapter: SimpleAdapter
    lateinit var progress: View
    private lateinit var welcome: SpeechBubble
    private lateinit var exporter: Exporter
    private var autoOpenedTree = false // To open automatically the tree at startup only once
    private var consumedNotifications = ArrayList<Int>() // The birthday notification IDs are stored to display the corresponding person only once
    private var draggedTreeId = 0

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        setContentView(R.layout.trees_activity)
        val listView = findViewById<ListView>(R.id.trees_list)
        progress = findViewById(R.id.progress_wheel)
        welcome = SpeechBubble(this, R.string.tap_add_tree)
        exporter = Exporter(this)

        // At very first startup
        val referrer = Global.settings.referrer
        if (referrer != null && referrer == "start") retrieveReferrer()
        // If in the referrer has been stored a dateID (which will be annulled as soon as used)
        else if (referrer != null && referrer.matches("\\d{14}".toRegex())) {
            showSharedTreeDialog(referrer) {}
        } // If there are no trees
        else if (Global.settings.trees.isEmpty()) welcome.show()

        if (savedState != null) {
            autoOpenedTree = savedState.getBoolean("autoOpenedTree")
            consumedNotifications = savedState.getIntegerArrayList("consumedNotifications")!!
        }

        if (Global.settings.trees == null) return
        treeList = ArrayList()
        adapter = object : SimpleAdapter(this, treeList, R.layout.tree_view,
                arrayOf("title", "data"), intArrayOf(R.id.tree_title, R.id.tree_data)) {
            // Returns a view of the tree list
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val treeView = super.getView(position, convertView, parent)
                val treeLayout = treeView.findViewById<RelativeLayout>(R.id.tree_layout)
                val titleView = treeLayout.findViewById<TextView>(R.id.tree_title)
                titleView.setTextColor(ResourcesCompat.getColor(resources, R.color.text, null))
                val detailView = treeLayout.findViewById<TextView>(R.id.tree_data)
                detailView.setTextColor(ResourcesCompat.getColor(resources, R.color.gray_text, null))
                val treeId = treeList[position]["id"]!!.toInt()
                val tree = Global.settings.getTree(treeId)
                val derived = tree.grade == 20
                val exhausted = tree.grade == 30
                if (derived) {
                    treeLayout.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.accent_medium, null))
                    detailView.setTextColor(ResourcesCompat.getColor(resources, R.color.text, null))
                    treeView.setOnClickListener {
                        if (!TreeUtil.compareTrees(this@TreesActivity, tree, true)) {
                            tree.grade = 10 // It is downgraded
                            Global.settings.save()
                            updateList()
                            Toast.makeText(this@TreesActivity, R.string.something_wrong, Toast.LENGTH_LONG).show()
                        }
                    }
                } else if (exhausted) {
                    treeLayout.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.consumed, null))
                    titleView.setTextColor(ResourcesCompat.getColor(resources, R.color.gray_text, null))
                    treeView.setOnClickListener {
                        if (!TreeUtil.compareTrees(this@TreesActivity, tree, true)) {
                            tree.grade = 10 // It is downgraded
                            Global.settings.save()
                            updateList()
                            Toast.makeText(this@TreesActivity, R.string.something_wrong, Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    treeLayout.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.back_element, null))
                    treeView.setOnClickListener {
                        progress.visibility = View.VISIBLE
                        if (Global.gc != null && treeId == Global.settings.openTree) { // Tree already opened
                            startActivity(Intent(this@TreesActivity, MainActivity::class.java))
                        } else {
                            lifecycleScope.launch(IO) {
                                if (TreeUtil.openGedcom(treeId, true))
                                    startActivity(Intent(this@TreesActivity, MainActivity::class.java))
                                else withContext(Main) { progress.visibility = View.GONE }
                            }
                        }
                    }
                }
                treeLayout.visibility = if (treeId == draggedTreeId) View.INVISIBLE else View.VISIBLE
                // Three dots button
                treeView.findViewById<ImageButton>(R.id.tree_menu).setOnClickListener { view: View ->
                    val exists = File(filesDir, "$treeId.json").exists()
                    val popup = PopupMenu(this@TreesActivity, view)
                    val menu = popup.menu
                    if (treeId == Global.settings.openTree && Global.shouldSave)
                        menu.add(0, -1, 0, R.string.save)
                    if (Global.settings.expert && derived || Global.settings.expert && exhausted)
                        menu.add(0, 0, 0, R.string.open)
                    if (!exhausted || Global.settings.expert)
                        menu.add(0, 1, 0, R.string.tree_info)
                    if (!derived && !exhausted || Global.settings.expert)
                        menu.add(0, 2, 0, R.string.rename)
                    if (exists && (!derived || Global.settings.expert) && !exhausted)
                        menu.add(0, 3, 0, R.string.media_folders)
                    if (!exhausted)
                        menu.add(0, 4, 0, R.string.find_errors)
                    if (exists && !derived && !exhausted) // You cannot re-share a tree received back, even if you are an expert
                        menu.add(0, 5, 0, R.string.share_tree)
                    if (exists && !derived && !exhausted && Global.settings.expert && Global.settings.trees.size > 1)
                        menu.add(0, 6, 0, R.string.merge_tree)
                    if (exists && !derived && !exhausted && Global.settings.expert && Global.settings.trees.size > 1
                            && tree.shares != null && tree.grade != 0) // Must be 9 or 10
                        menu.add(0, 7, 0, R.string.compare)
                    if (exists && Global.settings.expert && !exhausted)
                        menu.add(0, 8, 0, R.string.export_gedcom)
                    if (exists && Global.settings.expert)
                        menu.add(0, 9, 0, R.string.make_backup)
                    menu.add(0, 10, 0, R.string.delete)
                    popup.setOnMenuItemClickListener(MenuItemClickListener(position, treeId))
                    popup.show()
                }
                if (Global.settings.trees.size <= 1) return treeView
                // Dragging tree listeners
                treeView.setOnLongClickListener { view ->
                    val dragShadow = View.DragShadowBuilder(view)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                        view.startDragAndDrop(null, dragShadow, tree, 0)
                    else view.startDrag(null, dragShadow, tree, 0)
                    draggedTreeId = treeId
                    treeLayout.visibility = View.INVISIBLE
                    true
                }
                treeView.setOnDragListener { view, event ->
                    when (event.action) {
                        DragEvent.ACTION_DRAG_ENTERED -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                treeLayout.foreground = ResourcesCompat.getDrawable(resources, R.drawable.line_above, null)
                            }
                        }
                        DragEvent.ACTION_DRAG_LOCATION -> {
                            val touchY = view.top + event.y // Y coordinate of touch event inside the current viewport
                            if (touchY < listView.top + U.dpToPx(60f)) {
                                listView.smoothScrollBy(-U.dpToPx(10f), 25)
                            } else if (touchY > listView.bottom - U.dpToPx(60f)) {
                                listView.smoothScrollBy(U.dpToPx(10f), 25)
                            }
                        }
                        DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                treeLayout.foreground = null
                            }
                        }
                        DragEvent.ACTION_DROP -> {
                            val draggedTree = event.localState as Settings.Tree
                            val startPosition = Global.settings.trees.indexOf(draggedTree)
                            var dropPosition = listView.getPositionForView(view)
                            draggedTreeId = 0
                            when (startPosition) {
                                dropPosition -> treeLayout.visibility = View.VISIBLE // Dropped on itself
                                dropPosition - 1 -> adapter.notifyDataSetChanged() // Dropped on the following one
                                else -> {
                                    Global.settings.trees.remove(draggedTree)
                                    if (startPosition < dropPosition) dropPosition--
                                    Global.settings.trees.add(dropPosition, draggedTree)
                                    updateList()
                                    Global.settings.save()
                                }
                            }
                        }
                    }
                    true
                }
                return treeView
            }
        }
        listView.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DROP -> {
                    // Drops the tree at the end of the list
                    val draggedTree = event.localState as Settings.Tree
                    Global.settings.trees.remove(draggedTree)
                    Global.settings.trees.add(draggedTree)
                    draggedTreeId = 0
                    updateList()
                    Global.settings.save()
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    // Fixes the drop outside the list on old devices
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                        draggedTreeId = 0
                        adapter.notifyDataSetChanged()
                    }
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    // Refreshes the list dropping a tree outside the list (e.g. on the toolbar)
                    // Called only from API 24+
                    draggedTreeId = 0
                    adapter.notifyDataSetChanged()
                }
            }
            true
        }
        listView.adapter = adapter
        updateList()

        // Custom actionbar
        val bar = supportActionBar
        val treesBar = layoutInflater.inflate(R.layout.trees_bar, listView, false)
        treesBar.findViewById<ImageButton>(R.id.trees_help).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/michelesalvador/FamilyGem/wiki")))
        }
        treesBar.findViewById<ImageButton>(R.id.trees_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        bar!!.customView = treesBar
        bar.setDisplayShowCustomEnabled(true)

        // FAB
        findViewById<View>(R.id.fab).setOnClickListener {
            welcome.hide()
            startActivity(Intent(this, NewTreeActivity::class.java))
        }

        // Automatic load of last opened tree of previous session
        if ((!birthdayNotifyTapped(intent) && !autoOpenedTree
                        && intent.getBooleanExtra(Extra.AUTO_LOAD_TREE, false)) && Global.settings.openTree > 0) {
            progress.visibility = View.VISIBLE
            lifecycleScope.launch(IO) {
                if (TreeUtil.openGedcom(Global.settings.openTree, false)) {
                    autoOpenedTree = true
                    startActivity(Intent(this@TreesActivity, MainActivity::class.java))
                } else withContext(Main) { progress.visibility = View.GONE }
            }
        }
    }

    /**
     * Displays an AlertDialog that asks to download the shared tree.
     */
    private fun showSharedTreeDialog(dateId: String, onCancel: () -> Unit) {
        AlertDialog.Builder(this)
                .setTitle(R.string.a_new_tree)
                .setMessage(R.string.you_can_download)
                .setNeutralButton(R.string.cancel) { _, _ -> onCancel() }
                .setOnCancelListener { onCancel() }
                .setPositiveButton(R.string.download) { _, _ ->
                    progress.visibility = View.VISIBLE
                    lifecycleScope.launch(IO) {
                        TreeUtil.downloadSharedTree(this@TreesActivity, dateId, {
                            progress.visibility = View.GONE
                            updateList()
                        }, { progress.visibility = View.GONE })
                    }
                }.show()
    }

    inner class MenuItemClickListener(val position: Int, val treeId: Int) : PopupMenu.OnMenuItemClickListener {
        override fun onMenuItemClick(item: MenuItem): Boolean {
            val id = item.itemId
            if (id == -1) { // Save
                GlobalScope.launch(IO) { TreeUtil.saveJson(Global.gc, treeId) }
                Global.shouldSave = false
            } else if (id == 0) { // Open a derived tree
                progress.visibility = View.VISIBLE
                lifecycleScope.launch(IO) {
                    if (TreeUtil.openGedcom(treeId, true))
                        startActivity(Intent(this@TreesActivity, MainActivity::class.java))
                    else withContext(Main) { progress.visibility = View.GONE }
                }
            } else if (id == 1) { // Tree info
                val intent = Intent(this@TreesActivity, InfoActivity::class.java)
                intent.putExtra(Extra.TREE_ID, treeId)
                startActivity(intent)
            } else if (id == 2) { // Rename tree
                val renameView = layoutInflater.inflate(R.layout.tree_title_dialog, null, false)
                val titleEdit = renameView.findViewById<EditText>(R.id.treeTitle_edit)
                titleEdit.setText(treeList[position]["title"])
                val dialog = AlertDialog.Builder(this@TreesActivity)
                        .setView(renameView).setTitle(R.string.title)
                        .setPositiveButton(R.string.rename) { _, _ ->
                            Global.settings.renameTree(treeId, titleEdit.text.toString())
                            updateList()
                        }.setNeutralButton(R.string.cancel, null).create()
                titleEdit.setOnEditorActionListener { _, action, _ ->
                    if (action == EditorInfo.IME_ACTION_DONE) dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                    false
                }
                dialog.show()
                renameView.postDelayed({
                    titleEdit.requestFocus()
                    titleEdit.setSelection(titleEdit.text.length)
                    val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    inputMethodManager.showSoftInput(titleEdit, InputMethodManager.SHOW_IMPLICIT)
                }, 300)
            } else if (id == 3) { // Media folders
                startActivity(Intent(this@TreesActivity, MediaFoldersActivity::class.java)
                        .putExtra(Extra.TREE_ID, treeId))
            } else if (id == 4) { // Find errors
                progress.visibility = View.VISIBLE
                lifecycleScope.launch(Default) {
                    if (findErrors(treeId, false) == null)
                        withContext(Main) { progress.visibility = View.GONE }
                }
            } else if (id == 5) { // Share tree
                startActivity(Intent(this@TreesActivity, SharingActivity::class.java)
                        .putExtra(Extra.TREE_ID, treeId))
            } else if (id == 6) { // Merge with another tree
                startActivity(Intent(this@TreesActivity, MergeActivity::class.java)
                        .putExtra(Extra.TREE_ID, treeId))
            } else if (id == 7) { // Compare with existing trees
                val tree = Global.settings.getTree(treeId)
                if (TreeUtil.compareTrees(this@TreesActivity, tree, false)) {
                    tree.grade = 20
                    updateList()
                } else Toast.makeText(this@TreesActivity, R.string.no_results, Toast.LENGTH_LONG).show()
            } else if (id == 8) { // Export GEDCOM
                lifecycleScope.launch(IO) {
                    if (exporter.openTree(treeId)) {
                        var mime = "application/octet-stream"
                        var extension = "ged"
                        var code = Code.GEDCOM_FILE
                        val totMedia = exporter.countMediaFilesToAttach()
                        if (totMedia > 0) {
                            withContext(Main) {
                                val choices = arrayOf(getString(R.string.gedcom_media_zip, totMedia), getString(R.string.gedcom_only))
                                AlertDialog.Builder(this@TreesActivity)
                                        .setTitle(R.string.export_gedcom)
                                        .setSingleChoiceItems(choices, -1) { dialog, selected ->
                                            if (selected == 0) {
                                                mime = "application/zip"
                                                extension = "zip"
                                                code = Code.ZIPPED_GEDCOM_FILE
                                            }
                                            F.saveDocument(this@TreesActivity, null, treeId, mime, extension, code)
                                            dialog.dismiss()
                                        }.show()
                            }
                        } else {
                            F.saveDocument(this@TreesActivity, null, treeId, mime, extension, code)
                        }
                    } else Util.toast(exporter.errorMessage)
                }
            } else if (id == 9) { // Export ZIP backup
                lifecycleScope.launch(IO) {
                    exporter.openTree(treeId)
                    F.saveDocument(this@TreesActivity, null, treeId, "application/zip", "zip", Code.ZIP_BACKUP)
                }
            } else if (id == 10) { // Delete tree
                AlertDialog.Builder(this@TreesActivity).setMessage(R.string.really_delete_tree)
                        .setPositiveButton(R.string.delete) { _, _ ->
                            TreeUtil.deleteTree(treeId)
                            updateList()
                        }.setNeutralButton(R.string.cancel, null).show()
            } else {
                return false
            }
            return true
        }
    }

    // Since TreesActivity is launchMode=singleTask, onRestart is also called with startActivity (except the first one)
    // but obviously only if TreesActivity has called onStop (doing it fast only calls onPause)
    override fun onRestart() {
        super.onRestart()
        updateList()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("autoOpenedTree", autoOpenedTree)
        outState.putIntegerArrayList("consumedNotifications", consumedNotifications)
        super.onSaveInstanceState(outState)
    }

    // By leaving the activity hides the progress wheel
    override fun onStop() {
        super.onStop()
        progress.visibility = View.GONE
    }

    // New intent coming from a tapped notification
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        birthdayNotifyTapped(intent)
    }

    // If a birthday notification was tapped loads the relative tree and returns true
    private fun birthdayNotifyTapped(intent: Intent): Boolean {
        val treeId = intent.getIntExtra(Notifier.TREE_ID_KEY, 0)
        val notifyId = intent.getIntExtra(Notifier.NOTIFY_ID_KEY, 0)
        if (treeId > 0 && !consumedNotifications.contains(notifyId)) {
            progress.visibility = View.VISIBLE
            lifecycleScope.launch(IO) {
                if (TreeUtil.openGedcom(treeId, true)) {
                    Global.indi = intent.getStringExtra(Notifier.PERSON_ID_KEY)
                    consumedNotifications.add(notifyId)
                    startActivity(Intent(this@TreesActivity, MainActivity::class.java))
                    Notifier(this@TreesActivity, Global.gc, treeId, Notifier.What.DEFAULT) // Actually deletes present notification
                } else withContext(Main) { progress.visibility = View.GONE }
            }
            return true
        }
        return false
    }

    // Tries to retrieve the dateID from the Play Store in case the app was installed after a sharing
    // If finds the dateID proposes to download the shared tree
    private fun retrieveReferrer() {
        val client = InstallReferrerClient.newBuilder(this).build()
        client.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(answer: Int) {
                when (answer) {
                    InstallReferrerClient.InstallReferrerResponse.OK -> try {
                        val details = client.installReferrer
                        // Normally 'referrer' is a string like "utm_source=google-play&utm_medium=organic"
                        // But if the app was installed from the link in the share page it will be a dateID like "20191003215337"
                        val referrer = details.installReferrer
                        if (referrer != null && referrer.matches("\\d{14}".toRegex())) { // It's a dateID
                            Global.settings.referrer = referrer
                            showSharedTreeDialog(referrer, welcome::show)
                        } else { // It's anything else
                            Global.settings.referrer = null // We null it so we won't look for it again
                            welcome.show()
                        }
                        Global.settings.save()
                        client.endConnection()
                    } catch (e: Exception) {
                        U.toast(e.localizedMessage)
                    }
                    InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED, // Play Store app does not exist on the device or other incorrect answer
                    InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> { // I've never seen this appear
                        Global.settings.referrer = null // So we never come back here
                        Global.settings.save()
                        welcome.show()
                    }
                }
            }

            override fun onInstallReferrerServiceDisconnected() {
                // Never seen it appear
                U.toast("Install Referrer Service Disconnected")
            }
        })
    }

    fun updateList() {
        treeList.clear()
        for (tree in Global.settings.trees) {
            val item: MutableMap<String, String> = HashMap(3)
            item["id"] = tree.id.toString()
            item["title"] = tree.title
            // If GEDCOM is already open, updates the data
            if (Global.gc != null && Global.settings.openTree == tree.id)
                TreeUtil.refreshData(Global.gc, tree)
            item["data"] = tree.getBasicData()
            treeList.add(item)
        }
        adapter.notifyDataSetChanged()
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            progress.visibility = View.VISIBLE
            lifecycleScope.launch(IO) {
                val uri = data!!.data
                if (uri != null) {
                    var result = false
                    when (requestCode) {
                        // Exports GEDCOM file only
                        Code.GEDCOM_FILE -> result = exporter.exportGedcom(uri)
                        // Exports GEDCOM with media in a ZIP file
                        Code.ZIPPED_GEDCOM_FILE -> result = exporter.exportGedcomToZip(uri)
                        // Exports ZIP backup
                        Code.ZIP_BACKUP -> result = exporter.exportZipBackup(null, -1, uri)
                    }
                    if (result) Util.toast(exporter.successMessage)
                    else Util.toast(exporter.errorMessage)
                } else Util.toast(R.string.cant_understand_uri)
                withContext(Main) { progress.visibility = View.GONE }
            }
        }
    }

    // List of error messages with occurrence count
    private var errorList: LinkedHashMap<String, Int> = LinkedHashMap()

    /**
     * Looks for some errors and returns the fixed GEDCOM or null.
     * @param correct Fix the errors or add error messages to [errorList]
     */
    suspend fun findErrors(treeId: Int, correct: Boolean): Gedcom? {
        errorList.clear()
        val gedcom = if (Global.shouldSave && treeId == Global.settings.openTree && Global.gc != null) Global.gc
        else TreeUtil.readJson(treeId) ?: return null

        // Root in settings
        val tree = Global.settings.getTree(treeId)
        val root = gedcom.getPerson(tree.root)
        // Root points to a non-existent person
        if (tree.root != null && root == null) {
            if (gedcom.people.isNotEmpty()) {
                if (correct) {
                    tree.root = U.findRootId(gedcom)
                    Global.settings.save()
                } else addError("Missing root person")
            } else { // Tree without persons
                if (correct) {
                    tree.root = null
                    Global.settings.save()
                } else addError("Root without people")
            }
        }
        // Or a root is not indicated in settings even though there are people in the tree
        if (root == null && gedcom.people.isNotEmpty()) {
            if (correct) {
                tree.root = U.findRootId(gedcom)
                Global.settings.save()
            } else addError("Root not defined")
        }
        // Or in settings there is a share root that doesn't exist
        val shareRoot = gedcom.getPerson(tree.shareRoot)
        if (tree.shareRoot != null && shareRoot == null) {
            if (correct) {
                tree.shareRoot = null // Just deletes it
                Global.settings.save()
            } else addError("Missing share root person")
        }

        // Null IDs
        gedcom.people.filter { it.id == null }.forEach {
            if (correct) it.id = U.newID(gedcom, Person::class.java)
            else addError("INDI without ID")
        }
        gedcom.families.filter { it.id == null }.forEach {
            if (correct) it.id = U.newID(gedcom, Family::class.java)
            else addError("FAM without ID")
        }
        gedcom.media.filter { it.id == null }.forEach {
            if (correct) it.id = U.newID(gedcom, Media::class.java)
            else addError("OBJE without ID")
        }
        gedcom.notes.filter { it.id == null }.forEach {
            if (correct) it.id = U.newID(gedcom, Note::class.java)
            else addError("NOTE without ID")
        }
        gedcom.sources.filter { it.id == null }.forEach {
            if (correct) it.id = U.newID(gedcom, Source::class.java)
            else addError("SOUR without ID")
        }
        gedcom.repositories.filter { it.id == null }.forEach {
            if (correct) it.id = U.newID(gedcom, Repository::class.java)
            else addError("REPO without ID")
        }
        gedcom.submitters.filter { it.id == null }.forEach {
            if (correct) it.id = U.newID(gedcom, Submitter::class.java)
            else addError("SUBM without ID")
        }

        // Duplicated IDs
        val doneIds: MutableSet<String> = mutableSetOf()
        gedcom.people.filterNot { it.id == null }.filter { person -> gedcom.people.count { it.id == person.id } > 1 }
                .filterNot { doneIds.add(it.id) }.forEach {
                    if (correct) it.id = U.newID(gedcom, Person::class.java)
                    else addError("Multiple INDI with ID ${it.id}")
                }
        doneIds.clear()
        gedcom.families.filterNot { it.id == null }.filter { family -> gedcom.families.count { it.id == family.id } > 1 }
                .filterNot { doneIds.add(it.id) }.forEach {
                    if (correct) it.id = U.newID(gedcom, Family::class.java)
                    else addError("Multiple FAM with ID ${it.id}")
                }
        doneIds.clear()
        gedcom.media.filterNot { it.id == null }.filter { media -> gedcom.media.count { it.id == media.id } > 1 }
                .filterNot { doneIds.add(it.id) }.forEach {
                    if (correct) it.id = U.newID(gedcom, Media::class.java)
                    else addError("Multiple OBJE with ID ${it.id}")
                }
        doneIds.clear()
        gedcom.notes.filterNot { it.id == null }.filter { note -> gedcom.notes.count { it.id == note.id } > 1 }
                .filterNot { doneIds.add(it.id) }.forEach {
                    if (correct) it.id = U.newID(gedcom, Note::class.java)
                    else addError("Multiple NOTE with ID ${it.id}")
                }
        doneIds.clear()
        gedcom.sources.filterNot { it.id == null }.filter { source -> gedcom.sources.count { it.id == source.id } > 1 }
                .filterNot { doneIds.add(it.id) }.forEach {
                    if (correct) it.id = U.newID(gedcom, Source::class.java)
                    else addError("Multiple SOUR with ID ${it.id}")
                }
        doneIds.clear()
        gedcom.repositories.filterNot { it.id == null }.filter { repo -> gedcom.repositories.count { it.id == repo.id } > 1 }
                .filterNot { doneIds.add(it.id) }.forEach {
                    if (correct) it.id = U.newID(gedcom, Repository::class.java)
                    else addError("Multiple REPO with ID ${it.id}")
                }
        doneIds.clear()
        gedcom.submitters.filterNot { it.id == null }.filter { submitter -> gedcom.submitters.count { it.id == submitter.id } > 1 }
                .filterNot { doneIds.add(it.id) }.forEach {
                    if (correct) it.id = U.newID(gedcom, Submitter::class.java)
                    else addError("Multiple SUBM with ID ${it.id}")
                }

        // After modification of IDs it's necessary to refresh the indexes
        if (correct) gedcom.createIndexes()

        // Empty families
        val familyIterator = gedcom.families.iterator()
        while (familyIterator.hasNext()) {
            val family = familyIterator.next()
            if (family.eventsFacts.isEmpty() && family.notes.isEmpty() && family.noteRefs.isEmpty() && family.media.isEmpty()
                    && family.mediaRefs.isEmpty() && family.sourceCitations.isEmpty() && family.extensions.isEmpty()) {
                val members = family.husbandRefs.size + family.wifeRefs.size + family.childRefs.size
                if (members <= 1) {
                    if (correct) familyIterator.remove()
                    // This will leave in persons broken references to this family
                    // but later these references will be deleted too
                    else addError(if (members == 0) "${family.id} is empty family" else "${family.id} is single-member family")
                }
            }
        }
        // Silently deletes empty list of families
        if (gedcom.families.isEmpty() && correct) gedcom.families = null

        // References from persons to families
        gedcom.people.forEach { person ->
            val personId = person.id
            // References from a person towards parent families
            val pfrIterator = person.parentFamilyRefs.iterator()
            while (pfrIterator.hasNext()) {
                val familyId = pfrIterator.next().ref
                if (familyId == null) {
                    if (correct) pfrIterator.remove()
                    else addError("FAMC without value in $personId")
                } else if (gedcom.families.none { it.id == familyId }) {
                    if (correct) pfrIterator.remove()
                    else addError("Broken FAMC $familyId in $personId")
                } else {
                    val family = gedcom.getFamily(familyId)
                    if (family.childRefs.none { it.ref == person.id }) {
                        if (correct) {
                            val childRef = ChildRef()
                            childRef.ref = person.id
                            family.addChild(childRef)
                        } else addError("Missing CHIL $personId in $familyId")
                    }
                    if (person.parentFamilyRefs.filter { it.ref == familyId }.size > 1) {
                        if (correct) pfrIterator.remove()
                        else addError("Multiple FAMC $familyId in $personId")
                    }
                }
            }
            // Removes empty list of parent family refs
            if (person.parentFamilyRefs.isEmpty() && correct) {
                person.parentFamilyRefs = null
            }
            // References from a person towards spouse families
            val sfrIterator = person.spouseFamilyRefs.iterator()
            while (sfrIterator.hasNext()) {
                val familyId = sfrIterator.next().ref
                if (familyId == null) {
                    if (correct) sfrIterator.remove()
                    else addError("FAMS without value in $personId")
                } else if (gedcom.families.none { it.id == familyId }) {
                    if (correct) sfrIterator.remove()
                    else addError("Broken FAMS $familyId in $personId")
                } else {
                    val family = gedcom.getFamily(familyId)
                    if (family.husbandRefs.none { it.ref == person.id }
                            && family.wifeRefs.none { it.ref == person.id }) {
                        val female = Gender.isFemale(person)
                        if (correct) {
                            val spouseRef = SpouseRef()
                            spouseRef.ref = person.id
                            if (female) family.addWife(spouseRef)
                            else family.addHusband(spouseRef)
                        } else addError(if (female) "Missing WIFE $personId in $familyId" else "Missing HUSB $personId in $familyId")
                    }
                    if (person.spouseFamilyRefs.filter { it.ref == familyId }.size > 1) {
                        if (correct) sfrIterator.remove()
                        else addError("Multiple FAMS $familyId in $personId")
                    }
                }
            }
            // Removes empty list of spouse family refs
            if (person.spouseFamilyRefs.isEmpty() && correct) {
                person.spouseFamilyRefs = null
            }

            // References to non-existent media, or multiple media with same ID
            // TODO: it's for a person only, it should be done maybe with a Visitor for every other record
            val mediaIterator = person.mediaRefs.iterator()
            while (mediaIterator.hasNext()) {
                val mediaId = mediaIterator.next().ref
                val media = gedcom.getMedia(mediaId)
                if (media == null) {
                    if (correct) mediaIterator.remove()
                    else addError("Broken OBJE $mediaId in $personId")
                } else if (person.mediaRefs.filter { it.ref == mediaId }.size > 1) {
                    if (correct) mediaIterator.remove()
                    else addError("Multiple OBJE $mediaId in $personId")
                }
            }
        }
        // References from each family to the persons belonging to it
        gedcom.families.forEach { family ->
            val familyId = family.id
            // Husbands refs
            val husbandIterator = family.husbandRefs.iterator()
            while (husbandIterator.hasNext()) {
                val husbandId = husbandIterator.next().ref
                if (husbandId == null) {
                    if (correct) husbandIterator.remove()
                    else addError("HUSB without value in $familyId")
                } else if (gedcom.people.none { it.id == husbandId }) {
                    if (correct) husbandIterator.remove()
                    else addError("Broken HUSB $husbandId in $familyId")
                } else {
                    val husband = gedcom.getPerson(husbandId)
                    if (husband.spouseFamilyRefs.none { it.ref == familyId }) {
                        if (correct) {
                            val newSpouse = SpouseFamilyRef()
                            newSpouse.ref = familyId
                            husband.addSpouseFamilyRef(newSpouse)
                        } else addError("Missing FAMS $familyId in ${husband.id}")
                    }
                    if (family.husbandRefs.filter { it.ref == husbandId }.size > 1) {
                        if (correct) husbandIterator.remove()
                        else addError("Multiple HUSB $husbandId in $familyId")
                    }
                }
            }
            // Removes empty list of husband refs
            if (family.husbandRefs.isEmpty() && correct) {
                family.husbandRefs = null
            }
            // Wives refs
            val wifeIterator = family.wifeRefs.iterator()
            while (wifeIterator.hasNext()) {
                val wifeId = wifeIterator.next().ref
                if (wifeId == null) {
                    if (correct) wifeIterator.remove()
                    else addError("WIFE without value in $familyId")
                } else if (gedcom.people.none { it.id == wifeId }) {
                    if (correct) wifeIterator.remove()
                    else addError("Broken WIFE $wifeId in $familyId")
                } else {
                    val wife = gedcom.getPerson(wifeId)
                    if (wife.spouseFamilyRefs.none { it.ref == familyId }) {
                        if (correct) {
                            val spouseFamilyRef = SpouseFamilyRef()
                            spouseFamilyRef.ref = familyId
                            wife.addSpouseFamilyRef(spouseFamilyRef)
                        } else addError("Missing FAMS $familyId in $wifeId")
                    }
                    if (family.wifeRefs.filter { it.ref == wifeId }.size > 1) {
                        if (correct) wifeIterator.remove()
                        else addError("Multiple WIFE $wifeId in $familyId")
                    }
                }
            }
            // Remove empty list of wife refs
            if (family.wifeRefs.isEmpty() && correct) {
                family.wifeRefs = null
            }
            // Children refs
            val childIterator = family.childRefs.iterator()
            while (childIterator.hasNext()) {
                val childId = childIterator.next().ref
                if (childId == null) {
                    if (correct) childIterator.remove()
                    else addError("CHIL without value in $familyId")
                } else if (gedcom.people.none { it.id == childId }) {
                    if (correct) childIterator.remove()
                    else addError("Broken CHIL $childId in $familyId")
                } else {
                    val child = gedcom.getPerson(childId)
                    if (child.parentFamilyRefs.none { it.ref == familyId }) {
                        if (correct) {
                            val parentFamilyRef = ParentFamilyRef()
                            parentFamilyRef.ref = familyId
                            child.addParentFamilyRef(parentFamilyRef)
                        } else addError("Missing FAMC $familyId in $childId")
                    }
                    if (family.childRefs.filter { it.ref == childId }.size > 1) {
                        if (correct) childIterator.remove()
                        else addError("Multiple CHIL $childId in $familyId")
                    }
                }
            }
            // Remove empty list of child refs
            if (family.childRefs.isEmpty() && correct) {
                family.childRefs = null
            }
        }

        // Adds a 'TYPE' tag to name types that don't have it
        gedcom.people.forEach { person ->
            person.names.forEach {
                if (it.type != null && it.typeTag == null) {
                    if (correct) it.typeTag = "TYPE"
                    else addError("NAME without TYPE tag in ${person.id}")
                }
            }
        }

        // Adds a 'FILE' tag to media that don't have it
        val mediaList = MediaList(gedcom, 0)
        gedcom.accept(mediaList)
        mediaList.list.forEach {
            if (it.fileTag == null) {
                if (correct) it.fileTag = "FILE"
                else addError("OBJE without FILE tag")
            }
        }

        // Completes the process
        if (!correct) {
            val dialog = AlertDialog.Builder(this)
            val total = errorList.values.sum()
            var message: String
            if (total == 0) message = getString(R.string.all_ok)
            else {
                message = if (total == 1) getString(R.string.error_found) else getString(R.string.errors_found, total)
                message += "\n"
                errorList.forEach {
                    message += it.key
                    message += if (it.value > 1) " (×${it.value})\n" else "\n"
                }
            }
            dialog.setMessage(message)
            if (total > 0) {
                dialog.setPositiveButton(R.string.correct) { _, _ ->
                    progress.visibility = View.VISIBLE
                    lifecycleScope.launch(Default) {
                        val correctGedcom = findErrors(treeId, true)
                        if (correctGedcom != null) {
                            TreeUtil.saveJson(correctGedcom, treeId)
                            Global.shouldSave = false // Just in case
                            if (treeId == Global.settings.openTree && Global.gc != null) Global.gc = correctGedcom
                            else Global.gc = null // Resets it to reload corrected
                            findErrors(treeId, false) // Restart process to display the result
                            withContext(Main) { updateList() }
                        }
                    }
                }
            }
            dialog.setNeutralButton(R.string.cancel, null)
            withContext(Main) {
                progress.visibility = View.GONE
                dialog.show()
            }
        }
        return gedcom
    }

    private fun addError(description: String) {
        if (!errorList.contains(description)) errorList[description] = 1
        else errorList[description] = errorList[description]!! + 1
    }
}
