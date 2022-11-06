package app.familygem.visitor;

import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.NoteRef;
import java.util.HashSet;
import java.util.Set;

/**
 * Visitor somewhat complementary to ReferencesNote, having a double function:
 * - Edit refs pointing to the shared note
 * - Collect a list of containers that include the shared Note
 *
 * Visitatore un po' complementare a RiferimentiNota, avente una doppia funzione:
 * - Modifica i ref che puntano alla nota condivisa
 * - Colleziona una lista dei contenitori che includono la Nota condivisa
 */
public class NoteContainers extends TotalVisitor {

	public Set<NoteContainer> containers = new HashSet<>();
	private Note note; // the shared note to search for
	private String newId; // the new id to put in the ref

	public NoteContainers(Gedcom gc, Note note, String newId) {
		this.note = note;
		this.newId = newId;
		gc.accept(this);
	}

	@Override
	boolean visit(Object object, boolean capostipite) {
		if( object instanceof NoteContainer ) {
			for( NoteRef noteRef : ((NoteContainer)object).getNoteRefs() ) {
				if( noteRef.getRef().equals(note.getId()) ) {
					noteRef.setRef(newId);
					containers.add((NoteContainer)object);
				}
			}
		}
		return true;
	}
}
