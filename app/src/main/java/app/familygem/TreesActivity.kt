package app.familygem

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.res.ResourcesCompat
import app.familygem.constant.Code
import app.familygem.constant.Extra
import app.familygem.constant.Gender
import app.familygem.merge.MergeActivity
import app.familygem.share.SharingActivity
import app.familygem.util.TreeUtils.deleteTree
import app.familygem.util.TreeUtils.openGedcom
import app.familygem.util.TreeUtils.readJson
import app.familygem.util.getBasicData
import app.familygem.visitor.MediaList
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import org.folg.gedcom.model.*
import java.io.File

/**
 * First activity visible in the app, listing all available trees.
 */
class TreesActivity : AppCompatActivity() {
    private lateinit var treeList: MutableList<Map<String, String>>
    private var adapter: SimpleAdapter? = null
    lateinit var progress: View
    private lateinit var welcome: SpeechBubble
    private lateinit var exporter: Exporter
    private var autoOpenedTree = false // To open automatically the tree at startup only once
    private var consumedNotifications: ArrayList<Int>? = ArrayList() // The birthday notification IDs are stored to display the corresponding person only once

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        setContentView(R.layout.trees)
        val listView = findViewById<ListView>(R.id.trees_list)
        progress = findViewById(R.id.trees_progress)
        welcome = SpeechBubble(this, R.string.tap_add_tree)
        exporter = Exporter(this)

        // At very first startup
        val referrer = Global.settings.referrer
        if (referrer != null && referrer == "start") retrieveReferrer()
        // If in the referrer has been stored a dateID (which will be annulled as soon as used)
        else if (referrer != null && referrer.matches("\\d{14}".toRegex())) {
            AlertDialog.Builder(this).setTitle(R.string.a_new_tree)
                    .setMessage(R.string.you_can_download)
                    .setPositiveButton(R.string.download) { _, _ -> LauncherActivity.downloadShared(this, referrer, progress) }
                    .setNeutralButton(R.string.cancel, null).show()
        } // If there is no tree
        else if (Global.settings.trees.isEmpty()) welcome.show()

        if (savedState != null) {
            autoOpenedTree = savedState.getBoolean("autoOpenedTree")
            consumedNotifications = savedState.getIntegerArrayList("consumedNotifications")
        }

        if (Global.settings.trees != null) {
            treeList = ArrayList()
            adapter = object : SimpleAdapter(this, treeList, R.layout.pezzo_albero,
                    arrayOf("title", "data"), intArrayOf(R.id.albero_titolo, R.id.albero_dati)) {
                // Returns a view of the tree list
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val treeView = super.getView(position, convertView, parent)
                    val titleView = treeView.findViewById<TextView>(R.id.albero_titolo)
                    titleView.setTextColor(ResourcesCompat.getColor(resources, R.color.text, null))
                    val detailView = treeView.findViewById<TextView>(R.id.albero_dati)
                    detailView.setTextColor(ResourcesCompat.getColor(resources, R.color.gray_text, null))
                    val treeId = treeList[position]["id"]!!.toInt()
                    val tree = Global.settings.getTree(treeId)
                    val derived = tree.grade == 20
                    val exhausted = tree.grade == 30
                    if (derived) {
                        treeView.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.accent_medium, null))
                        detailView.setTextColor(ResourcesCompat.getColor(resources, R.color.text, null))
                        treeView.setOnClickListener {
                            if (!NewTreeActivity.confronta(this@TreesActivity, tree, true)) {
                                tree.grade = 10 // It is downgraded
                                Global.settings.save()
                                updateList()
                                Toast.makeText(this@TreesActivity, R.string.something_wrong, Toast.LENGTH_LONG).show()
                            }
                        }
                    } else if (exhausted) {
                        treeView.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.consumed, null))
                        titleView.setTextColor(ResourcesCompat.getColor(resources, R.color.gray_text, null))
                        treeView.setOnClickListener {
                            if (!NewTreeActivity.confronta(this@TreesActivity, tree, true)) {
                                tree.grade = 10 // It is downgraded
                                Global.settings.save()
                                updateList()
                                Toast.makeText(this@TreesActivity, R.string.something_wrong, Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        treeView.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.back_element, null))
                        treeView.setOnClickListener {
                            progress.visibility = View.VISIBLE
                            if (!(Global.gc != null && treeId == Global.settings.openTree)) { // I not already opened
                                if (!openGedcom(treeId, true)) {
                                    progress.visibility = View.GONE
                                    return@setOnClickListener
                                }
                            }
                            startActivity(Intent(this@TreesActivity, Principal::class.java))
                        }
                    }
                    treeView.findViewById<ImageButton>(R.id.albero_menu).setOnClickListener { view: View ->
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
                        popup.show()
                        popup.setOnMenuItemClickListener { item: MenuItem ->
                            val id = item.itemId
                            if (id == -1) { // Save
                                U.saveJson(Global.gc, treeId)
                                Global.shouldSave = false
                            } else if (id == 0) { // Open a derived tree
                                openGedcom(treeId, true)
                                startActivity(Intent(this@TreesActivity, Principal::class.java))
                            } else if (id == 1) { // Tree info
                                val intent = Intent(this@TreesActivity, InfoActivity::class.java)
                                intent.putExtra(Extra.TREE_ID, treeId)
                                startActivity(intent)
                            } else if (id == 2) { // Rename tree
                                val renameView = layoutInflater.inflate(R.layout.albero_nomina, listView, false)
                                val titleEdit = renameView.findViewById<EditText>(R.id.nuovo_nome_albero)
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
                                        .putExtra(Extra.TREE_ID, treeId)
                                )
                            } else if (id == 4) { // Find errors
                                findErrors(treeId, false)
                            } else if (id == 5) { // Share tree
                                startActivity(Intent(this@TreesActivity, SharingActivity::class.java)
                                        .putExtra(Extra.TREE_ID, treeId)
                                )
                            } else if (id == 6) { // Merge with another tree
                                startActivity(Intent(this@TreesActivity, MergeActivity::class.java)
                                        .putExtra(Extra.TREE_ID, treeId))
                            } else if (id == 7) { // Compare with existing trees
                                if (NewTreeActivity.confronta(this@TreesActivity, tree, false)) {
                                    tree.grade = 20
                                    updateList()
                                } else Toast.makeText(this@TreesActivity, R.string.no_results, Toast.LENGTH_LONG).show()
                            } else if (id == 8) { // Export GEDCOM
                                if (exporter.openTree(treeId)) {
                                    val mime = arrayOf("application/octet-stream")
                                    val extension = arrayOf("ged")
                                    val code = intArrayOf(Code.GEDCOM_FILE)
                                    val totMedia = exporter.countMediaFilesToAttach()
                                    if (totMedia > 0) {
                                        val choices = arrayOf(getString(R.string.gedcom_media_zip, totMedia),
                                                getString(R.string.gedcom_only))
                                        AlertDialog.Builder(this@TreesActivity)
                                                .setTitle(R.string.export_gedcom)
                                                .setSingleChoiceItems(choices, -1) { dialog, selected ->
                                                    if (selected == 0) {
                                                        mime[0] = "application/zip"
                                                        extension[0] = "zip"
                                                        code[0] = Code.ZIPPED_GEDCOM_FILE
                                                    }
                                                    F.saveDocument(this@TreesActivity, null, treeId, mime[0], extension[0], code[0])
                                                    dialog.dismiss()
                                                }.show()
                                    } else {
                                        F.saveDocument(this@TreesActivity, null, treeId, mime[0], extension[0], code[0])
                                    }
                                }
                            } else if (id == 9) { // Export ZIP backup
                                if (exporter.openTree(treeId)) F.saveDocument(this@TreesActivity, null, treeId, "application/zip", "zip", Code.ZIP_BACKUP)
                            } else if (id == 10) { // Delete tree
                                AlertDialog.Builder(this@TreesActivity).setMessage(R.string.really_delete_tree)
                                        .setPositiveButton(R.string.delete) { _, _ ->
                                            deleteTree(treeId)
                                            updateList()
                                        }.setNeutralButton(R.string.cancel, null).show()
                            } else {
                                return@setOnMenuItemClickListener false
                            }
                            true
                        }
                    }
                    return treeView
                }
            }
            listView.adapter = adapter
            updateList()
        }

        // Custom actionbar
        val bar = supportActionBar
        val treesBar = layoutInflater.inflate(R.layout.trees_bar, listView, false)
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
            listView.post {
                if (openGedcom(Global.settings.openTree, false)) {
                    progress.visibility = View.VISIBLE
                    autoOpenedTree = true
                    startActivity(Intent(this, Principal::class.java))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Hides the progress wheel, especially when navigating back to this activity
        progress.visibility = View.GONE
    }

    // Since TreesActivity is launchMode=singleTask, onRestart is also called with startActivity (except the first one)
    // but obviously only if TreesActivity has called onStop (doing it fast only calls onPause)
    override fun onRestart() {
        super.onRestart()
        updateList()
    }

    // New intent coming from a tapped notification
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        birthdayNotifyTapped(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("autoOpenedTree", autoOpenedTree)
        outState.putIntegerArrayList("consumedNotifications", consumedNotifications)
        super.onSaveInstanceState(outState)
    }

    // If a birthday notification was tapped loads the relative tree and returns true
    private fun birthdayNotifyTapped(intent: Intent): Boolean {
        val treeId = intent.getIntExtra(Notifier.TREE_ID_KEY, 0)
        val notifyId = intent.getIntExtra(Notifier.NOTIFY_ID_KEY, 0)
        if (treeId > 0 && !consumedNotifications!!.contains(notifyId)) {
            Handler().post {
                if (openGedcom(treeId, true)) {
                    progress.visibility = View.VISIBLE
                    Global.indi = intent.getStringExtra(Notifier.INDI_ID_KEY)
                    consumedNotifications!!.add(notifyId)
                    startActivity(Intent(this, Principal::class.java))
                    Notifier(this, Global.gc, treeId, Notifier.What.DEFAULT) // Actually deletes present notification
                }
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
                            AlertDialog.Builder(this@TreesActivity).setTitle(R.string.a_new_tree)
                                    .setMessage(R.string.you_can_download)
                                    .setPositiveButton(R.string.download) { _, _ ->
                                        LauncherActivity.downloadShared(this@TreesActivity, referrer, progress)
                                    }.setNeutralButton(R.string.cancel) { _, _ -> welcome.show() }
                                    .setOnCancelListener { welcome.show() }.show()
                        } else { // It's anything else
                            Global.settings.referrer = null // Nulls it so he won't look for it again
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
            if (Global.gc != null && Global.settings.openTree == tree.id && tree.persons < 100)
                InfoActivity.refreshData(Global.gc, tree)
            item["data"] = tree.getBasicData()
            treeList.add(item)
        }
        adapter!!.notifyDataSetChanged()
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            val uri = data!!.data
            var result = false
            when (requestCode) {
                // Export GEDCOM file only
                Code.GEDCOM_FILE -> result = exporter.exportGedcom(uri)
                // Export GEDCOM with media in a ZIP file
                Code.ZIPPED_GEDCOM_FILE -> result = exporter.exportGedcomToZip(uri)
                // Export ZIP backup
                Code.ZIP_BACKUP -> result = exporter.exportZipBackup(null, -1, uri)
            }
            if (result) Toast.makeText(this@TreesActivity, exporter.successMessage, Toast.LENGTH_SHORT).show()
            else Toast.makeText(this@TreesActivity, exporter.errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    // List of error messages with occurrence count
    private var errorList: LinkedHashMap<String, Int> = LinkedHashMap()

    /**
     * Looks for some errors and returns the fixed GEDCOM or null.
     * @param correct Fix the errors or add error messages to [errorList]
     */
    fun findErrors(treeId: Int, correct: Boolean): Gedcom? {
        errorList.clear()
        val gedcom = readJson(treeId) ?: return null

        // Root in settings
        val tree = Global.settings.getTree(treeId)
        val root = gedcom.getPerson(tree.root)
        // Root points to a non-existent person
        if (tree.root != null && root == null) {
            if (gedcom.people.isNotEmpty()) {
                if (correct) {
                    tree.root = U.trovaRadice(gedcom)
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
                tree.root = U.trovaRadice(gedcom)
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

            // References to non-existent media
            // TODO: it's for a person only, it should be done maybe with a Visitor for every other record
            val mediaIterator = person.mediaRefs.iterator()
            var count = 0
            while (mediaIterator.hasNext()) {
                val mediaId = mediaIterator.next().ref
                val media = gedcom.getMedia(mediaId)
                if (media == null) {
                    if (correct) mediaIterator.remove()
                    else addError("Broken OBJE $mediaId in $personId")
                } else {
                    if (mediaId == media.id) {
                        count++
                        if (count > 1) if (correct) mediaIterator.remove()
                        else addError("Multiple OBJE $mediaId in $personId")
                    }
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

        // Null references in source towards media
        // Solves possible errors caused by the "mediaId" bug, fixed with commit dbea5adb
        gedcom.sources.forEach { source ->
            val mediaIterator = source.mediaRefs.iterator()
            while (mediaIterator.hasNext()) {
                if (mediaIterator.next().ref == null) {
                    if (correct) mediaIterator.remove()
                    else addError("OBJE without value in ${source.id}")
                }
            }
            // Removes empty list of media refs
            if (source.mediaRefs.isEmpty() && correct) {
                source.mediaRefs = null
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
                    val correctGedcom = findErrors(treeId, true)
                    U.saveJson(correctGedcom, treeId)
                    if (treeId == Global.settings.openTree && Global.gc != null) Global.gc = correctGedcom
                    else Global.gc = null // Resets it to reload corrected
                    findErrors(treeId, false) // Restart process to display the result
                    updateList()
                }
            }
            dialog.setNeutralButton(R.string.cancel, null).show()
        }
        return gedcom
    }

    private fun addError(description: String) {
        if (!errorList.contains(description)) errorList[description] = 1
        else errorList[description] = errorList[description]!! + 1
    }
}
