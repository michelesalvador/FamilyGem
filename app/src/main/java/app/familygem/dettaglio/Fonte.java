package app.familygem.dettaglio;

import android.content.Intent;
import androidx.cardview.widget.CardView;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.RepositoryRef;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import java.util.Map;
import app.familygem.Chiesa;
import app.familygem.Dettaglio;
import app.familygem.Ponte;
import app.familygem.R;
import app.familygem.U;
import app.familygem.visita.ListaCitazioniFonte;
import static app.familygem.Globale.gc;

public class Fonte extends Dettaglio {

	Source f = (Source) Ponte.ricevi( "oggetto" );

	@Override
	public void impagina() {
		if( f != null ) {
			oggetto = f;
			setTitle( R.string.source );
			vistaId.setText( f.getId() );
			ListaCitazioniFonte citazioni = new ListaCitazioniFonte( f.getId() );
			gc.accept( citazioni );
			f.putExtension( "citaz", citazioni.lista.size() );	// per la Biblioteca
			metti( getString(R.string.abbreviation), "Abbreviation" );
			metti( getString(R.string.title), "Title", true, true );
			metti( getString(R.string.type), "Type", false, true );	// _type
			metti( getString(R.string.author), "Author", true, true );
			metti( getString(R.string.publication_facts), "PublicationFacts", true, true );
			metti( getString(R.string.date), "Date" );    // sempre null nel mio Gedcom
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
					( (TextView) vistaRef.findViewById( R.id.fonte_titolo ) ).setText( refArchivio.getRepository( gc ).getName() );
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
						Ponte.manda( refArchivio, "oggetto" );
						Ponte.manda( f, "contenitore" ); // serve per eliminare il ref
						startActivity( new Intent( Fonte.this, ArchivioRef.class ) );
					}
				} );
				registerForContextMenu( vistaRef );
				vistaRef.setTag( R.id.tag_oggetto, refArchivio );	// per il menu contestuale
			}
			U.mettiNote( box, f, true );
			U.mettiMedia( box, f, true );
			U.cambiamenti( box, f.getChange() );

			if( !citazioni.lista.isEmpty() ) {
				LayoutInflater.from(this).inflate( R.layout.titoletto, box, true );
				((TextView)findViewById(R.id.titolo_testo)).setText( R.string.cited_by );
			}
			// TODO ricondurre questa lista variegata solo alle Person e alle Family
			for( Map.Entry<SourceCitation,Object> e : citazioni.lista.entrySet() ) {
				// in effetti la chiave SourceCitation non viene usata, anzichè un Map una semplice List?
				if( e.getValue() instanceof Person )
					U.linkaPersona( box, (Person) e.getValue(), 1 );
				if( e.getValue() instanceof Family )
					Chiesa.mettiFamiglia( box, (Family)e.getValue() );
				if( e.getValue() instanceof Name )
					U.metti( box, "Name", U.nomeCognome((Name)e.getValue()) );
				if( e.getValue() instanceof EventFact )
					U.metti( box, ((EventFact)e.getValue()).getDisplayType(), "" + ((EventFact)e.getValue()).getValue() );
				if( e.getValue() instanceof Note )
					U.metti( box, "Note", ((Note)e.getValue()).getValue() );
			}
		}
	}

	@Override
	public void elimina() {
		// todo: ma se la fonte è citata, la citazione rimane....
		gc.getSources().remove( f );
		gc.createIndexes();	// necessario
	}
}
