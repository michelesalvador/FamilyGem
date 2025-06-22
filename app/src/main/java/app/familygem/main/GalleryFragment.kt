package app.familygem.main

import android.app.Activity
import android.content.Context
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
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBar
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.familygem.FileUri
import app.familygem.Global
import app.familygem.MediaFoldersActivity
import app.familygem.ProgressView
import app.familygem.R
import app.familygem.constant.Choice
import app.familygem.constant.Destination
import app.familygem.constant.Extra
import app.familygem.util.ChangeUtil
import app.familygem.util.FileUtil
import app.familygem.util.MediaUtil
import app.familygem.util.TreeUtil
import app.familygem.util.Util
import app.familygem.visitor.MediaLeaders
import app.familygem.visitor.MediaReferences
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.folg.gedcom.model.Media
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

/** Fragment with a list of all Media of the tree. */
class GalleryFragment : BaseFragment() {

    private lateinit var mediaVisitor: MediaLeaders
    lateinit var adapter: GalleryAdapter
    private var sharedMediaOnly = false
    private lateinit var progress: ProgressView
    private lateinit var treeDir: File // Tree media folder in app external storage
    private var searchView: SearchView? = null
    private var checkJob: Job? = null
    private var copyJob: Job? = null
    private var shrinkJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val view = inflater.inflate(R.layout.recyclerview, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.setHasFixedSize(true)
        view.findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            FileUtil.displayFileChooser(requireContext(), sharedMediaLauncher, Destination.SHARED_MEDIA)
        }
        sharedMediaOnly = requireActivity().intent.getBooleanExtra(Choice.MEDIA, false)
        mediaVisitor = MediaLeaders(sharedMediaOnly, true)
        recyclerView.layoutManager = GridLayoutManager(context, 2)
        adapter = GalleryAdapter(mediaVisitor.list)
        recyclerView.adapter = adapter
        setupFastScroller(recyclerView)
        progress = view.findViewById(R.id.recycler_progress)
        treeDir = requireContext().getExternalFilesDir(Global.settings.openTree.toString())!!
        return view
    }

    override fun showContent() {
        progress.visibility = View.VISIBLE
        mediaVisitor.list.clear()
        Global.gc.accept(mediaVisitor)
        adapter.filter.filter(searchView?.query ?: "")
        progress.visibility = View.GONE
    }

    override fun isSearching(): Boolean {
        return searchView?.query?.isNotBlank() == true
    }

    override fun onPause() {
        super.onPause()
        // Resets the extra if no shared media has been chosen
        requireActivity().intent.removeExtra(Choice.MEDIA)
        // Cancels any active job
        if (checkJob?.isActive == true) checkJob?.cancel()
        if (copyJob?.isActive == true) copyJob?.cancel()
        if (shrinkJob?.isActive == true) shrinkJob?.cancel()
        progress.hideBar()
        progress.visibility = View.GONE
    }

    /** The file retrieved from SAF becomes a shared media. */
    private val sharedMediaLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val sharedMedia = MediaUtil.newSharedMedia(null)
            if (FileUtil.setFileAndProposeCropping(requireContext(), result.data, sharedMedia)) {
                finalizeAction(arrayOf(sharedMedia))
            }
        }
    }

    private lateinit var media: Media

    override fun onCreateContextMenu(menu: ContextMenu, view: View, info: ContextMenuInfo?) {
        media = view.getTag(R.id.tag_object) as Media
        if (media.id != null) {
            val index = Global.gc.media.indexOf(media)
            if (index > 0) menu.add(3, 0, 0, R.string.move_up)
            if (index < Global.gc.media.size - 1) menu.add(3, 1, 0, R.string.move_down)
            val mediaReferences = MediaReferences(Global.gc, media, false)
            if (mediaReferences.num > 0) menu.add(3, 2, 0, R.string.make_media)
        } else menu.add(3, 3, 0, R.string.make_shared_media)
        menu.add(3, 4, 0, R.string.delete)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (item.groupId == 3) {
            return when (item.itemId) {
                0 -> finalizeAction(swapSharedMedia(-1))
                1 -> finalizeAction(swapSharedMedia(1))
                2 -> finalizeAction(MediaUtil.makeSimpleMedia(media))
                3 -> finalizeAction(MediaUtil.makeSharedMedia(media))
                4 -> {
                    Util.confirmDelete(requireContext()) {
                        finalizeAction(MediaUtil.deleteMedia(media))
                    }
                    true
                }
                else -> false
            }
        }
        return false
    }

    private fun swapSharedMedia(direction: Int): Array<Any> {
        val index = Global.gc.media.indexOf(media)
        Collections.swap(Global.gc.media, index, index + direction)
        return emptyArray()
    }

    /** Saves the changes and updates the content. */
    private fun finalizeAction(modified: Array<Any>): Boolean {
        TreeUtil.save(true, *modified)
        showContent()
        (requireActivity() as MainActivity).refreshInterface()
        return true
    }

    override fun updateToolbar(bar: ActionBar, menu: Menu, inflater: MenuInflater) {
        bar.title = "${mediaVisitor.list.size} ${Util.caseString(R.string.media)}"
        if (!sharedMediaOnly) {
            menu.add(0, 0, 0, R.string.media_folders)
            menu.add(0, 1, 0, R.string.copy_app_storage)
            menu.add(0, 2, 0, R.string.shrink_path)
            checkJob = lifecycleScope.launch(IO) {
                // Sometimes long running tasks
                if (!isThereAnyExternalFile(requireContext())) withContext(Main) { menu.removeItem(1) }
                if (!isThereAnyShrinkablePath(requireContext())) withContext(Main) { menu.removeItem(2) }
            }
        }
        // Search for Media
        if (mediaVisitor.list.size > 1) {
            inflater.inflate(R.menu.search, menu)
            searchView = menu.findItem(R.id.search_item).actionView as SearchView
            searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextChange(query: String): Boolean {
                    adapter.filter.filter(query)
                    return true
                }

                override fun onQueryTextSubmit(q: String): Boolean {
                    searchView?.clearFocus()
                    return false
                }
            })
        }
    }

    override fun selectItem(id: Int) {
        when (id) {
            0 -> startActivity(Intent(context, MediaFoldersActivity::class.java).putExtra(Extra.TREE_ID, Global.settings.openTree))
            1 -> {
                progress.visibility = View.VISIBLE
                copyJob = lifecycleScope.launch(IO) { copyFilesToTreeStorage() }
            }
            2 -> {
                progress.visibility = View.VISIBLE
                shrinkJob = lifecycleScope.launch(Default) { shrinkMediaPaths() }
            }
        }
    }

    /** @return True in case there is at least one media linked to a file not in the app storage. */
    private fun isThereAnyExternalFile(context: Context): Boolean {
        return mediaVisitor.list.filterNot { it.media.file.isNullOrBlank() }.any {
            if (it.fileUri == null) it.fileUri = FileUri(context, it.media)
            it.fileUri?.file?.startsWith(treeDir) == false || it.fileUri?.uri != null
                    || it.media.file.startsWith("https://") || it.media.file.startsWith("http://")
        }
    }

    /** @return True in case there is at least one media link shrinkable to filename only. */
    private fun isThereAnyShrinkablePath(context: Context): Boolean {
        return mediaVisitor.list.filterNot { it.media.file.isNullOrBlank() }.any {
            if (it.fileUri == null) it.fileUri = FileUri(context, it.media)
            it.fileUri?.treeDirFilename == true
        }
    }

    private var copiedFiles = 0 // Number of files actually copied
    private var toBeSaved = false // Some media link has been modified

    /** Each valid file outside the tree app storage is copied inside. */
    private suspend fun copyFilesToTreeStorage() {
        // Creates a list of media grouped by the file they are linked to
        progress.displayBar("Preparing files", mediaVisitor.list.size.toLong())
        var count: Long = 0
        mediaVisitor.list.forEach {
            yield()
            if (it.fileUri == null) it.fileUri = FileUri(requireContext(), it.media)
            progress.progress = ++count
        }
        progress.hideBar()
        val groupedMedia = mediaVisitor.list.filterNot { it.media.file.isNullOrBlank() || it.fileUri?.file?.startsWith(treeDir) == true }
            .groupBy { it.fileUri?.path ?: it.media.file }
        // Copies each file
        progress.displayBar("Copying files", groupedMedia.size.toLong())
        count = 0
        copiedFiles = 0
        val excluded = arrayOf("text/html", "text/javascript", "application/json", "text/css", "text/xml")
        groupedMedia.forEach { entry ->
            entry.value[0].fileUri?.apply {
                val name = name ?: URLUtil.guessFileName(media.file, null, null)
                val newFile = FileUtil.nextAvailableFileName(treeDir, name)
                if (file != null) {
                    file!!.copyTo(newFile)
                    completeCopy(entry.value, newFile)
                } else if (uri != null) {
                    requireContext().contentResolver.openInputStream(uri!!).use { input ->
                        newFile.outputStream().use { output ->
                            input?.copyTo(output)
                            completeCopy(entry.value, newFile)
                        }
                    }
                } else if (media.file.startsWith("https://") || media.file.startsWith("http://")) {
                    try {
                        val connection = URL(media.file).openConnection() as HttpURLConnection
                        if (connection.responseCode == HttpURLConnection.HTTP_OK && excluded.none { connection.contentType.contains(it) }) {
                            connection.inputStream.use { input ->
                                newFile.outputStream().use { output ->
                                    input?.copyTo(output)
                                    completeCopy(entry.value, newFile)
                                }
                            }
                        } else completeCopy()
                    } catch (ignored: Exception) {
                        ignored.printStackTrace()
                    }
                } else completeCopy()
            }
            progress.progress = ++count
        }
        if (toBeSaved) TreeUtil.save(true)
        withContext(Main) {
            showContent()
            (requireActivity() as MainActivity).refreshInterface()
            progress.hideBar()
            progress.visibility = View.GONE
            val message = if (copiedFiles > 0) "$copiedFiles files copied." else "No file copied."
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    private fun completeCopy(wrapperList: List<MediaLeaders.MediaWrapper>? = null, newFile: File? = null) {
        if (wrapperList != null && newFile != null) {
            val name = newFile.name
            wrapperList.filter { it.media.file != name }.forEach {
                it.media.file = name
                ChangeUtil.updateChangeDate(it.leader)
                toBeSaved = true
            }
            copiedFiles++
        }
    }

    /** Reduces media links to filename only where possible. */
    private suspend fun shrinkMediaPaths() {
        progress.displayBar("Preparing files", mediaVisitor.list.size.toLong())
        var count: Long = 0
        mediaVisitor.list.forEach {
            yield()
            if (it.fileUri == null) it.fileUri = FileUri(requireContext(), it.media)
            progress.progress = ++count
        }
        progress.hideBar()
        val shrinkableMedia = mediaVisitor.list.filter { it.fileUri?.treeDirFilename == true }
        var modified = 0
        shrinkableMedia.forEach { wrapper ->
            wrapper.fileUri?.name?.let {
                wrapper.media.file = it
                ChangeUtil.updateChangeDate(wrapper.leader)
                modified++
            }
        }
        if (modified > 0) TreeUtil.save(true)
        withContext(Main) {
            showContent()
            (requireActivity() as MainActivity).refreshInterface()
            progress.visibility = View.GONE
            val message = if (modified > 0) "$modified paths shrunk." else "No path shrunk."
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }
}
