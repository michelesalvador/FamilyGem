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
 * It's equivalent to {@link SourcesFragment#countSourceCitations()} but is probably slower.
 */
public class CountSourceCitations extends Visitor {

    public int count = 0;
    private final String id;

    public CountSourceCitations(String id) {
        this.id = id;
    }

    @Override
    public boolean visit(Person person) {
        for (SourceCitation c : person.getSourceCitations())
            if (c.getRef() != null && c.getRef().equals(id)) count++;
        return true;
    }

    @Override
    public boolean visit(Family family) {
        for (SourceCitation c : family.getSourceCitations())
            if (c.getRef() != null && c.getRef().equals(id)) count++;
        return true;
    }

    @Override
    public boolean visit(Name name) {
        for (SourceCitation c : name.getSourceCitations())
            if (c.getRef() != null && c.getRef().equals(id)) count++;
        return true;
    }

    @Override
    public boolean visit(EventFact fact) {
        for (SourceCitation c : fact.getSourceCitations())
            if (c.getRef() != null && c.getRef().equals(id)) count++;
        return true;
    }

    @Override
    public boolean visit(Note note) {
        for (SourceCitation c : note.getSourceCitations())
            if (c.getRef() != null && c.getRef().equals(id)) count++;
        return true;
    }
}
