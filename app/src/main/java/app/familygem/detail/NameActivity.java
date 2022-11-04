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
			String firstName = "";
			String lastName = "";
			String epithet = n.getValue();
			if( epithet != null ) {
				firstName = epithet.replaceAll("/.*?/", "").trim(); // Remove the lastName
				if( epithet.indexOf('/') < epithet.lastIndexOf('/') )
					lastName = epithet.substring(epithet.indexOf('/') + 1, epithet.lastIndexOf('/')).trim();
			}
			placePiece(getString(R.string.given), firstName, 4043, false);
			placePiece(getString(R.string.surname), lastName, 6064, false);
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
		U.placeMedia(box, n, true); // It seems strange to me that a Name has Media .. anyway .. //Mi sembra strano che un Name abbia Media.. comunque..
		U.placeSourceCitations(box, n);
	}

	@Override
	public void delete() {
		Person currentPerson = gc.getPerson(Global.indi);
		currentPerson.getNames().remove(n);
		U.updateChangeDate(currentPerson);
		Memory.setInstanceAndAllSubsequentToNull(n);
	}
}
