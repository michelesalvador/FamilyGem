// Singleton che gestisce gli oggetti dei 2 Gedcom durante l'importazione degli aggiornamenti

package app.familygem;

import java.util.ArrayList;
import java.util.List;

public class Confronto {

	private static final Confronto confronto = new Confronto();
	private List<Fronte> lista = new ArrayList<>();
	int posizione = 0;

	static Confronto getInstance() {
		return confronto;
	}

	public static List<Fronte> getLista() {
		return getInstance().lista;
	}

	static Fronte addFronte( Object oggetto, Object oggetto2, int tipo ) {
		Fronte fronte = new Fronte();
		fronte.oggetto = oggetto;
		fronte.oggetto2 = oggetto2;
		fronte.tipo = tipo;
		getLista().add( fronte );
		return fronte;
	}

	static Fronte getFronte() {
		return getLista().get( getInstance().posizione - 1 ) ;
	}

	static void next() {
		getInstance().posizione++;
	}

	static void prev() {
		getInstance().posizione--;
		// Tornando indietro prima di Compara resetta la situazione
		if( getInstance().posizione < 0 ) {
			getInstance().lista.clear();
			getInstance().posizione = 0;
		}
	}

	static class Fronte {
		Object oggetto;
		Object oggetto2;
		int tipo; // numero da 1 a 7 che definisce il tipo: 1 Nota -> 7 Famiglia
		boolean doppiaOpzione; // ha la possibilità di aggiungi + sostituisci
		/*
		che fare di questa coppia di oggetti:
		0 niente
		1 oggetto2 viene aggiunto ad albero
		2 oggetto2 sostituisce oggetto
		3 oggetto viene eliminato */
		int destino;
	}
}