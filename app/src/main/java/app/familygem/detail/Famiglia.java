package app.familygem.detail;

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
import app.familygem.Global;
import app.familygem.Individuo;
import app.familygem.Memoria;
import app.familygem.R;
import app.familygem.U;
import app.familygem.constant.Gender;
import app.familygem.constant.Relation;
import app.familygem.constant.Status;
import static app.familygem.Global.gc;

public class Famiglia extends Dettaglio {

	Family f;

	@Override
	public void impagina() {
		setTitle(R.string.family);
		f = (Family)cast(Family.class);
		placeSlug("FAM", f.getId());
		for( SpouseRef refMarito : f.getHusbandRefs() )
			member(refMarito, Relation.PARTNER);
		for( SpouseRef refMoglie : f.getWifeRefs() )
			member(refMoglie, Relation.PARTNER);
		for( ChildRef refFiglio : f.getChildRefs() )
			member(refFiglio, Relation.CHILD);
		for( EventFact ef : f.getEventsFacts() ) {
			place(writeEventTitle(f, ef), ef);
		}
		placeExtensions(f);
		U.placeNotes(box, f, true);
		U.placeMedia(box, f, true);
		U.placeSourceCitations(box, f);
		U.placeChangeDate(box, f.getChange());
	}

	// Add a member to the family
	void member(SpouseRef sr, Relation relation) {
		Person p = sr.getPerson(gc);
		if( p == null ) return;
		View vistaPersona = U.mettiIndividuo(box, p, getRole(p, f, relation, true));
		vistaPersona.setTag(R.id.tag_oggetto, p); // per il menu contestuale in Dettaglio
		/*  Ref nell'individuo verso la famiglia
			Se la stessa persona è presente più volte con lo stesso ruolo (parent/child) nella stessa famiglia
			i 2 loop seguenti individuano nella person il *primo* FamilyRef (INDI.FAMS / INDI.FAMC) che rimanda a quella famiglia
			Non prendono quello con lo stesso indice del corrispondente Ref nella famiglia  (FAM.HUSB / FAM.WIFE)
			Poteva essere un problema in caso di 'Scollega', ma non più perché tutto il contenuto di Famiglia viene ricaricato
		 */
		if( relation == Relation.PARTNER ) {
			for( SpouseFamilyRef sfr : p.getSpouseFamilyRefs() )
				if( f.getId().equals(sfr.getRef()) ) {
					vistaPersona.setTag(R.id.tag_spouse_family_ref, sfr);
					break;
				}
		} else if( relation == Relation.CHILD ) {
			for( ParentFamilyRef pfr : p.getParentFamilyRefs() )
				if( f.getId().equals(pfr.getRef()) ) {
					vistaPersona.setTag(R.id.tag_spouse_family_ref, pfr);
					break;
				}
		}
		vistaPersona.setTag(R.id.tag_spouse_ref, sr);
		registerForContextMenu(vistaPersona);
		vistaPersona.setOnClickListener(v -> {
			List<Family> parentFam = p.getParentFamilies(gc);
			List<Family> spouseFam = p.getSpouseFamilies(gc);
			// un coniuge con una o più famiglie in cui è figlio
			if( relation == Relation.PARTNER && !parentFam.isEmpty() ) {
				U.qualiGenitoriMostrare(this, p, 2);
			} // un figlio con una o più famiglie in cui è coniuge
			else if( relation == Relation.CHILD && !p.getSpouseFamilies(gc).isEmpty() ) {
				U.qualiConiugiMostrare(this, p, null);
			} // un figlio non sposato che ha più famiglie genitoriali
			else if( parentFam.size() > 1 ) {
				if( parentFam.size() == 2 ) { // Swappa tra le 2 famiglie genitoriali
					Global.indi = p.getId();
					Global.familyNum = parentFam.indexOf(f) == 0 ? 1 : 0;
					Memoria.replacePrimo(parentFam.get(Global.familyNum));
					recreate();
				} else // Più di due famiglie
					U.qualiGenitoriMostrare( this, p, 2 );
			} // un coniuge senza genitori ma con più famiglie coniugali
			else if( spouseFam.size() > 1 ) {
				if( spouseFam.size() == 2 ) { // Swappa tra le 2 famiglie coniugali
					Global.indi = p.getId();
					Family altraFamiglia = spouseFam.get(spouseFam.indexOf(f) == 0 ? 1 : 0);
					Memoria.replacePrimo(altraFamiglia);
					recreate();
				} else
					U.qualiConiugiMostrare(this, p, null);
			} else {
				Memoria.setPrimo(p);
				startActivity(new Intent(this, Individuo.class));
			}
		});
		if( unRappresentanteDellaFamiglia == null )
			unRappresentanteDellaFamiglia = p;
	}

	/** Find the role of a person from their relation with a family
	 * @param person
	 * @param family Can be null
	 * @param relation
	 * @param respectFamily The role to find is relative to the family (it becomes 'parent' with children)
	 * @return A descriptor text of the person's role
	 */
	public static String getRole(Person person, Family family, Relation relation, boolean respectFamily) {
		int role = 0;
		if( respectFamily && relation == Relation.PARTNER && family != null && !family.getChildRefs().isEmpty() )
			relation = Relation.PARENT;
		Status status = Status.getStatus(family);
		if( Gender.isMale(person) ) {
			switch( relation ) {
				case PARENT: role = R.string.father; break;
				case SIBLING: role = R.string.brother; break;
				case HALF_SIBLING: role = R.string.half_brother; break;
				case PARTNER:
					switch( status ) {
						case MARRIED: role = R.string.husband; break;
						case DIVORCED: role = R.string.ex_husband; break;
						case SEPARATED: role = R.string.ex_male_partner; break;
						default: role = R.string.male_partner;
					}
					break;
				case CHILD: role = R.string.son;
			}
		} else if( Gender.isFemale(person) ) {
			switch( relation ) {
				case PARENT: role = R.string.mother; break;
				case SIBLING: role = R.string.sister; break;
				case HALF_SIBLING: role = R.string.half_sister; break;
				case PARTNER:
					switch( status ) {
						case MARRIED: role = R.string.wife; break;
						case DIVORCED: role = R.string.ex_wife; break;
						case SEPARATED: role = R.string.ex_female_partner; break;
						default: role = R.string.female_partner;
					}
					break;
				case CHILD: role = R.string.daughter;
			}
		} else {
			switch( relation ) {
				case PARENT: role = R.string.parent; break;
				case SIBLING: role = R.string.sibling; break;
				case HALF_SIBLING: role = R.string.half_sibling; break;
				case PARTNER:
					switch( status ) {
						case MARRIED: role = R.string.spouse; break;
						case DIVORCED: role = R.string.ex_spouse; break;
						case SEPARATED: role = R.string.ex_partner; break;
						default: role = R.string.partner;
					}
					break;
				case CHILD: role = R.string.child;
			}
		}
		return Global.context.getString(role);
	}

	// Collega una persona ad una famiglia come genitore o figlio
	public static void aggrega( Person person, Family fam, int ruolo ) {
		switch( ruolo ) {
			case 5:	// Genitore
				// il ref dell'indi nella famiglia
				SpouseRef sr = new SpouseRef();
				sr.setRef(person.getId());
				EditaIndividuo.aggiungiConiuge(fam, sr);

				// il ref della famiglia nell'indi
				SpouseFamilyRef sfr = new SpouseFamilyRef();
				sfr.setRef( fam.getId() );
				//tizio.getSpouseFamilyRefs().add( sfr );	// no: con lista vuota UnsupportedOperationException
				//List<SpouseFamilyRef> listaSfr = tizio.getSpouseFamilyRefs();	// Non va bene:
				// quando la lista è inesistente, anzichè restituire una ArrayList restituisce una Collections$EmptyList che è IMMUTABILE cioè non ammette add()
				List<SpouseFamilyRef> listaSfr = new ArrayList<>( person.getSpouseFamilyRefs() );	// ok
				listaSfr.add( sfr );	// ok
				person.setSpouseFamilyRefs( listaSfr );
				break;
			case 6:	// Figlio
				ChildRef cr = new ChildRef();
				cr.setRef( person.getId() );
				fam.addChild( cr );
				ParentFamilyRef pfr = new ParentFamilyRef();
				pfr.setRef( fam.getId() );
				//tizio.getParentFamilyRefs().add( pfr );	// UnsupportedOperationException
				List<ParentFamilyRef> listaPfr = new ArrayList<>( person.getParentFamilyRefs() );
				listaPfr.add( pfr );
				person.setParentFamilyRefs( listaPfr );
		}
	}

	// Rimuove il singolo SpouseFamilyRef dall'individuo e il corrispondente SpouseRef dalla famiglia
	public static void scollega(SpouseFamilyRef sfr, SpouseRef sr) {
		// Dalla persona alla famiglia
		Person person = sr.getPerson(gc);
		person.getSpouseFamilyRefs().remove(sfr);
		if( person.getSpouseFamilyRefs().isEmpty() )
			person.setSpouseFamilyRefs(null); // Eventuale lista vuota viene eliminata
		person.getParentFamilyRefs().remove(sfr);
		if( person.getParentFamilyRefs().isEmpty() )
			person.setParentFamilyRefs(null);
		// Dalla famiglia alla persona
		Family fam = sfr.getFamily(gc);
		fam.getHusbandRefs().remove(sr);
		if( fam.getHusbandRefs().isEmpty() )
			fam.setHusbandRefs(null);
		fam.getWifeRefs().remove(sr);
		if( fam.getWifeRefs().isEmpty() )
			fam.setWifeRefs(null);
		fam.getChildRefs().remove(sr);
		if( fam.getChildRefs().isEmpty() )
			fam.setChildRefs(null);
	}

	// Rimuove TUTTI i ref di un individuo in una famiglia
	public static void scollega(String indiId, Family family) {
		// Rimuove i ref dell'indi nella famiglia
		Iterator<SpouseRef> spouseRefs = family.getHusbandRefs().iterator();
		while( spouseRefs.hasNext() )
			if( spouseRefs.next().getRef().equals(indiId) )
				spouseRefs.remove();
		if( family.getHusbandRefs().isEmpty() )
			family.setHusbandRefs(null); // Elimina eventuale lista vuota

		spouseRefs = family.getWifeRefs().iterator();
		while( spouseRefs.hasNext() )
			if( spouseRefs.next().getRef().equals(indiId) )
				spouseRefs.remove();
		if( family.getWifeRefs().isEmpty() )
			family.setWifeRefs(null);

		Iterator<ChildRef> childRefs = family.getChildRefs().iterator();
		while( childRefs.hasNext() )
			if( childRefs.next().getRef().equals(indiId) )
				childRefs.remove();
		if( family.getChildRefs().isEmpty() )
			family.setChildRefs(null);

		// Rimuove i ref della famiglia nell'indi
		Person person = gc.getPerson(indiId);
		Iterator<SpouseFamilyRef> iterSfr = person.getSpouseFamilyRefs().iterator();
		while( iterSfr.hasNext() )
			if( iterSfr.next().getRef().equals(family.getId()) )
				iterSfr.remove();
		if( person.getSpouseFamilyRefs().isEmpty() )
			person.setSpouseFamilyRefs(null);

		Iterator<ParentFamilyRef> iterPfr = person.getParentFamilyRefs().iterator();
		while( iterPfr.hasNext() )
			if( iterPfr.next().getRef().equals(family.getId()) )
				iterPfr.remove();
		if( person.getParentFamilyRefs().isEmpty() )
			person.setParentFamilyRefs(null);
	}
}