package app.familygem.visitor;

import org.folg.gedcom.model.ExtensionContainer;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.NoteRef;

import java.util.HashSet;
import java.util.Set;

/**
 * Visitor somewhat complementary to NoteReferences, having a double function:
 * - Edits refs pointing to the shared note
 * - Collects a list of containers that include the shared Note
 */
public class NoteContainers extends TotalVisitor {

    public Set<NoteContainer> containers = new HashSet<>();
    private Note note; // The shared Note to search for
    private String newId; // The new ID to put in the Ref

    public NoteContainers(Gedcom gedcom, Note note, String newId) {
        this.note = note;
        this.newId = newId;
        gedcom.accept(this);
    }

    @Override
    boolean visit(ExtensionContainer object, boolean isLeader) {
        if (object instanceof NoteContainer) {
            for (NoteRef noteRef : ((NoteContainer)object).getNoteRefs()) {
                if (noteRef.getRef().equals(note.getId())) {
                    noteRef.setRef(newId);
                    containers.add((NoteContainer)object);
                }
            }
        }
        return true;
    }
}
