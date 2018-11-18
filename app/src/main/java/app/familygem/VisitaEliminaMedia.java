// Elimina un media in tutti i MediaContainer

package app.familygem;

import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;
import org.folg.gedcom.model.MediaRef;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.Visitor;
import java.util.Iterator;

public class VisitaEliminaMedia extends Visitor {

	Media media;

	VisitaEliminaMedia( Media media ) {
		this.media = media;
	}

	@Override
	public boolean visit( Person p ){
		scanna( p );
		return true;
	}
	@Override
	public boolean visit( Family f ) {
		scanna( f );
		return true;
	}
	@Override
	public boolean visit( EventFact e ) {
		scanna( e );
		return true;
	}
	@Override
	public boolean visit( Name n ) {
		scanna( n );
		return true;
	}
	@Override
	public boolean visit( SourceCitation c ) {
		scanna( c );
		return true;
	}
	@Override
	public boolean visit( Source s ) {
		scanna( s );
		return true;
	}

	private void scanna( MediaContainer contenitore ) {
		Iterator<MediaRef> refiMedia = contenitore.getMediaRefs().iterator();
		while( refiMedia.hasNext() )
			if( refiMedia.next().getRef().equals(media.getId()) )
				refiMedia.remove();
		if( contenitore.getMediaRefs().isEmpty() )
			contenitore.setMediaRefs( null );
	}
}