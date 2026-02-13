package app.familygem

import android.content.Context
import app.familygem.util.Util
import org.joda.time.Period
import org.joda.time.PeriodType

/** This class receives a GEDCOM age, parses it and translates it into a [Age]. */
class AgeConverter {
    var age: Age

    enum class Modifier { EXACT, MORE, LESS }

    constructor(gedcomAge: String) {
        age = Age()
        age.scan(gedcomAge)
    }

    /** Composition of a single age with all necessary attributes. */
    class Age {
        var period: Period = Period().withPeriodType(PeriodType.yearMonthDay())
        var modifier = Modifier.EXACT

        /** Receives a GEDCOM age and fills all the attributes of Age. */
        fun scan(gedcomAge: String) {
            period = period.withYears(0).withMonths(0).withDays(0)
            modifier = Modifier.EXACT

            val gedcomAge = gedcomAge.trim()
            if (gedcomAge.isEmpty()) return

            if (gedcomAge.contains("CHILD", true)) {
                modifier = Modifier.LESS
                period = period.withYears(8)
                return
            } else if (gedcomAge.contains("INFANT", true)) {
                modifier = Modifier.LESS
                period = period.withYears(1)
                return
            } else if (gedcomAge.contains("STILLBORN", true)) {
                return
            }

            if (gedcomAge.startsWith('<')) modifier = Modifier.LESS
            else if (gedcomAge.startsWith('>')) modifier = Modifier.MORE

            "(\\d+)y".toRegex().find(gedcomAge)?.groupValues?.get(1)?.toIntOrNull()?.let { period = period.withYears(it) }
            "(\\d+)m".toRegex().find(gedcomAge)?.groupValues?.get(1)?.toIntOrNull()?.let { period = period.withMonths(it) }
            "(\\d+)d".toRegex().find(gedcomAge)?.groupValues?.get(1)?.toIntOrNull()?.let { period = period.withDays(it) }
        }

        override fun toString(): String {
            val builder = StringBuilder()
            period.apply {
                builder.append(years).append(' ').append(months).append(' ').append(days)
            }
            return builder.toString()
        }
    }

    /** Checks if a string follows the GEDCOM 5.5.1 standard for AGE_AT_EVENT. */
    fun isValid(gedcomAge: String): Boolean {
        val gedcomAge = gedcomAge.trim()
        if (gedcomAge.isEmpty()) return true
        val years = "\\d{1,3}y"
        val months = "\\d{1,2}m"
        val days = "\\d{1,3}d"
        val ageParts = "(< |> )?($years $months $days|$years $months|$months $days|$years $days|$years|$months|$days)"
        return gedcomAge.matches(ageParts.toRegex())
                || gedcomAge.matches("CHILD".toRegex())
                || gedcomAge.matches("INFANT".toRegex())
                || gedcomAge.matches("STILLBORN".toRegex())
    }

    /** Writes a long text-version of the age in the default locale. */
    fun writeAge(context: Context): String {
        val builder = StringBuilder()
        if (age.modifier == Modifier.LESS) builder.append(context.getString(R.string.less_than)).append(' ')
        else if (age.modifier == Modifier.MORE) builder.append(context.getString(R.string.more_than)).append(' ')
        age.period.apply {
            if (years > 0) {
                val yearStr = Util.caseString(if (years == 1) R.string.year else R.string.years)
                builder.append(years).append(' ').append(yearStr).append(' ')
            }
            if (months > 0) builder.append(months).append(' ').append(context.getString(R.string.months)).append(' ')
            if (days > 0) builder.append(days).append(' ').append(context.getString(R.string.days))
        }
        return builder.toString().trim()
    }
}
