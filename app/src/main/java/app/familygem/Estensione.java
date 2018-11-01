package app.familygem;

import org.folg.gedcom.model.GedcomTag;

public class Estensione {
	String nome;
	String testo;
	GedcomTag gedcomTag;
	public Estensione( String nome, String testo, GedcomTag gedcomTag ) {
		this.nome = nome;
		this.testo = testo;
		this.gedcomTag = gedcomTag;
	}
}
