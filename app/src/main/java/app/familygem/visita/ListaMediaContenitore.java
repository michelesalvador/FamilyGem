// Mappa ordinata dei media ciascuno col suo oggetto contenitore
// Il contenitore serve praticamente solo a scollegaMedia in IndividuoMedia

package app.familygem.visita;

import java.util.LinkedHashMap;
import java.util.Map;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.Visitor;

public class ListaMediaContenitore extends Visitor {

	public Map<Media,Object> listaMedia = new LinkedHashMap<>();
	private Gedcom gc;
	private boolean voglioTutti;	// Elencare tutti i media (anche i locali) o solo gli oggetti media condivisi

	public ListaMediaContenitore( Gedcom gc, boolean voglioTutti ) {
		this.gc = gc;
		this.voglioTutti = voglioTutti;
	}

	private boolean visita( Object oggetto ) {
		if( voglioTutti && oggetto instanceof MediaContainer ) {
			//for( MediaRef r : p.getMediaRefs() ) listaMedia.put( r.getMedia(gc), p );	// elenca i ref a vuoto => media null
			MediaContainer contenitore = (MediaContainer) oggetto;
			for( Media med : contenitore.getAllMedia( gc ) )
				listaMedia.put( med, oggetto );	// Oggetti media e media locali di tutti gli individui
						// put non aggiunge duplicati alle chiavi già esistenti
		}
		return true;
	}

	@Override
	public boolean visit( Gedcom gc ) {
		for( Media med : gc.getMedia() )
			listaMedia.put( med, gc );	// rastrella gli oggetti media
		return true;
	}
	@Override
	public boolean visit( Person p ) {
		return visita( p );
	}
	@Override
	public boolean visit( Family f ) {
		return visita( f );
	}
	@Override
	public boolean visit( EventFact e ) {
		return visita( e );
	}
	@Override
	public boolean visit( Name n ) {
		return visita( n );
	}
	@Override
	public boolean visit( SourceCitation c ) {
		return visita( c );
	}
	@Override
	public boolean visit( Source s ) {
		return visita( s );
	}
}