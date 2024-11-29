package app.familygem.util

import org.folg.gedcom.model.EventFact
import org.folg.gedcom.model.Person

/** [sexFact] comes already with the "SEX" tag, or it is completely null. */
class SexUtil(private var sexFact: EventFact? = null) {

    val value = sexFact?.value

    fun isMale(): Boolean {
        return value == "M"
    }

    fun isFemale(): Boolean {
        return value == "F"
    }

    /** Sex value is either "M", "F" or something other than "U". */
    fun isDefined(): Boolean {
        return value == "M" || value == "F" || value != null && value != "U"
    }

    /** Sex event is null. */
    fun isMissing(): Boolean {
        return sexFact == null
    }

    /** No sex tag, null value or "U" value. */
    fun isUndefined(): Boolean {
        return value == null || value == "U"
    }

    internal fun setSex(person: Person, newSex: SexUtil) {
        if (sexFact == null) {
            sexFact = EventFact()
            sexFact?.tag = "SEX"
            person.addEventFact(sexFact)
        }
        sexFact?.value = newSex.value
    }

    override fun toString(): String {
        if (sexFact == null) return "null"
        return "SEX $value"
    }
}
