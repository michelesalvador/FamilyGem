package app.familygem;

import org.folg.gedcom.model.GedcomTag;

public class Extension {
	String nome;
	String testo;
	GedcomTag gedcomTag;
	public Extension(String nome, String testo, GedcomTag gedcomTag ) {
		this.nome = nome;
		this.testo = testo;
		this.gedcomTag = gedcomTag;
	}
}
