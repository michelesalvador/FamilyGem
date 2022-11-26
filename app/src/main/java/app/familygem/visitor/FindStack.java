package app.familygem.visitor;

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
import java.util.List;

import app.familygem.Memory;

/**
 * Visitor that produces for {@link Memory} a hierarchic stack of objects from a leader record to a given object (target).
 * E.g. Person > Simple Media
 * or Family> Simple Note> SourceCitation> Simple Note
 */
public class FindStack extends Visitor {

    private List<Memory.Step> stack;
    private Object target;
    private boolean found;

    public FindStack(Gedcom gedcom, Object target) {
        stack = Memory.addStack(); // In a new dedicated stack
        this.target = target;
        gedcom.accept(this);
    }

    private boolean opera(Object object, String tag, boolean isLeader) {
        if (!found) {
            if (isLeader)
                stack.clear(); // A leader makes a stack start all over again
            Memory.Step step = new Memory.Step();
            step.object = object;
            step.tag = tag;
            if (!isLeader)
                step.clearStackOnBackPressed = true; // Marks it to delete when onBackPressed()
            stack.add(step);
        }
        if (object.equals(target)) {
            Iterator<Memory.Step> steps = stack.iterator();
            while (steps.hasNext()) {
                CleanStack janitor = new CleanStack(target);
                ((Visitable)steps.next().object).accept(janitor);
                if (janitor.toDelete)
                    steps.remove();
            }
            found = true;
            //Memoria.stampa("FindStack");
        }
        return true;
    }

    @Override
    public boolean visit(Header step) {
        return opera(step, "HEAD", true);
    }

    @Override
    public boolean visit(Person step) {
        return opera(step, "INDI", true);
    }

    @Override
    public boolean visit(Family step) {
        return opera(step, "FAM", true);
    }

    @Override
    public boolean visit(Source step) {
        return opera(step, "SOUR", true);
    }

    @Override
    public boolean visit(Repository step) {
        return opera(step, "REPO", true);
    }

    @Override
    public boolean visit(Submitter step) {
        return opera(step, "SUBM", true);
    }

    @Override
    public boolean visit(Media step) {
        return opera(step, "OBJE", step.getId() != null);
    }

    @Override
    public boolean visit(Note step) {
        return opera(step, "NOTE", step.getId() != null);
    }

    @Override
    public boolean visit(Name step) {
        return opera(step, "NAME", false);
    }

    @Override
    public boolean visit(EventFact step) {
        return opera(step, step.getTag(), false);
    }

    @Override
    public boolean visit(SourceCitation step) {
        return opera(step, "SOUR", false);
    }

    @Override
    public boolean visit(RepositoryRef step) {
        return opera(step, "REPO", false);
    }

    @Override
    public boolean visit(Change step) {
        return opera(step, "CHAN", false);
    }

    /* GedcomTag is not Visitable and therefore the visit does not continue
    @Override
    public boolean visit(String extensionKey, Object extensions) {
        if (extensionKey.equals(ModelParser.MORE_TAGS_EXTENSION_KEY)) {
            for (GedcomTag ext : (List<GedcomTag>)extensions) {
                opera(ext, ext.getTag(), false);
            }
        }
        return true;
    }*/
}
