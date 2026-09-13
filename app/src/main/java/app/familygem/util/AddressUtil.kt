package app.familygem.util

import org.folg.gedcom.model.Address

/**
 * Writes the address concatenating all available fields.
 * @param singleLine Comma-separated single line or multi-line
 */
fun Address.toString(singleLine: Boolean): String {
    val builder = StringBuilder()
    val delimiter = if (singleLine) ", " else "\n"
    if (value != null) builder.append(value).append(delimiter)
    if (addressLine1 != null) builder.append(addressLine1).append(delimiter)
    if (addressLine2 != null) builder.append(addressLine2).append(delimiter)
    if (addressLine3 != null) builder.append(addressLine3).append(delimiter)
    if (postalCode != null) builder.append(postalCode).append(if (city != null) " " else if (state != null) ", " else delimiter)
    if (city != null) builder.append(city).append(if (state != null) ", " else delimiter)
    if (state != null) builder.append(state).append(delimiter)
    if (country != null) builder.append(country)
    return builder.toString().trim { it <= ' ' || it == ',' }
}
