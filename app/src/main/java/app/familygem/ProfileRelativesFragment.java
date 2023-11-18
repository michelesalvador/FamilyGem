package app.familygem;

import static app.familygem.Global.gc;

import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SpouseFamilyRef;

import java.util.Collections;
import java.util.List;

import app.familygem.constant.Extra;
import app.familygem.constant.Relation;
import app.familygem.detail.FamilyActivity;
import app.familygem.list.PersonsFragment;
import app.familygem.util.TreeUtils;

public class ProfileRelativesFragment extends Fragment {

    private View relativesView;
    private Person one;
    private FragmentActivity activity;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        relativesView = inflater.inflate(R.layout.profile_page_fragment, container, false);
        if (gc != null) {
            one = gc.getPerson(Global.indi);
            if (one != null) {
                // Parents and siblings
                List<Family> families = one.getParentFamilies(gc);
                for (Family family : families) {
                    for (Person father : family.getHusbands(gc))
                        createCard(father, Relation.PARENT, family);
                    for (Person mother : family.getWives(gc))
                        createCard(mother, Relation.PARENT, family);
                    for (Person sibling : family.getChildren(gc)) // Only children of the same two parents, not half-siblings
                        if (!sibling.equals(one))
                            createCard(sibling, Relation.SIBLING, family);
                }
                // Half-siblings
                for (Family family : one.getParentFamilies(gc)) {
                    for (Person father : family.getHusbands(gc)) {
                        List<Family> fatherFamilies = father.getSpouseFamilies(gc);
                        fatherFamilies.removeAll(families);
                        for (Family fam : fatherFamilies)
                            for (Person halfSibling : fam.getChildren(gc))
                                createCard(halfSibling, Relation.HALF_SIBLING, fam);
                    }
                    for (Person mother : family.getWives(gc)) {
                        List<Family> motherFamilies = mother.getSpouseFamilies(gc);
                        motherFamilies.removeAll(families);
                        for (Family fam : motherFamilies)
                            for (Person halfSibling : fam.getChildren(gc))
                                createCard(halfSibling, Relation.HALF_SIBLING, fam);
                    }
                }
                // Partners and children
                for (Family family : one.getSpouseFamilies(gc)) {
                    for (Person husband : family.getHusbands(gc))
                        if (!husband.equals(one))
                            createCard(husband, Relation.PARTNER, family);
                    for (Person wife : family.getWives(gc))
                        if (!wife.equals(one))
                            createCard(wife, Relation.PARTNER, family);
                    for (Person child : family.getChildren(gc)) {
                        createCard(child, Relation.CHILD, family);
                    }
                }
            }
        }
        activity = requireActivity();
        return relativesView;
    }

    void createCard(final Person person, Relation relation, Family family) {
        LinearLayout layout = relativesView.findViewById(R.id.profile_page);
        View personView = U.placeIndividual(layout, person,
                FamilyActivity.getRole(person, family, relation, false) + FamilyActivity.writeLineage(person, family));
        personView.setOnClickListener(view -> {
            getActivity().finish(); // Removes the current activity from the stack
            Memory.replaceLeader(person);
            Intent intent = new Intent(getContext(), ProfileActivity.class);
            intent.putExtra(Extra.PAGE, 2); // Opens the Relatives page
            startActivity(intent);
        });
        registerForContextMenu(personView);
        personView.setTag(R.id.tag_family, family); // To pass the family to the context menu
    }

    private void moveFamilyRef(int direction) {
        Collections.swap(one.getSpouseFamilyRefs(), familyPosition, familyPosition + direction);
        TreeUtils.INSTANCE.save(true, one);
        refreshAll();
    }

    // Context menu
    private String personId;
    private Person person;
    private Family family;
    private int familyPosition; // Position of the spouse family (for those who have more than one)

    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, View view, ContextMenu.ContextMenuInfo info) {
        personId = (String)view.getTag();
        person = gc.getPerson(personId);
        family = (Family)view.getTag(R.id.tag_family);
        familyPosition = -1;
        if (one.getSpouseFamilyRefs().size() > 1 && !family.getChildren(gc).contains(person)) {
            List<SpouseFamilyRef> familyRefs = one.getSpouseFamilyRefs();
            for (SpouseFamilyRef ref : familyRefs)
                if (ref.getRef().equals(family.getId()))
                    familyPosition = familyRefs.indexOf(ref);
        }
        // Better to use numbers that do not conflict with the context menus of other profile pages
        menu.add(0, 300, 0, R.string.diagram);
        String[] familyLabels = DiagramFragment.getFamilyLabels(getContext(), person, family);
        if (familyLabels[0] != null)
            menu.add(0, 301, 0, familyLabels[0]);
        if (familyLabels[1] != null)
            menu.add(0, 302, 0, familyLabels[1]);
        if (familyPosition > 0)
            menu.add(0, 303, 0, R.string.move_before);
        if (familyPosition >= 0 && familyPosition < one.getSpouseFamilyRefs().size() - 1)
            menu.add(0, 304, 0, R.string.move_after);
        menu.add(0, 305, 0, R.string.modify);
        if (FamilyActivity.findParentFamilyRef(person, family) != null)
            menu.add(0, 306, 0, R.string.lineage);
        menu.add(0, 307, 0, R.string.unlink);
        if (!person.equals(one)) // Here cannot delete himself
            menu.add(0, 308, 0, R.string.delete);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == 300) { // Diagram
            U.askWhichParentsToShow(getContext(), person, 1);
        } else if (id == 301) { // Family as child
            U.askWhichParentsToShow(getContext(), person, 2);
        } else if (id == 302) { // Family as partner
            U.askWhichSpouceToShow(getContext(), person, family);
        } else if (id == 303) { // Move up
            moveFamilyRef(-1);
        } else if (id == 304) { // Move down
            moveFamilyRef(1);
        } else if (id == 305) { // Edit
            Intent intent = new Intent(getContext(), PersonEditorActivity.class);
            intent.putExtra(Extra.PERSON_ID, personId);
            startActivity(intent);
        } else if (id == 306) { // Lineage
            FamilyActivity.chooseLineage(getContext(), person, family);
        } else if (id == 307) { // Unlink
            FamilyActivity.disconnect(personId, family);
            TreeUtils.INSTANCE.save(true, family, person);
            refreshAll();
            U.controllaFamiglieVuote(getContext(), this::refreshOptionsMenu, false, family);
        } else if (id == 308) { // Delete
            new AlertDialog.Builder(getContext()).setMessage(R.string.really_delete_person)
                    .setPositiveButton(R.string.delete, (dialog, i) -> {
                        PersonsFragment.deletePerson(getContext(), personId);
                        refreshAll();
                        U.controllaFamiglieVuote(getContext(), this::refreshOptionsMenu, false, family);
                    }).setNeutralButton(R.string.cancel, null).show();
        } else {
            return false;
        }
        return true;
    }

    /**
     * Refreshes all the content.
     */
    public void refreshAll() {
        ((ProfileActivity)requireActivity()).refresh();
    }

    /**
     * Refreshes options menu only.
     */
    public void refreshOptionsMenu() {
        activity.invalidateOptionsMenu(); // Can't call requireActivity() here because the fragment is already detached
    }
}
