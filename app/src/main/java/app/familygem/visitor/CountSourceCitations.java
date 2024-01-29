package app.familygem.visitor;

import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.Visitor;

import app.familygem.main.SourcesFragment;

/**
 * Counter of citations of a source.
 * It would be used to replace {@link SourcesFragment#quanteCitazioni}.
 * It's more accurate in counting, but is four times slower.
 */
public class CountSourceCitations extends Visitor {

    public int count = 0;
    String id;

    CountSourceCitations(String id) {
        this.id = id;
    }

    @Override
    public boolean visit(Person p) {
        for (SourceCitation c : p.getSourceCitations())
            if (c.getRef() != null) // Required because the note-sources have no reference to the source
                if (c.getRef().equals(id)) count++;
        return true;
    }

    @Override
    public boolean visit(Family f) {
        for (SourceCitation c : f.getSourceCitations())
            if (c.getRef() != null)
                if (c.getRef().equals(id)) count++;
        return true;
    }

    @Override
    public boolean visit(Name n) {
        for (SourceCitation c : n.getSourceCitations())
            if (c.getRef() != null)
                if (c.getRef().equals(id)) count++;
        return true;
    }

    @Override
    public boolean visit(EventFact e) {
        for (SourceCitation c : e.getSourceCitations())
            if (c.getRef() != null)
                if (c.getRef().equals(id)) count++;
        return true;
    }

    @Override
    public boolean visit(Note n) {
        for (SourceCitation c : n.getSourceCitations())
            if (c.getRef() != null)
                if (c.getRef().equals(id)) count++;
        return true;
    }
}
