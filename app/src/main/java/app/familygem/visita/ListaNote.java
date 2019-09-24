// Visitatore che produce una Mappa ordinata delle note INLINE

package app.familygem.visita;

import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import java.util.ArrayList;
import java.util.List;

public class ListaNote extends VisitorTotale {

	public List<Note> listaNote = new ArrayList<>();

	@Override
	boolean visita( Object oggetto, boolean capo ) {
		if( oggetto instanceof NoteContainer ) {
			NoteContainer blocco = (NoteContainer) oggetto;
			for( Note nota : blocco.getNotes() )
				listaNote.add( nota );
		}
		return true;
	}
}