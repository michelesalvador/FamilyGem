package app.familygem.util

import app.familygem.Global
import app.familygem.Memory
import app.familygem.U
import org.folg.gedcom.model.Family
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
    fun newFamily(): Family {
        val newFamily = Family()
        newFamily.id = U.newID(Global.gc, Family::class.java)
        Global.gc.addFamily(newFamily)
        return newFamily
    }
}
