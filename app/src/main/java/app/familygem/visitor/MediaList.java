package app.familygem.visitor;

import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.Visitor;

import java.util.LinkedHashSet;
import java.util.Set;

import app.familygem.F;
import app.familygem.Global;

/**
 * Collects an ordered set of Media.
 * Can almost always replace {@link MediaContainerList}.
 */
public class MediaList extends Visitor {

    public Set<Media> list = new LinkedHashSet<>();
    private Gedcom gedcom;
    /**
     * <ol start="0">
     *     <li>All media
     *     <li>Only shared media
     *     <li>Only local media (no gedcom needed)
     *     <li>Shared and local but only previewable images and videos (for the main menu)
     * </ol>
     */
    private int mediaType;

    public MediaList(Gedcom gedcom, int mediaType) {
        this.gedcom = gedcom;
        this.mediaType = mediaType;
    }

    private boolean visita(Object object) {
        if (object instanceof MediaContainer) {
            MediaContainer container = (MediaContainer)object;
            if (mediaType == 0)
                list.addAll(container.getAllMedia(gedcom)); // Adds shared and local media
            else if (mediaType == 2)
                list.addAll(container.getMedia()); // Local media only
            else if (mediaType == 3)
                for (Media med : container.getAllMedia(gedcom))
                    filter(med);
        }
        return true;
    }

    /**
     * Adds only the media alleged with preview (images and videos).
     */
    private void filter(Media media) {
        String path = F.mediaPath(Global.settings.openTree, media); // TODO: and images from URIs?
        if (path != null) {
            int index = path.lastIndexOf('.');
            if (index > 0) {
                String extension = path.substring(index + 1);
                switch (extension) {
                    case "jpg":
                    case "jpeg":
                    case "png":
                    case "gif":
                    case "bmp":
                    case "webp":
                    case "heic": // TODO: the image may be rotated 90° or 180°
                    case "heif": // Synonymous with .heic
                    case "mp4":
                    case "3gp":
                    case "webm":
                    case "mkv":
                        list.add(media);
                }
            }
        }
    }

    @Override
    public boolean visit(Gedcom gedcom) {
        if (mediaType < 2)
            list.addAll(gedcom.getMedia()); // Finds all shared media of the tree
        else if (mediaType == 3)
            for (Media med : gedcom.getMedia())
                filter(med);
        return true;
    }

    @Override
    public boolean visit(Person p) {
        return visita(p);
    }

    @Override
    public boolean visit(Family f) {
        return visita(f);
    }

    @Override
    public boolean visit(EventFact e) {
        return visita(e);
    }

    @Override
    public boolean visit(Name n) {
        return visita(n);
    }

    @Override
    public boolean visit(SourceCitation c) {
        return visita(c);
    }

    @Override
    public boolean visit(Source s) {
        return visita(s);
    }
}
