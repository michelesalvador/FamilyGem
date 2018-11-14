package app.familygem.dettaglio;

import org.folg.gedcom.model.Name;
import app.familygem.Dettaglio;
import app.familygem.Globale;
import app.familygem.Ponte;
import app.familygem.R;
import app.familygem.U;
import static app.familygem.Globale.gc;

public class Nome extends Dettaglio {

	Name n = (Name) Ponte.ricevi( "oggetto" );

	@Override
	public void impagina() {
		if( n != null ) {
			oggetto = n;
			setTitle( R.string.name );
			vistaId.setText( "NAME" );
			if( Globale.preferenze.esperto )
				metti( getString(R.string.value), "Value" );
			else {
				String epiteto = n.getValue();
				String nome = epiteto.replaceAll( "/.*?/", "" ).trim();
				creaPezzo( getString(R.string.name), nome, 4043, false );
				String cognome = "";
				if( epiteto.indexOf('/') < epiteto.lastIndexOf('/') )
					cognome = epiteto.substring( epiteto.indexOf('/') + 1, epiteto.lastIndexOf('/') ).trim();
				creaPezzo( getString(R.string.surname), cognome, 6064, false );
			}
			metti( getString(R.string.type), "Type", false, false );  // _type non Gedcom standard
			metti( getString(R.string.prefix), "Prefix" );
			metti( getString(R.string.given), "Given", Globale.preferenze.esperto, false );
			metti( getString(R.string.nickname), "Nickname" );
			metti( getString(R.string.surname_prefix), "SurnamePrefix" );
			metti( getString(R.string.surname), "Surname", Globale.preferenze.esperto, false );
			metti( getString(R.string.suffix), "Suffix" );
			metti( getString(R.string.married_name), "MarriedName", false, false ); // _marrnm
			metti( getString(R.string.aka), "Aka", false, false );    // _aka
			metti( getString(R.string.romanized), "Romn", false, false );
			metti( getString(R.string.phonetic), "Fone", false, false );
			mettiEstensioni( n );
			U.mettiNote( box, n, true );
			U.mettiMedia( box, n, true );    // Mi sembra strano che un Name abbia Media.. comunque..
			U.citaFonti( box, n );
		}
	}

	@Override
	public void elimina() {
		gc.getPerson( Globale.individuo ).getNames().remove( n );
		//((Person)contenitore).getNames().remove( n );	// se non vuoi usare Globale.individuo
	}
}
