package app.familygem.dettaglio;

import android.content.Intent;
import android.view.View;
import org.folg.gedcom.model.ChildRef;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.ParentFamilyRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SpouseFamilyRef;
import org.folg.gedcom.model.SpouseRef;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import app.familygem.Dettaglio;
import app.familygem.EditaIndividuo;
import app.familygem.Globale;
import app.familygem.Individuo;
import app.familygem.Memoria;
import app.familygem.R;
import app.familygem.U;
import app.familygem.s;
import static app.familygem.Globale.gc;

public class Famiglia extends Dettaglio {

	Family f;

	@Override
	public void impagina() {
		setTitle( R.string.family );
		f = (Family) casta( Family.class );
		mettiBava( "FAM", f.getId() );
		for( Person marito : f.getHusbands(gc) )
			membro( marito, !f.getChildRefs().isEmpty() ? 1 : 2 );
		for( Person moglie : f.getWives(gc) )
			membro( moglie, !f.getChildRefs().isEmpty() ? 1 : 2 );
		for( EventFact ef : f.getEventsFacts() ) {
			if( ef.getTag().equals("MARR") )
				metti( getString(R.string.marriage), ef );
		}
		for( Person figlio : f.getChildren(gc) )
			membro( figlio, 3 );
		for( EventFact ef : f.getEventsFacts() ) {
			if( !ef.getTag().equals( "MARR" ) )
				metti( ef.getDisplayType(), ef );
		}
		mettiEstensioni( f );
		U.mettiNote( box, f, true );
		U.mettiMedia( box, f, true );
		U.citaFonti( box, f );
		U.cambiamenti( box, f.getChange() );
	}

	void membro( Person p, int relazione ) {
		int ruolo = 0;
		if( U.sesso(p) == 1 ) {
			switch( relazione ) {
				case 1: ruolo = R.string.father; break;
				case 2: ruolo = R.string.husband; break;
				case 3: ruolo = R.string.son;
			}
		} else if( U.sesso(p) == 2 ) {
			switch( relazione ) {
				case 1: ruolo = R.string.mother; break;
				case 2: ruolo = R.string.wife; break;
				case 3: ruolo = R.string.daughter;
			}
		} else {
			switch( relazione ) {
				case 1: ruolo = R.string.parent; break;
				case 2: ruolo = R.string.spouse; break;
				case 3: ruolo = R.string.child;
			}
		}
		View vistaPersona = U.mettiIndividuo( box, p, getString(ruolo) );
		vistaPersona.setTag( R.id.tag_oggetto, p ); // per il menu contestuale in Dettaglio
		registerForContextMenu( vistaPersona );
		vistaPersona.setOnClickListener( v -> {
			List<Family> parentFam = p.getParentFamilies(gc);
			List<Family> spouseFam = p.getSpouseFamilies(gc);
			// un coniuge con una o più famiglie in cui è figlio
			if( (relazione==1 || relazione==2) && !parentFam.isEmpty() ) {
				U.qualiGenitoriMostrare( this, p, 2 );
			} // un figlio con una o più famiglie in cui è coniuge
			else if( relazione == 3 && !p.getSpouseFamilies(gc).isEmpty() ) {
				U.qualiConiugiMostrare( this, p, null );
			} // un figlio non sposato che ha più famiglie genitoriali
			else if( parentFam.size() > 1 ) {
				if( parentFam.size() == 2 ) { // Swappa tra le 2 famiglie genitoriali
					Globale.individuo = p.getId();
					Globale.numFamiglia = parentFam.indexOf(f) == 0 ? 1 : 0;
					Memoria.replacePrimo( parentFam.get(Globale.numFamiglia) );
					recreate();
				} else // Più di due famiglie
					U.qualiGenitoriMostrare( this, p, 2 );
			} // un coniuge senza genitori ma con più famiglie coniugali
			else if( spouseFam.size() > 1 ) {
				if( spouseFam.size() == 2 ) { // Swappa tra le 2 famiglie coniugali
					Globale.individuo = p.getId();
					Family altraFamiglia = spouseFam.get( spouseFam.indexOf(f) == 0 ? 1 : 0 );
					Memoria.replacePrimo( altraFamiglia );
					recreate();
				} else
					U.qualiConiugiMostrare( this, p, null );
			} else {
				Memoria.setPrimo( p );
				startActivity( new Intent(this, Individuo.class) );
			}
		});
		if( unRappresentanteDellaFamiglia == null )
			unRappresentanteDellaFamiglia = p;
	}

	// Collega una persona ad una famiglia come genitore o figlio
	public static void aggrega( Person tizio, Family fam, int ruolo ) {
		switch( ruolo ) {
			case 5:	// Genitore
				// il ref dell'indi nella famiglia
				SpouseRef sr = new SpouseRef();
				sr.setRef( tizio.getId() );
				EditaIndividuo.aggiungiConiuge( fam, sr );

				// il ref della famiglia nell'indi
				SpouseFamilyRef sfr = new SpouseFamilyRef();
				sfr.setRef( fam.getId() );
				//tizio.getSpouseFamilyRefs().add( sfr );	// no: con lista vuota UnsupportedOperationException
				//List<SpouseFamilyRef> listaSfr = tizio.getSpouseFamilyRefs();	// Non va bene:
				// quando la lista è inesistente, anzichè restituire una ArrayList restituisce una Collections$EmptyList che è IMMUTABILE cioè non ammette add()
				List<SpouseFamilyRef> listaSfr = new ArrayList<>( tizio.getSpouseFamilyRefs() );	// ok
				listaSfr.add( sfr );	// ok
				tizio.setSpouseFamilyRefs( listaSfr );
				break;
			case 6:	// Figlio
				ChildRef cr = new ChildRef();
				cr.setRef( tizio.getId() );
				fam.addChild( cr );
				ParentFamilyRef pfr = new ParentFamilyRef();
				pfr.setRef( fam.getId() );
				//tizio.getParentFamilyRefs().add( pfr );	// UnsupportedOperationException
				List<ParentFamilyRef> listaPfr = new ArrayList<>( tizio.getParentFamilyRefs() );
				listaPfr.add( pfr );
				tizio.setParentFamilyRefs( listaPfr );
		}
	}

	// Rimuove i ref reciproci individuo-famiglia
	public static void scollega( String idIndi, Family fam ) {
		// Rimuove i ref dell'indi nella famiglia
		Iterator<SpouseRef> refiSposo = fam.getHusbandRefs().iterator();
		while( refiSposo.hasNext() )
			if( refiSposo.next().getRef().equals(idIndi) )
				refiSposo.remove();
		// poi nella famiglia rimane   "husbandRefs":[]   vuoto, todo forse bisognerebbe eliminarlo?

		refiSposo = fam.getWifeRefs().iterator();
		while( refiSposo.hasNext() )
			if( refiSposo.next().getRef().equals(idIndi) )
				refiSposo.remove();

		Iterator<ChildRef> refiFiglio = fam.getChildRefs().iterator();
		while( refiFiglio.hasNext() )
			if( refiFiglio.next().getRef().equals(idIndi) )
				refiFiglio.remove();

		// Rimuove i ref della famiglia nell'indi
		Iterator<SpouseFamilyRef> iterSfr = gc.getPerson(idIndi).getSpouseFamilyRefs().iterator();
		while( iterSfr.hasNext() )
			if( iterSfr.next().getRef().equals(fam.getId()) )
				iterSfr.remove();
		// anche qui nell'indi rimane un  "fams":[]  vuoto	todo eliminarlo?

		Iterator<ParentFamilyRef> iterPfr = gc.getPerson(idIndi).getParentFamilyRefs().iterator();
		while( iterPfr.hasNext() )
			if( iterPfr.next().getRef().equals(fam.getId()) )
				iterPfr.remove();
	}
}