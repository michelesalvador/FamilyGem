package app.familygem.util

import android.content.Context
import app.familygem.Global
import app.familygem.R
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
