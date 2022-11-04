/*
Visitatore che rispetto a un media condiviso ha una triplice funzione:
- Contare i riferimenti al media in tutti i MediaContainer
- Oppure elimina gli stessi riferimenti al media
- Nel frattempo elenca gli oggetti capostipite delle pile che contengono il media
*/

package app.familygem.visitor;

import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;
import org.folg.gedcom.model.MediaRef;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public class MediaReferences extends TotalVisitor {

	private Media media; // il media condiviso
	private boolean elimina; // eliminare i Ref o no
	private Object capo; // il capostipite della pila
	public int num = 0; // il conto dei riferimenti a un Media
	public Set<Object> founders = new LinkedHashSet<>(); // the list of the founding objects containing a Media//l'elenco degli oggetti capostipiti contenti un Media

	public MediaReferences(Gedcom gc, Media media, boolean elimina ) {
		this.media = media;
		this.elimina = elimina;
		gc.accept( this );
	}

	@Override
	boolean visita( Object oggetto, boolean capostipite ) {
		if( capostipite )
			capo = oggetto;
		if( oggetto instanceof MediaContainer ) {
			MediaContainer contenitore = (MediaContainer)oggetto;
			Iterator<MediaRef> refiMedia = contenitore.getMediaRefs().iterator();
			while( refiMedia.hasNext() )
				if( refiMedia.next().getRef().equals(media.getId()) ) {
					founders.add( capo );
					if( elimina )
						refiMedia.remove();
					else
						num++;
				}
			if( contenitore.getMediaRefs().isEmpty() )
				contenitore.setMediaRefs( null );
		}
		return true;
	}
}