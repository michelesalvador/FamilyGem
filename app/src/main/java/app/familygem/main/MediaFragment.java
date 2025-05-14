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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.folg.gedcom.model.Media;

import app.familygem.Global;
import app.familygem.MediaFoldersActivity;
import app.familygem.R;
import app.familygem.constant.Choice;
import app.familygem.constant.Destination;
import app.familygem.constant.Extra;
import app.familygem.util.FileUtil;
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
                v -> FileUtil.INSTANCE.displayFileChooser(requireContext(), sharedMediaLauncher, Destination.SHARED_MEDIA));
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
    private final ActivityResultLauncher<Intent> sharedMediaLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Media sharedMedia = MediaUtil.INSTANCE.newSharedMedia(null);
                    if (FileUtil.INSTANCE.setFileAndProposeCropping(requireContext(), result.getData(), sharedMedia)) {
                        finalizeAction(new Media[]{sharedMedia});
                    }
                }
            });

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

    /**
     * Saves the changes and updates the content.
     */
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
}
