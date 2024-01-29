package app.familygem.main;

import static app.familygem.Global.gc;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;

import org.folg.gedcom.model.Header;
import org.folg.gedcom.model.Submitter;

import java.util.Collections;
import java.util.List;

import app.familygem.Global;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import app.familygem.databinding.ScrollviewBinding;
import app.familygem.detail.SubmitterActivity;
import app.familygem.util.ChangeUtil;
import app.familygem.util.SubmitterUtilKt;
import app.familygem.util.TreeUtil;
import app.familygem.util.Util;

/**
 * The list of submitters.
 */
public class SubmittersFragment extends BaseFragment {

    ScrollviewBinding binding;
    List<Submitter> submitters = Collections.emptyList();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        super.onCreateView(inflater, container, bundle);
        binding = ScrollviewBinding.inflate(inflater, container, false);
        binding.scrollviewFab.getRoot().setOnClickListener(v -> {
            createSubmitter(getContext());
            TreeUtil.INSTANCE.save(true);
        });
        return binding.getRoot();
    }

    @Override
    public void showContent() {
        displayList();
    }

    private void displayList() {
        submitters = gc.getSubmitters();
        binding.scrollviewLayout.removeAllViews();
        for (Submitter submitter : submitters) {
            View submView = getLayoutInflater().inflate(R.layout.scrollview_item, binding.scrollviewLayout, false);
            binding.scrollviewLayout.addView(submView);
            ((TextView)submView.findViewById(R.id.item_name)).setText(SubmitterUtilKt.writeName(submitter));
            submView.findViewById(R.id.item_num).setVisibility(View.GONE);
            submView.setOnClickListener(v -> {
                Memory.setLeader(submitter);
                startActivity(new Intent(getContext(), SubmitterActivity.class));
            });
            registerForContextMenu(submView);
            submView.setTag(submitter);
        }
    }

    @Override
    public void updateToolbar(@NonNull ActionBar bar, @NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        bar.setTitle(submitters.size() + " " + Util.INSTANCE.caseString(submitters.size() == 1 ? R.string.submitter : R.string.submitters));
    }

    // Delete one submitter
    // Todo mi sa che andrebbe cercato eventuale SubmitterRef in tutti i record
    public static void deleteSubmitter(Submitter submitter) {
        Header header = gc.getHeader();
        if (header != null && header.getSubmitterRef() != null
                && header.getSubmitterRef().equals(submitter.getId())) {
            header.setSubmitterRef(null);
        }
        gc.getSubmitters().remove(submitter);
        if (gc.getSubmitters().isEmpty())
            gc.setSubmitters(null);
        Memory.setInstanceAndAllSubsequentToNull(submitter);
    }

    // Create a new submitter. Receiving a context open them for editing.
    public static Submitter createSubmitter(Context context) {
        Submitter subm = new Submitter();
        subm.setId(U.newID(gc, Submitter.class));
        subm.setName("");
        ChangeUtil.INSTANCE.updateChangeDate(subm);
        gc.addSubmitter(subm);
        if (context != null) {
            Memory.setLeader(subm);
            context.startActivity(new Intent(context, SubmitterActivity.class));
        }
        return subm;
    }

    public static void mainSubmitter(Submitter submitter) {
        Header header = gc.getHeader();
        if (header == null) {
            header = TreeUtil.INSTANCE.createHeader(Global.settings.openTree + ".json");
            gc.setHeader(header);
        }
        header.setSubmitterRef(submitter.getId());
        TreeUtil.INSTANCE.save(false, submitter);
    }

    Submitter submitter;

    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info) {
        submitter = (Submitter)vista.getTag();
        if (gc.getHeader() == null || gc.getHeader().getSubmitter(gc) == null || !gc.getHeader().getSubmitter(gc).equals(submitter))
            menu.add(7, 0, 0, R.string.make_default);
        if (!U.submitterHasShared(submitter)) // Can be deleted only if he has never shared
            menu.add(7, 1, 0, R.string.delete);
        // todo spiegare perché non può essere eliminato?
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getGroupId() == 7) {
            switch (item.getItemId()) {
                case 0:
                    mainSubmitter(submitter);
                    return true;
                case 1:
                    // Todo conferma elimina
                    deleteSubmitter(submitter);
                    TreeUtil.INSTANCE.save(true);
                    showContent();
                    ((MainActivity)requireActivity()).refreshInterface();
                    return true;
            }
        }
        return false;
    }
}
