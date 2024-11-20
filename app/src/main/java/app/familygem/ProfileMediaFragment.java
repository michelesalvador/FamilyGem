package app.familygem;

import static app.familygem.Global.gc;

import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;

import app.familygem.main.MediaAdapter;
import app.familygem.main.MediaFragment;
import app.familygem.util.TreeUtil;
import app.familygem.visitor.MediaContainerList;

public class ProfileMediaFragment extends ProfileBaseFragment {

    MediaContainerList mediaVisitor;

    @Override
    public void createContent() {
        if (prepareContent()) {
            mediaVisitor = new MediaContainerList(gc, true);
            person.accept(mediaVisitor);
            RecyclerView recyclerView = new RecyclerView(layout.getContext());
            recyclerView.setHasFixedSize(true);
            RecyclerView.LayoutManager layoutManager = new GridLayoutManager(getContext(), 2);
            recyclerView.setLayoutManager(layoutManager);
            MediaAdapter adapter = new MediaAdapter(mediaVisitor.mediaList, true);
            recyclerView.setAdapter(adapter);
            layout.addView(recyclerView);
        }
    }

    // Context menu
    private Media media;
    private Object container; // Media belong not only to 'one', but also to their subordinates EventFact, SourceCitation...

    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, View view, ContextMenu.ContextMenuInfo info) {
        media = (Media)view.getTag(R.id.tag_object);
        container = view.getTag(R.id.tag_container);
        /* Changing configuration (e.g. rotating the screen) this fragment is recreated
           and mediaVisitor here becomes null: really strange, because in onCreateView it is OK.
           And moreover when recreated mediaList is empty. */
        if (mediaVisitor == null) mediaVisitor = new MediaContainerList(gc, true);
        if (mediaVisitor.mediaList.size() > 1 && media.getPrimary() == null)
            menu.add(0, 0, 0, R.string.primary_media);
        if (media.getId() != null)
            menu.add(0, 1, 0, R.string.unlink);
        menu.add(0, 2, 0, R.string.delete);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == 0) { // Primary media
            for (MediaContainerList.MedCont medCont : mediaVisitor.mediaList) // Resets them all then marks one
                medCont.media.setPrimary(null);
            media.setPrimary("Y");
            if (media.getId() != null) // To update the change date in the Media record rather than in the Person
                TreeUtil.INSTANCE.save(true, media);
            else
                TreeUtil.INSTANCE.save(true, person);
            refresh();
            return true;
        } else if (id == 1) { // Unlink
            MediaFragment.disconnectMedia(media.getId(), (MediaContainer)container);
            TreeUtil.INSTANCE.save(true, person);
            refresh();
            return true;
        } else if (id == 2) { // Delete
            Object[] leaders = MediaFragment.deleteMedia(media, null);
            TreeUtil.INSTANCE.save(true, leaders);
            refresh();
            return true;
        }
        return false;
    }
}
