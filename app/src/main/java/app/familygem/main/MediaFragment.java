package app.familygem.main;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.theartofdev.edmodo.cropper.CropImage;

import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;
import org.folg.gedcom.model.MediaRef;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import app.familygem.F;
import app.familygem.Global;
import app.familygem.MediaFoldersActivity;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import app.familygem.constant.Choice;
import app.familygem.constant.Extra;
import app.familygem.util.TreeUtil;
import app.familygem.util.Util;
import app.familygem.visitor.FindStack;
import app.familygem.visitor.MediaContainerList;
import app.familygem.visitor.MediaReferences;

/**
 * Fragment with a list of all Media of the tree.
 */
public class MediaFragment extends BaseFragment {

    MediaContainerList mediaVisitor;
    MediaAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        super.onCreateView(inflater, container, bundle);
        View view = inflater.inflate(R.layout.recyclerview, container, false);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        view.findViewById(R.id.fab).setOnClickListener(
                v -> F.displayImageCaptureDialog(getContext(), MediaFragment.this, 4546, null));
        mediaVisitor = new MediaContainerList(Global.gc, !getActivity().getIntent().getBooleanExtra(Choice.MEDIA, false));
        RecyclerView.LayoutManager layoutManager = new GridLayoutManager(getContext(), 2);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new MediaAdapter(mediaVisitor.mediaList, true);
        recyclerView.setAdapter(adapter);
        return view;
    }

    @Override
    public void showContent() {
        mediaVisitor.mediaList.clear();
        Global.gc.accept(mediaVisitor);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Leaving the activity resets the extra if no shared media has been chosen
        getActivity().getIntent().removeExtra(Choice.MEDIA);
    }

    /**
     * Creates a new shared media, optionally inside a container record.
     */
    public static Media newSharedMedia(@Nullable Object container) {
        Media media = new Media();
        media.setId(U.newID(Global.gc, Media.class));
        media.setFileTag("FILE"); // Necessary to then export the GEDCOM
        Global.gc.addMedia(media);
        if (container != null) {
            MediaRef mediaRef = new MediaRef();
            mediaRef.setRef(media.getId());
            ((MediaContainer)container).addMediaRef(mediaRef);
        }
        return media;
    }

    /**
     * Detaches a shared media from a container.
     */
    public static void disconnectMedia(String mediaId, MediaContainer container) { // TODO: unlinkMedia()
        Iterator<MediaRef> refs = container.getMediaRefs().iterator();
        while (refs.hasNext()) {
            MediaRef ref = refs.next();
            if (ref.getMedia(Global.gc) == null // Possible ref to a non-existent media
                    || ref.getRef().equals(mediaId))
                refs.remove();
        }
        if (container.getMediaRefs().isEmpty())
            container.setMediaRefs(null);
    }

    /**
     * Deletes a shared or local media and removes the references in container records.
     *
     * @return An array with the modified leader objects
     */
    public static Object[] deleteMedia(Media media, View view) {
        Set<Object> leaders;
        if (media.getId() != null) { // Shared Media
            Global.gc.getMedia().remove(media);
            // Delete references in all containers
            MediaReferences deleteMedia = new MediaReferences(Global.gc, media, true);
            leaders = deleteMedia.leaders;
        } else { // Local Media
            new FindStack(Global.gc, media); // Temporally generates a stack of the media to locate its container
            MediaContainer container = (MediaContainer)Memory.getSecondToLastObject();
            container.getMedia().remove(media);
            if (container.getMedia().isEmpty())
                container.setMedia(null);
            leaders = new HashSet<>(); // Set with only one leader object
            leaders.add(Memory.getLeaderObject());
            Memory.stepBack(); // Deletes the stack just created
        }
        Memory.setInstanceAndAllSubsequentToNull(media);
        if (view != null)
            view.setVisibility(View.GONE);
        return leaders.toArray(new Object[0]);
    }

    /**
     * The file retrieved from SAF becomes a shared media.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 4546) { // File taken from the supplier app is saved in the Media and possibly cropped
                Media media = newSharedMedia(null);
                if (F.setFileAndProposeCropping(getContext(), this, data, media))
                    TreeUtil.INSTANCE.save(true, media);
            } else if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
                F.endImageCropping(data);
                TreeUtil.INSTANCE.save(true, Global.croppedMedia);
            }
        } else if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) { // If user clicks the back arrow in Image Cropper
            F.saveFolderInSettings();
        }
    }

    // Contextual menu
    private Media media;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo info) {
        media = (Media)view.getTag(R.id.tag_object);
        menu.add(3, 0, 0, R.string.delete);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getGroupId() == 3 && item.getItemId() == 0) {
            Object[] modified = deleteMedia(media, null);
            TreeUtil.INSTANCE.save(true, modified);
            showContent();
            ((MainActivity)requireActivity()).refreshInterface();
            return true;
        }
        return false;
    }

    @Override
    public void updateToolbar(ActionBar bar, Menu menu, MenuInflater inflater) {
        bar.setTitle(mediaVisitor.mediaList.size() + " " + Util.INSTANCE.caseString(R.string.media));
        menu.add(0, 0, 0, R.string.media_folders);
    }

    @Override
    public void selectItem(int id) {
        if (id == 0) {
            startActivity(new Intent(getContext(), MediaFoldersActivity.class).putExtra(Extra.TREE_ID, Global.settings.openTree));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permission, @NonNull int[] grantResults) {
        F.permissionsResult(getContext(), this, requestCode, permission, grantResults, null);
    }
}
