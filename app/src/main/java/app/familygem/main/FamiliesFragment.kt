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
import app.familygem.util.getSpouses
import app.familygem.util.writeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.folg.gedcom.model.Family
import kotlin.concurrent.timer

/** List of all families of the tree. */
class FamiliesFragment : BaseFragment(R.layout.recyclerview) {

    private lateinit var binding: RecyclerviewBinding
    private val allFamilies = ArrayList<FamilyWrapper>()
    val filteredFamilies = ArrayList<FamilyWrapper>()
    private val adapter = FamiliesAdapter(this)
    private var searchView: SearchView? = null
    private var idsAreNumeric = false
    var order = Order.NONE

    enum class Order {
        NONE, ID_ASC, ID_DESC, SURNAME_ASC, SURNAME_DESC, MEMBERS_ASC, MEMBERS_DESC;

        fun next(): Order {
            return entries[ordinal + 1]
        }

        fun prev(): Order {
            return entries[ordinal - 1]
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = RecyclerviewBinding.inflate(inflater, container, false)
        binding.recyclerView.adapter = adapter
        // FAB
        binding.recyclerFab.root.setOnClickListener {
            val newFamily = FamilyUtil.createNewFamily() // TODO: Crashes if Global.gc is null
            TreeUtil.save(true, newFamily)
            Memory.setLeader(newFamily)
            startActivity(Intent(context, FamilyActivity::class.java))
        }
        setupFastScroller(binding.recyclerView)
        return binding.root
    }

    override fun showContent() {
        binding.recyclerProgress.visibility = View.VISIBLE
        val prepareJob = lifecycleScope.launch(Dispatchers.Default) {
            allFamilies.clear()
            Global.gc.families.forEach { allFamilies.add(FamilyWrapper(it)) }
            idsAreNumeric = verifyIdsAreNumeric()
            allFamilies.forEach { it.completeFields() } // This could be time-consuming
        }
        timer(period = 800) {
            lifecycleScope.launch(Dispatchers.Main) {
                filterFamilies(searchView?.query ?: "")
                sortFamilies()
                adapter.notifyDataSetChanged()
                if (prepareJob.isCompleted) {
                    binding.recyclerProgress.visibility = View.GONE
                    cancel()
                }
            }
        }
    }

    override fun updateToolbar(bar: ActionBar, menu: Menu, inflater: MenuInflater) {
        val total = Global.gc.families.size
        bar.title = "$total ${Util.caseString(if (total == 1) R.string.family else R.string.families)}"
        if (total > 1) {
            // Search view
            inflater.inflate(R.menu.search, menu)
            searchView = menu.findItem(R.id.search_item).actionView as SearchView
            searchView!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextChange(query: String): Boolean {
                    filterFamilies(query)
                    adapter.notifyDataSetChanged()
                    return true
                }

                override fun onQueryTextSubmit(query: String?): Boolean {
                    searchView!!.clearFocus()
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

    override fun isSearching(): Boolean {
        return searchView != null && searchView!!.query.isNotBlank()
    }

    /**
     * Searches all the words of the query and displays filtered families.
     */
    private fun filterFamilies(query: CharSequence) {
        val queries = query.trim().toString().lowercase().split("\\s+".toRegex()).dropWhile { it.isBlank() }
        filteredFamilies.clear()
        if (queries.isEmpty()) {
            filteredFamilies.addAll(allFamilies)
        } else {
            outer@ for (wrapper in allFamilies) {
                for (word in queries) {
                    if (!wrapper.text.contains(word)) continue@outer
                }
                filteredFamilies.add(wrapper)
            }
        }
    }

    override fun selectItem(id: Int) {
        if (id in 1..3) {
            // Clicking twice the same menu item switches sorting ASC and DESC
            order = when (order) {
                Order.entries[id * 2 - 1] -> order.next()
                Order.entries[id * 2] -> order.prev()
                else -> Order.entries[id * 2 - 1]
            }
            sortFamilies()
            adapter.notifyDataSetChanged()
            // Updates families sorting in global GEDCOM
            if (filteredFamilies.size == Global.gc.families.size) {
                Global.gc.families = filteredFamilies.map { it.family }
                TreeUtil.save(false) // Immediately saves families sorting
                if (Global.shouldSave) (requireActivity() as MainActivity).furnishMenu() // Displays the Save button
            }
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
        filteredFamilies.sortWith { f1: FamilyWrapper, f2: FamilyWrapper ->
            return@sortWith when (order) {
                // Sorts by ID
                Order.ID_ASC -> if (idsAreNumeric) U.extractNum(f1.id) - U.extractNum(f2.id)
                else f1.id.compareTo(f2.id, ignoreCase = true)
                Order.ID_DESC -> if (idsAreNumeric) U.extractNum(f2.id) - U.extractNum(f1.id)
                else f2.id.compareTo(f1.id, ignoreCase = true)
                // Sorts by surname
                Order.SURNAME_ASC -> {
                    if (f1.lowerSurname == null && f2.lowerSurname == null) 0
                    else if (f1.lowerSurname == null) 1 // null names go to the bottom
                    else if (f2.lowerSurname == null) -1
                    else f1.lowerSurname!!.compareTo(f2.lowerSurname!!)
                }
                Order.SURNAME_DESC -> {
                    if (f1.lowerSurname == null && f2.lowerSurname == null) 0
                    else if (f1.lowerSurname == null) 1
                    else if (f2.lowerSurname == null) -1
                    else f2.lowerSurname!!.compareTo(f1.lowerSurname!!)
                }
                // Sorts by number of family members
                Order.MEMBERS_ASC -> f1.members - f2.members
                Order.MEMBERS_DESC -> f2.members - f1.members
                else -> 0
            }
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
                        Util.confirmDelete(requireContext()) { deleteFamily() }
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
            family.getSpouses().forEach { parents.append(U.properName(it)).append(",\n") }
            if (parents.isNotEmpty()) parents = StringBuilder(parents.substring(0, parents.length - 2)) // Just to remove the final ',\n'
            return parents.toString()
        }

        private fun writeChildren(): String {
            var children = java.lang.StringBuilder()
            family.getChildren(Global.gc).forEach { children.append(U.properName(it)).append(",\n") }
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
            family.eventsFacts.filterNot { it.value == "Y" }.forEach { builder.append(it.writeContent()).append(' ') } // Time-consuming
            text = builder.toString().lowercase()
        }
    }
}
