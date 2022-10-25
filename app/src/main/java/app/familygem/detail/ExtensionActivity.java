package app.familygem.detail;

import org.folg.gedcom.model.GedcomTag;
import app.familygem.DetailsActivity;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;

public class ExtensionActivity extends DetailsActivity {

	GedcomTag e;

	@Override
	public void impagina() {
		setTitle(getString(R.string.extension));
		e = (GedcomTag)cast(GedcomTag.class);
		placeSlug(e.getTag());
		place(getString(R.string.id), "Id", false, false);
		place(getString(R.string.value), "Value", true, true);
		place("Ref", "Ref", false, false);
		place("ParentTagName", "ParentTagName", false, false); // non ho capito se viene usato o no
		for( GedcomTag child : e.getChildren() ) {
			String text = U.scavaEstensione(child, 0);
			if( text.endsWith("\n") )
				text = text.substring(0, text.length() - 1);
			placePiece(child.getTag(), text, child, true);
		}
	}

	@Override
	public void elimina() {
		U.eliminaEstensione(e, Memory.oggettoContenitore(), null);
		U.updateChangeDate(Memory.oggettoCapo());
	}
}
