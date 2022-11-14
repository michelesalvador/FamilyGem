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
import app.familygem.detail.RepositoryActivity;
import app.familygem.detail.RepositoryRefActivity;
import app.familygem.detail.AuthorActivity;
import app.familygem.detail.ChangesActivity;
import app.familygem.detail.SourceCitationActivity;
import app.familygem.detail.ExtensionActivity;
import app.familygem.detail.EventActivity;
import app.familygem.detail.FamilyActivity;
import app.familygem.detail.SourceActivity;
import app.familygem.detail.ImageActivity;
import app.familygem.detail.AddressActivity;
import app.familygem.detail.NameActivity;
import app.familygem.detail.NoteActivity;

/**
 * Manage stacks of hierarchical objects for writing a breadcrumb trail in {@link DetailActivity}
 * */
public class Memory {

	static Map<Class,Class> classes = new HashMap<>();
	private static final Memory memory = new Memory();
	List<StepStack> list = new ArrayList<>();

	Memory() {
		classes.put( Person.class, IndividualPersonActivity.class );
		classes.put( Repository.class, RepositoryActivity.class );
		classes.put( RepositoryRef.class, RepositoryRefActivity.class );
		classes.put( Submitter.class, AuthorActivity.class );
		classes.put( Change.class, ChangesActivity.class );
		classes.put( SourceCitation.class, SourceCitationActivity.class );
		classes.put( GedcomTag.class, ExtensionActivity.class );
		classes.put( EventFact.class, EventActivity.class );
		classes.put( Family.class, FamilyActivity.class );
		classes.put( Source.class, SourceActivity.class );
		classes.put( Media.class, ImageActivity.class );
		classes.put( Address.class, AddressActivity.class );
		classes.put( Name.class, NameActivity.class );
		classes.put( Note.class, NoteActivity.class );
	}

	/**
	 * Return the last created stack if there is at least one
	 * or return an empty one just to not return null
	 * */
	static StepStack getStepStack() {
		if( memory.list.size() > 0 )
			return memory.list.get( memory.list.size() - 1 );
		else
			return new StepStack(); // an empty stack that is not added to the list
	}

	public static StepStack addStack() {
		StepStack stepStack = new StepStack();
		memory.list.add(stepStack);
		return stepStack;
	}

	/**
	 * Adds the first object to a new stack
	 * */
	public static void setFirst(Object object ) {
		setFirst( object, null );
	}

	public static void setFirst(Object object, String tag ) {
		addStack();
		Step step = add( object );
		if( tag != null )
			step.tag = tag;
		else if( object instanceof Person )
			step.tag = "INDI";
		//log("setPrimo");
	}

	/**
	 * Adds an object to the end of the last existing stack
	 * */
	public static Step add(Object object ) {
		Step step = new Step();
		step.object = object;
		getStepStack().add(step);
		//log("aggiungi");
		return step;
	}

	/**
	 * Put the first item if there are no stacks or replace the first item in the last existing stack.
	 * In other words, it puts the first object without adding any more stacks
	 * */
	public static void replaceFirst(Object object ) {
		String tag = object instanceof Family ? "FAM" : "INDI";
		if( memory.list.size() == 0 ) {
			setFirst( object, tag );
		} else {
			getStepStack().clear();
			Step step = add( object );
			step.tag = tag;
		}
		//log("replacePrimo");
	}

	/**
	 * The object contained in the first step of the stack
	 * */
	public static Object firstObject() {
		if( getStepStack().size() > 0 )
			return getStepStack().firstElement().object;
		else
			return null;
	}

	/**
	 * If the stack has more than one object, get the second to last object, otherwise return null
	 * The object in the previous step to the last - L'object nel passo precedente all'ultimo
	 * I think it was called containerObject()?
	 * */
	public static Object getSecondToLastObject() {
		StepStack stepStack = getStepStack();
		if( stepStack.size() > 1 )
			return stepStack.get( stepStack.size() - 2 ).object;
		else
			return null;
	}

	/**
	 * The object in the last step
	 * */
	public static Object getObject() {
		if( getStepStack().size() == 0 )
			return null;
		else
			return getStepStack().peek().object;
	}

	static void clearStackAndRemove() { //lit. retreat
		while( getStepStack().size() > 0 && getStepStack().lastElement().clearStackOnBackPressed)
			getStepStack().pop();
		if( getStepStack().size() > 0 )
			getStepStack().pop();
		if( getStepStack().isEmpty() )
			memory.list.remove( getStepStack() );
		//log("arretra");
	}

	/**
	 * When an object is deleted, make it null in all steps,
	 * and the objects in any subsequent steps are also canceled.
	 * */
	public static void setInstanceAndAllSubsequentToNull(Object subject ) {
		for( StepStack stepStack : memory.list) {
			boolean shouldSetSubsequentToNull = false;
			/*TODO consider using index instead, to avoid needless reassignment
			    and boolean expression evaluation ("|| shouldSetSubsequentToNull")
			*
			* int index = -1;
			* for (int i = 0; i < stepStack.size(); i++) {
            *     if (step.object != null && step.object.equals(subject)) {
            *         index = i;
            * 		  break;
            *     }
            * }
			* if(index >= 0) {
			*     for(Step step : stepStack.subList(index, stepStack.size) {
			*         step.object = null
			*     }
			* }
			*
			* in Kotlin this would be:
			* val index = stepStack.indexOf { it.object != null && it.object == subject }
			* if(index >= 0) for(step in stepStack.subList(index, stepStack.size) {
			*     step.object = null
			* }
			* */
			for( Step step : stepStack) {
				if( step.object != null && (step.object.equals(subject) || shouldSetSubsequentToNull) ) {
					step.object = null;
					shouldSetSubsequentToNull = true;
				}
			}
		}
	}

	public static void log( String intro ) {
		if( intro != null )
			s.l( intro );
		for( StepStack stepStack : memory.list) {
			for( Step step : stepStack) {
				String triplet = step.clearStackOnBackPressed ? "< " : "";
				if( step.tag != null )
					s.p( triplet + step.tag + " " );
				else if( step.object != null )
					s.p( triplet + step.object.getClass().getSimpleName() + " " );
				else
					s.p( triplet + "Null" ); // it happens in very rare cases
			}
			s.l( "" );
		}
		s.l("- - - -");
	}

	static class StepStack extends Stack<Step> {}

	public static class Step {
		public Object object;
		public String tag;
		public boolean clearStackOnBackPressed; // FindStack sets it to true then onBackPressed the stack must be deleted in bulk
	}
}