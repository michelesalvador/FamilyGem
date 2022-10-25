package app.familygem.detail;

import android.app.Activity;
import org.folg.gedcom.model.Note;
import app.familygem.DetailActivity;
import app.familygem.Global;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import app.familygem.visitor.NoteReferences;

public class NoteActivity extends DetailActivity {

	Note n;

	@Override
	public void format() {
		n = (Note)cast(Note.class);
		if( n.getId() == null ) {
			setTitle(R.string.note);
			placeSlug("NOTE");
		} else {
			setTitle(R.string.shared_note);
			placeSlug("NOTE", n.getId());
		}
		place(getString(R.string.text), "Value", true, true);
		place(getString(R.string.rin), "Rin", false, false);
		placeExtensions(n);
		U.placeSourceCitations(box, n);
		U.placeChangeDate(box, n.getChange());
		if( n.getId() != null ) {
			NoteReferences rifNota = new NoteReferences(Global.gc, n.getId(), false);
			if( rifNota.tot > 0 )
				U.mettiDispensa(box, rifNota.capostipiti.toArray(), R.string.shared_by);
		} else if( ((Activity)box.getContext()).getIntent().getBooleanExtra("daQuaderno", false) ) {
			U.mettiDispensa(box, Memory.firstObject(), R.string.written_in);
		}
	}

	@Override
	public void delete() {
		U.updateChangeDate(U.deleteNote(n, null));
	}
}
