package app.familygem.visitor;

import org.folg.gedcom.model.ExtensionContainer;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.MediaContainer;
import org.folg.gedcom.model.MediaRef;

/**
 * Visitor similar to MediaContainers, having a double function:
 * - Edits the references pointing to a shared media using an uniqueness guardian
 * - Removes the guardians
 */
public class MediaContainersGuarded extends TotalVisitor {

    private final String oldId;
    private final String newId;
    private final boolean clean;

    public MediaContainersGuarded(Gedcom gedcom, String oldId, String newId, boolean clean) {
        this.oldId = oldId;
        this.newId = newId;
        this.clean = clean;
        gedcom.accept(this);
    }

    @Override
    boolean visit(ExtensionContainer object, boolean isLeader) {
        if (object instanceof MediaContainer) {
            final String GUARDIAN = "modifiedMediaRef";
            for (MediaRef mediaRef : ((MediaContainer)object).getMediaRefs()) {
                // Removes guardian
                if (clean && mediaRef.getExtension(GUARDIAN) != null) {
                    mediaRef.getExtensions().remove(GUARDIAN);
                    if (mediaRef.getExtensions().isEmpty())
                        mediaRef.setExtensions(null);
                } // Modifies ID and adds guardian
                else if (mediaRef.getExtension(GUARDIAN) == null && mediaRef.getRef().equals(oldId)) {
                    mediaRef.setRef(newId);
                    mediaRef.putExtension(GUARDIAN, true);
                }
            }
        }
        return true;
    }
}
