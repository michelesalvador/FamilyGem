package app.familygem.visitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

/**
 * Ordered map of the media each with its own container object
 * The container is used practically only to disconnectMedia in IndividualMedia
 *
 * Mappa ordinata dei media ciascuno col suo object contenitore
 * Il contenitore serve praticamente solo a scollegaMedia in IndividuoMedia
 * */
public class MediaListContainer extends Visitor {

	public List<MedCont> mediaList = new ArrayList<>();
	private Gedcom gc;
	private boolean requestAll;	// List all media (even local) or shared media objects only //Elencare tutti i media (anche i locali) o solo gli oggetti media condivisi

	public MediaListContainer(Gedcom gc, boolean voglioTutti ) {
		this.gc = gc;
		this.requestAll = voglioTutti;
	}

	private boolean visitInternal(Object object ) {
		if( requestAll && object instanceof MediaContainer ) {
			//for( MediaRef r : p.getMediaRefs() ) listaMedia.put( r.getMedia(gc), p );	//list empty refs => null media // elenca i ref a vuoto => media null
			MediaContainer container = (MediaContainer) object;
			for( Media med : container.getAllMedia( gc ) ) { // Media objects and local media of each record //Oggetti media e media locali di ciascun record
				MedCont medCont = new MedCont(med, object);
				if( !mediaList.contains(medCont) )
					mediaList.add( medCont );
			}
		}
		return true;
	}

	@Override
	public boolean visit( Gedcom gc ) {
		for( Media med : gc.getMedia() )
			mediaList.add( new MedCont(med, gc) );	// (rake?) the media items //rastrella gli oggetti media
		return true;
	}
	@Override
	public boolean visit( Person p ) {
		return visitInternal( p );
	}
	@Override
	public boolean visit( Family f ) {
		return visitInternal( f );
	}
	@Override
	public boolean visit( EventFact e ) {
		return visitInternal( e );
	}
	@Override
	public boolean visit( Name n ) {
		return visitInternal( n );
	}
	@Override
	public boolean visit( SourceCitation c ) {
		return visitInternal( c );
	}
	@Override
	public boolean visit( Source s ) {
		return visitInternal( s );
	}

	/**
	 * Class representing a Media with its container object
	 * */
	static public class MedCont {
		public Media media;
		public Object container;
		public MedCont( Media media, Object contenitore ) {
			this.media = media;
			this.container = contenitore;
		}
		@Override
		public boolean equals( Object o ) {
			return o instanceof MedCont && media.equals( ((MedCont)o).media);
		}
		@Override
		public int hashCode() {
			return Objects.hash( media );
		}
	}
}