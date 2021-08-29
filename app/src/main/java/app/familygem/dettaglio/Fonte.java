package app.familygem.dettaglio;

import android.content.Intent;
import androidx.cardview.widget.CardView;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.folg.gedcom.model.RepositoryRef;
import org.folg.gedcom.model.Source;
import app.familygem.Biblioteca;
import app.familygem.Dettaglio;
import app.familygem.Memoria;
import app.familygem.R;
import app.familygem.U;
import app.familygem.visita.ListaCitazioniFonte;
import static app.familygem.Global.gc;

public class Fonte extends Dettaglio {

	Source f;

	@Override
	public void impagina() {
		setTitle( R.string.source );
		f = (Source) casta( Source.class );
		mettiBava( "SOUR", f.getId() );
		ListaCitazioniFonte citazioni = new ListaCitazioniFonte( gc, f.getId() );
		f.putExtension( "citaz", citazioni.lista.size() );	// per la Biblioteca
		metti( getString(R.string.abbreviation), "Abbreviation" );
		metti( getString(R.string.title), "Title", true, true );
		metti( getString(R.string.type), "Type", false, true );	// _type
		metti( getString(R.string.author), "Author", true, true );
		metti( getString(R.string.publication_facts), "PublicationFacts", true, true );
		metti( getString(R.string.date), "Date" );	// sempre null nel mio Gedcom
		metti( getString(R.string.text), "Text", true, true );
		metti( getString(R.string.call_number), "CallNumber", false, false ); // CALN deve stare nel SOURCE_REPOSITORY_CITATION
		metti( getString(R.string.italic), "Italic", false, false );	// _italic indicates source title to be in italics ???
		metti( getString(R.string.media_type), "MediaType", false, false );	// MEDI, sarebbe in SOURCE_REPOSITORY_CITATION
		metti( getString(R.string.parentheses), "Paren", false, false );	// _PAREN indicates source facts are to be enclosed in parentheses
		metti( getString(R.string.reference_number), "ReferenceNumber" );	// refn false???
		metti( getString(R.string.rin), "Rin", false, false );
		metti( getString(R.string.user_id), "Uid", false, false );
		mettiEstensioni( f );
		// Mette la citazione all'archivio
		if( f.getRepositoryRef() != null ) {
			View vistaRef = LayoutInflater.from(this).inflate( R.layout.pezzo_citazione_fonte, box, false );
			box.addView( vistaRef );
			vistaRef.setBackgroundColor( getResources().getColor(R.color.archivioCitazione) );
			final RepositoryRef refArchivio = f.getRepositoryRef();
			if( refArchivio.getRepository(gc) != null ) {
				( (TextView) vistaRef.findViewById( R.id.fonte_testo ) ).setText( refArchivio.getRepository( gc ).getName() );
				(( CardView) vistaRef.findViewById( R.id.citazione_fonte )).setCardBackgroundColor( getResources().getColor(R.color.archivio) );
			} else vistaRef.findViewById( R.id.citazione_fonte ).setVisibility( View.GONE );
			String t = "";
			if( refArchivio.getValue() != null ) t += refArchivio.getValue() + "\n";
			if( refArchivio.getCallNumber() != null ) t += refArchivio.getCallNumber() + "\n";
			if( refArchivio.getMediaType() != null ) t += refArchivio.getMediaType() + "\n";
			TextView vistaTesto = vistaRef.findViewById( R.id.citazione_testo );
			if( t.isEmpty() ) vistaTesto.setVisibility( View.GONE );
			else vistaTesto.setText( t.substring( 0, t.length() - 1 ) );
			U.mettiNote( (LinearLayout)vistaRef.findViewById( R.id.citazione_note ), refArchivio, false );
			vistaRef.setOnClickListener( new View.OnClickListener() {
				public void onClick( View v ) {
					Memoria.aggiungi( refArchivio );
					startActivity( new Intent( Fonte.this, ArchivioRef.class ) );
				}
			} );
			registerForContextMenu( vistaRef );
			vistaRef.setTag( R.id.tag_oggetto, refArchivio );	// per il menu contestuale
		}
		U.mettiNote( box, f, true );
		U.mettiMedia( box, f, true );
		U.cambiamenti( box, f.getChange() );
		if( !citazioni.lista.isEmpty() )
			U.mettiDispensa( box, citazioni.getCapi(), R.string.cited_by );
	}

	@Override
	public void elimina() {
		U.aggiornaDate( Biblioteca.eliminaFonte(f) );
	}
}
