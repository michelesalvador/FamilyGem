// Partendo dall'id di una fonte genera una lista di triplette: capostipite / contenitore / citazioni della fonte
// Usato da Biblioteca, da Fonte e da Conferma

package app.familygem.visitor;

import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ListOfSourceCitations extends TotalVisitor {

	public List<Tripletta> lista = new ArrayList<>();
	private String id; // id della fonte
	private Object capo;

	public ListOfSourceCitations(Gedcom gc, String id ) {
		this.id = id;
		gc.accept( this );
	}

	@Override
	boolean visita( Object oggetto, boolean capostipite ) {
		if( capostipite )
			capo = oggetto;
		if( oggetto instanceof SourceCitationContainer ) {
			analizza( oggetto, ((SourceCitationContainer)oggetto).getSourceCitations() );
		} // Note non estende SourceCitationContainer, ma implementa i suoi propri metodi
		else if( oggetto instanceof Note ) {
			analizza( oggetto, ((Note)oggetto).getSourceCitations() );
		}
		return true;
	}

	private void analizza( Object contenitore, List<SourceCitation> citazioni ) {
		for( SourceCitation citaz : citazioni )
			// Le fonti-note non hanno Ref ad una fonte
			if( citaz.getRef() != null && citaz.getRef().equals(id) ) {
				Tripletta tris = new Tripletta();
				tris.capostipite = capo;
				tris.contenitore = contenitore;
				tris.citazione = citaz;
				lista.add( tris );
			}
	}

	public Object[] getCapi() {
		Set<Object> capi = new LinkedHashSet<>(); // unifica i duplicati
		for( Tripletta tri : lista ) {
			capi.add(tri.capostipite);
		}
		return capi.toArray();
	}

	// Classe per stoccare insieme i tre elementi capostipite - contenitore - citazione
	public static class Tripletta {
		public Object capostipite;
		public Object contenitore; // Sarebbe un SourceCitationContainer ma Note fa eccezione
		public SourceCitation citazione;
	}
}
