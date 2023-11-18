package app.familygem.detail;

import static app.familygem.Global.gc;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.RepositoryRef;
import org.folg.gedcom.model.Source;

import app.familygem.DetailActivity;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import app.familygem.util.ChangeUtils;

public class RepositoryRefActivity extends DetailActivity {

    RepositoryRef repoRef;

    @Override
    protected void format() {
        placeSlug("REPO");
        repoRef = (RepositoryRef)cast(RepositoryRef.class);
        if (repoRef.getRepository(gc) != null) { // An actual repository is referenced
            setTitle(R.string.repository_citation);
            View repositoryCard = putRepository(box, repoRef.getRepository(gc));
            repositoryCard.setTag(R.id.tag_object, repoRef.getRepository(gc)); // For the context menu TODO: still needed?
            registerForContextMenu(repositoryCard);
        } else if (repoRef.getRef() != null) { // Ref to a non-existent repository (perhaps deleted)
            setTitle(R.string.inexistent_repository_citation); // TODO: could be removed
        } else { // Without ref
            setTitle(R.string.repository_note);
        }
        place(getString(R.string.value), "Value", false, 0);
        place(getString(R.string.call_number), "CallNumber");
        place(getString(R.string.media_type), "MediaType");
        placeExtensions(repoRef);
        U.placeNotes(box, repoRef, true);
    }

    public static View putRepository(LinearLayout layout, final Repository repo) {
        final Context context = layout.getContext();
        View repoView = LayoutInflater.from(context).inflate(R.layout.pezzo_fonte, layout, false);
        layout.addView(repoView);
        ((TextView)repoView.findViewById(R.id.fonte_testo)).setText(repo.getName());
        ((CardView)repoView).setCardBackgroundColor(context.getResources().getColor(R.color.repository));
        repoView.setOnClickListener(v -> {
            Memory.setLeader(repo);
            context.startActivity(new Intent(context, RepositoryActivity.class));
        });
        return repoView;
    }

    @Override
    public void delete() {
        // Delete the citation from the archive and update the date of the source that contained it
        Source container = (Source)Memory.getSecondToLastObject();
        container.setRepositoryRef(null);
        ChangeUtils.INSTANCE.updateChangeDate(container);
        Memory.setInstanceAndAllSubsequentToNull(repoRef);
    }
}
