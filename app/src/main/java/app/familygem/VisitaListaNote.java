// Mappa ordinata delle note INLINE ciascuno col suo oggetto contenitore

package app.familygem;

import org.folg.gedcom.model.Change;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Header;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.Visitor;
import java.util.LinkedHashMap;
import java.util.Map;

public class VisitaListaNote extends Visitor {

	Map<Note,Object> listaNote = new LinkedHashMap<>();
	boolean x = true;
	String id;
	int num = 0;

	VisitaListaNote( String id ) {
		if( id != null ) {
			this.id = id;
			x = false;
		}
	}

	@Override
	public boolean visit( Header h ) {
		if(x) for( Note n : h.getNotes() ) listaNote.put( n, h );
		else for( NoteRef r : h.getNoteRefs() )
			//if( r.getRef() != null )
				if( r.getRef().equals(id) ) num++;
		return true;
	}
	@Override
	public boolean visit( Person p ) {
		if(x) for( Note n : p.getNotes() ) listaNote.put( n, p );
		else for( NoteRef r : p.getNoteRefs() )
			//if( r.getRef() != null )
				if( r.getRef().equals(id) ) num++;
		return true;
	}
	@Override
	public boolean visit( Family f ) {
		if(x) for( Note n : f.getNotes() ) listaNote.put( n, f );
		else for( NoteRef r : f.getNoteRefs() )
			//if( r.getRef() != null )
				if( r.getRef().equals(id) ) num++;
		return true;
	}
	@Override
	public boolean visit( Name m ) {
		if(x) for( Note n : m.getNotes() ) listaNote.put( n, m );
		else for( NoteRef r : m.getNoteRefs() )
			//if( r.getRef() != null )
				if( r.getRef().equals(id) ) num++;
		return true;
	}
	@Override
	public boolean visit( EventFact e ) {
		if(x) for( Note n : e.getNotes() ) listaNote.put( n, e );
		else for( NoteRef r : e.getNoteRefs() )
			//if( r.getRef() != null )
				if( r.getRef().equals(id) ) num++;
		return true;
	}
	@Override
	public boolean visit( Media m ) {
		if(x) for( Note n : m.getNotes() ) listaNote.put( n, m );
		else for( NoteRef r : m.getNoteRefs() )
			//if( r.getRef() != null )
				if( r.getRef().equals(id) ) num++;
		return true;
	}
	@Override
	public boolean visit( SourceCitation c ) {
		if(x) for( Note n : c.getNotes() ) listaNote.put( n, c );
		else for( NoteRef r : c.getNoteRefs() )
			//if( r.getRef() != null )
				if( r.getRef().equals(id) ) num++;
		return true;
	}
	@Override
	public boolean visit( Source s ) {
		if(x) for( Note n : s.getNotes() ) listaNote.put( n, s );
		else for( NoteRef r : s.getNoteRefs() )
			//if( r.getRef() != null )
				if( r.getRef().equals(id) ) num++;
		return true;
	}
	@Override
	public boolean visit( Repository a ) {
		if(x) for( Note n : a.getNotes() ) listaNote.put( n, a );
		else for( NoteRef r : a.getNoteRefs() )
			//if( r.getRef() != null )
				if( r.getRef().equals(id) ) num++;
		return true;
	}
	@Override
	public boolean visit( Change c ) {
		if(x) for( Note n : c.getNotes() ) listaNote.put( n, c );
		else for( NoteRef r : c.getNoteRefs() )
			//if( r.getRef() != null )
				if( r.getRef().equals(id) ) num++;
		return true;
	}
}