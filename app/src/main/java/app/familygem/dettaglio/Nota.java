package app.familygem.dettaglio;

import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import app.familygem.Dettaglio;
import app.familygem.Ponte;
import app.familygem.R;
import app.familygem.U;
import static app.familygem.Globale.gc;

public class Nota extends Dettaglio {

	Note n = (Note) Ponte.ricevi( "oggetto" );

	@Override
	public void impagina() {
		if( n != null ) {
			oggetto = n;
			setTitle( R.string.note );
			if( n.getId() == null ) vistaId.setText( "NOTE" );
			else vistaId.setText( n.getId() );
			metti( getString(R.string.text), "Value", true, true);
			metti( getString(R.string.rin), "Rin", false, false );
			mettiEstensioni( n );
			U.citaFonti( box, n );
			U.cambiamenti( box, n.getChange() );
		}
	}

	@Override
	public void elimina() {
		((NoteContainer)contenitore).getNotes().remove( n );	// rimuove solo se è una nota locale, non se object note
		gc.getNotes().remove( n );	// la rimuove se è un'object note
		gc.createIndexes();	// necessario per gli object note
	}
}
