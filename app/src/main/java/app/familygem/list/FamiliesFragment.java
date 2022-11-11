package app.familygem.list;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.ParentFamilyRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SpouseFamilyRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import app.familygem.Global;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import app.familygem.detail.Famiglia;
import static app.familygem.Global.gc;

public class FamiliesFragment extends Fragment {

	private LinearLayout layout;
	private List<FamilyWrapper> familyList;
	private int order;
	private boolean idsAreNumeric;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
		View view = inflater.inflate(R.layout.scrollview, container, false);
		layout = view.findViewById(R.id.scrollview_layout);
		if( gc != null ) {
			familyList = new ArrayList<>();
			refresh(What.RELOAD);
			if( familyList.size() > 1 )
				setHasOptionsMenu(true);
			idsAreNumeric = checkIdsNumeric();
			view.findViewById(R.id.fab).setOnClickListener(v -> {
				Family newFamily = newFamily(true);
				U.save(true, newFamily);
				// Se torna subito indietro in Chiesa rinfresca la lista con la famiglia vuota
				Memory.setPrimo(newFamily);
				startActivity(new Intent(getContext(), Famiglia.class));
			});
		}
		return view;
	}

	void placeFamily(LinearLayout layout, FamilyWrapper wrapper) {
		View familyView = LayoutInflater.from(layout.getContext()).inflate(R.layout.families_item, layout, false);
		layout.addView(familyView);
		TextView infoView = familyView.findViewById(R.id.family_info);
		switch( order ) {
			case 1:
			case 2:
				infoView.setText(wrapper.id);
				break;
			case 3:
			case 4:
				if( wrapper.originalSurname != null )
					infoView.setText(wrapper.originalSurname);
				else
					infoView.setVisibility(View.GONE);
				break;
			case 5:
			case 6:
				infoView.setText(String.valueOf(wrapper.members));
				break;
			default:
				infoView.setVisibility(View.GONE);
		}
		String parents = "";
		for( Person husband : wrapper.family.getHusbands(gc) )
			parents += U.epiteto(husband) + "\n";
		for( Person wife : wrapper.family.getWives(gc) )
			parents += U.epiteto(wife) + "\n";
		if( !parents.isEmpty() )
			parents = parents.substring(0, parents.length() - 1);
		((TextView)familyView.findViewById(R.id.family_parents)).setText(parents);
		String children = "";
		for( Person child : wrapper.family.getChildren(gc) )
			children += U.epiteto(child) + "\n";
		if( !children.isEmpty() )
			children = children.substring(0, children.length() - 1);
		TextView childrenView = familyView.findViewById(R.id.family_children);
		if( children.isEmpty() ) {
			familyView.findViewById(R.id.family_strut).setVisibility(View.GONE);
			childrenView.setVisibility(View.GONE);
		} else
			childrenView.setText(children);
		registerForContextMenu(familyView);
		familyView.setOnClickListener(v -> {
			Memory.setPrimo(wrapper.family);
			layout.getContext().startActivity(new Intent(layout.getContext(), Famiglia.class));
		});
		familyView.setTag(wrapper.id); // solo per il menu contestuale Elimina qui in Chiesa
	}

	// Delete a family, removing the refs from members
	public static void deleteFamily(Family family) {
		if( family == null ) return;
		Set<Person> membri = new HashSet<>();
		// Remove references to the family from family members
		for( Person marito : family.getHusbands(gc) ) {
			Iterator<SpouseFamilyRef> refi = marito.getSpouseFamilyRefs().iterator();
			while( refi.hasNext() ) {
				SpouseFamilyRef sfr = refi.next();
				if( sfr.getRef().equals(family.getId()) ) {
					refi.remove();
					membri.add( marito );
				}
			}
		}
		for( Person moglie : family.getWives(gc) ) {
			Iterator<SpouseFamilyRef> refi = moglie.getSpouseFamilyRefs().iterator();
			while( refi.hasNext() ) {
				SpouseFamilyRef sfr = refi.next();
				if( sfr.getRef().equals(family.getId()) ) {
					refi.remove();
					membri.add( moglie );
				}
			}
		}
		for( Person figlio : family.getChildren(gc) ) {
			Iterator<ParentFamilyRef> refi = figlio.getParentFamilyRefs().iterator();
			while( refi.hasNext() ) {
				ParentFamilyRef pfr = refi.next();
				if( pfr.getRef().equals(family.getId()) ) {
					refi.remove();
					membri.add( figlio );
				}
			}
		}
		// The family is deleted
		gc.getFamilies().remove(family);
		gc.createIndexes();	// necessario per aggiornare gli individui
		Memory.annullaIstanze(family);
		Global.familyNum = 0; // Nel caso fortuito che sia stata eliminata proprio questa famiglia
		U.save(true, membri.toArray(new Object[0]));
	}

	public static Family newFamily(boolean addToGedcom) {
		Family family = new Family();
		family.setId(U.nuovoId(gc, Family.class));
		if( addToGedcom )
			gc.addFamily(family);
		return family;
	}

	private Family selected;
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo info) {
		selected = gc.getFamily((String)view.getTag());
		if( Global.settings.expert )
			menu.add(0, 0, 0, R.string.edit_id);
		menu.add(0, 1, 0, R.string.delete);
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		if( item.getItemId() == 0 ) { // Edit ID
			U.editId(getContext(), selected, () -> this.refresh(What.UPDATE));
		} else if( item.getItemId() == 1 ) { // Elimina
			if( selected.getHusbandRefs().size() + selected.getWifeRefs().size() + selected.getChildRefs().size() > 0 ) {
				new AlertDialog.Builder(getContext()).setMessage(R.string.really_delete_family)
						.setPositiveButton(android.R.string.yes, (dialog, i) -> {
							deleteFamily(selected);
							refresh(What.RELOAD);
						}).setNeutralButton(android.R.string.cancel, null).show();
			} else {
				deleteFamily(selected);
				refresh(What.RELOAD);
			}
		} else {
			return false;
		}
		return true;
	}

	// Check if all family ids contain only numbers
	// As soon as an id contains only letters it returns false
	boolean checkIdsNumeric() {
		outer:
		for( Family family : gc.getFamilies() ) {
			for( char character : family.getId().toCharArray() ) {
				if( Character.isDigit(character) )
					continue outer;
			}
			return false;
		}
		return true;
	}

	void sortFamilies() {
		if( order > 0 ) {  // 0 keeps actual sorting
			Collections.sort(familyList, (f1, f2 ) -> {
				switch( order ) {
					case 1: // Ordina per ID
						if( idsAreNumeric )
							return U.getNumberOnly(f1.id) - U.getNumberOnly(f2.id);
						else
							return f1.id.compareToIgnoreCase(f2.id);
					case 2:
						if( idsAreNumeric )
							return U.getNumberOnly(f2.id) - U.getNumberOnly(f1.id);
						else
							return f2.id.compareToIgnoreCase(f1.id);
					case 3: // Ordina per cognome
						if (f1.lowerSurname == null) // i nomi null vanno in fondo
							return f2.lowerSurname == null ? 0 : 1;
						if (f2.lowerSurname == null)
							return -1;
						return f1.lowerSurname.compareTo(f2.lowerSurname);
					case 4:
						if (f1.lowerSurname == null)
							return f2.lowerSurname == null ? 0 : 1;
						if (f2.lowerSurname == null)
							return -1;
						return f2.lowerSurname.compareTo(f1.lowerSurname);
					case 5:	// Ordina per numero di familiari
						return f1.members - f2.members;
					case 6:
						return f2.members - f1.members;
				}
				return 0;
			});
		}
	}

	public enum What {
		RELOAD, UPDATE, BASIC
	}
	public void refresh(What toDo) {
		if( toDo == What.RELOAD ) { // Reload all families from Global.gc
			familyList.clear();
			for( Family family : gc.getFamilies() )
				familyList.add(new FamilyWrapper(family));
			((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(familyList.size() + " "
					+ getString(familyList.size() == 1 ? R.string.family : R.string.families).toLowerCase());
			sortFamilies();
		} else if( toDo == What.UPDATE) { // Update the content of existing family wrappers
			for( FamilyWrapper wrapper : familyList )
				wrapper.id = wrapper.family.getId();
		}
		layout.removeAllViews();
		for( FamilyWrapper wrapper : familyList )
			placeFamily(layout, wrapper);
	}

	private class FamilyWrapper {
		Family family;
		String id;
		String lowerSurname;
		String originalSurname;
		int members;

		public FamilyWrapper(Family family) {
			this.family=family;
			id = family.getId();
			lowerSurname = familySurname(true);
			originalSurname = familySurname(false);
			members = countMembers();
		}

		// Main surname of the family
		private String familySurname(boolean lowerCase) {
			if( !family.getHusbands(gc).isEmpty() )
				return U.surname(family.getHusbands(gc).get(0), lowerCase);
			if( !family.getWives(gc).isEmpty() )
				return U.surname(family.getWives(gc).get(0), lowerCase);
			if( !family.getChildren(gc).isEmpty() )
				return U.surname(family.getChildren(gc).get(0), lowerCase);
			return null;
		}

		// Count how many family members
		private int countMembers() {
			return family.getHusbandRefs().size() + family.getWifeRefs().size() + family.getChildRefs().size();
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		SubMenu subMenu = menu.addSubMenu(R.string.order_by);
		if( Global.settings.expert )
			subMenu.add(0, 1, 0, R.string.id);
		subMenu.add(0, 2, 0, R.string.surname);
		subMenu.add(0, 3, 0, R.string.number_members);
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if( id > 0 && id <= 3 ) {
			if( order == id * 2 - 1 )
				order++;
			else if( order == id * 2 )
				order--;
			else
				order = id * 2 - 1;
			sortFamilies();
			refresh(What.BASIC);
			//U.salvaJson( false ); // dubbio se metterlo per salvare subito il riordino delle famiglie
			return true;
		}
		return false;
	}
}