package app.familygem.detail;

import org.folg.gedcom.model.Submitter;
import app.familygem.DetailActivity;
import app.familygem.ListOfAuthorsFragment;
import app.familygem.R;
import app.familygem.U;

public class AuthorActivity extends DetailActivity {

	Submitter a;

	@Override
	public void format() {
		setTitle(R.string.submitter);
		a = (Submitter)cast(Submitter.class);
		placeSlug("SUBM", a.getId());
		place(getString(R.string.value), "Value", false, true);   // Value of what? //Value de che?
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
	public void delete() {
		// Remember that at least one author must be specified // Ricordiamo che almeno un autore deve essere specificato
		// don't update the date of any record // non aggiorna la data di nessun record
		ListOfAuthorsFragment.deleteAuthor( a );
	}
}
