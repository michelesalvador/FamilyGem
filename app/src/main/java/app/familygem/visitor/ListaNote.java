// Visitatore che produce una Mappa ordinata delle note INLINE

package app.familygem.visitor;

import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.Source;
import java.util.ArrayList;
import java.util.List;
import app.familygem.Global;

public class ListaNote extends VisitorTotale {

	public List<Note> listaNote = new ArrayList<>();

	@Override
	boolean visita( Object object, boolean capo ) {
		if( object instanceof NoteContainer
				&& !(!Global.settings.expert && (object instanceof Source || object instanceof Repository)) ) {
			NoteContainer blocco = (NoteContainer)object;
			listaNote.addAll(blocco.getNotes());
		}
		return true;
	}
}
