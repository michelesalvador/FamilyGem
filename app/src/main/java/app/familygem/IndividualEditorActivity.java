package app.familygem;

import android.os.Bundle;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import static app.familygem.Global.gc;

public class IndividualEditorActivity extends AppCompatActivity {

	Person p;
	String idIndi;
	String familyId;
	int relationship;
	RadioButton sexMale;
	RadioButton sexFemale;
	RadioButton sexUnknown;
	int lastChecked;
	EditText dateOfBirth;
	PublisherDateLinearLayout publisherDateLinearLayoutDOB; //DOB = Date of Birth
	EditText birthplaceEditText;
	SwitchCompat isDeadSwitch;
	EditText dateOfDeath;
	PublisherDateLinearLayout publisherDateLinearLayoutDOD; //DOD = Date of Death
	EditText deathPlace;
	boolean nameFromPieces; //If the name / surname comes from the Given and Surname pieces, they must return there // Se il nome/cognome vengono dai pieces Given e Surname, lì devono tornare

	@Override
	protected void onCreate(Bundle bandolo) {
		super.onCreate(bandolo);
		U.ensureGlobalGedcomNotNull(gc);
		setContentView( R.layout.edita_individuo );
		Bundle bundle = getIntent().getExtras();
		idIndi = bundle.getString("idIndividuo");
		familyId = bundle.getString("idFamiglia");
		relationship = bundle.getInt("relazione", 0 );

		sexMale = findViewById(R.id.sesso1);
		sexFemale = findViewById(R.id.sesso2);
		sexUnknown = findViewById(R.id.sesso3);
		dateOfBirth = findViewById( R.id.data_nascita );
		publisherDateLinearLayoutDOB = findViewById(R.id.editore_data_nascita);
		birthplaceEditText = findViewById(R.id.luogo_nascita);
		isDeadSwitch = findViewById( R.id.defunto );
		dateOfDeath = findViewById( R.id.data_morte );
		publisherDateLinearLayoutDOD = findViewById( R.id.editore_data_morte );
		deathPlace = findViewById( R.id.luogo_morte );

		// Toggle sex radio buttons
		RadioGroup radioGroup = findViewById(R.id.radioGroup);
		View.OnClickListener radioClick = radioButton -> {
			if( radioButton.getId() == lastChecked ) {
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

		// New individual in kinship relationship
		if( relationship > 0 ) {
			p = new Person();
			Person pivot = gc.getPerson(idIndi);
			String surname = null;
			// Brother's surname
			if( relationship == 2 ) { // = brother
				surname = U.surname(pivot);
			// Father's surname
			} else if( relationship == 4 ) { // = child from Diagram or Individual // = figlio da Diagramma o Individuo
				if( Gender.isMale(pivot) )
					surname = U.surname(pivot);
				else if( familyId != null ) {
					Family fam = gc.getFamily(familyId);
					if( fam != null && !fam.getHusbands(gc).isEmpty() )
						surname = U.surname(fam.getHusbands(gc).get(0));
				}
			} else if( relationship == 6 ) { // = child of Family(Activity?) // = figlio da Famiglia
				Family fam = gc.getFamily(familyId);
				if( !fam.getHusbands(gc).isEmpty() )
					surname = U.surname(fam.getHusbands(gc).get(0));
				else if( !fam.getChildren(gc).isEmpty() )
					surname = U.surname(fam.getChildren(gc).get(0));
			}
			((EditText)findViewById(R.id.cognome)).setText(surname);
		// New disconnected individual
		} else if( idIndi.equals("TIZIO_NUOVO") ) {
			p = new Person();
		// Upload the data of an existing individual to modify
		} else {
			p = gc.getPerson(idIndi);
			// Name and surname
			if( !p.getNames().isEmpty() ) {
				String name = "";
				String surname = "";
				Name n = p.getNames().get(0);
				String epithet = n.getValue();
				if( epithet != null ) {
					name = epithet.replaceAll( "/.*?/", "" ).trim(); //removes surname '/.../' // rimuove il cognome '/.../'
					if( epithet.indexOf('/') < epithet.lastIndexOf('/') )
						surname = epithet.substring( epithet.indexOf('/') + 1, epithet.lastIndexOf('/') ).trim();
				} else {
					if( n.getGiven() != null ) {
						name = n.getGiven();
						nameFromPieces = true;
					}
					if( n.getSurname() != null ) {
						surname = n.getSurname();
						nameFromPieces = true;
					}
				}
				((EditText)findViewById( R.id.nome )).setText( name );
				((EditText)findViewById( R.id.cognome )).setText( surname );
			}
			// Sex
			switch( Gender.getGender(p) ) {
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
			for( EventFact fact : p.getEventsFacts() ) {
				if( fact.getTag().equals("BIRT") ) {
					if( fact.getDate() != null )
						dateOfBirth.setText( fact.getDate().trim() );
					if( fact.getPlace() != null )
						birthplaceEditText.setText(fact.getPlace().trim());
				}
				if( fact.getTag().equals("DEAT") ) {
					isDeadSwitch.setChecked(true);
					activateDeathSwitch();
					if( fact.getDate() != null )
						dateOfDeath.setText( fact.getDate().trim() );
					if( fact.getPlace() != null )
						deathPlace.setText(fact.getPlace().trim());
				}
			}
		}
		publisherDateLinearLayoutDOB.initialize(dateOfBirth);
		isDeadSwitch.setOnCheckedChangeListener( (button, checked) -> {
			if (checked)
				activateDeathSwitch();
			else
				disableDeath();
		});
		publisherDateLinearLayoutDOD.initialize(dateOfDeath);
		deathPlace.setOnEditorActionListener( (vista, actionId, keyEvent) -> {
			if( actionId == EditorInfo.IME_ACTION_DONE )
				save();
			return false;
		});

		// Toolbar
		ActionBar toolbar = getSupportActionBar();
		View toolbarAction = getLayoutInflater().inflate( R.layout.barra_edita, new LinearLayout(getApplicationContext()), false);
		toolbarAction.findViewById( R.id.edita_annulla ).setOnClickListener( v -> onBackPressed() );
		toolbarAction.findViewById(R.id.edita_salva).setOnClickListener( v -> save() );
		toolbar.setCustomView( toolbarAction );
		toolbar.setDisplayShowCustomEnabled( true );
	}

	void disableDeath() {
		findViewById(R.id.morte).setVisibility(View.GONE);
		birthplaceEditText.setImeOptions(EditorInfo.IME_ACTION_DONE);
		birthplaceEditText.setNextFocusForwardId(0);
		// Intercept the 'Done' on the keyboard
		birthplaceEditText.setOnEditorActionListener((view, action, event) -> {
			if( action == EditorInfo.IME_ACTION_DONE )
				save();
			return false;
		});
	}

	void activateDeathSwitch() {
		birthplaceEditText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
		birthplaceEditText.setNextFocusForwardId(R.id.data_morte);
		birthplaceEditText.setOnEditorActionListener(null);
		findViewById(R.id.morte).setVisibility(View.VISIBLE);
	}

	void save() {
		U.ensureGlobalGedcomNotNull(gc); //A crash occurred because gc was null here

		// Name
		String nameString = ((EditText)findViewById(R.id.nome)).getText().toString().trim();
		String surname = ((EditText)findViewById(R.id.cognome)).getText().toString().trim();
		Name name;
		if( p.getNames().isEmpty() ) {
			List<Name> names = new ArrayList<>();
			name = new Name();
			names.add(name);
			p.setNames(names);
		} else
			name = p.getNames().get(0);

		if(nameFromPieces) {
			name.setGiven(nameString);
			name.setSurname(surname);
		} else {
			name.setValue(nameString + " /" + surname + "/".trim());
		}

		// Sex
		String chosenGender = null;
		if( sexMale.isChecked() )
			chosenGender = "M";
		else if( sexFemale.isChecked() )
			chosenGender = "F";
		else if( sexUnknown.isChecked() )
			chosenGender = "U";
		if( chosenGender != null ) {
			boolean missingSex = true;
			for( EventFact fact : p.getEventsFacts() ) {
				if( fact.getTag().equals("SEX") ) {
					fact.setValue(chosenGender);
					missingSex = false;
				}
			}
			if( missingSex ) {
				EventFact sex = new EventFact();
				sex.setTag("SEX");
				sex.setValue(chosenGender);
				p.addEventFact(sex);
			}
			IndividualEventsFragment.updateMaritalRoles(p);
		} else { // Remove existing sex tag
			for( EventFact fact : p.getEventsFacts() ) {
				if( fact.getTag().equals("SEX") ) {
					p.getEventsFacts().remove(fact);
					break;
				}
			}
		}

		// Birth
		publisherDateLinearLayoutDOB.encloseInParentheses();
		String data = dateOfBirth.getText().toString().trim();
		String location = birthplaceEditText.getText().toString().trim();
		boolean found = false;
		for (EventFact fact : p.getEventsFacts()) {
			if( fact.getTag().equals("BIRT") ) {
					/* TODO:
					    if (data.isEmpty () && place.isEmpty () && tagAllEmpty (done))
							p.getEventsFacts (). remove (done);
						more generally, delete a tag when it is empty

					    if( data.isEmpty() && luogo.isEmpty() && tagTuttoVuoto(fatto) )
					    	p.getEventsFacts().remove(fatto);
					    più in generale, eliminare un tag quando è vuoto */
				fact.setDate( data );
				fact.setPlace( location );
				EventActivity.cleanUpTag( fact );
				found = true;
			}
		}
		// If there is any data to save, create the tag
		if( !found && ( !data.isEmpty() || !location.isEmpty() ) ) {
			EventFact birth = new EventFact();
			birth.setTag( "BIRT" );
			birth.setDate( data );
			birth.setPlace( location );
			EventActivity.cleanUpTag( birth );
			p.addEventFact( birth );
		}

		// Death
		publisherDateLinearLayoutDOD.encloseInParentheses();
		data = dateOfDeath.getText().toString().trim();
		location = deathPlace.getText().toString().trim();
		found = false;
		for( EventFact fact : p.getEventsFacts() ) {
			if( fact.getTag().equals("DEAT") ) {
				if( !isDeadSwitch.isChecked() ) {
					p.getEventsFacts().remove(fact);
				} else {
					fact.setDate( data );
					fact.setPlace( location );
					EventActivity.cleanUpTag( fact );
				}
				found = true;
				break;
			}
		}
		if( !found && isDeadSwitch.isChecked() ) {
			EventFact morte = new EventFact();
			morte.setTag( "DEAT" );
			morte.setDate( data );
			morte.setPlace( location );
			EventActivity.cleanUpTag( morte );
			p.addEventFact( morte );
		}

		// Finalization of new individual
		Object[] modifications = { p, null }; // the null is used to accommodate a possible Family
		if( idIndi.equals("TIZIO_NUOVO") || relationship > 0 ) {
			String newId = U.newID( gc, Person.class );
			p.setId( newId );
			gc.addPerson( p );
			if( Global.settings.getCurrentTree().root == null )
				Global.settings.getCurrentTree().root = newId;
			Global.settings.save();
			if( relationship >= 5 ) { // comes from Family(Activity)
				FamilyActivity.connect( p, gc.getFamily(familyId), relationship);
				modifications[1] = gc.getFamily(familyId);
			} else if( relationship > 0 ) // comes from Family Diagram or Individual
				modifications = addParent( idIndi, newId, familyId, relationship, getIntent().getStringExtra("collocazione") );
		} else
			Global.indi = p.getId(); //to proudly (prominently?) show it in Diagram // per mostrarlo orgogliosi in Diagramma
		U.save(true, modifications);
		onBackPressed();
	}

	/**
	 * Aggiunge un nuovo individuo in relazione di parentela con 'perno', eventualmente all'interno della famiglia fornita.
	 * @param familyId Id della famiglia di destinazione. Se è null si crea una nuova famiglia
	 * @param collection Sintetizza come è stata individuata la famiglia e quindi cosa fare delle persone coinvolte
	 *
	 *
	 * Adds a new kinship individual with 'pivot', possibly within the given family.
	 * @param familyId Id of the target family. If it is null, a new family is created
	 * @param collection Summarizes how the family was identified and therefore what to do with the people involved
 	 */
	static Object[] addParent(String pivotId, String newId, String familyId, int relationship, String collection) {
		Global.indi = pivotId;
		Person newPerson = gc.getPerson( newId );
		// A new family is created in which both Pin and New end up
			if( collection != null && collection.startsWith("NUOVA_FAMIGLIA_DI") ) { // Contains the id of the parent to create a new family for
				pivotId = collection.substring(17); // the parent effectively becomes the pivot
			relationship = relationship == 2 ? 4 : relationship; //instead of a pivotal sibling, it is as if we were putting a child to the parent // anziché un fratello a perno, è come se mettessimo un figlio al genitore
		}
		//The family in which the pivot will end up has been identified in ListOfPeopleActivity // In Anagrafe è stata individuata la famiglia in cui finirà perno
		else if( collection != null && collection.equals("FAMIGLIA_ESISTENTE") ) {
			newId = null;
			newPerson = null;
		}
		// New is welcomed into the pivot family
		else if( familyId != null ) {
			pivotId = null; // pivot is already present in his family and should not be added again
		}
		Family family = familyId != null ? gc.getFamily(familyId) : ChurchFragment.newFamily(true);;
		Person pivot = gc.getPerson( pivotId );
		SpouseRef refSpouse1 = new SpouseRef(), refSposo2 = new SpouseRef();
		ChildRef refChild1 = new ChildRef(), refFiglio2 = new ChildRef();
		ParentFamilyRef parentFamilyRef = new ParentFamilyRef();
		SpouseFamilyRef spouseFamilyRef = new SpouseFamilyRef();
		parentFamilyRef.setRef( family.getId() );
		spouseFamilyRef.setRef( family.getId() );

		// Population of refs
		switch (relationship) {
			case 1: // Parent
				refSpouse1.setRef(newId);
				refChild1.setRef(pivotId);
				if (newPerson != null) newPerson.addSpouseFamilyRef( spouseFamilyRef );
				if (pivot != null) pivot.addParentFamilyRef( parentFamilyRef );
				break;
			case 2: // Brother
				refChild1.setRef(pivotId);
				refFiglio2.setRef(newId);
				if (pivot != null) pivot.addParentFamilyRef( parentFamilyRef );
				if (newPerson != null) newPerson.addParentFamilyRef( parentFamilyRef );
				break;
			case 3: // Spouse
				refSpouse1.setRef(pivotId);
				refSposo2.setRef(newId);
				if (pivot != null) pivot.addSpouseFamilyRef( spouseFamilyRef );
				if (newPerson != null) newPerson.addSpouseFamilyRef( spouseFamilyRef );
				break;
			case 4: // Child
				refSpouse1.setRef(pivotId);
				refChild1.setRef(newId);
				if (pivot != null) pivot.addSpouseFamilyRef( spouseFamilyRef );
				if (newPerson != null) newPerson.addParentFamilyRef( parentFamilyRef );
		}

		if( refSpouse1.getRef() != null )
			addSpouse(family, refSpouse1);
		if( refSposo2.getRef() != null )
			addSpouse(family, refSposo2);
		if( refChild1.getRef() != null )
			family.addChild(refChild1);
		if( refFiglio2.getRef() != null )
			family.addChild(refFiglio2);

		if( relationship == 1 || relationship == 2 ) // It will bring up the selected family
			Global.familyNum = gc.getPerson(Global.indi).getParentFamilies(gc).indexOf(family);
		else
			Global.familyNum = 0; // eventually reset

		Set<Object> transformed = new HashSet<>();
		if( pivot != null && newPerson != null )
			Collections.addAll(transformed, family, pivot, newPerson);
		else if( pivot != null )
			Collections.addAll(transformed, family, pivot);
		else if( newPerson != null )
			Collections.addAll(transformed, family, newPerson);
		return transformed.toArray();
	}

	/**
	 * Adds the spouse in a family: always and only on the basis of sex
	 * */
	public static void addSpouse(Family family, SpouseRef sr) {
		Person person = Global.gc.getPerson(sr.getRef());
		if( Gender.isFemale(person) ) family.addWife(sr);
		else family.addHusband(sr);
	}
}