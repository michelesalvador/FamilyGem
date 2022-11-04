package app.familygem.detail;

import android.content.Context;
import android.content.Intent;
import androidx.cardview.widget.CardView;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.RepositoryRef;
import org.folg.gedcom.model.Source;
import app.familygem.DetailActivity;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import static app.familygem.Global.gc;

public class RepositoryRefActivity extends DetailActivity {

	RepositoryRef r;

	@Override
	public void format() {
		placeSlug("REPO");
		r = (RepositoryRef)cast(RepositoryRef.class);
		if( r.getRepository(gc) != null ) { // valid
			setTitle(R.string.repository_citation);
			View repositoryCard = putRepository(box, r.getRepository(gc));
			repositoryCard.setTag(R.id.tag_oggetto, r.getRepository(gc)); //for the context menu TODO still needed?
			registerForContextMenu(repositoryCard);
		} else if( r.getRef() != null ) { // of a non-existent archive (perhaps deleted) //di un archivio inesistente (magari eliminato)
			setTitle(R.string.inexistent_repository_citation);
		} else { // without ref??
			setTitle(R.string.repository_note);
		}
		place(getString(R.string.value), "Value", false, true);
		place(getString(R.string.call_number), "CallNumber");
		place(getString(R.string.media_type), "MediaType");
		placeExtensions(r);
		U.placeNotes(box, r, true);
	}

	public static View putRepository(LinearLayout container, final Repository repo) {
		final Context context = container.getContext();
		View repositoryCard = LayoutInflater.from(context).inflate(R.layout.pezzo_fonte, container, false);
		container.addView(repositoryCard);
		((TextView)repositoryCard.findViewById(R.id.fonte_testo)).setText(repo.getName());
		((CardView)repositoryCard).setCardBackgroundColor(context.getResources().getColor(R.color.archivio));
		repositoryCard.setOnClickListener(v -> {
			Memory.setFirst(repo);
			context.startActivity(new Intent(context, RepositoryActivity.class));
		});
		return repositoryCard;
	}

	@Override
	public void delete() {
		// Delete the citation from the archive and update the date of the source that contained it
		Source container = (Source) Memory.getSecondToLastObject();
		container.setRepositoryRef(null);
		U.updateChangeDate(container);
		Memory.setInstanceAndAllSubsequentToNull(r);
	}
}