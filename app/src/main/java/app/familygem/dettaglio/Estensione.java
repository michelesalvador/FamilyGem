package app.familygem.dettaglio;

import org.folg.gedcom.model.ExtensionContainer;
import org.folg.gedcom.model.GedcomTag;
import app.familygem.Dettaglio;
import app.familygem.Ponte;
import app.familygem.R;
import app.familygem.U;

public class Estensione extends Dettaglio {

	GedcomTag e = (GedcomTag) Ponte.ricevi( "oggetto" );

	@Override
	public void impagina() {
		if( e != null ) {
			oggetto = e;
			setTitle( getString( R.string.extension ) );
			vistaId.setText( e.getTag() );
			metti( getString(R.string.id), "Id", false, false );
			metti( getString(R.string.value), "Value", true, true );
			metti( "Ref", "Ref", false, false );
			metti( "ParentTagName", "ParentTagName", false, false ); // non ho capito se viene usato o no
			for( GedcomTag figlio : e.getChildren() ) {
				String testo = U.scavaEstensione(figlio);
				if( testo.endsWith("\n") )
					testo = testo.substring( 0, testo.length()-1 );
				creaPezzo( figlio.getTag(), testo, figlio, true );
			}
		}
	}

	@Override
	public void elimina() {
		//((ExtensionContainer)contenitore).getExtensions().remove( e );// non si fa così
		U.eliminaEstensione( e, contenitore, null );
	}
}
