package app.familygem.detail;

import android.text.InputType;

import org.folg.gedcom.model.Address;

import app.familygem.DetailActivity;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.util.ChangeUtil;

public class AddressActivity extends DetailActivity {

    Address address;

    @Override
    protected void format() {
        setTitle(R.string.address);
        placeSlug("ADDR");
        address = (Address)cast(Address.class);
        int capWords = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS;
        int capChars = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
        // Deprecated by GEDCOM standard in favor of the fragmented address
        place(getString(R.string.value), "Value", false, capWords | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        place(getString(R.string.name), "Name", false, capWords); // '_NAME' tag not GEDCOM standard
        place(getString(R.string.line_1), "AddressLine1", true, capWords);
        place(getString(R.string.line_2), "AddressLine2", true, capWords);
        place(getString(R.string.line_3), "AddressLine3", true, capWords);
        place(getString(R.string.postal_code), "PostalCode", true, capChars);
        place(getString(R.string.city), "City", true, capWords);
        place(getString(R.string.state), "State", true, capChars);
        place(getString(R.string.country), "Country", true, capWords);
        placeExtensions(address);
    }

    @Override
    public void delete() {
        deleteAddress(Memory.getSecondToLastObject());
        ChangeUtil.INSTANCE.updateChangeDate(Memory.getLeaderObject());
        Memory.setInstanceAndAllSubsequentToNull(address);
    }
}
