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
import androidx.appcompat.app.ActionBar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.theartofdev.edmodo.cropper.CropImage;

import org.folg.gedcom.model.Media;

import app.familygem.F;
import app.familygem.Global;
import app.familygem.MediaFoldersActivity;
import app.familygem.R;
import app.familygem.constant.Choice;
import app.familygem.constant.Extra;
import app.familygem.util.MediaUtil;
import app.familygem.util.TreeUtil;
import app.familygem.util.Util;
import app.familygem.visitor.MediaContainerList;
import app.familygem.visitor.MediaReferences;
import kotlin.Unit;

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
        setupFastScroller(recyclerView);
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
     * The file retrieved from SAF becomes a shared media.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 4546) { // File taken from the supplier app is saved in the Media and possibly cropped
                Media media = MediaUtil.INSTANCE.newSharedMedia(null);
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
        if (media.getId() != null) {
            MediaReferences mediaReferences = new MediaReferences(Global.gc, media, false);
            if (mediaReferences.num > 0)
                menu.add(3, 0, 0, R.string.make_media);
        } else menu.add(3, 1, 0, R.string.make_shared_media);
        menu.add(3, 2, 0, R.string.delete);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getGroupId() == 3) {
            switch (item.getItemId()) {
                case 0:
                    return finalizeAction(MediaUtil.INSTANCE.makeSimpleMedia(media));
                case 1:
                    return finalizeAction(MediaUtil.INSTANCE.makeSharedMedia(media));
                case 2:
                    Util.INSTANCE.confirmDelete(requireContext(), () -> {
                        finalizeAction(MediaUtil.INSTANCE.deleteMedia(media));
                        return Unit.INSTANCE;
                    });
                    return true;
            }
        }
        return false;
    }

    private boolean finalizeAction(Object[] modified) {
        TreeUtil.INSTANCE.save(true, modified);
        showContent();
        ((MainActivity)requireActivity()).refreshInterface();
        return true;
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
