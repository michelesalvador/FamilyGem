package app.familygem.detail;

import static app.familygem.Global.gc;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import org.folg.gedcom.model.ChildRef;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.ParentFamilyRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SpouseFamilyRef;
import org.folg.gedcom.model.SpouseRef;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import app.familygem.DetailActivity;
import app.familygem.Global;
import app.familygem.Memory;
import app.familygem.PersonEditorActivity;
import app.familygem.ProfileActivity;
import app.familygem.R;
import app.familygem.U;
import app.familygem.constant.Gender;
import app.familygem.constant.Relation;
import app.familygem.constant.Status;
import app.familygem.util.TreeUtils;

public class FamilyActivity extends DetailActivity {

    Family family;
    static String[] lineageTexts = {U.s(R.string.undefined) + " (" + U.s(R.string.birth).toLowerCase() + ")",
            U.s(R.string.birth), U.s(R.string.adopted), U.s(R.string.foster)};
    static String[] lineageTypes = {null, "birth", "adopted", "foster"};

    @Override
    protected void format() {
        setTitle(R.string.family);
        family = (Family)cast(Family.class);
        placeSlug("FAM", family.getId());
        for (SpouseRef husbandRef : family.getHusbandRefs())
            addMember(husbandRef, Relation.PARTNER);
        for (SpouseRef wifeRef : family.getWifeRefs())
            addMember(wifeRef, Relation.PARTNER);
        for (ChildRef childRef : family.getChildRefs())
            addMember(childRef, Relation.CHILD);
        for (EventFact ef : family.getEventsFacts()) {
            place(writeEventTitle(family, ef), ef);
        }
        placeExtensions(family);
        U.placeNotes(box, family, true);
        U.placeMedia(box, family, true);
        U.placeSourceCitations(box, family);
        U.placeChangeDate(box, family.getChange());
    }

    /**
     * Adds a member to the family.
     */
    void addMember(SpouseRef sr, Relation relation) {
        Person p = sr.getPerson(gc);
        if (p == null) return;
        View personView = U.placeIndividual(box, p, getRole(p, family, relation, true) + writeLineage(p, family));
        personView.setTag(R.id.tag_object, p); // For the context menu in DetailActivity

        /* Ref inside the individual towards the family.
           If the same person is present several times with the same role (partner or child) in the same family
           the 2 following loops identify in the person the first FamilyRef (INDI.FAMS / INDI.FAMC) that refers to that family.
           They do not take the FamilyRef with the same index as the corresponding Ref in the family (FAM.HUSB / FAM.WIFE).
           It could be a problem in case of 'Unlink person', but not anymore because all the Family content is reloaded.
         */
        if (relation == Relation.PARTNER) {
            for (SpouseFamilyRef sfr : p.getSpouseFamilyRefs())
                if (family.getId().equals(sfr.getRef())) {
                    personView.setTag(R.id.tag_spouse_family_ref, sfr);
                    break;
                }
        } else if (relation == Relation.CHILD) {
            for (ParentFamilyRef pfr : p.getParentFamilyRefs())
                if (family.getId().equals(pfr.getRef())) {
                    personView.setTag(R.id.tag_spouse_family_ref, pfr);
                    break;
                }
        }
        personView.setTag(R.id.tag_spouse_ref, sr);
        registerForContextMenu(personView);
        personView.setOnClickListener(v -> {
            List<Family> parentFam = p.getParentFamilies(gc);
            List<Family> spouseFam = p.getSpouseFamilies(gc);
            // A spouse with one or more families in which he is a child
            if (relation == Relation.PARTNER && !parentFam.isEmpty()) {
                U.whichParentsToShow(this, p, 2);
            } // A child with one or more families in which he is a partner
            else if (relation == Relation.CHILD && !p.getSpouseFamilies(gc).isEmpty()) {
                U.whichSpousesToShow(this, p, null);
            } // An unmarried child who has multiple parental families
            else if (parentFam.size() > 1) {
                if (parentFam.size() == 2) { // Swaps between the 2 parental families
                    Global.indi = p.getId();
                    Global.familyNum = parentFam.indexOf(family) == 0 ? 1 : 0;
                    Memory.replaceLeader(parentFam.get(Global.familyNum));
                    recreate();
                } else // More than two families
                    U.whichParentsToShow(this, p, 2);
            } // A spouse without parents but with multiple spouse families
            else if (spouseFam.size() > 1) {
                if (spouseFam.size() == 2) { // Swaps between the 2 spouse families
                    Global.indi = p.getId();
                    Family otherFamily = spouseFam.get(spouseFam.indexOf(family) == 0 ? 1 : 0);
                    Memory.replaceLeader(otherFamily);
                    recreate();
                } else
                    U.whichSpousesToShow(this, p, null);
            } else {
                Memory.setLeader(p);
                startActivity(new Intent(this, ProfileActivity.class));
            }
        });
        if (oneFamilyMember == null)
            oneFamilyMember = p;
    }

    /**
     * Finds the role of a person from their relation with a family.
     *
     * @param family        Can be null
     * @param respectFamily The role to find is relative to the family (it becomes 'parent' when there are children)
     * @return A descriptor text of the person's role
     */
    public static String getRole(Person person, Family family, Relation relation, boolean respectFamily) {
        int role = 0;
        if (respectFamily && relation == Relation.PARTNER && family != null && !family.getChildRefs().isEmpty())
            relation = Relation.PARENT;
        Status status = Status.getStatus(family);
        if (Gender.isMale(person)) {
            switch (relation) {
                case PARENT:
                    role = R.string.father;
                    break;
                case SIBLING:
                    role = R.string.brother;
                    break;
                case HALF_SIBLING:
                    role = R.string.half_brother;
                    break;
                case PARTNER:
                    switch (status) {
                        case MARRIED:
                            role = R.string.husband;
                            break;
                        case DIVORCED:
                            role = R.string.ex_husband;
                            break;
                        case SEPARATED:
                            role = R.string.ex_male_partner;
                            break;
                        default:
                            role = R.string.male_partner;
                    }
                    break;
                case CHILD:
                    role = R.string.son;
            }
        } else if (Gender.isFemale(person)) {
            switch (relation) {
                case PARENT:
                    role = R.string.mother;
                    break;
                case SIBLING:
                    role = R.string.sister;
                    break;
                case HALF_SIBLING:
                    role = R.string.half_sister;
                    break;
                case PARTNER:
                    switch (status) {
                        case MARRIED:
                            role = R.string.wife;
                            break;
                        case DIVORCED:
                            role = R.string.ex_wife;
                            break;
                        case SEPARATED:
                            role = R.string.ex_female_partner;
                            break;
                        default:
                            role = R.string.female_partner;
                    }
                    break;
                case CHILD:
                    role = R.string.daughter;
            }
        } else { // Neutral roles
            switch (relation) {
                case PARENT:
                    role = R.string.parent;
                    break;
                case SIBLING:
                    role = R.string.sibling;
                    break;
                case HALF_SIBLING:
                    role = R.string.half_sibling;
                    break;
                case PARTNER:
                    switch (status) {
                        case MARRIED:
                            role = R.string.spouse;
                            break;
                        case DIVORCED:
                            role = R.string.ex_spouse;
                            break;
                        case SEPARATED:
                            role = R.string.ex_partner;
                            break;
                        default:
                            role = R.string.partner;
                    }
                    break;
                case CHILD:
                    role = R.string.child;
            }
        }
        return Global.context.getString(role);
    }

    /**
     * Finds the ParentFamilyRef of a child person in a family.
     */
    public static ParentFamilyRef findParentFamilyRef(Person person, Family family) {
        for (ParentFamilyRef parentFamilyRef : person.getParentFamilyRefs()) {
            if (parentFamilyRef.getRef().equals(family.getId())) {
                return parentFamilyRef;
            }
        }
        return null;
    }

    /**
     * Composes the lineage definition to be added to the role in the person card.
     */
    public static String writeLineage(Person person, Family family) {
        ParentFamilyRef parentFamilyRef = findParentFamilyRef(person, family);
        if (parentFamilyRef != null) {
            int actual = Arrays.asList(lineageTypes).indexOf(parentFamilyRef.getRelationshipType());
            if (actual > 0)
                return " – " + lineageTexts[actual];
        }
        return "";
    }

    /**
     * Displays the alert dialog to choose the lineage of one person.
     */
    public static void chooseLineage(Context context, Person person, Family family) {
        ParentFamilyRef parentFamilyRef = findParentFamilyRef(person, family);
        if (parentFamilyRef != null) {
            int actual = Arrays.asList(lineageTypes).indexOf(parentFamilyRef.getRelationshipType());
            new AlertDialog.Builder(context).setSingleChoiceItems(lineageTexts, actual, (dialog, i) -> {
                parentFamilyRef.setRelationshipType(lineageTypes[i]);
                dialog.dismiss();
                if (context instanceof ProfileActivity)
                    ((ProfileActivity)context).refresh();
                else if (context instanceof FamilyActivity)
                    ((FamilyActivity)context).refresh();
                TreeUtils.INSTANCE.save(true, person);
            }).show();
        }
    }

    /**
     * Connects a person to a family as parent or child.
     */
    public static void connect(Person person, Family family, Relation relation) {
        switch (relation) {
            case PARTNER:
                // The person Ref inside the family
                SpouseRef spouseRef = new SpouseRef();
                spouseRef.setRef(person.getId());
                PersonEditorActivity.addSpouse(family, spouseRef);
                // The family Ref inside the person
                SpouseFamilyRef spouseFamilyRef = new SpouseFamilyRef();
                spouseFamilyRef.setRef(family.getId());
                person.addSpouseFamilyRef(spouseFamilyRef);
                break;
            case CHILD:
                ChildRef childRef = new ChildRef();
                childRef.setRef(person.getId());
                family.addChild(childRef);
                ParentFamilyRef parentFamilyRef = new ParentFamilyRef();
                parentFamilyRef.setRef(family.getId());
                person.addParentFamilyRef(parentFamilyRef);
        }
    }

    /**
     * Removes the single SpouseFamilyRef from the person and the corresponding SpouseRef from the family.
     */
    public static void disconnect(SpouseFamilyRef sfr, SpouseRef sr) {
        // Inside the person towards the family
        Person person = sr.getPerson(gc);
        person.getSpouseFamilyRefs().remove(sfr);
        if (person.getSpouseFamilyRefs().isEmpty())
            person.setSpouseFamilyRefs(null); // Any empty list is deleted
        person.getParentFamilyRefs().remove(sfr);
        if (person.getParentFamilyRefs().isEmpty())
            person.setParentFamilyRefs(null);
        // Inside the family towards the person
        Family fam = sfr.getFamily(gc);
        fam.getHusbandRefs().remove(sr);
        if (fam.getHusbandRefs().isEmpty())
            fam.setHusbandRefs(null);
        fam.getWifeRefs().remove(sr);
        if (fam.getWifeRefs().isEmpty())
            fam.setWifeRefs(null);
        fam.getChildRefs().remove(sr);
        if (fam.getChildRefs().isEmpty())
            fam.setChildRefs(null);
    }

    /**
     * Unlinks a person from a family.
     */
    public static void disconnect(String indiId, Family family) {
        // Removes the refs of the person inside the family
        Iterator<SpouseRef> spouseRefs = family.getHusbandRefs().iterator();
        while (spouseRefs.hasNext())
            if (spouseRefs.next().getRef().equals(indiId))
                spouseRefs.remove();
        if (family.getHusbandRefs().isEmpty())
            family.setHusbandRefs(null); // Deletes any empty list

        spouseRefs = family.getWifeRefs().iterator();
        while (spouseRefs.hasNext())
            if (spouseRefs.next().getRef().equals(indiId))
                spouseRefs.remove();
        if (family.getWifeRefs().isEmpty())
            family.setWifeRefs(null);

        Iterator<ChildRef> childRefs = family.getChildRefs().iterator();
        while (childRefs.hasNext())
            if (childRefs.next().getRef().equals(indiId))
                childRefs.remove();
        if (family.getChildRefs().isEmpty())
            family.setChildRefs(null);

        // Removes family refs inside the person
        Person person = gc.getPerson(indiId);
        Iterator<SpouseFamilyRef> iterSfr = person.getSpouseFamilyRefs().iterator();
        while (iterSfr.hasNext())
            if (iterSfr.next().getRef().equals(family.getId()))
                iterSfr.remove();
        if (person.getSpouseFamilyRefs().isEmpty())
            person.setSpouseFamilyRefs(null);

        Iterator<ParentFamilyRef> iterPfr = person.getParentFamilyRefs().iterator();
        while (iterPfr.hasNext())
            if (iterPfr.next().getRef().equals(family.getId()))
                iterPfr.remove();
        if (person.getParentFamilyRefs().isEmpty())
            person.setParentFamilyRefs(null);
    }
}
