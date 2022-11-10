package app.familygem.visitor;

import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * // Starting from the id of a source it generates a list of triplets: parent / container / citations of the source
 * // Used by [LibraryFragment], [SourceActivity] and [ConfirmationActivity]
 *
 * // Partendo dall'id di una fonte genera una lista di triplette: capostipite / contenitore / citazioni della fonte
 * // Usato da Biblioteca, da Fonte e da Conferma
 * */
public class ListOfSourceCitations extends TotalVisitor {

	public List<Triplet> list = new ArrayList<>();
	private String id; // id of the source
	private Object capo;

	public ListOfSourceCitations(Gedcom gc, String id ) {
		this.id = id;
		gc.accept( this );
	}

	@Override
	boolean visit(Object object, boolean isProgenitor) {
		if(isProgenitor)
			capo = object;
		if( object instanceof SourceCitationContainer ) {
			analyze( object, ((SourceCitationContainer)object).getSourceCitations() );
		} // Note does not extend SourceCitationContainer, but implements its own methods
		else if( object instanceof Note ) {
			analyze( object, ((Note)object).getSourceCitations() );
		}
		return true;
	}

	private void analyze(Object container, List<SourceCitation> citations ) {
		for( SourceCitation citation : citations )
			// (Known sources?)[SourceCitations?] have no Ref to a source //Le fonti-note non hanno Ref ad una fonte
			if( citation.getRef() != null && citation.getRef().equals(id) ) {
				Triplet triplet = new Triplet();
				triplet.progenitor = capo;
				triplet.container = container;
				triplet.citation = citation;
				list.add( triplet );
			}
	}

	public Object[] getProgenitors() {
		Set<Object> heads = new LinkedHashSet<>(); // merge duplicates
		for( Triplet tri : list) {
			heads.add(tri.progenitor);
		}
		return heads.toArray();
	}

	/**
	 * Class for storing together the three parent elements - container - quote
	 * Classe per stoccare insieme i tre elementi capostipite - contenitore - citazione
	 * */
	public static class Triplet {
		public Object progenitor;
		public Object container; // It would be a SourceCitationContainer but Note is an exception //Sarebbe un SourceCitationContainer ma Note fa eccezione
		public SourceCitation citation;
	}
}
