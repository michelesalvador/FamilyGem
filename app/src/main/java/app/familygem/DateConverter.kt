package app.familygem

import app.familygem.constant.Format
import app.familygem.constant.Kind
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import java.util.Locale

/** This class receives a GEDCOM date, parses it and translates it into a [SingleDate]. */
class DateConverter {
    var firstDate: SingleDate
    var secondDate: SingleDate? = null
    var phrase: String? = null // Text that will go between parentheses
    var kind: Kind? = null

    /** Kinds of date that represent a single event in time. */
    val isSingleKind: Boolean
        get() = kind == Kind.EXACT || kind == Kind.APPROXIMATE || kind == Kind.CALCULATED || kind == Kind.ESTIMATED

    /** With a string date in GEDCOM style. */
    constructor(gedcomDate: String) {
        firstDate = SingleDate()
        secondDate = SingleDate()
        analyze(gedcomDate)
    }

    /** With one single complete LocalDate. */
    constructor(date: LocalDate) {
        firstDate = SingleDate()
        firstDate.date = date
        firstDate.format = Format.D_M_Y
        kind = Kind.EXACT
    }

    /** Composition of a single date with all necessary attributes. */
    class SingleDate {
        var date: LocalDate? = null
        var format: Format? = null
        var negative = false
            set(value) {
                field = value
                date = date?.withEra(if (value) 0 else 1)
            }
        var dual = false

        /** Receives an exact GEDCOM date and fills all the attributes of the SingleDate. */
        fun scan(gedcomDate: String) {
            var gedcomDate = gedcomDate.trim()
            // Recognizes if the date is B.C. and removes the suffix
            negative = if (gedcomDate.endsWith("BC")) {
                gedcomDate = gedcomDate.substring(0, gedcomDate.length - 2).trim()
                true
            } else false
            gedcomDate = gedcomDate.replace("\\s+".toRegex(), " ") // All spaces
            // Distinguishes a date with a double year 1712/1713 from a date as 17/12/1713
            dual = false // Resets it
            if (gedcomDate.indexOf('/') > 0) {
                val tokens = gedcomDate.split("[/ ]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (tokens.size > 1 && tokens[tokens.size - 2].length < 3 && U.extractNum(tokens[tokens.size - 2]) <= 12)
                    gedcomDate = gedcomDate.replace('/', ' ')
                else {
                    gedcomDate = gedcomDate.substring(0, gedcomDate.indexOf('/')).trim()
                    if (gedcomDate.length > 1) dual = true
                }
            }
            val locales = listOf(Locale.getDefault(), Locale.ENGLISH).distinct()
            for (format in Format.entries) {
                this.format = format
                date = locales.firstNotNullOfOrNull { locale ->
                    try {
                        DateTimeFormat.forPattern(format.pattern).withLocale(locale).parseLocalDate(gedcomDate)
                    } catch (_: Exception) {
                        null
                    }
                }
                if (date != null) break
            }
            if (format == Format.D_m_Y) format = Format.D_M_Y
            if (format == Format.m_Y) format = Format.M_Y
            // Makes the date effectively negative (for age calculation)
            if (negative && date != null) {
                date = date!!.withEra(0)
            }
        }

        /** Date format is one of those that include the year. */
        val hasYear: Boolean
            get() = format == Format.D_M_Y || format == Format.M_Y || format == Format.Y

        override fun toString(): String {
            if (date != null) {
                return date!!.toString("d MMM yyyy G")
            }
            return "null"
        }
    }

    /** Recognizes the kind of GEDCOM date and creates the SingleDate class(es). */
    fun analyze(gedcomDate: String) {
        // Resets the important values
        kind = null
        firstDate.date = null
        firstDate.format = Format.OTHER
        secondDate?.format = Format.OTHER

        val gedcomDate = gedcomDate.replace("B.C.", "BC", true).replace("BCE", "BC", true).trim()
        if (gedcomDate.isEmpty()) {
            kind = Kind.EXACT
            return
        }
        // Recognizes types other than EXACT and converts the string to SingleDate
        // Replaces all characters except numbers, letters, '/' and parenthesis
        val upperDate = gedcomDate.replace("[^0-9\\p{L}/()]+".toRegex(), " ").uppercase()
        for (i in 1..<Kind.entries.size) {
            val k = Kind.entries[i]
            if (upperDate.startsWith(k.prefix)) {
                kind = k
                if (k == Kind.BETWEEN_AND && upperDate.contains("AND")) {
                    if (upperDate.indexOf("AND") > upperDate.indexOf("BET") + 4)
                        firstDate.scan(upperDate.substring(3, upperDate.indexOf("AND")))
                    if (upperDate.length > upperDate.indexOf("AND") + 3)
                        secondDate?.scan(upperDate.substring(upperDate.indexOf("AND") + 3))
                } else if (k == Kind.FROM && upperDate.contains("TO")) {
                    kind = Kind.FROM_TO
                    if (upperDate.indexOf("TO") > upperDate.indexOf("FROM") + 4)
                        firstDate.scan(upperDate.substring(4, upperDate.indexOf("TO")))
                    if (upperDate.length > upperDate.indexOf("TO") + 2)
                        secondDate?.scan(upperDate.substring(upperDate.indexOf("TO") + 2))
                } else if (k == Kind.PHRASE) { // Phrase date between parenthesis
                    phrase = gedcomDate.replace("[()]".toRegex(), "")
                } else if (upperDate.length > k.prefix.length) { // Other prefixes followed by something
                    firstDate.scan(upperDate.substring(k.prefix.length))
                }
                break
            }
        }
        // It remains to test the type EXACT, otherwise it becomes a phrase
        if (kind == null) {
            firstDate.scan(upperDate)
            if (firstDate.date != null) {
                kind = Kind.EXACT
            } else {
                phrase = gedcomDate
                kind = Kind.PHRASE
            }
        }
    }

    /** Checks if a string follows the GEDCOM 5.5.1 standard for Gregorian dates. */
    fun isValid(gedcomDate: String): Boolean {
        val gedcomDate = gedcomDate.trim()
        if (gedcomDate.isEmpty() || kind == Kind.PHRASE) return true
        val day = "\\d{1,2}"
        val month = "(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)"
        val year = "\\d{3,4}(/\\d{2})?"
        val datePart = "($day +$month +$year|$month +$year|$year( B\\.C\\.)?)"
        return gedcomDate.matches(datePart.toRegex())
                || gedcomDate.matches(("(ABT|CAL|EST|BEF|AFT|FROM|TO) +$datePart").toRegex())
                || gedcomDate.matches(("BET +$datePart +AND +$datePart").toRegex())
                || gedcomDate.matches(("FROM +$datePart +TO +$datePart").toRegex())
    }

    /** Writes a short text-version of the date in the default locale.
     * @param yearOnly Writes the year only or the whole date with day and month */
    fun writeDate(yearOnly: Boolean): String {
        if (kind == Kind.PHRASE) return phrase!!
        var text = ""
        if (firstDate.date != null) {
            text = writeSingleDate(firstDate, yearOnly, true)
        }
        text += if (kind == Kind.BETWEEN_AND) "~" else if (kind == Kind.FROM_TO) "→" else ""
        if (secondDate?.date != null) {
            text += writeSingleDate(secondDate!!, yearOnly, false)
        }
        return text
    }

    private fun writeSingleDate(singleDate: SingleDate, yearOnly: Boolean, isFirst: Boolean): String {
        var pattern = if (yearOnly) {
            if (singleDate.hasYear) "yyy" else ""
        } else singleDate.format!!.pattern
        var date = if (singleDate.dual) singleDate.date!!.plusYears(1) else singleDate.date!!
        if (singleDate.negative) {
            pattern = "-$pattern"
            date = date.withEra(1)
        }
        if (isFirst) {
            pattern = when (kind) {
                Kind.APPROXIMATE, Kind.CALCULATED, Kind.ESTIMATED -> "$pattern?"
                Kind.AFTER, Kind.FROM -> "$pattern→"
                Kind.BEFORE -> "←$pattern"
                Kind.TO -> "→$pattern"
                else -> pattern
            }
        }
        return if (pattern.isNotEmpty()) date.toString(pattern, Locale.getDefault()) else ""
    }

    /** Plain text of the date in local language. */
    fun writeDateLong(): String {
        if (kind == Kind.PHRASE) return phrase!!
        var txt = ""
        val prefix = when (kind) {
            Kind.APPROXIMATE -> R.string.approximate
            Kind.CALCULATED -> R.string.calculated
            Kind.ESTIMATED -> R.string.estimated
            Kind.AFTER -> R.string.after
            Kind.BEFORE -> R.string.before
            Kind.BETWEEN_AND -> R.string.between
            Kind.FROM, Kind.FROM_TO -> R.string.from
            Kind.TO -> R.string.to
            else -> 0
        }
        if (prefix > 0) txt = Global.context.getString(prefix) + " "
        if (firstDate.date != null) {
            txt += writeSingleDateLong(firstDate)
            // Uppercase initial month
            if (kind == Kind.EXACT && (firstDate.format == Format.M || firstDate.format == Format.M_Y)) {
                txt = txt.replaceFirstChar { it.titlecase() }
            }
            if (kind == Kind.BETWEEN_AND || kind == Kind.FROM_TO) {
                txt += " " + Global.context.getString(if (kind == Kind.BETWEEN_AND) R.string.and else R.string.to).lowercase() + " "
                if (secondDate?.date != null) txt += writeSingleDateLong(secondDate!!)
            }
        }
        return txt.replace("-", "").trim()
    }

    private fun writeSingleDateLong(singleDate: SingleDate): String {
        val pattern = singleDate.format!!.pattern.replace("MMM", "MMMM")
        var txt = singleDate.date!!.toString(pattern)
        if (singleDate.dual) txt += "/" + singleDate.date!!.plusYears(1).toString("yy")
        if (singleDate.negative) txt += " B.C."
        return txt
    }

    /** @return An integer representing the main date in the format YYYYMMDD, otherwise MAX_VALUE */
    fun getDateNumber(): Int {
        if (firstDate.date != null && firstDate.hasYear) {
            val year = firstDate.date!!.plusYears(if (firstDate.dual) 1 else 0).year * 10000
            val monthDay = firstDate.date!!.monthOfYear * 100 + firstDate.date!!.dayOfMonth
            return if (year < 0) year - monthDay else year + monthDay
        }
        return Int.MAX_VALUE
    }

    /** Returns an integer representing the year of the main date, only if [isSingleKind], otherwise MAX_VALUE. */
    fun getYear(): Int {
        if (firstDate.date != null && firstDate.hasYear && isSingleKind) {
            return firstDate.date!!.plusYears(if (firstDate.dual) 1 else 0).year
        }
        return Int.MAX_VALUE
    }
}
