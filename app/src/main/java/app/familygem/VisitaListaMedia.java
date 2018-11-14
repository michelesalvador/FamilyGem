// Mappa ordinata dei media ciascuno col suo oggetto contenitore
// Il contenitore serve per i comandi Scollega e Elimina nel menu contestuale di ciascun media

package app.familygem;

import java.util.LinkedHashMap;
import java.util.Map;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.Visitor;

public class VisitaListaMedia extends Visitor {

	Map<Media,Object> listaMedia = new LinkedHashMap<>();
	Gedcom gc;
	boolean tutti;	// Elencare tutti i media (anche i locali) o solo gli oggetti media collegabili

	VisitaListaMedia( Gedcom gc, boolean voglioTutti ) {
		this.gc = gc;
		tutti = voglioTutti;
	}

	@Override
	public boolean visit( Gedcom gc ) {
		for( Media m : gc.getMedia() ) listaMedia.put( m, gc );	// rastrella gli oggetti media
		return true;
	}
	@Override
	public boolean visit( Person p ){
		//if(tutti) for( MediaRef r : p.getMediaRefs() ) listaMedia.put( r.getMedia(gc), p );	// elenca i ref a vuoto => media null
		if(tutti) for( Media m : p.getAllMedia(gc) ) listaMedia.put( m, p );	// Oggetti media e media locali di tutti gli individui
													// put non aggiunge duplicati alle chiavi già esistenti
		return true;
	}
	@Override
	public boolean visit( Family f ) {
		if(tutti) for( Media m : f.getAllMedia(gc) ) listaMedia.put( m, f );
		return true;
	}
	@Override
	public boolean visit( EventFact e ) {
		if(tutti) for( Media m : e.getAllMedia(gc) ) listaMedia.put( m, e );
		return true;
	}
	@Override
	public boolean visit( Name n ) {
		if(tutti) for( Media m : n.getAllMedia(gc) ) listaMedia.put( m, n );
		return true;
	}
	@Override
	public boolean visit( SourceCitation c ) {
		if(tutti) for( Media m : c.getAllMedia(gc) ) listaMedia.put( m, c );
		return true;
	}
	@Override
	public boolean visit( Source s ) {
		if(tutti) for( Media m : s.getAllMedia(gc) ) listaMedia.put( m, s );
		return true;
	}
}