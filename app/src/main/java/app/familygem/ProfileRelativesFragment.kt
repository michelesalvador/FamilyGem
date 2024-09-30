package app.familygem

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import app.familygem.constant.Extra
import app.familygem.constant.Relation
import app.familygem.detail.FamilyActivity
import app.familygem.main.PersonsFragment
import app.familygem.util.TreeUtil.save
import app.familygem.util.delete
import app.familygem.util.getFamilyLabels
import org.folg.gedcom.model.Family
import org.folg.gedcom.model.Person
import java.util.Collections

/**
 * Relatives of the profile person, organized under family groups.
 */
class ProfileRelativesFragment : Fragment() {
    private lateinit var relativesView: View
    private lateinit var person: Person
    private lateinit var activity: FragmentActivity

    private enum class Branch { ANY, PATERNAL, MATERNAL }
    private enum class FamilyType { ORIGIN, HALF, ORIGIN_SMALL, OWN }

    // Group data in a tree structure
    private val groupTree = Pair(
        mutableListOf<Pair<MutableList<ParentItem>, MutableList<ParentItem>>>(), // Parent families
        mutableListOf<GroupData>() // Own families
    )

    private class ParentItem(val parent: Person) {
        val groups = mutableListOf<GroupData>()
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            other as ParentItem
            return groups == other.groups
        }

        override fun hashCode(): Int {
            return groups.hashCode()
        }
    }

    private data class GroupData(
        val family: Family, val members: List<Pair<Person, Relation>>, val type: FamilyType, val branch: Branch,
        val listIndex: Int, val lineageIndex: Int = 0, val parentIndex: Int = 0, val groupIndex: Int = 0
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            other as GroupData
            return family == other.family
        }

        override fun hashCode(): Int {
            return family.hashCode()
        }
    }

    // Same group data in a simple list
    private val groupList = mutableListOf<GroupData>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        relativesView = inflater.inflate(R.layout.profile_page_fragment, container, false)
        if (Global.gc != null) {
            person = Global.gc.getPerson(Global.indi)
            val parentFamilies = person.getParentFamilies(Global.gc)
            // Parents, siblings and half-siblings
            var listIndex = 0
            parentFamilies.forEachIndexed { lineageIndex, parentFamily ->
                val lineageItem = Pair(mutableListOf<ParentItem>(), mutableListOf<ParentItem>())
                groupTree.first.add(lineageItem)
                parentFamily.getHusbands(Global.gc).forEachIndexed { parentIndex, father ->
                    listIndex = createParentItem(parentFamilies, lineageItem, father, Branch.PATERNAL, listIndex, lineageIndex, parentIndex)
                }
                parentFamily.getWives(Global.gc).forEachIndexed { parentIndex, mother ->
                    listIndex = createParentItem(parentFamilies, lineageItem, mother, Branch.MATERNAL, listIndex, lineageIndex, parentIndex)
                }
            }
            // Partners and children
            person.getSpouseFamilies(Global.gc).forEachIndexed { index, family ->
                val members = findGroupMembers(family, FamilyType.OWN)
                if (members.isNotEmpty()) {
                    val groupData = GroupData(family, members, FamilyType.OWN, Branch.ANY, index)
                    groupTree.second.add(groupData)
                    groupList.add(groupData)
                }
            }

            // Places the groups on layout
            val layout = relativesView.findViewById<LinearLayout>(R.id.profile_page)
            if (Global.settings.expert) {
                // Parent families
                groupTree.first.forEach { parentFamily ->
                    parentFamily.first.forEach { createGroupLayout(it, layout) }
                    parentFamily.second.forEach { createGroupLayout(it, layout) }
                }
                // Own families
                groupTree.second.forEach { placeGroup(it, layout) }
            } else { // Simple person list without group titles
                groupList.forEach { data -> data.members.forEach { placeCard(it.first, it.second, data.family, layout) } }
            }
        }
        activity = requireActivity()
        return relativesView
    }

    /**
     * Creates the groups to populate [lineageItem] and [groupList].
     * @param parent Husband or wife
     * @param branch PATERNAL or MATERNAL
     */
    private fun createParentItem(
        parentFamilies: List<Family>, lineageItem: Pair<MutableList<ParentItem>, MutableList<ParentItem>>,
        parent: Person, branch: Branch, listIndex: Int, lineageIndex: Int, parentIndex: Int
    ): Int {
        var listIndexIncrement = listIndex
        val tempGroups = mutableListOf<GroupData>()
        parent.getSpouseFamilies(Global.gc).forEachIndexed { groupIndex, parentFamily ->
            val type = if (groupList.any { it.family == parentFamily }) FamilyType.ORIGIN_SMALL
            else if (parentFamilies.contains(parentFamily)) FamilyType.ORIGIN
            else FamilyType.HALF
            tempGroups.add(
                GroupData(
                    parentFamily, findGroupMembers(parentFamily, type), type, branch, listIndexIncrement++, lineageIndex, parentIndex, groupIndex
                )
            )
        }
        try {
            if (tempGroups.count { it.members.isEmpty() } == tempGroups.size)
                throw Exception("All groups are empty")
            val parentItem = ParentItem(parent)
            parentItem.groups.addAll(tempGroups)
            if (groupTree.first.any { it.first.contains(parentItem) || it.second.contains(parentItem) })
                throw Exception("ParentItem already exists")
            // Adds parentItem to lineageItem
            if (branch == Branch.PATERNAL) lineageItem.first.add(parentItem) else lineageItem.second.add(parentItem)
            // Adds the groups to groupList
            groupList.addAll(tempGroups)
        } catch (e: Exception) {
            return listIndex // Original value
        }
        return listIndexIncrement
    }

    /**
     * Chooses to place parentItem groups in a new group container or just in the provided layout.
     */
    private fun createGroupLayout(parentItem: ParentItem, layout: LinearLayout) {
        val listLayout = if (parentItem.groups.size > 1) {
            // Creates the macro container of groups
            val groupView = LayoutInflater.from(context).inflate(R.layout.profile_relatives_group, layout, false)
            layout.addView(groupView)
            val groupTitle = groupView.findViewById<TextView>(R.id.group_title)
            groupTitle.text = U.properName(parentItem.parent)
            groupTitle.setTypeface(null, Typeface.BOLD)
            groupTitle.setOnClickListener { clickRelative(parentItem.parent) }
            if (parentItem.groups.last().members.isEmpty()) {
                val cap = groupView.findViewById<ImageView>(R.id.group_cap)
                (cap.layoutParams as ConstraintLayout.LayoutParams).bottomMargin = U.dpToPx(7F)
            }
            val groupList = groupView.findViewById<LinearLayout>(R.id.group_list)
            (groupList.layoutParams as ConstraintLayout.LayoutParams).marginStart = 0
            groupList
        } else {
            layout
        }
        parentItem.groups.forEach { placeGroup(it, listLayout) }
    }

    /**
     * Creates a group layout of relatives from a family.
     * @param layout LinearLayout where the family members will be added
     */
    private fun placeGroup(data: GroupData, layout: LinearLayout) {
        var title: Int
        when (data.type) {
            FamilyType.ORIGIN, FamilyType.ORIGIN_SMALL -> {
                // Title based on the lineage type
                FamilyActivity.findParentFamilyRef(person, data.family).let {
                    title = when (it.relationshipType) {
                        FamilyActivity.lineageTypes[2] -> R.string.adoptive_family
                        FamilyActivity.lineageTypes[3] -> R.string.foster_family
                        else -> R.string.origin_family
                    }
                }
            }
            FamilyType.HALF -> {
                title = if (data.branch == Branch.PATERNAL) R.string.paternal_family else R.string.maternal_family
            }
            FamilyType.OWN -> {
                title = R.string.own_family
            }
        }
        // Creates the group view
        val resource = if (layout.id == R.id.group_list) R.layout.profile_relatives_title else R.layout.profile_relatives_group
        val groupView = LayoutInflater.from(context).inflate(resource, layout, false)
        layout.addView(groupView)
        // Sets title
        val groupTitle = groupView.findViewById<TextView>(R.id.group_title)
        groupTitle.text = getText(title)
        /*groupTitle.text = "${data.listIndex}) ${groupTitle.text} ${data.lineageIndex}.${data.branch.toString().substring(0, 1)}" +
                ".${data.parentIndex}.${data.groupIndex} - ${data.family.id}"*/
        if (data.members.isEmpty()) {
            groupView.findViewById<ImageView>(R.id.group_tab).visibility = View.GONE
        }
        groupTitle.setOnClickListener {
            Memory.setLeader(data.family)
            startActivity(Intent(context, FamilyActivity::class.java))
        }
        registerForContextMenu(groupTitle)
        groupTitle.setTag(R.id.tag_family, data.family) // For the context menu
        groupTitle.setTag(R.id.tag_object, data) // Will distinguish group title from listed person
        // Creates relatives cards
        data.members.forEach { placeCard(it.first, it.second, data.family, groupView.findViewById(R.id.group_list)) }
    }

    /**
     * Creates a person card and adds it to [groupLayout].
     */
    private fun placeCard(relative: Person, relation: Relation, family: Family, groupLayout: LinearLayout) {
        val personView = U.placePerson(
            groupLayout, relative,
            FamilyActivity.getRole(relative, family, relation, false) + FamilyActivity.writeLineage(relative, family)
        )
        personView.setOnClickListener { clickRelative(relative) }
        registerForContextMenu(personView)
        personView.setTag(R.id.tag_family, family) // For the context menu
    }

    private fun clickRelative(person: Person) {
        requireActivity().finish() // Removes the current activity from the stack
        Memory.replaceLeader(person)
        val intent = Intent(context, ProfileActivity::class.java)
        intent.putExtra(Extra.PAGE, 2) // Opens the Relatives page
        startActivity(intent)
    }

    /**
     * Collects persons for a group, given a family and type.
     */
    private fun findGroupMembers(family: Family, type: FamilyType): List<Pair<Person, Relation>> {
        val members = mutableListOf<Pair<Person, Relation>>()
        fun add(person: Person, relation: Relation) {
            members.add(Pair(person, relation))
        }
        when (type) {
            FamilyType.ORIGIN -> {
                family.getHusbands(Global.gc).forEach { add(it, Relation.PARENT) }
                family.getWives(Global.gc).forEach { add(it, Relation.PARENT) }
                family.getChildren(Global.gc).forEach { if (it != person) add(it, Relation.SIBLING) }
            }
            FamilyType.HALF -> {
                family.getChildren(Global.gc).forEach { add(it, Relation.HALF_SIBLING) }
            }
            FamilyType.ORIGIN_SMALL -> {}
            FamilyType.OWN -> {
                family.getHusbands(Global.gc).forEach { if (it != person) add(it, Relation.PARTNER) }
                family.getWives(Global.gc).forEach { if (it != person) add(it, Relation.PARTNER) }
                family.getChildren(Global.gc).forEach { add(it, Relation.CHILD) }
            }
        }
        return members
    }

    // Context menu
    private lateinit var family: Family // The family of the selected group or person
    private lateinit var data: GroupData // Data of the selected group title
    private lateinit var selectedId: String
    private lateinit var selected: Person

    override fun onCreateContextMenu(menu: ContextMenu, view: View, info: ContextMenuInfo?) {
        family = view.getTag(R.id.tag_family) as Family
        if (view.getTag(R.id.tag_object) != null) { // Long press on a group title
            data = view.getTag(R.id.tag_object) as GroupData
            // Is a parent family
            if (data.type == FamilyType.ORIGIN || data.type == FamilyType.HALF || data.type == FamilyType.ORIGIN_SMALL) {
                // Move lineage family before
                if (data.listIndex > 0 && groupList[data.listIndex - 1].lineageIndex != data.lineageIndex) {
                    menu.add(0, 300, 0, R.string.move_before)
                }
                // Move origin or half family before
                if (data.groupIndex > 0) {
                    menu.add(0, 301, 0, R.string.move_before)
                }
                // Move lineage family after
                if (data.listIndex < groupList.size - 1 && groupList[data.listIndex + 1].lineageIndex != data.lineageIndex) {
                    menu.add(0, 302, 0, R.string.move_after)
                }
                // Move origin or half family after
                val actualParent = if (data.branch == Branch.PATERNAL) groupTree.first[data.lineageIndex].first[data.parentIndex]
                else groupTree.first[data.lineageIndex].second[data.parentIndex]
                if (data.groupIndex in 0..<actualParent.groups.lastIndex) {
                    menu.add(0, 303, 0, R.string.move_after)
                }
            } else { // Is an own family
                if (person.spouseFamilyRefs.size > 1) {
                    if (data.listIndex > 0) menu.add(0, 304, 0, R.string.move_before)
                    if (data.listIndex >= 0 && data.listIndex < person.spouseFamilyRefs.size - 1) menu.add(0, 305, 0, R.string.move_after)
                }
            }
            menu.add(0, 306, 0, R.string.delete)
        } else { // Long press on a person
            selectedId = view.tag as String
            selected = Global.gc.getPerson(selectedId)
            menu.add(0, 310, 0, R.string.diagram)
            val familyLabels = selected.getFamilyLabels(requireContext(), family)
            if (familyLabels[0] != null) menu.add(0, 311, 0, familyLabels[0])
            if (familyLabels[1] != null) menu.add(0, 312, 0, familyLabels[1])
            menu.add(0, 313, 0, R.string.modify)
            if (FamilyActivity.findParentFamilyRef(selected, family) != null) menu.add(0, 314, 0, R.string.lineage)
            menu.add(0, 315, 0, R.string.unlink)
            if (selected != person) // Here cannot delete himself
                menu.add(0, 316, 0, R.string.delete)
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // Family
            300 -> moveLineageFamilyRef(-1) // Move before
            301 -> moveParentFamilyRef(-1)
            302 -> moveLineageFamilyRef(1) // Move after
            303 -> moveParentFamilyRef(1)
            304 -> moveOwnFamilyRef(-1) // Move before
            305 -> moveOwnFamilyRef(1) // Move after
            306 -> { // Delete
                AlertDialog.Builder(requireContext()).setMessage(R.string.really_delete_family)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        family.delete()
                        refreshAll()
                    }
                    .setNeutralButton(android.R.string.cancel, null).show()
            }
            // Person
            310 -> U.whichParentsToShow(context, selected, 1) // Diagram
            311 -> U.whichParentsToShow(context, selected, 2) // Family as child
            312 -> U.whichSpousesToShow(context, selected, family) // Family as partner
            313 -> { // Edit
                val intent = Intent(context, PersonEditorActivity::class.java)
                intent.putExtra(Extra.PERSON_ID, selectedId)
                startActivity(intent)
            }
            314 -> FamilyActivity.chooseLineage(context, selected, family) // Lineage
            315 -> { // Unlink
                FamilyActivity.disconnect(selectedId, family)
                save(true, family, selected)
                refreshAll()
                U.deleteEmptyFamilies(context, { this.refreshOptionsMenu() }, false, family)
            }
            316 -> { // Delete
                AlertDialog.Builder(requireContext()).setMessage(R.string.really_delete_person)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        selected.delete()
                        refreshAll()
                        U.deleteEmptyFamilies(context, { this.refreshOptionsMenu() }, false, family)
                    }.setNeutralButton(R.string.cancel, null).show()
            }
            else -> return false
        }
        return true
    }

    private fun moveLineageFamilyRef(direction: Int) {
        Collections.swap(person.parentFamilyRefs, data.lineageIndex, data.lineageIndex + direction)
        save(true, person)
        refreshAll()
    }

    private fun moveParentFamilyRef(direction: Int) {
        val family = person.getParentFamilies(Global.gc)[data.lineageIndex]
        if (data.branch == Branch.PATERNAL)
            Collections.swap(family.getHusbands(Global.gc)[data.parentIndex].spouseFamilyRefs, data.groupIndex, data.groupIndex + direction)
        else if (data.branch == Branch.MATERNAL)
            Collections.swap(family.getWives(Global.gc)[data.parentIndex].spouseFamilyRefs, data.groupIndex, data.groupIndex + direction)
        save(true, person)
        refreshAll()
    }

    private fun moveOwnFamilyRef(direction: Int) {
        Collections.swap(person.spouseFamilyRefs, data.listIndex, data.listIndex + direction)
        save(true, person)
        refreshAll()
    }

    /**
     * Refreshes all the content.
     */
    private fun refreshAll() {
        (requireActivity() as ProfileActivity).refresh()
    }

    /**
     * Refreshes options menu only.
     */
    private fun refreshOptionsMenu() {
        activity.invalidateOptionsMenu() // Can't call requireActivity() here because the fragment is already detached
    }
}
