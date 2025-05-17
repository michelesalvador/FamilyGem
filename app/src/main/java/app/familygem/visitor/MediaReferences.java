package app.familygem.visitor;

import org.folg.gedcom.model.ExtensionContainer;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;
import org.folg.gedcom.model.MediaRef;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Visitor that receives a shared media and has a triple function:
 * - Count the references to the media in all MediaContainers
 * - Or delete the same references to the media
 * - In the meantime, list the leader objects of the stacks that contain the media
 */
public class MediaReferences extends TotalVisitor {

    private final Media media; // The shared Media
    private final boolean shouldDeleteRefs;
    private Object leader; // The first object of the stack
    public int num = 0; // Number of references to the Media
    public Set<Object> leaders = new LinkedHashSet<>(); // List of the leader objects containing the Media

    public MediaReferences(Gedcom gedcom, Media media, boolean deleteRefs) {
        this.media = media;
        shouldDeleteRefs = deleteRefs;
        gedcom.accept(this);
    }

    @Override
    boolean visit(ExtensionContainer object, boolean isLeader) {
        if (isLeader)
            leader = object;
        if (object instanceof MediaContainer) {
            MediaContainer container = (MediaContainer)object;
            Iterator<MediaRef> iterator = container.getMediaRefs().iterator();
            while (iterator.hasNext()) {
                MediaRef mediaRef = iterator.next();
                if (mediaRef.getRef() == null) {
                    // Removes possible null reference caused by the "mediaId" bug, fixed with commit dbea5adb
                    iterator.remove();
                } else if (mediaRef.getRef().equals(media.getId())) {
                    leaders.add(leader);
                    if (shouldDeleteRefs)
                        iterator.remove();
                    else
                        num++;
                }
            }
            if (container.getMediaRefs().isEmpty())
                container.setMediaRefs(null);
        }
        return true;
    }
}
