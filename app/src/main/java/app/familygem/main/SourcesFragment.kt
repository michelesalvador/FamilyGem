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
import app.familygem.detail.SourceCitationActivity
import app.familygem.util.FileUtil.showImage
import app.familygem.util.SourceUtil
import app.familygem.util.TreeUtil
import app.familygem.util.Util
import app.familygem.util.getMainText
import app.familygem.visitor.FindStack
import app.familygem.visitor.NoteSourcesList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.folg.gedcom.model.ExtensionContainer
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.Note
import org.folg.gedcom.model.NoteContainer
import org.folg.gedcom.model.Source
import org.folg.gedcom.model.SourceCitation
import org.folg.gedcom.model.SourceCitationContainer
import java.util.Locale

/** List of all sources of the tree, searchable and sortable. */
class SourcesFragment : BaseFragment() {
    private var allWrappers = mutableListOf<SourceWrapper>()
    private var selectedWrappers = mutableListOf<SourceWrapper>()
    private lateinit var adapter: SourcesAdapter
    private lateinit var progress: ProgressView
    private var prepareJob: Job? = null
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
        prepareJob = lifecycleScope.launch(Dispatchers.Default) {
            countSourceCitations()
            allWrappers = Global.gc.sources.map {
                SourceWrapper(it, null, U.extractNum(it.id), it.getMainText(), citationCount[it.id] ?: 0, getSearchText(it))
            }.toMutableList()
            if (!requireActivity().intent.getBooleanExtra(Choice.SOURCE, false)) {
                val noteSources = NoteSourcesList()
                Global.gc.accept(noteSources)
                allWrappers.addAll(noteSources.list.map {
                    SourceWrapper(noteSource = it, displayText = it.getMainText(), searchText = getSearchText(it))
                })
            }
            // Family Gem 1.3 removed the "citaz" extension from sources
            // TODO remove this loop on a future release
            for (source in Global.gc.sources) {
                source.extensions.remove("citaz")
                if (source.extensions.isEmpty()) source.extensions = null
            }
            withContext(Dispatchers.Main) {
                adapter.filter.filter(searchView?.query ?: "")
                if (!isSearching()) activity?.invalidateOptionsMenu()
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
                val media = if (wrapper.source != null) {
                    itemView.setTag(R.id.tag_object, wrapper.source.id)
                    idView.text = wrapper.source.id
                    idView.visibility = if (order == Order.ID_ASC || order == Order.ID_DESC) View.VISIBLE else View.GONE
                    textView.text = wrapper.displayText
                    numView.text = wrapper.citations.toString()
                    numView.visibility = View.VISIBLE
                    wrapper.source.getAllMedia(Global.gc)
                } else if (wrapper.noteSource != null) {
                    itemView.setTag(R.id.tag_object, wrapper.noteSource)
                    idView.visibility = View.GONE
                    textView.text = wrapper.displayText
                    numView.visibility = View.GONE
                    wrapper.noteSource.getAllMedia(Global.gc)
                } else emptyList<Media>()
                // Media logic
                val params = textView.layoutParams as RelativeLayout.LayoutParams
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
        val textView: TextView = view.findViewById(R.id.source_title)
        val imageView: ImageView = view.findViewById(R.id.source_image)
        val numView: TextView = view.findViewById(R.id.source_num)

        init {
            view.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            val id = itemView.getTag(R.id.tag_object) as? String
            val citation = itemView.getTag(R.id.tag_object) as? SourceCitation
            // Returns a source ID to ProfileActivity or to DetailActivity
            if (requireActivity().intent.getBooleanExtra(Choice.SOURCE, false)) {
                val intent = Intent()
                intent.putExtra(Extra.SOURCE_ID, id)
                requireActivity().setResult(Activity.RESULT_OK, intent)
                requireActivity().finish()
            } else if (id != null) { // Regular source opening
                val source = Global.gc.getSource(id)
                Memory.setLeader(source)
                startActivity(Intent(context, SourceActivity::class.java))
            } else if (citation != null) { // Note-source opening
                FindStack(Global.gc, citation, true)
                startActivity(Intent(context, SourceCitationActivity::class.java).putExtra("fromSources", true))
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
            if (Global.settings.expert) builder.append(id).append(' ')
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

    private fun getSearchText(sourceCitation: SourceCitation): String {
        return sourceCitation.run {
            val builder = StringBuilder()
            if (value != null) builder.append(value).append(' ')
            if (page != null) builder.append(page).append(' ')
            if (date != null) builder.append(date).append(' ')
            if (text != null) builder.append(text).append(' ')
            if (quality != null) builder.append(quality).append(' ')
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
                    Order.ID_DESC -> {
                        if (w1.id == Int.MAX_VALUE) 1
                        else if (w2.id == Int.MAX_VALUE) -1
                        else w2.id - w1.id
                    }
                    Order.TITLE_ASC -> w1.displayText.compareTo(w2.displayText, true)
                    Order.TITLE_DESC -> w2.displayText.compareTo(w1.displayText, true)
                    Order.CITATIONS_ASC -> w1.citations - w2.citations
                    Order.CITATIONS_DESC -> {
                        if (w1.citations == Int.MAX_VALUE) 1
                        else if (w2.citations == Int.MAX_VALUE) -1
                        else w2.citations - w1.citations
                    }
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
        bar.title = allWrappers.size.toString() + " " + Util.caseString(if (allWrappers.size == 1) R.string.source else R.string.sources)
        if (allWrappers.size > 1) {
            // Search in SourcesFragment
            inflater.inflate(R.menu.search, menu)
            val searchItem = menu.findItem(R.id.search_item)
            searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(p0: MenuItem): Boolean {
                    return true
                }

                // Click on back arrow of SearchView
                override fun onMenuItemActionCollapse(p0: MenuItem): Boolean {
                    activity?.invalidateOptionsMenu() // Updates the title in case a source was deleted
                    return true
                }
            })
            searchView = searchItem.actionView as? SearchView
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
            if (Global.settings.expert && Global.gc.sources.isNotEmpty()) subMenu!!.add(0, 1, 0, R.string.id)
            subMenu!!.add(0, 2, 0, R.string.text)
            if (Global.gc.sources.isNotEmpty()) subMenu.add(0, 3, 0, R.string.citations)
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
    private var citation: SourceCitation? = null

    override fun onCreateContextMenu(menu: ContextMenu, view: View, info: ContextMenuInfo?) {
        if (prepareJob?.isCompleted == true) {
            source = Global.gc.getSource((view.getTag(R.id.tag_object) as? String))
            citation = view.getTag(R.id.tag_object) as? SourceCitation
            if (Global.settings.expert && source != null) menu.add(5, 0, 0, R.string.edit_id)
            if (source != null) menu.add(5, 1, 0, R.string.delete)
            else if (citation != null) menu.add(5, 2, 0, R.string.delete)
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (item.groupId == 5) {
            if (item.itemId == 0) { // Edit source ID
                U.editId(context, source) { this.showContent() }
            } else if (item.itemId == 1) { // Delete source
                Util.confirmDelete(requireContext()) {
                    val objects = SourceUtil.deleteSource(source!!)
                    conclude(*objects)
                }
            } else if (item.itemId == 2) { // Delete note-source
                Util.confirmDelete(requireContext()) {
                    val stack = FindStack(Global.gc, citation, false)
                    val container = stack.containerObject as SourceCitationContainer
                    container.sourceCitations.remove(citation)
                    if (container.sourceCitations.isEmpty()) container.sourceCitations = null
                    conclude(stack.leaderObject)
                }
            }
            return true
        }
        return false
    }

    private fun conclude(vararg objects: Any?) {
        TreeUtil.save(false, *objects)
        showContent()
        (requireActivity() as MainActivity).refreshInterface()
    }
}

data class SourceWrapper(
    val source: Source? = null, val noteSource: SourceCitation? = null, // Source or SourceCitation one of the two is not null
    val id: Int = Int.MAX_VALUE, val displayText: String, val citations: Int = Int.MAX_VALUE, val searchText: String
)
