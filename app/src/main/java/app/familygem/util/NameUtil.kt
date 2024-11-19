package app.familygem.util

import app.familygem.Global
import app.familygem.R
import app.familygem.TypeView
import org.folg.gedcom.model.Name

/** Composes the title of a name fact, optionally adding the type. */
fun Name.writeTitle(): String {
    var txt = Global.context.getString(R.string.name)
    if (type != null && type.isNotEmpty()) {
        txt += " (${TypeView.getTranslatedType(type, TypeView.Combo.NAME)})"
    }
    return txt
}
