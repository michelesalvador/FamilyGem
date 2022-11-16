package app.familygem;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.RecyclerView;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import android.widget.Toast;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Person;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.joda.time.Years;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import app.familygem.constant.Format;
import app.familygem.constant.Gender;
import static app.familygem.Global.gc;
import com.lb.fast_scroller_and_recycler_view_fixes_library.FastScrollerEx;

public class ListOfPeopleFragment extends Fragment {

	List<Person> people;
	PeopleAdapter adapter;
	private Order order;
	private boolean idsAreNumeric;

	private enum Order {
		NONE,
		ID_ASC, ID_DESC,
		SURNAME_ASC, SURNAME_DESC,
		DATE_ASC, DATE_DESC,
		AGE_ASC, AGE_DESC,
		KIN_ASC, KIN_DESC;
		public Order next() {
			return values()[ordinal() + 1];
		}
		public Order prev() {
			return values()[ordinal() - 1];
		}
	};

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
		View view = inflater.inflate(R.layout.ricicla_vista, container, false);
		if( gc != null ) {
			people = gc.getPeople();
			setupToolbar();
			RecyclerView recyclerView = view.findViewById(R.id.riciclatore);
			recyclerView.setPadding(12, 12, 12, recyclerView.getPaddingBottom());
			adapter = new PeopleAdapter();
			recyclerView.setAdapter(adapter);
			idsAreNumeric = verifyIdsAreNumeric();
			view.findViewById(R.id.fab).setOnClickListener(v -> {
				Intent intent = new Intent(getContext(), IndividualEditorActivity.class);
				intent.putExtra("idIndividuo", "TIZIO_NUOVO");
				startActivity(intent);
			});

			// Fast scroller
			StateListDrawable thumbDrawable = (StateListDrawable)ContextCompat.getDrawable(getContext(), R.drawable.scroll_thumb);
			Drawable lineDrawable = ContextCompat.getDrawable(getContext(), R.drawable.empty);
			new FastScrollerEx(recyclerView, thumbDrawable, lineDrawable, thumbDrawable, lineDrawable,
					 U.dpToPx(40), U.dpToPx(100), 0, true, U.dpToPx(80));
		}
		return view;
	}

	void setupToolbar() {
		((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(people.size() + " "
				+ getString(people.size() == 1 ? R.string.person : R.string.persons).toLowerCase());
		setHasOptionsMenu(people.size() > 1);
	}

	public class PeopleAdapter extends RecyclerView.Adapter<IndividualViewHolder> implements Filterable {
		@Override
		public IndividualViewHolder onCreateViewHolder(ViewGroup parent, int type) {
			View individualView = LayoutInflater.from(parent.getContext())
					.inflate(R.layout.pezzo_individuo, parent, false);
			registerForContextMenu(individualView);
			return new IndividualViewHolder(individualView);
		}
		@Override
		public void onBindViewHolder(IndividualViewHolder holder, int position) {
			Person person = people.get(position);
			View indiView = holder.view;

			String label = null;
			if( order == Order.ID_ASC || order == Order.ID_DESC )
				label = person.getId();
			else if( order == Order.SURNAME_ASC || order == Order.SURNAME_DESC )
				label = U.surname(person);
			else if( order == Order.KIN_ASC || order == Order.KIN_DESC )
				label = String.valueOf(person.getExtension("kin"));
			TextView infoView = indiView.findViewById(R.id.indi_ruolo);
			if( label == null )
				infoView.setVisibility(View.GONE);
			else {
				infoView.setAllCaps(false);
				infoView.setText(label);
				infoView.setVisibility(View.VISIBLE);
			}

			TextView nameView = indiView.findViewById(R.id.indi_nome);
			String name = U.properName(person);
			nameView.setText(name);
			nameView.setVisibility((name.isEmpty() && label != null) ? View.GONE : View.VISIBLE);

			TextView titleView = indiView.findViewById(R.id.indi_titolo);
			String title = U.title(person);
			if( title.isEmpty() )
				titleView.setVisibility(View.GONE);
			else {
				titleView.setText(title);
				titleView.setVisibility(View.VISIBLE);
			}

			int border;
			switch( Gender.getGender(person) ) {
				case MALE: border = R.drawable.casella_bordo_maschio; break;
				case FEMALE: border = R.drawable.casella_bordo_femmina; break;
				default: border = R.drawable.casella_bordo_neutro;
			}
			indiView.findViewById(R.id.indi_bordo).setBackgroundResource(border);

			U.details(person, indiView.findViewById(R.id.indi_dettagli));
			F.showMainImageForPerson(Global.gc, person, indiView.findViewById(R.id.indi_foto));
			indiView.findViewById(R.id.indi_lutto).setVisibility(U.isDead(person) ? View.VISIBLE : View.GONE);
			indiView.setTag(person.getId());
		}
		@Override
		public Filter getFilter() {
			return new Filter() {
				@Override
				protected FilterResults performFiltering(CharSequence charSequence) {
					String query = charSequence.toString();
					if( query.isEmpty() ) {
						people = gc.getPeople();
					} else {
						List<Person> filteredList = new ArrayList<>();
						for( Person person : gc.getPeople() ) {
							if( U.properName(person).toLowerCase().contains(query.toLowerCase()) ) {
								filteredList.add(person);
							}
						}
						people = filteredList;
					}
					sortPeople();
					FilterResults filterResults = new FilterResults();
					filterResults.values = people;
					return filterResults;
				}
				@Override
				protected void publishResults(CharSequence cs, FilterResults fr) {
					notifyDataSetChanged();
				}
			};
		}
		@Override
		public int getItemCount() {
			return people.size();
		}
	}

	class IndividualViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
		View view;
		IndividualViewHolder(View view) {
			super(view);
			this.view = view;
			view.setOnClickListener(this);
		}
		@Override
		public void onClick( View vista ) {
			// Registry to choose the relative and return the values to Diagram, IndividualActivity, FamilyActivity or SharingActivity
			Person relative = gc.getPerson((String)vista.getTag());
			Intent intent = getActivity().getIntent();
			if( intent.getBooleanExtra("anagrafeScegliParente", false) ) {
				intent.putExtra( "idParente", relative.getId() );
				// Look for any existing family that can host the pivot
				String placement = intent.getStringExtra("collocazione");
				if( placement != null && placement.equals("FAMIGLIA_ESISTENTE") ) {
					String familyId = null;
					switch( intent.getIntExtra("relazione",0) ) {
						case 1: // Parent
							if( relative.getSpouseFamilyRefs().size() > 0 )
								familyId = relative.getSpouseFamilyRefs().get(0).getRef();
							break;
						case 2:
							if( relative.getParentFamilyRefs().size() > 0 )
								familyId = relative.getParentFamilyRefs().get(0).getRef();
							break;
						case 3:
							for( Family fam : relative.getSpouseFamilies(gc) ) {
								if( fam.getHusbandRefs().isEmpty() || fam.getWifeRefs().isEmpty() ) {
									familyId = fam.getId();
									break;
								}
							}
							break;
						case 4:
							for( Family fam : relative.getParentFamilies(gc) ) {
								if( fam.getHusbandRefs().isEmpty() || fam.getWifeRefs().isEmpty() ) {
									familyId = fam.getId();
									break;
								}
							}
							break;
					}
					if( familyId != null ) // addRelative() will use the found family
						intent.putExtra( "idFamiglia", familyId );
					else // addRelative() will create a new family
						intent.removeExtra( "collocazione" );
				}
				getActivity().setResult( AppCompatActivity.RESULT_OK, intent );
				getActivity().finish();
			} else { // Normal link to the individual file
				// todo Click on the photo opens the media tab..
				// intent.putExtra( "scheda", 0 );
				Memory.setFirst( relative );
				startActivity( new Intent(getContext(), IndividualPersonActivity.class) );
			}
		}
	}

	/**
	 * Leaving the activity without having chosen a relative resets the extra
	 * */
	@Override
	public void onPause() {
		super.onPause();
		getActivity().getIntent().removeExtra("anagrafeScegliParente");
	}

	/**
	 * Check if all people's ids contain numbers
	 * As soon as an id contains only letters it returns false
	 * */
	boolean verifyIdsAreNumeric() {
		external:
		for( Person p : gc.getPeople() ) {
			for( char c : p.getId().toCharArray() ) {
				if (Character.isDigit(c))
					continue external;
			}
			return false;
		}
		return true;
	}

	private void sortPeople() {
		Collections.sort(people, (p1, p2) -> {
			switch( order ) {
				case ID_ASC: // Sort for GEDCOM ID
					if(idsAreNumeric)
						return U.extractNum(p1.getId()) - U.extractNum(p2.getId());
					else
						return p1.getId().compareToIgnoreCase(p2.getId());
				case ID_DESC:
					if(idsAreNumeric)
						return U.extractNum(p2.getId()) - U.extractNum(p1.getId());
					else
						return p2.getId().compareToIgnoreCase(p1.getId());
				case SURNAME_ASC: // Sort for surname
					if (p1.getNames().size() == 0) // null names go to the bottom
						return (p2.getNames().size() == 0) ? 0 : 1;
					if (p2.getNames().size() == 0)
						return -1;
					Name n1 = p1.getNames().get(0);
					Name n2 = p2.getNames().get(0);
					// names with value, given, and surname null also go to the bottom
					if (n1.getValue() == null && n1.getGiven() == null && n1.getSurname() == null)
						return (n2.getValue() == null) ? 0 : 1;
					if (n2.getValue() == null && n2.getGiven() == null && n2.getSurname() == null)
						return -1;
					return surnameName(p1).compareToIgnoreCase(surnameName(p2));
				case SURNAME_DESC:
					if (p1.getNames().size() == 0)
						return p2.getNames().size() == 0 ? 0 : 1;
					if (p2.getNames().size() == 0)
						return -1;
					n1 = p1.getNames().get(0);
					n2 = p2.getNames().get(0);
					if (n1.getValue() == null && n1.getGiven() == null && n1.getSurname() == null)
						return (n2.getValue() == null) ? 0 : 1;
					if (n2.getValue() == null && n2.getGiven() == null && n2.getSurname() == null)
						return -1;
					return surnameName(p2).compareToIgnoreCase(surnameName(p1));
				case DATE_ASC: // Sort for person's main year
					return getDate(p1) - getDate(p2);
				case DATE_DESC:
					int date1 = getDate(p1);
					int date2 = getDate(p2);
					if( date2 == Integer.MAX_VALUE ) // Those without year go to the bottom
						return -1;
					if( date1 == Integer.MAX_VALUE )
						return date2 == Integer.MAX_VALUE ? 0 : 1;
					return date2 - date1;
				case AGE_ASC: // Sort for main person's year
					return getAge(p1) - getAge(p2);
				case AGE_DESC:
					int age1 = getAge(p1);
					int age2 = getAge(p2);
					if( age2 == Integer.MAX_VALUE ) // Those without age go to the bottom
						return -1;
					if( age1 == Integer.MAX_VALUE )
						return age2 == Integer.MAX_VALUE ? 0 : 1;
					return age2 - age1;
				case KIN_ASC: // Sort for number of relatives
					return countRelatives(p1) - countRelatives(p2);
				case KIN_DESC:
					return countRelatives(p2) - countRelatives(p1);
			}
			return 0;
		});
	}

	/**
	 * Returns a string with surname and firstname attached:
	 * 'SalvadorMichele ' or 'ValleFrancesco Maria ' or ' Donatella '
	 * */
	private static String surnameName(Person person) {
		Name name = person.getNames().get(0);
		String epithet = name.getValue();
		String givenName = "";
		String surname = " "; // there must be a space to sort first names without last names
		if( epithet != null ) {
			if( epithet.indexOf('/') > 0 )
				givenName = epithet.substring( 0, epithet.indexOf('/') );	// takes name before '/'
			if( epithet.lastIndexOf('/') - epithet.indexOf('/') > 1 )	// if there is a last name between the two '/'
				surname = epithet.substring( epithet.indexOf('/')+1, epithet.lastIndexOf("/") );
			String prefix = name.getPrefix(); // Only the givenname coming from value could have a prefix, from given no because it is already only the givenname
			if( prefix != null && givenName.startsWith(prefix) )
				givenName = givenName.substring( prefix.length() ).trim();
		} else {
			if( name.getGiven() != null )
				givenName = name.getGiven();
			if( name.getSurname() != null )
				surname = name.getSurname();
		}
		String surPrefix = name.getSurnamePrefix();
		if( surPrefix != null && surname.startsWith(surPrefix) )
			surname = surname.substring( surPrefix.length() ).trim();
		return surname.concat( givenName );
	}

	/**
	 * receives a Person and returns the first year of its existence
	 * */
	GedcomDateConverter gedcomDateConverter = new GedcomDateConverter("");
	private int findDate(Person person) {
		for( EventFact event : person.getEventsFacts() ) {
			if( event.getDate() != null ) {
				gedcomDateConverter.analyze(event.getDate());
				return gedcomDateConverter.getDateNumber();
			}
		}
		return Integer.MAX_VALUE;
	}

	int getDate(Person person) {
		Object date = person.getExtension("date");
		return date == null ? Integer.MAX_VALUE : (int)date;
	}

	/**
	 * Calculate the age of a person in days or MAX_VALUE
	 * */
	private int calcAge(Person person) {
		int days = Integer.MAX_VALUE;
		GedcomDateConverter start = null, end = null;
		for( EventFact event : person.getEventsFacts() ) {
			if( event.getTag() != null && event.getTag().equals("BIRT") && event.getDate() != null ) {
				start = new GedcomDateConverter(event.getDate());
				break;
			}
		}
		for( EventFact event : person.getEventsFacts() ) {
			if( event.getTag() != null && event.getTag().equals("DEAT") && event.getDate() != null ) {
				end = new GedcomDateConverter(event.getDate());
				break;
			}
		}
		if( start != null && start.isSingleKind() && !start.data1.isFormat(Format.D_M) ) {
			LocalDate startDate = new LocalDate(start.data1.date);
			// If the person is still alive the end is now
			LocalDate now = LocalDate.now();
			if( end == null && startDate.isBefore(now)
					&& Years.yearsBetween(startDate, now).getYears() <= 120 && !U.isDead(person) ) {
				end = new GedcomDateConverter(now.toDate());
			}
			if( end != null && end.isSingleKind() && !end.data1.isFormat(Format.D_M) ) {
				LocalDate endDate = new LocalDate(end.data1.date);
				if( startDate.isBefore(endDate) || startDate.isEqual(endDate) ) {
					days = Days.daysBetween(startDate, endDate).getDays();
				}
			}
		}
		return days;
	}

	int getAge(Person person) {
		Object age = person.getExtension("age");
		return age == null ? Integer.MAX_VALUE : (int)age;
	}

	/**
	 * Count how many near relatives a person has: parents, siblings, step-siblings, spouses and children.
	 * Save also the result in the 'kin' extension.
	 * @param person The person to start from
	 * @return Number of near relatives (person excluded)
	 */
	static int countRelatives(Person person) {
		int count = 0;
		if( person != null ) {
			// Families of origin: parents and siblings
			List<Family> families = person.getParentFamilies(gc);
			for( Family family : families ) {
				count += family.getHusbandRefs().size();
				count += family.getWifeRefs().size();
				for( Person sibling : family.getChildren(gc) ) // only children of the same two parents, not half-siblings
					if( !sibling.equals(person) )
						count++;
			}
			// Stepbrothers and stepsisters
			for( Family family : person.getParentFamilies(gc) ) {
				for( Person father : family.getHusbands(gc) ) {
					List<Family> fatherFamily = father.getSpouseFamilies(gc);
					fatherFamily.removeAll(families);
					for( Family fam : fatherFamily )
						count += fam.getChildRefs().size();
				}
				for( Person mother : family.getWives(gc) ) {
					List<Family> motherFamily = mother.getSpouseFamilies(gc);
					motherFamily.removeAll(families);
					for( Family fam : motherFamily )
						count += fam.getChildRefs().size();
				}
			}
			// Spouses and children
			for( Family family : person.getSpouseFamilies(gc) ) {
				count += family.getWifeRefs().size();
				count += family.getHusbandRefs().size();
				count--; // Minus their self
				count += family.getChildRefs().size();
			}
			person.putExtension("kin", count);
		}
		return count;
	}

	// option menu in the toolbar
	@Override
	public void onCreateOptionsMenu( Menu menu, MenuInflater inflater ) {

		SubMenu subMenu = menu.addSubMenu(R.string.order_by);
		if( Global.settings.expert )
			subMenu.add(0, 1, 0, R.string.id);
		subMenu.add(0, 2, 0, R.string.surname);
		subMenu.add(0, 3, 0, R.string.date);
		subMenu.add(0, 4, 0, R.string.age);
		subMenu.add(0, 5, 0, R.string.number_relatives);

		//Search in the ListOfPeopleActivity
		inflater.inflate( R.menu.cerca, menu );	// this is already enough to bring up the lens with the search field
		final SearchView searchView = (SearchView) menu.findItem(R.id.ricerca).getActionView();
		searchView.setOnQueryTextListener( new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextChange( String query ) {
				adapter.getFilter().filter(query);
				return true;
			}
			@Override
			public boolean onQueryTextSubmit( String q ) {
				searchView.clearFocus();
				return false;
			}
		});
	}
	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
		int id = item.getItemId();
		if( id > 0 && id <= 5 ) {
			// Clicking twice the same menu item switchs sorting ASC and DESC
			if( order == Order.values()[id * 2 - 1] )
				order = order.next();
			else if( order == Order.values()[id * 2] )
				order = order.prev();
			else
				order = Order.values()[id * 2 - 1];

			if( order == Order.DATE_ASC ) {
				for( Person p : gc.getPeople() ) {
					int date = findDate(p);
					if( date < Integer.MAX_VALUE )
						p.putExtension("date", date);
					else
						p.getExtensions().remove("date");
				}
			} else if( order == Order.AGE_ASC ) {
				for( Person p : gc.getPeople() ) {
					int age = calcAge(p);
					if( age < Integer.MAX_VALUE )
						p.putExtension("age", age);
					else
						p.getExtensions().remove("age");
				}
			}
			sortPeople();
			adapter.notifyDataSetChanged();
			//U.saveJson( false ); // doubt whether to put it to immediately save the tidying up of people...
			return true;
		}
		return false;
	}

	// contextual Menu
	private String idIndi;
	@Override
	public void onCreateContextMenu( ContextMenu menu, View view, ContextMenu.ContextMenuInfo info) {
		idIndi = (String)view.getTag();
		menu.add(0, 0, 0, R.string.diagram);
		String[] familyLabels = Diagram.getFamilyLabels(getContext(), gc.getPerson(idIndi), null);
		if( familyLabels[0] != null )
			menu.add(0, 1, 0, familyLabels[0]);
		if( familyLabels[1] != null )
			menu.add(0, 2, 0, familyLabels[1]);
		menu.add(0, 3, 0, R.string.modify);
		if( Global.settings.expert )
			menu.add(0, 4, 0, R.string.edit_id);
		menu.add(0, 5, 0, R.string.delete);
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		int id = item.getItemId();
		if( id == 0 ) {	// Open Diagram
			U.askWhichParentsToShow(getContext(), gc.getPerson(idIndi), 1);
		} else if( id == 1 ) { // Family as child
			U.askWhichParentsToShow(getContext(), gc.getPerson(idIndi), 2);
		} else if( id == 2 ) { // Family as spouse
			U.askWhichSpouseToShow(getContext(), gc.getPerson(idIndi), null);
		} else if( id == 3 ) { // Edit
			Intent intent = new Intent(getContext(), IndividualEditorActivity.class);
			intent.putExtra("idIndividuo", idIndi);
			startActivity(intent);
		} else if( id == 4 ) { // Edit ID
			U.editId(getContext(), gc.getPerson(idIndi), adapter::notifyDataSetChanged);
		} else if( id == 5 ) { // Delete
			new AlertDialog.Builder(getContext()).setMessage( R.string.really_delete_person )
					.setPositiveButton( R.string.delete, (dialog, i) -> {
						Family[] families = deletePerson(getContext(), idIndi);
						adapter.notifyDataSetChanged();
						setupToolbar();
						U.checkFamilyItem(getContext(), null, false, families);
					}).setNeutralButton( R.string.cancel, null ).show();
		} else {
			return false;
		}
		return true;
	}

	/**
	 * Delete all refs in that person's families
	 * @return the list of affected families
	 * */
	static Family[] disconnect(String idToDisconnect ) {
		Person personToDisconnect = gc.getPerson( idToDisconnect );
		Set<Family> families = new HashSet<>();
		for( Family f : personToDisconnect.getParentFamilies(gc) ) {	// unlink its refs in families
			f.getChildRefs().remove( f.getChildren(gc).indexOf(personToDisconnect) );
			families.add( f );
		}
		for( Family f : personToDisconnect.getSpouseFamilies(gc) ) {
			if( f.getHusbands(gc).contains(personToDisconnect) ) {
				f.getHusbandRefs().remove( f.getHusbands(gc).indexOf(personToDisconnect) );
				families.add( f );
			}
			if( f.getWives(gc).contains(personToDisconnect) ) {
				f.getWifeRefs().remove( f.getWives(gc).indexOf(personToDisconnect) );
				families.add( f );
			}
		}
		personToDisconnect.setParentFamilyRefs( null );	// in the indi it unlinks the refs of the families it belongs to
		personToDisconnect.setSpouseFamilyRefs( null );
		return families.toArray( new Family[0] );
	}

	/**
	 * Delete a person from the tree, possibly find the new root.
	 * @param context
	 * @param personId Id of the person to be deleted
	 * @return Array of modified families
	 */
	static Family[] deletePerson(Context context, String personId) {
		Family[] families = disconnect(personId);
		Person person = gc.getPerson(personId);
		Memory.setInstanceAndAllSubsequentToNull(person);
		gc.getPeople().remove(person);
		gc.createIndexes(); // Necessary
		String newRootId = U.findRoot(gc); // Todo should read: findNearestRelative
		if( Global.settings.getCurrentTree().root != null && Global.settings.getCurrentTree().root.equals(personId) ) {
			Global.settings.getCurrentTree().root = newRootId;
		}
		Global.settings.save();
		if( Global.indi != null && Global.indi.equals(personId) )
			Global.indi = newRootId;
		Toast.makeText(context, R.string.person_deleted, Toast.LENGTH_SHORT).show();
		U.save(true, (Object[])families);
		return families;
	}
}
