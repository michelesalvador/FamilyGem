package app.familygem.visitor;

import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.NoteRef;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
/**
 * Visitor per shared note with a triple function:
 *  - To count in all the elements of the Gedcom the references to the shared note
 *  - Delete references to the note
 *  - In the meantime, collect all the founders
 *
 * Visitatore per nota condivisa con una triplice funzione:
 *  - Contare in tutti gli elementi del Gedcom i riferimenti alla nota condivisa
 *  - Eliminare i riferimenti alla nota
 *  - Nel frattempo raccogliere tutti i capostipiti
 * */
public class NoteReferences extends TotalVisitor {

	private String id;  // Id of the shared note
	private boolean deleteRefs; // flag to eliminate the refs to the note rather than counting them //bandierina per eliminare i ref alla nota piuttosto che contarli
	private Object head;
	public int count = 0; // references to the shared note
	public Set<Object> founders = new LinkedHashSet<>();

	public NoteReferences(Gedcom gc, String id, boolean deleteRegs ) {
		this.id = id;
		this.deleteRefs = deleteRegs;
		gc.accept( this );
	}

	@Override
	boolean visit(Object object, boolean isProgenitor) {
		if(isProgenitor)
			head = object;
		if( object instanceof NoteContainer ) {
			NoteContainer container = (NoteContainer) object;
			Iterator<NoteRef> refs = container.getNoteRefs().iterator();
			while( refs.hasNext() ) {
				NoteRef nr = refs.next();
				if( nr.getRef().equals(id) ) {
					founders.add(head);
					if(deleteRefs) refs.remove();
					else count++;
				}
			}
			if( container.getNoteRefs().isEmpty() ) container.setNoteRefs( null );
		}
		return true;
	}
}