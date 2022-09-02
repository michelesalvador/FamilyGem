/* Visitatore un po' complementare a RiferimentiNota, avente una doppia funzione:
- produce una lista dei contenitori che includono una certa Nota condivisa
- modifica il ref che punta alla nota
*/

package app.familygem.visitor;

import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.NoteRef;
import java.util.LinkedHashSet;
import java.util.Set;

public class ContenitoriNota extends VisitorTotale {

	public Set<NoteContainer> contenitori = new LinkedHashSet<>();
	private Note nota; // la nota condivisa da cercare
	private String nuovoId; // il nuovo id da mettere nei ref

	public ContenitoriNota( Gedcom gc, Note nota, String nuovoId ) {
		this.nota = nota;
		this.nuovoId = nuovoId;
		gc.accept( this );
	}

	@Override
	boolean visita( Object oggetto, boolean capostipite ) {
		if( oggetto instanceof NoteContainer ) {
			for( NoteRef nr : ((NoteContainer)oggetto).getNoteRefs() )
				if( nr.getRef().equals( nota.getId() ) ) {
					if( nuovoId != null )
						nr.setRef( nuovoId );
					else
						contenitori.add( (NoteContainer) oggetto );
				}
		}
		return true;
	}
}
