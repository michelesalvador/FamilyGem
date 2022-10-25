package app.familygem.detail;

import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Person;
import app.familygem.DetailActivity;
import app.familygem.Global;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import static app.familygem.Global.gc;

public class NameActivity extends DetailActivity {

	Name n;

	@Override
	public void format() {
		setTitle(R.string.name);
		placeSlug("NAME", null);
		n = (Name)cast(Name.class);
		if( Global.settings.expert )
			place(getString(R.string.value), "Value");
		else {
			String nome = "";
			String cognome = "";
			String epiteto = n.getValue();
			if( epiteto != null ) {
				nome = epiteto.replaceAll("/.*?/", "").trim(); // Rimuove il cognome
				if( epiteto.indexOf('/') < epiteto.lastIndexOf('/') )
					cognome = epiteto.substring(epiteto.indexOf('/') + 1, epiteto.lastIndexOf('/')).trim();
			}
			placePiece(getString(R.string.given), nome, 4043, false);
			placePiece(getString(R.string.surname), cognome, 6064, false);
		}
		place(getString(R.string.nickname), "Nickname");
		place(getString(R.string.type), "Type", true, false); // _TYPE in GEDCOM 5.5, TYPE in GEDCOM 5.5.1
		place(getString(R.string.prefix), "Prefix", Global.settings.expert, false);
		place(getString(R.string.given), "Given", Global.settings.expert, false);
		place(getString(R.string.surname_prefix), "SurnamePrefix", Global.settings.expert, false);
		place(getString(R.string.surname), "Surname", Global.settings.expert, false);
		place(getString(R.string.suffix), "Suffix", Global.settings.expert, false);
		place(getString(R.string.married_name), "MarriedName", false, false); // _marrnm
		place(getString(R.string.aka), "Aka", false, false); // _aka
		place(getString(R.string.romanized), "Romn", Global.settings.expert, false);
		place(getString(R.string.phonetic), "Fone", Global.settings.expert, false);
		placeExtensions(n);
		U.placeNotes(box, n, true);
		U.placeMedia(box, n, true); // Mi sembra strano che un Name abbia Media.. comunque..
		U.placeSourceCitations(box, n);
	}

	@Override
	public void delete() {
		Person costui = gc.getPerson(Global.indi);
		costui.getNames().remove(n);
		U.updateChangeDate(costui);
		Memory.setInstanceAndAllSubsequentToNull(n);
	}
}
