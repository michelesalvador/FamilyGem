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
import app.familygem.Chiesa;
import app.familygem.Dettaglio;
import app.familygem.Individuo;
import app.familygem.R;
import app.familygem.U;
import static app.familygem.Globale.gc;

public class Famiglia extends Dettaglio {

	Family f;

	@Override
	public void impagina() {
		String idFamiglia = getIntent().getStringExtra("idFamiglia");
		f = gc.getFamily( idFamiglia );
		oggetto = f;
		setTitle( R.string.family );
		vistaId.setText( f.getId() );

		for( Person marito : f.getHusbands(gc) )
			membro( marito, getString(R.string.husband) );	// todo se ci sono figli "Padre" "Madre" ??
		for( Person moglie : f.getWives(gc) )
			membro( moglie, getString(R.string.wife) );
		for( EventFact ef : f.getEventsFacts() ) {
			if( ef.getTag().equals("MARR") )
				metti( getString(R.string.marriage), ef );
		}
		for( Person figlio : f.getChildren(gc) )
			membro( figlio, ( U.sesso(figlio)==2 )? getString(R.string.daughter) : getString(R.string.son) );
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

	void membro( final Person p, String ruolo ) {
		View vistaPersona = U.mettiIndividuo( box, p, ruolo );
		vistaPersona.setTag( R.id.tag_oggetto, p ); // per il menu contestuale in Dettaglio
		registerForContextMenu( vistaPersona );
		vistaPersona.setOnClickListener( new View.OnClickListener() {
			public void onClick( View v ) {
				if( !p.getParentFamilies(gc).isEmpty() )
					if( !p.getParentFamilies(gc).get(0).equals( f ) ) {
						Intent intento = new Intent( Famiglia.this, Famiglia.class );
						intento.putExtra( "idFamiglia", p.getParentFamilies(gc).get(0).getId() );
						startActivity( intento );
						return;
					}
				if( !p.getSpouseFamilies(gc).isEmpty() )
					if( !p.getSpouseFamilies(gc).get(0).equals( f ) ) {
						Intent intento = new Intent( Famiglia.this, Famiglia.class );
						intento.putExtra( "idFamiglia", p.getSpouseFamilies(gc).get(0).getId() );
						startActivity( intento );
						return;
					}
				Intent intento = new Intent( Famiglia.this, Individuo.class );
				intento.putExtra( "idIndividuo", p.getId() );
				startActivity( intento );
			}
		} );
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
				if( U.sesso(tizio) == 1 ) fam.addHusband( sr );
				else fam.addWife( sr );

				// il ref della famiglia nell'indi
				SpouseFamilyRef sfr = new SpouseFamilyRef();
				sfr.setRef( fam.getId() );
				//tizio.getSpouseFamilyRefs().add( sfr );	// no: con lista vuota UnsupportedOperationException
				//List<SpouseFamilyRef> listaSfr = tizio.getSpouseFamilyRefs();	// Non va bene:
				// quando la lista è inesistente, anzichè restituire una ArrayList restituisce una Collections$EmptyList che è IMMUTABILE cioè non ammette add()
				List<SpouseFamilyRef> listaSfr = new ArrayList<>( tizio.getSpouseFamilyRefs() );	// ok
				//s.l( listaSfr + " "+ listaSfr.size() +" "+ listaSfr.isEmpty() +" "+ listaSfr.getClass() );
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

	// Elimina famiglia
	@Override
	public void elimina() {
		Chiesa.elimina( f.getId() );
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