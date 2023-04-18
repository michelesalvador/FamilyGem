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
import app.familygem.ProfileActivity;
import app.familygem.ProfileFactsFragment;
import app.familygem.R;
import app.familygem.U;
import app.familygem.constant.Choice;
import app.familygem.constant.Format;
import app.familygem.constant.Gender;
import app.familygem.util.TreeUtils;

public class PersonsFragment extends Fragment {

    private List<PersonWrapper> allPeople = new ArrayList<>(); // The immutable complete list of people
    private List<PersonWrapper> selectedPeople = new ArrayList<>(); // Some persons selected by the search feature
    private PersonsAdapter adapter = new PersonsAdapter();
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
        View view = inflater.inflate(R.layout.recycler_view, container, false);
        if (gc != null) {
            establishPeople();
            RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
            recyclerView.setPadding(12, 12, 12, recyclerView.getPaddingBottom());
            recyclerView.setAdapter(adapter);
            idsAreNumeric = verifyNumericIds();
            view.findViewById(R.id.fab).setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), PersonEditorActivity.class);
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
        // Display search results every second
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (getActivity() != null && searchView != null) {
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
                // Display final rusults
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

    private class PersonsAdapter extends RecyclerView.Adapter<IndiHolder> implements Filterable {
        @Override
        public IndiHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View indiView = LayoutInflater.from(parent.getContext()).inflate(R.layout.piece_person, parent, false);
            registerForContextMenu(indiView);
            return new IndiHolder(indiView);
        }

        @Override
        public void onBindViewHolder(IndiHolder indiHolder, int position) {
            Person person = selectedPeople.get(position).person;
            View indiView = indiHolder.view;
            indiView.setTag(R.id.tag_id, person.getId());
            indiView.setTag(R.id.tag_position, position);

            String label = null;
            if (order == Order.ID_ASC || order == Order.ID_DESC)
                label = person.getId();
            else if (order == Order.SURNAME_ASC || order == Order.SURNAME_DESC)
                label = U.surname(person);
            else if (order == Order.KIN_ASC || order == Order.KIN_DESC)
                label = String.valueOf(selectedPeople.get(position).relatives);
            TextView infoView = indiView.findViewById(R.id.person_info);
            if (label == null)
                infoView.setVisibility(View.GONE);
            else {
                infoView.setAllCaps(false);
                infoView.setText(label);
                infoView.setVisibility(View.VISIBLE);
            }

            TextView nameView = indiView.findViewById(R.id.person_name);
            String name = U.properName(person);
            nameView.setText(name);
            nameView.setVisibility((name.isEmpty() && label != null) ? View.GONE : View.VISIBLE);

            TextView titleView = indiView.findViewById(R.id.person_title);
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
            indiView.findViewById(R.id.person_border).setBackgroundResource(border);

            U.details(person, indiView.findViewById(R.id.person_details));
            F.showMainImageForPerson(Global.gc, person, indiView.findViewById(R.id.person_image));
            indiView.findViewById(R.id.person_mourning).setVisibility(U.isDead(person) ? View.VISIBLE : View.GONE);
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence charSequence) {
                    // Split query by spaces and search all the words
                    String[] query = charSequence.toString().trim().toLowerCase().split("\\s+");
                    selectedPeople.clear();
                    if (query.length == 0) {
                        selectedPeople.addAll(allPeople);
                    } else {
                        outer:
                        for (PersonWrapper wrapper : allPeople) {
                            if (wrapper.text != null) {
                                for (String word : query) {
                                    if (!wrapper.text.contains(word)) {
                                        continue outer;
                                    }
                                }
                                selectedPeople.add(wrapper);
                            }
                        }
                    }
                    if (order != Order.NONE)
                        sortPeople();
                    FilterResults filterResults = new FilterResults();
                    filterResults.values = selectedPeople;
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

    private class IndiHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        View view;

        IndiHolder(View view) {
            super(view);
            this.view = view;
            view.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            // Choose the relative and return the values to DiagramFragment, ProfileActivity, FamilyActivity or SharingActivity
            Person relative = gc.getPerson((String)view.getTag(R.id.tag_id));
            Intent intent = getActivity().getIntent();
            if (intent.getBooleanExtra(Choice.PERSON, false)) {
                intent.putExtra("idParente", relative.getId());
                // Cerca una eventuale famiglia esistente che possa ospitare perno
                String collocazione = intent.getStringExtra("collocazione");
                if (collocazione != null && collocazione.equals("FAMIGLIA_ESISTENTE")) {
                    String idFamiglia = null;
                    switch (intent.getIntExtra("relazione", 0)) {
                        case 1: // Genitore
                            if (relative.getSpouseFamilyRefs().size() > 0)
                                idFamiglia = relative.getSpouseFamilyRefs().get(0).getRef();
                            break;
                        case 2:
                            if (relative.getParentFamilyRefs().size() > 0)
                                idFamiglia = relative.getParentFamilyRefs().get(0).getRef();
                            break;
                        case 3:
                            for (Family fam : relative.getSpouseFamilies(gc)) {
                                if (fam.getHusbandRefs().isEmpty() || fam.getWifeRefs().isEmpty()) {
                                    idFamiglia = fam.getId();
                                    break;
                                }
                            }
                            break;
                        case 4:
                            for (Family fam : relative.getParentFamilies(gc)) {
                                if (fam.getHusbandRefs().isEmpty() || fam.getWifeRefs().isEmpty()) {
                                    idFamiglia = fam.getId();
                                    break;
                                }
                            }
                            break;
                    }
                    if (idFamiglia != null) // aggiungiParente() userà la famiglia trovata
                        intent.putExtra("idFamiglia", idFamiglia);
                    else // aggiungiParente() creerà una nuova famiglia
                        intent.removeExtra("collocazione");
                }
                getActivity().setResult(AppCompatActivity.RESULT_OK, intent);
                getActivity().finish();
            } else { // Normal link to the profile
                Memory.setLeader(relative);
                startActivity(new Intent(getContext(), ProfileActivity.class));
            }
        }
    }

    // Update all the contents onBackPressed()
    public void restart() {
        // Recreate the lists for some person added or removed
        establishPeople();
        // Update content of existing views
        adapter.notifyDataSetChanged();
    }

    // Reset the extra if leaving this fragment without choosing a person
    @Override
    public void onPause() {
        super.onPause();
        getActivity().getIntent().removeExtra(Choice.PERSON);
    }

    // Verifica se tutti gli ID delle persone contengono numeri
    // Appena un ID contiene solo lettere restituisce false
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

    // Write a string with surname and first name concatenated:
    // 'salvadormichele ' or 'vallefrancesco maria ' or ' donatella '
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
     * Count how many near relatives one person has: parents, siblings, step-siblings, spouses and children.
     *
     * @param person The person to start from
     * @return Number of near relatives (person excluded)
     */
    public static int countRelatives(Person person) {
        int count = 0;
        if (person != null) {
            // Famiglie di origine: genitori e fratelli
            List<Family> listaFamiglie = person.getParentFamilies(gc);
            for (Family famiglia : listaFamiglie) {
                count += famiglia.getHusbandRefs().size();
                count += famiglia.getWifeRefs().size();
                for (Person fratello : famiglia.getChildren(gc)) // solo i figli degli stessi due genitori, non i fratellastri
                    if (!fratello.equals(person))
                        count++;
            }
            // Fratellastri e sorellastre
            for (Family famiglia : person.getParentFamilies(gc)) {
                for (Person padre : famiglia.getHusbands(gc)) {
                    List<Family> famigliePadre = padre.getSpouseFamilies(gc);
                    famigliePadre.removeAll(listaFamiglie);
                    for (Family fam : famigliePadre)
                        count += fam.getChildRefs().size();
                }
                for (Person madre : famiglia.getWives(gc)) {
                    List<Family> famiglieMadre = madre.getSpouseFamilies(gc);
                    famiglieMadre.removeAll(listaFamiglie);
                    for (Family fam : famiglieMadre)
                        count += fam.getChildRefs().size();
                }
            }
            // Coniugi e figli
            for (Family famiglia : person.getSpouseFamilies(gc)) {
                count += famiglia.getWifeRefs().size();
                count += famiglia.getHusbandRefs().size();
                count--; // Minus their self
                count += famiglia.getChildRefs().size();
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
            // Write one string concatenating all names and personal events
            text = "";
            for (Name name : person.getNames()) {
                text += U.firstAndLastName(name, " ") + " ";
            }
            for (EventFact event : person.getEventsFacts()) {
                if (!("SEX".equals(event.getTag()) || "Y".equals(event.getValue()))) // Sex and 'Yes' excluded
                    text += ProfileFactsFragment.writeEventText(event) + " ";
            }
            text = text.toLowerCase();

            // Surname and first name concatenated
            surname = getSurnameFirstname(person);

            // Find the first date of a person's life or MAX_VALUE
            date = Integer.MAX_VALUE;
            for (EventFact event : person.getEventsFacts()) {
                if (event.getDate() != null) {
                    datator.analyze(event.getDate());
                    date = datator.getDateNumber();
                }
            }

            // Calculate the age of a person in days or MAX_VALUE
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

            // Relatives
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
        inflater.inflate(R.menu.search, menu); // già questo basta a far comparire la lente con il campo di ricerca
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
            //U.salvaJson(false); // dubbio se metterlo per salvare subito il riordino delle persone...
            return true;
        }
        return false;
    }

    // Context menu
    private int position;
    private String indiId;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo info) {
        indiId = (String)view.getTag(R.id.tag_id);
        position = (int)view.getTag(R.id.tag_position);
        menu.add(0, 0, 0, R.string.diagram);
        String[] familyLabels = DiagramFragment.getFamilyLabels(getContext(), gc.getPerson(indiId), null);
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
        if (id == 0) {    // Apri Diagramma
            U.askWhichParentsToShow(getContext(), gc.getPerson(indiId), 1);
        } else if (id == 1) { // Famiglia come figlio
            U.askWhichParentsToShow(getContext(), gc.getPerson(indiId), 2);
        } else if (id == 2) { // Famiglia come coniuge
            U.askWhichSpouceToShow(getContext(), gc.getPerson(indiId), null);
        } else if (id == 3) { // Modifica
            Intent intent = new Intent(getContext(), PersonEditorActivity.class);
            intent.putExtra("idIndividuo", indiId);
            startActivity(intent);
        } else if (id == 4) { // Edit ID
            U.editId(getContext(), gc.getPerson(indiId), adapter::notifyDataSetChanged);
        } else if (id == 5) { // Elimina
            new AlertDialog.Builder(getContext()).setMessage(R.string.really_delete_person)
                    .setPositiveButton(R.string.delete, (dialog, i) -> {
                        Family[] famiglie = deletePerson(getContext(), indiId);
                        selectedPeople.remove(position);
                        allPeople.remove(position);
                        adapter.notifyItemRemoved(position);
                        adapter.notifyItemRangeChanged(position, selectedPeople.size() - position);
                        furnishToolbar();
                        U.controllaFamiglieVuote(getContext(), null, false, famiglie);
                    }).setNeutralButton(R.string.cancel, null).show();
        } else {
            return false;
        }
        return true;
    }

    // Cancella tutti i ref nelle famiglie della tal persona
    // Restituisce l'elenco delle famiglie affette
    static Family[] unlinkPerson(Person person) {
        Set<Family> families = new HashSet<>();
        for (Family f : person.getParentFamilies(gc)) { // scollega i suoi ref nelle famiglie
            f.getChildRefs().remove(f.getChildren(gc).indexOf(person));
            families.add(f);
        }
        for (Family f : person.getSpouseFamilies(gc)) {
            if (f.getHusbands(gc).contains(person)) {
                f.getHusbandRefs().remove(f.getHusbands(gc).indexOf(person));
                families.add(f);
            }
            if (f.getWives(gc).contains(person)) {
                f.getWifeRefs().remove(f.getWives(gc).indexOf(person));
                families.add(f);
            }
        }
        person.setParentFamilyRefs(null); // nell'indi scollega i ref delle famiglie a cui appartiene
        person.setSpouseFamilyRefs(null);
        return families.toArray(new Family[0]);
    }

    /**
     * Delete a person from the tree, possibly find the new root.
     *
     * @param context
     * @param personId Id of the person to be deleted
     * @return Array of modified families
     */
    public static Family[] deletePerson(Context context, String personId) {
        Person person = gc.getPerson(personId);
        Family[] families = unlinkPerson(person);
        Memory.setInstanceAndAllSubsequentToNull(person);
        gc.getPeople().remove(person);
        gc.createIndexes(); // Necessary
        String newRootId = U.trovaRadice(gc); // Todo dovrebbe essere: trovaParentePiuProssimo
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
