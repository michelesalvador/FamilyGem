package app.familygem.list;

import static app.familygem.Global.gc;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.lb.fast_scroller_and_recycler_view_fixes_library.FastScrollerEx;

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
import java.util.Timer;
import java.util.TimerTask;

import app.familygem.DiagramFragment;
import app.familygem.F;
import app.familygem.GedcomDateConverter;
import app.familygem.Global;
import app.familygem.Memory;
import app.familygem.PersonEditorActivity;
import app.familygem.Principal;
import app.familygem.ProfileActivity;
import app.familygem.ProfileFactsFragment;
import app.familygem.R;
import app.familygem.U;
import app.familygem.constant.Choice;
import app.familygem.constant.Extra;
import app.familygem.constant.Format;
import app.familygem.constant.Gender;
import app.familygem.constant.Relation;
import app.familygem.util.TreeUtils;

public class PersonsFragment extends Fragment {

    private final List<PersonWrapper> allPeople = new ArrayList<>(); // The immutable complete list of people
    private List<PersonWrapper> selectedPeople = new ArrayList<>(); // Some persons selected by the search feature
    private final PersonsAdapter adapter = new PersonsAdapter();
    private @NonNull
    Order order = Order.NONE;
    private SearchView searchView;
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
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        View view = inflater.inflate(R.layout.recyclerview, container, false);
        if (gc != null) {
            establishPeople();
            RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
            recyclerView.setPadding(12, 12, 12, recyclerView.getPaddingBottom());
            recyclerView.setAdapter(adapter);
            idsAreNumeric = verifyNumericIds();
            view.findViewById(R.id.fab).setOnClickListener(v -> startActivity(new Intent(getContext(), PersonEditorActivity.class)));

            // Fast scroller
            StateListDrawable thumbDrawable = (StateListDrawable)ContextCompat.getDrawable(getContext(), R.drawable.scroll_thumb);
            Drawable lineDrawable = ContextCompat.getDrawable(getContext(), R.drawable.empty);
            new FastScrollerEx(recyclerView, thumbDrawable, lineDrawable, thumbDrawable, lineDrawable,
                    U.dpToPx(40), U.dpToPx(100), 0, true, U.dpToPx(80));
        }
        return view;
    }

    // Put all the people inside the lists
    private void establishPeople() {
        allPeople.clear();
        for (Person person : gc.getPeople()) {
            allPeople.add(new PersonWrapper(person));
            // On version 0.9.2 all person's extensions was removed, replaced by PersonWrapper fields
            person.setExtensions(null); // todo remove on a future release
        }
        selectedPeople.clear();
        selectedPeople.addAll(allPeople);
        // Displays search results every second
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (getActivity() != null && searchView != null && searchView.hasFocus()) {
                    getActivity().runOnUiThread(() -> adapter.getFilter().filter(searchView.getQuery()));
                }
            }
        };
        timer.scheduleAtFixedRate(task, 500, 1000);
        new Thread() {
            @Override
            public void run() {
                for (PersonWrapper wrapper : allPeople) {
                    wrapper.completeFields(); // This task could take long time on a big tree
                }
                timer.cancel();
                // Displays final results
                if (getActivity() != null && searchView != null) {
                    getActivity().runOnUiThread(() -> adapter.getFilter().filter(searchView.getQuery()));
                }
            }
        }.start();
        furnishToolbar();
    }

    // Title and options in toolbar
    private void furnishToolbar() {
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(allPeople.size() + " "
                + getString(allPeople.size() == 1 ? R.string.person : R.string.persons).toLowerCase());
        setHasOptionsMenu(allPeople.size() > 1);
    }

    private class PersonsAdapter extends RecyclerView.Adapter<PersonHolder> implements Filterable {
        @Override
        public PersonHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View personView = LayoutInflater.from(parent.getContext()).inflate(R.layout.piece_person, parent, false);
            registerForContextMenu(personView);
            return new PersonHolder(personView);
        }

        @Override
        public void onBindViewHolder(PersonHolder personHolder, int position) {
            Person person = selectedPeople.get(position).person;
            View personView = personHolder.view;
            personView.setTag(R.id.tag_id, person.getId());
            personView.setTag(R.id.tag_position, position);

            String label = null;
            if (order == Order.ID_ASC || order == Order.ID_DESC)
                label = person.getId();
            else if (order == Order.SURNAME_ASC || order == Order.SURNAME_DESC)
                label = U.surname(person);
            else if (order == Order.KIN_ASC || order == Order.KIN_DESC)
                label = String.valueOf(selectedPeople.get(position).relatives);
            TextView infoView = personView.findViewById(R.id.person_info);
            if (label == null)
                infoView.setVisibility(View.GONE);
            else {
                infoView.setAllCaps(false);
                infoView.setText(label);
                infoView.setVisibility(View.VISIBLE);
            }

            TextView nameView = personView.findViewById(R.id.person_name);
            String name = U.properName(person);
            nameView.setText(name);
            nameView.setVisibility((name.isEmpty() && label != null) ? View.GONE : View.VISIBLE);

            TextView titleView = personView.findViewById(R.id.person_title);
            String title = U.titolo(person);
            if (title.isEmpty())
                titleView.setVisibility(View.GONE);
            else {
                titleView.setText(title);
                titleView.setVisibility(View.VISIBLE);
            }

            int border;
            switch (Gender.getGender(person)) {
                case MALE:
                    border = R.drawable.casella_bordo_maschio;
                    break;
                case FEMALE:
                    border = R.drawable.casella_bordo_femmina;
                    break;
                default:
                    border = R.drawable.casella_bordo_neutro;
            }
            personView.findViewById(R.id.person_border).setBackgroundResource(border);

            U.details(person, personView.findViewById(R.id.person_details));
            F.showMainImageForPerson(gc, person, personView.findViewById(R.id.person_image));
            personView.findViewById(R.id.person_mourning).setVisibility(U.isDead(person) ? View.VISIBLE : View.GONE);
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence charSequence) {
                    // Split query by spaces and search all the words
                    String[] query = charSequence.toString().trim().toLowerCase().split("\\s+");
                    // Instead of using selectedPeople, we create a new list to avoid IndexOutOfBoundsException when RecyclerView is scrolled
                    List<PersonWrapper> filteredPeople = new ArrayList<>();
                    if (query[0].isEmpty()) {
                        filteredPeople.addAll(allPeople);
                    } else {
                        outer:
                        for (PersonWrapper wrapper : allPeople) {
                            if (wrapper.text != null) {
                                for (String word : query) {
                                    if (!wrapper.text.contains(word)) {
                                        continue outer;
                                    }
                                }
                                filteredPeople.add(wrapper);
                            }
                        }
                    }
                    if (order != Order.NONE)
                        sortPeople();
                    FilterResults filterResults = new FilterResults();
                    filterResults.values = filteredPeople;
                    selectedPeople = filteredPeople;
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
            return selectedPeople.size();
        }
    }

    private class PersonHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        View view;

        PersonHolder(View view) {
            super(view);
            this.view = view;
            view.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            // Chooses the relative and returns the values to DiagramFragment, ProfileActivity, FamilyActivity or SharingActivity
            Person relative = gc.getPerson((String)view.getTag(R.id.tag_id));
            Intent intent = getActivity().getIntent();
            if (intent.getBooleanExtra(Choice.PERSON, false)) {
                intent.putExtra(Extra.RELATIVE_ID, relative.getId());
                // Searches for any existing family that can host the pivot
                String destination = intent.getStringExtra(Extra.DESTINATION);
                if (destination != null && destination.equals("EXISTING_FAMILY")) {
                    String familyId = null;
                    switch ((Relation)intent.getSerializableExtra(Extra.RELATION)) {
                        case PARENT:
                            if (relative.getSpouseFamilyRefs().size() > 0)
                                familyId = relative.getSpouseFamilyRefs().get(0).getRef();
                            break;
                        case SIBLING:
                            if (relative.getParentFamilyRefs().size() > 0)
                                familyId = relative.getParentFamilyRefs().get(0).getRef();
                            break;
                        case PARTNER:
                            for (Family family : relative.getSpouseFamilies(gc)) {
                                if (family.getHusbandRefs().isEmpty() || family.getWifeRefs().isEmpty()) {
                                    familyId = family.getId();
                                    break;
                                }
                            }
                            break;
                        case CHILD:
                            for (Family family : relative.getParentFamilies(gc)) {
                                if (family.getHusbandRefs().isEmpty() || family.getWifeRefs().isEmpty()) {
                                    familyId = family.getId();
                                    break;
                                }
                            }
                            break;
                    }
                    if (familyId != null) // addRelative() will use the found family
                        intent.putExtra(Extra.FAMILY_ID, familyId);
                    else // addRelative() will create a new family
                        intent.removeExtra(Extra.DESTINATION);
                }
                getActivity().setResult(AppCompatActivity.RESULT_OK, intent);
                getActivity().finish();
            } else { // Normal link to the person profile
                Memory.setLeader(relative);
                startActivity(new Intent(getContext(), ProfileActivity.class));
            }
        }
    }

    // Updates all the contents onBackPressed()
    public void restart() {
        // Recreates the lists for some person added or removed
        establishPeople();
        // Updates content of existing views
        adapter.notifyDataSetChanged();
    }

    // Reset the extra if leaving this fragment without choosing a person
    @Override
    public void onPause() {
        super.onPause();
        getActivity().getIntent().removeExtra(Choice.PERSON);
    }

    /**
     * Checks if all people's IDs contain numbers.
     *
     * @return False as soon as an ID contains only letters
     */
    private boolean verifyNumericIds() {
        out:
        for (Person person : gc.getPeople()) {
            for (char character : person.getId().toCharArray()) {
                if (Character.isDigit(character))
                    continue out;
            }
            return false;
        }
        return true;
    }

    private void sortPeople() {
        Collections.sort(selectedPeople, (wrapper1, wrapper2) -> {
            Person p1 = wrapper1.person;
            Person p2 = wrapper2.person;
            switch (order) {
                case ID_ASC: // Sort for GEDCOM ID
                    if (idsAreNumeric)
                        return U.extractNum(p1.getId()) - U.extractNum(p2.getId());
                    else
                        return p1.getId().compareToIgnoreCase(p2.getId());
                case ID_DESC:
                    if (idsAreNumeric)
                        return U.extractNum(p2.getId()) - U.extractNum(p1.getId());
                    else
                        return p2.getId().compareToIgnoreCase(p1.getId());
                case SURNAME_ASC: // Sort for surname
                    if (wrapper1.surname == null) // Null surnames go to the end
                        return wrapper2.surname == null ? 0 : 1;
                    if (wrapper2.surname == null)
                        return -1;
                    return wrapper1.surname.compareTo(wrapper2.surname);
                case SURNAME_DESC:
                    if (wrapper1.surname == null)
                        return wrapper2.surname == null ? 0 : 1;
                    if (wrapper2.surname == null)
                        return -1;
                    return wrapper2.surname.compareTo(wrapper1.surname);
                case DATE_ASC: // Sort for person's main year
                    return wrapper1.date - wrapper2.date;
                case DATE_DESC:
                    if (wrapper2.date == Integer.MAX_VALUE) // Those without year go to the bottom
                        return -1;
                    if (wrapper1.date == Integer.MAX_VALUE)
                        return 1;
                    return wrapper2.date - wrapper1.date;
                case AGE_ASC: // Sort for main person's year
                    return wrapper1.age - wrapper2.age;
                case AGE_DESC:
                    if (wrapper2.age == Integer.MAX_VALUE) // Those without age go to the bottom
                        return -1;
                    if (wrapper1.age == Integer.MAX_VALUE)
                        return 1;
                    return wrapper2.age - wrapper1.age;
                case KIN_ASC: // Sort for number of relatives
                    return wrapper1.relatives - wrapper2.relatives;
                case KIN_DESC:
                    return wrapper2.relatives - wrapper1.relatives;
            }
            return 0;
        });
    }

    /**
     * Writes a string with surname and first name concatenated.
     * E.g. 'salvadormichele ' or 'vallefrancesco maria ' or ' donatella '.
     */
    private String getSurnameFirstname(Person person) {
        List<Name> names = person.getNames();
        if (!names.isEmpty()) {
            Name name = names.get(0);
            String value = name.getValue();
            if (value != null || name.getGiven() != null || name.getSurname() != null) {
                String given = "";
                String surname = " "; // There must be a space to sort names without surname
                if (value != null) {
                    if (value.replace('/', ' ').trim().isEmpty()) // Empty value
                        return null;
                    if (value.indexOf('/') > 0)
                        given = value.substring(0, value.indexOf('/')); // Take the given name before '/'
                    if (value.lastIndexOf('/') - value.indexOf('/') > 1) // If there is a surname between two '/'
                        surname = value.substring(value.indexOf('/') + 1, value.lastIndexOf("/"));
                    // Only the given name coming from the value could have a prefix,
                    // from getGiven() no, because it is already only the given name.
                    String prefix = name.getPrefix();
                    if (prefix != null && given.startsWith(prefix))
                        given = given.substring(prefix.length()).trim();
                } else {
                    if (name.getGiven() != null)
                        given = name.getGiven();
                    if (name.getSurname() != null)
                        surname = name.getSurname();
                }
                String surPrefix = name.getSurnamePrefix();
                if (surPrefix != null && surname.startsWith(surPrefix))
                    surname = surname.substring(surPrefix.length()).trim();
                return surname.concat(given).toLowerCase();
            }
        }
        return null;
    }

    /**
     * Counts how many near relatives one person has: parents, siblings, half-siblings, spouses and children.
     *
     * @param person The person to start from
     * @return Number of near relatives (person excluded)
     */
    public static int countRelatives(Person person) {
        int count = 0;
        if (person != null) {
            // Parents and siblings
            List<Family> personFamilies = person.getParentFamilies(gc);
            for (Family family : personFamilies) {
                count += family.getHusbandRefs().size();
                count += family.getWifeRefs().size();
                for (Person sibling : family.getChildren(gc)) // Only the children of the same two parents, not the half-siblings
                    if (!sibling.equals(person))
                        count++;
            }
            // Half-sibling
            for (Family family : person.getParentFamilies(gc)) {
                for (Person father : family.getHusbands(gc)) {
                    List<Family> fatherFamilies = father.getSpouseFamilies(gc);
                    fatherFamilies.removeAll(personFamilies);
                    for (Family fam : fatherFamilies)
                        count += fam.getChildRefs().size();
                }
                for (Person mother : family.getWives(gc)) {
                    List<Family> motherFamilies = mother.getSpouseFamilies(gc);
                    motherFamilies.removeAll(personFamilies);
                    for (Family fam : motherFamilies)
                        count += fam.getChildRefs().size();
                }
            }
            // Partners and children
            for (Family family : person.getSpouseFamilies(gc)) {
                count += family.getWifeRefs().size();
                count += family.getHusbandRefs().size();
                count--; // Minus their self
                count += family.getChildRefs().size();
            }
        }
        return count;
    }

    // Class to wrap a person of the list and all their relevant fields
    GedcomDateConverter datator = new GedcomDateConverter(""); // Here outside to initialize only once

    private class PersonWrapper {

        final Person person;
        String text; // Single string with all names and events for search
        String surname; // Surname and name of the person
        int date; // Date in the format YYYYMMDD
        int age; // Age in days
        int relatives; // Number of near relatives

        PersonWrapper(Person person) {
            this.person = person;
        }

        void completeFields() {
            // Writes one string concatenating all names and personal events
            StringBuilder builder = new StringBuilder();
            for (Name name : person.getNames()) {
                builder.append(U.firstAndLastName(name, " ")).append(" ");
            }
            for (EventFact event : person.getEventsFacts()) {
                if (!("SEX".equals(event.getTag()) || "Y".equals(event.getValue()))) // Sex and 'Yes' excluded
                    builder.append(ProfileFactsFragment.writeEventText(event)).append(" ");
            }
            text = builder.toString().toLowerCase();

            // Surname and first name concatenated
            surname = getSurnameFirstname(person);

            // Finds the first date of a person's life or MAX_VALUE
            date = Integer.MAX_VALUE;
            for (EventFact event : person.getEventsFacts()) {
                if (event.getDate() != null) {
                    datator.analyze(event.getDate());
                    date = datator.getDateNumber();
                }
            }

            // Calculates the age of a person in days or MAX_VALUE
            age = Integer.MAX_VALUE;
            GedcomDateConverter start = null, end = null;
            for (EventFact event : person.getEventsFacts()) {
                if (event.getTag() != null && event.getTag().equals("BIRT") && event.getDate() != null) {
                    start = new GedcomDateConverter(event.getDate());
                    break;
                }
            }
            for (EventFact event : person.getEventsFacts()) {
                if (event.getTag() != null && event.getTag().equals("DEAT") && event.getDate() != null) {
                    end = new GedcomDateConverter(event.getDate());
                    break;
                }
            }
            if (start != null && start.isSingleKind() && !start.data1.isFormat(Format.D_M)) {
                LocalDate startDate = new LocalDate(start.data1.date);
                // If the person is still alive the end is now
                LocalDate now = LocalDate.now();
                if (end == null && startDate.isBefore(now)
                        && Years.yearsBetween(startDate, now).getYears() <= 120 && !U.isDead(person)) {
                    end = new GedcomDateConverter(now.toDate());
                }
                if (end != null && end.isSingleKind() && !end.data1.isFormat(Format.D_M)) {
                    LocalDate endDate = new LocalDate(end.data1.date);
                    if (startDate.isBefore(endDate) || startDate.isEqual(endDate)) {
                        age = Days.daysBetween(startDate, endDate).getDays();
                    }
                }
            }

            // Relatives count
            relatives = countRelatives(person);
        }
    }

    // Options menu in toolbar
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {

        SubMenu subMenu = menu.addSubMenu(R.string.order_by);
        if (Global.settings.expert)
            subMenu.add(0, 1, 0, R.string.id);
        subMenu.add(0, 2, 0, R.string.surname);
        subMenu.add(0, 3, 0, R.string.date);
        subMenu.add(0, 4, 0, R.string.age);
        subMenu.add(0, 5, 0, R.string.number_relatives);

        // Search in PersonsFragment
        inflater.inflate(R.menu.search, menu); // This only makes appear the lens with the search field
        searchView = (SearchView)menu.findItem(R.id.search_item).getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextChange(String query) {
                adapter.getFilter().filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextSubmit(String q) {
                searchView.clearFocus();
                return false;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id > 0 && id <= 5) {
            // Clicking twice the same menu item switches sorting ASC and DESC
            if (order == Order.values()[id * 2 - 1])
                order = order.next();
            else if (order == Order.values()[id * 2])
                order = order.prev();
            else
                order = Order.values()[id * 2 - 1];
            sortPeople();
            adapter.notifyDataSetChanged();
            // Updates sorting of people in global GEDCOM too
            if (selectedPeople.size() == gc.getPeople().size()) { // Only if there is no filtering
                List<Person> sortedPeople = new ArrayList<>();
                for (PersonWrapper wrapper : selectedPeople) sortedPeople.add(wrapper.person);
                gc.setPeople(sortedPeople);
                TreeUtils.INSTANCE.save(false); // Too much?
            }
            return true;
        }
        return false;
    }

    // Context menu
    private int position;
    private String personId;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo info) {
        personId = (String)view.getTag(R.id.tag_id);
        position = (int)view.getTag(R.id.tag_position);
        menu.add(0, 0, 0, R.string.diagram);
        String[] familyLabels = DiagramFragment.getFamilyLabels(getContext(), gc.getPerson(personId), null);
        if (familyLabels[0] != null)
            menu.add(0, 1, 0, familyLabels[0]);
        if (familyLabels[1] != null)
            menu.add(0, 2, 0, familyLabels[1]);
        menu.add(0, 3, 0, R.string.modify);
        if (Global.settings.expert)
            menu.add(0, 4, 0, R.string.edit_id);
        menu.add(0, 5, 0, R.string.delete);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == 0) { // Display diagram
            U.askWhichParentsToShow(getContext(), gc.getPerson(personId), 1);
        } else if (id == 1) { // Family as child
            U.askWhichParentsToShow(getContext(), gc.getPerson(personId), 2);
        } else if (id == 2) { // Family as partner
            U.askWhichSpouceToShow(getContext(), gc.getPerson(personId), null);
        } else if (id == 3) { // Edit person
            Intent intent = new Intent(getContext(), PersonEditorActivity.class);
            intent.putExtra(Extra.PERSON_ID, personId);
            startActivity(intent);
        } else if (id == 4) { // Edit ID
            U.editId(getContext(), gc.getPerson(personId), adapter::notifyDataSetChanged);
        } else if (id == 5) { // Delete person
            new AlertDialog.Builder(getContext()).setMessage(R.string.really_delete_person)
                    .setPositiveButton(R.string.delete, (dialog, i) -> {
                        Family[] families = deletePerson(getContext(), personId);
                        selectedPeople.remove(position);
                        allPeople.remove(position);
                        adapter.notifyItemRemoved(position);
                        adapter.notifyItemRangeChanged(position, selectedPeople.size() - position);
                        furnishToolbar();
                        ((Principal)requireActivity()).furnishMenu();
                        U.controllaFamiglieVuote(getContext(), null, false, families);
                    }).setNeutralButton(R.string.cancel, null).show();
        } else {
            return false;
        }
        return true;
    }

    /**
     * Deletes all references between a person and their families.
     *
     * @return An array of affected families
     */
    static Family[] unlinkPerson(Person person) {
        // From families to person
        Set<Family> families = new HashSet<>();
        for (Family family : person.getParentFamilies(gc)) {
            family.getChildRefs().remove(family.getChildren(gc).indexOf(person));
            families.add(family);
        }
        for (Family family : person.getSpouseFamilies(gc)) {
            if (family.getHusbands(gc).contains(person)) {
                family.getHusbandRefs().remove(family.getHusbands(gc).indexOf(person));
                families.add(family);
            }
            if (family.getWives(gc).contains(person)) {
                family.getWifeRefs().remove(family.getWives(gc).indexOf(person));
                families.add(family);
            }
        }
        // From person to families
        person.setParentFamilyRefs(null);
        person.setSpouseFamilyRefs(null);
        return families.toArray(new Family[0]);
    }

    /**
     * Deletes a person from the tree, possibly finding the new root.
     *
     * @param personId Id of the person to be deleted
     * @return Array of modified families
     */
    public static Family[] deletePerson(Context context, String personId) {
        Person person = gc.getPerson(personId);
        Family[] families = unlinkPerson(person);
        Memory.setInstanceAndAllSubsequentToNull(person);
        gc.getPeople().remove(person);
        gc.createIndexes(); // Necessary
        String newRootId = U.trovaRadice(gc); // TODO: could be "find next of kin"
        if (Global.settings.getCurrentTree().root != null && Global.settings.getCurrentTree().root.equals(personId)) {
            Global.settings.getCurrentTree().root = newRootId;
        }
        Global.settings.save();
        if (Global.indi != null && Global.indi.equals(personId))
            Global.indi = newRootId;
        Toast.makeText(context, R.string.person_deleted, Toast.LENGTH_SHORT).show();
        TreeUtils.INSTANCE.save(true, (Object[])families);
        return families;
    }
}
