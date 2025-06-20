package app.familygem.merge

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.familygem.FileUri
import app.familygem.GedcomDateConverter
import app.familygem.Global
import app.familygem.Settings.Tree
import app.familygem.U
import app.familygem.constant.Extra
import app.familygem.util.ChangeUtil
import app.familygem.util.FileUtil
import app.familygem.util.TreeUtil
import app.familygem.util.getSpouseRefs
import app.familygem.util.getSpouses
import app.familygem.util.sex
import app.familygem.visitor.ListOfSourceCitations
import app.familygem.visitor.MediaContainersGuarded
import app.familygem.visitor.MediaLeaders
import app.familygem.visitor.NoteContainersGuarded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.folg.gedcom.model.ExtensionContainer
import org.folg.gedcom.model.Family
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.Note
import org.folg.gedcom.model.NoteContainer
import org.folg.gedcom.model.ParentFamilyRef
import org.folg.gedcom.model.Person
import org.folg.gedcom.model.PersonFamilyCommonContainer
import org.folg.gedcom.model.Repository
import org.folg.gedcom.model.Source
import org.folg.gedcom.model.SpouseFamilyRef
import org.folg.gedcom.model.SpouseRef
import org.folg.gedcom.model.Submitter
import java.io.File
import kotlin.math.abs


/** Merges second tree into first tree, or generates a third tree from the two. */
class MergeViewModel(state: SavedStateHandle) : ViewModel() {

    val firstNum = state.get<Int>(Extra.TREE_ID)!! // ID of the first tree
    val secondNum = MutableLiveData<Int>() // ID of the second tree
    var newNum = 0 // ID of the third tree created from first and second
    val firstTree: Tree = Global.settings.getTree(firstNum)
    lateinit var secondTree: Tree
    val trees = Global.settings.trees.filterNot { it == firstTree || it.grade >= 20 } // Derived and exhausted trees are excluded
    lateinit var firstGedcom: Gedcom
    lateinit var secondGedcom: Gedcom
    private val records = Records()
    val personMatches = PersonMatches()
    var actualMatch: Int = 0 // Actual person match
    private val familyMatches = FamilyMatches()
    private lateinit var oldId: String // Old ID of records
    private var newId: String? = null // New ID of records
    lateinit var coroutine: Job
    val state = MutableLiveData<State>() // The state of the coroutine

    companion object {
        private const val SCORE = "score"
        private const val MATCHING = "firstId"
        private const val UPDATE_REF = "secondFamilyRef" // This ref must be updated
        private const val GUARDIAN = "modifiedId" // The ID/Ref of this object has been modified
    }

    init {
        // Populates the records array with the 7 types
        records.apply {
            add(Person::class.java, "I")
            add(Family::class.java, "F")
            add(Media::class.java, "M")
            add(Note::class.java, "T")
            add(Source::class.java, "S")
            add(Repository::class.java, "R")
            add(Submitter::class.java, "U")
        }
    }

    fun setSecondTree(treeId: Int) {
        secondNum.value = treeId
        secondTree = Global.settings.getTree(treeId)
    }

    /** Returns the person name or null. */
    private fun getNameValue(person: Person): String? {
        if (person.names.any()) {
            val name = person.names[0].displayValue.replace("/", "").trim()
            if (name.isNotEmpty()) return name
        }
        return null
    }

    private suspend fun setState(stat: State) {
        withContext(Dispatchers.Main) { state.value = stat }
    }

    /** Loads first and second GEDCOM, optionally searching for people with similar name. */
    fun openTwoGedcom(searchMatches: Boolean) {
        coroutine = viewModelScope.launch(Dispatchers.Default) {
            setState(State.ACTIVE)
            var tempGedcom = TreeUtil.readJson(firstNum)
            if (tempGedcom != null) firstGedcom = tempGedcom else {
                setState(State.RESET)
                return@launch
            }
            if (isActive) tempGedcom = TreeUtil.readJson(secondNum.value!!) else return@launch
            if (tempGedcom != null) secondGedcom = tempGedcom else {
                setState(State.RESET)
                return@launch
            }
            personMatches.clear()
            if (searchMatches) firstGedcom.people.forEach { person1 ->
                yield()
                val name1 = getNameValue(person1)
                if (name1 != null) {
                    secondGedcom.people.forEach inner@{ person2 ->
                        val name2 = getNameValue(person2)
                        if (name2 != null && (name1.startsWith(name2, true) || name2.startsWith(name1, true))) {
                            // Avoids persons with too distant years of birth
                            val births1 = person1.eventsFacts.filter { it.tag == "BIRT" && it.date != null }
                            val births2 = person2.eventsFacts.filter { it.tag == "BIRT" && it.date != null }
                            if (births1.isNotEmpty() && births2.isNotEmpty()) {
                                val year1 = GedcomDateConverter(births1[0].date).year
                                val year2 = GedcomDateConverter(births2[0].date).year
                                if (year1 != Int.MAX_VALUE && year2 != Int.MAX_VALUE) {
                                    if (abs(year1 - year2) > 5) return@inner
                                }
                            }
                            personMatches.add(PersonMatch(person1, person2))
                        }
                    }
                }
            }
            setState(State.COMPLETE)
        }
    }

    fun nextMatch(will: Will): Boolean {
        val match = personMatches[actualMatch]
        match.destiny = will
        if (will == Will.MERGE && state.value != State.ACTIVE) { // In case the user navigated back
            // Adds to the matches mergeable parents of actual matching person
            match.left.getParentFamilies(firstGedcom).forEach { firstFamily ->
                match.right.getParentFamilies(secondGedcom).forEach { secondFamily ->
                    firstFamily.getHusbands(firstGedcom).forEach { firstFather ->
                        secondFamily.getHusbands(secondGedcom).forEach { secondFather ->
                            personMatches.addMatch(PersonMatch(firstFather, secondFather))
                        }
                    }
                    firstFamily.getWives(firstGedcom).forEach { firstMother ->
                        secondFamily.getWives(secondGedcom).forEach { secondMother ->
                            personMatches.addMatch(PersonMatch(firstMother, secondMother))
                        }
                    }
                }
            }
            // Adds to the matches mergeable spouses of actual matching person
            match.left.getSpouseFamilies(firstGedcom).forEach { firstFamily ->
                match.right.getSpouseFamilies(secondGedcom).forEach { secondFamily ->
                    firstFamily.getHusbands(firstGedcom).filterNot { it == match.left }.forEach { firstHusband ->
                        secondFamily.getHusbands(secondGedcom).filterNot { it == match.right }.forEach { secondHusband ->
                            personMatches.addMatch(PersonMatch(firstHusband, secondHusband))
                        }
                    }
                    firstFamily.getWives(firstGedcom).filterNot { it == match.left }.forEach { firstWife ->
                        secondFamily.getWives(secondGedcom).filterNot { it == match.right }.forEach { secondWife ->
                            personMatches.addMatch(PersonMatch(firstWife, secondWife))
                        }
                    }
                }
            }
        }
        return if (actualMatch < personMatches.size - 1) {
            actualMatch++
            true
        } else false
    }

    fun previousMatch() {
        if (actualMatch > 0)
            actualMatch--
    }

    /** Assigns the given will to all remaining matches. */
    fun resolveRemainingMatches(will: Will) {
        val actual = actualMatch
        while (nextMatch(will)) {
            // All done
        }
        actualMatch = actual // To display last visible match on back pressed from ResultFragment
    }

    /** @param position false = left, true = right */
    fun getPerson(position: Boolean): Person {
        return if (position) personMatches[actualMatch].right else personMatches[actualMatch].left
    }

    /** Copies media files from a source external storage to a destination external storage. */
    private suspend fun copyMediaFiles(context: Context, sourceGedcom: Gedcom, sourceId: Int, destinationId: Int) {
        yield()
        // Collects existing media and files of source tree, but only from externalFilesDir
        val mediaList = MediaLeaders()
        sourceGedcom.accept(mediaList)
        val mediaFiles: MutableList<Triple<File, Media, NoteContainer>> = mutableListOf()
        val extSourceDir = context.getExternalFilesDir(sourceId.toString())!!.path
        mediaList.list.forEach { wrapper ->
            val media = wrapper.media
            FileUri(context, media, sourceId, true).file?.let { file ->
                if (file.path.startsWith(extSourceDir)) {
                    mediaFiles.add(Triple(file, media, wrapper.leader))
                }
            }
        }
        // Copies the files to media folder of destination tree, renaming them if necessary
        if (mediaFiles.isNotEmpty()) {
            val extDestinationDir = context.getExternalFilesDir(destinationId.toString())!! // Creates the folder if not existing
            for (entry in mediaFiles.iterator()) {
                yield()
                val sourceFile = entry.first
                FileUtil.nextAvailableFileName(extDestinationDir, sourceFile.name, sourceFile).let { pair ->
                    val destinationFile = pair.first
                    if (pair.second) { // Source file has no duplicate, copies it
                        sourceFile.inputStream().use { inputStream -> destinationFile.outputStream().use { inputStream.copyTo(it) } }
                    }
                    // Updates file link inside the Media and change date of the leader
                    val media = entry.second
                    if (media.file != destinationFile.name) {
                        media.file = destinationFile.name
                        ChangeUtil.updateChangeDate(entry.third)
                    }
                }
            }
        }
        // Copies media folder paths
        val sourceTree = Global.settings.getTree(sourceId)
        val destinationTree = Global.settings.getTree(destinationId)
        destinationTree.dirs.addAll(sourceTree.dirs.filterNot { it == null || it.startsWith(extSourceDir) }) // External files dir excluded
        destinationTree.uris.addAll(sourceTree.uris.filterNot { it == null })
        // No need to save Global.settings here because TreeUtils.saveJson() will do
    }

    /** Executes the merge of second GEDCOM into first GEDCOM. */
    private suspend fun doMerge() {
        // Loops in records of first GEDCOM seeking for maximum ID of each record type
        firstGedcom.run {
            people.forEach { findMaxId(it) }
            families.forEach { findMaxId(it) }
            media.forEach { findMaxId(it) }
            notes.forEach { findMaxId(it) }
            sources.forEach { findMaxId(it) }
            repositories.forEach { findMaxId(it) }
            submitters.forEach { findMaxId(it) }
        }

        // Populates familyMatches with personMatches grouped in parent and spouse families
        personMatches.forEach { personMatch ->
            yield()
            if (personMatch.destiny == Will.MERGE) {
                personMatch.right.getParentFamilies(secondGedcom).forEach {
                    familyMatches.getMatch(it).childMatches.add(personMatch)
                }
                personMatch.right.getSpouseFamilies(secondGedcom).forEach {
                    familyMatches.getMatch(it).spouseMatches.add(personMatch)
                }
            }
        }

        // Families of first GEDCOM with at least one matching member
        val firstMatchingIds = personMatches.filter { it.destiny == Will.MERGE }.map { it.left.id }
        val firstFamiliesPool = firstGedcom.families.filter { family ->
            family.husbandRefs.any { firstMatchingIds.contains(it.ref) } ||
                    family.wifeRefs.any { firstMatchingIds.contains(it.ref) } ||
                    family.childRefs.any { firstMatchingIds.contains(it.ref) }
        }

        // Loops familyMatches to find the most suitable matching family from the first GEDCOM and assign them KEEP or MERGE
        familyMatches.forEach { match ->
            yield()
            // Loops possible families of the first GEDCOM assigning a score to each
            firstFamiliesPool.forEach { family ->
                // Counts members of first family matching members of second family
                val matchers = family.husbandRefs.count { ref -> match.spouseMatches.map { it.left.id }.contains(ref.ref) } +
                        family.wifeRefs.count { ref -> match.spouseMatches.map { it.left.id }.contains(ref.ref) } +
                        family.childRefs.count { ref -> match.childMatches.map { it.left.id }.contains(ref.ref) }
                // Counts non-matching spouses of first family
                val nonMatchingPartners = family.getSpouses(firstGedcom).minus(match.spouseMatches.map { it.left }.toSet()).size
                family.putExtension(SCORE, matchers - nonMatchingPartners)
            }
            // Selects the family with higher score or null
            val matchingFamily = firstFamiliesPool.maxByOrNull { it.getExtension(SCORE) as Int }
            if (matchingFamily == null) {
                match.destiny = Will.KEEP
            } else {
                val score = matchingFamily.getExtension(SCORE) as Int
                // Total spouses of first and second family must be less than two
                val totalSpouses = matchingFamily.getSpouses(firstGedcom).minus(match.spouseMatches.map { it.left }.toSet()).size +
                        match.right.getSpouseRefs().size
                if (score <= 0 || totalSpouses > 2) {
                    match.destiny = Will.KEEP
                } else {
                    match.left = matchingFamily
                    match.destiny = Will.MERGE
                }
            }
        }

        // Loops personMatches to bring persons data from the second GEDCOM into the first one
        personMatches.forEach { match ->
            yield()
            if (match.destiny == Will.MERGE) {
                val first = match.left
                val second = match.right
                // Merges the names of the second person into the first person
                val firstNameValue = getNameValue(first)
                val secondNameValue = getNameValue(second)
                if (firstNameValue != null && secondNameValue != null && firstNameValue.equals(secondNameValue, true)) {
                    first.names[0].apply { // First person name
                        val secondName = second.names[0]
                        if (nickname == null) nickname = secondName.nickname
                        if (type == null) type = secondName.type
                        if (prefix == null) prefix = secondName.prefix
                        if (given == null) given = secondName.given
                        if (surnamePrefix == null) surnamePrefix = secondName.surnamePrefix
                        if (surname == null) surname = secondName.surname
                        if (suffix == null) suffix = secondName.suffix
                        if (romn == null) romn = secondName.romn
                        if (fone == null) fone = secondName.fone
                        secondName.notes.forEach { addNote(it) }
                        secondName.noteRefs.forEach { addNoteRef(it) }
                        secondName.media.forEach { addMedia(it) }
                        secondName.mediaRefs.forEach { addMediaRef(it) }
                        secondName.sourceCitations.forEach { addSourceCitation(it) }
                        secondName.extensions.forEach { putExtension(it.key, it.value) }
                        second.names.filterNot { it == secondName }.forEach { first.addName(it) }
                    }
                } else {
                    second.names.forEach { first.addName(it) }
                }
                // All data from second person to the first one
                first.apply { // First person
                    val secondSex = second.sex
                    if (sex.isUndefined() && secondSex.isDefined()) sex = secondSex
                    second.eventsFacts.filterNot { it.tag == "SEX" }.forEach { addEventFact(it) }
                    second.media.forEach { addMedia(it) }
                    second.mediaRefs.forEach { addMediaRef(it) }
                    second.notes.forEach { addNote(it) }
                    second.noteRefs.forEach { addNoteRef(it) }
                    second.sourceCitations.forEach { addSourceCitation(it) }
                    second.extensions.forEach { putExtension(it.key, it.value) }
                    second.putExtension(MATCHING, id) // Adds the MATCHING extension to the second person to make them recognizable later
                }
                ChangeUtil.updateChangeDate(first)
            }
        }

        // Loops the familyMatches to bring data from the second GEDCOM into the first one
        familyMatches.forEach { match ->
            yield()
            val secondFamily = match.right
            if (match.destiny == Will.MERGE) {
                // Merges all content of second family into first family
                match.left?.apply { // First family
                    secondFamily.husbandRefs.filterNot { isMatch(it) || husbandRefs.contains(it) }.forEach { addHusband(it) }
                    secondFamily.wifeRefs.filterNot { isMatch(it) || wifeRefs.contains(it) }.forEach { addWife(it) }
                    secondFamily.childRefs.filterNot { isMatch(it) || childRefs.contains(it) }.forEach { addChild(it) }
                    secondFamily.eventsFacts.forEach { addEventFact(it) }
                    secondFamily.media.forEach { addMedia(it) }
                    secondFamily.mediaRefs.forEach { addMediaRef(it) }
                    secondFamily.notes.forEach { addNote(it) }
                    secondFamily.noteRefs.forEach { addNoteRef(it) }
                    secondFamily.sourceCitations.forEach { addSourceCitation(it) }
                    secondFamily.putExtension(MATCHING, id) // Adds the extension to use it later
                    ChangeUtil.updateChangeDate(this)
                }
            } else if (match.destiny == Will.KEEP) {
                // Adds to first family members (designated to be merged) references to second family
                match.childMatches.forEach {
                    val parentRef = ParentFamilyRef()
                    parentRef.ref = secondFamily.id
                    parentRef.putExtension(UPDATE_REF, true) // To updated it later
                    it.left.addParentFamilyRef(parentRef)
                }
                match.spouseMatches.forEach {
                    val spouseRef = SpouseFamilyRef()
                    spouseRef.ref = secondFamily.id
                    spouseRef.putExtension(UPDATE_REF, true)
                    it.left.addSpouseFamilyRef(spouseRef)
                }
            }
        }

        // Loops in records of the second GEDCOM to update their ID and every related ID
        secondGedcom.run {
            people.forEach { person ->
                yield()
                findIds(person)
                if (checkNotDone(person)) person.id = newId
                families.forEach { family ->
                    family.husbandRefs.filter { it.ref == oldId && checkNotDone(it) }.forEach { it.ref = newId }
                    family.wifeRefs.filter { it.ref == oldId && checkNotDone(it) }.forEach { it.ref = newId }
                    family.childRefs.filter { it.ref == oldId && checkNotDone(it) }.forEach { it.ref = newId }
                }
            }
            families.forEach { family ->
                yield()
                findIds(family)
                if (checkNotDone(family))
                    family.id = newId
                people.forEach { person ->
                    person.parentFamilyRefs.filter { it.ref == oldId && checkNotDone(it) }.forEach { it.ref = newId }
                    person.spouseFamilyRefs.filter { it.ref == oldId && checkNotDone(it) }.forEach { it.ref = newId }
                }
                // Updates first GEDCOM too
                firstGedcom.people.forEach { person ->
                    person.parentFamilyRefs.filter { it.ref == oldId && it.getExtension(UPDATE_REF) != null && checkNotDone(it) }
                        .forEach { it.ref = newId }
                    person.spouseFamilyRefs.filter { it.ref == oldId && it.getExtension(UPDATE_REF) != null && checkNotDone(it) }
                        .forEach { it.ref = newId }
                }
            }
            media.forEach { media ->
                yield()
                findIds(media)
                media.id = newId
                MediaContainersGuarded(secondGedcom, oldId, newId, false)
            }
            notes.forEach { note ->
                yield()
                findIds(note)
                note.id = newId
                NoteContainersGuarded(secondGedcom, oldId, newId, false)
            }
            sources.forEach { source ->
                yield()
                findIds(source)
                source.id = newId
                ListOfSourceCitations(secondGedcom, oldId).list.filter { checkNotDone(it.citation) }.forEach { it.citation.ref = newId }
            }
            repositories.forEach { repo ->
                yield()
                findIds(repo)
                repo.id = newId
                sources.map { it.repositoryRef }.filter { it != null && it.ref == oldId && checkNotDone(it) }.forEach { it.ref = newId }
            }
            submitters.forEach { submitter ->
                yield()
                findIds(submitter)
                submitter.id = newId
            }

            // Deletes persons and families with MATCHING extension because they are duplicate
            people = people.filter { it.getExtension(MATCHING) == null }
            families = families.filter { it.getExtension(MATCHING) == null }

            // Removes guardian extensions from second GEDCOM
            people.forEach { person ->
                yield()
                removeGuardian(person)
                person.parentFamilyRefs.forEach { removeGuardian(it) }
                person.spouseFamilyRefs.forEach { removeGuardian(it) }
            }
            families.forEach { family ->
                yield()
                removeGuardian(family)
                family.husbandRefs.forEach { removeGuardian(it) }
                family.wifeRefs.forEach { removeGuardian(it) }
                family.childRefs.forEach { removeGuardian(it) }
            }
            sources.forEach { source ->
                yield()
                ListOfSourceCitations(secondGedcom, source.id).list.forEach { removeGuardian(it.citation) }
                source.repositoryRef?.let { removeGuardian(it) }
            }
            // Removes extensions from first GEDCOM
            firstGedcom.people.forEach { person ->
                yield()
                person.parentFamilyRefs.forEach { removeExtensions(it) }
                person.spouseFamilyRefs.forEach { removeExtensions(it) }
            }
            MediaContainersGuarded(firstGedcom, null, null, true)
            NoteContainersGuarded(firstGedcom, null, null, true)
            yield()

            // Merges the records from selected tree into base tree
            people.forEach { firstGedcom.addPerson(it) }
            families.forEach { firstGedcom.addFamily(it) }
            media.forEach { firstGedcom.addMedia(it) }
            notes.forEach { firstGedcom.addNote(it) }
            sources.forEach { firstGedcom.addSource(it) }
            repositories.forEach { firstGedcom.addRepository(it) }
            submitters.forEach { firstGedcom.addSubmitter(it) }
        }
    }

    /** Receives the spouse/child ref of a "right" (second) person and finds whether exists in matches as mergeable. */
    private fun isMatch(ref: SpouseRef): Boolean {
        return personMatches.any { it.right.id == ref.ref && it.destiny == Will.MERGE }
    }

    /** Populates [records] with the maximum ID number of a record. */
    private fun findMaxId(record: ExtensionContainer) {
        val aClass: Class<*> = record.javaClass
        try {
            val id = aClass.getMethod("getId").invoke(record) as String
            val num = U.extractNum(id)
            if (num > records.getMax(aClass)) records.setMax(aClass, num)
        } catch (ignored: Exception) {
        }
    }

    /** Finds the old and new ID for a record to be merged. */
    private fun findIds(record: ExtensionContainer) {
        val aClass: Class<*> = record.javaClass
        try {
            oldId = aClass.getMethod("getId").invoke(record) as String
        } catch (ignored: Exception) {
        }
        newId = record.getExtension(MATCHING) as String?
        if (newId == null) {
            val nextNum = records.getMax(aClass) + 1
            newId = records.getPrefix(aClass) + nextNum
            records.setMax(aClass, nextNum)
        }
    }

    /** Checks if the GUARDIAN extension doesn't exist and puts it in the object. */
    private fun checkNotDone(obj: ExtensionContainer): Boolean {
        if (obj.getExtension(GUARDIAN) == null) {
            obj.putExtension(GUARDIAN, true)
            return true
        }
        return false
    }

    /** Removes all extensions. */
    private fun removeExtensions(obj: ExtensionContainer) {
        obj.getExtension(UPDATE_REF)?.let { obj.extensions.remove(UPDATE_REF) }
        removeGuardian(obj)
    }

    /** Removes the guardian extension. */
    private fun removeGuardian(obj: ExtensionContainer) {
        obj.getExtension(GUARDIAN)?.let { obj.extensions.remove(GUARDIAN) }
        if (obj.extensions.isEmpty()) obj.extensions = null
    }

    /** Merges second GEDCOM into first GEDCOM. */
    fun performAnnexMerge(context: Context) {
        coroutine = viewModelScope.launch(Dispatchers.Default) {
            setState(State.ACTIVE)
            copyMediaFiles(context, secondGedcom, secondNum.value!!, firstNum)
            doMerge()
            if (Global.settings.openTree == firstNum) Global.gc = firstGedcom // We don't want to modify Global.settings.openTree here
            else {
                firstTree.persons = firstGedcom.people.size
                firstTree.generations = TreeUtil.countGenerations(firstGedcom, U.getRootId(firstGedcom, firstTree))
                firstTree.media += secondTree.media
            }
            if (isActive) TreeUtil.saveJson(firstGedcom, firstNum, false) // Saves also Global.settings through Notifier
            setState(if (isActive) State.COMPLETE else State.QUIET)
        }
    }

    /** Generates a third GEDCOM from first and second. */
    fun performGenerateMerge(context: Context, title: String) {
        coroutine = viewModelScope.launch(Dispatchers.Default) {
            setState(State.ACTIVE)
            newNum = Global.settings.max() + 1
            val persons = firstGedcom.people.size + secondGedcom.people.size
            val generations = firstTree.generations.coerceAtLeast(secondTree.generations)
            Global.settings.addTree(Tree(newNum, title, persons, generations, firstTree.root, firstTree.settings, null, 0))
            copyMediaFiles(context, firstGedcom, firstNum, newNum)
            copyMediaFiles(context, secondGedcom, secondNum.value!!, newNum)
            doMerge()
            if (isActive) TreeUtil.saveJson(firstGedcom, newNum, false)
            setState(if (isActive) State.COMPLETE else State.QUIET)
        }
    }

    /** List of 7 record types. */
    private class Records : ArrayList<Record>() {
        fun add(theClass: Class<*>, prefix: String) {
            add(Record(theClass, prefix))
        }

        fun getMax(aClass: Class<*>): Int {
            for (record in this) {
                if (record.theClass == aClass) return record.max
            }
            return 0
        }

        fun setMax(aClass: Class<*>, num: Int) {
            for (record in this) {
                if (record.theClass == aClass) {
                    record.max = num
                    break
                }
            }
        }

        fun getPrefix(aClass: Class<*>): String? {
            for (record in this) {
                if (record.theClass == aClass) return record.prefix
            }
            return null
        }
    }

    private class Record(
        val theClass: Class<*>, // Person, Family, Note...
        val prefix: String // "I", "F", "T"...
    ) {
        var max = 0 // Maximum ID number for this type of record
    }

    private interface Matching {
        val left: PersonFamilyCommonContainer?
        val right: PersonFamilyCommonContainer
        var destiny: Will
    }

    class PersonMatch(override val left: Person, override val right: Person) : Matching {
        override var destiny = Will.NONE
        override fun toString(): String {
            return "${U.properName(left)}, ${U.properName(right)}, $destiny"
        }
    }

    class FamilyMatch(override var left: Family?, override val right: Family) : Matching {
        override var destiny = Will.NONE
        var spouseMatches = mutableListOf<PersonMatch>() // Spouse matches inside the right family
        var childMatches = mutableListOf<PersonMatch>() // Child matches inside the right family
        override fun toString(): String {
            var str = "${left?.id}, ${right.id}, $destiny\n"
            spouseMatches.forEach { str += "\tSpouse match: $it\n" }
            childMatches.forEach { str += "\tChild match: $it" }
            return str.trimEnd('\n')
        }
    }

    class PersonMatches : MutableList<PersonMatch> by mutableListOf() {
        fun addMatch(match: PersonMatch) { // Avoiding duplicates
            if (none { it.left == match.left && it.right == match.right }) add(match)
        }

        override fun toString(): String {
            var str = ""
            forEach { str += "$it\n" }
            return str
        }
    }

    class FamilyMatches : MutableList<FamilyMatch> by mutableListOf() {
        /** Creates or retrieve the match containing the provided second (right) family. */
        fun getMatch(secondFamily: Family): FamilyMatch {
            return if (none { it.right == secondFamily }) {
                val match = FamilyMatch(null, secondFamily)
                add(match)
                return match
            } else first { it.right == secondFamily }
        }

        override fun toString(): String {
            var str = ""
            forEach { str += "$it" }
            return str
        }
    }
}
