package app.familygem

import app.familygem.constant.Format
import app.familygem.constant.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DateTest {

    class DateWrapper(
        val gedcomDate: String, val kind: Kind,
        val firstFormat: Format, val secondFormat: Format,
        val firstDual: Boolean, val secondDual: Boolean,
        val firstNegative: Boolean, val secondNegative: Boolean,
        val valid: Boolean
    )

    @Test
    fun `parse various GEDCOM dates`() {
        val dateList = ArrayList<DateWrapper>()
        fun add(
            date: String, kind: Kind,
            firstFormat: Format = Format.OTHER, secondFormat: Format = Format.OTHER,
            firstDual: Boolean = false, secondDual: Boolean = false,
            firstNegative: Boolean = false, secondNegative: Boolean = false,
            valid: Boolean = false
        ) {
            dateList.add(DateWrapper(date, kind, firstFormat, secondFormat, firstDual, secondDual, firstNegative, secondNegative, valid))
        }

        add("", Kind.EXACT, valid = true)
        add("   ", Kind.EXACT, valid = true)
        add("31", Kind.EXACT, Format.D)
        add("031", Kind.EXACT, Format.Y, valid = true)
        add("32", Kind.EXACT, Format.Y)
        add("032", Kind.EXACT, Format.Y, valid = true)
        add(" APR  ", Kind.EXACT, Format.M)
        add("7 AUG", Kind.EXACT, Format.D_M)
        add("MAr 78 ", Kind.EXACT, Format.M_Y)
        add("10 agosto 2024", Kind.PHRASE, valid = true)
        add("10 august 2024", Kind.EXACT, Format.D_M_Y)
        add("29 FEB 1999", Kind.PHRASE, valid = true)
        add("12/1713", Kind.EXACT, Format.M_Y)
        add("15/05/1970", Kind.EXACT, Format.D_M_Y)

        add(" abt  29 feb 2000 ", Kind.APPROXIMATE, Format.D_M_Y)
        add("ABT", Kind.APPROXIMATE)
        add("ABT9", Kind.APPROXIMATE, Format.D)
        add("ABTJan 1234", Kind.APPROXIMATE, Format.M_Y)
        add(" BEF   ", Kind.BEFORE)
        add("AFT foobaz...", Kind.AFTER)
        add("to 15 1544", Kind.TO)

        add("BET 1000 ABCxyz", Kind.BETWEEN_AND)
        add("  Bet 28 feb 005 and 5 may 123 ", Kind.BETWEEN_AND, Format.D_M_Y, Format.D_M_Y)
        add("BET 13 NOV 1333", Kind.BETWEEN_AND, Format.D_M_Y)
        add("BET 22 FEB 1500 AND", Kind.BETWEEN_AND, Format.D_M_Y)
        add("BET 5 MAY AND 20 MAY", Kind.BETWEEN_AND, Format.D_M, Format.D_M)
        add("BET 1 AUG 2005 AND quaquaqua", Kind.BETWEEN_AND, Format.D_M_Y)
        add("FROM 1111 TO", Kind.FROM_TO, Format.Y)
        add("from     TO 1234", Kind.FROM_TO, Format.OTHER, Format.Y)
        add("   FROM 1001 to 1001  ", Kind.FROM_TO, Format.Y, Format.Y)

        add("Söme ràndom téxt", Kind.PHRASE, valid = true)
        add("BE 7 Jan 1913", Kind.PHRASE, valid = true)
        add("(JAn 1458)", Kind.PHRASE, valid = true)
        add("(one parenthesis only", Kind.PHRASE, valid = true)
        add("  (  True phrase ) ", Kind.PHRASE, valid = true)

        add("jan 1699/00 ", Kind.EXACT, Format.M_Y, firstDual = true)
        add("/99", Kind.PHRASE, valid = true)
        add("FROM 3 FEB 1715/16", Kind.FROM, Format.D_M_Y, firstDual = true, valid = true)
        add("ABT AUG 123/24", Kind.APPROXIMATE, Format.M_Y, firstDual = true, valid = true)
        add("TOAUG 1595/96", Kind.TO, Format.M_Y, firstDual = true)
        add("bet 1701/02 and 1756/1757", Kind.BETWEEN_AND, Format.Y, Format.Y, true, true)

        add("0", Kind.EXACT, Format.Y) // Is not converted to year 1 BC
        add("000", Kind.EXACT, Format.Y, valid = true) // Idem
        add("11 DEC 000", Kind.EXACT, Format.D_M_Y, valid = true) // Idem
        add("000 B.C.", Kind.EXACT, Format.Y, firstNegative = true, valid = true)

        add("b.c.", Kind.PHRASE, firstNegative = true, valid = true)
        add("1BC", Kind.EXACT, Format.D, firstNegative = true)
        add("XX b.c.", Kind.PHRASE, firstNegative = true, valid = true)
        add("ABT 7/8 B.C.", Kind.APPROXIMATE, Format.M_Y, firstNegative = true)
        add("CAL 007/08 B.C.", Kind.CALCULATED, Format.Y, firstDual = true, firstNegative = true, valid = true)
        add("111B.C.", Kind.EXACT, Format.Y, firstNegative = true)
        add("1-2-123B.C.", Kind.EXACT, Format.D_M_Y, firstNegative = true)
        add("CAL 2000/99 B.C.", Kind.CALCULATED, Format.Y, firstDual = true, firstNegative = true, valid = true)
        add("BET 3 AUG 020 B.C. AND SEP 010 B.C.", Kind.BETWEEN_AND, Format.D_M_Y, Format.M_Y, firstNegative = true, secondNegative = true)
        add("BET 50/49 BC AND 5 BC", Kind.BETWEEN_AND, Format.Y, Format.D, firstDual = true, firstNegative = true, secondNegative = true)
        add("FROM 1000 B.C. TO AUG 200", Kind.FROM_TO, Format.Y, Format.M_Y, firstNegative = true, valid = true)

        arrayOf( // Other possible GEDCOM dates
            "bet 800 and 15 may 805", "BET jun 2000 and 19.1999", "BET 7 AUG 1974 AND 31 DEC 1974", "BET 11 12 1975 and 25 12 1975",
            "Bet 1550 bc and 1510 B.C.", "bet 2000 bc and 2000", "BET 500 and 5010",
            "from 34/1234",
            "14\"4.1970",
            "12 1440/41", "from DEC 1699/700 to 15 mar 1752/", "jan 11/3", "4 mar 05/06",
            "apr 34 BC", "12 AUG 3456BCE", "-039 BCE",
            "ABT 3BC", "CAL 43 BC", "FROM aug 123b.C.", "TO -1 B.C.", "BET 10BCE AND 5 BCE", "BET 1-Apr 0125 BCE AND 003BC",
            "21 APR 753 B.C.", "1/2 BC", "apr 10/11 BC", "6 aug 100/101 BC", "from 1000/01B.C. TO 020/21 BCE", "ABT 28 NOV 050/51 B.C."
        )

        Locale.setDefault(Locale.ENGLISH)
        for (date in dateList) {
            print("'" + date.gedcomDate + "'")
            val converter = DateConverter(date.gedcomDate)
            val valid = converter.isValid(date.gedcomDate)
            println(if (valid) "" else " ⚠")
            println("\t" + converter.firstDate + " - " + converter.secondDate + " - " + converter.kind)
            if (converter.phrase != null) println("\t(" + converter.phrase + ")")
            else println("\t'" + converter.writeDate(false) + "' '" + converter.writeDate(true) + "'")
            println("\t" + converter.getDateNumber() + " " + converter.getYear())
            assertNotNull(converter)
            assertEquals(converter.kind, date.kind)
            assertTrue(converter.firstDate.format == date.firstFormat)
            assertTrue(converter.secondDate!!.format == date.secondFormat)
            assertEquals(converter.firstDate.dual, date.firstDual)
            assertEquals(converter.secondDate!!.dual, date.secondDual)
            assertEquals(converter.firstDate.negative, date.firstNegative)
            assertEquals(converter.secondDate!!.negative, date.secondNegative)
            assertEquals(valid, date.valid)
        }
    }
}
