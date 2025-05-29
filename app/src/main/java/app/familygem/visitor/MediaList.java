package app.familygem.visitor;

import android.net.Uri;

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

import app.familygem.Global;
import app.familygem.util.FileUtil;

/**
 * Collects an ordered set of Media.
 * Can almost always replace {@link MediaContainerList}.
 */
public class MediaList extends Visitor {

    public Set<Media> list = new LinkedHashSet<>();
    private final Gedcom gedcom;
    /**
     * <ol start="0">
     *     <li>All media
     *     <li>Only shared media
     *     <li>Only local media (no Gedcom needed)
     *     <li>Shared and local media, but only images and videos with preview (for the main menu)
     * </ol>
     */
    private final int mediaType;

    public MediaList(Gedcom gedcom, int mediaType) {
        this.gedcom = gedcom;
        this.mediaType = mediaType;
    }

    private boolean visitInternal(MediaContainer object) {
        if (mediaType == 0)
            list.addAll(object.getAllMedia(gedcom)); // Shared and local media
        else if (mediaType == 2)
            list.addAll(object.getMedia()); // Local media only
        else if (mediaType == 3)
            for (Media med : object.getAllMedia(gedcom))
                filter(med);
        return true;
    }

    /**
     * Adds only the media alleged with preview (images and videos).
     */
    private void filter(Media media) {
        String path = FileUtil.INSTANCE.getPathFromMedia(media, Global.settings.openTree);
        if (path == null) { // Images from URIs
            Uri uri = FileUtil.INSTANCE.getUriFromMedia(media, Global.settings.openTree);
            if (uri != null) path = uri.getPath();
        }
        // Maybe is the URL of a web resource
        if (path == null && media.getFile() != null
                && (media.getFile().startsWith("http://") || media.getFile().startsWith("https://"))) {
            path = media.getFile();
            if (path.indexOf('?') > 0) path = path.substring(0, (path.indexOf('?'))); // Removes any query
        }
        if (path != null) {
            int index = path.lastIndexOf('.');
            if (index > 0) {
                String extension = path.substring(index + 1).toLowerCase();
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
                    case "pdf":
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
    public boolean visit(Person person) {
        return visitInternal(person);
    }

    @Override
    public boolean visit(Family family) {
        return visitInternal(family);
    }

    @Override
    public boolean visit(EventFact eventFact) {
        return visitInternal(eventFact);
    }

    @Override
    public boolean visit(Name name) {
        return visitInternal(name);
    }

    @Override
    public boolean visit(SourceCitation sourceCitation) {
        return visitInternal(sourceCitation);
    }

    @Override
    public boolean visit(Source source) {
        return visitInternal(source);
    }
}
