package app.familygem;

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
import app.familygem.detail.FamilyActivity;
import static app.familygem.Global.gc;

public class ChurchFragment extends Fragment {

	private LinearLayout layout;
	private List<FamilyWrapper> familyList;
	private int order;
	private boolean idsAreNumeric;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
		View view = inflater.inflate(R.layout.magazzino, container, false);
		layout = view.findViewById(R.id.magazzino_scatola);
		if( gc != null ) {
			familyList = new ArrayList<>();
			refresh(What.RELOAD);
			if( familyList.size() > 1 )
				setHasOptionsMenu(true);
			idsAreNumeric = verifyIdsAreNumeric();
			view.findViewById(R.id.fab).setOnClickListener(v -> {
				Family newFamily = newFamily(true);
				U.save(true, newFamily);
				// If he(user?) goes straight back to the church he refreshes the list with the empty family //Se torna subito indietro in Chiesa rinfresca la lista con la famiglia vuota
				Memory.setFirst(newFamily);
				startActivity(new Intent(getContext(), FamilyActivity.class));
			});
		}
		return view;
	}

	void placeFamily(LinearLayout layout, FamilyWrapper wrapper) {
		View familyView = LayoutInflater.from(layout.getContext()).inflate(R.layout.pezzo_famiglia, layout, false);
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
		StringBuilder parents = new StringBuilder();
		for( Person husband : wrapper.family.getHusbands(gc) )
			parents.append(U.properName(husband)).append("\n");
		for( Person wife : wrapper.family.getWives(gc) )
			parents.append(U.properName(wife)).append("\n");
		if(parents.length() > 0)
			parents = new StringBuilder(parents.substring(0, parents.length() - 1));
		((TextView)familyView.findViewById(R.id.family_parents)).setText(parents.toString());
		StringBuilder children = new StringBuilder();
		for( Person child : wrapper.family.getChildren(gc) )
			children.append(U.properName(child)).append("\n");
		if(children.length() > 0)
			children = new StringBuilder(children.substring(0, children.length() - 1));
		TextView childrenView = familyView.findViewById(R.id.family_children);
		if(children.length() == 0) {
			familyView.findViewById(R.id.family_strut).setVisibility(View.GONE);
			childrenView.setVisibility(View.GONE);
		} else
			childrenView.setText(children.toString());
		registerForContextMenu(familyView);
		familyView.setOnClickListener(v -> {
			Memory.setFirst(wrapper.family);
			layout.getContext().startActivity(new Intent(layout.getContext(), FamilyActivity.class));
		});
		familyView.setTag(wrapper.id); // only for the context menu Delete here in the Church //solo per il menu contestuale Elimina qui in Chiesa
	}

	// Delete a family, removing the refs from members
	static void deleteFamily(Family family) {
		if( family == null ) return;
		Set<Person> members = new HashSet<>();
		// Remove references to the family from family members
		for( Person husband : family.getHusbands(gc) ) {
			Iterator<SpouseFamilyRef> refs = husband.getSpouseFamilyRefs().iterator();
			while( refs.hasNext() ) {
				SpouseFamilyRef sfr = refs.next();
				if( sfr.getRef().equals(family.getId()) ) {
					refs.remove();
					members.add( husband );
				}
			}
		}
		for( Person wife : family.getWives(gc) ) {
			Iterator<SpouseFamilyRef> refs = wife.getSpouseFamilyRefs().iterator();
			while( refs.hasNext() ) {
				SpouseFamilyRef sfr = refs.next();
				if( sfr.getRef().equals(family.getId()) ) {
					refs.remove();
					members.add( wife );
				}
			}
		}
		for( Person children : family.getChildren(gc) ) {
			Iterator<ParentFamilyRef> refi = children.getParentFamilyRefs().iterator();
			while( refi.hasNext() ) {
				ParentFamilyRef pfr = refi.next();
				if( pfr.getRef().equals(family.getId()) ) {
					refi.remove();
					members.add( children );
				}
			}
		}
		// The family is deleted
		gc.getFamilies().remove(family);
		gc.createIndexes();	// necessary to update individuals
		Memory.setInstanceAndAllSubsequentToNull(family);
		Global.familyNum = 0; // In the unlikely event that this family was eliminated //Nel caso fortuito che sia stata eliminata proprio questa famiglia
		U.save(true, members.toArray(new Object[0]));
	}

	static Family newFamily(boolean add) {
		Family newFamily = new Family();
		newFamily.setId(U.newID(gc, Family.class));
		if( add )
			gc.addFamily(newFamily);
		return newFamily;
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
		} else if( item.getItemId() == 1 ) { // Delete
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

	/**
	 * Check if all family ids contain numbers
	 * As soon as an id contains only letters it returns false
	 * */
	boolean verifyIdsAreNumeric() {
		outer:
		for( Family f : gc.getFamilies() ) {
			for( char c : f.getId().toCharArray() ) {
				if (Character.isDigit(c))
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
					case 1: // Sort by ID
						if( idsAreNumeric )
							return U.extractNum(f1.id) - U.extractNum(f2.id);
						else
							return f1.id.compareToIgnoreCase(f2.id);
					case 2:
						if( idsAreNumeric )
							return U.extractNum(f2.id) - U.extractNum(f1.id);
						else
							return f2.id.compareToIgnoreCase(f1.id);
					case 3: // Sort by surname
						if (f1.lowerSurname == null) // null names go to the bottom
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
					case 5:	// Sort by number of family members
						return f1.members - f2.members;
					case 6:
						return f2.members - f1.members;
				}
				return 0;
			});
		}
	}

	enum What {
		RELOAD, UPDATE, BASIC
	}
	void refresh(What toDo) {
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

		/**
		 * Main surname of the family
		 * */
		private String familySurname(boolean lowerCase) {
			if( !family.getHusbands(gc).isEmpty() )
				return U.surname(family.getHusbands(gc).get(0), lowerCase);
			if( !family.getWives(gc).isEmpty() )
				return U.surname(family.getWives(gc).get(0), lowerCase);
			if( !family.getChildren(gc).isEmpty() )
				return U.surname(family.getChildren(gc).get(0), lowerCase);
			return null;
		}

		/**
		 * Count how many family members
		 * */
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
			//U.salvaJson( false ); // doubt whether to put it to immediately save the reorganization of families //dubbio se metterlo per salvare subito il riordino delle famiglie
			return true;
		}
		return false;
	}
}