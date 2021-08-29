package app.familygem.dettaglio;

import android.view.Menu;
import org.folg.gedcom.model.Change;
import org.folg.gedcom.model.DateTime;
import app.familygem.Dettaglio;
import app.familygem.R;
import app.familygem.U;

public class Cambiamenti extends Dettaglio {

	Change c;

	@Override
	public void impagina() {
		setTitle(R.string.change_date);
		mettiBava("CHAN");
		c = (Change)casta(Change.class);
		DateTime dateTime = c.getDateTime();
		if( dateTime != null ) {
			if( dateTime.getValue() != null )
				U.metti(box, getString(R.string.value), dateTime.getValue());
			if( dateTime.getTime() != null )
				U.metti(box, getString(R.string.time), dateTime.getTime());
		}
		mettiEstensioni(c);
		U.mettiNote(box, c, true);
	}

	// Qui non c'è bisogno di un menu
	@Override
	public boolean onCreateOptionsMenu( Menu m ) {
		return false;
	}
}
