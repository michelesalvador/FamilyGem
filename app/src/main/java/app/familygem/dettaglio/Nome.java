package app.familygem.dettaglio;

import org.folg.gedcom.model.Name;
import app.familygem.Dettaglio;
import app.familygem.Globale;
import app.familygem.Ponte;
import app.familygem.R;
import app.familygem.U;
import static app.familygem.Globale.gc;

public class Nome extends Dettaglio {

	//Name n = (Name) Globale.oggetto;
	Name n = (Name) Ponte.ricevi( "oggetto" );

	@Override
	public void impagina() {
		//s.l( "IMPAGINA, n = " + n );
		if( n != null ) {
			//n = (Name) Globale.oggetto;
			oggetto = n;
			setTitle( R.string.name );
			vistaId.setText( "NAME" );
			metti( getString(R.string.value), "Value" );
			metti( getString(R.string.type), "Type", false, false );  // _type non Gedcom standard
			metti( getString(R.string.prefix), "Prefix" );
			metti( getString(R.string.given), "Given" );
			metti( getString(R.string.nickname), "Nickname" );
			metti( getString(R.string.surname_prefix), "SurnamePrefix" );
			metti( getString(R.string.surname), "Surname" );
			metti( getString(R.string.suffix), "Suffix" );
			metti( getString(R.string.married_name), "MarriedName", false, false ); // _marrnm
			metti( getString(R.string.aka), "Aka", false, false );    // _aka
			metti( getString(R.string.romanized), "Romn", false, false );
			metti( getString(R.string.phonetic), "Fone", false, false );
			mettiEstensioni( n );
			//for( Note nota : n.getAllNotes( gc ) )
				//U.mettiNota( box, nota, true );
			U.mettiNote( box, n, true );
			//for( Media med : n.getAllMedia( gc ) )
			U.mettiMedia( box, n, true );    // Mi sembra strano che un Name abbia Media.. comunque..
			//for( SourceCitation citaz : n.getSourceCitations() )
			U.citaFonti( box, n );
		}
	}

	/* sostituito da U.mettin.getAllMedia( gc )
	void metti( String tit, String cosa ) {
		View vistaPezzo = LayoutInflater.from(box.getContext()).inflate( R.layout.pezzo_fatto, box, false );
		box.addView( vistaPezzo );
		((TextView)vistaPezzo.findViewById( R.id.fatto_titolo )).setText( tit );
		TextView vistaTesto = vistaPezzo.findViewById( R.id.fatto_testo );
		if( cosa.isEmpty() ) vistaTesto.setVisibility( View.GONE );
		else vistaTesto.setText( cosa );
		//vistaPezzo.setTag( n.getTag() );	approcio per rendere il pezzo editabile..
	}*/

	// Menu opzioni
	@Override
	public void elimina() {
		gc.getPerson( Globale.individuo ).getNames().remove( n );
		//((Person)contenitore).getNames().remove( n );	// se non vuoi usare Globale.individuo
	}
}
