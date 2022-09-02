/* Visitatore un po' complementare a RiferimentiMedia, avente una funzione:
- modifica il ref che punta alla nota
- potrebbe produrre una lista dei contenitori che includono un certo Media condiviso
*/

package app.familygem.visitor;

import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;
import org.folg.gedcom.model.MediaRef;

public class ContenitoriMedia extends VisitorTotale {

	//public Set<MediaContainer> contenitori = new LinkedHashSet<>();
	private Media media;
	private String nuovoId;

	public ContenitoriMedia( Gedcom gc, Media media, String nuovoId ) {
		this.media = media;
		this.nuovoId = nuovoId;
		gc.accept( this );
	}

	@Override
	boolean visita( Object oggetto, boolean capostipite ) {
		if( oggetto instanceof MediaContainer ) {
			for( MediaRef mr : ((MediaContainer)oggetto).getMediaRefs() )
				if( mr.getRef().equals( media.getId() ) ) {
					//if( nuovoId != null )
					mr.setRef( nuovoId );
					//else
						//contenitori.add( (MediaContainer) oggetto );
				}
		}
		return true;
	}
}
