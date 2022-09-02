// Strettamente connesso a TrovaPila, individua gli oggetti da tenere nella pila

package app.familygem.visitor;

class PulisciPila extends VisitorTotale {

	private Object scopo;
	boolean daEliminare = true;

	PulisciPila( Object scopo ) {
		this.scopo = scopo;
	}

	@Override
	boolean visita( Object oggetto, boolean capo ) { // il boolean qui è inutilizzato
		if( oggetto.equals(scopo) )
			daEliminare = false;
		return true;
	}
}

