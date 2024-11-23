package app.familygem.visitor;

import androidx.annotation.NonNull;

import org.folg.gedcom.model.Change;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Gedcom;
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
import org.folg.gedcom.model.Visitable;
import org.folg.gedcom.model.Visitor;

import java.util.Iterator;

import app.familygem.Memory;

/**
 * Visitor that produces for {@link Memory} a hierarchic stack of "weak" objects from a leader record to a given object (target).
 * E.g. Person > Simple Media
 * or Family > Simple Note > SourceCitation > Simple Note
 */
public class FindStack extends Visitor {

    private final Memory.StepStack stack;
    private final Object target;
    private boolean found;

    public FindStack(Gedcom gedcom, Object target, boolean addToMemory) {
        if (addToMemory) stack = Memory.addStack(); // Stack is added to Memory
        else stack = new Memory.StepStack(); // Stack is only used here
        this.target = target;
        gedcom.accept(this);
    }

    private boolean addStep(Object object, String tag, boolean isLeader) {
        if (!found) {
            if (isLeader) stack.clear(); // A leader makes a stack start all over again
            Memory.Step step = new Memory.Step();
            step.object = object;
            step.tag = tag;
            if (!isLeader) step.weak = true; // Marks it to delete when onBackPressed()
            stack.add(step);
        }
        if (object.equals(target)) {
            // Eventually removes from the resulting stack the not relevant steps
            Iterator<Memory.Step> steps = stack.iterator();
            while (steps.hasNext()) {
                CleanStack cleaner = new CleanStack(target);
                ((Visitable)steps.next().object).accept(cleaner);
                if (cleaner.toDelete) steps.remove();
            }
            found = true;
        }
        return true;
    }

    @Override
    public boolean visit(Header header) {
        return addStep(header, "HEAD", true);
    }

    @Override
    public boolean visit(Person person) {
        return addStep(person, "INDI", true);
    }

    @Override
    public boolean visit(Family family) {
        return addStep(family, "FAM", true);
    }

    @Override
    public boolean visit(Source source) {
        return addStep(source, "SOUR", true);
    }

    @Override
    public boolean visit(Repository repository) {
        return addStep(repository, "REPO", true);
    }

    @Override
    public boolean visit(Submitter submitter) {
        return addStep(submitter, "SUBM", true);
    }

    @Override
    public boolean visit(Media media) {
        return addStep(media, "OBJE", media.getId() != null);
    }

    @Override
    public boolean visit(Note note) {
        return addStep(note, "NOTE", note.getId() != null);
    }

    @Override
    public boolean visit(Name name) {
        return addStep(name, "NAME", false);
    }

    @Override
    public boolean visit(EventFact eventFact) {
        return addStep(eventFact, eventFact.getTag(), false);
    }

    @Override
    public boolean visit(SourceCitation sourceCitation) {
        return addStep(sourceCitation, "SOUR", false);
    }

    @Override
    public boolean visit(RepositoryRef repositoryRef) {
        return addStep(repositoryRef, "REPO", false);
    }

    @Override
    public boolean visit(Change change) {
        return addStep(change, "CHAN", false);
    }

    /* GedcomTag is not Visitable and therefore the visit does not continue
    @Override
    public boolean visit(String extensionKey, Object extensions) {
        if (extensionKey.equals(ModelParser.MORE_TAGS_EXTENSION_KEY)) {
            for (GedcomTag extension : (List<GedcomTag>)extensions) {
                addStep(extension, extension.getTag(), false);
            }
        }
        return true;
    }*/

    /**
     * Returns first object of the stack.
     */
    public Object getLeaderObject() {
        return stack.firstElement().object;
    }

    /**
     * Returns penultimate object of the stack.
     */
    public Object getContainerObject() {
        return stack.get(stack.size() - 2).object;
    }

    @NonNull
    @Override
    public String toString() {
        return stack.toString();
    }
}
