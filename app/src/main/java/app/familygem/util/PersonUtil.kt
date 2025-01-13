package app.familygem.util

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import app.familygem.Global
import app.familygem.Memory
import app.familygem.R
import app.familygem.U
import app.familygem.constant.Relation
import app.familygem.constant.Status
import app.familygem.detail.FamilyActivity
import app.familygem.profile.ProfileActivity
import org.folg.gedcom.model.Family
import org.folg.gedcom.model.Person

var Person.sex: SexUtil
    get() = SexUtil(eventsFacts.firstOrNull { it.tag == "SEX" })
    set(value) = sex.setSex(this, value)

/** Generates the 2 family labels (as child and as parent) for contextual menu. */
fun Person.getFamilyLabels(context: Context, family: Family? = null): Array<String?> {
    var family = family
    val labels = arrayOf<String?>(null, null)
    val parentFams = getParentFamilies(Global.gc)
    val spouseFams = getSpouseFamilies(Global.gc)
    if (parentFams.size > 0) labels[0] = if (spouseFams.isEmpty()) context.getString(R.string.family)
    else context.getString(R.string.family_as, PersonUtil.writeRole(this, null, Relation.CHILD, true))
    if (family == null && spouseFams.size == 1) family = spouseFams[0]
    if (spouseFams.size > 0) labels[1] = if (parentFams.isEmpty()) context.getString(R.string.family)
    else context.getString(R.string.family_as, PersonUtil.writeRole(this, family, Relation.PARTNER, true))
    return labels
}

/**
 * Counts how many near relatives one person has: parents, siblings, half-siblings, spouses and children.
 * @return Number of near relatives (person excluded)
 */
fun Person.countRelatives(): Int {
    var count = 0
    val parentFamilies = getParentFamilies(Global.gc)
    parentFamilies.forEach { family ->
        // Parents
        count += family.husbandRefs.size
        count += family.wifeRefs.size
        // Siblings
        count += family.getChildren(Global.gc).count { it != this }
        // Half-sibling
        family.getHusbands(Global.gc).forEach { father ->
            father.getSpouseFamilies(Global.gc).filter { !parentFamilies.contains(it) }.forEach { count += it.childRefs.size }
        }
        family.getWives(Global.gc).forEach { mother ->
            mother.getSpouseFamilies(Global.gc).filter { !parentFamilies.contains(it) }.forEach { count += it.childRefs.size }
        }
    }
    getSpouseFamilies(Global.gc).forEach { family ->
        // Partners
        count += family.wifeRefs.size
        count += family.husbandRefs.size
        count-- // Minus their self
        // Children
        count += family.childRefs.size
    }
    return count
}

/**
 * Deletes a person from the tree, possibly finding the new root, and saves the changes.
 * @return An array of modified families
 */
fun Person.delete(): Array<Family> {
    // Deletes all references from families to person
    val modifiedFamilies: MutableSet<Family> = HashSet()
    getParentFamilies(Global.gc).forEach {
        it.childRefs.removeAt(it.getChildren(Global.gc).indexOf(this))
        modifiedFamilies.add(it)
    }
    getSpouseFamilies(Global.gc).forEach {
        if (it.getHusbands(Global.gc).contains(this)) {
            it.husbandRefs.removeAt(it.getHusbands(Global.gc).indexOf(this))
            FamilyUtil.updateSpouseRoles(it)
            modifiedFamilies.add(it)
        }
        if (it.getWives(Global.gc).contains(this)) {
            it.wifeRefs.removeAt(it.getWives(Global.gc).indexOf(this))
            FamilyUtil.updateSpouseRoles(it)
            modifiedFamilies.add(it)
        }
    }
    Memory.setInstanceAndAllSubsequentToNull(this)
    Global.gc.people.remove(this)
    Global.gc.createIndexes() // Necessary
    val newRootId = U.findRootId(Global.gc) // TODO: could be "find next of kin"
    if (Global.settings.currentTree.root != null && Global.settings.currentTree.root == id) {
        Global.settings.currentTree.root = newRootId
        Global.settings.save()
    }
    if (Global.indi != null && Global.indi == id) Global.indi = newRootId
    val families = modifiedFamilies.toTypedArray<Family>()
    TreeUtil.save(true, *families)
    return families
}

object PersonUtil {

    private val lineageTexts = arrayOf(
        U.s(R.string.undefined) + " (" + Util.caseString(R.string.birth) + ")",
        U.s(R.string.birth), U.s(R.string.adopted), U.s(R.string.foster)
    )
    val lineageTypes = arrayOf(null, "birth", "adopted", "foster")

    /** Creates into layout a small card of a person and returns the created view. */
    fun placeSmallPerson(layout: LinearLayout, person: Person): View {
        val personView = LayoutInflater.from(layout.context).inflate(R.layout.small_person_layout, layout, false)
        layout.addView(personView)
        FileUtil.selectMainImage(person, personView.findViewById(R.id.smallPerson_image))
        personView.findViewById<TextView>(R.id.smallPerson_name).text = U.properName(person)
        val dates = U.twoDates(person, false)
        val detailView = personView.findViewById<TextView>(R.id.smallPerson_detail)
        if (dates.isBlank()) detailView.visibility = View.GONE
        else detailView.text = dates
        if (!U.isDead(person)) personView.findViewById<View>(R.id.smallPerson_dead).visibility = View.GONE
        val borderView = personView.findViewById<View>(R.id.smallPerson_border)
        val sex = person.sex
        if (sex.isMale()) borderView.setBackgroundResource(R.drawable.person_border_male)
        else if (sex.isFemale()) borderView.setBackgroundResource(R.drawable.person_border_female)
        return personView
    }

    /** Returns a string with all titles of a person concatenated. */
    fun writeTitles(person: Person): String {
        return person.eventsFacts.filter { it.tag != null && it.tag == "TITL" && it.value != null }.joinToString("\n") { it.value }
    }

    /**
     * Returns a definition of the person's role from their relation with a family.
     * @param family Can be null
     * @param respectFamily The role to find is relative to the family (it becomes 'parent' when there are children)
     */
    fun writeRole(person: Person, family: Family?, relation: Relation, respectFamily: Boolean): String {
        var relation = relation
        if (respectFamily && relation == Relation.PARTNER && family != null && family.childRefs.isNotEmpty()) relation = Relation.PARENT
        val status = Status.getStatus(family)
        val sex = person.sex
        val role = if (sex.isMale()) {
            when (relation) {
                Relation.PARENT -> R.string.father
                Relation.SIBLING -> R.string.brother
                Relation.HALF_SIBLING -> R.string.half_brother
                Relation.PARTNER -> when (status) {
                    Status.MARRIED -> R.string.husband
                    Status.DIVORCED -> R.string.ex_husband
                    Status.SEPARATED -> R.string.ex_male_partner
                    else -> R.string.male_partner
                }
                Relation.CHILD -> R.string.son
            }
        } else if (sex.isFemale()) {
            when (relation) {
                Relation.PARENT -> R.string.mother
                Relation.SIBLING -> R.string.sister
                Relation.HALF_SIBLING -> R.string.half_sister
                Relation.PARTNER -> when (status) {
                    Status.MARRIED -> R.string.wife
                    Status.DIVORCED -> R.string.ex_wife
                    Status.SEPARATED -> R.string.ex_female_partner
                    else -> R.string.female_partner
                }
                Relation.CHILD -> R.string.daughter
            }
        } else { // Neutral roles
            when (relation) {
                Relation.PARENT -> R.string.parent
                Relation.SIBLING -> R.string.sibling
                Relation.HALF_SIBLING -> R.string.half_sibling
                Relation.PARTNER -> when (status) {
                    Status.MARRIED -> R.string.spouse
                    Status.DIVORCED -> R.string.ex_spouse
                    Status.SEPARATED -> R.string.ex_partner
                    else -> R.string.partner
                }
                Relation.CHILD -> R.string.child
            }
        }
        return Util.caseString(role)
    }

    /** Composes the lineage definition to be added to the role in a person card. */
    fun writeLineage(person: Person, family: Family): String {
        return person.parentFamilyRefs.firstOrNull { it.ref == family.id }.let { ref ->
            lineageTypes.indexOf(ref?.relationshipType).let {
                if (it > 0) " – ${lineageTexts[it]}" else ""
            }
        }
    }

    /** Displays an alert dialog to choose the lineage of one person. */
    fun chooseLineage(context: Context, person: Person, family: Family) {
        person.parentFamilyRefs.firstOrNull { it.ref == family.id }?.apply {
            lineageTypes.indexOf(relationshipType).let {
                AlertDialog.Builder(context).setSingleChoiceItems(lineageTexts, it) { dialog, selected ->
                    relationshipType = lineageTypes[selected]
                    dialog.dismiss()
                    if (context is ProfileActivity) context.refresh()
                    else if (context is FamilyActivity) context.refresh()
                    TreeUtil.save(true, person)
                }.show()
            }
        }
    }
}
