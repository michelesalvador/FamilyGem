package app.familygem.main

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.GravityCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import app.familygem.Global
import app.familygem.R
import app.familygem.TreesActivity
import app.familygem.U
import app.familygem.constant.Choice
import app.familygem.constant.Extra
import app.familygem.constant.Image
import app.familygem.databinding.MainActivityBinding
import app.familygem.detail.MediaActivity
import app.familygem.util.FileUtil.showImage
import app.familygem.util.TreeUtil
import app.familygem.visitor.FindStack
import app.familygem.visitor.MediaList
import app.familygem.visitor.NoteList
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Layout containing the main menu and displaying fragments.
 */
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: MainActivityBinding
    lateinit var manager: FragmentManager
    private var _frontFragment: BaseFragment? = null // The fragment visible on the main view
    val frontFragment
        get() = _frontFragment ?: manager.findFragmentById(R.id.main_fragment) as BaseFragment
    private val menuIds: List<Int> = listOf(
        R.id.menu_diagram, R.id.menu_persons, R.id.menu_families, R.id.menu_media, R.id.menu_notes,
        R.id.menu_sources, R.id.menu_repositories, R.id.menu_submitters, R.id.menu_settings
    )
    private val fragments = listOf<Class<*>>(
        DiagramFragment::class.java, PersonsFragment::class.java, FamiliesFragment::class.java,
        GalleryFragment::class.java, NotesFragment::class.java, SourcesFragment::class.java, RepositoriesFragment::class.java,
        SubmittersFragment::class.java, TreeSettingsFragment::class.java
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainActivityBinding.inflate(layoutInflater)
        manager = supportFragmentManager
        setContentView(binding.root)
        setSupportActionBar(binding.mainToolbar)
        val toggle = ActionBarDrawerToggle(this, binding.mainLayout, binding.mainToolbar, R.string.drawer_open, R.string.drawer_close)
        binding.mainLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.mainMenu.setNavigationItemSelectedListener(this)
        furnishMenu()
        if (savedInstanceState == null) { // Loads only the first time, not rotating the screen
            val fragment: BaseFragment = if (intent.getBooleanExtra(Choice.PERSON, false)) PersonsFragment()
            else if (intent.getBooleanExtra(Choice.MEDIA, false)) GalleryFragment()
            else if (intent.getBooleanExtra(Choice.NOTE, false)) NotesFragment()
            else if (intent.getBooleanExtra(Choice.SOURCE, false)) SourcesFragment()
            else if (intent.getBooleanExtra(Choice.REPOSITORY, false)) RepositoriesFragment()
            else DiagramFragment() // Regular opening
            showFragment(fragment)
        }
        binding.mainMenu.getHeaderView(0).findViewById<View>(R.id.menuHeader_trees).setOnClickListener {
            binding.mainLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this@MainActivity, TreesActivity::class.java))
        }

        // Hides some menu items for non-expert users
        if (!Global.settings.expert) {
            val menu = binding.mainMenu.menu
            menu.findItem(R.id.menu_sources).isVisible = false
            menu.findItem(R.id.menu_repositories).isVisible = false
            menu.findItem(R.id.menu_submitters).isVisible = false
        }

        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                binding.mainToolbar.apply {
                    visibility = if (frontFragment is DiagramFragment) View.GONE else View.VISIBLE
                    post { // Because sometimes onCreateMenu() is called before the fragment creation
                        menu.clear()
                        if (Global.gc != null) frontFragment.updateToolbar(supportActionBar!!, menu, inflater)
                    }
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                frontFragment.selectItem(menuItem.itemId)
                return true
            }
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.mainLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.mainLayout.closeDrawer(GravityCompat.START)
                } else {
                    if (manager.fragments.size <= 1) {
                        finish() // Goes back to TreesActivity
                    } else {
                        // Removes the last visible fragment from the backstack (and TreeSettingsFragment also)
                        var last = true
                        for (fragment in manager.fragments.asReversed()) {
                            if (last || fragment is TreeSettingsFragment) {
                                do {
                                    manager.popBackStackImmediate()
                                } while (fragment.isVisible)
                                last = false
                            } else {
                                _frontFragment = fragment as BaseFragment
                                break
                            }
                        }
                        refreshToolbar()
                        selectMenuItem()
                        frontFragment.onResume() // In case there was editing
                    }
                }
            }
        })
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // If we click Diagram and diagram is already displayed, shows the root person, possibly asking about multiple parents
        if (item.itemId == R.id.menu_diagram && isCurrentFragment(DiagramFragment::class.java)) {
            Global.indi = Global.settings.currentTree.root
            val openDiagram = Runnable { U.whichParentsToShow(this, Global.gc.getPerson(Global.indi), 1) }
            if (TreeUtil.isGlobalGedcomOk(openDiagram)) openDiagram.run()
        } else {
            val fragmentClass = fragments[menuIds.indexOf(item.itemId)]
            val existingFragment = manager.findFragmentByTag(fragmentClass.simpleName) as BaseFragment?
            if (existingFragment != null) { // Recycles a fragment already existing in the back stack
                manager.beginTransaction().remove(existingFragment).commit()
                showFragment(existingFragment)
            } else {
                val fragment = fragmentClass.getDeclaredConstructor().newInstance() as BaseFragment
                showFragment(fragment)
            }
        }
        binding.mainLayout.closeDrawer(GravityCompat.START)
        return true
    }

    /**
     * Receives a class like 'DiagramFragment.class' and says whether it is the fragment currently visible.
     */
    private fun isCurrentFragment(aClass: Class<*>): Boolean {
        return aClass.isInstance(manager.findFragmentById(R.id.main_fragment))
    }

    /**
     * Displays a fragment in the main view.
     */
    fun showFragment(fragment: BaseFragment) {
        manager.beginTransaction().add(R.id.main_fragment, fragment, fragment.javaClass.simpleName).addToBackStack(null).commit()
        _frontFragment = fragment
        refreshToolbar()
        selectMenuItem()
    }

    /**
     * Highlights one item of the main menu.
     */
    private fun selectMenuItem() {
        val fragmentPosition = fragments.indexOf(frontFragment.javaClass)
        binding.mainMenu.setCheckedItem(menuIds[fragmentPosition])
    }

    private fun refreshToolbar() {
        invalidateMenu()
    }

    /**
     * Updates both toolbar and main menu.
     */
    fun refreshInterface() {
        if (!frontFragment.isSearching()) refreshToolbar()
        furnishMenu()
    }

    /**
     * Updates title, random image, 'Save' button in menu header, and menu items count.
     */
    fun furnishMenu() {
        val menuHeader = binding.mainMenu.getHeaderView(0)
        val imageView = menuHeader.findViewById<ImageView>(R.id.menuHeader_image)
        val mainTitle = menuHeader.findViewById<TextView>(R.id.menuHeader_title)
        imageView.visibility = ImageView.GONE
        mainTitle.text = ""
        if (Global.gc != null) {
            val mediaList = MediaList(Global.gc)
            Global.gc.accept(mediaList)
            mediaList.getRandomPreviewMedia()?.let { media ->
                showImage(media, imageView, Image.DARK)
                // TODO Visible and clickable only if not Type.PLACEHOLDER nor Type.DOCUMENT
                imageView.visibility = ImageView.VISIBLE
                imageView.setOnClickListener {
                    FindStack(Global.gc, media, true)
                    val intent = Intent(this, MediaActivity::class.java)
                    if (media.id == null) intent.putExtra(Extra.ALONE, true) // Simple Media always display cabinet
                    startActivity(intent)
                }
            }
            mainTitle.text = Global.settings.currentTree.title
            mainTitle.setOnClickListener {
                TreeUtil.renameTree(this, Global.settings.openTree) { refreshInterface() }
            }
            if (Global.settings.expert) {
                val treeNumView = menuHeader.findViewById<TextView>(R.id.menuHeader_number)
                treeNumView.text = "${Global.settings.openTree}"
                treeNumView.visibility = ImageView.VISIBLE
            }
            // Puts count of existing records in menu items
            for (i in 1..7) {
                var count = 0
                when (i) {
                    1 -> count = Global.gc.people.size
                    2 -> count = Global.gc.families.size
                    3 -> {
                        val mediaList1 = MediaList(Global.gc)
                        Global.gc.accept(mediaList1)
                        count = mediaList1.list.size
                    }
                    4 -> {
                        val noteList = NoteList()
                        Global.gc.accept(noteList)
                        count = noteList.noteList.size + Global.gc.notes.size
                    }
                    5 -> count = Global.gc.sources.size
                    6 -> count = Global.gc.repositories.size
                    7 -> count = Global.gc.submitters.size
                }
                val countView = binding.mainMenu.menu.getItem(i).actionView!!.findViewById<TextView>(R.id.menu_item_text)
                countView.text = if (count > 0) count.toString() else ""
            }
        }
        // Save button
        val saveButton = menuHeader.findViewById<Button>(R.id.menuHeader_save)
        saveButton.setOnClickListener { button ->
            button.isEnabled = false
            binding.mainLayout.closeDrawer(GravityCompat.START)
            GlobalScope.launch(Dispatchers.IO) {
                TreeUtil.saveJson(Global.gc, Global.settings.openTree)
                Global.shouldSave = false
                withContext(Dispatchers.Main) {
                    button.visibility = View.GONE
                    Toast.makeText(this@MainActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                }
            }
        }
        saveButton.setOnLongClickListener { button ->
            val popup = PopupMenu(this, button)
            popup.menu.add(0, 0, 0, R.string.revert)
            popup.show()
            popup.setOnMenuItemClickListener { item: MenuItem ->
                if (item.itemId == 0) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        TreeUtil.openGedcom(Global.settings.openTree, false)
                        Global.edited = false
                        withContext(Dispatchers.Main) {
                            while (manager.backStackEntryCount > 0) {
                                manager.popBackStackImmediate()
                            }
                            showFragment(DiagramFragment())
                            binding.mainLayout.closeDrawer(GravityCompat.START)
                            furnishMenu()
                        }
                    }
                }
                true
            }
            true
        }
        saveButton.visibility = if (Global.shouldSave) {
            saveButton.isEnabled = true
            View.VISIBLE
        } else View.GONE
    }
}
