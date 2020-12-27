package app.familygem.dettaglio;

import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.Source;
import java.util.ArrayList;
import java.util.List;
import app.familygem.Dettaglio;
import app.familygem.Globale;
import app.familygem.Magazzino;
import app.familygem.R;
import app.familygem.U;

public class Archivio extends Dettaglio {

	Repository a;

	@Override
	public void impagina() {
		setTitle( R.string.repository );
		a = (Repository) casta( Repository.class );
		mettiBava( "REPO", a.getId() );
		metti( getString(R.string.value), "Value", false, true );	// Non molto Gedcom standard
		metti( getString(R.string.name), "Name" );
		metti( getString(R.string.address), a.getAddress() );
		metti( getString(R.string.www), "Www" );
		metti( getString(R.string.email), "Email" );
		metti( getString(R.string.telephone), "Phone" );
		metti( getString(R.string.fax), "Fax" );
		metti( getString(R.string.rin), "Rin", false, false );
		mettiEstensioni( a );
		U.mettiNote( box, a, true );
		U.cambiamenti( box, a.getChange() );

		// Raccoglie e mostra le fonti che citano questo Repository
		List<Source> fontiCitanti = new ArrayList<>();
		for( Source fonte : Globale.gc.getSources() )
			if( fonte.getRepositoryRef() != null && fonte.getRepositoryRef().getRef() != null
					&& fonte.getRepositoryRef().getRef().equals(a.getId()) )
				fontiCitanti.add( fonte );
		if( !fontiCitanti.isEmpty() )
			U.mettiDispensa( box, fontiCitanti.toArray(), R.string.sources );
		a.putExtension( "fonti", fontiCitanti.size() );
	}

	@Override
	public void elimina() {
		U.aggiornaDate( (Object[]) Magazzino.elimina( a ) );
	}
}
