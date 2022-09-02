/* Visitatore per nota condivisa con una triplice funzione:
 - Contare in tutti gli elementi del Gedcom i riferimenti alla nota condivisa
 - Eliminare i riferimenti alla nota
 - Nel frattempo raccogliere tutti i capostipiti
*/

package app.familygem.visitor;

import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.NoteRef;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public class RiferimentiNota extends VisitorTotale {

	private String id;  // Id della nota condivisa
	private boolean elimina; // bandierina per eliminare i ref alla nota piuttosto che contarli
	private Object capo;
	public int tot = 0; // i riferimenti alla nota condivisa
	public Set<Object> capostipiti = new LinkedHashSet<>();

	public RiferimentiNota( Gedcom gc, String id, boolean elimina ) {
		this.id = id;
		this.elimina = elimina;
		gc.accept( this );
	}

	@Override
	boolean visita( Object oggetto, boolean capostipite ) {
		if( capostipite )
			capo = oggetto;
		if( oggetto instanceof NoteContainer ) {
			NoteContainer blocco = (NoteContainer) oggetto;
			Iterator<NoteRef> refi = blocco.getNoteRefs().iterator();
			while( refi.hasNext() ) {
				NoteRef nr = refi.next();
				if( nr.getRef().equals(id) ) {
					capostipiti.add( capo );
					if(elimina) refi.remove();
					else tot++;
				}
			}
			if( blocco.getNoteRefs().isEmpty() ) blocco.setNoteRefs( null );
		}
		return true;
	}
}