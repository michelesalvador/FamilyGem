// List of submitters

package app.familygem.list;

import static app.familygem.Global.gc;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import org.folg.gedcom.model.Header;
import org.folg.gedcom.model.Submitter;

import java.util.List;

import app.familygem.Global;
import app.familygem.Memory;
import app.familygem.NewTreeActivity;
import app.familygem.R;
import app.familygem.InfoActivity;
import app.familygem.U;
import app.familygem.detail.SubmitterActivity;

public class SubmittersFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle stato) {
        List<Submitter> submitterList = gc.getSubmitters();
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(submitterList.size() + " " +
                getString(submitterList.size() == 1 ? R.string.submitter : R.string.submitters).toLowerCase());
        setHasOptionsMenu(true);
        View vista = inflater.inflate(R.layout.scrollview, container, false);
        LinearLayout layout = vista.findViewById(R.id.scrollview_layout);
        for (final Submitter autor : submitterList) {
            View vistaPezzo = inflater.inflate(R.layout.scrollview_item, layout, false);
            layout.addView(vistaPezzo);
            ((TextView)vistaPezzo.findViewById(R.id.item_name)).setText(InfoActivity.submitterName(autor));
            vistaPezzo.findViewById(R.id.item_num).setVisibility(View.GONE);
            vistaPezzo.setOnClickListener(v -> {
                Memory.setLeader(autor);
                startActivity(new Intent(getContext(), SubmitterActivity.class));
            });
            registerForContextMenu(vistaPezzo);
            vistaPezzo.setTag(autor);
        }
        vista.findViewById(R.id.fab).setOnClickListener(v -> {
            createSubmitter(getContext());
            U.save(true);
        });
        return vista;
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
        U.updateChangeDate(subm);
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
            header = NewTreeActivity.createHeader(Global.settings.openTree + ".json");
            gc.setHeader(header);
        }
        header.setSubmitterRef(submitter.getId());
        U.save(false, submitter);
    }

    Submitter submitter;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info) {
        submitter = (Submitter)vista.getTag();
        if (gc.getHeader() == null || gc.getHeader().getSubmitter(gc) == null || !gc.getHeader().getSubmitter(gc).equals(submitter))
            menu.add(0, 0, 0, R.string.make_default);
        if (!U.submitterHasShared(submitter)) // Can be deleted only if he has never shared
            menu.add(0, 1, 0, R.string.delete);
        // todo spiegare perché non può essere eliminato?
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 0:
                mainSubmitter(submitter);
                return true;
            case 1:
                // Todo conferma elimina
                deleteSubmitter(submitter);
                U.save(false);
                getActivity().recreate();
                return true;
        }
        return false;
    }
}
