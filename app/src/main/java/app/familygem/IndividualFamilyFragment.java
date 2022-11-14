package app.familygem;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SpouseFamilyRef;
import java.util.Collections;
import java.util.List;
import app.familygem.constant.Relation;
import app.familygem.detail.FamilyActivity;
import static app.familygem.Global.gc;

public class IndividualFamilyFragment extends Fragment {

	private View familyView;
	Person person1;

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState ) {
		familyView = inflater.inflate(R.layout.individuo_scheda, container, false);
		if( gc != null ) {
			person1 = gc.getPerson( Global.indi);
			if( person1 != null ) {
				/* TODO Show / be able to set the pedigree in the geniotrial families, in particular 'adopted' // Mostrare/poter settare nelle famiglie geniotriali il pedigree, in particolare 'adopted'
				LinearLayout container = vistaFamiglia.findViewById( R.id.contenuto_scheda );
				for( ParentFamilyRef pfr : person1.getParentFamilyRefs() ) {
					U.place( container, "Ref", pfr.getRef() );
					U.place( container, "Primary", pfr.getPrimary() ); // Custom tag _PRIM _PRIMARY
					U.place( container, "Relationship Type", pfr.getRelationshipType() ); // Tag PEDI (pedigree)
					for( Extension otherTag : U.findExtensions( pfr ) )
						U.place( container, otherTag.name, otherTag.text );
				} */
				// Families of origin: parents and siblings
				List<Family> listOfFamilies = person1.getParentFamilies(gc);
				for( Family family : listOfFamilies ) {
					for( Person father : family.getHusbands(gc) )
						createCard(father, Relation.PARENT, family);
					for( Person mother : family.getWives(gc) )
						createCard(mother, Relation.PARENT, family);
					for( Person sibling : family.getChildren(gc) ) // only children of the same two parents, not half-siblings
						if( !sibling.equals(person1) )
							createCard(sibling, Relation.SIBLING, family);
				}
				// Step (half?) brothers and sisters
				for( Family family : person1.getParentFamilies(gc) ) {
					for( Person husband : family.getHusbands(gc) ) {
						List<Family> fatherFamilies = husband.getSpouseFamilies(gc);
						fatherFamilies.removeAll(listOfFamilies);
						for( Family fam : fatherFamilies )
							for( Person stepSibling : fam.getChildren(gc) )
								createCard(stepSibling, Relation.HALF_SIBLING, fam);
					}
					for( Person wife : family.getWives(gc) ) {
						List<Family> wifeFamilies = wife.getSpouseFamilies(gc);
						wifeFamilies.removeAll(listOfFamilies);
						for( Family fam : wifeFamilies )
							for( Person stepSibling : fam.getChildren(gc) )
								createCard(stepSibling, Relation.HALF_SIBLING, fam);
					}
				}
				// Spouses and children
				for( Family family : person1.getSpouseFamilies(gc) ) {
					for( Person husband : family.getHusbands(gc) )
						if( !husband.equals(person1) )
							createCard(husband, Relation.PARTNER, family);
					for( Person wife : family.getWives(gc) )
						if( !wife.equals(person1) )
							createCard(wife, Relation.PARTNER, family);
					for( Person child : family.getChildren(gc) ) {
						createCard(child, Relation.CHILD, family);
					}
				}
			}
		}
		return familyView;
	}

	void createCard(final Person person, Relation relation, Family family) {
		LinearLayout container = familyView.findViewById(R.id.contenuto_scheda);
		View personView = U.placeIndividual(container, person,
				FamilyActivity.getRole(person, family, relation, false) + FamilyActivity.writeLineage(person, family));
		personView.setOnClickListener(v -> {
			getActivity().finish(); // Removes the current activity from the stack
			Memory.replaceFirst(person);
			Intent intent = new Intent(getContext(), IndividualPersonActivity.class);
			intent.putExtra("scheda", 2); // apre la scheda famiglia
			startActivity(intent);
		});
		registerForContextMenu(personView);

		// The main purpose of this tag is to be able to disconnect the individual from the family
		// but it is also used below to move multiple marriages:
		personView.setTag(R.id.tag_famiglia, family);
	}

	private void moveFamilyReference(int direction) {
		Collections.swap(person1.getSpouseFamilyRefs(), familyPos, familyPos + direction);
		U.save(true, person1);
		refresh();
	}

	// context Menu
	private String indiId;
	private Person person;
	private Family family;
	private int familyPos; // position of the marital family for those who have more than one
	@Override
	public void onCreateContextMenu(@NonNull ContextMenu menu, View view, ContextMenu.ContextMenuInfo info ) {
		indiId = (String)view.getTag();
		person = gc.getPerson(indiId);
		family = (Family)view.getTag(R.id.tag_famiglia);
		familyPos = -1;
		if( person1.getSpouseFamilyRefs().size() > 1 && !family.getChildren(gc).contains(person) ) { // only spouses, not children
			List<SpouseFamilyRef> refs = person1.getSpouseFamilyRefs();
			for( SpouseFamilyRef sfr : refs )
				if( sfr.getRef().equals(family.getId()) )
					familyPos = refs.indexOf(sfr);
		}
		// Better to use numbers that do not conflict with the context menus of the other individual tabs
		menu.add(0, 300, 0, R.string.diagram);
		String[] familyLabels = Diagram.getFamilyLabels(getContext(), person, family);
		if( familyLabels[0] != null )
			menu.add(0, 301, 0, familyLabels[0]);
		if( familyLabels[1] != null )
			menu.add(0, 302, 0, familyLabels[1]);
		if( familyPos > 0 )
			menu.add(0, 303, 0, R.string.move_before);
		if( familyPos >= 0 && familyPos < person1.getSpouseFamilyRefs().size() - 1 )
			menu.add(0, 304, 0, R.string.move_after);
		menu.add(0, 305, 0, R.string.modify);
		if( FamilyActivity.findParentFamilyRef(person, family) != null )
			menu.add(0, 306, 0, R.string.lineage);
		menu.add(0, 307, 0, R.string.unlink);
		if( !person.equals(person1) ) // Here he cannot eliminate himself
			menu.add(0, 308, 0, R.string.delete);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		int id = item.getItemId();
		if( id == 300 ) { // Diagram
			U.askWhichParentsToShow(getContext(), person, 1);
		} else if( id == 301 ) { // Family as a son
			U.askWhichParentsToShow(getContext(), person, 2);
		} else if( id == 302 ) { // Family as a spouse
			U.askWhichSpouceToShow(getContext(), person, family);
		} else if( id == 303 ) { // Move up
			moveFamilyReference(-1);
		} else if( id == 304 ) { // Move down
			moveFamilyReference(1);
		} else if( id == 305 ) { // Modify
			Intent intent = new Intent(getContext(), IndividualEditorActivity.class);
			intent.putExtra("idIndividuo", indiId);
			startActivity(intent);
		} else if( id == 306 ) { // Lineage
			FamilyActivity.chooseLineage(getContext(), person, family);
		} else if( id == 307 ) { // Disconnect from this family
			FamilyActivity.disconnect(indiId, family);
			refresh();
			U.controllaFamiglieVuote(getContext(), this::refresh, false, family);
			U.save(true, family, person);
		} else if( id == 308 ) { // Delete
			new AlertDialog.Builder(getContext()).setMessage(R.string.really_delete_person)
					.setPositiveButton(R.string.delete, (dialog, i) -> {
						ListOfPeopleFragment.deletePerson(getContext(), indiId);
						refresh();
						U.controllaFamiglieVuote(getContext(), this::refresh, false, family);
					}).setNeutralButton(R.string.cancel, null).show();
		} else {
			return false;
		}
		return true;
	}

	/**
	 * Refresh the contents of the Family Fragment
	 * */
	public void refresh() {
		FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
		fragmentManager.beginTransaction().detach(this).commit();
		fragmentManager.beginTransaction().attach(this).commit();
		requireActivity().invalidateOptionsMenu();
		// todo update the change date in the Facts tab
	}
}
