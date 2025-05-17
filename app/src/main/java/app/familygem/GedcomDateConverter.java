package app.familygem;

import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import app.familygem.constant.Format;
import app.familygem.constant.Kind;

/**
 * This class receives a GEDCOM date, parses it and translates it into a {@link SingleDate}.
 */
public class GedcomDateConverter {

    public SingleDate firstDate;
    SingleDate secondDate;
    String phrase; // Text that will go between parentheses
    Kind kind;
    static final String[] gedcomMonths = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
    static final String[] suffixes = {"B.C.", "BC", "BCE"};

    /**
     * With a string date in GEDCOM style.
     */
    public GedcomDateConverter(String gedcomDate) {
        firstDate = new SingleDate();
        secondDate = new SingleDate();
        analyze(gedcomDate);
    }

    /**
     * With one single complete Date.
     */
    public GedcomDateConverter(Date date) {
        firstDate = new SingleDate();
        firstDate.date = date;
        firstDate.format.applyPattern(Format.D_M_Y);
        kind = Kind.EXACT;
    }

    /**
     * Composition of a single date with all necessary attributes.
     */
    public static class SingleDate {
        public Date date;
        SimpleDateFormat format;
        boolean negative;
        boolean doubleDate;

        SingleDate() {
            DateFormatSymbols formatSymbols = new DateFormatSymbols();
            formatSymbols.setShortMonths(gedcomMonths);
            format = new SimpleDateFormat();
            format.setDateFormatSymbols(formatSymbols);
        }

        /**
         * Receives an exact GEDCOM date and fills all the attributes of the SingleDate.
         */
        void scan(String gedcomDate) {
            // Recognize if the date is B.C. and remove the suffix
            negative = false; // Possibly resets it
            for (String suffix : suffixes) {
                if (gedcomDate.endsWith(suffix)) {
                    negative = true;
                    gedcomDate = gedcomDate.substring(0, gedcomDate.indexOf(suffix)).trim();
                    break;
                }
            }
            gedcomDate = gedcomDate.replaceAll("[\\\\_\\-|.,;:?'\"#^&*°+=~()\\[\\]{}]", " "); // All characters except '/'
            // Distinguishes a date with a double year 1712/1713 from a date as 17/12/1713
            doubleDate = false; // Resets it
            if (gedcomDate.indexOf('/') > 0) {
                String[] tokens = gedcomDate.split("[/ ]");
                if (tokens.length > 1 && tokens[tokens.length - 2].length() < 3 && U.extractNum(tokens[tokens.length - 2]) <= 12)
                    gedcomDate = gedcomDate.replace('/', ' ');
                else
                    doubleDate = true;
            }
            for (String dateFormat : Format.PATTERNS) {
                format.applyPattern(dateFormat);
                try {
                    date = format.parse(gedcomDate);
                    break;
                } catch (ParseException ignored) {
                }
            }
            if (isFormat(Format.D_m_Y))
                format.applyPattern(Format.D_M_Y);
            if (isFormat(Format.m_Y))
                format.applyPattern(Format.M_Y);
            // Makes the date effectively negative (for age calculation)
            if (negative) changeEra();
        }

        /**
         * Makes the date BC or AD consistent with the 'negative' boolean.
         */
        void changeEra() {
            if (date != null) {
                // The date is repaired by changing the era
                SimpleDateFormat dateFormat = new SimpleDateFormat(Format.D_M_Y + " G", Locale.US);
                String dateStr = dateFormat.format(date);
                if (negative)
                    dateStr = dateStr.replace("AD", "BC");
                else
                    dateStr = dateStr.replace("BC", "AD");
                try {
                    date = dateFormat.parse(dateStr);
                } catch (Exception ignored) {
                }
            }
        }

        public boolean isFormat(String format) {
            return this.format.toPattern().equals(format);
        }

        @Override
        public String toString() {
            if (date != null) {
                DateFormat format = new SimpleDateFormat("d MMM yyyy G HH:mm:ss", Locale.US);
                return format.format(date);
            }
            return "null";
        }
    }

    /**
     * Recognizes the kind of a GEDCOM date and creates the SingleDate class(es).
     */
    public void analyze(String gedcomDate) {
        // Resets the important values
        kind = null;
        firstDate.date = null;
        firstDate.format.applyPattern(Format.OTHER);
        secondDate.format.applyPattern(Format.OTHER);

        gedcomDate = gedcomDate.trim();
        if (gedcomDate.isEmpty()) {
            kind = Kind.EXACT;
            return;
        }
        // Recognizes types other than EXACT and converts the string to SingleDate
        String upperDate = gedcomDate.toUpperCase();
        for (int i = 1; i < Kind.values().length; i++) {
            Kind k = Kind.values()[i];
            if (upperDate.startsWith(k.prefix)) {
                kind = k;
                if (k == Kind.BETWEEN_AND && upperDate.contains("AND")) {
                    if (upperDate.indexOf("AND") > upperDate.indexOf("BET") + 4)
                        firstDate.scan(upperDate.substring(4, upperDate.indexOf("AND") - 1));
                    if (upperDate.length() > upperDate.indexOf("AND") + 3)
                        secondDate.scan(upperDate.substring(upperDate.indexOf("AND") + 4));
                } else if (k == Kind.FROM && upperDate.contains("TO")) {
                    kind = Kind.FROM_TO;
                    if (upperDate.indexOf("TO") > upperDate.indexOf("FROM") + 5)
                        firstDate.scan(upperDate.substring(5, upperDate.indexOf("TO") - 1));
                    if (upperDate.length() > upperDate.indexOf("TO") + 2)
                        secondDate.scan(upperDate.substring(upperDate.indexOf("TO") + 3));
                } else if (k == Kind.PHRASE) { // Phrase date between parenthesis
                    phrase = gedcomDate.replaceAll("[()]", "");
                } else if (upperDate.length() > k.prefix.length()) { // Other prefixes followed by something
                    firstDate.scan(upperDate.substring(k.prefix.length() + 1));
                }
                break;
            }
        }
        // It remains to test the type EXACT, otherwise it becomes a phrase
        if (kind == null) {
            firstDate.scan(gedcomDate);
            if (firstDate.date != null) {
                kind = Kind.EXACT;
            } else {
                phrase = gedcomDate;
                kind = Kind.PHRASE;
            }
        }
    }

    /**
     * Writes a short text-version of the date in the default locale.
     *
     * @param yearOnly Writes the year only or the whole date with day and month
     */
    public String writeDate(boolean yearOnly) {
        String text = "";
        if (firstDate.date != null && !(firstDate.isFormat(Format.D_M) && yearOnly)) {
            Locale locale = Locale.getDefault();
            DateFormat dateFormat = new SimpleDateFormat(yearOnly ? Format.Y : firstDate.format.toPattern(), locale);
            Date dateOne = (Date)firstDate.date.clone(); // Cloned so the year of a double date can be modified without consequences
            if (firstDate.doubleDate)
                dateOne.setYear(firstDate.date.getYear() + 1);
            text = dateFormat.format(dateOne);
            if (firstDate.negative)
                text = "-" + text;
            if (kind == Kind.APPROXIMATE || kind == Kind.CALCULATED || kind == Kind.ESTIMATED)
                text += "?";
            else if (kind == Kind.AFTER || kind == Kind.FROM)
                text += "→";
            else if (kind == Kind.BEFORE)
                text = "←" + text;
            else if (kind == Kind.TO)
                text = "→" + text;
            else if ((kind == Kind.BETWEEN_AND || kind == Kind.FROM_TO) && secondDate.date != null) {
                Date dateTwo = (Date)secondDate.date.clone();
                if (secondDate.doubleDate)
                    dateTwo.setYear(secondDate.date.getYear() + 1);
                dateFormat = new SimpleDateFormat(yearOnly ? Format.Y : secondDate.format.toPattern(), locale);
                String second = dateFormat.format(dateTwo);
                if (secondDate.negative)
                    second = "-" + second;
                if (!second.equals(text)) {
                    if (!firstDate.negative && !secondDate.negative) {
                        if (!yearOnly && firstDate.isFormat(Format.D_M_Y) && firstDate.format.equals(secondDate.format)
                                && dateOne.getMonth() == dateTwo.getMonth() && dateOne.getYear() == dateTwo.getYear()) { // Same month and year
                            text = text.substring(0, text.indexOf(' '));
                        } else if (!yearOnly && firstDate.isFormat(Format.D_M_Y) && firstDate.format.equals(secondDate.format)
                                && dateOne.getYear() == dateTwo.getYear()) { // Same year
                            text = text.substring(0, text.lastIndexOf(' '));
                        } else if (!yearOnly && firstDate.isFormat(Format.M_Y) && firstDate.format.equals(secondDate.format)
                                && dateOne.getYear() == dateTwo.getYear()) { // Same year
                            text = text.substring(0, text.indexOf(' '));
                        } else if ((yearOnly || (firstDate.isFormat(Format.Y) && firstDate.format.equals(secondDate.format))) // Two years only
                                && ((text.length() == 4 && second.length() == 4 && text.substring(0, 2).equals(second.substring(0, 2))) // of the same century
                                || (text.length() == 3 && second.length() == 3 && text.substring(0, 1).equals(second.substring(0, 1))))) {
                            second = second.substring(second.length() - 2); // Keeps the last two digits
                        }
                    }
                    text += (kind == Kind.BETWEEN_AND ? "~" : "→") + second;
                }
            }
        } else if (kind == Kind.PHRASE) text = phrase;
        return text;
    }

    /**
     * Plain text of the date in local language.
     */
    public String writeDateLong() {
        String txt = "";
        int pre = 0;
        switch (kind) {
            case APPROXIMATE:
                pre = R.string.approximate;
                break;
            case CALCULATED:
                pre = R.string.calculated;
                break;
            case ESTIMATED:
                pre = R.string.estimated;
                break;
            case AFTER:
                pre = R.string.after;
                break;
            case BEFORE:
                pre = R.string.before;
                break;
            case BETWEEN_AND:
                pre = R.string.between;
                break;
            case FROM:
            case FROM_TO:
                pre = R.string.from;
                break;
            case TO:
                pre = R.string.to;
        }
        if (pre > 0)
            txt = Global.context.getString(pre);
        if (firstDate.date != null) {
            txt += writePiece(firstDate);
            // Uppercase initial
            if (kind == Kind.EXACT && firstDate.isFormat(Format.M_Y)) {
                txt = txt.substring(0, 1).toUpperCase() + txt.substring(1);
            }
            if (kind == Kind.BETWEEN_AND || kind == Kind.FROM_TO) {
                txt += " " + Global.context.getString(kind == Kind.BETWEEN_AND ? R.string.and : R.string.to).toLowerCase();
                if (secondDate.date != null)
                    txt += writePiece(secondDate);
            }
        } else if (phrase != null) {
            txt = phrase;
        }
        return txt.trim();
    }

    String writePiece(SingleDate date) {
        DateFormat dateFormat = new SimpleDateFormat(date.format.toPattern().replace("MMM", "MMMM"), Locale.getDefault());
        String txt = " " + dateFormat.format(date.date);
        if (date.doubleDate) {
            String year = String.valueOf(date.date.getYear() + 1901);
            if (year.length() > 1) // Two or more digits
                txt += "/" + year.substring(year.length() - 2);
            else // One digit
                txt += "/0" + year;
        }
        if (date.negative)
            txt += " B.C.";
        return txt;
    }

    /**
     * Returns an integer representing the main date in the format YYYYMMDD, otherwise MAX_VALUE.
     */
    public int getDateNumber() {
        if (firstDate.date != null && !firstDate.isFormat(Format.D_M)) {
            int number = (firstDate.date.getYear() + 1900) * 10000 + (firstDate.date.getMonth() + 1) * 100 + firstDate.date.getDate();
            return firstDate.negative ? -number : number;
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Returns an integer representing the year of the main date, otherwise MAX_VALUE.
     */
    public int getYear() {
        if (firstDate.date != null && !firstDate.isFormat(Format.D_M) && isSingleKind()) {
            return (firstDate.date.getYear() + 1900);
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Kinds of date that represent a single event in time.
     */
    public boolean isSingleKind() {
        return kind == Kind.EXACT || kind == Kind.APPROXIMATE || kind == Kind.CALCULATED || kind == Kind.ESTIMATED;
    }
}
