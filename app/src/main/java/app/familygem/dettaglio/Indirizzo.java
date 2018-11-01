package app.familygem.dettaglio;

import org.folg.gedcom.model.Address;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.PersonFamilyCommonContainer;
import app.familygem.Dettaglio;
import app.familygem.Ponte;
import app.familygem.R;
import app.familygem.U;

public class Indirizzo extends Dettaglio {

	Address a = (Address) Ponte.ricevi( "oggetto" );

	@Override
	public void impagina() {
		if( a != null ) {
			oggetto = a;
			setTitle( R.string.address );
			vistaId.setText( "ADDR" );
			metti( getString(R.string.value), "Value", false, true );	// Fortemente deprecato in favore dell'indirizzo frammentato
			metti( getString(R.string.name), "Name", false, false );	// _name non standard
			metti( getString(R.string.line_1), "AddressLine1" );
			metti( getString(R.string.line_2), "AddressLine2" );
			metti( getString(R.string.line_3), "AddressLine3" );
			metti( getString(R.string.postal_code), "PostalCode" );
			metti( getString(R.string.city), "City" );
			metti( getString(R.string.state), "State" );
			metti( getString(R.string.country), "Country" );
			mettiEstensioni( a );
		}
	}

	@Override
	public void elimina() {
		((EventFact)contenitore).setAddress( null );	// ok
	}
}
