package app.familygem.visitor;

import org.folg.gedcom.model.ExtensionContainer;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.NoteRef;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Visitor for shared Note with a triple function:
 * - Counts in all the records of the Gedcom the references to the shared Note
 * - Optionally deletes these references to the Note
 * - In the meantime, collects all the leader objects
 */
public class NoteReferences extends TotalVisitor {

    private String id;  // Id of the shared Note
    private boolean deleteRefs; // Flag to delete the Refs to the Note rather than counting them
    private Object leader;
    public int count = 0; // Number of references to the shared note
    public Set<Object> leaders = new LinkedHashSet<>(); // List of first objects of the stacks containing the shared Note

    public NoteReferences(Gedcom gedcom, String id, boolean deleteRefs) {
        this.id = id;
        this.deleteRefs = deleteRefs;
        if (gedcom != null) gedcom.accept(this);
    }

    @Override
    boolean visit(ExtensionContainer object, boolean isLeader) {
        if (isLeader)
            leader = object;
        if (object instanceof NoteContainer) {
            NoteContainer container = (NoteContainer)object;
            Iterator<NoteRef> refs = container.getNoteRefs().iterator();
            while (refs.hasNext()) {
                NoteRef nr = refs.next();
                if (nr.getRef().equals(id)) {
                    leaders.add(leader);
                    if (deleteRefs) refs.remove();
                    else count++;
                }
            }
            if (container.getNoteRefs().isEmpty()) container.setNoteRefs(null);
        }
        return true;
    }
}
