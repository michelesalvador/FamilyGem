// Set ordinato dei media
// Quasi sempre può sostituire ListaMediaContenitore

package app.familygem.visita;

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
import app.familygem.Globale;

public class ListaMedia extends Visitor {

	public Set<Media> lista = new LinkedHashSet<>();
	private Gedcom gc;
	/*	0 tutti i media
		1 solo gli oggetti media condivisi (per tutto il Gedcom)
		2 solo i locali (non serve gc)
		3 condivisi e locali ma solo immagini e video anteprimabili	(per il menu principale) */
	private int cosaVuoi;

	public ListaMedia( Gedcom gc, int cosaVuoi ) {
		this.gc = gc;
		this.cosaVuoi = cosaVuoi;
	}

	private boolean visita( Object oggetto ) {
		if( oggetto instanceof MediaContainer ) {
			MediaContainer contenitore = (MediaContainer) oggetto;
			if( cosaVuoi == 0 )
				lista.addAll( contenitore.getAllMedia(gc) ); // aggiunge media condivisi e locali
			else if( cosaVuoi == 2 )
				lista.addAll( contenitore.getMedia() ); // solo i media locali
			else if( cosaVuoi == 3 )
				for( Media med : contenitore.getAllMedia(gc) )
					filtra( med );
		}
		return true;
	}

	// Aggiunge solo quelli presunti bellini con anteprima
	private void filtra( Media media ) {
		String file = F.percorsoMedia( Globale.preferenze.idAprendo, media); // todo e le immagini dagli URI?
		if( file != null && file.lastIndexOf('.') > 0 ) {
			String estensione = file.substring( file.lastIndexOf('.')+1 );
			switch( estensione ) {
				case "jpg":
				case "jpeg":
				case "png":
				case "gif":
				case "bmp":
				case "webp": // ok
				case "heic": // ok todo l'immagine può risultare ruotata di 90° o 180°
				case "heif": // sinonimo di .heic
				case "mp4":
				case "3gp": // ok
				case "webm": // ok
				case "mkv": // ok
					lista.add( media );
			}
		}

	}

	@Override
	public boolean visit( Gedcom gc ) {
		if( cosaVuoi < 2 )
			lista.addAll( gc.getMedia() ); // rastrella tutti gli oggetti media condivisi del Gedcom
		else if( cosaVuoi == 3 )
			for( Media med : gc.getMedia() )
				filtra(med);
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