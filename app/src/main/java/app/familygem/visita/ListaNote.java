// Visitatore che produce una Mappa ordinata delle note INLINE ciascuno col suo oggetto contenitore

package app.familygem.visita;

import org.folg.gedcom.model.Change;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Header;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.Visitor;
import java.util.LinkedHashMap;
import java.util.Map;

public class ListaNote extends Visitor {

	public Map<Note,Object> listaNote = new LinkedHashMap<>();

	void opera( NoteContainer blocco ) {
		for( Note nota : blocco.getNotes() )
			listaNote.put( nota, blocco );
	}

	@Override
	public boolean visit( Header blocco ) {
		opera(blocco);
		return true;
	}
	@Override
	public boolean visit( Person blocco ) {
		opera(blocco);
		return true;
	}
	@Override
	public boolean visit( Family blocco ) {
		opera(blocco);
		return true;
	}
	@Override
	public boolean visit( Name blocco ) {
		opera(blocco);
		return true;
	}
	@Override
	public boolean visit( EventFact blocco ) {
		opera(blocco);
		return true;
	}
	@Override
	public boolean visit( Media blocco ) {
		opera(blocco);
		return true;
	}
	@Override
	public boolean visit( SourceCitation blocco ) {
		opera(blocco);
		return true;
	}
	@Override
	public boolean visit( Source blocco ) {
		opera(blocco);
		return true;
	}
	@Override
	public boolean visit( Repository blocco ) {
		opera(blocco);
		return true;
	}
	@Override
	public boolean visit( Change blocco ) {
		opera(blocco);
		return true;
	}
}