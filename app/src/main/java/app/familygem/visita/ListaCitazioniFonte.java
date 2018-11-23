// Genera una lista delle citazioni di una fonte con il contenitore della citazione
// Usato da Fonte

package app.familygem.visita;

import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;
import org.folg.gedcom.model.Visitor;
import java.util.LinkedHashMap;
import java.util.Map;

public class ListaCitazioniFonte extends Visitor {

	public Map<SourceCitation,Object> lista = new LinkedHashMap<>();
	private String id;

	public ListaCitazioniFonte( String id ){
		this.id = id;
	}

	private void elenca( SourceCitationContainer contenitore ) {
		for( SourceCitation citaz : contenitore.getSourceCitations() )
			// Le fonti-note non hanno Ref ad una fonte
			if( citaz.getRef() != null && citaz.getRef().equals(id) )
				lista.put( citaz, contenitore );
	}

	@Override
	public boolean visit( Person citatore ){
		elenca(citatore);
		return true;
	}
	@Override
	public boolean visit( Family citatore ) {
		elenca(citatore);
		return true;
	}
	@Override
	public boolean visit( Name citatore ) {
		elenca(citatore);
		return true;
	}
	@Override
	public boolean visit( EventFact citatore ) {
		elenca(citatore);
		return true;
	}
	// Note non estende SourceCitationContainer, ma implementa i suoi propri metodi
	@Override
	public boolean visit( Note citatore ) {
		for( SourceCitation citaz : citatore.getSourceCitations() )
			if( citaz.getRef() != null && citaz.getRef().equals(id) )
				lista.put( citaz, citatore );
		return true;
	}
}
