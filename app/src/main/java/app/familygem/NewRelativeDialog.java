package app.familygem;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.Spinner;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Person;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import app.familygem.constant.Choice;
import app.familygem.constant.Extra;
import app.familygem.constant.Relation;
import app.familygem.main.DiagramFragment;
import app.familygem.main.MainActivity;
import app.familygem.profile.ProfileActivity;
import app.familygem.util.FamilyUtil;

/**
 * Dialog to connect a relative (parent, sibling, partner or child) to a person in expert mode,
 * with dropdown menu to choose in which family to add the relative.
 */
public class NewRelativeDialog extends DialogFragment {

    private Person pivot; // Person we are starting from to create or link a relative
    private Family prefParentFamily; // Parent family to be selected in the spinner
    private Family prefSpouseFamily; // Spouse family to be selected in the spinner
    private boolean newPerson; // Link new person or link existing person
    private Fragment fragment;
    private AlertDialog dialog;
    private Spinner spinner;
    private final List<FamilyItem> options = new ArrayList<>();
    private Relation relation;

    public NewRelativeDialog(Person pivot, Family prefParentFamily, Family prefSpouseFamily, boolean newPerson, Fragment fragment) {
        this.pivot = pivot;
        this.prefParentFamily = prefParentFamily;
        this.prefSpouseFamily = prefSpouseFamily;
        this.newPerson = newPerson;
        this.fragment = fragment;
    }

    // Zero-argument constructor: necessary to re-instantiate this fragment (e.g. rotating the device screen)
    @Keep // Requests to don't remove when minify
    public NewRelativeDialog() {
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle bundle) {
        // Recreates the dialog
        if (bundle != null) {
            pivot = Global.gc.getPerson(bundle.getString("pivotId"));
            prefParentFamily = Global.gc.getFamily(bundle.getString("childFamilyId"));
            prefSpouseFamily = Global.gc.getFamily(bundle.getString("spouseFamilyId"));
            newPerson = bundle.getBoolean("newPerson");
            fragment = requireActivity().getSupportFragmentManager().getFragment(bundle, "fragment");
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        //builder.setTitle(newPerson ? R.string.new_relative : R.string.link_person);
        View view = requireActivity().getLayoutInflater().inflate(R.layout.new_relative_dialog, null);
        // Spinner to choose the destination family
        spinner = view.findViewById(R.id.newRelative_families);
        ArrayAdapter<FamilyItem> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        ((View)spinner.getParent()).setVisibility(View.GONE); // Initially the spinner is hidden

        RadioButton role1 = view.findViewById(R.id.newRelative_parent);
        role1.setOnCheckedChangeListener((r, selected) -> {
            if (selected) populateSpinner(Relation.PARENT);
        });
        RadioButton role2 = view.findViewById(R.id.newRelative_sibling);
        role2.setOnCheckedChangeListener((r, selected) -> {
            if (selected) populateSpinner(Relation.SIBLING);
        });
        RadioButton role3 = view.findViewById(R.id.newRelative_partner);
        role3.setOnCheckedChangeListener((r, selected) -> {
            if (selected) populateSpinner(Relation.PARTNER);
        });
        RadioButton role4 = view.findViewById(R.id.newRelative_child);
        role4.setOnCheckedChangeListener((r, selected) -> {
            if (selected) populateSpinner(Relation.CHILD);
        });

        builder.setView(view).setPositiveButton(android.R.string.ok, (dialog, id) -> {
            // Sets some extras that will be passed to PersonEditorActivity or to PersonsFragment and will arrive to addRelative()
            Intent intent = new Intent();
            intent.putExtra(Extra.PERSON_ID, pivot.getId());
            intent.putExtra(Extra.RELATION, relation);
            FamilyItem familyItem = (FamilyItem)spinner.getSelectedItem();
            if (familyItem.family != null)
                intent.putExtra(Extra.FAMILY_ID, familyItem.family.getId());
            else if (familyItem.parent != null) // We use DESTINATION to convey the parent's ID (the third actor in the scene)
                intent.putExtra(Extra.DESTINATION, "NEW_FAMILY_OF" + familyItem.parent.getId());
            else if (familyItem.existing) // Conveys to PersonsFragment the intention to join an existing family
                intent.putExtra(Extra.DESTINATION, "EXISTING_FAMILY");
            if (newPerson) { // Link new person
                intent.setClass(requireContext(), PersonEditorActivity.class);
                startActivity(intent);
            } else { // Link existing person
                intent.putExtra(Choice.PERSON, true);
                intent.setClass(requireContext(), MainActivity.class);
                if (fragment != null)
                    ((DiagramFragment)fragment).getChoosePersonLauncher().launch(intent);
                else
                    ((ProfileActivity)requireActivity()).choosePersonLauncher.launch(intent);
            }
        }).setNeutralButton(R.string.cancel, null);
        dialog = builder.create();
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false); // Initially disabled
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        bundle.putString("pivotId", pivot.getId());
        if (prefParentFamily != null)
            bundle.putString("childFamilyId", prefParentFamily.getId());
        if (prefSpouseFamily != null)
            bundle.putString("spouseFamilyId", prefSpouseFamily.getId());
        bundle.putBoolean("newPerson", newPerson);
        //Save the fragment's instance
        if (fragment != null)
            requireActivity().getSupportFragmentManager().putFragment(bundle, "fragment", fragment);
    }

    /**
     * Tells whether there is empty space in a family to add one or two parents.
     */
    private boolean checkSpouseRoom(Family family) {
        return family.getHusbandRefs().size() + family.getWifeRefs().size() < 2;
    }

    private void populateSpinner(Relation relation) {
        this.relation = relation;
        options.clear();
        int select = -1; // Index of the item to be selected in the spinner
        // If it remains -1, selects the first spinner entry
        switch (relation) {
            case PARENT:
                for (Family family : pivot.getParentFamilies(Global.gc)) {
                    if (checkSpouseRoom(family)) { // If there is empty parental space
                        addOption(new FamilyItem(getContext(), family));
                        if (family.equals(prefParentFamily) // Selects the preferred family in which is child
                                || select < 0) // Or the first available
                            select = options.size() - 1;
                    }
                }
                addOption(new FamilyItem(getContext(), false));
                if (select < 0) select = options.size() - 1; // Selects "New family"
                break;
            case SIBLING:
                for (Family family : pivot.getParentFamilies(Global.gc)) {
                    addOption(new FamilyItem(getContext(), family));
                    for (Person father : family.getHusbands(Global.gc)) {
                        for (Family fam : father.getSpouseFamilies(Global.gc))
                            if (!fam.equals(family))
                                addOption(new FamilyItem(getContext(), fam));
                        addOption(new FamilyItem(getContext(), father));
                    }
                    for (Person mother : family.getWives(Global.gc)) {
                        for (Family fam : mother.getSpouseFamilies(Global.gc))
                            if (!fam.equals(family))
                                addOption(new FamilyItem(getContext(), fam));
                        addOption(new FamilyItem(getContext(), mother));
                    }
                }
                addOption(new FamilyItem(getContext(), false));
                // Selects preferred family as child
                select = 0;
                for (FamilyItem item : options)
                    if (item.family != null && item.family.equals(prefParentFamily)) {
                        select = options.indexOf(item);
                        break;
                    }
                break;
            case PARTNER:
                for (Family family : pivot.getSpouseFamilies(Global.gc)) {
                    if (checkSpouseRoom(family)) {
                        addOption(new FamilyItem(getContext(), family));
                        if (select < 0) select = 0; // Selects the first family where spouses are missing
                    }
                }
                addOption(new FamilyItem(getContext(), pivot));
                if (select < 0) select = options.size() - 1; // Selects "New family of..."
                break;
            case CHILD:
                for (Family family : pivot.getSpouseFamilies(Global.gc)) {
                    addOption(new FamilyItem(getContext(), family));
                }
                addOption(new FamilyItem(getContext(), pivot));
                // Selects the preferred family (if any), otherwise the first one
                select = 0;
                for (FamilyItem item : options) {
                    if (item.family != null && item.family.equals(prefSpouseFamily)) {
                        select = options.indexOf(item);
                        break;
                    }
                }
        }
        if (!newPerson) {
            addOption(new FamilyItem(getContext(), true));
        }
        ArrayAdapter<FamilyItem> adapter = (ArrayAdapter)spinner.getAdapter();
        adapter.clear();
        adapter.addAll(options);
        ((View)spinner.getParent()).setVisibility(View.VISIBLE);
        spinner.setSelection(select);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
    }

    private void addOption(FamilyItem item) {
        if (!options.contains(item)) options.add(item);
    }

    /**
     * Container of a family entry for the list of "Inside" spinner.
     */
    static class FamilyItem {
        Context context;
        Family family;
        Person parent;
        boolean existing; // Pivot will try to join the already existing family

        /**
         * Existing family.
         */
        FamilyItem(Context context, Family family) {
            this.context = context;
            this.family = family;
        }

        /**
         * New family of a parent.
         */
        FamilyItem(Context context, Person parent) {
            this.context = context;
            this.parent = parent;
        }

        /**
         * New empty family (false) or family that will be acquired from a recipient (true).
         */
        FamilyItem(Context context, boolean existing) {
            this.context = context;
            this.existing = existing;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof FamilyItem) {
                FamilyItem that = (FamilyItem)obj;
                return existing == that.existing && Objects.equals(family, that.family) && Objects.equals(parent, that.parent);
            }
            return false;
        }

        @NonNull
        @Override
        public String toString() {
            if (family != null)
                return FamilyUtil.INSTANCE.writeMembers(context, Global.gc, family, false);
            else if (parent != null)
                return context.getString(R.string.new_family_of, U.properName(parent));
            else if (existing)
                return context.getString(R.string.existing_family);
            else
                return context.getString(R.string.new_family);
        }
    }
}
