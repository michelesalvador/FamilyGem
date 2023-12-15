package app.familygem.detail;

import android.text.InputType;

import org.folg.gedcom.model.GedcomTag;

import app.familygem.DetailActivity;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import app.familygem.util.ChangeUtil;

public class ExtensionActivity extends DetailActivity {

    GedcomTag extension;

    @Override
    protected void format() {
        setTitle(getString(R.string.extension));
        extension = (GedcomTag)cast(GedcomTag.class);
        placeSlug(extension.getTag());
        place(getString(R.string.id), "Id", false, 0);
        place(getString(R.string.value), "Value");
        place("Ref", "Ref", false, 0);
        place("ParentTagName", "ParentTagName", false, 0); // Not sure if it is used in real life
        for (GedcomTag child : extension.getChildren()) {
            String text = U.traverseExtension(child, 0);
            if (text.endsWith("\n"))
                text = text.substring(0, text.length() - 1);
            placePiece(child.getTag(), text, child, InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        }
    }

    @Override
    public void delete() {
        U.deleteExtension(extension, Memory.getSecondToLastObject(), null);
        ChangeUtil.INSTANCE.updateChangeDate(Memory.getLeaderObject());
    }
}
