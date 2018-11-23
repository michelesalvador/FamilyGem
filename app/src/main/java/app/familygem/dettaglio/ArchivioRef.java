package app.familygem.dettaglio;

import android.content.Intent;
import android.support.v7.widget.CardView;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import org.folg.gedcom.model.RepositoryRef;
import org.folg.gedcom.model.Source;
import app.familygem.Dettaglio;
import app.familygem.Ponte;
import app.familygem.R;
import app.familygem.U;
import static app.familygem.Globale.gc;

public class ArchivioRef extends Dettaglio {

	RepositoryRef r = (RepositoryRef) Ponte.ricevi("oggetto" );

	@Override
	public void impagina() {
		if( r != null ) {
			oggetto = r;
			if( r.getRepository(gc) != null ) {  // valido
				setTitle( R.string.repository_citation );
				vistaId.setText( r.getRef() );    // che è poi l'id dell'archivio
				View cartaRepo = LayoutInflater.from(this).inflate( R.layout.pezzo_fonte, box, false );
				box.addView( cartaRepo );
				((TextView) cartaRepo.findViewById( R.id.fonte_titolo ) ).setText( r.getRepository(gc).getName() );
				((CardView) cartaRepo).setCardBackgroundColor( getResources().getColor(R.color.archivio) );
				cartaRepo.setTag( R.id.tag_oggetto, r.getRepository(gc) );	// per il menu contestuale
				registerForContextMenu( cartaRepo );
				cartaRepo.setOnClickListener( new View.OnClickListener() {
					@Override
					public void onClick( View v ) {
						Ponte.manda( r.getRepository(gc), "oggetto" );
						startActivity( new Intent( getApplicationContext(), Archivio.class ) );
					}
				});
			} else if( r.getRef() != null ) {  // di un archivio inesistente (magari eliminato)
				setTitle( R.string.inexistent_repository_citation );
				vistaId.setText( r.getRef() );
			} else {    // senza ref??
				setTitle( R.string.repository_note );
				vistaId.setText( "REPO" );

			}
			metti( getString(R.string.value), "Value", false, true );
			metti( getString(R.string.call_number), "CallNumber" );
			metti( getString(R.string.media_type), "MediaType" );
			mettiEstensioni( r );
			U.mettiNote( box, r, true );
		}
	}

	@Override
	public void elimina() {
		((Source)contenitore).setRepositoryRef( null );
	}
}