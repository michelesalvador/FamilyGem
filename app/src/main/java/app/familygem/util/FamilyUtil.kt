package app.familygem.util

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import app.familygem.Global
import app.familygem.Memory
import app.familygem.R
import app.familygem.U
import app.familygem.detail.FamilyActivity
import org.folg.gedcom.model.Family
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Person

fun Family.isEmpty(): Boolean {
    val members = this.husbandRefs.size + this.wifeRefs.size + this.childRefs.size
    return members <= 1 && this.eventsFacts.isEmpty() && this.getAllMedia(Global.gc).isEmpty()
            && this.getAllNotes(Global.gc).isEmpty() && this.sourceCitations.isEmpty()
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

    /**
     * Places in layout a small family view linked to family activity.
     */
    fun placeFamily(layout: LinearLayout, family: Family) {
        val familyView = LayoutInflater.from(layout.context).inflate(R.layout.family_layout, layout, false)
        layout.addView(familyView)
        familyView.findViewById<TextView>(R.id.family_text).text = writeMembers(layout.context, Global.gc, family, true)
        familyView.setOnClickListener {
            Memory.setLeader(family)
            layout.context.startActivity(Intent(layout.context, FamilyActivity::class.java))
        }
    }

    /**
     * Returns an array of strings with families member names.
     */
    fun listFamilies(families: List<Family>): Array<String> {
        val list: MutableList<String> = ArrayList()
        families.forEach { list.add(writeMembers(Global.context, Global.gc, it)) }
        return list.toTypedArray<String>()
    }

    /**
     * Returns a string with family member names.
     */
    fun writeMembers(context: Context, gedcom: Gedcom, family: Family, multiLine: Boolean = false): String {
        val builder = StringBuilder()
        val divider = if (multiLine) '\n' else ' '
        family.getHusbands(gedcom).forEach { builder.append(U.properName(it)).append(',').append(divider)  }
        family.getWives(gedcom).forEach { builder.append(U.properName(it)).append(',').append(divider) }
        if (family.childRefs.size == 1) {
            builder.append(U.properName(family.getChildren(gedcom)[0]))
        } else if (family.childRefs.size > 1) builder.append(context.getString(R.string.num_children, family.childRefs.size))
        if (builder.isEmpty()) builder.append("[${context.getString(R.string.empty_family)}]")
        return builder.trimEnd { it == divider || it == ',' }.toString()
    }
}
