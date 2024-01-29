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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import app.familygem.ProfileMediaFragment;

/**
 * Ordered map of the media each with its own container object.
 * The container is used practically only by 'Unlink media' context menu in {@link ProfileMediaFragment}.
 */
public class MediaContainerList extends Visitor {

    public List<MedCont> mediaList = new ArrayList<>();
    private Gedcom gedcom;
    private boolean requestAll; // Asks to list all media (even local), otherwise the shared media objects only

    public MediaContainerList(Gedcom gedcom, boolean requestAll) {
        this.gedcom = gedcom;
        this.requestAll = requestAll;
    }

    private boolean visitInternal(Object object) {
        if (requestAll && object instanceof MediaContainer) {
            MediaContainer container = (MediaContainer)object;
            for (Media media : container.getAllMedia(gedcom)) { // Media objects and local media of each record
                MedCont medCont = new MedCont(media, object);
                if (!mediaList.contains(medCont))
                    mediaList.add(medCont);
            }
        }
        return true;
    }

    @Override
    public boolean visit(Gedcom gedcom) {
        for (Media media : gedcom.getMedia())
            mediaList.add(new MedCont(media, gedcom)); // Collects the media objects
        if (this.gedcom == null) this.gedcom = gedcom; // Just in case
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

    /**
     * Class representing a Media with its container object.
     */
    static public class MedCont {
        public Media media;
        public Object container;

        public MedCont(Media media, Object container) {
            this.media = media;
            this.container = container;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof MedCont && media.equals(((MedCont)o).media);
        }

        @Override
        public int hashCode() {
            return Objects.hash(media);
        }
    }
}