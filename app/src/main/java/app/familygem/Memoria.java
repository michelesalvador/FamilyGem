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
import app.familygem.dettaglio.Archivio;
import app.familygem.dettaglio.ArchivioRef;
import app.familygem.dettaglio.Autore;
import app.familygem.dettaglio.Cambiamenti;
import app.familygem.dettaglio.CitazioneFonte;
import app.familygem.dettaglio.Estensione;
import app.familygem.dettaglio.Evento;
import app.familygem.dettaglio.Famiglia;
import app.familygem.dettaglio.Fonte;
import app.familygem.dettaglio.Immagine;
import app.familygem.dettaglio.Indirizzo;
import app.familygem.dettaglio.Nome;
import app.familygem.dettaglio.Nota;

public class Memoria {

	static Map<Class,Class> classi = new HashMap<>();
	private static final Memoria memoria = new Memoria();
	List<Pila> lista = new ArrayList<>();

	Memoria() {
		classi.put( Person.class, Individuo.class );
		classi.put( Repository.class, Archivio.class );
		classi.put( RepositoryRef.class, ArchivioRef.class );
		classi.put( Submitter.class, Autore.class );
		classi.put( Change.class, Cambiamenti.class );
		classi.put( SourceCitation.class, CitazioneFonte.class );
		classi.put( GedcomTag.class, Estensione.class );
		classi.put( EventFact.class, Evento.class );
		classi.put( Family.class, Famiglia.class );
		classi.put( Source.class, Fonte.class );
		classi.put( Media.class, Immagine.class );
		classi.put( Address.class, Indirizzo.class );
		classi.put( Name.class, Nome.class );
		classi.put( Note.class, Nota.class );
	}

	// Restituisce l'ultima pila creata se ce n'è almeno una, oppure ne crea una vuota
	static Pila getPila() {
		if( memoria.lista.size() > 0 )
			return memoria.lista.get( memoria.lista.size() - 1 );
		else
			return new Pila(); // una pila vuota giusto per non restituire null
	}

	public static Pila addPila() {
		Pila pila =  new Pila();
		memoria.lista.add( pila );
		return pila;
	}

	// Aggiunge il primo oggetto in una nuova pila
	public static void setPrimo( Object oggetto ) {
		setPrimo( oggetto, null );
	}

	public static void setPrimo( Object oggetto, String tag ) {
		addPila();
		Passo passo = aggiungi( oggetto );
		if( tag != null )
			passo.tag = tag;
		else if( oggetto instanceof Person )
			passo.tag = "INDI";
	}

	// Aggiunge un oggetto alla fine dell'ultima pila esistente
	public static Passo aggiungi( Object oggetto ) {
		Passo passo = new Passo();
		passo.oggetto = oggetto;
		getPila().add( passo );
		//stampa();
		return passo;
	}

	// Mette il primo oggetto se non ci sono pile oppure sostituisce il primo oggetto nell'ultima pila esistente
	// In altre parole mette il primo oggetto senza aggiungere ulteriori pile
	public static void replacePrimo( Object oggetto ) {
		if( memoria.lista.size() == 0 ) {
			setPrimo( oggetto );
		} else {
			getPila().clear();
			aggiungi( oggetto );
		}
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
			memoria.lista.remove( getPila() );
		//stampa();
	}

	// Quando un oggetto viene eliminato, lo rende null in tutti i passi,
	// e anche gli oggetti negli eventuali passi seguenti vengono annullati.
	public static void annullaIstanze( Object oggio ) {
		for( Pila pila : memoria.lista ) {
			boolean seguente = false;
			for( Passo passo : pila ) {
				if( passo.oggetto != null && (passo.oggetto.equals(oggio) || seguente) ) {
					passo.oggetto = null;
					seguente = true;
				}
			}
		}
	}

	public static void stampa() {
		for( Pila pila : memoria.lista ) {
			for( Passo passo : pila ) {
				String filotto = passo.filotto ? "< " : "";
				if( passo.tag != null )
					s.p( filotto + passo.tag + " " );
				else if( passo.oggetto != null )
					s.p( filotto + passo.oggetto.getClass().getSimpleName() + " " );
				else
					s.p( filotto + "Null" ); // capita in rarissimi casi
			}
			s.l( "" );
		}
		s.l("- - - -");
	}

	static class Pila extends Stack<Passo> {}

	public static class Passo {
		public Object oggetto;
		public String tag;
		public boolean filotto; // TrovaPila lo setta true quindi onBackPressed la pila va eliminata in blocco
	}
}