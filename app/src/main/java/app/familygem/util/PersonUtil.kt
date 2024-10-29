package app.familygem.util

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import app.familygem.Global
import app.familygem.Memory
import app.familygem.R
import app.familygem.U
import app.familygem.constant.Gender
import app.familygem.constant.Relation
import app.familygem.detail.FamilyActivity
import org.folg.gedcom.model.Family
import org.folg.gedcom.model.Person

/**
 * Generates the 2 family labels (as child and as parent) for contextual menu.
 */
fun Person.getFamilyLabels(context: Context, family: Family?): Array<String?> {
    var family = family
    val labels = arrayOf<String?>(null, null)
    val parentFams = getParentFamilies(Global.gc)
    val spouseFams = getSpouseFamilies(Global.gc)
    if (parentFams.size > 0) labels[0] = if (spouseFams.isEmpty()) context.getString(R.string.family)
    else context.getString(R.string.family_as, FamilyActivity.getRole(this, null, Relation.CHILD, true))
    if (family == null && spouseFams.size == 1) family = spouseFams[0]
    if (spouseFams.size > 0) labels[1] = if (parentFams.isEmpty()) context.getString(R.string.family)
    else context.getString(R.string.family_as, FamilyActivity.getRole(this, family, Relation.PARTNER, true))
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
 * Deletes a person from the tree, possibly finding the new root.
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
            modifiedFamilies.add(it)
        }
        if (it.getWives(Global.gc).contains(this)) {
            it.wifeRefs.removeAt(it.getWives(Global.gc).indexOf(this))
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
    Toast.makeText(Global.context, R.string.person_deleted, Toast.LENGTH_LONG).show()
    return families
}

object PersonUtil {

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
        if (Gender.isMale(person)) borderView.setBackgroundResource(R.drawable.casella_bordo_maschio)
        else if (Gender.isFemale(person)) borderView.setBackgroundResource(R.drawable.casella_bordo_femmina)
        return personView
    }
}
