package app.familygem.detail;

import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;
import app.familygem.Dettaglio;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import static app.familygem.Global.gc;

public class CitazioneFonte extends Dettaglio {

	SourceCitation c;

	@Override
	public void impagina() {
		placeSlug("SOUR");
		c = (SourceCitation)cast(SourceCitation.class);
		if( c.getSource(gc) != null ) {  // source CITATION valida
			setTitle(R.string.source_citation);
			U.placeSource(box, c.getSource(gc), true);
		} else if( c.getRef() != null ) {  // source CITATION di una fonte inesistente (magari eliminata)
			setTitle(R.string.inexistent_source_citation);
		} else {    // source NOTE
			setTitle(R.string.source_note);
			place(getString(R.string.value), "Value", true, true);
		}
		place(getString(R.string.page), "Page", true, true);
		place(getString(R.string.date), "Date");
		place(getString(R.string.text), "Text", true, true);    // vale sia per sourceNote che per sourceCitation
		//c.getTextOrValue();	praticamente inutile
		//if( c.getDataTagContents() != null )
		//	U.metti( box, "Data Tag Contents", c.getDataTagContents().toString() );	// COMBINED DATA TEXT
		place(getString(R.string.certainty), "Quality");    // un numero da 0 a 3
		//metti( "Ref", "Ref", false, false ); // l'id della fonte
		placeExtensions(c);
		U.placeNotes(box, c, true);
		U.placeMedia(box, c, true);
	}

	@Override
	public void elimina() {
		Object contenitore = Memory.oggettoContenitore();
		if( contenitore instanceof Note ) // Note doesn't extend SourceCitationContainer
			((Note)contenitore).getSourceCitations().remove(c);
		else
			((SourceCitationContainer)contenitore).getSourceCitations().remove(c);
		U.updateChangeDate(Memory.oggettoCapo());
		Memory.annullaIstanze(c);
	}
}
