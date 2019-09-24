package app.familygem.dettaglio;

import org.folg.gedcom.model.GedcomTag;
import app.familygem.Dettaglio;
import app.familygem.Memoria;
import app.familygem.R;
import app.familygem.U;

public class Estensione extends Dettaglio {

	GedcomTag e;

	@Override
	public void impagina() {
		setTitle( getString( R.string.extension ) );
		e = (GedcomTag) casta( GedcomTag.class );
		mettiBava( e.getTag() );
		metti( getString(R.string.id), "Id", false, false );
		metti( getString(R.string.value), "Value", true, true );
		metti( "Ref", "Ref", false, false );
		metti( "ParentTagName", "ParentTagName", false, false ); // non ho capito se viene usato o no
		for( GedcomTag figlio : e.getChildren() ) {
			String testo = U.scavaEstensione(figlio,0);
			if( testo.endsWith("\n") )
				testo = testo.substring( 0, testo.length()-1 );
			creaPezzo( figlio.getTag(), testo, figlio, true );
		}
	}

	@Override
	public void elimina() {
		U.eliminaEstensione( e, Memoria.oggettoContenitore(), null );
		U.aggiornaDate( Memoria.oggettoCapo() );
	}
}
