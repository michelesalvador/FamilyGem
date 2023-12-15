package app.familygem.util

import app.familygem.R
import app.familygem.util.Util.string
import org.folg.gedcom.model.Submitter

fun Submitter.writeName(): String {
    var name = name
    if (name == null) name = "[${string(R.string.no_name)}]"
    else if (name.isEmpty()) name = "[${string(R.string.empty_name)}]"
    return name
}
