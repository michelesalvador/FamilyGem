package app.familygem.detail;

import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.Source;
import java.util.ArrayList;
import java.util.List;
import app.familygem.DetailActivity;
import app.familygem.Global;
import app.familygem.RepositoryFragment;
import app.familygem.R;
import app.familygem.U;

public class RepositoryActivity extends DetailActivity {

	Repository a;

	@Override
	public void format() {
		setTitle(R.string.repository);
		a = (Repository)cast(Repository.class);
		placeSlug("REPO", a.getId());
		place(getString(R.string.value), "Value", false, true);    // Non molto Gedcom standard
		place(getString(R.string.name), "Name");
		place(getString(R.string.address), a.getAddress());
		place(getString(R.string.www), "Www");
		place(getString(R.string.email), "Email");
		place(getString(R.string.telephone), "Phone");
		place(getString(R.string.fax), "Fax");
		place(getString(R.string.rin), "Rin", false, false);
		placeExtensions(a);
		U.placeNotes(box, a, true);
		U.placeChangeDate(box, a.getChange());

		// Raccoglie e mostra le fonti che citano questo Repository
		List<Source> fontiCitanti = new ArrayList<>();
		for( Source fonte : Global.gc.getSources() )
			if( fonte.getRepositoryRef() != null && fonte.getRepositoryRef().getRef() != null
					&& fonte.getRepositoryRef().getRef().equals(a.getId()) )
				fontiCitanti.add(fonte);
		if( !fontiCitanti.isEmpty() )
			U.mettiDispensa(box, fontiCitanti.toArray(), R.string.sources);
	}

	@Override
	public void delete() {
		U.updateChangeDate((Object[]) RepositoryFragment.delete(a));
	}
}
