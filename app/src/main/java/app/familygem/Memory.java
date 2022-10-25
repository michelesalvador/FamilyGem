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

	static Map<Class,Class> classi = new HashMap<>();
	private static final Memory memory = new Memory();
	List<StepStack> lista = new ArrayList<>();

	Memory() {
		classi.put( Person.class, IndividualPersonActivity.class );
		classi.put( Repository.class, RepositoryActivity.class );
		classi.put( RepositoryRef.class, RepositoryRefActivity.class );
		classi.put( Submitter.class, AuthorActivity.class );
		classi.put( Change.class, ChangesActivity.class );
		classi.put( SourceCitation.class, SourceCitationActivity.class );
		classi.put( GedcomTag.class, ExtensionActivity.class );
		classi.put( EventFact.class, EventActivity.class );
		classi.put( Family.class, FamilyActivity.class );
		classi.put( Source.class, SourceActivity.class );
		classi.put( Media.class, ImageActivity.class );
		classi.put( Address.class, AddressActivity.class );
		classi.put( Name.class, NameActivity.class );
		classi.put( Note.class, NoteActivity.class );
	}

	// Restituisce l'ultima pila creata se ce n'è almeno una
	// oppure ne restituisce una vuota giusto per non restituire null
	static StepStack getPila() {
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

	// Aggiunge il primo oggetto in una nuova pila
	public static void setPrimo( Object oggetto ) {
		setPrimo( oggetto, null );
	}

	public static void setPrimo( Object oggetto, String tag ) {
		addPila();
		Step step = aggiungi( oggetto );
		if( tag != null )
			step.tag = tag;
		else if( oggetto instanceof Person )
			step.tag = "INDI";
		//stampa("setPrimo");
	}

	// Aggiunge un oggetto alla fine dell'ultima pila esistente
	public static Step aggiungi(Object oggetto ) {
		Step step = new Step();
		step.oggetto = oggetto;
		getPila().add(step);
		//stampa("aggiungi");
		return step;
	}

	// Mette il primo oggetto se non ci sono pile oppure sostituisce il primo oggetto nell'ultima pila esistente
	// In altre parole mette il primo oggetto senza aggiungere ulteriori pile
	public static void replacePrimo( Object oggetto ) {
		String tag = oggetto instanceof Family ? "FAM" : "INDI";
		if( memory.lista.size() == 0 ) {
			setPrimo( oggetto, tag );
		} else {
			getPila().clear();
			Step step = aggiungi( oggetto );
			step.tag = tag;
		}
		//stampa("replacePrimo");
	}

	// L'oggetto contenuto nel primo passo della pila
	public static Object oggettoCapo() {
		if( getPila().size() > 0 )
			return getPila().firstElement().oggetto;
		else
			return null;
	}

	// L'oggetto nel passo precedente all'ultimo
	public static Object oggettoContenitore() {
		if( getPila().size() > 1 )
			return getPila().get( getPila().size() - 2 ).oggetto;
		else
			return null;
	}

	// L'oggetto nell'ultimo passo
	public static Object getOggetto() {
		if( getPila().size() == 0 )
			return null;
		else
			return getPila().peek().oggetto;
	}

	static void arretra() {
		while( getPila().size() > 0 && getPila().lastElement().filotto )
			getPila().pop();
		if( getPila().size() > 0 )
			getPila().pop();
		if( getPila().isEmpty() )
			memory.lista.remove( getPila() );
		//stampa("arretra");
	}

	// Quando un oggetto viene eliminato, lo rende null in tutti i passi,
	// e anche gli oggetti negli eventuali passi seguenti vengono annullati.
	public static void annullaIstanze( Object oggio ) {
		for( StepStack stepStack : memory.lista ) {
			boolean seguente = false;
			for( Step step : stepStack) {
				if( step.oggetto != null && (step.oggetto.equals(oggio) || seguente) ) {
					step.oggetto = null;
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
				else if( step.oggetto != null )
					s.p( filotto + step.oggetto.getClass().getSimpleName() + " " );
				else
					s.p( filotto + "Null" ); // capita in rarissimi casi
			}
			s.l( "" );
		}
		s.l("- - - -");
	}

	static class StepStack extends Stack<Step> {}

	public static class Step {
		public Object oggetto;
		public String tag;
		public boolean filotto; // TrovaPila lo setta true quindi onBackPressed la pila va eliminata in blocco
	}
}