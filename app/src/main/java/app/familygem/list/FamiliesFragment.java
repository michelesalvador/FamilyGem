package app.familygem.list;

import static app.familygem.Global.gc;

import android.content.Intent;
import android.os.Bundle;
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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

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
import app.familygem.detail.FamilyActivity;

public class FamiliesFragment extends Fragment {

    private LinearLayout layout;
    private List<FamilyWrapper> familyList;
    private int order;
    private boolean idsAreNumeric;

    public enum What {
        RELOAD, UPDATE, BASIC
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        View view = inflater.inflate(R.layout.scrollview, container, false);
        layout = view.findViewById(R.id.scrollview_layout);
        if (gc != null) {
            familyList = new ArrayList<>();
            refresh(What.RELOAD);
            if (familyList.size() > 1)
                setHasOptionsMenu(true);
            idsAreNumeric = verifyIdsAreNumeric();
            view.findViewById(R.id.fab).setOnClickListener(v -> {
                Family newFamily = newFamily(true);
                U.save(true, newFamily);
                // If the user returns immediately back to this fragment, the new empty family is displayed in the list
                Memory.setFirst(newFamily);
                startActivity(new Intent(getContext(), FamilyActivity.class));
            });
        }
        return view;
    }

    void placeFamily(LinearLayout layout, FamilyWrapper wrapper) {
        View familyView = LayoutInflater.from(layout.getContext()).inflate(R.layout.families_item, layout, false);
        layout.addView(familyView);
        TextView infoView = familyView.findViewById(R.id.family_info);
        switch (order) {
            case 1:
            case 2:
                infoView.setText(wrapper.id);
                break;
            case 3:
            case 4:
                if (wrapper.originalSurname != null)
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
        for (Person husband : wrapper.family.getHusbands(gc))
            parents.append(U.properName(husband)).append("\n");
        for (Person wife : wrapper.family.getWives(gc))
            parents.append(U.properName(wife)).append("\n");
        if (parents.length() > 0)
            parents = new StringBuilder(parents.substring(0, parents.length() - 1)); // Just to remove the final '\n' TODO: does StringBuilder has .trim()?
        ((TextView)familyView.findViewById(R.id.family_parents)).setText(parents.toString());
        StringBuilder children = new StringBuilder();
        for (Person child : wrapper.family.getChildren(gc))
            children.append(U.properName(child)).append("\n");
        if (children.length() > 0)
            children = new StringBuilder(children.substring(0, children.length() - 1));
        TextView childrenView = familyView.findViewById(R.id.family_children);
        if (children.length() == 0) {
            familyView.findViewById(R.id.family_strut).setVisibility(View.GONE);
            childrenView.setVisibility(View.GONE);
        } else
            childrenView.setText(children.toString());
        registerForContextMenu(familyView);
        familyView.setOnClickListener(v -> {
            Memory.setFirst(wrapper.family);
            layout.getContext().startActivity(new Intent(layout.getContext(), FamilyActivity.class));
        });
        familyView.setTag(wrapper.id); // For 'Delete' item in the context menu here in FamiliesFragment
    }

    /**
     * Delete a family, removing the Refs from family members.
     */
    public static void deleteFamily(Family family) {
        if (family == null) return;
        Set<Person> members = new HashSet<>();
        // Removes references from family members towards the family
        for (Person husband : family.getHusbands(gc)) {
            Iterator<SpouseFamilyRef> refs = husband.getSpouseFamilyRefs().iterator();
            while (refs.hasNext()) {
                SpouseFamilyRef sfr = refs.next();
                if (sfr.getRef().equals(family.getId())) {
                    refs.remove();
                    members.add(husband);
                }
            }
        }
        for (Person wife : family.getWives(gc)) {
            Iterator<SpouseFamilyRef> refs = wife.getSpouseFamilyRefs().iterator();
            while (refs.hasNext()) {
                SpouseFamilyRef sfr = refs.next();
                if (sfr.getRef().equals(family.getId())) {
                    refs.remove();
                    members.add(wife);
                }
            }
        }
        for (Person children : family.getChildren(gc)) {
            Iterator<ParentFamilyRef> refs = children.getParentFamilyRefs().iterator();
            while (refs.hasNext()) {
                ParentFamilyRef pfr = refs.next();
                if (pfr.getRef().equals(family.getId())) {
                    refs.remove();
                    members.add(children);
                }
            }
        }
        // The family is deleted
        gc.getFamilies().remove(family);
        gc.createIndexes(); // Necessary to update persons
        Memory.setInstanceAndAllSubsequentToNull(family);
        Global.familyNum = 0; // In the case that is deleted exactly the family referenced in Global.familyNum
        U.save(true, members.toArray(new Object[0]));
    }

    public static Family newFamily(boolean addToGedcom) {
        Family newFamily = new Family();
        newFamily.setId(U.newID(gc, Family.class));
        if (addToGedcom)
            gc.addFamily(newFamily);
        return newFamily;
    }

    private Family selected;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo info) {
        selected = gc.getFamily((String)view.getTag());
        if (Global.settings.expert)
            menu.add(0, 0, 0, R.string.edit_id);
        menu.add(0, 1, 0, R.string.delete);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == 0) { // Edit ID
            U.editId(getContext(), selected, () -> this.refresh(What.UPDATE));
        } else if (item.getItemId() == 1) { // Delete
            if (selected.getHusbandRefs().size() + selected.getWifeRefs().size() + selected.getChildRefs().size() > 0) {
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
     * Checks if all family IDs contain numbers.
     * As soon as an ID contains only letters it returns false.
     */
    boolean verifyIdsAreNumeric() {
        outer:
        for (Family f : gc.getFamilies()) {
            for (char character : f.getId().toCharArray()) {
                if (Character.isDigit(character))
                    continue outer;
            }
            return false;
        }
        return true;
    }

    void sortFamilies() {
        if (order > 0) { // 0 keeps actual sorting
            Collections.sort(familyList, (f1, f2) -> {
                switch (order) {
                    case 1: // Sorts by ID
                        if (idsAreNumeric)
                            return U.extractNum(f1.id) - U.extractNum(f2.id);
                        else
                            return f1.id.compareToIgnoreCase(f2.id);
                    case 2:
                        if (idsAreNumeric)
                            return U.extractNum(f2.id) - U.extractNum(f1.id);
                        else
                            return f2.id.compareToIgnoreCase(f1.id);
                    case 3: // Sorts by surname
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
                    case 5: // Sorts by number of family members
                        return f1.members - f2.members;
                    case 6:
                        return f2.members - f1.members;
                }
                return 0;
            });
        }
    }

    public void refresh(What toDo) {
        if (toDo == What.RELOAD) { // Reloads all families from Global.gc
            familyList.clear();
            for (Family family : gc.getFamilies())
                familyList.add(new FamilyWrapper(family));
            ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(familyList.size() + " "
                    + getString(familyList.size() == 1 ? R.string.family : R.string.families).toLowerCase());
            sortFamilies();
        } else if (toDo == What.UPDATE) { // Updates the content of existing family wrappers
            for (FamilyWrapper wrapper : familyList)
                wrapper.id = wrapper.family.getId();
        }
        layout.removeAllViews();
        for (FamilyWrapper wrapper : familyList)
            placeFamily(layout, wrapper);
    }

    private class FamilyWrapper {
        Family family;
        String id;
        String lowerSurname; // Surname lowercase for comparison
        String originalSurname;
        int members;

        public FamilyWrapper(Family family) {
            this.family = family;
            id = family.getId();
            lowerSurname = familySurname(true);
            originalSurname = familySurname(false);
            members = countMembers();
        }

        /**
         * Finds the main surname of the family.
         */
        private String familySurname(boolean lowerCase) {
            if (!family.getHusbands(gc).isEmpty())
                return U.surname(family.getHusbands(gc).get(0), lowerCase);
            if (!family.getWives(gc).isEmpty())
                return U.surname(family.getWives(gc).get(0), lowerCase);
            if (!family.getChildren(gc).isEmpty())
                return U.surname(family.getChildren(gc).get(0), lowerCase);
            return null;
        }

        /**
         * Counts how many family members.
         */
        private int countMembers() {
            return family.getHusbandRefs().size() + family.getWifeRefs().size() + family.getChildRefs().size();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        SubMenu subMenu = menu.addSubMenu(R.string.order_by);
        if (Global.settings.expert)
            subMenu.add(0, 1, 0, R.string.id);
        subMenu.add(0, 2, 0, R.string.surname);
        subMenu.add(0, 3, 0, R.string.number_members);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id > 0 && id <= 3) {
            if (order == id * 2 - 1)
                order++;
            else if (order == id * 2)
                order--;
            else
                order = id * 2 - 1;
            sortFamilies();
            refresh(What.BASIC);
            //U.saveJson(false); // Saves the sorting of families
            return true;
        }
        return false;
    }
}
