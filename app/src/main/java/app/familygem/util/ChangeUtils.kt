package app.familygem.util

import org.folg.gedcom.model.Change
import org.folg.gedcom.model.DateTime
import java.util.*

object ChangeUtils {

    /**
     * Updates change date of any object.
     */
    fun updateChangeDate(objct: Any) {
        try { // If objct has no getChange/setChange method, silently passes by
            var change = objct.javaClass.getMethod("getChange").invoke(objct) as Change?
            if (change == null) // The record does not yet have a CHAN tag
                change = Change()
            change.dateTime = actualDateTime()
            objct.javaClass.getMethod("setChange", Change::class.java).invoke(objct, change)
            // Extension with the ID of the zone, a string like "America/Sao_Paulo"
            change.putExtension("zone", TimeZone.getDefault().id)
        } catch (ignored: Exception) {
        }
    }

    /**
     * Returns actual DateTime.
     */
    fun actualDateTime(): DateTime {
        val dateTime = DateTime()
        val now = Date()
        dateTime.value = String.format(Locale.ENGLISH, "%te %<Tb %<tY", now)
        dateTime.time = String.format(Locale.ENGLISH, "%tT", now)
        return dateTime
    }
}
