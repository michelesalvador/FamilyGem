package app.familygem.profile

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import app.familygem.Global
import app.familygem.Memory
import app.familygem.NewRelativeDialog
import app.familygem.PersonEditorActivity
import app.familygem.R
import app.familygem.U
import app.familygem.constant.Choice
import app.familygem.constant.Destination
import app.familygem.constant.Extra
import app.familygem.constant.Image
import app.familygem.constant.Relation
import app.familygem.constant.Type
import app.familygem.detail.EventActivity
import app.familygem.detail.MediaActivity
import app.familygem.detail.NameActivity
import app.familygem.detail.NoteActivity
import app.familygem.main.MainActivity
import app.familygem.main.SourcesFragment
import app.familygem.util.FamilyUtil
import app.familygem.util.FileUtil
import app.familygem.util.MediaUtil
import app.familygem.util.NoteUtil
import app.familygem.util.TreeUtil.save
import app.familygem.util.Util
import app.familygem.util.delete
import app.familygem.util.getFamilyLabels
import app.familygem.util.sex
import app.familygem.visitor.FindStack
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.folg.gedcom.model.EventFact
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.MediaRef
import org.folg.gedcom.model.Name
import org.folg.gedcom.model.Note
import org.folg.gedcom.model.NoteRef
import org.folg.gedcom.model.Person
import org.folg.gedcom.model.SourceCitation

/** Here is displayed all about a person. */
class ProfileActivity : AppCompatActivity() {

    private var person: Person? = null
    private lateinit var adapter: PagesAdapter
    private lateinit var tabLayout: TabLayout
    private lateinit var toolbarLayout: CollapsingToolbarLayout
    private lateinit var fabView: FloatingActionButton
    private val pages = arrayOfNulls<Fragment>(3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.profile_activity)
        onBackPressedDispatcher.addCallback(this) {
            Memory.stepBack()
            isEnabled = false // Disables this callback to avoid infinite loop
            onBackPressedDispatcher.onBackPressed()
        }

        // Toolbar
        val toolbar = findViewById<Toolbar>(R.id.profile_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Brings up the back arrow and the options menu

        // Assigns to the pager an adapter that manages the three pages
        val viewPager = findViewById<ViewPager2>(R.id.profile_pager)
        adapter = PagesAdapter(this)
        viewPager.adapter = adapter
        // Furnishes tab layout
        tabLayout = findViewById(R.id.profile_tabs)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = getString(
                when (position) {
                    0 -> R.string.media
                    1 -> R.string.events
                    else -> R.string.relatives
                }
            )
        }.attach()
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            // Animates the FAB
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                // positionOffset is 0.0 > 1.0 during swiping, and becomes 0.0 at the end
                fabView.apply { if (positionOffset > 0) hide() else show() }
            }
        })
        // Only at first creation selects one specific tab
        if (savedInstanceState == null) tabLayout.getTabAt(intent.getIntExtra(Extra.PAGE, 1))!!.select()

        // Setups FAB
        fabView = findViewById(R.id.fab)
        setupFab()

        // The person displayed
        person = Memory.getLeaderObject() as Person?
        if (person == null) {
            goBack()
            return
        }
        // Person ID in the header
        val idView = findViewById<TextView>(R.id.profile_id)
        if (Global.settings.expert) {
            idView.text = "INDI ${person?.id}"
            idView.setOnClickListener {
                U.editId(
                    this, person
                ) { this.refresh() }
            }
        } else idView.visibility = View.GONE
        // Person name in the header
        findViewById<CollapsingToolbarLayout>(R.id.profile_toolbar_layout).apply {
            toolbarLayout = this
            title = U.properName(person)
            setExpandedTitleTextAppearance(R.style.AppTheme_ExpandedAppBar)
            setCollapsedTitleTextAppearance(R.style.AppTheme_CollapsedAppBar)
            setOnClickListener { // Not only the name view but all the expanded toolbar
                if (person!!.names.isNotEmpty()) {
                    Memory.add(person!!.names[0])
                    startActivity(Intent(this@ProfileActivity, NameActivity::class.java))
                }
            }
        }
        setImages()
        Global.indi = person?.id
    }

    private inner class PagesAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

        override fun createFragment(position: Int): Fragment {
            pages[position] = when (position) {
                0 -> MediaFragment()
                1 -> FactsFragment()
                else -> RelativesFragment()
            }
            return pages[position]!!
        }

        override fun getItemCount(): Int = 3

        /** Reloads the content of the three pages. */
        fun notifyPagesChanged() {
            pages.filterNotNull().forEach {
                if (it.isAdded) (it as BaseFragment).createContent()
            }
        }
    }

    private var isActivityRestarting: Boolean = false // To refresh activity only onBackPressed()

    public override fun onRestart() {
        super.onRestart()
        isActivityRestarting = true
    }

    // We need onResume instead of onRestart because it is executed after the ActivityResult launchers
    override fun onResume() {
        super.onResume()
        // Updates contents when coming back with onBackPressed()
        if (isActivityRestarting) {
            person = Memory.getLeaderObject() as Person?
            if (person == null) goBack() // Coming back to the profile of a person who has been deleted
            else if (Global.edited) refresh()
            isActivityRestarting = false
        }
    }

    /** Displays two images in the profile header: a regular one and the same blurred on background. */
    private fun setImages() {
        val imageView = findViewById<ImageView>(R.id.profile_image)
        val media = FileUtil.selectMainImage(person!!, imageView)
        // Same image blurred on background
        val backImageView = findViewById<ImageView>(R.id.profile_background)
        if (media != null) {
            imageView.setOnClickListener {
                FindStack(Global.gc, media, true)
                startActivity(Intent(this, MediaActivity::class.java))
            }
            // imageView waits for the image to be loaded
            imageView.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    val type = imageView.getTag(R.id.tag_file_type) as Type
                    if (type == Type.CROPPABLE || type == Type.VIDEO || type == Type.PDF || type == Type.WEB_IMAGE) {
                        FileUtil.showImage(media, backImageView, Image.BLUR or Image.DARK)
                        backImageView.visibility = View.VISIBLE
                    } else backImageView.visibility = View.GONE
                    if (type != Type.NONE) {
                        imageView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                }
            })
        } else backImageView.visibility = View.GONE
    }

    /** Setups the click listener of floating action button. */
    private fun setupFab() {
        val mainEventTags = arrayOf("BIRT", "CHR", "RESI", "OCCU", "DEAT", "BURI")
        val otherEventTags = arrayOf(
            "CREM", "ADOP", "BAPM", "BARM", "BATM", "BLES", "CONF", "FCOM", "ORDN",  //Events
            "NATU", "EMIG", "IMMI", "CENS", "PROB", "WILL", "GRAD", "RETI", "EVEN",
            "CAST", "DSCR", "EDUC", "NATI", "NCHI", "PROP", "RELI", "SSN", "TITL",  // Attributes
            "_MILT" // User-defined
        ) // Standard GEDCOM tags missing in the EventFact.DISPLAY_TYPE list: BASM (there is BATM instead) CHRA IDNO NMR FACT
        val otherEvents: MutableList<Pair<String, String>> = ArrayList() // List of tag + label
        otherEventTags.forEach {
            val event = EventFact()
            event.tag = it
            var label = event.displayType
            if (Global.settings.expert) label += " — $it"
            otherEvents.add(Pair(it, label))
        }
        // Alphabetically sorted by label
        otherEvents.sortWith { item1: Pair<String, String>, item2: Pair<String, String> ->
            item1.second.compareTo(item2.second)
        }
        fabView.setOnClickListener { view: View? ->
            if (Global.gc == null) return@setOnClickListener
            val popup = PopupMenu(this, view!!)
            val menu = popup.menu
            when (tabLayout.selectedTabPosition) {
                0 -> { // Media
                    menu.add(0, 10, 0, R.string.new_media)
                    menu.add(0, 11, 0, R.string.new_shared_media)
                    if (Global.gc.media.isNotEmpty()) menu.add(0, 12, 0, R.string.link_shared_media)
                }
                1 -> { // Facts
                    menu.add(0, 20, 0, R.string.name)
                    // Sex
                    if (person?.sex?.isMissing() == true) menu.add(0, 21, 0, R.string.sex)
                    // Main events
                    val eventSubMenu = menu.addSubMenu(R.string.event)
                    val mainEventStrings = arrayOf(
                        R.string.birth, R.string.christening, R.string.residence, R.string.occupation, R.string.death, R.string.burial
                    )
                    mainEventTags.forEachIndexed { i, tag ->
                        val string = getString(mainEventStrings[i])
                        val label = if (Global.settings.expert) "$string — $tag" else string
                        eventSubMenu.add(0, 40 + i, 0, label)
                    }
                    // Other events
                    val otherSubMenu = eventSubMenu.addSubMenu(R.string.other)
                    otherEvents.forEachIndexed { i, item -> otherSubMenu.add(0, 50 + i, 0, item.second) }
                    val noteSubMenu = menu.addSubMenu(R.string.note)
                    noteSubMenu.add(0, 22, 0, R.string.new_note)
                    noteSubMenu.add(0, 23, 0, R.string.new_shared_note)
                    if (Global.gc.notes.isNotEmpty()) noteSubMenu.add(0, 24, 0, R.string.link_shared_note)
                    if (Global.settings.expert) {
                        val sourceSubMenu = menu.addSubMenu(R.string.source)
                        sourceSubMenu.add(0, 25, 0, R.string.new_source)
                        if (Global.gc.sources.isNotEmpty()) sourceSubMenu.add(0, 26, 0, R.string.link_source)
                    }
                }
                2 -> { // Relatives
                    menu.add(0, 30, 0, R.string.new_relative)
                    if (U.linkablePersons(person)) menu.add(0, 31, 0, R.string.link_person)
                }
            }
            popup.show()
            popup.setOnMenuItemClickListener { item: MenuItem ->
                val relativeStrings = arrayOf(
                    getString(R.string.parent), getString(R.string.sibling), getString(R.string.partner), getString(R.string.child)
                )
                when (item.itemId) {
                    0 -> {} // When a submenu is clicked
                    // Media page
                    10 -> FileUtil.displayFileChooser(this, localMediaLauncher, Destination.SIMPLE_MEDIA) // New simple media
                    11 -> FileUtil.displayFileChooser(this, sharedMediaLauncher, Destination.SHARED_MEDIA) // New shared media
                    12 -> { // Link shared media
                        val intent = Intent(this, MainActivity::class.java)
                        intent.putExtra(Choice.MEDIA, true)
                        chooseLauncher.launch(intent)
                    }
                    // Facts page
                    20 -> { // Create name
                        val name = Name()
                        name.value = "//"
                        person!!.addName(name)
                        Memory.add(name)
                        startActivity(Intent(this, NameActivity::class.java))
                        save(true, person)
                    }
                    21 -> { // Create sex
                        val sexNames =
                            arrayOf(getString(R.string.male), getString(R.string.female), getString(R.string.unknown))
                        AlertDialog.Builder(tabLayout.context)
                            .setSingleChoiceItems(sexNames, -1) { dialog: DialogInterface, i: Int ->
                                val gender = EventFact()
                                gender.tag = "SEX"
                                val sexValues = arrayOf("M", "F", "U")
                                gender.value = sexValues[i]
                                person?.addEventFact(gender)
                                dialog.dismiss()
                                FamilyUtil.updateSpouseRoles(person!!)
                                refresh()
                                save(true, person)
                            }.show()
                    }
                    22 -> { // Create note
                        val note = Note()
                        note.value = ""
                        person?.addNote(note)
                        Memory.add(note)
                        startActivity(Intent(this, NoteActivity::class.java))
                        // TODO: maybe make it editable with DetailActivity.edit(value);
                        save(true, person)
                    }
                    23 -> NoteUtil.createSharedNote(this, person)
                    24 -> { // Link shared note
                        val intent = Intent(this, MainActivity::class.java)
                        intent.putExtra(Choice.NOTE, true)
                        chooseLauncher.launch(intent)
                    }
                    25 -> SourcesFragment.newSource(this, person)
                    26 -> { // Link existing source
                        val intent = Intent(this, MainActivity::class.java)
                        intent.putExtra(Choice.SOURCE, true)
                        chooseLauncher.launch(intent)
                    }
                    // Relatives page
                    30 -> { // Link new person
                        if (Global.settings.expert) {
                            NewRelativeDialog(person, null, null, true, null).show(supportFragmentManager, null)
                        } else {
                            AlertDialog.Builder(this).setItems(relativeStrings) { _, selected ->
                                val intent = Intent(this, PersonEditorActivity::class.java)
                                intent.putExtra(Extra.PERSON_ID, person!!.id)
                                intent.putExtra(Extra.RELATION, Relation.get(selected))
                                if (U.checkMultiMarriages(intent, this, null)) return@setItems
                                startActivity(intent)
                            }.show()
                        }
                    }
                    31 -> { // Link existing person
                        if (Global.settings.expert) {
                            NewRelativeDialog(person, null, null, false, null).show(supportFragmentManager, null)
                        } else {
                            AlertDialog.Builder(this).setItems(relativeStrings) { _, selected ->
                                val intent = Intent(this, MainActivity::class.java)
                                intent.putExtra(Choice.PERSON, true)
                                intent.putExtra(Extra.PERSON_ID, person!!.id)
                                intent.putExtra(Extra.RELATION, Relation.get(selected))
                                if (U.checkMultiMarriages(intent, this, null)) return@setItems
                                choosePersonLauncher.launch(intent)
                            }.show()
                        }
                    }
                    else -> { // Events
                        val keyTag = if (item.itemId >= 50) otherEvents[item.itemId - 50].first
                        else if (item.itemId >= 40) mainEventTags[item.itemId - 40]
                        else null
                        if (keyTag == null) return@setOnMenuItemClickListener false
                        val event = EventFact().apply {
                            tag = keyTag
                            when (keyTag) {
                                "EVEN" -> {
                                    type = ""
                                    date = ""
                                    value = ""
                                }
                                "OCCU", "TITL" -> value = ""
                                "RESI" -> place = ""
                                "BIRT", "DEAT", "CHR", "BAPM", "BURI" -> {
                                    place = ""
                                    date = ""
                                }
                            }
                        }
                        person?.addEventFact(event)
                        Memory.add(event)
                        startActivity(Intent(this, EventActivity::class.java))
                        save(true, person)
                    }
                }
                true
            }
        }
    }

    /** Refreshes header and pages without recreating the activity. */
    fun refresh() {
        toolbarLayout.title = U.properName(person) // Name in the header
        setImages() // Header images
        // ID in the header
        if (Global.settings.expert) {
            val idView = findViewById<TextView>(R.id.profile_id)
            idView.text = "INDI ${person?.id}"
        }
        adapter.notifyPagesChanged() // Three pages
        invalidateOptionsMenu() // Menu
    }

    @JvmField
    val choosePersonLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            it.data?.apply {
                val modified = PersonEditorActivity.addRelative(
                    getStringExtra(Extra.PERSON_ID), // Corresponds to person.id
                    getStringExtra(Extra.RELATIVE_ID),
                    getStringExtra(Extra.FAMILY_ID),
                    getSerializableExtra(Extra.RELATION) as Relation?,
                    getStringExtra(Extra.DESTINATION)
                )
                save(true, *modified)
                refresh()
            }
        }
    }

    /** Launcher to manage various records picked. */
    private val chooseLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            it.data?.apply {
                if (getStringExtra(Extra.MEDIA_ID) != null) { // Shared media
                    val mediaRef = MediaRef()
                    mediaRef.ref = getStringExtra(Extra.MEDIA_ID)
                    person?.addMediaRef(mediaRef)
                } else if (getStringExtra(Extra.NOTE_ID) != null) { // Shared note
                    val noteRef = NoteRef()
                    noteRef.ref = getStringExtra(Extra.NOTE_ID)
                    person?.addNoteRef(noteRef)
                } else if (getStringExtra(Extra.SOURCE_ID) != null) { // Source
                    val citation = SourceCitation()
                    citation.ref = getStringExtra(Extra.SOURCE_ID)
                    person?.addSourceCitation(citation)
                }
                save(true, person)
                refresh()
            }
        }
    }

    private val localMediaLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            val media = Media()
            media.fileTag = "FILE"
            person?.addMedia(media)
            if (FileUtil.setFileAndProposeCropping(this, it.data, media)) {
                save(true, person)
                refresh()
            }
        }
    }

    private val sharedMediaLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            val sharedMedia = MediaUtil.newSharedMedia(person)
            if (FileUtil.setFileAndProposeCropping(this, it.data, sharedMedia)) {
                save(true, sharedMedia, person)
                refresh()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (Global.gc == null || person == null) return false
        menu.add(0, 0, 0, R.string.diagram)
        val familyLabels = person!!.getFamilyLabels(this)
        if (familyLabels[0] != null) menu.add(0, 1, 0, familyLabels[0])
        if (familyLabels[1] != null) menu.add(0, 2, 0, familyLabels[1])
        if (Global.settings.currentTree.root == null || Global.settings.currentTree.root != person?.id)
            menu.add(0, 3, 0, R.string.make_root)
        menu.add(0, 4, 0, R.string.modify)
        menu.add(0, 5, 0, R.string.delete)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            0 -> U.whichParentsToShow(this, person, 1) // DiagramFragment
            1 -> U.whichParentsToShow(this, person, 2) // Family as child
            2 -> U.whichSpousesToShow(this, person) // Family as partner
            3 -> { // Set as root
                Global.settings.currentTree.root = person?.id
                Global.settings.save()
                Toast.makeText(this, getString(R.string.this_is_root, U.properName(person)), Toast.LENGTH_LONG).show()
            }
            4 -> { // Edit
                val intent = Intent(this, PersonEditorActivity::class.java)
                intent.putExtra(Extra.PERSON_ID, person?.id)
                startActivity(intent)
            }
            5 -> { // Delete
                Util.confirmDelete(this) {
                    val families = person!!.delete()
                    if (!U.deleteEmptyFamilies(this, { this.goBack() }, true, *families)) goBack()
                }
            }
            else -> goBack()
        }
        return false
    }

    private fun goBack() = onBackPressedDispatcher.onBackPressed()

    fun getPageFragment(page: Int): Fragment {
        return pages[page] ?: adapter.createFragment(page)
    }
}
