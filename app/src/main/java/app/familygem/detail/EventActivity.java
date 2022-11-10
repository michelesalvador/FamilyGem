package app.familygem.detail;

import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.PersonFamilyCommonContainer;
import java.util.Arrays;
import app.familygem.DetailActivity;
import app.familygem.IndividualEventsFragment;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;

public class EventActivity extends DetailActivity {

	EventFact e;
	/**
	 * List of event tags useful to avoid putting the Value of the EventFact
	 * */
	String[] eventTags = { "BIRT","CHR","DEAT","BURI","CREM","ADOP","BAPM","BARM","BASM","BLES", // Individual events
			"CHRA","CONF","FCOM","ORDN","NATU","EMIG","IMMI","CENS","PROB","WILL","GRAD","RETI",
			"ANUL","DIV","DIVF","ENGA","MARB","MARC","MARR","MARL","MARS" }; // Family events

	@Override
	public void format() {
		e = (EventFact)cast(EventFact.class);
		if( Memory.firstObject() instanceof Family )
			setTitle(writeEventTitle((Family) Memory.firstObject(), e));
		else
			setTitle(IndividualEventsFragment.writeEventTitle(e)); // It includes e.getDisplayType()
		placeSlug(e.getTag());
		if( Arrays.asList(eventTags).contains(e.getTag()) ) // is an event (without Value)
			place(getString(R.string.value), "Value", false, true);
		else // all other cases, usually attributes (with Value)
			place(getString(R.string.value), "Value", true, true);
		if( e.getTag().equals("EVEN") || e.getTag().equals("MARR") )
			place(getString(R.string.type), "Type"); // Type of event, relationship etc.
		else
			place(getString(R.string.type), "Type", false, false);
		place(getString(R.string.date), "Date");
		place(getString(R.string.place), "Place");
		place(getString(R.string.address), e.getAddress());
		if( e.getTag() != null && e.getTag().equals("DEAT") )
			place(getString(R.string.cause), "Cause");
		else
			place(getString(R.string.cause), "Cause", false, false);
		place(getString(R.string.www), "Www", false, false);
		place(getString(R.string.email), "Email", false, false);
		place(getString(R.string.telephone), "Phone", false, false);
		place(getString(R.string.fax), "Fax", false, false);
		place(getString(R.string.rin), "Rin", false, false);
		place(getString(R.string.user_id), "Uid", false, false);
		//otherMethods = { "WwwTag", "EmailTag", "UidTag" };
		placeExtensions(e);
		U.placeNotes(box, e, true);
		U.placeMedia(box, e, true);
		U.placeSourceCitations(box, e);
	}

	@Override
	public void delete() {
		((PersonFamilyCommonContainer) Memory.getSecondToLastObject()).getEventsFacts().remove(e);
		U.updateChangeDate(Memory.firstObject());
		Memory.setInstanceAndAllSubsequentToNull(e);
	}

	/**
	 * Delete the main empty tags and eventually add the 'Y'
	 * Elimina i principali tag vuoti e eventualmente aggiunge la 'Y'
	 * */
	public static void cleanUpTag(EventFact ef ) {
		if( ef.getType() != null && ef.getType().isEmpty() ) ef.setType(null);
		if( ef.getDate() != null && ef.getDate().isEmpty() ) ef.setDate(null);
		if( ef.getPlace() != null && ef.getPlace().isEmpty() ) ef.setPlace(null);
		String tag = ef.getTag();
		if( tag != null && (tag.equals("BIRT") || tag.equals("CHR") || tag.equals("DEAT")
				|| tag.equals("MARR") || tag.equals("DIV")) ) {
			if( ef.getType() == null && ef.getDate() == null && ef.getPlace() == null
					&& ef.getAddress() == null && ef.getCause() == null )
				ef.setValue("Y");
			else
				ef.setValue(null);
		}
		if( ef.getValue() != null && ef.getValue().isEmpty() ) ef.setValue(null);
	}
}
