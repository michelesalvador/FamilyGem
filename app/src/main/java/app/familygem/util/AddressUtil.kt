package app.familygem.util

import org.folg.gedcom.model.Address

/**
 * Writes the address concatenating all fields.
 * @param singleLine Comma-separated single line or multi-line
 */
fun Address.toString(singleLine: Boolean): String {
    var txt = ""
    val br = if (singleLine) ", " else "\n"
    if (value != null) txt = value + br
    if (addressLine1 != null) txt += addressLine1 + br
    if (addressLine2 != null) txt += addressLine2 + br
    if (addressLine3 != null) txt += addressLine3 + br
    if (postalCode != null || city != null || state != null) {
        if (postalCode != null) txt += "$postalCode "
        if (city != null) txt += "$city "
        if (state != null) txt += state
        txt = txt.trim() + br
    }
    if (country != null) txt += country
    if (txt.endsWith(br)) txt = txt.substring(0, txt.length - br.length)
    return txt
}
