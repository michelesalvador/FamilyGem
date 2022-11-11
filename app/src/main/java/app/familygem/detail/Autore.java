package app.familygem.detail;

import org.folg.gedcom.model.Submitter;
import app.familygem.Dettaglio;
import app.familygem.Podio;
import app.familygem.R;
import app.familygem.U;

public class Autore extends Dettaglio {

	Submitter a;

	@Override
	public void impagina() {
		setTitle(R.string.submitter);
		a = (Submitter)cast(Submitter.class);
		placeSlug("SUBM", a.getId());
		place(getString(R.string.value), "Value", false, true); // Value of what?
		place(getString(R.string.name), "Name");
		place(getString(R.string.address), a.getAddress());
		place(getString(R.string.www), "Www");
		place(getString(R.string.email), "Email");
		place(getString(R.string.telephone), "Phone");
		place(getString(R.string.fax), "Fax");
		place(getString(R.string.language), "Language");
		place(getString(R.string.rin), "Rin", false, false);
		placeExtensions(a);
		U.placeChangeDate(box, a.getChange());
	}

	@Override
	public void elimina() {
		// Ricordiamo che almeno un autore deve essere specificato
		// non aggiorna la data di nessun record
		Podio.deleteSubmitter(a);
	}
}
