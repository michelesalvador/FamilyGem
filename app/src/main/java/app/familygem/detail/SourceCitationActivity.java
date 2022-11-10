package app.familygem.detail;

import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;
import app.familygem.DetailActivity;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import static app.familygem.Global.gc;

public class SourceCitationActivity extends DetailActivity {

	SourceCitation c;

	@Override
	public void format() {
		placeSlug("SOUR");
		c = (SourceCitation)cast(SourceCitation.class);
		if( c.getSource(gc) != null ) {  // valid source CITATION
			setTitle(R.string.source_citation);
			U.placeSource(box, c.getSource(gc), true);
		} else if( c.getRef() != null ) {  // source CITATION of a non-existent source (perhaps deleted)
			setTitle(R.string.inexistent_source_citation);
		} else {    // source NOTE
			setTitle(R.string.source_note);
			place(getString(R.string.value), "Value", true, true);
		}
		place(getString(R.string.page), "Page", true, true);
		place(getString(R.string.date), "Date");
		place(getString(R.string.text), "Text", true, true);    // applies to both sourceNote and sourceCitation
		//c.getTextOrValue();	practically useless
		//if( c.getDataTagContents() != null )
		//	U.metti( box, "Data Tag Contents", c.getDataTagContents().toString() );	// COMBINED DATA TEXT
		place(getString(R.string.certainty), "Quality");    // a number from 0 to 3
		//metti( "Ref", "Ref", false, false ); // the id of the source
		placeExtensions(c);
		U.placeNotes(box, c, true);
		U.placeMedia(box, c, true);
	}

	@Override
	public void delete() {
		Object container = Memory.getSecondToLastObject();
		if( container instanceof Note )	// Note doesn't extend SourceCitationContainer
			((Note)container).getSourceCitations().remove( c );
		else
			((SourceCitationContainer)container).getSourceCitations().remove( c );
		U.updateChangeDate( Memory.firstObject() );
		Memory.setInstanceAndAllSubsequentToNull(c);
	}
}
