/* Visitatore per nota condivisa con una doppia funzione:
 - Contare in tutti gli elementi del Gedcom i riferimenti alla nota
 - Eliminare i riferimenti alla nota
*/

package app.familygem.visita;

import org.folg.gedcom.model.Change;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Header;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.NoteRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.Visitor;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class RiferimentiNota extends Visitor {

	boolean elimina; // bandierina per eliminare i ref alla nota piuttosto che contarli
	String id;  // Id della nota condivisa
	public int num = 0;

	public RiferimentiNota( String id, boolean elimina ) {
		this.id = id;
		this.elimina = elimina;
	}

	void opera( NoteContainer blocco ) {
		Iterator<NoteRef> refi = blocco.getNoteRefs().iterator();
		while( refi.hasNext() ) {
			NoteRef nr = refi.next();
			if( nr.getRef().equals(id) ) {
				if(elimina) refi.remove();
				else num++;
			}
		}
		if( blocco.getNoteRefs().isEmpty() ) blocco.setNoteRefs( null );
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