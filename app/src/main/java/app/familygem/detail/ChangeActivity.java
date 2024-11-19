package app.familygem.detail;

import android.view.Menu;

import org.folg.gedcom.model.Change;
import org.folg.gedcom.model.DateTime;

import app.familygem.DetailActivity;
import app.familygem.R;
import app.familygem.U;
import app.familygem.util.NoteUtil;

/**
 * Detail of the change date and time of a record.
 * Date and time can't be edited here, as they are automatically updated on saving the tree.
 */
public class ChangeActivity extends DetailActivity {

    Change change;

    @Override
    protected void format() {
        setTitle(R.string.change_date);
        placeSlug("CHAN");
        change = (Change)cast(Change.class);
        DateTime dateTime = change.getDateTime();
        if (dateTime != null) {
            if (dateTime.getValue() != null)
                U.place(box, getString(R.string.value), dateTime.getValue());
            if (dateTime.getTime() != null)
                U.place(box, getString(R.string.time), dateTime.getTime());
        }
        placeExtensions(change);
        NoteUtil.INSTANCE.placeNotes(box, change);
    }

    // Options menu not needed
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }
}
