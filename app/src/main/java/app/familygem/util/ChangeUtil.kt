package app.familygem.util

import org.folg.gedcom.model.Change
import org.folg.gedcom.model.DateTime
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ChangeUtil {

    /**
     * Updates change date of any number of objects.
     */
    fun updateChangeDate(vararg objects: Any?) {
        objects.filterNotNull().forEach {
            try { // If the object has no getChange/setChange method, silently passes by
                var change = it.javaClass.getMethod("getChange").invoke(it) as Change?
                if (change == null) // The record does not yet have a CHAN tag
                    change = Change()
                change.dateTime = actualDateTime()
                it.javaClass.getMethod("setChange", Change::class.java).invoke(it, change)
                // Extension with the ID of the zone, a string like "America/Sao_Paulo"
                change.putExtension("zone", TimeZone.getDefault().id)
            } catch (ignored: Exception) {
            }
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
