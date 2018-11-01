// Contatore delle citazioni di una fonte
// Serve per Biblioteca

package app.familygem;

import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.Visitor;
import java.util.LinkedHashMap;
import java.util.Map;

public class VisitaListaCitazioni extends Visitor {

	//public List<SourceCitation> lista = new ArrayList<>();
	public Map<SourceCitation,Object> lista = new LinkedHashMap<>();
	String id;

	public VisitaListaCitazioni( String id ){
		this.id = id;
	}

	@Override
	public boolean visit( Person p ){
		//lista.addAll( p.getSourceCitations() );
		for( SourceCitation c : p.getSourceCitations() )
			if( c.getRef() != null )
				if( c.getRef().equals(id) )
					lista.put( c, p );
		return true;
	}
	@Override
	public boolean visit( Family f ) {
		for( SourceCitation c : f.getSourceCitations() )
			if( c.getRef() != null )
				if( c.getRef().equals(id) )
					lista.put( c, f );
		return true;
	}
	@Override
	public boolean visit( Name n ) {
		for( SourceCitation c : n.getSourceCitations() )
			if( c.getRef() != null )
				if( c.getRef().equals(id) )
					lista.put( c, n );
		return true;
	}
	@Override
	public boolean visit( EventFact e ) {
		for( SourceCitation c : e.getSourceCitations() )
			if( c.getRef() != null )
				if( c.getRef().equals(id) )
					lista.put( c, e );
		return true;
	}
	@Override
	public boolean visit( Note n ) {
		for( SourceCitation c : n.getSourceCitations() )
			if( c.getRef() != null )
				if( c.getRef().equals(id) )
					lista.put( c, n );
		return true;
	}
}
