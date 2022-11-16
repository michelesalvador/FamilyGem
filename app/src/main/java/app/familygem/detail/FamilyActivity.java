package app.familygem.detail;

import android.content.Context;
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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import app.familygem.DetailActivity;
import app.familygem.IndividualEditorActivity;
import app.familygem.Global;
import app.familygem.IndividualPersonActivity;
import app.familygem.IndividualFamilyFragment;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import app.familygem.constant.Gender;
import app.familygem.constant.Relation;
import app.familygem.constant.Status;
import static app.familygem.Global.gc;
import androidx.appcompat.app.AlertDialog;

public class FamilyActivity extends DetailActivity {

	Family f;
	static String[] pediTexts = {U.s(R.string.undefined) + " (" + U.s(R.string.birth).toLowerCase() + ")",
			U.s(R.string.birth), U.s(R.string.adopted), U.s(R.string.foster)};
	static String[] pediTypes = {null, "birth", "adopted", "foster"};

	@Override
	public void format() {
		setTitle(R.string.family);
		f = (Family)cast(Family.class);
		placeSlug("FAM", f.getId());
		for( SpouseRef husbandRef : f.getHusbandRefs() )
			addMember(husbandRef, Relation.PARTNER);
		for( SpouseRef wifeRef : f.getWifeRefs() )
			addMember(wifeRef, Relation.PARTNER);
		for( ChildRef childRef : f.getChildRefs() )
			addMember(childRef, Relation.CHILD);
		for( EventFact ef : f.getEventsFacts() ) {
			place(writeEventTitle(f, ef), ef);
		}
		placeExtensions(f);
		U.placeNotes(box, f, true);
		U.placeMedia(box, f, true);
		U.placeSourceCitations(box, f);
		U.placeChangeDate(box, f.getChange());
	}

	/**
	 * Add a member to the family
	 * */
	void addMember(SpouseRef sr, Relation relation) {
		Person p = sr.getPerson(gc);
		if( p == null ) return;
		View personView = U.placeIndividual(box, p, getRole(p, f, relation, true) + writeLineage(p, f));
		personView.setTag(R.id.tag_object, p); // for the context menu in DetailActivity
		/*  Ref in the individual towards the family //Ref nell'individuo verso la famiglia
			If the same person is present several times with the same role (parent / child) in the same family // Se la stessa persona è presente più volte con lo stesso ruolo (parent/child) nella stessa famiglia
			the 2 following loops identify in the person the * first * FamilyRef (INDI.FAMS / INDI.FAMC) that refers to that family // i 2 loop seguenti individuano nella person il *primo* FamilyRef (INDI.FAMS / INDI.FAMC) che rimanda a quella famiglia
			They do not take the one with the same index as the corresponding Ref in the family (FAM.HUSB / FAM.WIFE) //Non prendono quello con lo stesso indice del corrispondente Ref nella famiglia  (FAM.HUSB / FAM.WIFE)
			It could be a problem in case of 'Unlink', but not anymore because all the Family content is reloaded //Poteva essere un problema in caso di 'Scollega', ma non più perché tutto il contenuto di Famiglia viene ricaricato
		 */
		if( relation == Relation.PARTNER ) {
			for( SpouseFamilyRef sfr : p.getSpouseFamilyRefs() )
				if( f.getId().equals(sfr.getRef()) ) {
					personView.setTag(R.id.tag_spouse_family_ref, sfr);
					break;
				}
		} else if( relation == Relation.CHILD ) {
			for( ParentFamilyRef pfr : p.getParentFamilyRefs() )
				if( f.getId().equals(pfr.getRef()) ) {
					personView.setTag(R.id.tag_spouse_family_ref, pfr);
					break;
				}
		}
		personView.setTag(R.id.tag_spouse_ref, sr);
		registerForContextMenu(personView);
		personView.setOnClickListener(v -> {
			List<Family> parentFam = p.getParentFamilies(gc);
			List<Family> spouseFam = p.getSpouseFamilies(gc);
			// a spouse with one or more families in which he is a child
			if( relation == Relation.PARTNER && !parentFam.isEmpty() ) {
				U.askWhichParentsToShow(this, p, 2);
			} // a child with one or more families in which he is a spouse
			else if( relation == Relation.CHILD && !p.getSpouseFamilies(gc).isEmpty() ) {
				U.askWhichSpouseToShow(this, p, null);
			} // an unmarried child who has multiple parental families
			else if( parentFam.size() > 1 ) {
				if( parentFam.size() == 2 ) { // Swap between the 2 parental families //Swappa tra le 2 famiglie genitoriali
					Global.indi = p.getId();
					Global.familyNum = parentFam.indexOf(f) == 0 ? 1 : 0;
					Memory.replaceFirst(parentFam.get(Global.familyNum));
					recreate();
				} else //More than two families
					U.askWhichParentsToShow( this, p, 2 );
			} // a spouse without parents but with multiple marital families //un coniuge senza genitori ma con più famiglie coniugali
			else if( spouseFam.size() > 1 ) {
				if( spouseFam.size() == 2 ) { // Swap between the 2 marital families //Swappa tra le 2 famiglie coniugali
					Global.indi = p.getId();
					Family otherFamily = spouseFam.get(spouseFam.indexOf(f) == 0 ? 1 : 0);
					Memory.replaceFirst(otherFamily);
					recreate();
				} else
					U.askWhichSpouseToShow(this, p, null);
			} else {
				Memory.setFirst(p);
				startActivity(new Intent(this, IndividualPersonActivity.class));
			}
		});
		if( aRepresentativeOfTheFamily == null )
			aRepresentativeOfTheFamily = p;
	}

	/** Find the role of a person from their relation with a family
	 * @param family Can be null
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

	/**
	 * Find the ParentFamilyRef of a child person in a family
	 * */
	public static ParentFamilyRef findParentFamilyRef(Person person, Family family) {
		for( ParentFamilyRef parentFamilyRef : person.getParentFamilyRefs() ) {
			if( parentFamilyRef.getRef().equals(family.getId()) ) {
				return parentFamilyRef;
			}
		}
		return null;
	}

	/**
	 * Compose the lineage definition to be added to the role
	 * */
	public static String writeLineage(Person person, Family family) {
		ParentFamilyRef parentFamilyRef = findParentFamilyRef(person, family);
		if( parentFamilyRef != null ) {
			int actual = Arrays.asList(pediTypes).indexOf(parentFamilyRef.getRelationshipType());
			if( actual > 0 )
				return " – " + pediTexts[actual];
		}
		return "";
	}

	/**
	 * Display the alert dialog to choose the lineage of one person
	 * */
	public static void chooseLineage(Context context, Person person, Family family) {
		ParentFamilyRef parentFamilyRef = findParentFamilyRef(person, family);
		if( parentFamilyRef != null ) {
			int actual = Arrays.asList(pediTypes).indexOf(parentFamilyRef.getRelationshipType());
			new AlertDialog.Builder(context).setSingleChoiceItems(pediTexts, actual, (dialog, i) -> {
				parentFamilyRef.setRelationshipType(pediTypes[i]);
				dialog.dismiss();
				if( context instanceof IndividualPersonActivity)
					((IndividualFamilyFragment)((IndividualPersonActivity)context).getSupportFragmentManager()
							.findFragmentByTag("android:switcher:" + R.id.schede_persona + ":2")).refresh();
				else if( context instanceof FamilyActivity)
					((FamilyActivity)context).refresh();
				U.save(true, person);
			}).show();
		}
	}

	/**
	 * Connect a person to a family as a parent or child
	 * */
	public static void connect(Person person, Family fam, int roleFlag ) {
		switch( roleFlag ) {
			case 5:	// Parent //TODO code smell: use of magic number
				// the ref of the indi in the family //il ref dell'indi nella famiglia
				SpouseRef sr = new SpouseRef();
				sr.setRef(person.getId());
				IndividualEditorActivity.addSpouse(fam, sr);

				// the family ref in the indi //il ref della famiglia nell'indi
				SpouseFamilyRef sfr = new SpouseFamilyRef();
				sfr.setRef( fam.getId() );
				//tizio.getSpouseFamilyRefs().add( sfr );	// no: with empty list UnsupportedOperationException //no: con lista vuota UnsupportedOperationException
				//List<SpouseFamilyRef> listOfRefs = tizio.getSpouseFamilyRefs();	// That's no good://Non va bene:
				// when the list is non-existent, instead of returning an ArrayList it returns a Collections$EmptyList which is IMMUTABLE i.e. it does not allow add ()
				List<SpouseFamilyRef> listOfRefs = new ArrayList<>( person.getSpouseFamilyRefs() );	// ok
				listOfRefs.add( sfr );	// ok
				person.setSpouseFamilyRefs( listOfRefs );
				break;
			case 6:	// Child
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

	/**
	 * Removes the single SpouseFamilyRef from the individual and the corresponding SpouseRef from the family
	 * */
	public static void disconnect(SpouseFamilyRef sfr, SpouseRef sr) {
		// From person to family //Dalla persona alla famiglia
		Person person = sr.getPerson(gc);
		person.getSpouseFamilyRefs().remove(sfr);
		if( person.getSpouseFamilyRefs().isEmpty() )
			person.setSpouseFamilyRefs(null); // Any empty list is deleted //Eventuale lista vuota viene eliminata
		person.getParentFamilyRefs().remove(sfr);
		if( person.getParentFamilyRefs().isEmpty() )
			person.setParentFamilyRefs(null);
		// From family to person //Dalla famiglia alla persona
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

	/**
	 * Removes ALL refs from an individual in a family
	 * */
	public static void disconnect(String indiId, Family family) {
		// Removes the refs of the indi in the family //Rimuove i ref dell'indi nella famiglia
		Iterator<SpouseRef> spouseRefs = family.getHusbandRefs().iterator();
		while( spouseRefs.hasNext() )
			if( spouseRefs.next().getRef().equals(indiId) )
				spouseRefs.remove();
		if( family.getHusbandRefs().isEmpty() )
			family.setHusbandRefs(null); // Delete any empty list //Elimina eventuale lista vuota

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

		// Removes family refs in the indi //Rimuove i ref della famiglia nell'indi
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