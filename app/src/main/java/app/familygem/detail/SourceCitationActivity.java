package app.familygem.detail;

import static app.familygem.Global.gc;

import android.text.InputType;

import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;

import app.familygem.DetailActivity;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import app.familygem.util.ChangeUtil;
import app.familygem.util.NoteUtil;
import app.familygem.util.SourceUtil;

public class SourceCitationActivity extends DetailActivity {

    SourceCitation citation;

    @Override
    protected void format() {
        placeSlug("SOUR");
        citation = (SourceCitation)cast(SourceCitation.class);
        if (citation.getSource(gc) != null) { // Citation of an existing source
            setTitle(R.string.source_citation);
            SourceUtil.INSTANCE.placeSource(box, citation.getSource(gc), true);
        } else if (citation.getRef() != null) { // Citation of a non-existent source (maybe deleted)
            setTitle(R.string.inexistent_source_citation); // TODO: maybe this can be removed
        } else { // Note-source
            setTitle(R.string.source_note);
            place(getString(R.string.value), "Value", true, InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        }
        place(getString(R.string.page), "Page");
        place(getString(R.string.date), "Date");
        place(getString(R.string.text), "Text", true, InputType.TYPE_TEXT_FLAG_MULTI_LINE); // Applies to both note-source and source citation
        place(getString(R.string.certainty), "Quality"); // A number from 0 to 3
        //c.getTextOrValue(); // Practically useless
        //if (c.getDataTagContents() != null)
        //    U.place(box, "Data Tag Contents", c.getDataTagContents().toString()); // COMBINED DATA TEXT
        //place("Ref", "Ref", false, false); // The ID of the source, useless here
        placeExtensions(citation);
        NoteUtil.INSTANCE.placeNotes(box, citation);
        U.placeMedia(box, citation, true);
    }

    @Override
    public void delete() {
        Object container = Memory.getSecondToLastObject();
        if (container instanceof Note) // Note doesn't extend SourceCitationContainer
            ((Note)container).getSourceCitations().remove(citation);
        else
            ((SourceCitationContainer)container).getSourceCitations().remove(citation);
        ChangeUtil.INSTANCE.updateChangeDate(Memory.getLeaderObject());
        Memory.setInstanceAndAllSubsequentToNull(citation);
    }
}
