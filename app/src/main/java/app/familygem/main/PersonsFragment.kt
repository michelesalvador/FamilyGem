package app.familygem.main

import android.content.Intent
import android.os.Bundle
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import app.familygem.GedcomDateConverter
import app.familygem.Global
import app.familygem.Memory
import app.familygem.PersonEditorActivity
import app.familygem.ProgressView
import app.familygem.R
import app.familygem.U
import app.familygem.constant.Choice
import app.familygem.constant.Extra
import app.familygem.constant.Format
import app.familygem.constant.Relation
import app.familygem.profile.ProfileActivity
import app.familygem.util.FileUtil.selectMainImage
import app.familygem.util.PersonUtil
import app.familygem.util.TreeUtil.save
import app.familygem.util.Util
import app.familygem.util.Util.caseString
import app.familygem.util.countRelatives
import app.familygem.util.delete
import app.familygem.util.getFamilyLabels
import app.familygem.util.getSpouseRefs
import app.familygem.util.sex
import app.familygem.util.writeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.folg.gedcom.model.Person
import org.joda.time.Days
import org.joda.time.LocalDate
import org.joda.time.Period
import org.joda.time.Years
import java.util.Locale
import kotlin.concurrent.timer

/** List of all people of the tree, searchable and sortable. */
class PersonsFragment : BaseFragment() {
    private val allPeople: MutableList<PersonWrapper> = ArrayList() // The immutable complete list of people
    private var selectedPeople: MutableList<PersonWrapper> = ArrayList() // Some persons selected by the search feature
    private val adapter = PeopleAdapter()
    private lateinit var progress: ProgressView
    private var prepareJob: Job? = null
    private var searchView: SearchView? = null
    private var order = Order.NONE
    private var idsAreNumeric = false

    private enum class Order {
        NONE,
        ID_ASC, ID_DESC,
        SURNAME_ASC, SURNAME_DESC,
        DATE_ASC, DATE_DESC,
        AGE_ASC, AGE_DESC,
        BIRTHDAY_ASC, BIRTHDAY_DESC,
        KIN_ASC, KIN_DESC;

        fun next(): Order {
            return entries[ordinal + 1]
        }

        fun prev(): Order {
            return entries[ordinal - 1]
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val view = inflater.inflate(R.layout.recyclerview, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
        val padding = U.dpToPx(8F)
        recyclerView.setPadding(padding, padding, padding, recyclerView.paddingBottom)
        recyclerView.adapter = adapter
        view.findViewById<View>(R.id.fab).setOnClickListener { startActivity(Intent(context, PersonEditorActivity::class.java)) }
        progress = view.findViewById(R.id.recycler_progress)
        setupFastScroller(recyclerView)
        return view
    }

    override fun showContent() {
        progress.visibility = View.VISIBLE
        if (prepareJob == null || prepareJob!!.isCompleted) { // To avoid ConcurrentModificationException
            prepareJob = lifecycleScope.launch(Dispatchers.Default) {
                // Recreates the list for some person modified, added or removed
                allPeople.clear()
                Global.gc.people.forEach {
                    allPeople.add(PersonWrapper(it))
                    // On version 0.9.2 all person's extensions was removed, replaced by PersonWrapper fields
                    it.extensions = null // TODO: remove on a future release
                }
                idsAreNumeric = verifyNumericIds() // Maybe some ID has been changed
                allPeople.forEach { it.completeFields() } // This could be time-consuming on a big tree
            }
        }
        // Updates displayed people, filtered or not, every second
        timer(period = 1000) {
            lifecycleScope.launch(Dispatchers.Main) {
                adapter.filter.filter(searchView?.query ?: "")
                if (prepareJob!!.isCompleted) {
                    progress.visibility = View.GONE
                    cancel()
                }
            }
        }
    }

    override fun isSearching(): Boolean {
        return searchView != null && searchView!!.query.isNotBlank()
    }

    private inner class PeopleAdapter : RecyclerView.Adapter<PersonHolder>(), Filterable {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonHolder {
            val personView = LayoutInflater.from(parent.context).inflate(R.layout.person_layout, parent, false)
            registerForContextMenu(personView)
            return PersonHolder(personView)
        }

        override fun onBindViewHolder(personHolder: PersonHolder, position: Int) {
            val person = selectedPeople[position].person
            val personView = personHolder.view
            personView.setTag(R.id.tag_object, person)
            personView.setTag(R.id.tag_position, position)

            var label: String? = null
            if (order == Order.ID_ASC || order == Order.ID_DESC) label = person.id
            else if (order == Order.SURNAME_ASC || order == Order.SURNAME_DESC) label = U.surname(person)
            else if (order == Order.BIRTHDAY_ASC || order == Order.BIRTHDAY_DESC) {
                val birthday = selectedPeople[position].birthday
                if (birthday > Int.MIN_VALUE) label = birthday.toString()
            } else if (order == Order.KIN_ASC || order == Order.KIN_DESC) label = selectedPeople[position].relatives.toString()
            val infoView = personView.findViewById<TextView>(R.id.person_info)
            if (label == null) infoView.visibility = View.GONE
            else {
                infoView.isAllCaps = false
                infoView.text = label
                infoView.visibility = View.VISIBLE
            }

            val nameView = personView.findViewById<TextView>(R.id.person_name)
            val name = U.properName(person)
            nameView.text = name
            nameView.visibility = if ((name.isBlank() && label != null)) View.GONE else View.VISIBLE

            val titleView = personView.findViewById<TextView>(R.id.person_title)
            val title = PersonUtil.writeTitles(person)
            if (title.isEmpty()) titleView.visibility = View.GONE
            else {
                titleView.text = title
                titleView.visibility = View.VISIBLE
            }
            val sex = person.sex
            val border = if (sex.isMale()) R.drawable.person_border_male
            else if (sex.isFemale()) R.drawable.person_border_female
            else R.drawable.person_border_undefined
            personView.findViewById<View>(R.id.person_border).setBackgroundResource(border)

            U.details(person, personView.findViewById(R.id.person_details))
            selectMainImage(person, personView.findViewById(R.id.person_image))
            personView.findViewById<View>(R.id.person_mourning).visibility =
                if (U.isDead(person)) View.VISIBLE else View.GONE
        }

        override fun getFilter(): Filter {
            return object : Filter() {
                override fun performFiltering(charSequence: CharSequence): FilterResults {
                    // Splits query by spaces and search all the words
                    val query = charSequence.trim().toString().lowercase(Locale.getDefault()).split("\\s+".toRegex()).dropWhile { it.isEmpty() }
                    // Instead of using selectedPeople, we create a new list to avoid IndexOutOfBoundsException when RecyclerView is scrolled
                    val filteredPeople: MutableList<PersonWrapper> = ArrayList()
                    if (query.isEmpty()) {
                        filteredPeople.addAll(allPeople)
                    } else {
                        outer@ for (wrapper in allPeople) {
                            if (wrapper.text != null) {
                                for (word in query) {
                                    if (!wrapper.text!!.contains(word)) {
                                        continue@outer
                                    }
                                }
                                filteredPeople.add(wrapper)
                            }
                        }
                    }
                    selectedPeople = filteredPeople
                    if (order != Order.NONE) sortPeople()
                    val filterResults = FilterResults()
                    filterResults.values = selectedPeople
                    return filterResults
                }

                override fun publishResults(cs: CharSequence, fr: FilterResults) {
                    notifyDataSetChanged()
                }
            }
        }

        override fun getItemCount(): Int {
            return selectedPeople.size
        }
    }

    private inner class PersonHolder(var view: View) : RecyclerView.ViewHolder(view), View.OnClickListener {
        init {
            view.setOnClickListener(this)
        }

        override fun onClick(view: View) {
            val relative = view.getTag(R.id.tag_object) as Person
            val intent = activity!!.intent
            // Chooses the relative and returns the values to DiagramFragment, ProfileActivity, FamilyActivity or SharingActivity
            if (intent.getBooleanExtra(Choice.PERSON, false)) {
                intent.putExtra(Extra.RELATIVE_ID, relative.id)
                // Searches for any existing family that can host the pivot
                val destination = intent.getStringExtra(Extra.DESTINATION)
                if (destination != null && destination == "EXISTING_FAMILY") {
                    val familyId = when (intent.getSerializableExtra(Extra.RELATION) as Relation) {
                        Relation.PARENT -> relative.spouseFamilyRefs.firstOrNull { it.ref != null }?.ref
                        Relation.SIBLING -> relative.parentFamilyRefs.firstOrNull { it.ref != null }?.ref
                        Relation.PARTNER -> relative.getSpouseFamilies(Global.gc).firstOrNull { it.getSpouseRefs().size < 2 }?.id
                        Relation.CHILD -> relative.getParentFamilies(Global.gc).firstOrNull { it.getSpouseRefs().size < 2 }?.id
                        else -> null
                    }
                    if (familyId != null) // addRelative() will use the found family
                        intent.putExtra(Extra.FAMILY_ID, familyId)
                    else // addRelative() will create a new family
                        intent.removeExtra(Extra.DESTINATION)
                }
                activity!!.setResult(AppCompatActivity.RESULT_OK, intent)
                activity!!.finish()
            } else { // Normal link to the person profile
                Memory.setLeader(relative)
                startActivity(Intent(context, ProfileActivity::class.java))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Resets the extra in case of leaving this fragment without choosing a person
        requireActivity().intent.removeExtra(Choice.PERSON)
    }

    /**
     * Checks if all people's IDs contain numbers.
     * @return False as soon as an ID contains only letters
     */
    private fun verifyNumericIds(): Boolean {
        out@ for (person in Global.gc.people) {
            for (character in person.id.toCharArray()) {
                if (Character.isDigit(character)) continue@out
            }
            return false
        }
        return true
    }

    private fun sortPeople() {
        selectedPeople.sortWith { wrapper1, wrapper2 ->
            val id1 = wrapper1.person.id
            val id2 = wrapper2.person.id
            return@sortWith when (order) {
                Order.ID_ASC -> if (idsAreNumeric) U.extractNum(id1) - U.extractNum(id2)
                else id1.compareTo(id2, true)
                Order.ID_DESC -> if (idsAreNumeric) U.extractNum(id2) - U.extractNum(id1)
                else id2.compareTo(id1, true)
                Order.SURNAME_ASC -> {
                    if (wrapper1.name == null && wrapper2.name == null) 0
                    else if (wrapper1.name == null) 1 // Null names go to the bottom
                    else if (wrapper2.name == null) -1
                    else wrapper1.name!!.compareTo(wrapper2.name!!)
                }
                Order.SURNAME_DESC -> {
                    if (wrapper1.name == null && wrapper2.name == null) 0
                    else if (wrapper1.name == null) 1
                    else if (wrapper2.name == null) -1
                    else wrapper2.name!!.compareTo(wrapper1.name!!)
                }
                Order.DATE_ASC -> {
                    if (wrapper2.date == Int.MAX_VALUE) -1 // Those without date go to the bottom
                    else if (wrapper1.date == Int.MAX_VALUE) 1
                    else wrapper1.date - wrapper2.date
                }
                Order.DATE_DESC -> {
                    if (wrapper1.date == Int.MAX_VALUE) 1
                    else if (wrapper2.date == Int.MAX_VALUE) -1
                    else wrapper2.date - wrapper1.date
                }
                Order.AGE_ASC -> wrapper1.age - wrapper2.age
                Order.AGE_DESC -> {
                    if (wrapper1.age == Int.MAX_VALUE) 1 // Those without age go to the bottom
                    else if (wrapper2.age == Int.MAX_VALUE) -1
                    else wrapper2.age - wrapper1.age
                }
                Order.BIRTHDAY_ASC -> {
                    if (wrapper2.birthday == 0) 1 // Otherwise with wrapper1 MIN_VALUE returns -2147483648
                    else wrapper2.birthday - wrapper1.birthday
                }
                Order.BIRTHDAY_DESC -> {
                    if (wrapper1.birthday == Int.MIN_VALUE) 1 // Those without birthday go to the bottom
                    else if (wrapper2.birthday == Int.MIN_VALUE) -1
                    else wrapper1.birthday - wrapper2.birthday
                }
                Order.KIN_ASC -> wrapper1.relatives - wrapper2.relatives
                Order.KIN_DESC -> wrapper2.relatives - wrapper1.relatives
                else -> 0
            }
        }
    }

    /**
     * Writes a string with surname and given name concatenated.
     * E.g. "salvadormichele " or "vallefrancesco maria " or " donatella ".
     */
    private fun getSurnameGivenName(person: Person): String? {
        val names = person.names
        if (names.isNotEmpty()) {
            val name = names[0]
            val value = name.value
            if (value != null || name.given != null || name.surname != null) {
                var given = ""
                var surname = " " // There must be a space to sort names without surname
                if (value != null) {
                    if (value.replace('/', ' ').trim { it <= ' ' }.isEmpty()) // Empty value
                        return null
                    if (value.indexOf('/') > 0) given = value.substring(0, value.indexOf('/')) // Given name before '/'
                    else if (value.indexOf('/') < 0) given = value // Name only without any '/'
                    else if (value.lastIndexOf('/') >= 0 && value.lastIndexOf('/') < value.length - 1) given =
                        value.substring(value.lastIndexOf('/') + 1).trim { it <= ' ' } // Something after last '/'

                    if (value.lastIndexOf('/') - value.indexOf('/') > 1) // If there is a surname between two '/'
                        surname = value.substring(value.indexOf('/') + 1, value.lastIndexOf('/'))
                    else if (name.surname != null) surname = name.surname
                    // Only the given name coming from the value could have a prefix,
                    // from getGiven() no, because it is already only the given name.
                    val prefix = name.prefix
                    if (prefix != null && given.startsWith(prefix)) given = given.substring(prefix.length).trim { it <= ' ' }
                } else {
                    if (name.given != null) given = name.given
                    if (name.surname != null) surname = name.surname
                }
                val surPrefix = name.surnamePrefix
                if (surPrefix != null && surname.startsWith(surPrefix)) surname = surname.substring(surPrefix.length).trim { it <= ' ' }
                return (surname + given).lowercase(Locale.getDefault())
            }
        }
        return null
    }

    var dateConverter: GedcomDateConverter = GedcomDateConverter("") // Here outside to initialize only once

    /**
     * Class to wrap a person of the list and all their relevant fields
     */
    private inner class PersonWrapper(val person: Person) {
        var text: String? = null // Single string with all names and events for search
        var name: String? = null // Surname and given name of the person
        var date: Int = Int.MAX_VALUE // Date in the format YYYYMMDD
        var age: Int = Int.MAX_VALUE // Age in days
        var birthday: Int = Int.MIN_VALUE // Negative days to the next birthday
        var relatives: Int = 0 // Number of near relatives

        fun completeFields() {
            // Writes one string concatenating all names and personal events
            val builder = StringBuilder()
            for (name in person.names) {
                builder.append(U.firstAndLastName(name, " ")).append(' ')
            }
            for (event in person.eventsFacts) {
                if (!("SEX" == event.tag || "Y" == event.value)) // Sex and 'Yes' excluded
                    builder.append(event.writeContent()).append(' ')
            }
            text = builder.toString().lowercase(Locale.getDefault())

            // Surname and given name concatenated
            name = getSurnameGivenName(person)

            // Finds the first date of the person's life
            for (event in person.eventsFacts) {
                if (event.date != null) {
                    dateConverter.analyze(event.date)
                    date = dateConverter.dateNumber
                    break
                }
            }

            // Calculates age and days to next birthday
            var start: GedcomDateConverter? = null
            var end: GedcomDateConverter? = null
            for (event in person.eventsFacts) {
                if (event.tag != null && event.tag == "BIRT" && event.date != null) {
                    start = GedcomDateConverter(event.date)
                    break
                }
            }
            for (event in person.eventsFacts) {
                if (event.tag != null && event.tag == "DEAT" && event.date != null) {
                    end = GedcomDateConverter(event.date)
                    break
                }
            }
            if (start != null && start.isSingleKind && !start.firstDate.isFormat(Format.OTHER)) {
                val treeSettings = Global.settings.currentTree.settings
                val startDate = LocalDate(start.firstDate.date)
                val now = if (treeSettings.customDate) LocalDate(treeSettings.fixedDate) else LocalDate.now()
                val living = !U.isDead(person) && Years.yearsBetween(startDate, now).years <= treeSettings.lifeSpan
                // Calculates the person age in days
                if (!start.firstDate.isFormat(Format.D_M)) {
                    if (living && end == null && (startDate.isBefore(now) || startDate.isEqual(now))) {
                        end = GedcomDateConverter(now.toDate())
                    }
                    if (end != null && end.isSingleKind && !end.firstDate.isFormat(Format.D_M)) {
                        val endDate = LocalDate(end.firstDate.date)
                        if (startDate.isBefore(endDate) || startDate.isEqual(endDate)) {
                            age = Days.daysBetween(startDate, endDate).days
                        }
                    }
                }
                // Counts the days remaining to the person next birthday
                if (living && !(start.firstDate.isFormat(Format.Y) || start.firstDate.isFormat(Format.M_Y))) {
                    val ageYears = Period(startDate, now).years
                    var nextBirthday = startDate.plusYears(ageYears)
                    if (nextBirthday.isBefore(now)) nextBirthday = startDate.plusYears(ageYears + 1)
                    birthday = Days.daysBetween(nextBirthday, now).days
                }
            }

            // Relatives number
            relatives = person.countRelatives()
        }
    }

    override fun updateToolbar(bar: ActionBar, menu: Menu, inflater: MenuInflater) {
        bar.title = allPeople.size.toString() + " " + caseString(if (allPeople.size == 1) R.string.person else R.string.persons)
        if (allPeople.size > 1) {
            // Search in PersonsFragment
            inflater.inflate(R.menu.search, menu) // This only makes appear the lens with the search field
            searchView = menu.findItem(R.id.search_item).actionView as SearchView
            searchView!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextChange(query: String): Boolean {
                    adapter.filter.filter(query)
                    return true
                }

                override fun onQueryTextSubmit(q: String): Boolean {
                    searchView!!.clearFocus()
                    return false
                }
            })
            // Sort by menu
            inflater.inflate(R.menu.sort_by, menu)
            val subMenu = menu.findItem(R.id.sortBy).subMenu
            if (Global.settings.expert) subMenu!!.add(0, 1, 0, R.string.id)
            subMenu!!.add(0, 2, 0, R.string.surname)
            subMenu.add(0, 3, 0, R.string.date)
            subMenu.add(0, 4, 0, R.string.age)
            subMenu.add(0, 5, 0, R.string.birthday)
            subMenu.add(0, 6, 0, R.string.number_relatives)
        }
    }

    override fun selectItem(id: Int) {
        if (id in 1..6) {
            // Clicking twice the same menu item switches sorting ASC and DESC
            order = if (order == Order.entries[id * 2 - 1]) order.next()
            else if (order == Order.entries[id * 2]) order.prev()
            else Order.entries[id * 2 - 1]
            sortPeople()
            adapter.notifyDataSetChanged()
            // Updates sorting of people in global GEDCOM too
            if (selectedPeople.size == Global.gc.people.size) { // Only if there is no filtering
                Global.gc.people = selectedPeople.map { it.person }
                save(false) // Too much?
                if (Global.shouldSave) (requireActivity() as MainActivity).furnishMenu() // Displays the Save button
            }
        }
    }

    // Context menu
    private lateinit var person: Person
    private var position = 0

    override fun onCreateContextMenu(menu: ContextMenu, view: View, info: ContextMenuInfo?) {
        person = view.getTag(R.id.tag_object) as Person
        position = view.getTag(R.id.tag_position) as Int
        menu.add(1, 0, 0, R.string.diagram)
        val familyLabels = person.getFamilyLabels(requireContext(), null)
        if (familyLabels[0] != null) menu.add(1, 1, 0, familyLabels[0])
        if (familyLabels[1] != null) menu.add(1, 2, 0, familyLabels[1])
        menu.add(1, 3, 0, R.string.modify)
        if (Global.settings.expert) menu.add(1, 4, 0, R.string.edit_id)
        menu.add(1, 5, 0, R.string.delete)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (item.groupId != 1) return false
        when (item.itemId) {
            0 -> U.whichParentsToShow(context, person, 1) // Display diagram
            1 -> U.whichParentsToShow(context, person, 2) // Family as child
            2 -> U.whichSpousesToShow(context, person) // Family as partner
            3 -> { // Edit person
                val intent = Intent(context, PersonEditorActivity::class.java)
                intent.putExtra(Extra.PERSON_ID, person.id)
                startActivity(intent)
            }
            4 -> U.editId(context, person) { adapter.notifyDataSetChanged() } // Edit ID
            5 -> { // Delete person
                Util.confirmDelete(requireContext()) {
                    val families = person.delete()
                    selectedPeople.removeAt(position)
                    allPeople.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    adapter.notifyItemRangeChanged(position, selectedPeople.size - position)
                    (requireActivity() as MainActivity).refreshInterface()
                    U.deleteEmptyFamilies(context, null, false, *families)
                }
            }
            else -> return false
        }
        return true
    }
}
