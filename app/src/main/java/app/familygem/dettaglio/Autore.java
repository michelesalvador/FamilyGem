package app.familygem.dettaglio;

import org.folg.gedcom.model.Submitter;
import app.familygem.Dettaglio;
import app.familygem.Magazzino;
import app.familygem.Podio;
import app.familygem.Ponte;
import app.familygem.R;
import app.familygem.U;

import static app.familygem.Globale.gc;

public class Autore extends Dettaglio {

	//Submitter a = gc.getSubmitter();
	Submitter a = (Submitter) Ponte.ricevi( "oggetto" );

	@Override
	public void impagina() {
		if( a != null ) {
			oggetto = a;
			setTitle( R.string.submitter );
			vistaId.setText( a.getId() );
			metti( getString(R.string.value), "Value", false, true );   // Value de che?
			metti( getString(R.string.name), "Name" );
			metti( getString(R.string.address), a.getAddress() );
			metti( getString(R.string.www), "Www" );
			metti( getString(R.string.email), "Email" );
			metti( getString(R.string.telephone), "Phone" );
			metti( getString(R.string.fax), "Fax" );
			metti( getString(R.string.language), "Language" );
			metti( getString(R.string.rin), "Rin", false, false );
			mettiEstensioni( a );
			U.cambiamenti( box, a.getChange() );
		}
	}

	@Override
	public void elimina() {
		// Non ha molto senso, perchè Un autore deve essere specificato
		Podio.elimina( a );
	}
}
