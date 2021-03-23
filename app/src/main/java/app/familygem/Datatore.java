// Questa classe riceve una data Gedcom, la analizza e la traduce in una classe Data

package app.familygem;

import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import app.familygem.constants.Format;

class Datatore {

	Data data1;
	Data data2;
	String frase; // Quella che andrà tra parentesi
	int tipo; // da 0 a 10
	static final String[] mesiGedcom = { "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC" };
	static final String[] prefissi = { "", "ABT", "CAL", "EST", "AFT", "BEF", "BET", "FROM", "TO", "FROM", "(" }; // Todo "INT"
	static final String[] suffissi = { "B.C.", "BC", "BCE" };

	Datatore( String dataGc ) {
		data1 = new Data();
		data2 = new Data();
		analizza( dataGc );
	}

	class Data {
		Date date;
		SimpleDateFormat format;
		boolean negativa;
		boolean doppia;

		Data() {
			DateFormatSymbols simboliFormato = new DateFormatSymbols();
			simboliFormato.setShortMonths( mesiGedcom );
			format = new SimpleDateFormat();
			format.setDateFormatSymbols( simboliFormato );
		}

		// Prende una data Gedcom esatta e ci farcisce gli attributi della classe Data
		void scanna( String dataGc ) {

			// Riconosce se la data è B.C. e rimuove il suffisso
			negativa = false; // resetta eventuale true
			for( String suffix : suffissi ) {
				if( dataGc.endsWith(suffix) ) {
					negativa = true;
					dataGc = dataGc.substring(0, dataGc.indexOf(suffix)).trim();
					break;
				}
			}
			dataGc = dataGc.replaceAll("[\\\\_\\-|.,;:?'\"#^&*°+=~()\\[\\]{}]", " "); // tutti tranne '/'
			
			// Distingue una data con anno doppio 1712/1713 da una data tipo 17/12/1713
			doppia = false; // reset
			if( dataGc.indexOf('/') > 0 ) {
				String[] tata = dataGc.split("[/ ]");
				if( tata.length > 1 && tata[tata.length-2].length() < 3 && U.soloNumeri( tata[tata.length-2] ) <= 12 )
					dataGc = dataGc.replace( '/', ' ' );
				else
					doppia = true;
			}
			for( String dateFormat : Format.PATTERNS ) {
				format.applyPattern( dateFormat );
				try {
					date = format.parse( dataGc );
					break;
				} catch( ParseException e ) {}
			}
			if( format.toPattern().equals(Format.D_m_Y) )
				format.applyPattern(Format.D_M_Y);
			if( format.toPattern().equals(Format.m_Y) )
				format.applyPattern(Format.M_Y);

			// Rende la data effettivamente negativa (per il calcolo delle età)
			if( negativa ) cambiaEra();
		}

		// Rende la data BC oppure AD coerentemente con il boolean 'negativa'
		void cambiaEra() {
			if( date != null ) {
				// La data viene riparsata cambiandogli l'era
				SimpleDateFormat sdf = new SimpleDateFormat(Format.D_M_Y + " G", Locale.US);
				String data = sdf.format(date);
				if( negativa )
					data = data.replace("AD", "BC");
				else
					data = data.replace("BC", "AD");
				try {
					date = sdf.parse(data);
				} catch (Exception e) {}
			}
		}

		@Override
		public String toString() {
			DateFormat format = new SimpleDateFormat("d MMM yyyy G HH:mm:ss", Locale.US);
			return format.format(date);
		}
	}
	
	// Riconosce il tipo di data e crea la classe Data
	void analizza( String dataGc ) {

		// Resetta i valori che contano
		tipo = 0;
		data1.date = null;

		dataGc = dataGc.trim();
		String dataGcMaiusc = dataGc.toUpperCase();
		
		// Riconosce i tipi diversi da 0 e converte la stringa in Data
		for( int t = 1; t < prefissi.length; t++  ) {
			if( dataGcMaiusc.startsWith(prefissi[t]) ) {
				tipo = t;
				if( t == 6 && dataGcMaiusc.contains("AND") ) { // BET... AND
					if( dataGcMaiusc.indexOf("AND") > dataGcMaiusc.indexOf("BET")+4 )
						data1.scanna( dataGcMaiusc.substring( 4, dataGcMaiusc.indexOf("AND")-1 ));
					if( dataGcMaiusc.length() > dataGcMaiusc.indexOf("AND")+3 )
						data2.scanna( dataGcMaiusc.substring( dataGcMaiusc.indexOf("AND")+4 ));
				} else if( t == 7 && dataGcMaiusc.contains("TO") ) { // FROM... TO
					tipo = 9;
					if( dataGcMaiusc.indexOf("TO") > dataGcMaiusc.indexOf("FROM")+5 )
						data1.scanna( dataGcMaiusc.substring( 5, dataGcMaiusc.indexOf("TO")-1 ));
					if( dataGcMaiusc.length() > dataGcMaiusc.indexOf("TO")+2 )
						data2.scanna( dataGcMaiusc.substring( dataGcMaiusc.indexOf("TO")+3 ) );
				} else if( t == 10 ) { // Data frase tra parentesi
					//data1.scanna( dataGc.substring( 1, dataGc.indexOf(")") ) ); // Ripristina date del tipo 0 messe tra parentesi
					if( dataGc.endsWith(")") )
						frase = dataGc.substring( 1, dataGc.indexOf(")") );
					else
						frase = dataGc;
				} else if( dataGcMaiusc.length() > prefissi[t].length() ) // Altri prefissi seguiti da qualcosa
					data1.scanna( dataGcMaiusc.substring( prefissi[t].length() + 1 ) );
				break;
			}
		}
		// Rimane da provare il tipo 0, altrimenti diventa una frase
		if( tipo == 0 && !dataGc.isEmpty() ) {
			data1.scanna( dataGc );
			if( data1.date == null ) {
				frase = dataGc;
				tipo = 10;
			}
		}
	}

	/** Write a short text-version of the date in the default locale.
	 * @param yearOnly Write the year only or the whole date with day and month
	 * @return The date well written
	 */
	public String writeDate(boolean yearOnly) {
		String text = "";
		if( data1.date != null && !(data1.format.toPattern().equals(Format.D_M) && yearOnly) ) {
			Locale locale = Locale.getDefault();
			DateFormat dateFormat = new SimpleDateFormat(yearOnly ? Format.Y : data1.format.toPattern(), locale);
			Date dateOne = (Date)data1.date.clone(); // Cloned so the year of a double date can be modified without consequences
			if( data1.doppia )
				dateOne.setYear(data1.date.getYear() + 1);
			text = dateFormat.format(dateOne);
			if( data1.negativa )
				text = "-" + text;
			if( tipo >= 1 && tipo <= 3 )
				text += "?";
			else if( tipo == 4 || tipo == 7 )
				text += "→";
			else if( tipo == 5 )
				text = "←" + text;
			else if( tipo == 8 )
				text = "→" + text;
			else if( (tipo == 6 || tipo == 9) && data2.date != null ) {
				Date dateTwo = (Date)data2.date.clone();
				if( data2.doppia )
					dateTwo.setYear(data2.date.getYear() + 1);
				dateFormat = new SimpleDateFormat(yearOnly ? Format.Y : data2.format.toPattern(), locale);
				String second = dateFormat.format(dateTwo);
				if( data2.negativa )
					second = "-" + second;
				if( !second.equals(text) ) {
					if( !data1.negativa && !data2.negativa ) {
						if( !yearOnly && data1.format.toPattern().equals(Format.D_M_Y) && data1.format.equals(data2.format)
								&& dateOne.getMonth() == dateTwo.getMonth() && dateOne.getYear() == dateTwo.getYear() ) { // Same month and year
							text = text.substring(0, text.indexOf(' '));
						} else if( !yearOnly && data1.format.toPattern().equals(Format.D_M_Y) && data1.format.equals(data2.format)
								&& dateOne.getYear() == dateTwo.getYear() ) { // Same year
							text = text.substring(0, text.lastIndexOf(' '));
						} else if( !yearOnly && data1.format.toPattern().equals(Format.M_Y) && data1.format.equals(data2.format)
								&& dateOne.getYear() == dateTwo.getYear() ) { // Same year
							text = text.substring(0, text.indexOf(' '));
						} else if( (yearOnly || (data1.format.toPattern().equals(Format.Y) && data1.format.equals(data2.format))) // Two years only
								&& ((text.length() == 4 && second.length() == 4 && text.substring(0, 2).equals(second.substring(0, 2))) // of the same century
								|| (text.length() == 3 && second.length() == 3 && text.substring(0, 1).equals(second.substring(0, 1)))) ) {
							second = second.substring(second.length() - 2); // Keeps the last two digits
						}
					}
					text += (tipo == 6 ? "~" : "→") + second;
				}
			}
		}
		return text;
	}

	// Restituisce l'anno della data principale oppure 9999
	public int soloAnno() {
		int anno = 9999;
		if( data1.date != null && !data1.format.toPattern().equals(Format.D_M) )
			anno = data1.date.getYear() + 1900;
		return anno;
	}
}
