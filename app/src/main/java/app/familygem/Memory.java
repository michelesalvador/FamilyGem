// Gestisce le pile di oggetti gerarchici per scrivere la bava in Dettaglio

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

public class Memory {

	static Map<Class,Class> classes = new HashMap<>();
	private static final Memory memory = new Memory();
	List<StepStack> lista = new ArrayList<>();

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

	// Restituisce l'ultima pila creata se ce n'è almeno una
	// oppure ne restituisce una vuota giusto per non restituire null
	static StepStack getStepStack() {
		if( memory.lista.size() > 0 )
			return memory.lista.get( memory.lista.size() - 1 );
		else
			return new StepStack(); // una pila vuota che non viene aggiunta alla lista
	}

	public static StepStack addPila() {
		StepStack stepStack = new StepStack();
		memory.lista.add(stepStack);
		return stepStack;
	}

	/**
	 * Adds the first object to a new stack
	 * */
	public static void setFirst(Object object ) {
		setFirst( object, null );
	}

	public static void setFirst(Object oggetto, String tag ) {
		addPila();
		Step step = add( oggetto );
		if( tag != null )
			step.tag = tag;
		else if( oggetto instanceof Person )
			step.tag = "INDI";
		//stampa("setPrimo");
	}

	// Aggiunge un oggetto alla fine dell'ultima pila esistente
	public static Step add(Object oggetto ) {
		Step step = new Step();
		step.object = oggetto;
		getStepStack().add(step);
		//stampa("aggiungi");
		return step;
	}

	/**
	 * Put the first item if there are no stacks or replace the first item in the last existing stack.
	 * In other words, it puts the first object without adding any more stacks
	 * */
	public static void replaceFirst(Object object ) {
		String tag = object instanceof Family ? "FAM" : "INDI";
		if( memory.lista.size() == 0 ) {
			setFirst( object, tag );
		} else {
			getStepStack().clear();
			Step step = add( object );
			step.tag = tag;
		}
		//stampa("replacePrimo");
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
	 * The object in the previous step to the last - L'oggetto nel passo precedente all'ultimo
	 * I think it was called containerObject()?
	 * */
	public static Object getSecondToLastObject() {
		StepStack stepStack = getStepStack();
		if( stepStack.size() > 1 )
			return stepStack.get( stepStack.size() - 2 ).object;
		else
			return null;
	}

	// L'oggetto nell'ultimo passo
	public static Object getObject() {
		if( getStepStack().size() == 0 )
			return null;
		else
			return getStepStack().peek().object;
	}

	static void clearStackAndRemove() { //lit. retreat
		while( getStepStack().size() > 0 && getStepStack().lastElement().filotto )
			getStepStack().pop();
		if( getStepStack().size() > 0 )
			getStepStack().pop();
		if( getStepStack().isEmpty() )
			memory.lista.remove( getStepStack() );
		//stampa("arretra");
	}

	/**
	 * When an object is deleted, make it null in all steps,
	 * and the objects in any subsequent steps are also canceled.
	 * */
	public static void setInstanceAndAllSubsequentToNull(Object oggio ) {
		for( StepStack stepStack : memory.lista ) {
			boolean seguente = false;
			for( Step step : stepStack) {
				if( step.object != null && (step.object.equals(oggio) || seguente) ) {
					step.object = null;
					seguente = true;
				}
			}
		}
	}

	public static void stampa( String intro ) {
		if( intro != null )
			s.l( intro );
		for( StepStack stepStack : memory.lista ) {
			for( Step step : stepStack) {
				String filotto = step.filotto ? "< " : "";
				if( step.tag != null )
					s.p( filotto + step.tag + " " );
				else if( step.object != null )
					s.p( filotto + step.object.getClass().getSimpleName() + " " );
				else
					s.p( filotto + "Null" ); // capita in rarissimi casi
			}
			s.l( "" );
		}
		s.l("- - - -");
	}

	static class StepStack extends Stack<Step> {}

	public static class Step {
		public Object object;
		public String tag;
		public boolean filotto; // TrovaPila lo setta true quindi onBackPressed la pila va eliminata in blocco
	}
}