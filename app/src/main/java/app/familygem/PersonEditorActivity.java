package app.familygem;

import static app.familygem.Global.gc;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import org.folg.gedcom.model.ChildRef;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.ParentFamilyRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SpouseFamilyRef;
import org.folg.gedcom.model.SpouseRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.familygem.constant.Extra;
import app.familygem.constant.Gender;
import app.familygem.constant.Relation;
import app.familygem.util.EventUtilKt;
import app.familygem.util.FamilyUtil;
import app.familygem.util.TreeUtil;

public class PersonEditorActivity extends AppCompatActivity {

    private String personId; // ID of the person we want to edit. If null we have to create a new person.
    private String familyId;
    private Relation relation; // If not null is the relation between the pivot (personId) and the person we have to create
    private Person person; // The person to edit or create
    private EditText givenNameView;
    private EditText surnameView;
    private RadioGroup radioGroup;
    private RadioButton sexMale;
    private RadioButton sexFemale;
    private RadioButton sexUnknown;
    private int lastChecked;
    private EditText birthDate;
    private DateEditorLayout birthDateEditor;
    private EditText birthPlace;
    private SwitchCompat isDeadSwitch;
    private EditText deathDate;
    private DateEditorLayout deathDateEditor;
    private EditText deathPlace;
    private boolean fromFamilyActivity; // Previous activity was DetailActivity
    private boolean nameFromPieces; // If the given name and surname come from the Given and Surname pieces, they must return there
    private boolean surnameBefore; // The given name comes after the surname, e.g. "/Simpson/ Homer"
    private String nameSuffix; // Last part of the name, not editable here

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edita_individuo);
        Intent intent = getIntent();
        personId = intent.getStringExtra(Extra.PERSON_ID);
        familyId = intent.getStringExtra(Extra.FAMILY_ID);
        relation = (Relation)intent.getSerializableExtra(Extra.RELATION);
        fromFamilyActivity = intent.getBooleanExtra(Extra.FROM_FAMILY, false);
        nameSuffix = "";

        givenNameView = findViewById(R.id.nome);
        surnameView = findViewById(R.id.cognome);
        radioGroup = findViewById(R.id.radioGroup);
        sexMale = findViewById(R.id.sesso1);
        sexFemale = findViewById(R.id.sesso2);
        sexUnknown = findViewById(R.id.sesso3);
        birthDate = findViewById(R.id.data_nascita);
        birthDateEditor = findViewById(R.id.editore_data_nascita);
        birthPlace = findViewById(R.id.luogo_nascita);
        isDeadSwitch = findViewById(R.id.defunto);
        deathDate = findViewById(R.id.data_morte);
        deathDateEditor = findViewById(R.id.editore_data_morte);
        deathPlace = findViewById(R.id.luogo_morte);

        // Forbidden "/" character in name
        InputFilter[] filters = new InputFilter[]{(source, start, end, dest, dstart, dend) -> {
            if (source.toString().contains("/")) return source.toString().replaceAll("/", "");
            return null;
        }};
        givenNameView.setFilters(filters);
        surnameView.setFilters(filters);

        // Toggle sex radio buttons
        View.OnClickListener radioClick = radioButton -> {
            if (radioButton.getId() == lastChecked) {
                radioGroup.clearCheck();
            }
        };
        sexMale.setOnClickListener(radioClick);
        sexFemale.setOnClickListener(radioClick);
        sexUnknown.setOnClickListener(radioClick);
        radioGroup.setOnCheckedChangeListener((group, checked) -> {
            group.post(() -> {
                lastChecked = checked;
            });
        });

        disableDeath();

        isDeadSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (checked) enableDeath();
            else disableDeath();
        });
        deathPlace.setOnEditorActionListener((vista, actionId, keyEvent) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) save();
            return false;
        });

        // Toolbar
        ActionBar toolbar = getSupportActionBar();
        View actionBar = getLayoutInflater().inflate(R.layout.barra_edita, new LinearLayout(getApplicationContext()), false);
        actionBar.findViewById(R.id.edita_annulla).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        actionBar.findViewById(R.id.edita_salva).setOnClickListener(button -> {
            button.setEnabled(false); // To avoid multiple clicks
            save();
        });
        toolbar.setCustomView(actionBar);
        toolbar.setDisplayShowCustomEnabled(true);

        if (TreeUtil.INSTANCE.isGlobalGedcomOk(this::populateFields)) populateFields();
    }

    private void populateFields() {
        // New person in kinship relationship
        if (relation != null) {
            person = new Person();
            Person pivot = gc.getPerson(personId);
            String surname = null;
            // Sibling's surname
            if (relation == Relation.SIBLING) {
                surname = U.surname(pivot);
            } // Father's surname
            else if (relation == Relation.CHILD) {
                if (fromFamilyActivity) { // Child from FamilyActivity
                    Family fam = gc.getFamily(familyId);
                    if (!fam.getHusbands(gc).isEmpty())
                        surname = U.surname(fam.getHusbands(gc).get(0));
                    else if (!fam.getChildren(gc).isEmpty())
                        surname = U.surname(fam.getChildren(gc).get(0));
                } else { // Child from DiagramFragment or ProfileActivity
                    if (Gender.isMale(pivot))
                        surname = U.surname(pivot);
                    else if (familyId != null) {
                        Family fam = gc.getFamily(familyId);
                        if (fam != null && !fam.getHusbands(gc).isEmpty())
                            surname = U.surname(fam.getHusbands(gc).get(0));
                    }
                }
            }
            surnameView.setText(surname);
        } else if (personId == null) { // New unrelated person
            person = new Person();
        } else { // Gets the data of an existing person to edit them
            person = gc.getPerson(personId);
            // Given name and surname
            if (!person.getNames().isEmpty()) {
                String givenName = "";
                String surname = "";
                Name name = person.getNames().get(0);
                String value = name.getValue();
                if (value != null) {
                    value = value.trim();
                    if (value.indexOf('/') < value.lastIndexOf('/')) { // There is a surname between two "/"
                        if (value.indexOf('/') > 0) givenName = value.substring(0, value.indexOf('/')).trim();
                        surname = value.substring(value.indexOf('/') + 1, value.lastIndexOf('/')).trim();
                        nameSuffix = value.substring(value.lastIndexOf('/') + 1).trim();
                        if (givenName.isEmpty() && !nameSuffix.isEmpty()) { // Given name is after surname
                            givenName = nameSuffix;
                            surnameBefore = true;
                        }
                    } else {
                        givenName = value;
                    }
                } else {
                    if (name.getGiven() != null) {
                        givenName = name.getGiven();
                        nameFromPieces = true;
                    }
                    if (name.getSurname() != null) {
                        surname = name.getSurname();
                        nameFromPieces = true;
                    }
                }
                givenNameView.setText(givenName);
                surnameView.setText(surname);
            }
            // Sex
            switch (Gender.getGender(person)) {
                case MALE:
                    sexMale.setChecked(true);
                    break;
                case FEMALE:
                    sexFemale.setChecked(true);
                    break;
                case UNKNOWN:
                    sexUnknown.setChecked(true);
            }
            lastChecked = radioGroup.getCheckedRadioButtonId();
            // Birth
            for (EventFact fact : person.getEventsFacts()) {
                if (fact.getTag().equals("BIRT")) {
                    if (fact.getDate() != null)
                        birthDate.setText(fact.getDate().trim());
                    if (fact.getPlace() != null)
                        birthPlace.setText(fact.getPlace().trim());
                    break;
                }
            }
            // Death
            for (EventFact fact : person.getEventsFacts()) {
                if (fact.getTag().equals("DEAT")) {
                    isDeadSwitch.setChecked(true);
                    enableDeath();
                    if (fact.getDate() != null)
                        deathDate.setText(fact.getDate().trim());
                    if (fact.getPlace() != null)
                        deathPlace.setText(fact.getPlace().trim());
                    break;
                }
            }
        }
        birthDateEditor.initialize(birthDate);
        deathDateEditor.initialize(deathDate);
    }

    private void disableDeath() {
        findViewById(R.id.morte).setVisibility(View.GONE);
        birthPlace.setImeOptions(EditorInfo.IME_ACTION_DONE);
        birthPlace.setNextFocusForwardId(0);
        // Intercepts the 'Done' on the keyboard
        birthPlace.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE)
                save();
            return false;
        });
    }

    private void enableDeath() {
        birthPlace.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        birthPlace.setNextFocusForwardId(R.id.data_morte);
        birthPlace.setOnEditorActionListener(null);
        findViewById(R.id.morte).setVisibility(View.VISIBLE);
    }

    private void save() {
        if (!TreeUtil.INSTANCE.isGlobalGedcomOk(this::save)) return; // A crash occurred because gc was null here

        // Name
        String givenName = givenNameView.getText().toString().trim();
        String surname = surnameView.getText().toString().trim();
        Name name = null;
        if (person.getNames().isEmpty()) {
            if (!givenName.isEmpty() || !surname.isEmpty()) {
                List<Name> names = new ArrayList<>();
                name = new Name();
                names.add(name);
                person.setNames(names);
            }
        } else {
            name = person.getNames().get(0);
        }
        if (name != null) {
            if (nameFromPieces) {
                name.setGiven(givenName);
                name.setSurname(surname);
            } else {
                String value = "";
                if (!surname.isEmpty()) value = "/" + surname + "/";
                if (surnameBefore) value += " " + givenName;
                else {
                    value = givenName + " " + value;
                    if (!nameSuffix.isEmpty()) value += " " + nameSuffix;
                }
                name.setValue(value.trim());
            }
        }

        // Sex
        String chosenGender = null;
        if (sexMale.isChecked())
            chosenGender = "M";
        else if (sexFemale.isChecked())
            chosenGender = "F";
        else if (sexUnknown.isChecked())
            chosenGender = "U";
        if (chosenGender != null) {
            boolean missingSex = true;
            for (EventFact fact : person.getEventsFacts()) {
                if (fact.getTag().equals("SEX")) {
                    fact.setValue(chosenGender);
                    missingSex = false;
                }
            }
            if (missingSex) {
                EventFact sex = new EventFact();
                sex.setTag("SEX");
                sex.setValue(chosenGender);
                person.addEventFact(sex);
            }
            FamilyUtil.INSTANCE.updateSpouseRoles(person);
        } else { // Remove existing sex tag
            for (EventFact fact : person.getEventsFacts()) {
                if (fact.getTag().equals("SEX")) {
                    person.getEventsFacts().remove(fact);
                    break;
                }
            }
        }

        // Birth
        birthDateEditor.finishEditing();
        String date = birthDate.getText().toString().trim();
        String place = birthPlace.getText().toString().trim();
        boolean found = false;
        for (EventFact fact : person.getEventsFacts()) {
            if (fact.getTag().equals("BIRT")) {
                /* TODO: delete an EventsFact if it is completely empty
                    if (date.isEmpty() && place.isEmpty() && ...isEmpty())
                        person.getEventsFacts().remove(fact); */
                fact.setDate(date);
                fact.setPlace(place);
                EventUtilKt.cleanUpFields(fact);
                found = true;
                break;
            }
        }
        // If there is any data to save, creates the tag
        if (!found && (!date.isEmpty() || !place.isEmpty())) {
            EventFact birth = new EventFact();
            birth.setTag("BIRT");
            birth.setDate(date);
            birth.setPlace(place);
            EventUtilKt.cleanUpFields(birth);
            person.addEventFact(birth);
        }

        // Death
        deathDateEditor.finishEditing();
        date = deathDate.getText().toString().trim();
        place = deathPlace.getText().toString().trim();
        found = false;
        for (EventFact fact : person.getEventsFacts()) {
            if (fact.getTag().equals("DEAT")) {
                if (!isDeadSwitch.isChecked()) {
                    person.getEventsFacts().remove(fact);
                } else {
                    fact.setDate(date);
                    fact.setPlace(place);
                    EventUtilKt.cleanUpFields(fact);
                }
                found = true;
                break;
            }
        }
        if (!found && isDeadSwitch.isChecked()) {
            EventFact death = new EventFact();
            death.setTag("DEAT");
            death.setDate(date);
            death.setPlace(place);
            EventUtilKt.cleanUpFields(death);
            person.addEventFact(death);
        }

        // Finalization of new person
        Object[] modifications = {person, null}; // The null is used to receive a possible Family
        if (personId == null || relation != null) {
            String newId = U.newID(gc, Person.class);
            person.setId(newId);
            gc.addPerson(person);
            if (Global.settings.getCurrentTree().root == null)
                Global.settings.getCurrentTree().root = newId;
            Global.settings.save();
            if (fromFamilyActivity) { // Comes from FamilyActivity
                Family family = gc.getFamily(familyId);
                FamilyUtil.INSTANCE.linkPerson(person, family, relation);
                modifications[1] = family;
            } else if (relation != null) // Comes from DiagramFragment or profile.RelativesFragment
                modifications = addRelative(personId, newId, familyId, relation, getIntent().getStringExtra(Extra.DESTINATION));
        } else
            Global.indi = person.getId(); // To show the person then in DiagramFragment
        TreeUtil.INSTANCE.save(true, modifications);
        getOnBackPressedDispatcher().onBackPressed();
    }

    /**
     * Adds a new person in family relation with 'pivot', possibly within the given family.
     *
     * @param familyId    Id of the target family. If it is null, a new family is created
     * @param destination Summarizes how the family was identified and therefore what to do with the people involved
     * @return An array of modified records
     */
    public static Object[] addRelative(String pivotId, String newId, String familyId, Relation relation, String destination) {
        Global.indi = pivotId;
        Person newPerson = gc.getPerson(newId);
        // A new family is created in which both pivot and newPerson end up
        if (destination != null && destination.startsWith("NEW_FAMILY_OF")) { // Contains the ID of the parent to create a new family of
            pivotId = destination.substring(13); // The parent actually becomes the pivot
            // Instead of a sibling to pivot, it is as if we were putting a child to the parent
            relation = relation == Relation.SIBLING ? Relation.CHILD : relation;
        }
        // In PersonsFragment has been identified the family in which will end up the pivot
        else if (destination != null && destination.equals("EXISTING_FAMILY")) {
            newId = null;
            newPerson = null;
        }
        // The new person is welcomed into the pivot family
        else if (familyId != null) {
            pivotId = null; // Pivot is already present in his family and should not be added again
        }
        Family family = familyId != null ? gc.getFamily(familyId) : FamilyUtil.INSTANCE.createNewFamily();
        Person pivot = gc.getPerson(pivotId);
        SpouseRef spouseRef1 = new SpouseRef(), spouseRef2 = new SpouseRef();
        ChildRef childRef1 = new ChildRef(), childRef2 = new ChildRef();
        ParentFamilyRef parentFamilyRef = new ParentFamilyRef();
        SpouseFamilyRef spouseFamilyRef = new SpouseFamilyRef();
        parentFamilyRef.setRef(family.getId());
        spouseFamilyRef.setRef(family.getId());

        // Populates the refs
        switch (relation) {
            case PARENT:
                spouseRef1.setRef(newId);
                childRef1.setRef(pivotId);
                if (newPerson != null) newPerson.addSpouseFamilyRef(spouseFamilyRef);
                if (pivot != null) pivot.addParentFamilyRef(parentFamilyRef);
                break;
            case SIBLING:
                childRef1.setRef(pivotId);
                childRef2.setRef(newId);
                if (pivot != null) pivot.addParentFamilyRef(parentFamilyRef);
                if (newPerson != null) newPerson.addParentFamilyRef(parentFamilyRef);
                break;
            case PARTNER:
                spouseRef1.setRef(pivotId);
                spouseRef2.setRef(newId);
                if (pivot != null) pivot.addSpouseFamilyRef(spouseFamilyRef);
                if (newPerson != null) newPerson.addSpouseFamilyRef(spouseFamilyRef);
                break;
            case CHILD:
                spouseRef1.setRef(pivotId);
                childRef1.setRef(newId);
                if (pivot != null) pivot.addSpouseFamilyRef(spouseFamilyRef);
                if (newPerson != null) newPerson.addParentFamilyRef(parentFamilyRef);
        }

        if (spouseRef1.getRef() != null)
            FamilyUtil.INSTANCE.addSpouse(family, spouseRef1);
        if (spouseRef2.getRef() != null)
            FamilyUtil.INSTANCE.addSpouse(family, spouseRef2);
        if (childRef1.getRef() != null)
            family.addChild(childRef1);
        if (childRef2.getRef() != null)
            family.addChild(childRef2);

        if (relation == Relation.PARENT || relation == Relation.SIBLING) // It will bring up the selected family
            Global.familyNum = gc.getPerson(Global.indi).getParentFamilies(gc).indexOf(family);
        else
            Global.familyNum = 0; // Otherwise resets it

        Set<Object> modified = new HashSet<>();
        if (pivot != null && newPerson != null)
            Collections.addAll(modified, family, pivot, newPerson);
        else if (pivot != null)
            Collections.addAll(modified, family, pivot);
        else if (newPerson != null)
            Collections.addAll(modified, family, newPerson);
        return modified.toArray();
    }
}
