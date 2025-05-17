package app.familygem.visitor;

import org.folg.gedcom.model.ExtensionContainer;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.NoteRef;

/**
 * Visitor similar to NoteContainers, having a double function:
 * - Edits the references pointing to a shared note, using an uniqueness guardian
 * - Removes the guardians
 */
public class NoteContainersGuarded extends TotalVisitor {

    private final String oldId; // The old ID of the shared note to search for
    private final String newId; // The new ID to put in the Ref
    private final boolean clean;

    public NoteContainersGuarded(Gedcom gedcom, String oldId, String newId, boolean clean) {
        this.oldId = oldId;
        this.newId = newId;
        this.clean = clean;
        gedcom.accept(this);
    }

    @Override
    boolean visit(ExtensionContainer object, boolean isLeader) {
        if (object instanceof NoteContainer) {
            final String GUARDIAN = "modifiedNoteRef";
            for (NoteRef noteRef : ((NoteContainer)object).getNoteRefs()) {
                // Removes guardian
                if (clean && noteRef.getExtension(GUARDIAN) != null) {
                    noteRef.getExtensions().remove(GUARDIAN);
                    if (noteRef.getExtensions().isEmpty())
                        noteRef.setExtensions(null);
                } // Modifies ID and adds guardian
                else if (noteRef.getExtension(GUARDIAN) == null && noteRef.getRef().equals(oldId)) {
                    noteRef.setRef(newId);
                    noteRef.putExtension(GUARDIAN, true);
                }
            }
        }
        return true;
    }
}
