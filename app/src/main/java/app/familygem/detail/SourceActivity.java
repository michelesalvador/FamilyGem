package app.familygem.detail;

import android.content.Intent;
import androidx.cardview.widget.CardView;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.folg.gedcom.model.RepositoryRef;
import org.folg.gedcom.model.Source;
import app.familygem.LibraryFragment;
import app.familygem.DetailActivity;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import app.familygem.visitor.ListOfSourceCitations;
import static app.familygem.Global.gc;

public class SourceActivity extends DetailActivity {

	Source f;

	@Override
	public void format() {
		setTitle(R.string.source);
		f = (Source)cast(Source.class);
		placeSlug("SOUR", f.getId());
		ListOfSourceCitations citations = new ListOfSourceCitations(gc, f.getId());
		f.putExtension("citaz", citations.list.size());    // for the LibraryFragment
		place(getString(R.string.abbreviation), "Abbreviation");
		place(getString(R.string.title), "Title", true, true);
		place(getString(R.string.type), "Type", false, true);    // _type
		place(getString(R.string.author), "Author", true, true);
		place(getString(R.string.publication_facts), "PublicationFacts", true, true);
		place(getString(R.string.date), "Date");    // always null in my Gedcom
		place(getString(R.string.text), "Text", true, true);
		place(getString(R.string.call_number), "CallNumber", false, false); // CALN it must be in the SOURCE_REPOSITORY_CITATION
		place(getString(R.string.italic), "Italic", false, false);    // _italic indicates source title to be in italics ???
		place(getString(R.string.media_type), "MediaType", false, false);    //MEDI, would be in SOURCE REPOSITORY CITATION // MEDI, sarebbe in SOURCE_REPOSITORY_CITATION
		place(getString(R.string.parentheses), "Paren", false, false);    // _PAREN indicates source facts are to be enclosed in parentheses
		place(getString(R.string.reference_number), "ReferenceNumber");    // ref num false???
		place(getString(R.string.rin), "Rin", false, false);
		place(getString(R.string.user_id), "Uid", false, false);
		placeExtensions(f);
		// Put the quote to the archive //Mette la citazione all'archivio TODO improve translation
		if( f.getRepositoryRef() != null ) {
			View refView = LayoutInflater.from(this).inflate(R.layout.pezzo_citazione_fonte, box, false);
			box.addView(refView);
			refView.setBackgroundColor(getResources().getColor(R.color.archivioCitazione));
			final RepositoryRef repositoryRef = f.getRepositoryRef();
			if( repositoryRef.getRepository(gc) != null ) {
				((TextView)refView.findViewById(R.id.fonte_testo)).setText(repositoryRef.getRepository(gc).getName());
				((CardView)refView.findViewById(R.id.citazione_fonte)).setCardBackgroundColor(getResources().getColor(R.color.archivio));
			} else refView.findViewById(R.id.citazione_fonte).setVisibility(View.GONE);
			String t = "";
			if( repositoryRef.getValue() != null ) t += repositoryRef.getValue() + "\n";
			if( repositoryRef.getCallNumber() != null ) t += repositoryRef.getCallNumber() + "\n";
			if( repositoryRef.getMediaType() != null ) t += repositoryRef.getMediaType() + "\n";
			TextView textView = refView.findViewById(R.id.citazione_testo);
			if( t.isEmpty() ) textView.setVisibility(View.GONE);
			else textView.setText(t.substring(0, t.length() - 1));
			U.placeNotes((LinearLayout)refView.findViewById(R.id.citazione_note), repositoryRef, false);
			refView.setOnClickListener(v -> {
				Memory.add(repositoryRef);
				startActivity(new Intent(SourceActivity.this, RepositoryRefActivity.class));
			});
			registerForContextMenu(refView);
			refView.setTag(R.id.tag_object, repositoryRef); // for the context menu
		}
		U.placeNotes(box, f, true);
		U.placeMedia(box, f, true);
		U.placeChangeDate(box, f.getChange());
		if( !citations.list.isEmpty() )
			U.putContainer(box, citations.getProgenitors(), R.string.cited_by);
	}

	@Override
	public void delete() {
		U.updateChangeDate(LibraryFragment.deleteSource(f));
	}
}
