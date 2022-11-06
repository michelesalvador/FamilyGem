// Singleton che gestisce gli oggetti dei 2 Gedcom durante l'importazione degli aggiornamenti

package app.familygem;

import android.app.Activity;
import java.util.ArrayList;
import java.util.List;

public class Comparison {

	private static final Comparison comparison = new Comparison();
	private List<Fronte> lista = new ArrayList<>();
	boolean autoProsegui; // stabilisce se accettare automaticamente tutti gli aggiornamenti
	int quanteScelte; // Scelte totali in caso di autoProsegui
	int scelteFatte; // Posizione in caso di autoProsegui

	static Comparison get() {
		return comparison;
	}

	public static List<Fronte> getLista() {
		return get().lista;
	}

	static Fronte addFronte( Object object, Object object2, int tipo ) {
		Fronte fronte = new Fronte();
		fronte.object = object;
		fronte.object2 = object2;
		fronte.tipo = tipo;
		getLista().add( fronte );
		return fronte;
	}

	// Restituisce il fronte attualmente attivo
	static Fronte getFronte(Activity attivita) {
		return getLista().get( attivita.getIntent().getIntExtra("posizione",0) - 1 );
	}

	// Da chiamare quando si esce dal processo di confronto
	static void reset() {
		getLista().clear();
		get().autoProsegui = false;
	}

	static class Fronte {
		Object object;
		Object object2;
		int tipo; // numero da 1 a 7 che definisce il tipo: 1 Nota -> 7 Famiglia
		boolean doppiaOpzione; // ha la possibilità di aggiungi + sostituisci
		/*
		che fare di questa coppia di oggetti:
		0 niente
		1 object2 viene aggiunto ad albero
		2 object2 sostituisce object
		3 object viene eliminato */
		int destino;
	}
}