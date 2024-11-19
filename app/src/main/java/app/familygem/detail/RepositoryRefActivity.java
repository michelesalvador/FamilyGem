package app.familygem.detail;

import static app.familygem.Global.gc;

import android.view.View;

import org.folg.gedcom.model.RepositoryRef;
import org.folg.gedcom.model.Source;

import app.familygem.DetailActivity;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.util.ChangeUtil;
import app.familygem.util.NoteUtil;
import app.familygem.util.RepositoryUtil;

public class RepositoryRefActivity extends DetailActivity {

    RepositoryRef repoRef;

    @Override
    protected void format() {
        placeSlug("REPO");
        repoRef = (RepositoryRef)cast(RepositoryRef.class);
        if (repoRef.getRepository(gc) != null) { // An actual repository is referenced
            setTitle(R.string.repository_citation);
            View repositoryCard = RepositoryUtil.INSTANCE.placeRepository(box, repoRef.getRepository(gc));
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
        NoteUtil.INSTANCE.placeNotes(box, repoRef);
    }

    @Override
    public void delete() {
        // Delete the citation from the archive and update the date of the source that contained it
        Source container = (Source)Memory.getSecondToLastObject();
        container.setRepositoryRef(null);
        ChangeUtil.INSTANCE.updateChangeDate(container);
        Memory.setInstanceAndAllSubsequentToNull(repoRef);
    }
}
