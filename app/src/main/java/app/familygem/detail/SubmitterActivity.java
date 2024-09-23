package app.familygem.detail;

import android.text.InputType;

import org.folg.gedcom.model.Submitter;

import app.familygem.DetailActivity;
import app.familygem.R;
import app.familygem.main.SubmittersFragment;
import app.familygem.util.ChangeUtil;

public class SubmitterActivity extends DetailActivity {

    Submitter submitter;

    @Override
    protected void format() {
        setTitle(R.string.submitter);
        submitter = (Submitter)cast(Submitter.class);
        placeSlug("SUBM", submitter.getId());
        place(getString(R.string.value), "Value", false, 0); // A submitter shouldn't have any Value
        place(getString(R.string.name), "Name", true, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        place(getString(R.string.address), submitter.getAddress());
        place(getString(R.string.www), "Www", true, InputType.TYPE_CLASS_TEXT);
        place(getString(R.string.email), "Email", true, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        place(getString(R.string.telephone), "Phone", true, InputType.TYPE_CLASS_PHONE);
        place(getString(R.string.fax), "Fax", true, InputType.TYPE_CLASS_PHONE);
        place(getString(R.string.language), "Language");
        place(getString(R.string.rin), "Rin", false, 0);
        placeExtensions(submitter);
        ChangeUtil.INSTANCE.placeChangeDate(box, submitter.getChange());
    }

    @Override
    public void delete() {
        // Doesn't update the change date of any record.
        SubmittersFragment.deleteSubmitter(submitter);
    }
}
