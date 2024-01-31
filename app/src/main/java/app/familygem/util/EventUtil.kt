package app.familygem.util

import org.folg.gedcom.model.EventFact

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
