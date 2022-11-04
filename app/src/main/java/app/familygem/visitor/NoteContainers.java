/* Visitatore un po' complementare a RiferimentiNota, avente una doppia funzione:
- Modifica i ref che puntano alla nota condivisa
- Colleziona una lista dei contenitori che includono la Nota condivisa
*/

package app.familygem.visitor;

import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.NoteRef;
import java.util.HashSet;
import java.util.Set;

public class NoteContainers extends TotalVisitor {

	public Set<NoteContainer> containers = new HashSet<>();
	private Note note; // la nota condivisa da cercare
	private String newId; // il nuovo id da mettere nei ref

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
