package app.familygem.detail;

import org.folg.gedcom.model.Address;
import app.familygem.Dettaglio;
import app.familygem.Memoria;
import app.familygem.R;
import app.familygem.U;

public class Indirizzo extends Dettaglio {

	Address a;

	@Override
	public void impagina() {
		setTitle(R.string.address);
		placeSlug("ADDR");
		a = (Address)cast(Address.class);
		place(getString(R.string.value), "Value", false, true); // Fortemente deprecato in favore dell'indirizzo frammentato
		place(getString(R.string.name), "Name", false, false); // _name non standard
		place(getString(R.string.line_1), "AddressLine1");
		place(getString(R.string.line_2), "AddressLine2");
		place(getString(R.string.line_3), "AddressLine3");
		place(getString(R.string.postal_code), "PostalCode");
		place(getString(R.string.city), "City");
		place(getString(R.string.state), "State");
		place(getString(R.string.country), "Country");
		placeExtensions(a);
	}

	@Override
	public void elimina() {
		eliminaIndirizzo(Memoria.oggettoContenitore());
		U.updateChangeDate(Memoria.oggettoCapo());
		Memoria.annullaIstanze(a);
	}
}
