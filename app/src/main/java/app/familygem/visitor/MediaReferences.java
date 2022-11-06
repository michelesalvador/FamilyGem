package app.familygem.visitor;

import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;
import org.folg.gedcom.model.MediaRef;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 *
 * Visitor who, compared to a shared media, has a triple function:
 *   * - Count media references in all MediaContainers
 *   * - Or delete the same media references
 *   * - In the meantime, list the parent objects of the stacks that contain the media
 *
 *
 * Visitatore che rispetto a un media condiviso ha una triplice funzione:
 * - Contare i riferimenti al media in tutti i MediaContainer
 * - Oppure elimina gli stessi riferimenti al media
 * - Nel frattempo elenca gli oggetti capostipite delle pile che contengono il media
 * */
public class MediaReferences extends TotalVisitor {

	private Media media; // the shared media
	private boolean shouldEliminateRef;
	private Object progenitor; // the progenitor of the stack
	public int num = 0; // the number of references to a Media
	public Set<Object> founders = new LinkedHashSet<>(); // the list of the founding objects containing a Media//l'elenco degli oggetti capostipiti contenti un Media

	public MediaReferences(Gedcom gc, Media media, boolean eliminate ) {
		this.media = media;
		this.shouldEliminateRef = eliminate;
		gc.accept( this );
	}

	@Override
	boolean visit(Object object, boolean isProgenitor ) {
		if( isProgenitor )
			progenitor = object;
		if( object instanceof MediaContainer ) {
			MediaContainer container = (MediaContainer)object;
			Iterator<MediaRef> mediaRefs = container.getMediaRefs().iterator();
			while( mediaRefs.hasNext() )
				if( mediaRefs.next().getRef().equals(media.getId()) ) {
					founders.add(progenitor);
					if(shouldEliminateRef)
						mediaRefs.remove();
					else
						num++;
				}
			if( container.getMediaRefs().isEmpty() )
				container.setMediaRefs( null );
		}
		return true;
	}
}