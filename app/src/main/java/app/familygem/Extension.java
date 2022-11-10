package app.familygem;

import org.folg.gedcom.model.GedcomTag;

public class Extension {
	String name;
	String text;
	GedcomTag gedcomTag;
	public Extension(String name, String text, GedcomTag gedcomTag ) {
		this.name = name;
		this.text = text;
		this.gedcomTag = gedcomTag;
	}
}
