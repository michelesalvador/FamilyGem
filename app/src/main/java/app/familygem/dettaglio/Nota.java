package app.familygem.dettaglio;

import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import app.familygem.Dettaglio;
import app.familygem.Ponte;
import app.familygem.R;
import app.familygem.U;
import static app.familygem.Globale.gc;

public class Nota extends Dettaglio {

	//Note n = Globale.nota;
	Note n = (Note) Ponte.ricevi( "oggetto" );

	@Override
	public void impagina() {
		if( n != null ) {
			//n = new Gson().fromJson( getIntent().getStringExtra("o"), Note.class );
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
		/*List<Note> listaNote = gc.getPerson( Globale.individuo ).getAllNotes(gc);
		for( Note not : listaNote ) {
			s.l( "PRIMA ===>>> " + not.getValue() );
		}
		for( Note not : listaNote ) {
			if( not.equals( n ) ) {
				listaNote.remove( not );
				break;
			}
		}
		for( Note not : listaNote ) {
			s.l( "DOPO ======== " + not.getValue() );
		}
		gc.getPerson( Globale.individuo ).setNotes( listaNote );*/

		//int indice = ((NoteContainer)contenitore).getAllNotes(gc).indexOf( n );
		//s.l( "ELIMINA NOTA " + indice + "  " + contenitore + "   "  + n  );
		//((NoteContainer)contenitore).getAllNotes(gc).remove( n );	// non rimuove nessuna nota
		//((NoteContainer)contenitore).getAllNotes(gc).remove( indice ); // idem
		((NoteContainer)contenitore).getNotes().remove( n );	// rimuove solo se è una nota locale, non se object note
		//((NoteContainer)contenitore).getNotes().remove( 0 );	// idem, in base all'indice
		gc.getNotes().remove( n );	// la rimuove se è un'object note
		gc.createIndexes();	// necessario per gli object note
	}
}
