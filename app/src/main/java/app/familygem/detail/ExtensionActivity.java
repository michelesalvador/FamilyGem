package app.familygem.detail;

import org.folg.gedcom.model.GedcomTag;

import app.familygem.DetailActivity;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import app.familygem.util.ChangeUtils;

public class ExtensionActivity extends DetailActivity {

    GedcomTag e;

    @Override
    public void format() {
        setTitle(getString(R.string.extension));
        e = (GedcomTag)cast(GedcomTag.class);
        placeSlug(e.getTag());
        place(getString(R.string.id), "Id", false, false);
        place(getString(R.string.value), "Value", true, true);
        place("Ref", "Ref", false, false);
        place("ParentTagName", "ParentTagName", false, false); // Not sure if it is used in real life
        for (GedcomTag child : e.getChildren()) {
            String text = U.traverseExtension(child, 0);
            if (text.endsWith("\n"))
                text = text.substring(0, text.length() - 1);
            placePiece(child.getTag(), text, child, true);
        }
    }

    @Override
    public void delete() {
        U.deleteExtension(e, Memory.getSecondToLastObject(), null);
        ChangeUtils.INSTANCE.updateChangeDate(Memory.getLeaderObject());
    }
}
