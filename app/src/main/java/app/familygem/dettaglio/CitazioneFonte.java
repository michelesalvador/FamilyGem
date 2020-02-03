package app.familygem.dettaglio;

import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;
import app.familygem.Dettaglio;
import app.familygem.Memoria;
import app.familygem.R;
import app.familygem.U;
import static app.familygem.Globale.gc;

public class CitazioneFonte extends Dettaglio {

	SourceCitation c;

	@Override
	public void impagina() {
		mettiBava( "SOUR" );
		c = (SourceCitation) casta( SourceCitation.class );
		if( c.getSource(gc) != null ) {  // source CITATION valida
			setTitle( R.string.source_citation );
			U.linkaFonte( box, c.getSource(gc) );
		} else if( c.getRef() != null ) {  // source CITATION di una fonte inesistente (magari eliminata)
			setTitle( R.string.inexistent_source_citation );
		} else {	// source NOTE
			setTitle( R.string.source_note );
			metti( getString(R.string.value), "Value", true, true );
		}
		metti( getString(R.string.page), "Page", true, true );
		metti( getString(R.string.date), "Date" );
		metti( getString(R.string.text), "Text", true, true );	// vale sia per sourceNote che per sourceCitation
		//c.getTextOrValue();	praticamente inutile
		//if( c.getDataTagContents() != null )
		//	U.metti( box, "Data Tag Contents", c.getDataTagContents().toString() );	// COMBINED DATA TEXT
		metti( getString(R.string.certainty), "Quality" );	// un numero da 0 a 3
		//metti( "Ref", "Ref", false, false ); // l'id della fonte
		mettiEstensioni( c );
		U.mettiNote( box, c, true );
		U.mettiMedia( box, c, true );
	}

	@Override
	public void elimina() {
		Object contenitore = Memoria.oggettoContenitore();
		if( contenitore instanceof Note )	// Note non extende SourceCitationContainer
			((Note)contenitore).getSourceCitations().remove( c );
		else
			((SourceCitationContainer)contenitore).getSourceCitations().remove( c );
		U.aggiornaDate( Memoria.oggettoCapo() );
		Memoria.annullaIstanze(c);
	}
}
