package app.familygem.visitor;

import org.folg.gedcom.model.Change;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Header;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.RepositoryRef;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.Submitter;
import org.folg.gedcom.model.Visitor;

/**
 * Visitor model that visits all the possible Gedcom containers distinguishing the progenitors
 * Modello di Visitor che visita tutti i possibili contenitori del Gedcom distinguendo i capostipiti
 * */
public class TotalVisitor extends Visitor {

	private boolean visitInternal(Object object ) {
		return visit( object, false );
	}

	boolean visit(Object object, boolean isProgenitor ) {
		return true;
	}

	@Override
	public boolean visit( Header h ) {
		return visit( h, true );
	}
	@Override
	public boolean visit( Person p ) {
		return visit( p, true );
	}
	@Override
	public boolean visit( Family f ) {
		return visit( f, true );
	}
	@Override
	public boolean visit( Source s ) {
		return visit( s, true );
	}
	@Override
	public boolean visit( Repository r ) {
		return visit( r, true );
	}
	@Override
	public boolean visit( Submitter s ) {
		return visit( s, true );
	}
	@Override
	public boolean visit( Media m ) {
		return visit( m, m.getId()!=null );
	}
	@Override
	public boolean visit( Note n ) {
		return visit( n, n.getId()!=null );
	}
	@Override
	public boolean visit( Name n ) {
		return visitInternal( n );
	}
	@Override
	public boolean visit( EventFact e ) {
		return visitInternal( e );
	}
	@Override
	public boolean visit( SourceCitation s ) {
		return visitInternal( s );
	}
	@Override
	public boolean visit( RepositoryRef r ) {
		return visitInternal( r );
	}
	@Override
	public boolean visit( Change c ) {
		return visitInternal( c );
	}
}
