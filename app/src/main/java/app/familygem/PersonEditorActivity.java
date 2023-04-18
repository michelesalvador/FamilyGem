package app.familygem;

import static app.familygem.Global.gc;

import android.os.Bundle;
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

import app.familygem.constant.Gender;
import app.familygem.detail.EventActivity;
import app.familygem.detail.FamilyActivity;
import app.familygem.list.FamiliesFragment;
import app.familygem.util.TreeUtils;

public class PersonEditorActivity extends AppCompatActivity {

    Person p;
    String idIndi;
    String familyId;
    int relation;
    RadioButton sexMale;
    RadioButton sexFemale;
    RadioButton sexUnknown;
    int lastChecked;
    EditText birthDate;
    DateEditorLayout birthDateEditor;
    EditText birthPlace;
    SwitchCompat isDeadSwitch;
    EditText deathDate;
    DateEditorLayout deathDateEditor;
    EditText deathPlace;
    boolean nameFromPieces; //If the given name and surname come from the Given and Surname pieces, they must return there

    @Override
    protected void onCreate(Bundle bandolo) {
        super.onCreate(bandolo);
        U.ensureGlobalGedcomNotNull(gc);
        setContentView(R.layout.edita_individuo);
        Bundle bundle = getIntent().getExtras();
        idIndi = bundle.getString("idIndividuo");
        familyId = bundle.getString("idFamiglia");
        relation = bundle.getInt("relazione", 0);

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

        // Toggle sex radio buttons
        RadioGroup radioGroup = findViewById(R.id.radioGroup);
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

        // New person in kinship relationship
        if (relation > 0) {
            p = new Person();
            Person pivot = gc.getPerson(idIndi);
            String surname = null;
            // Brother's surname
            if (relation == 2) { // Sibling
                surname = U.surname(pivot);
                // Father's surname
            } else if (relation == 4) { // Child from DiagramFragment or ProfileActivity
                if (Gender.isMale(pivot))
                    surname = U.surname(pivot);
                else if (familyId != null) {
                    Family fam = gc.getFamily(familyId);
                    if (fam != null && !fam.getHusbands(gc).isEmpty())
                        surname = U.surname(fam.getHusbands(gc).get(0));
                }
            } else if (relation == 6) { // Child from FamilyActivity
                Family fam = gc.getFamily(familyId);
                if (!fam.getHusbands(gc).isEmpty())
                    surname = U.surname(fam.getHusbands(gc).get(0));
                else if (!fam.getChildren(gc).isEmpty())
                    surname = U.surname(fam.getChildren(gc).get(0));
            }
            ((EditText)findViewById(R.id.cognome)).setText(surname);
            // New unrelated person
        } else if (idIndi.equals("TIZIO_NUOVO")) {
            p = new Person();
            // Gets the data of an existing person to edit them
        } else {
            p = gc.getPerson(idIndi);
            // Given name and surname
            if (!p.getNames().isEmpty()) {
                String givenName = "";
                String surname = "";
                Name n = p.getNames().get(0);
                String value = n.getValue();
                if (value != null) {
                    givenName = value.replaceAll("/.*?/", "").trim(); // Removes surname between two "/"
                    if (value.indexOf('/') < value.lastIndexOf('/'))
                        surname = value.substring(value.indexOf('/') + 1, value.lastIndexOf('/')).trim();
                } else {
                    if (n.getGiven() != null) {
                        givenName = n.getGiven();
                        nameFromPieces = true;
                    }
                    if (n.getSurname() != null) {
                        surname = n.getSurname();
                        nameFromPieces = true;
                    }
                }
                ((EditText)findViewById(R.id.nome)).setText(givenName);
                ((EditText)findViewById(R.id.cognome)).setText(surname);
            }
            // Sex
            switch (Gender.getGender(p)) {
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
            // Birth and death
            for (EventFact fact : p.getEventsFacts()) {
                if (fact.getTag().equals("BIRT")) {
                    if (fact.getDate() != null)
                        birthDate.setText(fact.getDate().trim());
                    if (fact.getPlace() != null)
                        birthPlace.setText(fact.getPlace().trim());
                }
                if (fact.getTag().equals("DEAT")) {
                    isDeadSwitch.setChecked(true);
                    enableDeath();
                    if (fact.getDate() != null)
                        deathDate.setText(fact.getDate().trim());
                    if (fact.getPlace() != null)
                        deathPlace.setText(fact.getPlace().trim());
                }
            }
        }
        birthDateEditor.initialize(birthDate);
        isDeadSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (checked)
                enableDeath();
            else
                disableDeath();
        });
        deathDateEditor.initialize(deathDate);
        deathPlace.setOnEditorActionListener((vista, actionId, keyEvent) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE)
                save();
            return false;
        });

        // Toolbar
        ActionBar toolbar = getSupportActionBar();
        View actionBar = getLayoutInflater().inflate(R.layout.barra_edita, new LinearLayout(getApplicationContext()), false);
        actionBar.findViewById(R.id.edita_annulla).setOnClickListener(v -> onBackPressed());
        actionBar.findViewById(R.id.edita_salva).setOnClickListener(v -> save());
        toolbar.setCustomView(actionBar);
        toolbar.setDisplayShowCustomEnabled(true);
    }

    void disableDeath() {
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

    void enableDeath() {
        birthPlace.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        birthPlace.setNextFocusForwardId(R.id.data_morte);
        birthPlace.setOnEditorActionListener(null);
        findViewById(R.id.morte).setVisibility(View.VISIBLE);
    }

    void save() {
        U.ensureGlobalGedcomNotNull(gc); // A crash occurred because gc was null here

        // Name
        String givenName = ((EditText)findViewById(R.id.nome)).getText().toString().trim();
        String surname = ((EditText)findViewById(R.id.cognome)).getText().toString().trim();
        Name name;
        if (p.getNames().isEmpty()) {
            List<Name> names = new ArrayList<>();
            name = new Name();
            names.add(name);
            p.setNames(names);
        } else
            name = p.getNames().get(0);

        if (nameFromPieces) {
            name.setGiven(givenName);
            name.setSurname(surname);
        } else {
            name.setValue(givenName + " /" + surname + "/".trim());
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
            for (EventFact fact : p.getEventsFacts()) {
                if (fact.getTag().equals("SEX")) {
                    fact.setValue(chosenGender);
                    missingSex = false;
                }
            }
            if (missingSex) {
                EventFact sex = new EventFact();
                sex.setTag("SEX");
                sex.setValue(chosenGender);
                p.addEventFact(sex);
            }
            ProfileFactsFragment.updateMaritalRoles(p);
        } else { // Remove existing sex tag
            for (EventFact fact : p.getEventsFacts()) {
                if (fact.getTag().equals("SEX")) {
                    p.getEventsFacts().remove(fact);
                    break;
                }
            }
        }

        // Birth
        birthDateEditor.finishEditing();
        String date = birthDate.getText().toString().trim();
        String place = birthPlace.getText().toString().trim();
        boolean found = false;
        for (EventFact fact : p.getEventsFacts()) {
            if (fact.getTag().equals("BIRT")) {
                /* TODO:
                   if (date.isEmpty() && place.isEmpty() && tagAllEmpty(fact))
                      p.getEventsFacts().remove(fact);
                   More generally, delete a tag when it is empty */
                fact.setDate(date);
                fact.setPlace(place);
                EventActivity.cleanUpTag(fact);
                found = true;
            }
        }
        // If there is any data to save, creates the tag
        if (!found && (!date.isEmpty() || !place.isEmpty())) {
            EventFact birth = new EventFact();
            birth.setTag("BIRT");
            birth.setDate(date);
            birth.setPlace(place);
            EventActivity.cleanUpTag(birth);
            p.addEventFact(birth);
        }

        // Death
        deathDateEditor.finishEditing();
        date = deathDate.getText().toString().trim();
        place = deathPlace.getText().toString().trim();
        found = false;
        for (EventFact fact : p.getEventsFacts()) {
            if (fact.getTag().equals("DEAT")) {
                if (!isDeadSwitch.isChecked()) {
                    p.getEventsFacts().remove(fact);
                } else {
                    fact.setDate(date);
                    fact.setPlace(place);
                    EventActivity.cleanUpTag(fact);
                }
                found = true;
                break;
            }
        }
        if (!found && isDeadSwitch.isChecked()) {
            EventFact morte = new EventFact();
            morte.setTag("DEAT");
            morte.setDate(date);
            morte.setPlace(place);
            EventActivity.cleanUpTag(morte);
            p.addEventFact(morte);
        }

        // Finalization of new person
        Object[] modifications = {p, null}; // The null is used to receive a possible Family
        if (idIndi.equals("TIZIO_NUOVO") || relation > 0) {
            String newId = U.newID(gc, Person.class);
            p.setId(newId);
            gc.addPerson(p);
            if (Global.settings.getCurrentTree().root == null)
                Global.settings.getCurrentTree().root = newId;
            Global.settings.save();
            if (relation >= 5) { // Comes from FamilyActivity
                FamilyActivity.connect(p, gc.getFamily(familyId), relation);
                modifications[1] = gc.getFamily(familyId);
            } else if (relation > 0) // Comes from DiagramFragment o ProfileRelativesFragment
                modifications = addParent(idIndi, newId, familyId, relation, getIntent().getStringExtra("collocazione"));
        } else
            Global.indi = p.getId(); // To show the person then in DiagramFragment
        TreeUtils.INSTANCE.save(true, modifications);
        onBackPressed();
    }

    /**
     * Adds a new person in family relation with 'pivot', possibly within the given family.
     *
     * @param familyId Id of the target family. If it is null, a new family is created
     * @param placing  Summarizes how the family was identified and therefore what to do with the people involved
     */
    static Object[] addParent(String pivotId, String newId, String familyId, int relation, String placing) {
        Global.indi = pivotId;
        Person newPerson = gc.getPerson(newId);
        // A new family is created in which both pivot and newPerson end up
        if (placing != null && placing.startsWith("NUOVA_FAMIGLIA_DI")) { // Contains the ID of the parent to create a new family of
            pivotId = placing.substring(17); // The parent effectively becomes the pivot
            relation = relation == 2 ? 4 : relation; // Instead of a sibling to pivot, it is as if we were putting a child to the parent
        }
        // In ListOfPeopleActivity has been identified the family in which will end up the pivot
        else if (placing != null && placing.equals("FAMIGLIA_ESISTENTE")) {
            newId = null;
            newPerson = null;
        }
        // The new person is welcomed into the pivot family
        else if (familyId != null) {
            pivotId = null; // Pivot is already present in his family and should not be added again
        }
        Family family = familyId != null ? gc.getFamily(familyId) : FamiliesFragment.newFamily(true);
        Person pivot = gc.getPerson(pivotId);
        SpouseRef refSpouse1 = new SpouseRef(), refSposo2 = new SpouseRef();
        ChildRef refChild1 = new ChildRef(), refFiglio2 = new ChildRef();
        ParentFamilyRef parentFamilyRef = new ParentFamilyRef();
        SpouseFamilyRef spouseFamilyRef = new SpouseFamilyRef();
        parentFamilyRef.setRef(family.getId());
        spouseFamilyRef.setRef(family.getId());

        // Population of refs
        switch (relation) {
            case 1: // Parent
                refSpouse1.setRef(newId);
                refChild1.setRef(pivotId);
                if (newPerson != null) newPerson.addSpouseFamilyRef(spouseFamilyRef);
                if (pivot != null) pivot.addParentFamilyRef(parentFamilyRef);
                break;
            case 2: // Sibling
                refChild1.setRef(pivotId);
                refFiglio2.setRef(newId);
                if (pivot != null) pivot.addParentFamilyRef(parentFamilyRef);
                if (newPerson != null) newPerson.addParentFamilyRef(parentFamilyRef);
                break;
            case 3: // Partner
                refSpouse1.setRef(pivotId);
                refSposo2.setRef(newId);
                if (pivot != null) pivot.addSpouseFamilyRef(spouseFamilyRef);
                if (newPerson != null) newPerson.addSpouseFamilyRef(spouseFamilyRef);
                break;
            case 4: // Child
                refSpouse1.setRef(pivotId);
                refChild1.setRef(newId);
                if (pivot != null) pivot.addSpouseFamilyRef(spouseFamilyRef);
                if (newPerson != null) newPerson.addParentFamilyRef(parentFamilyRef);
        }

        if (refSpouse1.getRef() != null)
            addSpouse(family, refSpouse1);
        if (refSposo2.getRef() != null)
            addSpouse(family, refSposo2);
        if (refChild1.getRef() != null)
            family.addChild(refChild1);
        if (refFiglio2.getRef() != null)
            family.addChild(refFiglio2);

        if (relation == 1 || relation == 2) // It will bring up the selected family
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

    /**
     * Adds the spouse in a family: always and only on the basis of sex
     */
    public static void addSpouse(Family family, SpouseRef sr) {
        Person person = Global.gc.getPerson(sr.getRef());
        if (Gender.isFemale(person)) family.addWife(sr);
        else family.addHusband(sr);
    }
}