package app.familygem.main

import android.app.Activity
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
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import app.familygem.Global
import app.familygem.Memory
import app.familygem.ProgressView
import app.familygem.R
import app.familygem.U
import app.familygem.constant.Choice
import app.familygem.constant.Extra
import app.familygem.constant.Image
import app.familygem.detail.SourceActivity
import app.familygem.util.FileUtil.showImage
import app.familygem.util.SourceUtil
import app.familygem.util.TreeUtil
import app.familygem.util.Util
import app.familygem.util.getMainText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.folg.gedcom.model.ExtensionContainer
import org.folg.gedcom.model.Note
import org.folg.gedcom.model.NoteContainer
import org.folg.gedcom.model.Source
import org.folg.gedcom.model.SourceCitationContainer
import java.util.Locale

/** List of all sources of the tree, searchable and sortable. */
class SourcesFragment : BaseFragment() {
    private var allWrappers = mutableListOf<SourceWrapper>()
    private var selectedWrappers = mutableListOf<SourceWrapper>()
    private lateinit var adapter: SourcesAdapter
    private lateinit var progress: ProgressView
    private var searchView: SearchView? = null
    private val citationCount = mutableMapOf<String, Int>() // Source ID, citation count
    private var order = Order.NONE

    private enum class Order {
        NONE,
        ID_ASC, ID_DESC,
        TITLE_ASC, TITLE_DESC,
        CITATIONS_ASC, CITATIONS_DESC;

        fun next(): Order {
            return entries[ordinal + 1]
        }

        fun prev(): Order {
            return entries[ordinal - 1]
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        val view = inflater.inflate(R.layout.recyclerview, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
        adapter = SourcesAdapter()
        recyclerView.setAdapter(adapter)
        progress = view.findViewById(R.id.recycler_progress)
        setInterfacer(view.findViewById(R.id.recycler_fab), recyclerView, true)
        view.findViewById<View>(R.id.fab).setOnClickListener { SourceUtil.createSource(requireContext()) }
        return view
    }

    override fun showContent() {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.Default) {
            allWrappers.clear()
            selectedWrappers.clear()
            countSourceCitations()
            allWrappers = Global.gc.sources.map {
                SourceWrapper(it, U.extractNum(it.id), it.getMainText(), citationCount[it.id] ?: 0, getSearchText(it))
            }.toMutableList()
            selectedWrappers = allWrappers
            // Family Gem 1.3 removed the "citaz" extension from sources
            // TODO remove this loop on a future release
            for (source in Global.gc.sources) {
                source.extensions.remove("citaz")
                if (source.extensions.isEmpty()) source.extensions = null
            }
            launch(Dispatchers.Main) {
                adapter.notifyDataSetChanged()
                adapter.filter.filter(searchView?.query ?: "")
                progress.visibility = View.GONE
            }
        }
    }

    override fun isSearching(): Boolean {
        return searchView != null && searchView!!.query.length > 0
    }

    inner class SourcesAdapter : RecyclerView.Adapter<SourceHolder?>(), Filterable {
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): SourceHolder {
            val sourceView = LayoutInflater.from(parent.context).inflate(R.layout.sources_item, parent, false)
            registerForContextMenu(sourceView)
            return SourceHolder(sourceView)
        }

        override fun onBindViewHolder(holder: SourceHolder, position: Int) {
            val wrapper = selectedWrappers[position]
            holder.apply {
                itemView.setTag(R.id.source_id, wrapper.source.id)
                idView.text = wrapper.source.id
                idView.visibility = if (order == Order.ID_ASC || order == Order.ID_DESC) View.VISIBLE else View.GONE
                titleView.text = wrapper.mainTitle
                numView.text = wrapper.citations.toString()
                // Media logic
                val media = wrapper.source.getAllMedia(Global.gc)
                val params = titleView.layoutParams as RelativeLayout.LayoutParams
                if (media.isEmpty()) {
                    imageView.visibility = View.GONE
                    params.addRule(RelativeLayout.START_OF, R.id.source_num)
                } else {
                    showImage(media[0], imageView, Image.SOURCE)
                    imageView.visibility = View.VISIBLE
                    params.addRule(RelativeLayout.START_OF, R.id.source_image)
                }
            }
        }

        override fun getFilter(): Filter {
            return object : Filter() {
                override fun performFiltering(charSequence: CharSequence): FilterResults {
                    val queryStr = charSequence.trim().toString().lowercase(Locale.getDefault())
                    val queryWords = queryStr.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                    selectedWrappers = if (queryWords.isEmpty()) {
                        allWrappers
                    } else {
                        allWrappers.filter { wrapper ->
                            queryWords.all { word -> wrapper.searchText.contains(word) }
                        }.toMutableList()
                    }
                    sortSources()
                    return FilterResults().apply { values = selectedWrappers }
                }

                override fun publishResults(cs: CharSequence?, fr: FilterResults?) {
                    notifyDataSetChanged()
                }
            }
        }

        override fun getItemCount(): Int {
            return selectedWrappers.size
        }
    }

    inner class SourceHolder(view: View) : RecyclerView.ViewHolder(view), View.OnClickListener {
        val idView: TextView = view.findViewById(R.id.source_id)
        val titleView: TextView = view.findViewById(R.id.source_title)
        val imageView: ImageView = view.findViewById(R.id.source_image)
        val numView: TextView = view.findViewById(R.id.source_num)

        init {
            view.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            val id = itemView.getTag(R.id.source_id) as? String
            // Returns a source ID to ProfileActivity or to DetailActivity
            if (requireActivity().intent.getBooleanExtra(Choice.SOURCE, false)) {
                val intent = Intent()
                intent.putExtra(Extra.SOURCE_ID, id)
                requireActivity().setResult(Activity.RESULT_OK, intent)
                requireActivity().finish()
            } else { // Regular source opening
                val source = Global.gc.getSource(id)
                Memory.setLeader(source)
                startActivity(Intent(context, SourceActivity::class.java))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        requireActivity().intent.removeExtra(Choice.SOURCE)
    }

    /** Composes a string with all the content of a source to be searched. */
    private fun getSearchText(source: Source): String {
        return source.run {
            val builder = StringBuilder()
            builder.append(id).append(' ')
            if (abbreviation != null) builder.append(abbreviation).append(' ')
            if (title != null) builder.append(title).append(' ')
            if (author != null) builder.append(author).append(' ')
            if (text != null) builder.append(text).append(' ')
            if (type != null) builder.append(type).append(' ')
            if (date != null) builder.append(date).append(' ')
            if (publicationFacts != null) builder.append(publicationFacts).append(' ')
            if (callNumber != null) builder.append(callNumber).append(' ')
            for (note in notes) if (note.value != null) builder.append(note.value).append(' ')
            for (media in media) if (media.file != null) builder.append(media.file).append(' ')
            builder.toString().lowercase(Locale.getDefault())
        }
    }

    private fun sortSources() {
        if (order != Order.NONE) {
            selectedWrappers.sortWith { w1, w2 ->
                return@sortWith when (order) {
                    Order.ID_ASC -> w1.id - w2.id
                    Order.ID_DESC -> w2.id - w1.id
                    Order.TITLE_ASC -> w1.mainTitle.compareTo(w2.mainTitle, true)
                    Order.TITLE_DESC -> w2.mainTitle.compareTo(w1.mainTitle, true)
                    Order.CITATIONS_ASC -> w1.citations - w2.citations
                    Order.CITATIONS_DESC -> w2.citations - w1.citations
                    else -> 0
                }
            }
        }
    }

    /** Populates [citationCount] with the number of citations for each source. */
    private fun countSourceCitations() {
        citationCount.clear()
        for (person in Global.gc.people) {
            count(person)
            for (name in person.names) count(name)
            for (fact in person.eventsFacts) count(fact)
        }
        for (family in Global.gc.families) {
            count(family)
            for (fact in family.eventsFacts) count(fact)
        }
        for (source in Global.gc.sources) {
            for (note in source.notes) count(note)
        }
        for (note in Global.gc.notes) count(note)
    }

    private fun count(container: ExtensionContainer) {
        if (container is Note) container.sourceCitations
        else {
            for (note in (container as NoteContainer).notes) count(note)
            for (citation in (container as SourceCitationContainer).sourceCitations) {
                for (note in citation.notes) count(note)
            }
            container.sourceCitations
        }.map { it.ref }.forEach { citationCount[it] = (citationCount[it] ?: 0) + 1 }
    }

    override fun updateToolbar(bar: ActionBar, menu: Menu, inflater: MenuInflater) {
        bar.title = Global.gc.sources.size.toString() + " " +
                Util.caseString(if (Global.gc.sources.size == 1) R.string.source else R.string.sources)
        if (Global.gc.sources.size > 1) {
            // Search in SourcesFragment
            inflater.inflate(R.menu.search, menu)
            searchView = menu.findItem(R.id.search_item).actionView as SearchView?
            stylizeSearchView(searchView)
            searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextChange(query: String?): Boolean {
                    adapter.filter.filter(query)
                    return true
                }

                override fun onQueryTextSubmit(q: String?): Boolean {
                    searchView?.clearFocus()
                    return false
                }
            })
            // Sort-by menu
            inflater.inflate(R.menu.sort_by, menu)
            val subMenu = menu.findItem(R.id.sortBy).subMenu
            if (Global.settings.expert) subMenu!!.add(0, 1, 0, R.string.id)
            subMenu!!.add(0, 2, 0, R.string.title)
            subMenu.add(0, 3, 0, R.string.citations)
        }
    }

    override fun selectItem(id: Int) {
        if (id in 1..3) {
            order = if (order == Order.entries[id * 2 - 1]) order.next()
            else if (order == Order.entries[id * 2]) order.prev()
            else Order.entries[id * 2 - 1]
            sortSources()
            adapter.notifyDataSetChanged()
            if (selectedWrappers.size == Global.gc.sources.size) { // Only if there is no filtering
                Global.gc.sources = selectedWrappers.map { it.source }
                TreeUtil.save(false)
                if (!Global.settings.autoSave) (requireActivity() as MainActivity).furnishMenu() // Displays the Save button
            }
        }
    }

    private var source: Source? = null

    override fun onCreateContextMenu(menu: ContextMenu, view: View, info: ContextMenuInfo?) {
        source = Global.gc.getSource((view.getTag(R.id.source_id) as? String))
        if (Global.settings.expert) menu.add(5, 0, 0, R.string.edit_id)
        menu.add(5, 1, 0, R.string.delete)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (item.groupId == 5) {
            if (item.itemId == 0) { // Edit source ID
                U.editId(context, source) { this.showContent() }
            } else if (item.itemId == 1) { // Delete source
                Util.confirmDelete(requireContext()) {
                    val objects: Array<Any?> = SourceUtil.deleteSource(source!!)
                    TreeUtil.save(false, *objects)
                    showContent()
                    (requireActivity() as MainActivity).refreshInterface()
                }
            }
            return true
        }
        return false
    }
}

data class SourceWrapper(val source: Source, val id: Int, val mainTitle: String, val citations: Int, val searchText: String)
