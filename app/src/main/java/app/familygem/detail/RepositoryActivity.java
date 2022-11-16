package app.familygem.detail;

import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.Source;
import java.util.ArrayList;
import java.util.List;
import app.familygem.DetailActivity;
import app.familygem.Global;
import app.familygem.RepositoriesFragment;
import app.familygem.R;
import app.familygem.U;

public class RepositoryActivity extends DetailActivity {

	Repository a;

	@Override
	public void format() {
		setTitle(R.string.repository);
		a = (Repository)cast(Repository.class);
		placeSlug("REPO", a.getId());
		place(getString(R.string.value), "Value", false, true);    // Not very standard Gedcom //Non molto Gedcom standard
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

		// Collects and displays the sources citing this Repository //Raccoglie e mostra le fonti che citano questo Repository
		List<Source> citingSources = new ArrayList<>();
		for( Source source : Global.gc.getSources() )
			if( source.getRepositoryRef() != null && source.getRepositoryRef().getRef() != null
					&& source.getRepositoryRef().getRef().equals(a.getId()) )
				citingSources.add(source);
		if( !citingSources.isEmpty() )
			U.putContainer(box, citingSources.toArray(), R.string.sources);
	}

	@Override
	public void delete() {
		U.updateChangeDate((Object[]) RepositoriesFragment.delete(a));
	}
}
