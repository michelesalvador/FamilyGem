package app.familygem.detail

import android.content.Intent
import app.familygem.DetailActivity
import app.familygem.Global
import app.familygem.Memory
import app.familygem.R
import app.familygem.U
import app.familygem.constant.Relation
import app.familygem.profile.ProfileActivity
import app.familygem.util.ChangeUtil.placeChangeDate
import app.familygem.util.NoteUtil
import app.familygem.util.PersonUtil
import app.familygem.util.getSpouseRefs
import org.folg.gedcom.model.Family
import org.folg.gedcom.model.SpouseRef

class FamilyActivity : DetailActivity() {
    lateinit var family: Family
    override fun format() {
        setTitle(R.string.family)
        family = cast(Family::class.java) as Family
        placeSlug("FAM", family.id)
        family.getSpouseRefs().forEach { placeMember(it, Relation.PARTNER) }
        family.childRefs.forEach { placeMember(it, Relation.CHILD) }
        family.eventsFacts.forEach { place(writeEventTitle(family, it), it) }
        placeExtensions(family)
        NoteUtil.placeNotes(box, family)
        U.placeMedia(box, family, true)
        U.placeSourceCitations(box, family)
        placeChangeDate(box, family.change)
    }

    /** Adds a family member to the layout. */
    private fun placeMember(spouseChildRef: SpouseRef, relation: Relation) {
        val person = spouseChildRef.getPerson(Global.gc) ?: return
        val personView = U.placePerson(
            box, person, PersonUtil.writeRole(person, family, relation, true) + PersonUtil.writeLineage(person, family)
        )
        personView.setTag(R.id.tag_object, person) // For the context menu in DetailActivity
        if (relation == Relation.PARTNER) {
            person.spouseFamilyRefs.firstOrNull { it.ref == family.id }?.let {
                personView.setTag(R.id.tag_family_ref, it)
            }
        } else if (relation == Relation.CHILD) {
            person.parentFamilyRefs.firstOrNull { it.ref == family.id }?.let {
                personView.setTag(R.id.tag_family_ref, it)
            }
        }
        personView.setTag(R.id.tag_ref, spouseChildRef)
        registerForContextMenu(personView)
        personView.setOnClickListener {
            val parentFamilies = person.getParentFamilies(Global.gc)
            val spouseFamilies = person.getSpouseFamilies(Global.gc)
            // A spouse with one or more families in which he is a child
            if (relation == Relation.PARTNER && parentFamilies.isNotEmpty()) {
                U.whichParentsToShow(this, person, 2)
            } // A child with one or more families in which he is a partner
            else if (relation == Relation.CHILD && spouseFamilies.isNotEmpty()) {
                U.whichSpousesToShow(this, person)
            } // An unmarried child who has multiple parental families
            else if (parentFamilies.size > 1) {
                if (parentFamilies.size == 2) { // Swaps between the 2 parental families
                    Global.indi = person.id
                    Global.familyNum = if (parentFamilies.indexOf(family) == 0) 1 else 0
                    Memory.replaceLeader(parentFamilies[Global.familyNum])
                    recreate()
                } else // More than two families
                    U.whichParentsToShow(this, person, 2)
            } // A spouse without parents but with multiple spouse families
            else if (spouseFamilies.size > 1) {
                if (spouseFamilies.size == 2) { // Swaps between the 2 spouse families
                    Global.indi = person.id
                    val otherFamily = spouseFamilies[if (spouseFamilies.indexOf(family) == 0) 1 else 0]
                    Memory.replaceLeader(otherFamily)
                    recreate()
                } else U.whichSpousesToShow(this, person)
            } else { // Opens person profile
                Memory.setLeader(person)
                startActivity(Intent(this, ProfileActivity::class.java))
            }
        }
        if (oneFamilyMember == null) oneFamilyMember = person
    }
}
