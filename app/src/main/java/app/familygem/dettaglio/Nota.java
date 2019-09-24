package app.familygem.dettaglio;

import android.app.Activity;
import org.folg.gedcom.model.Note;
import app.familygem.Dettaglio;
import app.familygem.Globale;
import app.familygem.Memoria;
import app.familygem.R;
import app.familygem.U;
import app.familygem.visita.RiferimentiNota;

public class Nota extends Dettaglio {

	Note n;

	@Override
	public void impagina() {
		n = (Note) casta( Note.class );
		if( n.getId() == null ) {
			setTitle( R.string.note );
			mettiBava( "NOTE" );
		}
		else {
			setTitle( R.string.shared_note );
			mettiBava( "NOTE", n.getId() );
		}
		metti( getString(R.string.text), "Value", true, true);
		metti( getString(R.string.rin), "Rin", false, false );
		mettiEstensioni( n );
		U.citaFonti( box, n );
		U.cambiamenti( box, n.getChange() );
		if( n.getId() != null ) {
			RiferimentiNota rifNota = new RiferimentiNota( Globale.gc, n.getId(), false );
			if( rifNota.tot > 0 )
				U.mettiDispensa( box, rifNota.capostipiti.toArray(), R.string.shared_by );
		} else if( ((Activity)box.getContext()).getIntent().getBooleanExtra("daQuaderno",false) ) {
			U.mettiDispensa( box, Memoria.oggettoCapo(), R.string.written_in );
		}
	}

	@Override
	public void elimina() {
		U.aggiornaDate( U.eliminaNota(n,null) );
	}
}