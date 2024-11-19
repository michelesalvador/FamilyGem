package app.familygem.util

import app.familygem.GedcomDateConverter
import app.familygem.Global
import app.familygem.R
import org.folg.gedcom.model.EventFact

/** Composes the title of an event fact. */
fun EventFact.writeTitle(): String {
    val string = when (tag) {
        "BIRT" -> R.string.birth
        "BURI" -> R.string.burial
        "CHR" -> R.string.christening
        "DEAT" -> R.string.death
        "EVEN" -> R.string.event
        "OCCU" -> R.string.occupation
        "RESI" -> R.string.residence
        "SEX" -> R.string.sex
        else -> 0
    }
    var txt = if (string != 0) Global.context.getString(string) else displayType
    if (type != null) txt += " ($type)"
    return txt
}

/** Writes all event fields in a multi-line string. */
fun EventFact.writeContent(): String {
    val builder = StringBuilder()
    if (value != null) {
        if (value == "Y" && tag != null && (tag == "BIRT" || tag == "CHR" || tag == "DEAT" || tag == "MARR" || tag == "DIV"))
            builder.append(Global.context.getString(R.string.yes)).append("\n")
        else builder.append(value).append("\n")
    }
    //if (type != null) builder.append(type).append("\n") // Included in event title
    if (date != null) builder.append(GedcomDateConverter(date).writeDateLong()).append("\n")
    if (place != null) builder.append(place).append("\n")
    if (address != null) builder.append(address.toString(true)).append("\n")
    if (cause != null) builder.append(cause).append("\n")
    if (www != null) builder.append(www).append("\n")
    if (email != null) builder.append(email).append("\n")
    if (phone != null) builder.append(phone).append("\n")
    if (fax != null) builder.append(fax)
    return builder.toString().trim()
}

/**
 * Deletes the main empty fields and possibly sets 'Y' as value.
 * @return True if something has changed
 */
fun EventFact.cleanUpFields(): Boolean {
    var changed = false
    if (type != null && type.isEmpty()) {
        type = null; changed = true
    }
    if (date != null && date.isEmpty()) {
        date = null; changed = true
    }
    if (place != null && place.isEmpty()) {
        place = null; changed = true
    }
    if (tag != null && (tag == "BIRT" || tag == "CHR" || tag == "DEAT" || tag == "MARR" || tag == "DIV")) {
        if (type == null && date == null && place == null && address == null && cause == null) {
            if (value != "Y") changed = true
            value = "Y"
        } else {
            if (value != null) changed = true
            value = null
        }
    }
    if (value != null && value.isEmpty()) {
        value = null; changed = true
    }
    return changed
}
