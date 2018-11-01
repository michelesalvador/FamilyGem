package app.familygem.dettaglio;

import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.PersonFamilyCommonContainer;
import java.util.Arrays;
import app.familygem.Dettaglio;
import app.familygem.Ponte;
import app.familygem.R;
import app.familygem.U;

public class Evento extends Dettaglio {

	//EventFact e = (EventFact) Globale.oggetto;
	EventFact e = (EventFact) Ponte.ricevi( "oggetto" );
	// Lista di tag di eventi utili per evitare di mettere il Value dell'EventFact
	String[] tagEventi = { "BIRT","CHR","DEAT","BURI","CREM","ADOP","BAPM","BARM","BASM","BLES", // Eventi di Individuo
			"CHRA","CONF","FCOM","ORDN","NATU","EMIG","IMMI","CENS","PROB","WILL","GRAD","RETI",
			"ANUL","DIV","DIVF","ENGA","MARB","MARC","MARR","MARL","MARS" }; // eventi di Famiglia

	@Override
	public void impagina() {
		if( e != null ) {
			//ripulisciTag( e ); non qui, ma alla fine
			oggetto = e;
			//EventFact e = (EventFact) getIntent().getSerializableExtra( "fatto" ); // non funzia
			/*String tit = "";
			if( e.getType() != null )	tit = e.getType();
			else	tit = e.getDisplayType();
			if( tit.isEmpty() )
				tit = "Evento";
			setTitle( tit );*/
			setTitle( e.getDisplayType() );
			vistaId.setText( e.getTag() );
			if( Arrays.asList(tagEventi).contains(e.getTag()) ) // è un evento (senza Value)
				metti( getString(R.string.value), "Value", false, true );
			else // tutti gli altri casi, solitamente attributi (con Value)
				metti( getString(R.string.value), "Value", true, true );
			metti( getString(R.string.type), "Type", false, false ); // troppo raffinato per un'app generalista
			metti( getString(R.string.date), "Date" );
			metti( getString(R.string.place), "Place" );
			//new Uovo( "Indirizzo", e.getAddress() == null ? new Address() : e.getAddress(), false );
			//mettiParticolare( "Indirizzo", e.getAddress() == null ? 140151220 : e.getAddress() );
			//creaPezzo( "Indirizzo", indirizzo(e.getAddress()), e.getAddress() == null ? new Address() : e.getAddress() );
			metti( getString(R.string.address), e.getAddress() );
			//new Uovo( "Causa", e.getCause() == null ? new EventFact() : e.getCause(), false );
			//mettiParticolare( "Causa", e.getCause() == null ? 373470 : e.getCause() );
			//creaPezzo( "Causa", causa(e.getCause()), e.getCause() == null ? new EventFact() : e.getCause() );
			if( e.getTag() != null && e.getTag().equals("DEAT") )
				metti( getString(R.string.cause), "Cause" );
			else
				metti( getString(R.string.cause), "Cause", false, false );
			metti( getString(R.string.www), "Www", false, false );
			metti( getString(R.string.email), "Email", false, false );
			metti( getString(R.string.telephone), "Phone", false, false );
			metti( getString(R.string.fax), "Fax", false, false );
			metti( getString(R.string.rin), "Rin", false, false );
			metti( getString(R.string.user_id), "Uid", false, false );
			/*uova.put( "Valore", "Value" );
			uova.put( "Tipo", "Type" );
			uova.put( "Data", "Date" );
			uova.put( "Indirizzo", e.getAddress() != null ? e.getAddress() : new Address() );
			uova.put( "Luogo", "Place" );
			uova.put( "Causa", e.getCause() != null ? e.getCause() : new EventFact() );    // getCause dà un EventFact
			uova.put( "Sito web", "Www" );
			uova.put( "Email", "Email" );
			uova.put( "Telefono", "Phone" );
			uova.put( "Fax", "Fax" );
			uova.put( "Rin", "Rin" );
			uova.put( "Id utente", "Uid" );*/
			//deponi();
			//altriMetodi = { "WwwTag", "EmailTag", "UidTag" };
			mettiEstensioni( e );
			U.mettiNote( box, e, true );
			U.mettiMedia( box, e, true );
			U.citaFonti( box, e );
		}
	}

	@Override
	public void elimina() {
		//new Visitor().visit( e );
		//e.accept( );
		//gc.getPerson( Globale.individuo ).getEventsFacts().remove( e );
		/*if( contenitore instanceof EventFact )	// siamo in una Cause
			((EventFact)contenitore).setCause( null );
		else*/
		((PersonFamilyCommonContainer)contenitore).getEventsFacts().remove( e );
	}

	// Elimina i principali tag vuoti e eventualmente aggiunge la Y
	public static void ripulisciTag( EventFact ef ) {

		if( ef.getDate()!=null && ef.getDate().isEmpty() ) ef.setDate( null );
		if( ef.getPlace()!=null && ef.getPlace().isEmpty() ) ef.setPlace( null );

		if( ef.getTag()!=null && ( ef.getTag().equals("BIRT") || ef.getTag().equals("CHR") || ef.getTag().equals("DEAT") ) ) {
			if( ef.getDate() == null && ef.getPlace() == null )
				ef.setValue( "Y" );
			else
				ef.setValue( null );
		}
		if( ef.getValue()!=null && ef.getValue().isEmpty() ) ef.setValue( null );
	}
}
