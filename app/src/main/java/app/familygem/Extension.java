package app.familygem;

import org.folg.gedcom.model.GedcomTag;

/**
 * Represents the name and text of an extension.
 */
public class Extension {
    public String name;
    public String text;
    public GedcomTag gedcomTag;

    public Extension(String name, String text, GedcomTag gedcomTag) {
        this.name = name;
        this.text = text;
        this.gedcomTag = gedcomTag;
    }
}
