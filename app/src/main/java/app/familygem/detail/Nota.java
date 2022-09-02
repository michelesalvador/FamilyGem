package app.familygem.detail;

import android.app.Activity;
import org.folg.gedcom.model.Note;
import app.familygem.Dettaglio;
import app.familygem.Global;
import app.familygem.Memoria;
import app.familygem.R;
import app.familygem.U;
import app.familygem.visitor.RiferimentiNota;

public class Nota extends Dettaglio {

	Note n;

	@Override
	public void impagina() {
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
			RiferimentiNota rifNota = new RiferimentiNota(Global.gc, n.getId(), false);
			if( rifNota.tot > 0 )
				U.mettiDispensa(box, rifNota.capostipiti.toArray(), R.string.shared_by);
		} else if( ((Activity)box.getContext()).getIntent().getBooleanExtra("daQuaderno", false) ) {
			U.mettiDispensa(box, Memoria.oggettoCapo(), R.string.written_in);
		}
	}

	@Override
	public void elimina() {
		U.updateChangeDate(U.eliminaNota(n, null));
	}
}
