package app.familygem.dettaglio;

import org.folg.gedcom.model.Repository;
import app.familygem.Dettaglio;
import app.familygem.Magazzino;
import app.familygem.Ponte;
import app.familygem.R;
import app.familygem.U;

public class Archivio extends Dettaglio {

	Repository a = (Repository) Ponte.ricevi( "oggetto" );

	@Override
	public void impagina() {
		if( a != null ) {
			oggetto = a;
			setTitle( R.string.repository );
			vistaId.setText( a.getId() );
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
		}
	}

	@Override
	public void elimina() {
		Magazzino.elimina( a );
	}
}
