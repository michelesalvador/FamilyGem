package app.familygem.util

import android.content.Intent
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import app.familygem.GedcomDateConverter
import app.familygem.Global
import app.familygem.Memory
import app.familygem.R
import app.familygem.U
import app.familygem.detail.ChangeActivity
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

    /**
     * Adds to the layout a view with the date and time of change.
     */
    fun placeChangeDate(layout: LinearLayout, change: Change?) {
        if (change != null && Global.settings.expert) {
            val changeView = LayoutInflater.from(layout.context).inflate(R.layout.change_date_layout, layout, false)
            layout.addView(changeView)
            val textView = changeView.findViewById<TextView>(R.id.changeDate_text)
            if (change.dateTime != null) {
                var txt = ""
                if (change.dateTime.value != null) txt = GedcomDateConverter(change.dateTime.value).writeDateLong()
                if (change.dateTime.time != null) txt += " - " + change.dateTime.time
                textView.text = txt
            }
            val otherBox = changeView.findViewById<LinearLayout>(R.id.changeDate_box)
            U.findExtensions(change).forEach { U.place(otherBox, it.name, it.text) }
            NoteUtil.placeNotes(otherBox, change, false)
            changeView.setOnClickListener {
                Memory.add(change)
                layout.context.startActivity(Intent(layout.context, ChangeActivity::class.java))
            }
        }
    }
}
