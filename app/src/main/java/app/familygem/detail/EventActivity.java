package app.familygem.detail;

import android.text.InputType;

import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.PersonFamilyCommonContainer;

import java.util.Arrays;

import app.familygem.DetailActivity;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import app.familygem.util.ChangeUtil;
import app.familygem.util.EventUtilKt;
import app.familygem.util.NoteUtil;

public class EventActivity extends DetailActivity {

    EventFact event;
    /**
     * List of event tags useful to avoid putting the Value of the EventFact.
     */
    String[] eventTags = {"BIRT", "CHR", "DEAT", "BURI", "CREM", "ADOP", "BAPM", "BARM", "BASM", "BLES", // Individual events
            "CHRA", "CONF", "FCOM", "ORDN", "NATU", "EMIG", "IMMI", "CENS", "PROB", "WILL", "GRAD", "RETI", "RESI",
            "ANUL", "DIV", "DIVF", "ENGA", "MARB", "MARC", "MARR", "MARL", "MARS"}; // Family events

    @Override
    protected void format() {
        event = (EventFact)cast(EventFact.class);
        setTitle();
        placeSlug(event.getTag());
        if (Arrays.asList(eventTags).contains(event.getTag())) // It's an event (without Value)
            place(getString(R.string.value), "Value", false, 0);
        else // All other cases, usually attributes (with Value)
            place(getString(R.string.value), "Value");
        if (event.getTag().equals("MARR"))
            place(getString(R.string.type), "Type"); // Type of relationship
        else
            place(getString(R.string.type), "Type", event.getTag().equals("EVEN"), 0);
        place(getString(R.string.date), "Date");
        place(getString(R.string.place), "Place");
        place(getString(R.string.address), event.getAddress());
        place(getString(R.string.cause), "Cause", event.getTag() != null && event.getTag().equals("DEAT"), 0);
        place(getString(R.string.www), "Www", false, InputType.TYPE_CLASS_TEXT);
        place(getString(R.string.email), "Email", false, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        place(getString(R.string.telephone), "Phone", false, InputType.TYPE_CLASS_PHONE);
        place(getString(R.string.fax), "Fax", false, InputType.TYPE_CLASS_PHONE);
        place(getString(R.string.rin), "Rin", false, 0);
        place(getString(R.string.user_id), "Uid", false, 0);
        // Other methods are "WwwTag", "EmailTag", "UidTag"
        placeExtensions(event);
        NoteUtil.INSTANCE.placeNotes(box, event);
        U.placeMedia(box, event, true);
        U.placeSourceCitations(box, event);
    }

    @Override
    protected void setTitle() {
        if (Memory.getLeaderObject() instanceof Family)
            setTitle(writeEventTitle((Family)Memory.getLeaderObject(), event));
        else
            setTitle(EventUtilKt.writeTitle(event)); // The title includes event.getDisplayType()
    }

    @Override
    public void delete() {
        ((PersonFamilyCommonContainer)Memory.getSecondToLastObject()).getEventsFacts().remove(event);
        ChangeUtil.INSTANCE.updateChangeDate(Memory.getLeaderObject());
        Memory.setInstanceAndAllSubsequentToNull(event);
    }
}
