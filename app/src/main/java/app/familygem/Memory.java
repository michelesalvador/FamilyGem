package app.familygem;

import org.folg.gedcom.model.Address;
import org.folg.gedcom.model.Change;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.GedcomTag;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.RepositoryRef;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.Submitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import app.familygem.detail.AddressActivity;
import app.familygem.detail.SubmitterActivity;
import app.familygem.detail.ChangeActivity;
import app.familygem.detail.EventActivity;
import app.familygem.detail.ExtensionActivity;
import app.familygem.detail.FamilyActivity;
import app.familygem.detail.MediaActivity;
import app.familygem.detail.NameActivity;
import app.familygem.detail.NoteActivity;
import app.familygem.detail.RepositoryActivity;
import app.familygem.detail.RepositoryRefActivity;
import app.familygem.detail.SourceActivity;
import app.familygem.detail.SourceCitationActivity;
import app.familygem.visitor.FindStack;

/**
 * Manager of stacks of hierarchical objects, mainly to display the breadcrumbs in DetailActivity.
 */
public class Memory {

    static Map<Class, Class> classes = new HashMap<>();
    private static final Memory memory = new Memory();
    List<StepStack> list = new ArrayList<>();

    Memory() {
        classes.put(Person.class, ProfileActivity.class);
        classes.put(Repository.class, RepositoryActivity.class);
        classes.put(RepositoryRef.class, RepositoryRefActivity.class);
        classes.put(Submitter.class, SubmitterActivity.class);
        classes.put(Change.class, ChangeActivity.class);
        classes.put(SourceCitation.class, SourceCitationActivity.class);
        classes.put(GedcomTag.class, ExtensionActivity.class);
        classes.put(EventFact.class, EventActivity.class);
        classes.put(Family.class, FamilyActivity.class);
        classes.put(Source.class, SourceActivity.class);
        classes.put(Media.class, MediaActivity.class);
        classes.put(Address.class, AddressActivity.class);
        classes.put(Name.class, NameActivity.class);
        classes.put(Note.class, NoteActivity.class);
    }

    /**
     * Returns the last stack of the list, if there is at least one.
     * Otherwise returns an empty stack just to not return null.
     */
    static StepStack getStepStack() { // TODO: getLastStack()
        if (memory.list.size() > 0)
            return memory.list.get(memory.list.size() - 1);
        else
            return new StepStack(); // An empty stack that is not added to the list
    }

    /**
     * Adds a new stack to the list and returns it.
     */
    public static StepStack addStack() {
        StepStack stepStack = new StepStack();
        memory.list.add(stepStack);
        return stepStack;
    }

    /**
     * Adds an object in the first position of a new stack.
     */
    public static void setFirst(Object object) { // TODO: setLeader()
        setFirst(object, null);
    }

    public static void setFirst(Object object, String tag) { // TODO: setLeader()
        addStack();
        Step step = add(object);
        if (tag != null)
            step.tag = tag;
        else if (object instanceof Person)
            step.tag = "INDI";
        //print("setFirst");
    }

    // Aggiunge un object alla fine dell'ultima pila esistente.
    public static Step add(Object object) {
        Step step = new Step();
        step.object = object;
        getStepStack().add(step);
        //print("add");
        return step;
    }

    /**
     * Puts an object in a first step if there are no stacks, or replaces the first step in the last existing stack.
     * In other words, puts the leader object without adding any more stacks.
     */
    public static void replaceFirst(Object object) { // TODO: replaceLeader()
        String tag = object instanceof Family ? "FAM" : "INDI";
        if (memory.list.size() == 0) {
            setFirst(object, tag);
        } else {
            getStepStack().clear();
            Step step = add(object);
            step.tag = tag;
        }
        //print("replaceFirst");
    }

    /**
     * Returns the object of the first step of the last stack.
     */
    public static Object firstObject() { // TODO: getLeaderObject()
        if (getStepStack().size() > 0)
            return getStepStack().firstElement().object;
        else
            return null;
    }

    /**
     * If the stack has more than one object, gets the second to last object, otherwise returns null.
     */
    public static Object getSecondToLastObject() { // TODO: getPreviousObject()? getPenultimateObject()?
        StepStack stepStack = getStepStack();
        if (stepStack.size() > 1)
            return stepStack.get(stepStack.size() - 2).object;
        else
            return null;
    }

    /**
     * Returns the object in the last step of the last stack.
     */
    public static Object getObject() { // TODO: getLastObject()
        if (getStepStack().size() == 0)
            return null;
        else
            return getStepStack().peek().object;
    }

    /**
     * Removes one or maybe more steps at the end of the stacks.
     * Represents "one step back", called after onBackPressed().
     */
    public static void clearStackAndRemove() { // TODO: stepBack()
        while (getStepStack().size() > 0 && getStepStack().lastElement().clearStackOnBackPressed)
            getStepStack().pop();
        if (getStepStack().size() > 0)
            getStepStack().pop();
        if (getStepStack().isEmpty())
            memory.list.remove(getStepStack());
        //print("back");
    }

    /**
     * Makes an object null in all steps of all stacks.
     * The objects in any subsequent step are also annulled.
     */
    public static void setInstanceAndAllSubsequentToNull(Object object) {
        for (StepStack stepStack : memory.list) {
            boolean following = false;
            for (Step step : stepStack) {
                if (step.object != null && (step.object.equals(object) || following)) {
                    step.object = null;
                    following = true;
                }
            }
        }
    }

    // TODO: replace with Log
    public static void print(String intro) {
        if (intro != null)
            s.l(intro);
        for (StepStack stepStack : memory.list) {
            for (Step step : stepStack) {
                String stack = step.clearStackOnBackPressed ? "< " : "";
                if (step.tag != null)
                    s.p(stack + step.tag + " ");
                else if (step.object != null)
                    s.p(stack + step.object.getClass().getSimpleName() + " ");
                else
                    s.p(stack + "Null"); // capita in rarissimi casi
            }
            s.l("");
        }
        s.l("- - - -");
    }

    static class StepStack extends Stack<Step> {
    }

    public static class Step {
        public Object object;
        public String tag;
        /**
         * {@link FindStack} sets it to true then onBackPressed() the step will be deleted.
         */
        public boolean clearStackOnBackPressed; // TODO: deleteOnBackPressed()
    }
}
