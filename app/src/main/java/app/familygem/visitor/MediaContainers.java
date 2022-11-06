package app.familygem.visitor;

import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;
import org.folg.gedcom.model.MediaRef;
import java.util.HashSet;
import java.util.Set;

/**
 * Visitor is somewhat complementary to ReferencesMedia, having a double function:
 *   - Edit the refs pointing to the shared Media
 *   - Collect a list of containers that include the shared media
 * Visitatore un po' complementare a RiferimentiMedia, avente una doppia funzione:
 *   - Modifica i ref che puntano al Media condiviso
 *   - Colleziona una lista dei contenitori che includono il Media condiviso
 */
public class MediaContainers extends TotalVisitor {

	public Set<MediaContainer> containers = new HashSet<>();
	private final Media media;
	private final String newId;

	public MediaContainers(Gedcom gedcom, Media media, String newId) {
		this.media = media;
		this.newId = newId;
		gedcom.accept(this);
	}

	@Override
	boolean visit(Object object, boolean isProgenitor) {
		if( object instanceof MediaContainer ) {
			for( MediaRef mediaRef : ((MediaContainer)object).getMediaRefs() ) {
				if( mediaRef.getRef().equals(media.getId()) ) {
					mediaRef.setRef(newId);
					containers.add((MediaContainer)object);
				}
			}
		}
		return true;
	}
}
