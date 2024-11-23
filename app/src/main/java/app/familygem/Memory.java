package app.familygem;

import static app.familygem.Logger.l;

import androidx.annotation.NonNull;

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
import app.familygem.detail.SubmitterActivity;
import app.familygem.profile.ProfileActivity;
import app.familygem.visitor.FindStack;

/**
 * Manager of stacks of hierarchical objects, mainly to display the breadcrumbs in DetailActivity.
 */
public class Memory {

    public static final Map<Class<?>, Class<?>> classes = new HashMap<>();
    private static final Memory memory = new Memory();
    private final List<StepStack> list = new ArrayList<>();

    private Memory() {
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
    public static StepStack getLastStack() {
        if (!memory.list.isEmpty())
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
    public static void setLeader(Object object) {
        setLeader(object, null);
    }

    public static void setLeader(Object object, String tag) {
        addStack();
        Step step = add(object);
        if (tag != null)
            step.tag = tag;
        else if (object instanceof Person)
            step.tag = "INDI";
    }

    /**
     * Adds an object to the end of the last existing stack.
     */
    public static Step add(Object object) {
        Step step = new Step();
        step.object = object;
        getLastStack().add(step);
        return step;
    }

    /**
     * Puts an object in a first step if there are no stacks, or replaces the first step in the last existing stack.
     * In other words, puts the leader object without adding any more stacks.
     */
    public static void replaceLeader(Object object) {
        String tag = object instanceof Family ? "FAM" : "INDI";
        if (memory.list.isEmpty()) {
            setLeader(object, tag);
        } else {
            getLastStack().clear();
            Step step = add(object);
            step.tag = tag;
        }
    }

    /**
     * Returns the object of the first step of the last stack.
     */
    public static Object getLeaderObject() {
        if (!getLastStack().isEmpty())
            return getLastStack().firstElement().object;
        else
            return null;
    }

    /**
     * If the stack has more than one object, gets the second to last object, otherwise returns null.
     */
    public static Object getSecondToLastObject() { // TODO: getPreviousObject()? getPenultimateObject()?
        StepStack stepStack = getLastStack();
        if (stepStack.size() > 1)
            return stepStack.get(stepStack.size() - 2).object;
        else
            return null;
    }

    /**
     * Returns the object in the last step of the last stack.
     */
    public static Object getLastObject() {
        if (getLastStack().isEmpty())
            return null;
        else
            return getLastStack().peek().object;
    }

    /**
     * Removes one or maybe more steps at the end of the stacks.
     * Represents "one step back", called after onBackPressed().
     */
    public static void stepBack() {
        StepStack stack = getLastStack();
        while (!stack.isEmpty() && stack.lastElement().weak)
            stack.pop();
        if (!stack.isEmpty())
            stack.pop();
        if (stack.isEmpty())
            memory.list.remove(stack);
    }

    /**
     * Makes an object null in all steps of all stacks.
     * The objects in any subsequent step are also annulled.
     */
    public static void setInstanceAndAllSubsequentToNull(Object object) { // TODO: nullObject()? nullObjectAndFollower()?
        for (StepStack stepStack : memory.list) {
            boolean following = false;
            for (Step step : stepStack) {
                if (step.object != null && (step.object.equals(object) || following)) {
                    step.object = null;
                    following = true;
                }
            }
        }
        // Nulls some remaining object to avoid repetitions on back pressed
        Object previous = null;
        for (int i = memory.list.size() - 1; i >= 0; i--) {
            StepStack stepStack = memory.list.get(i);
            for (int j = stepStack.size() - 1; j >= 0; j--) {
                Step step = stepStack.get(j);
                if (step.object != null) {
                    if (step.object.equals(previous)) step.object = null;
                    else previous = step.object;
                }
            }
        }
    }

    /**
     * Sets the given object as leader of a new stack, and nulls previous instances of the object.
     */
    public static void makeLeaderStep(Object object) {
        stepBack();
        setInstanceAndAllSubsequentToNull(object);
        setLeader(object);
    }

    /**
     * Sets the object as last step in a new weak stack, and nulls all other steps containing the object.
     */
    public static void makeLastStep(Object object) {
        stepBack();
        for (StepStack stepStack : memory.list) {
            for (Step step : stepStack) {
                if (step.object != null && step.object.equals(object)) step.object = null;
            }
        }
        new FindStack(Global.gc, object, true);
    }

    public static class StepStack extends Stack<Step> {
        @NonNull
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            for (Step step : this) {
                builder.append(step);
            }
            return builder.toString();
        }
    }

    public static class Step {
        public Object object;
        public String tag;
        /**
         * {@link FindStack} sets it to true then onBackPressed() the step will be deleted.
         */
        public boolean weak;

        @NonNull
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            if (weak) builder.append("< ");
            if (tag != null) {
                builder.append(tag);
                if (object == null) builder.append("* ");
                else builder.append(" ");
            } else if (object != null)
                builder.append(object.getClass().getSimpleName()).append(" ");
            else
                builder.append("null "); // This should never happen
            return builder.toString();
        }
    }

    public static void log() {
        StringBuilder builder = new StringBuilder();
        for (StepStack stepStack : memory.list) {
            builder.append(stepStack).append("\n");
        }
        builder.append("- - - - -");
        l(builder.toString());
    }
}
