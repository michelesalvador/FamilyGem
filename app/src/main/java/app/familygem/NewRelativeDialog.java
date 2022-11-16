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

/**
 * DialogFragment which creates the dialog to connect a relative in expert mode
 * */
public class NewRelativeDialog extends DialogFragment {

	private Person pivot;
	private Family childFamPref; // Family as a child to possibly show first in the spinner
	private Family spouseFamPref; // Family as a spouse to possibly show first in the spinner
	private boolean newRelative;
	private Fragment fragment;
	private AlertDialog dialog;
	private Spinner spinner;
	private List<FamilyItem> items = new ArrayList<>();
	private int relationship;

	public NewRelativeDialog(Person pivot, Family favoriteChild, Family favoriteSpouse, boolean newRelative, Fragment fragment) {
		this.pivot = pivot;
		childFamPref = favoriteChild;
		spouseFamPref = favoriteSpouse;
		this.newRelative = newRelative;
		this.fragment = fragment;
	}

	// Zero-argument constructor: nececessary to re-instantiate this fragment (e.g. rotating the device screen)
	@Keep // Request to don't remove when minify
	public NewRelativeDialog() {}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle bundle) {
		// Recreate dialog
		if( bundle != null ) {
			pivot = Global.gc.getPerson(bundle.getString("idPerno"));
			childFamPref = Global.gc.getFamily(bundle.getString("idFamFiglio"));
			spouseFamPref = Global.gc.getFamily(bundle.getString("idFamSposo"));
			newRelative = bundle.getBoolean("nuovo");
			fragment = getActivity().getSupportFragmentManager().getFragment(bundle, "frammento");
		}
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		//builder.setTitle( newRelative ? R.string.new_relative : R.string.link_person );
		View vista = requireActivity().getLayoutInflater().inflate(R.layout.nuovo_parente, null);
		// Spinner to choose the family
		spinner = vista.findViewById(R.id.nuovoparente_famiglie);
		ArrayAdapter<FamilyItem> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		((View)spinner.getParent()).setVisibility( View.GONE ); //initially the spinner is hidden

		RadioButton radioButton1 = vista.findViewById(R.id.nuovoparente_1);
		radioButton1.setOnCheckedChangeListener((r, selected) -> {
			if( selected ) populateSpinner(1);
		});
		RadioButton radioButton2 = vista.findViewById(R.id.nuovoparente_2);
		radioButton2.setOnCheckedChangeListener((r, selected) -> {
			if( selected ) populateSpinner(2);
		});
		RadioButton radioButton3 = vista.findViewById(R.id.nuovoparente_3);
		radioButton3.setOnCheckedChangeListener((r, selected) -> {
			if( selected ) populateSpinner(3);
		});
		RadioButton radioButton4 = vista.findViewById(R.id.nuovoparente_4);
		radioButton4.setOnCheckedChangeListener((r, selected) -> {
			if( selected ) populateSpinner(4);
		});

		builder.setView(vista).setPositiveButton(android.R.string.ok, (dialog, id) -> {
			// Set some values that will be passed to IndividualEditorActivity or to ListOfPeopleFragment and will arrive at addRelative()
			Intent intent = new Intent();
			intent.putExtra("idIndividuo", pivot.getId());
			intent.putExtra("relazione", relationship);
			FamilyItem familyItem = (FamilyItem)spinner.getSelectedItem();
			if( familyItem.family != null )
				intent.putExtra("idFamiglia", familyItem.family.getId());
			else if( familyItem.parent != null ) // Using 'location' to convey the id of the parent (the third actor in the scene)
				intent.putExtra("collocazione", "NUOVA_FAMIGLIA_DI" + familyItem.parent.getId());
			else if( familyItem.existing) // conveys to the ListOfPeopleFragment the intention to join an existing family
				intent.putExtra("collocazione", "FAMIGLIA_ESISTENTE");
			if(newRelative) { // Collega persona nuova
				intent.setClass(getContext(), IndividualEditorActivity.class);
				startActivity(intent);
			} else { // Connect existing person
				intent.putExtra("anagrafeScegliParente", true);
				intent.setClass(getContext(), Principal.class);
				if( fragment != null )
					fragment.startActivityForResult(intent, 1401);
				else
					getActivity().startActivityForResult(intent, 1401);
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
	public void onSaveInstanceState(Bundle bandolo) {
		bandolo.putString("idPerno", pivot.getId());
		if( childFamPref != null )
			bandolo.putString("idFamFiglio", childFamPref.getId());
		if( spouseFamPref != null )
			bandolo.putString("idFamSposo", spouseFamPref.getId());
		bandolo.putBoolean("nuovo", newRelative);
		//Save the fragment's instance
		if( fragment != null )
			getActivity().getSupportFragmentManager().putFragment(bandolo, "frammento", fragment);
	}

	/**
	 * Tells if there is empty space in a family to add one of the two parents
	 * */
	boolean containsSpouseShortage(Family fam) {
		return fam.getHusbandRefs().size() + fam.getWifeRefs().size() < 2;
	}

	private void populateSpinner(int relationship) {
		this.relationship = relationship;
		items.clear();
		int select = -1; // Index of the item to select in the spinner. If -1 remains select the first entry of the spinner
		switch( relationship ) {
			case 1: // Parent
				for( Family fam : pivot.getParentFamilies(Global.gc) ) {
					items.add( new FamilyItem(getContext(),fam) );
					if( (fam.equals(childFamPref)   // Select the preferred family in which he is a child
							|| select < 0)           // or the first one available
							&& containsSpouseShortage(fam) ) // if they have empty parent space
						select = items.size() - 1;
				}
				items.add( new FamilyItem(getContext(),false) );
				if( select < 0 )
					select = items.size() - 1; // Select "New family"
				break;
			case 2: // Sibling
				for( Family fam : pivot.getParentFamilies(Global.gc) ) {
					items.add( new FamilyItem(getContext(),fam) );
					for( Person padre : fam.getHusbands(Global.gc) ) {
						for( Family fam2 : padre.getSpouseFamilies(Global.gc) )
							if( !fam2.equals(fam) )
								items.add( new FamilyItem(getContext(),fam2) );
						items.add( new FamilyItem(getContext(),padre) );
					}
					for( Person madre : fam.getWives(Global.gc) ) {
						for( Family fam2 : madre.getSpouseFamilies(Global.gc) )
							if( !fam2.equals(fam) )
								items.add( new FamilyItem(getContext(),fam2) );
						items.add( new FamilyItem(getContext(),madre) );
					}
				}
				items.add( new FamilyItem(getContext(),false) );
				// Select the preferred family as a child
				select = 0;
				for( FamilyItem voce : items)
					if( voce.family != null && voce.family.equals(childFamPref) ) {
						select = items.indexOf(voce);
						break;
					}
				break;
			case 3: // Spouse
			case 4: // Child
				for( Family fam : pivot.getSpouseFamilies(Global.gc) ) {
					items.add( new FamilyItem(getContext(),fam) );
					if( (items.size() > 1 && fam.equals(spouseFamPref)) // Select your favorite family as a spouse (except the first one)
							|| (containsSpouseShortage(fam) && select < 0) ) // Select the first family where there are no spouses
						select = items.size() - 1;
				}
				items.add( new FamilyItem(getContext(), pivot) );
				if( select < 0 )
					select = items.size() - 1; // Select "New family of..."
				// For a child, select the preferred family (if any), otherwise the first
				if( relationship == 4 ) {
					select = 0;
					for( FamilyItem voce : items)
						if( voce.family != null && voce.family.equals(spouseFamPref) ) {
							select = items.indexOf(voce);
							break;
						}
				}
		}
		if( !newRelative) {
			items.add( new FamilyItem(getContext(), true) );
		}
		ArrayAdapter<FamilyItem> adapter = (ArrayAdapter) spinner.getAdapter();
		adapter.clear();
		adapter.addAll(items);
		((View)spinner.getParent()).setVisibility( View.VISIBLE );
		spinner.setSelection(select);
		dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
	}

	/**
	 * Class for family list entries in dialogs "Which family do you want to add...?"
	 * */
	static class FamilyItem {
		Context context;
		Family family;
		Person parent;
		boolean existing; // pivot try to fit into the already existing family

		/**
		 * Existing family
		 * */
		FamilyItem(Context context, Family family) {
			this.context = context;
			this.family = family;
		}

		/**
		 * New family of a parent
		 * */
		FamilyItem(Context context, Person parent) {
			this.context = context;
			this.parent = parent;
		}

		/**
		 * Empty new family (false) OR recipient-acquired family (true)
		 * */
		FamilyItem(Context context, boolean existing) {
			this.context = context;
			this.existing = existing;
		}

		@Override
		public String toString() {
			if( family != null)
				return U.familyText(context, Global.gc, family, true);
			else if( parent != null )
				return context.getString(R.string.new_family_of, U.properName(parent));
			else if(existing)
				return context.getString(R.string.existing_family);
			else
				return context.getString(R.string.new_family);
		}
	}
}
