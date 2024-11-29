package app.familygem.util

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import app.familygem.Global
import app.familygem.Memory
import app.familygem.R
import app.familygem.U
import app.familygem.constant.Relation
import app.familygem.detail.FamilyActivity
import org.folg.gedcom.model.ChildRef
import org.folg.gedcom.model.Family
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.ParentFamilyRef
import org.folg.gedcom.model.Person
import org.folg.gedcom.model.SpouseFamilyRef
import org.folg.gedcom.model.SpouseRef

fun Family.isEmpty(): Boolean {
    val members = husbandRefs.size + wifeRefs.size + childRefs.size
    return members <= 1 && eventsFacts.isEmpty() && getAllMedia(Global.gc).isEmpty()
            && getAllNotes(Global.gc).isEmpty() && sourceCitations.isEmpty()
}

/** First husband, first wife, then other husbands and wives. */
fun Family.getSpouseRefs(): List<SpouseRef> {
    val list = ArrayList<SpouseRef>()
    husbandRefs.firstOrNull()?.let { list.add(it) }
    wifeRefs.firstOrNull()?.let { list.add(it) }
    list.addAll(husbandRefs.drop(1))
    list.addAll(wifeRefs.drop(1))
    return list
}

/** Returns a sorted list of spouses of the family. */
fun Family.getSpouses(gedcom: Gedcom = Global.gc): List<Person> {
    val list = ArrayList<Person>()
    getSpouseRefs().forEach { ref -> ref.getPerson(gedcom)?.let { list.add(it) } }
    return list
}

fun Family.delete() {
    val members: MutableSet<Person> = HashSet()
    // Removes references from family members towards the family
    for (husband in getHusbands(Global.gc)) {
        val refs = husband.spouseFamilyRefs.iterator()
        while (refs.hasNext()) {
            val sfr = refs.next()
            if (sfr.ref == id) {
                refs.remove()
                members.add(husband)
            }
        }
    }
    for (wife in getWives(Global.gc)) {
        val refs = wife.spouseFamilyRefs.iterator()
        while (refs.hasNext()) {
            val sfr = refs.next()
            if (sfr.ref == id) {
                refs.remove()
                members.add(wife)
            }
        }
    }
    for (children in getChildren(Global.gc)) {
        val refs = children.parentFamilyRefs.iterator()
        while (refs.hasNext()) {
            val pfr = refs.next()
            if (pfr.ref == id) {
                refs.remove()
                members.add(children)
            }
        }
    }
    // The family is deleted
    Global.gc.families.remove(this)
    Global.gc.createIndexes() // Necessary to update persons
    Memory.setInstanceAndAllSubsequentToNull(this)
    Global.familyNum = 0 // In the case that is deleted exactly the family referenced in Global.familyNum
    TreeUtil.save(true, *members.toTypedArray())
}

object FamilyUtil {

    fun createNewFamily(): Family {
        val newFamily = Family()
        newFamily.id = U.newID(Global.gc, Family::class.java)
        Global.gc.addFamily(newFamily)
        return newFamily
    }

    /** Places in layout a small family view linked to family activity. */
    fun placeFamily(layout: LinearLayout, family: Family) {
        val familyView = LayoutInflater.from(layout.context).inflate(R.layout.family_layout, layout, false)
        layout.addView(familyView)
        familyView.findViewById<TextView>(R.id.family_text).text = writeMembers(layout.context, Global.gc, family, true)
        familyView.setOnClickListener {
            Memory.setLeader(family)
            layout.context.startActivity(Intent(layout.context, FamilyActivity::class.java))
        }
    }

    /** Returns an array of strings with families member names. */
    fun listFamilies(families: List<Family>): Array<String> {
        val list: MutableList<String> = ArrayList()
        families.forEach { list.add(writeMembers(Global.context, Global.gc, it)) }
        return list.toTypedArray<String>()
    }

    /** Returns a string with family member names. */
    fun writeMembers(context: Context, gedcom: Gedcom, family: Family, multiLine: Boolean = false): String {
        val builder = StringBuilder()
        val divider = if (multiLine) '\n' else ' '
        family.getSpouses(gedcom).forEach { builder.append(U.properName(it)).append(',').append(divider) }
        if (family.childRefs.size == 1) {
            builder.append(U.properName(family.getChildren(gedcom)[0]))
        } else if (family.childRefs.size > 1) builder.append(context.getString(R.string.num_children, family.childRefs.size))
        if (builder.isEmpty()) builder.append("[${context.getString(R.string.empty_family)}]")
        return builder.trimEnd { it == divider || it == ',' }.toString()
    }

    /** Connects a person to a family as parent or child. */
    fun linkPerson(person: Person, family: Family, relation: Relation) {
        when (relation) {
            Relation.PARTNER -> {
                // The person Ref inside the family
                val spouseRef = SpouseRef()
                spouseRef.ref = person.id
                addSpouse(family, spouseRef)
                // The family Ref inside the person
                val spouseFamilyRef = SpouseFamilyRef()
                spouseFamilyRef.ref = family.id
                person.addSpouseFamilyRef(spouseFamilyRef)
            }
            Relation.CHILD -> {
                val childRef = ChildRef()
                childRef.ref = person.id
                family.addChild(childRef)
                val parentFamilyRef = ParentFamilyRef()
                parentFamilyRef.ref = family.id
                person.addParentFamilyRef(parentFamilyRef)
            }
            else -> {}
        }
    }

    /** Adds a spouse in a family, giving priority to keep one husband e one wife. */
    fun addSpouse(family: Family, spouseRef: SpouseRef) {
        family.apply {
            if (husbandRefs.size + wifeRefs.size == 1) { // Adds to the empty slot
                if (husbandRefs.isEmpty()) addHusband(spouseRef)
                else addWife(spouseRef)
            } else { // Adds according to sex
                val person = Global.gc.getPerson(spouseRef.ref)
                if (person.sex.isFemale()) addWife(spouseRef)
                else addHusband(spouseRef)
            }
        }
    }

    /** Iterates over all spouse families of a person to update husband and wife roles. */
    fun updateSpouseRoles(person: Person) {
        person.getSpouseFamilies(Global.gc).forEach { family ->
            updateSpouseRoles(family)
        }
    }

    /**
     * Puts males and females (also more than two) of a family in corresponding husband and wife roles.
     * Homosexual spouses are placed one as husband and one as wife.
     * All GEDCOM standards require max one HUSB and max one WIFE for each family.
     * @return A list of errors (whether [correct] is false)
     */
    @JvmOverloads
    fun updateSpouseRoles(family: Family, gedcom: Gedcom = Global.gc, correct: Boolean = true): List<String> {
        val errors = mutableListOf<String>()
        family.apply {
            fun swapHusband(ref: SpouseRef) {
                if (correct) {
                    husbandRefs.remove(ref)
                    addWife(ref)
                } else errors.add("HUSB ${ref.ref} instead of WIFE in $id")
            }

            fun swapWife(ref: SpouseRef) {
                if (correct) {
                    wifeRefs.remove(ref)
                    addHusband(ref)
                } else errors.add("WIFE ${ref.ref} instead of HUSB in $id")
            }

            val spouses = getSpouseRefs().mapNotNull { it.getPerson(gedcom) }
            val females = spouses.filter { it.sex.isFemale() }
            val males = spouses.minus(females.toSet()) // Males and undefined sexes, actually

            if (females.isEmpty()) { // Males only
                if (wifeRefs.isEmpty()) { // All husbands
                    husbandRefs.getOrNull(1)?.let { swapHusband(it) } // Second husband becomes wife
                } else if (husbandRefs.isEmpty()) { // All wives
                    wifeRefs.filterIndexed { index, _ -> index != 1 }.forEach { swapWife(it) } // All except second wife become husbands
                } else {
                    wifeRefs.drop(1).forEach { swapWife(it) } // From second wife become husbands
                }
            } else if (males.isEmpty() && females.size > 1) { // Many females only
                if (husbandRefs.isEmpty()) { // All wives
                    swapWife(wifeRefs[0]) // First wife becomes husband
                } else {
                    husbandRefs.drop(1).forEach { swapHusband(it) } // From second husband become wives
                }
            } else { // Males and females (or one single female husband)
                with(wifeRefs.iterator()) {
                    forEach { // All male wives become husband
                        if (males.contains(it.getPerson(gedcom))) {
                            if (correct) {
                                addHusband(it)
                                remove()
                            } else errors.add("WIFE ${it.ref} instead of HUSB in $id")
                        }
                    }
                }
                with(husbandRefs.iterator()) {
                    forEach { // All female husbands become wife
                        if (females.contains(it.getPerson(gedcom))) {
                            if (correct) {
                                addWife(it)
                                remove()
                            } else errors.add("HUSB ${it.ref} instead of WIFE in $id")
                        }
                    }
                }
            }
        }
        return errors
    }

    /**
     * Removes the single Spouse/ChildFamilyRef from the person and the corresponding Spouse/ChildRef from the family.
     * @param familyRef Could be null if FAMS or FAMC was missing inside INDI
     */
    fun unlinkRefs(familyRef: SpouseFamilyRef?, ref: SpouseRef) {
        // Inside the person towards the family
        ref.getPerson(Global.gc).apply {
            spouseFamilyRefs.remove(familyRef)
            if (spouseFamilyRefs.isEmpty()) spouseFamilyRefs = null // Any empty list is deleted
            parentFamilyRefs.remove(familyRef)
            if (parentFamilyRefs.isEmpty()) parentFamilyRefs = null
        }
        // Inside the family towards the person
        familyRef?.getFamily(Global.gc)?.apply {
            husbandRefs.remove(ref)
            if (husbandRefs.isEmpty()) husbandRefs = null
            wifeRefs.remove(ref)
            if (wifeRefs.isEmpty()) wifeRefs = null
            childRefs.remove(ref)
            if (childRefs.isEmpty()) childRefs = null
        } ?: Toast.makeText(Global.context, "Missing family reference in person ${ref.ref}.", Toast.LENGTH_LONG).show()
    }

    /** Unlinks a person from a family. */
    fun unlinkPerson(person: Person, family: Family) {
        // Removes the refs of the person inside the family
        family.apply {
            with(husbandRefs.iterator()) { forEach { if (it.ref == person.id) remove() } }
            if (husbandRefs.isEmpty()) husbandRefs = null // Deletes any empty list
            with(wifeRefs.iterator()) { forEach { if (it.ref == person.id) remove() } }
            if (wifeRefs.isEmpty()) wifeRefs = null
            with(childRefs.iterator()) { forEach { if (it.ref == person.id) remove() } }
            if (childRefs.isEmpty()) childRefs = null
        }
        // Removes family refs inside the person
        person.apply {
            with(spouseFamilyRefs.iterator()) { forEach { if (it.ref == family.id) remove() } }
            if (spouseFamilyRefs.isEmpty()) spouseFamilyRefs = null
            with(parentFamilyRefs.iterator()) { forEach { if (it.ref == family.id) remove() } }
            if (parentFamilyRefs.isEmpty()) parentFamilyRefs = null
        }
    }

    /** First husband and first wife have the same sex. */
    fun areSpousesHomosexual(family: Family): Boolean {
        family.apply {
            if (husbandRefs.size > 0 && wifeRefs.size > 0) {
                val femaleHusband = getHusbands(Global.gc)[0].sex.isFemale()
                val femaleWife = getWives(Global.gc)[0].sex.isFemale()
                return femaleHusband && femaleWife || !femaleHusband && !femaleWife
            }
        }
        return false
    }

    /** Finds the ParentFamilyRef of a child person in a family. */
    fun findParentFamilyRef(person: Person, family: Family): ParentFamilyRef? {
        return person.parentFamilyRefs.firstOrNull { it.ref == family.id }
    }
}
