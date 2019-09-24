// Questa classe riceve una data Gedcom, la analizza e la traduce in una classe Data

package app.familygem;

import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Datatore {

	Data data1;
	Data data2;
	String frase; // Quella che andrà tra parentesi
	int tipo; // da 0 a 10
	static String[] paterni = { "d MMM yyy", "d M yyy", "MMM yyy", "M yyy", "d MMM", "yyy" };
	static String G_M_A = paterni[0];
	static String G_m_A = paterni[1];
	static String M_A = paterni[2];
	static String m_A = paterni[3];
	static String G_M = paterni[4];
	static String A = paterni[5];
	static String[] mesiGedcom = { "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC" };
	static String[] prefissi = { "", "ABT", "CAL", "EST", "AFT", "BEF", "BET", "FROM", "TO", "FROM", "(" }; // Todo "INT"

	public Datatore( String dataGc ) {
		data1 = new Data();
		data2 = new Data();
		analizza( dataGc );
	}

	class Data {
		Date date;
		SimpleDateFormat format;
		boolean doppia;

		Data() {
			DateFormatSymbols simboliFormato = new DateFormatSymbols();
			simboliFormato.setShortMonths( mesiGedcom );
			//Locale locale = new Locale("en");
			format = new SimpleDateFormat();
			format.setDateFormatSymbols( simboliFormato );
		}

		// Prende una data Gedcom esatta e ci farcisce gli attributi della classe Data
		void scanna( String dataGc ) {

			dataGc = dataGc.replaceAll("[\\\\_\\-|.,;:?'\"#^&*°+=~()\\[\\]{}]", " "); // tutti tranne '/'
			
			// Distingue una data con anno doppio 1712/1713 da una data tipo 17/12/1713
			doppia = false; // resetta eventuale true
			if( dataGc.indexOf('/') > 0 ) {
				String[] tata = dataGc.split("[/ ]");
				if( tata.length > 1 && tata[tata.length-2].length() < 3 && U.soloNumeri( tata[tata.length-2] ) <= 12 )
					dataGc = dataGc.replace( '/', ' ' );
				else
					doppia = true;
			}
			for( String forma : paterni ) {
				format.applyPattern( forma );
				try {
					date = format.parse( dataGc );
					break;
				} catch( ParseException e ) {}
			}
			if( format.toPattern().equals(G_m_A) )
				format.applyPattern( G_M_A );
			if( format.toPattern().equals(m_A) )
				format.applyPattern( M_A );
		}
	}
	
	// Riconosce il tipo di data e crea la classe Data
	public void analizza( String dataGc ) {

		// Resetta i valori che contano
		tipo = 0;

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

	// Scrive solo l'anno semplificato alla mia maniera
	public String scriviAnno() {
		String anno = "";
		if( data1.date != null && !data1.format.toPattern().equals(G_M) ) {
			SimpleDateFormat formatoAnno = new SimpleDateFormat("yyy");
			if( data1.doppia )
				data1.date.setYear( data1.date.getYear() + 1 ); // cambia definitamente l'anno, ma non è un problema
			anno = formatoAnno.format( data1.date );
			if( tipo >= 1 && tipo <= 3 )
				anno += "?";
			if( tipo == 4 || tipo == 7 )
				anno += "→";
			if( tipo == 5 )
				anno = "←"+anno;
			if( tipo == 8 )
				anno = "→"+anno;
			if( tipo == 6 || tipo == 9 ) {
				anno += tipo==6 ? "~" : "→";
				if( data2.date != null && !data2.format.toPattern().equals(G_M) ) {
					if( data2.doppia )
						data2.date.setYear( data2.date.getYear() + 1 );
					String anno2 = formatoAnno.format( data2.date );
					if( !anno.equals(anno2) ) {
						if( anno2.length() > 3 && anno2.substring(0,2).equals(anno.substring(0,2)) ) // se sono dello stesso secolo
							anno2 = anno2.substring( anno2.length()-2 ); // prende solo gli anni
						anno += anno2;
					}
				}
			}
		}
		return anno;
	}

	// Restituisce l'anno della data principale oppure 9999
	public int soloAnno() {
		int anno = 9999;
		if( data1.date != null && !data1.format.toPattern().equals(G_M) )
			anno = data1.date.getYear() + 1900;
		return anno;
	}
}
