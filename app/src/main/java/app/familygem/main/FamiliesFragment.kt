package app.familygem.main

import android.content.Intent
import android.os.Bundle
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import app.familygem.Global
import app.familygem.Memory
import app.familygem.R
import app.familygem.U
import app.familygem.databinding.RecyclerviewBinding
import app.familygem.detail.FamilyActivity
import app.familygem.util.FamilyUtil
import app.familygem.util.TreeUtil
import app.familygem.util.Util
import app.familygem.util.delete
import app.familygem.util.writeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.folg.gedcom.model.Family

/**
 * List of all families of the tree.
 */
class FamiliesFragment : BaseFragment(R.layout.recyclerview) {

    private lateinit var binding: RecyclerviewBinding
    private val allFamilies = ArrayList<FamilyWrapper>()
    val filteredFamilies = ArrayList<FamilyWrapper>()
    private val adapter = FamiliesAdapter(this)
    private lateinit var searchView: SearchView
    private var prepareJob: Job? = null
    private var idsAreNumeric = false
    var order = Order.NONE

    enum class Order {
        NONE, ID_ASC, ID_DESC, SURNAME_ASC, SURNAME_DESC, MEMBERS_ASC, MEMBERS_DESC;

        fun next(): Order {
            return Order.values()[ordinal + 1]
        }

        fun prev(): Order {
            return Order.values()[ordinal - 1]
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = RecyclerviewBinding.inflate(inflater, container, false)
        binding.recyclerView.adapter = adapter
        // FAB
        binding.recyclerFab.root.setOnClickListener {
            val newFamily = FamilyUtil.newFamily() // TODO: Crashes if Global.gc is null
            TreeUtil.save(true, newFamily)
            Memory.setLeader(newFamily)
            startActivity(Intent(context, FamilyActivity::class.java))
        }
        setupFastScroller(binding.recyclerView)
        return binding.root
    }

    override fun showContent() {
        binding.recyclerWheel.root.visibility = View.VISIBLE
        prepareJob = lifecycleScope.launch(Dispatchers.Default) {
            allFamilies.clear()
            Global.gc.families.forEach { allFamilies.add(FamilyWrapper(it)) }
            filteredFamilies.clear()
            filteredFamilies.addAll(allFamilies)
            withContext(Dispatchers.Main) { adapter.notifyDataSetChanged() }
            idsAreNumeric = verifyIdsAreNumeric()
            allFamilies.forEach { it.completeFields() } // This could be time-consuming
            withContext(Dispatchers.Main) { binding.recyclerWheel.root.visibility = View.GONE }
        }
    }

    override fun updateToolbar(bar: ActionBar, menu: Menu, inflater: MenuInflater) {
        val total = Global.gc.families.size
        bar.title = "$total ${Util.caseString(if (total == 1) R.string.family else R.string.families)}"
        if (total > 1) {
            // Search view
            inflater.inflate(R.menu.search, menu)
            searchView = menu.findItem(R.id.search_item).actionView as SearchView
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextChange(query: String): Boolean {
                    filterFamilies(query)
                    return true
                }

                override fun onQueryTextSubmit(query: String?): Boolean {
                    searchView.clearFocus()
                    return false
                }
            })
            // Sort by button
            inflater.inflate(R.menu.sort_by, menu)
            val subMenu = menu.findItem(R.id.sortBy).subMenu
            if (Global.settings.expert) subMenu!!.add(0, 1, 0, R.string.id)
            subMenu!!.add(0, 2, 0, R.string.surname)
            subMenu.add(0, 3, 0, R.string.number_members)
        }
    }

    /**
     * Searches all the words of the query and displays filtered families.
     */
    private fun filterFamilies(query: String) {
        val queries = query.trim().lowercase().split("\\s+".toRegex())
        filteredFamilies.clear()
        if (queries[0].isEmpty()) {
            filteredFamilies.addAll(allFamilies)
        } else {
            outer@ for (wrapper in allFamilies) {
                for (word in queries) {
                    if (!wrapper.text.contains(word)) continue@outer
                }
                filteredFamilies.add(wrapper)
            }
        }
        adapter.notifyDataSetChanged()
    }

    override fun selectItem(id: Int) {
        if (id in 1..3) {
            if (prepareJob != null && prepareJob!!.isCompleted) {
                // Clicking twice the same menu item switches sorting ASC and DESC
                order = when (order) {
                    Order.values()[id * 2 - 1] -> order.next()
                    Order.values()[id * 2] -> order.prev()
                    else -> Order.values()[id * 2 - 1]
                }
                sortFamilies()
                filterFamilies(searchView.query.toString())
                adapter.notifyDataSetChanged()
            } else Toast.makeText(context, "Please wait.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Checks if all family IDs contain numbers.
     * As soon as an ID contains only letters it returns false.
     */
    private fun verifyIdsAreNumeric(): Boolean {
        outer@ for (family in Global.gc.families) {
            for (character in family.id.toCharArray()) {
                if (Character.isDigit(character)) continue@outer
            }
            return false
        }
        return true
    }

    private fun sortFamilies() {
        allFamilies.sortWith(Comparator { f1: FamilyWrapper, f2: FamilyWrapper ->
            when (order) {
                // Sorts by ID
                Order.ID_ASC -> if (idsAreNumeric) return@Comparator U.extractNum(f1.id) - U.extractNum(f2.id)
                else return@Comparator f1.id.compareTo(f2.id, ignoreCase = true)
                Order.ID_DESC -> if (idsAreNumeric) return@Comparator U.extractNum(f2.id) - U.extractNum(f1.id)
                else return@Comparator f2.id.compareTo(f1.id, ignoreCase = true)
                // Sorts by surname
                Order.SURNAME_ASC -> {
                    if (f1.lowerSurname == null) // null names go to the bottom
                        return@Comparator if (f2.lowerSurname == null) 0 else 1
                    if (f2.lowerSurname == null) return@Comparator -1
                    return@Comparator f1.lowerSurname!!.compareTo(f2.lowerSurname!!)
                }
                Order.SURNAME_DESC -> {
                    if (f1.lowerSurname == null)
                        return@Comparator if (f2.lowerSurname == null) 0 else 1
                    if (f2.lowerSurname == null) return@Comparator -1
                    return@Comparator f2.lowerSurname!!.compareTo(f1.lowerSurname!!)
                }
                // Sorts by number of family members
                Order.MEMBERS_ASC -> return@Comparator f1.members - f2.members
                Order.MEMBERS_DESC -> return@Comparator f2.members - f1.members
                else -> return@Comparator 0
            }
        })
        // Updates families sorting in global GEDCOM
        val sortedFamilies = allFamilies.map { it.family }
        if (sortedFamilies != Global.gc.families) {
            Global.gc.families = sortedFamilies
            TreeUtil.save(false) // Immediately saves families sorting
            if (Global.shouldSave) (requireActivity() as MainActivity).furnishMenu() // Displays the Save button
        }
    }

    private lateinit var selected: Family

    override fun onCreateContextMenu(menu: ContextMenu, view: View, info: ContextMenuInfo?) {
        selected = view.getTag(R.id.tag_family) as Family
        if (Global.settings.expert) menu.add(2, 0, 0, R.string.edit_id)
        menu.add(2, 1, 0, R.string.delete)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (item.groupId == 2) {
            when (item.itemId) {
                0 -> { // Edit ID
                    U.editId(context, selected) { showContent() }
                    return true
                }
                1 -> { // Delete
                    val deleteFamily = {
                        selected.delete()
                        showContent()
                        (requireActivity() as MainActivity).refreshInterface()
                    }
                    if (selected.husbandRefs.size + selected.wifeRefs.size + selected.childRefs.size > 0) {
                        AlertDialog.Builder(requireContext()).setMessage(R.string.really_delete_family)
                                .setPositiveButton(android.R.string.ok) { _, _ -> deleteFamily() }
                                .setNeutralButton(android.R.string.cancel, null).show()
                    } else {
                        deleteFamily()
                    }
                    return true
                }
            }
        }
        return false
    }

    /**
     * Container of family and fields relevant for displaying, sorting and filtering.
     */
    inner class FamilyWrapper(val family: Family) {
        val parents: String
        val children: String
        val id: String
        val members: Int
        var originalSurname: String? = null
        var lowerSurname: String? = null // Surname lowercase for comparison
        var text = "" // For searching

        init {
            parents = writeParents()
            children = writeChildren()
            id = family.id
            members = family.husbandRefs.size + family.wifeRefs.size + family.childRefs.size
        }

        private fun writeParents(): String {
            var parents = StringBuilder()
            for (husband in family.getHusbands(Global.gc)) parents.append(U.properName(husband)).append(",\n")
            for (wife in family.getWives(Global.gc)) parents.append(U.properName(wife)).append(",\n")
            if (parents.isNotEmpty()) parents = StringBuilder(parents.substring(0, parents.length - 2)) // Just to remove the final ',\n'
            return parents.toString()
        }

        private fun writeChildren(): String {
            var children = java.lang.StringBuilder()
            for (child in family.getChildren(Global.gc)) children.append(U.properName(child)).append(",\n")
            if (children.isNotEmpty()) children = java.lang.StringBuilder(children.substring(0, children.length - 2))
            return children.toString()
        }

        private fun findSurname(): String? {
            return if (family.getHusbands(Global.gc).isNotEmpty()) U.surname(family.getHusbands(Global.gc)[0])
            else if (family.getWives(Global.gc).isNotEmpty()) U.surname(family.getWives(Global.gc)[0])
            else if (family.getChildren(Global.gc).isNotEmpty()) U.surname(family.getChildren(Global.gc)[0])
            else null
        }

        fun completeFields() {
            // Surname
            originalSurname = findSurname()
            lowerSurname = originalSurname?.lowercase()
            // Searchable text
            val builder = StringBuilder()
            if (Global.settings.expert) builder.append(id).append(' ')
            family.getHusbands(Global.gc).forEach { builder.append(U.properName(it)).append(' ') }
            family.getWives(Global.gc).forEach { builder.append(U.properName(it)).append(' ') }
            family.getChildren(Global.gc).forEach { builder.append(U.properName(it)).append(' ') }
            family.eventsFacts.filterNot { it.value == "Y" }.forEach { builder.append(it.writeContent()).append(' ') } // This could be time-consuming
            text = builder.toString().lowercase()
        }
    }
}
