package app.familygem.visitor;

import org.folg.gedcom.model.ExtensionContainer;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Starting from the ID of a source, generates a list of triplets: leader / container / citation of the source.
 */
public class ListOfSourceCitations extends TotalVisitor {

    public List<Triplet> list = new ArrayList<>();
    private String id; // ID of the source
    private Object leader;

    public ListOfSourceCitations(Gedcom gedcom, String id) {
        this.id = id;
        gedcom.accept(this);
    }

    @Override
    boolean visit(ExtensionContainer object, boolean isLeader) {
        if (isLeader)
            leader = object;
        if (object instanceof SourceCitationContainer) {
            analyze(object, ((SourceCitationContainer)object).getSourceCitations());
        } // Note does not extend SourceCitationContainer, but implements its own methods
        else if (object instanceof Note) {
            analyze(object, ((Note)object).getSourceCitations());
        }
        return true;
    }

    private void analyze(Object container, List<SourceCitation> citations) {
        for (SourceCitation citation : citations)
            // Note-sources have no Ref to a source
            if (citation.getRef() != null && citation.getRef().equals(id)) {
                Triplet triplet = new Triplet();
                triplet.leader = leader;
                triplet.container = container;
                triplet.citation = citation;
                list.add(triplet);
            }
    }

    public Object[] getProgenitors() {
        Set<Object> heads = new LinkedHashSet<>(); // Merges duplicates
        for (Triplet tri : list) {
            heads.add(tri.leader);
        }
        return heads.toArray();
    }

    /**
     * Class to store together the three elements: leader - container - citation
     */
    public static class Triplet {
        public Object leader;
        public Object container; // It should be a SourceCitationContainer but Note is an exception
        public SourceCitation citation;
    }
}
