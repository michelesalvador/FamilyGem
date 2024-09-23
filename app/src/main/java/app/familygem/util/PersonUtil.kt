package app.familygem.util

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.familygem.Global
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

object PersonUtil {

    /**
     * Creates into layout a small card of a person and returns the created view.
     */
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
