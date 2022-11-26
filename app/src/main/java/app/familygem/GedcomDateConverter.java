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
 * This class receives a Gedcom date, parses it and translates it into a {@link Data}.
 */
public class GedcomDateConverter {

    public Data data1;
    Data data2;
    String phrase; // The one that will go in parentheses
    Kind kind;
    static final String[] gedcomMonths = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
    static final String[] suffixes = {"B.C.", "BC", "BCE"};

    /**
     * With a string date in GEDCOM style.
     */
    public GedcomDateConverter(String gedcomDate) {
        data1 = new Data();
        data2 = new Data();
        analyze(gedcomDate);
    }

    /**
     * With one single complete Date.
     */
    public GedcomDateConverter(Date date) {
        data1 = new Data();
        data1.date = date;
        data1.format.applyPattern(Format.D_M_Y);
        kind = Kind.EXACT;
    }

    public class Data {
        public Date date;
        SimpleDateFormat format;
        boolean negative;
        boolean doubleDate;

        Data() {
            DateFormatSymbols simboliFormato = new DateFormatSymbols();
            simboliFormato.setShortMonths(gedcomMonths);
            format = new SimpleDateFormat();
            format.setDateFormatSymbols(simboliFormato);
        }

        /**
         * Receives an exact GEDCOM date and fills all the attributes of this class.
         */
        void scan(String dataGc) {

            // Recognize if the date is B.C. and remove the suffix
            negative = false; // Possibly resets it
            for (String suffix : suffixes) {
                if (dataGc.endsWith(suffix)) {
                    negative = true;
                    dataGc = dataGc.substring(0, dataGc.indexOf(suffix)).trim();
                    break;
                }
            }
            dataGc = dataGc.replaceAll("[\\\\_\\-|.,;:?'\"#^&*°+=~()\\[\\]{}]", " "); // tutti tranne '/'

            // Distinguishes a date with a double year 1712/1713 from a date type 17/12/1713
            doubleDate = false; // Resets it
            if (dataGc.indexOf('/') > 0) {
                String[] tata = dataGc.split("[/ ]");
                if (tata.length > 1 && tata[tata.length - 2].length() < 3 && U.extractNum(tata[tata.length - 2]) <= 12)
                    dataGc = dataGc.replace('/', ' ');
                else
                    doubleDate = true;
            }
            for (String dateFormat : Format.PATTERNS) {
                format.applyPattern(dateFormat);
                try {
                    date = format.parse(dataGc);
                    break;
                } catch (ParseException e) {
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
                SimpleDateFormat sdf = new SimpleDateFormat(Format.D_M_Y + " G", Locale.US);
                String data = sdf.format(date);
                if (negative)
                    data = data.replace("AD", "BC");
                else
                    data = data.replace("BC", "AD");
                try {
                    date = sdf.parse(data);
                } catch (Exception e) {
                }
            }
        }

        public boolean isFormat(String format) {
            return this.format.toPattern().equals(format);
        }

        @Override
        public String toString() {
            DateFormat format = new SimpleDateFormat("d MMM yyyy G HH:mm:ss", Locale.US);
            return format.format(date);
        }
    }

    /**
     * Recognizes the type of date and creates the Data class.
     */
    public void analyze(String dataGc) {

        // Resets the important values
        kind = null;
        data1.date = null;

        dataGc = dataGc.trim();
        if (dataGc.isEmpty()) {
            kind = Kind.EXACT;
            return;
        }
        // Recognizes types other than EXACT and converts the string to Data
        String dataGcMaiusc = dataGc.toUpperCase();
        for (int i = 1; i < Kind.values().length; i++) {
            Kind k = Kind.values()[i];
            if (dataGcMaiusc.startsWith(k.prefix)) {
                kind = k;
                if (k == Kind.BETWEEN_AND && dataGcMaiusc.contains("AND")) {
                    if (dataGcMaiusc.indexOf("AND") > dataGcMaiusc.indexOf("BET") + 4)
                        data1.scan(dataGcMaiusc.substring(4, dataGcMaiusc.indexOf("AND") - 1));
                    if (dataGcMaiusc.length() > dataGcMaiusc.indexOf("AND") + 3)
                        data2.scan(dataGcMaiusc.substring(dataGcMaiusc.indexOf("AND") + 4));
                } else if (k == Kind.FROM && dataGcMaiusc.contains("TO")) {
                    kind = Kind.FROM_TO;
                    if (dataGcMaiusc.indexOf("TO") > dataGcMaiusc.indexOf("FROM") + 5)
                        data1.scan(dataGcMaiusc.substring(5, dataGcMaiusc.indexOf("TO") - 1));
                    if (dataGcMaiusc.length() > dataGcMaiusc.indexOf("TO") + 2)
                        data2.scan(dataGcMaiusc.substring(dataGcMaiusc.indexOf("TO") + 3));
                } else if (k == Kind.PHRASE) { // Phrase date between parenthesis
                    if (dataGc.endsWith(")"))
                        phrase = dataGc.substring(1, dataGc.indexOf(")"));
                    else
                        phrase = dataGc;
                } else if (dataGcMaiusc.length() > k.prefix.length()) // Other prefixes followed by something
                    data1.scan(dataGcMaiusc.substring(k.prefix.length() + 1));
                break;
            }
        }
        // It remains to test the type EXACT, otherwise it becomes a phrase
        if (kind == null) {
            data1.scan(dataGc);
            if (data1.date != null) {
                kind = Kind.EXACT;
            } else {
                phrase = dataGc;
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
        if (data1.date != null && !(data1.isFormat(Format.D_M) && yearOnly)) {
            Locale locale = Locale.getDefault();
            DateFormat dateFormat = new SimpleDateFormat(yearOnly ? Format.Y : data1.format.toPattern(), locale);
            Date dateOne = (Date)data1.date.clone(); // Cloned so the year of a double date can be modified without consequences
            if (data1.doubleDate)
                dateOne.setYear(data1.date.getYear() + 1);
            text = dateFormat.format(dateOne);
            if (data1.negative)
                text = "-" + text;
            if (kind == Kind.APPROXIMATE || kind == Kind.CALCULATED || kind == Kind.ESTIMATED)
                text += "?";
            else if (kind == Kind.AFTER || kind == Kind.FROM)
                text += "→";
            else if (kind == Kind.BEFORE)
                text = "←" + text;
            else if (kind == Kind.TO)
                text = "→" + text;
            else if ((kind == Kind.BETWEEN_AND || kind == Kind.FROM_TO) && data2.date != null) {
                Date dateTwo = (Date)data2.date.clone();
                if (data2.doubleDate)
                    dateTwo.setYear(data2.date.getYear() + 1);
                dateFormat = new SimpleDateFormat(yearOnly ? Format.Y : data2.format.toPattern(), locale);
                String second = dateFormat.format(dateTwo);
                if (data2.negative)
                    second = "-" + second;
                if (!second.equals(text)) {
                    if (!data1.negative && !data2.negative) {
                        if (!yearOnly && data1.isFormat(Format.D_M_Y) && data1.format.equals(data2.format)
                                && dateOne.getMonth() == dateTwo.getMonth() && dateOne.getYear() == dateTwo.getYear()) { // Same month and year
                            text = text.substring(0, text.indexOf(' '));
                        } else if (!yearOnly && data1.isFormat(Format.D_M_Y) && data1.format.equals(data2.format)
                                && dateOne.getYear() == dateTwo.getYear()) { // Same year
                            text = text.substring(0, text.lastIndexOf(' '));
                        } else if (!yearOnly && data1.isFormat(Format.M_Y) && data1.format.equals(data2.format)
                                && dateOne.getYear() == dateTwo.getYear()) { // Same year
                            text = text.substring(0, text.indexOf(' '));
                        } else if ((yearOnly || (data1.isFormat(Format.Y) && data1.format.equals(data2.format))) // Two years only
                                && ((text.length() == 4 && second.length() == 4 && text.substring(0, 2).equals(second.substring(0, 2))) // of the same century
                                || (text.length() == 3 && second.length() == 3 && text.substring(0, 1).equals(second.substring(0, 1))))) {
                            second = second.substring(second.length() - 2); // Keeps the last two digits
                        }
                    }
                    text += (kind == Kind.BETWEEN_AND ? "~" : "→") + second;
                }
            }
        }
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
        if (data1.date != null) {
            txt += writePiece(data1);
            // Uppercase initial
            if (kind == Kind.EXACT && data1.isFormat(Format.M_Y)) {
                txt = txt.substring(0, 1).toUpperCase() + txt.substring(1);
            }
            if (kind == Kind.BETWEEN_AND || kind == Kind.FROM_TO) {
                txt += " " + Global.context.getString(kind == Kind.BETWEEN_AND ? R.string.and : R.string.to).toLowerCase();
                if (data2.date != null)
                    txt += writePiece(data2);
            }
        } else if (phrase != null) {
            txt = phrase;
        }
        return txt.trim();
    }

    String writePiece(Data date) {
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
        if (data1.date != null && !data1.isFormat(Format.D_M)) {
            return (data1.date.getYear() + 1900) * 10000 + (data1.date.getMonth() + 1) * 100 + data1.date.getDate();
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
