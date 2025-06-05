package app.familygem.profile

import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.MenuItem
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.familygem.Global
import app.familygem.MediaAdapter
import app.familygem.Memory
import app.familygem.R
import app.familygem.util.MediaUtil
import app.familygem.util.TreeUtil.save
import app.familygem.util.Util
import app.familygem.util.unlinkMedia
import app.familygem.visitor.MediaContainerList
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.MediaContainer

/** Displays all the media of a person. */
class MediaFragment : BaseFragment() {

    private lateinit var mediaVisitor: MediaContainerList

    override fun createContent() {
        if (prepareContent()) {
            mediaVisitor = MediaContainerList(Global.gc, true)
            person.accept(mediaVisitor)
            val recyclerView = RecyclerView(layout.context)
            recyclerView.setHasFixedSize(true)
            val layoutManager: RecyclerView.LayoutManager = GridLayoutManager(context, 2)
            recyclerView.layoutManager = layoutManager
            val adapter = MediaAdapter(mediaVisitor.mediaList, true)
            recyclerView.adapter = adapter
            layout.addView(recyclerView)
        }
    }

    private lateinit var media: Media
    private lateinit var container: MediaContainer // Media belong not only to 'person', but also to their subordinates EventFact, SourceCitation...

    override fun onCreateContextMenu(menu: ContextMenu, view: View, info: ContextMenuInfo?) {
        media = view.getTag(R.id.tag_object) as Media
        container = view.getTag(R.id.tag_container) as MediaContainer
        if (mediaVisitor.mediaList.size > 1 && media.primary == null)
            menu.add(0, 0, 0, R.string.primary_media)
        if (media.id != null) {
            menu.add(0, 1, 0, R.string.make_media)
            menu.add(0, 2, 0, R.string.unlink)
        } else menu.add(0, 3, 0, R.string.make_shared_media)
        menu.add(0, 4, 0, R.string.delete)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            0 -> { // Primary media
                mediaVisitor.mediaList.forEach { it.media.primary = null } // Resets them all then marks one
                media.primary = "Y"
                if (media.id != null) // To update the change date in the Media record rather than in the Person
                    save(true, media)
                else save(true, person)
                refresh()
                true
            }
            1 -> { // Make media
                save(true, *MediaUtil.makeSimpleMedia(media))
                Memory.setInstanceAndAllSubsequentToNull(media)
                refresh()
                true
            }
            2 -> { // Unlink
                container.unlinkMedia(media.id)
                save(true, person)
                refresh()
                true
            }
            3 -> { // Make shared media
                save(true, *MediaUtil.makeSharedMedia(media))
                Memory.setInstanceAndAllSubsequentToNull(media)
                refresh()
                true
            }
            4 -> { // Delete
                Util.confirmDelete(requireContext()) {
                    val leaders = MediaUtil.deleteMedia(media)
                    save(true, *leaders)
                    refresh()
                }
                true
            }
            else -> false
        }
    }
}
