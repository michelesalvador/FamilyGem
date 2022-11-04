package app.familygem.detail;

import android.view.Menu;
import org.folg.gedcom.model.Change;
import org.folg.gedcom.model.DateTime;
import app.familygem.DetailActivity;
import app.familygem.R;
import app.familygem.U;

public class ChangesActivity extends DetailActivity {

	Change c;

	@Override
	public void format() {
		setTitle(R.string.change_date);
		placeSlug("CHAN");
		c = (Change)cast(Change.class);
		DateTime dateTime = c.getDateTime();
		if( dateTime != null ) {
			if( dateTime.getValue() != null )
				U.place(box, getString(R.string.value), dateTime.getValue());
			if( dateTime.getTime() != null )
				U.place(box, getString(R.string.time), dateTime.getTime());
		}
		placeExtensions(c);
		U.placeNotes(box, c, true);
	}

	// You don't need a menu here
	@Override
	public boolean onCreateOptionsMenu(Menu m) {
		return false;
	}
}
