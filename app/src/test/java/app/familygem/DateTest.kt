package app.familygem

import app.familygem.constant.Format
import app.familygem.constant.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DateTest {

    class DateWrapper(val gedcomDate: String, val kind: Kind,
                      val firstFormat: String, val secondFormat: String,
                      val firstDouble: Boolean, val secondDouble: Boolean,
                      val firstNegative: Boolean, val secondNegative: Boolean) {
    }

    @Test
    fun `parse various GEDCOM dates`() {
        val dateList = ArrayList<DateWrapper>()
        fun add(date: String, kind: Kind,
                firstFormat: String = Format.OTHER, secondFormat: String = Format.OTHER,
                firstDouble: Boolean = false, secondDouble: Boolean = false,
                firstNegative: Boolean = false, secondNegative: Boolean = false) {
            dateList.add(DateWrapper(date, kind, firstFormat, secondFormat, firstDouble, secondDouble, firstNegative, secondNegative))
        }

        add("", Kind.EXACT, Format.OTHER)
        add(" 1986 ", Kind.EXACT, Format.Y)
        add(" APR  ", Kind.PHRASE)
        add("7 AUG", Kind.EXACT, Format.D_M)
        add("MAr 78 ", Kind.EXACT, Format.M_Y)
        add("10 agosto 2024", Kind.EXACT, Format.Y)
        add("12/1713", Kind.EXACT, Format.M_Y)
        add("15/05/1970", Kind.EXACT, Format.D_M_Y)

        add(" abt  29 feb 2000 ", Kind.APPROXIMATE, Format.D_M_Y)
        add("ABT", Kind.APPROXIMATE, Format.OTHER)
        add(" BEF   ", Kind.BEFORE, Format.OTHER)
        add("AFT foobaz...", Kind.AFTER, Format.OTHER)
        add("to 15 1544", Kind.TO, Format.M_Y)

        add("  Bet 29 feb 005 and 5 may 123 ", Kind.BETWEEN_AND, Format.D_M_Y, Format.D_M_Y)
        add("BET 13 NOV 1333", Kind.BETWEEN_AND, Format.D_M_Y)
        add("BET 22 FEB 1500 AND", Kind.BETWEEN_AND, Format.D_M_Y)
        add("BET 1 AUG 2005 AND quaquaqua", Kind.BETWEEN_AND, Format.D_M_Y, Format.OTHER)
        add("FROM 1111 TO", Kind.FROM_TO, Format.Y)
        add("   FROM 1001 to 1001  ", Kind.FROM_TO, Format.Y, Format.Y)
        add("FROM 33 DEC 1099 TO -2 jan 1101", Kind.FROM_TO, Format.D_M_Y, Format.D_M_Y)

        add("Söme ràndom téxt", Kind.PHRASE)
        add("BE 7 Jan 1913", Kind.PHRASE)
        add("(JAn 1458)", Kind.PHRASE)
        add("(one parenthesis only", Kind.PHRASE)
        add("  (  True phrase ) ", Kind.PHRASE)

        add("TOAUG 1595/96", Kind.TO, Format.OTHER, firstDouble = true)
        add("jan 1699/00 ", Kind.EXACT, Format.M_Y, firstDouble = true)
        add("bet 1701/02 and 1756/1757", Kind.BETWEEN_AND, Format.Y, Format.Y, true, true)

        add("0", Kind.EXACT, Format.Y, firstNegative = false) // TODO: wrong! should become negative (-001)
        add("b.c.", Kind.PHRASE)
        add("XX b.c.", Kind.PHRASE)
        add("0 B.C.", Kind.EXACT, Format.Y, firstNegative = true)
        add("1BC", Kind.EXACT, Format.Y, firstNegative = true)
        add("1-2-123B.C.", Kind.EXACT, Format.D_M_Y, firstNegative = true)

        arrayOf( // Other possible GEDCOM dates
                "bet 800 and 15 may 805", "BET jun 2000 and 19.1999", "BET 7 AUG 1974 AND 31 DEC 1974", "BET 11 12 1975 and 25 12 1975",
                "Bet 1550 bc and 1510 B.C.", "BET 1 AUG 200 BC AND 23 AUG 200 BC", "bet 2000 bc and 2000", "BET 500 and 5010",
                "from 34/1234",
                "14\"4.1970",
                "3 feb 1715/16", "12 1440/41", "from DEC 1699/700 to 15 mar 1752/", "jan 11/3", "4 mar 05/06",
                "apr 34 BC", "12 AUG 3456BCE", "-039 BCE",
                "ABT 3BC", "CAL 43 BC", "FROM aug 123b.C.", "TO -1 B.C.", "BET 10BCE AND 5 BCE", "BET 1-Apr 0125 BCE AND 003BC",
                "21 APR 753 B.C.", "1/2 BC", "apr 10/11 BC", "6 aug 100/101 BC", "from 1000/01B.C. TO 020/21 BCE", "ABT 28 NOV 050/51 B.C."
        )

        Locale.setDefault(Locale.ENGLISH)
        for (date in dateList) {
            println("'" + date.gedcomDate + "'")
            val converter = GedcomDateConverter(date.gedcomDate)
            println("\t" + converter.firstDate + " - " + converter.secondDate)
            println("\t" + converter.writeDate(false) + " " + converter.kind)
            assertNotNull(converter)
            assertEquals(converter.kind, date.kind)
            assertTrue(converter.firstDate.isFormat(date.firstFormat))
            assertTrue(converter.secondDate.isFormat(date.secondFormat))
            assertEquals(converter.firstDate.doubleDate, date.firstDouble)
            assertEquals(converter.secondDate.doubleDate, date.secondDouble)
            assertEquals(converter.firstDate.negative, date.firstNegative)
            assertEquals(converter.secondDate.negative, date.secondNegative)
        }
    }
}
