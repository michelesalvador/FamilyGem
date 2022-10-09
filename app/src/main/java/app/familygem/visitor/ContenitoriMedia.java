/* Visitatore un po' complementare a RiferimentiMedia, avente una doppia funzione:
- Modifica i ref che puntano al Media condiviso
- Colleziona una lista dei contenitori che includono il Media condiviso
*/

package app.familygem.visitor;

import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;
import org.folg.gedcom.model.MediaRef;
import java.util.HashSet;
import java.util.Set;

public class ContenitoriMedia extends VisitorTotale {

	public Set<MediaContainer> containers = new HashSet<>();
	private final Media media;
	private final String newId;

	public ContenitoriMedia(Gedcom gedcom, Media media, String newId) {
		this.media = media;
		this.newId = newId;
		gedcom.accept(this);
	}

	@Override
	boolean visita(Object object, boolean capostipite) {
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
