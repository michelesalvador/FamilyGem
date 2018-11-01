package app.familygem.dettaglio;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;
import app.familygem.Dettaglio;
import app.familygem.Ponte;
import app.familygem.R;
import app.familygem.U;
import static app.familygem.Globale.gc;

public class CitazioneFonte extends Dettaglio {

	//SourceCitation c = Globale.citazioneFonte;
	SourceCitation c = (SourceCitation) Ponte.ricevi("oggetto" );
	//SourceCitation c;

	@Override
	public void impagina() {
		if( c != null ) {
			//c = new Gson().fromJson( getIntent().getStringExtra( "o" ), SourceCitation.class );
			oggetto = c;
			if( c.getSource(gc) != null ) {  // source CITATION valida
				setTitle( R.string.source_citation );
				vistaId.setText( c.getRef() );    // che è poi l'id della fonte
				U.linkaFonte( box, c.getSource(gc) );
			} else if( c.getRef() != null ) {  // source CITATION di una fonte inesistente (magari eliminata)
				setTitle( R.string.inexistent_source_citation );
				vistaId.setText( c.getRef() );
			} else {    // source NOTE
				setTitle( R.string.source_note );
				vistaId.setText( "SOUR" );
				metti( getString(R.string.value), "Value", true, true );
			}
			metti( getString(R.string.page), "Page", true, true );
			metti( getString(R.string.date), "Date" );
			metti( getString(R.string.text), "Text", true, true );    // vale sia per sourceNote che per sourceCitation
			//c.getTextOrValue();	praticamente inutile
			//if( c.getDataTagContents() != null )
			//	U.metti( box, "Data Tag Contents", c.getDataTagContents().toString() );    // COMBINED DATA TEXT
			metti( getString(R.string.certainty), "Quality" );    // un numero da 0 a 3
			//metti( "Ref", "Ref" );
			mettiEstensioni( c );
			U.mettiNote( box, c, true );
			U.mettiMedia( box, c, true );
		}
	}

	/* SPOSTATO in Dettaglio
	// Imposta la fonte che è stata scelta in Biblioteca
	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data ) {
		if( requestCode == 7047  ) {
			if( resultCode == RESULT_OK ) {
				c.setRef( data.getStringExtra("idFonte") );
				U.salvaJson();
			//} else if( resultCode == AppCompatActivity.RESULT_CANCELED ) {
			}
		}
	}*/

	@Override
	public void elimina() {
		if( contenitore instanceof Note )	// Note non extende SourceCitationContainer
			((Note)contenitore).getSourceCitations().remove( c );
		else
			((SourceCitationContainer)contenitore).getSourceCitations().remove( c );
	}
}
