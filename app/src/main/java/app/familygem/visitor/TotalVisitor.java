package app.familygem.visitor;

import org.folg.gedcom.model.Change;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.ExtensionContainer;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Header;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.RepositoryRef;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.Submitter;
import org.folg.gedcom.model.Visitor;

/**
 * Abstract visitor that visits all the possible GEDCOM containers distinguishing the leaders
 * (the top-level GEDCOM objects, that go at first on the stacks).
 */
public abstract class TotalVisitor extends Visitor {

    boolean visit(ExtensionContainer object, boolean isLeader) {
        return true;
    }

    @Override
    public boolean visit(Header header) {
        return visit(header, true);
    }

    @Override
    public boolean visit(Person person) {
        return visit(person, true);
    }

    @Override
    public boolean visit(Family family) {
        return visit(family, true);
    }

    @Override
    public boolean visit(Source source) {
        return visit(source, true);
    }

    @Override
    public boolean visit(Repository repository) {
        return visit(repository, true);
    }

    @Override
    public boolean visit(Submitter submitter) {
        return visit(submitter, true);
    }

    @Override
    public boolean visit(Media media) {
        return visit(media, media.getId() != null);
    }

    @Override
    public boolean visit(Note note) {
        return visit(note, note.getId() != null);
    }

    @Override
    public boolean visit(Name name) {
        return visit(name, false);
    }

    @Override
    public boolean visit(EventFact eventFact) {
        return visit(eventFact, false);
    }

    @Override
    public boolean visit(SourceCitation sourceCitation) {
        return visit(sourceCitation, false);
    }

    @Override
    public boolean visit(RepositoryRef repositoryRef) {
        return visit(repositoryRef, false);
    }

    @Override
    public boolean visit(Change change) {
        return visit(change, false);
    }
}
