package app.familygem.merge

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.familygem.F
import app.familygem.Global
import app.familygem.Settings.Tree
import app.familygem.U
import app.familygem.constant.Extra
import app.familygem.constant.Gender
import app.familygem.util.TreeUtils
import app.familygem.visitor.ListOfSourceCitations
import app.familygem.visitor.MediaContainersGuarded
import app.familygem.visitor.MediaList
import app.familygem.visitor.NoteContainersGuarded
import kotlinx.coroutines.*
import org.apache.commons.io.FileUtils
import org.folg.gedcom.model.*
import java.io.File
import java.io.FileInputStream
import kotlin.collections.set

class MergeViewModel(state: SavedStateHandle) : ViewModel() {

    val firstNum: Int // ID of the first tree
    val secondNum = MutableLiveData<Int>() // ID of the second tree
    var newNum = 0 // ID of the third tree created from first and second
    val firstTree: Tree
    lateinit var secondTree: Tree
    val trees = Global.settings.trees.filter { it.grade < 20 } as MutableList<Tree> // Derived and exhausted trees are excluded
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
        firstNum = state.get<Int>(Extra.TREE_ID)!!
        firstTree = Global.settings.getTree(firstNum)
        trees.remove(firstTree)

        // Populates the records array with the 7 types
        records.add(Person::class.java, "I")
        records.add(Family::class.java, "F")
        records.add(Media::class.java, "M")
        records.add(Note::class.java, "T")
        records.add(Source::class.java, "S")
        records.add(Repository::class.java, "R")
        records.add(Submitter::class.java, "U")
    }

    fun setSecondTree(treeId: Int) {
        secondNum.value = treeId
        secondTree = Global.settings.getTree(treeId)
    }

    /**
     * Returns the person name or null.
     */
    private fun getNameValue(person: Person): String? {
        return if (person.names.any()) person.names[0].displayValue.replace("/", "").trim()
        else null
    }

    private suspend fun setState(stat: State) {
        withContext(Dispatchers.Main) { state.value = stat }
    }

    /**
     * Searches for persons with the same name in the first and second GEDCOM
     */
    fun findMatches() {
        coroutine = viewModelScope.launch(Dispatchers.Default) {
            setState(State.ACTIVE)
            var tempGedcom = TreeUtils.readJson(firstNum)
            if (tempGedcom != null) firstGedcom = tempGedcom
            if (isActive) tempGedcom = TreeUtils.readJson(secondNum.value!!) else return@launch
            if (tempGedcom != null) secondGedcom = tempGedcom
            personMatches.clear()
            for (person1 in firstGedcom.people) {
                yield()
                val name1 = getNameValue(person1)
                if (name1 != null) {
                    for (person2 in secondGedcom.people) {
                        val name2 = getNameValue(person2)
                        if (name2 != null && name1.equals(name2, true)) {
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
        // Checks if actual matching person has mergeable parents and adds them to matches
        if (will == Will.MERGE && state.value != State.ACTIVE) { // In case the user navigated back
            if (match.left.parentFamilyRefs.any() && match.right.parentFamilyRefs.any()) {
                val firstFamily = match.left.getParentFamilies(firstGedcom)[0]
                val secondFamily = match.right.getParentFamilies(secondGedcom)[0]
                if (firstFamily.husbandRefs.any() && secondFamily.husbandRefs.any()) {
                    val firstFather = firstFamily.getHusbands(firstGedcom)[0]
                    val secondFather = secondFamily.getHusbands(secondGedcom)[0]
                    personMatches.addMatch(PersonMatch(firstFather, secondFather))
                }
                if (firstFamily.wifeRefs.any() && secondFamily.wifeRefs.any()) {
                    val firstMother = firstFamily.getWives(firstGedcom)[0]
                    val secondMother = secondFamily.getWives(secondGedcom)[0]
                    personMatches.addMatch(PersonMatch(firstMother, secondMother))
                }
            }
            // Checks if actual matching person has mergeable spouses and adds them to matches
            if (match.left.spouseFamilyRefs.any() && match.right.spouseFamilyRefs.any()) {
                // TODO: find first and second non-matching partners to propose to merge them
                //personMatches.addMatch(PersonMatch(firstPartner, secondPartner))
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

    /**
     * @param position false = left, true = right
     */
    fun getPerson(position: Boolean): Person {
        return if (position) personMatches[actualMatch].right else personMatches[actualMatch].left
    }

    private suspend fun copyMediaFiles(context: Context, sourceGedcom: Gedcom, sourceId: Int, destinationId: Int) {
        yield()
        // Collects existing media and file paths of source tree, but only from externalFilesDir
        val mediaList = MediaList(sourceGedcom, 0)
        sourceGedcom.accept(mediaList)
        val mediaPaths: MutableMap<Media, String> = HashMap()
        val extSourceDir = context.getExternalFilesDir(sourceId.toString())!!.path
        for (media in mediaList.list) {
            val path = F.mediaPath(sourceId, media)
            if (path != null && path.startsWith(extSourceDir)) mediaPaths[media] = path
        }
        // Copies the files to media folder of destination tree, renaming them if necessary
        if (mediaPaths.isNotEmpty()) {
            val extDestinationDir: File = context.getExternalFilesDir(destinationId.toString())!! // Creates the folder if not existing
            if (extDestinationDir.list()?.size == 0) { // Empty folder, probably because just created
                Global.settings.getTree(destinationId).dirs.add(extDestinationDir.path)
                // No need to save Global.settings here because TreeUtils.saveJson() will do
            }
            for (entry in mediaPaths.entries.iterator()) {
                yield()
                val path = entry.value
                val media = entry.key
                val sourceFile = File(path)
                val destinationFile = F.nextAvailableFileName(
                        extDestinationDir.path, path.substring(path.lastIndexOf('/') + 1))
                try {
                    val sourceStream = FileInputStream(sourceFile)
                    FileUtils.copyInputStreamToFile(sourceStream, destinationFile)
                } catch (ignored: Exception) {
                }
                // Updates file link inside media
                if (media.file.contains("/")) media.file = destinationFile.path
            }
        }
    }

    /**
     * Executes the merge of second GEDCOM into first GEDCOM.
     */
    private suspend fun doMerge() {
        // Loops in records of first GEDCOM seeking for maximum ID of each record type
        firstGedcom.people.forEach { findMaxId(it) }
        firstGedcom.families.forEach { findMaxId(it) }
        firstGedcom.media.forEach { findMaxId(it) }
        firstGedcom.notes.forEach { findMaxId(it) }
        firstGedcom.sources.forEach { findMaxId(it) }
        firstGedcom.repositories.forEach { findMaxId(it) }
        firstGedcom.submitters.forEach { findMaxId(it) }

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
        val firstFamiliesPool = firstGedcom.families.filter { family ->
            family.husbandRefs.any { ref -> personMatches.map { it.left.id }.contains(ref.ref) } ||
                    family.wifeRefs.any { ref -> personMatches.map { it.left.id }.contains(ref.ref) } ||
                    family.childRefs.any { ref -> personMatches.map { it.left.id }.contains(ref.ref) }
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
                val nonMatchingPartners = (family.getHusbands(firstGedcom) + family.getWives(firstGedcom)).minus(
                        match.spouseMatches.map { it.left }.toSet()
                ).size
                family.putExtension(SCORE, matchers - nonMatchingPartners)
            }
            // Selects the family with higher score or null
            val matchingFamily: Family? = firstFamiliesPool.maxByOrNull { it.getExtension(SCORE) as Int }
            if (matchingFamily == null) {
                match.destiny = Will.KEEP
            } else {
                val score = matchingFamily.getExtension(SCORE) as Int
                if (score <= 0) {
                    match.destiny = Will.KEEP
                } else {
                    // Non-matching spouse IDs of the second family
                    val freePartnersIds = match.right.husbandRefs.map { it.ref }.minus(match.spouseMatches.map { it.right.id }.toSet()) +
                            match.right.wifeRefs.map { it.ref }.minus(match.spouseMatches.map { it.right.id }.toSet())
                    if (freePartnersIds.isNotEmpty()) {
                        match.destiny = Will.KEEP
                    } else {
                        match.left = matchingFamily
                        match.destiny = Will.MERGE
                    }
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
                    val firstName = first.names[0]
                    val secondName = second.names[0]
                    if (firstName.nickname == null) firstName.nickname = secondName.nickname
                    if (firstName.type == null) firstName.type = secondName.type
                    if (firstName.prefix == null) firstName.prefix = secondName.prefix
                    if (firstName.given == null) firstName.given = secondName.given
                    if (firstName.surnamePrefix == null) firstName.surnamePrefix = secondName.surnamePrefix
                    if (firstName.surname == null) firstName.surname = secondName.surname
                    if (firstName.suffix == null) firstName.suffix = secondName.suffix
                    if (firstName.romn == null) firstName.romn = secondName.romn
                    if (firstName.fone == null) firstName.fone = secondName.fone
                    secondName.notes.forEach { firstName.addNote(it) }
                    secondName.noteRefs.forEach { firstName.addNoteRef(it) }
                    secondName.media.forEach { firstName.addMedia(it) }
                    secondName.mediaRefs.forEach { firstName.addMediaRef(it) }
                    secondName.sourceCitations.forEach { firstName.addSourceCitation(it) }
                    secondName.extensions.forEach { firstName.putExtension(it.key, it.value) }
                    second.names.filter { it != secondName }.forEach { first.addName(it) }
                } else {
                    second.names.forEach { first.addName(it) }
                }
                // All data from second person to the first one
                outer@ for (event in second.eventsFacts) {
                    // Avoids duplication of sex tag in first person
                    if (event.tag == "SEX") {
                        if (Gender.isDefined(first)) {
                            continue
                        } else if (Gender.isDefined(second)) {
                            val firstGender = Gender.getGender(first)
                            if (firstGender == Gender.UNKNOWN || firstGender == Gender.EMPTY) {
                                for (firstEvent in first.eventsFacts) {
                                    if (firstEvent.tag == "SEX") {
                                        firstEvent.value = event.value
                                        continue@outer
                                    }
                                }
                            }
                        }
                    }
                    first.addEventFact(event)
                }
                second.media.forEach { first.addMedia(it) }
                second.mediaRefs.forEach { first.addMediaRef(it) }
                second.notes.forEach { first.addNote(it) }
                second.noteRefs.forEach { first.addNoteRef(it) }
                second.sourceCitations.forEach { first.addSourceCitation(it) }
                second.extensions.forEach { first.putExtension(it.key, it.value) }
                // Adds the MATCHING extension to the second person to make them recognizable later
                second.putExtension(MATCHING, first.id)
            }
        }

        // Loops the familyMatches to bring data from the second GEDCOM into the first one
        familyMatches.forEach { match ->
            yield()
            val secondFamily = match.right
            if (match.destiny == Will.MERGE) {
                val firstFamily = match.left!!
                secondFamily.husbandRefs.filter { !isMatch(it) && !firstFamily.husbandRefs.contains(it) }.forEach {
                    firstFamily.addHusband(it)
                }
                secondFamily.wifeRefs.filter { !isMatch(it) && !firstFamily.wifeRefs.contains(it) }.forEach {
                    firstFamily.addWife(it)
                }
                secondFamily.childRefs.filter { !isMatch(it) && !firstFamily.childRefs.contains(it) }.forEach {
                    firstFamily.addChild(it)
                }
                secondFamily.eventsFacts.forEach { firstFamily.addEventFact(it) }
                secondFamily.media.forEach { firstFamily.addMedia(it) }
                secondFamily.mediaRefs.forEach { firstFamily.addMediaRef(it) }
                secondFamily.notes.forEach { firstFamily.addNote(it) }
                secondFamily.noteRefs.forEach { firstFamily.addNoteRef(it) }
                secondFamily.sourceCitations.forEach { firstFamily.addSourceCitation(it) }
                // Adds the extension to use it later
                secondFamily.putExtension(MATCHING, firstFamily.id)
            } else if (match.destiny == Will.KEEP) {
                match.childMatches.forEach {
                    val parentRef = ParentFamilyRef()
                    parentRef.ref = secondFamily.id
                    parentRef.putExtension(UPDATE_REF, true) // To recognize it later
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
        for (person in secondGedcom.people) {
            yield()
            if (isEligible(person)) {
                if (checkNotDone(person))
                    person.id = newId
                secondGedcom.families.forEach { family ->
                    for (husbandRef in family.husbandRefs) {
                        if (husbandRef.ref == oldId && checkNotDone(husbandRef))
                            husbandRef.ref = newId
                    }
                    for (wifeRef in family.wifeRefs) {
                        if (wifeRef.ref == oldId && checkNotDone(wifeRef))
                            wifeRef.ref = newId
                    }
                    for (childRef in family.childRefs) {
                        if (childRef.ref == oldId && checkNotDone(childRef))
                            childRef.ref = newId
                    }
                }
            }
        }
        for (family in secondGedcom.families) {
            yield()
            if (isEligible(family)) {
                if (checkNotDone(family))
                    family.id = newId
                secondGedcom.people.forEach { person ->
                    person.parentFamilyRefs.forEach {
                        if (it.ref == oldId && checkNotDone(it))
                            it.ref = newId
                    }
                    person.spouseFamilyRefs.forEach {
                        if (it.ref == oldId && checkNotDone(it))
                            it.ref = newId
                    }
                }
                // Updates first GEDCOM too
                firstGedcom.people.forEach { person ->
                    person.parentFamilyRefs.forEach {
                        if (it.ref == oldId && it.getExtension(UPDATE_REF) != null && checkNotDone(it))
                            it.ref = newId
                    }
                    person.spouseFamilyRefs.forEach {
                        if (it.ref == oldId && it.getExtension(UPDATE_REF) != null && checkNotDone(it))
                            it.ref = newId
                    }
                }
            }
        }
        for (media in secondGedcom.media) {
            yield()
            if (isEligible(media)) {
                media.id = newId
                MediaContainersGuarded(secondGedcom, oldId, newId, false)
            }
        }
        for (note in secondGedcom.notes) {
            yield()
            if (isEligible(note)) {
                note.id = newId
                NoteContainersGuarded(secondGedcom, oldId, newId, false)
            }
        }
        for (source in secondGedcom.sources) {
            yield()
            if (isEligible(source)) {
                source.id = newId
                val citations = ListOfSourceCitations(secondGedcom, oldId)
                for (triplet in citations.list) {
                    if (checkNotDone(triplet.citation))
                        triplet.citation.ref = newId
                }
            }
        }
        for (repo in secondGedcom.repositories) {
            yield()
            if (isEligible(repo)) {
                repo.id = newId
                for (source in secondGedcom.sources) {
                    val repoRef = source.repositoryRef
                    if (repoRef != null && repoRef.ref == oldId && checkNotDone(repoRef))
                        repoRef.ref = newId
                }
            }
        }
        for (submitter in secondGedcom.submitters) {
            yield()
            if (isEligible(submitter)) {
                submitter.id = newId
            }
        }

        // Deletes persons and families with MATCHING extension because they are duplicate
        secondGedcom.people = secondGedcom.people.filter { it.getExtension(MATCHING) == null }
        secondGedcom.families = secondGedcom.families.filter { it.getExtension(MATCHING) == null }

        // Removes guardian extensions from second GEDCOM
        secondGedcom.people.forEach { person ->
            yield()
            removeGuardian(person)
            for (ref in person.parentFamilyRefs) removeGuardian(ref)
            for (ref in person.spouseFamilyRefs) removeGuardian(ref)
        }
        secondGedcom.families.forEach { family ->
            yield()
            removeGuardian(family)
            for (ref in family.husbandRefs) removeGuardian(ref)
            for (ref in family.wifeRefs) removeGuardian(ref)
            for (ref in family.childRefs) removeGuardian(ref)
        }
        secondGedcom.sources.forEach { source ->
            yield()
            val citations = ListOfSourceCitations(secondGedcom, source.id)
            for (triplet in citations.list) removeGuardian(triplet.citation)
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
        secondGedcom.people.forEach { firstGedcom.addPerson(it) }
        secondGedcom.families.forEach { firstGedcom.addFamily(it) }
        secondGedcom.media.forEach { firstGedcom.addMedia(it) }
        secondGedcom.notes.forEach { firstGedcom.addNote(it) }
        secondGedcom.sources.forEach { firstGedcom.addSource(it) }
        secondGedcom.repositories.forEach { firstGedcom.addRepository(it) }
        secondGedcom.submitters.forEach { firstGedcom.addSubmitter(it) }
    }

    /**
     * Receives the spouse/child ref of a "right" (second) person and finds whether exists in matches as mergeable.
     */
    private fun isMatch(ref: SpouseRef): Boolean {
        return personMatches.any { it.right.id == ref.ref && it.destiny == Will.MERGE }
    }

    /**
     * Populates [records] with the maximum ID number of a record.
     */
    private fun findMaxId(record: ExtensionContainer) {
        val aClass: Class<*> = record.javaClass
        try {
            val id = aClass.getMethod("getId").invoke(record) as String
            val num = U.extractNum(id)
            if (num > records.getMax(aClass)) records.setMax(aClass, num)
        } catch (ignored: Exception) {
        }
    }

    /**
     * Finds a new ID for a record to be merged and returns true.
     */
    private fun isEligible(record: ExtensionContainer): Boolean {
        val aClass: Class<*> = record.javaClass
        try {
            oldId = aClass.getMethod("getId").invoke(record) as String
        } catch (ignored: java.lang.Exception) {
        }
        newId = record.getExtension(MATCHING) as String?
        if (newId == null) {
            var maxNum = records.getMax(aClass)
            if (maxNum >= U.extractNum(oldId)) {
                maxNum++
                newId = records.getPrefix(aClass) + maxNum
                records.setMax(aClass, maxNum)
            }
        }
        return newId != null
    }

    /**
     * Checks if the GUARDIAN extension doesn't exist and puts it in the object.
     */
    private fun checkNotDone(objct: ExtensionContainer): Boolean {
        if (objct.getExtension(GUARDIAN) == null) {
            objct.putExtension(GUARDIAN, true)
            return true
        }
        return false
    }

    /**
     * Removes all extensions.
     */
    private fun removeExtensions(objct: ExtensionContainer) {
        objct.getExtension(UPDATE_REF)?.let { objct.extensions.remove(UPDATE_REF) }
        removeGuardian(objct)
    }

    /**
     * Removes the guardian extension.
     */
    private fun removeGuardian(objct: ExtensionContainer) {
        objct.getExtension(GUARDIAN)?.let { objct.extensions.remove(GUARDIAN) }
        if (objct.extensions.isEmpty()) objct.extensions = null
    }

    /**
     * Merges second GEDCOM into first GEDCOM.
     */
    fun performAnnexMerge(context: Context) {
        coroutine = viewModelScope.launch(Dispatchers.Default) {
            setState(State.ACTIVE)
            copyMediaFiles(context, secondGedcom, secondNum.value!!, firstNum)
            doMerge()
            if (isActive) TreeUtils.saveJson(firstGedcom, firstNum) // Saves also Global.settings through Notifier
            setState(if (isActive) State.COMPLETE else State.QUIET)
        }
    }

    /*
    * Generates a third GEDCOM from first and second.
    */
    fun performGenerateMerge(context: Context, title: String) {
        coroutine = viewModelScope.launch(Dispatchers.Default) {
            setState(State.ACTIVE)
            newNum = Global.settings.max() + 1
            val persons = firstGedcom.people.size + secondGedcom.people.size
            val generations = firstTree.generations.coerceAtLeast(secondTree.generations)
            Global.settings.addTree(Tree(newNum, title, null, persons, generations, firstTree.root, null, 0))
            copyMediaFiles(context, firstGedcom, firstNum, newNum)
            copyMediaFiles(context, secondGedcom, secondNum.value!!, newNum)
            doMerge()
            if (isActive) TreeUtils.saveJson(firstGedcom, newNum)
            setState(if (isActive) State.COMPLETE else State.QUIET)
        }
    }

    /**
     * List of 7 record types.
     */
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
            val prefix: String) { // "I", "F", "T"...
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
            spouseMatches.forEach { str += "\tSpouse: $it\n" }
            childMatches.forEach { str += "\tChild: $it\n" }
            return str
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
        // Creates or retrieve the match containing the provided second (right) family
        fun getMatch(secondFamily: Family): FamilyMatch {
            return if (none { it.right == secondFamily }) {
                val match = FamilyMatch(null, secondFamily)
                add(match)
                return match
            } else filter { it.right == secondFamily }[0]
        }

        override fun toString(): String {
            var str = ""
            forEach { str += "$it" }
            return str
        }
    }
}
