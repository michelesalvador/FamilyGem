package app.familygem.visitor;

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
import java.util.LinkedHashSet;
import java.util.Set;
import app.familygem.F;
import app.familygem.Global;

/**
 * Ordered set of media
 * Can almost always replace ContainerMediaList
 * */
public class MediaList extends Visitor {

	public Set<Media> list = new LinkedHashSet<>();
	private Gedcom gc;
	/**
	 *
	 * 0 all media
	 * 1 only shared media objects (for all Gedcom)
	 * 2 only (local media?) (no gc needed)
	 * 3 shared and local but only previewable images and videos (for the main menu)
	 *
	 * 0 tutti i media
	 * 1 solo gli oggetti media condivisi (per tutto il Gedcom)
	 * 2 solo i locali (non serve gc)
	 * 3 condivisi e locali ma solo immagini e video anteprimabili	(per il menu principale)
	 */
	private int mediaType;

	public MediaList(Gedcom gc, int mediaType) {
		this.gc = gc;
		this.mediaType = mediaType;
	}

	private boolean visita(Object object) {
		if( object instanceof MediaContainer ) {
			MediaContainer container = (MediaContainer)object;
			if( mediaType == 0 )
				list.addAll(container.getAllMedia(gc)); // adds shared and local media
			else if( mediaType == 2 )
				list.addAll(container.getMedia()); // local media only
			else if( mediaType == 3 )
				for( Media med : container.getAllMedia(gc) )
					filter(med);
		}
		return true;
	}

	/**
	 * Adds only the alleged (pretty? - "bellini") ones with preview
	 * Aggiunge solo quelli presunti bellini con anteprima
	 * */
	private void filter(Media media) {
		String file = F.mediaPath(Global.settings.openTree, media); // TODO and images from URIs?
		if(file != null) {
			int index = file.lastIndexOf('.');
			if (index > 0) {
				String extension = file.substring(index + 1);
				switch (extension) {
					case "jpg":
					case "jpeg":
					case "png":
					case "gif":
					case "bmp":
					case "webp": // ok
					case "heic": // ok TODO the image may be rotated 90 ° or 180 °
					case "heif": // synonymous with .heic
					case "mp4":
					case "3gp": // ok
					case "webm": // ok
					case "mkv": // ok
						list.add(media);
				}
			}
		}
	}

	@Override
	public boolean visit(Gedcom gc) {
		if( mediaType < 2 )
			list.addAll(gc.getMedia()); // (rakes?) all Gedcom shared media items //rastrella tutti gli oggetti media condivisi del Gedcom
		else if( mediaType == 3 )
			for( Media med : gc.getMedia() )
				filter(med);
		return true;
	}
	@Override
	public boolean visit(Person p) {
		return visita(p);
	}
	@Override
	public boolean visit(Family f) {
		return visita(f);
	}
	@Override
	public boolean visit(EventFact e) {
		return visita(e);
	}
	@Override
	public boolean visit(Name n) {
		return visita(n);
	}
	@Override
	public boolean visit(SourceCitation c) {
		return visita(c);
	}
	@Override
	public boolean visit(Source s) {
		return visita(s);
	}
}
