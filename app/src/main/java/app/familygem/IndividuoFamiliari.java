package app.familygem;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SpouseFamilyRef;
import java.util.Collections;
import java.util.List;
import app.familygem.constant.Relation;
import app.familygem.detail.Famiglia;
import static app.familygem.Global.gc;

public class IndividuoFamiliari extends Fragment {

	private View vistaFamiglia;
	Person uno;

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState ) {
		vistaFamiglia = inflater.inflate(R.layout.individuo_scheda, container, false);
		if( gc != null ) {
			uno = gc.getPerson( Global.indi);
			if( uno != null ) {
				/* ToDo Mostrare/poter settare nelle famiglie geniotriali il pedigree, in particolare 'adopted'
				LinearLayout scatola = vistaFamiglia.findViewById( R.id.contenuto_scheda );
				for( ParentFamilyRef pfr : uno.getParentFamilyRefs() ) {
					U.metti( scatola, "Ref", pfr.getRef() );
					U.metti( scatola, "Primary", pfr.getPrimary() ); // Custom tag _PRIM _PRIMARY
					U.metti( scatola, "Relationship Type", pfr.getRelationshipType() ); // Tag PEDI (pedigree)
					for( Estensione altroTag : U.trovaEstensioni( pfr ) )
						U.metti( scatola, altroTag.nome, altroTag.testo );
				} */
				// Famiglie di origine: genitori e fratelli
				List<Family> listaFamiglie = uno.getParentFamilies(gc);
				for( Family famiglia : listaFamiglie ) {
					for( Person padre : famiglia.getHusbands(gc) )
						createCard(padre, Relation.PARENT, famiglia);
					for( Person madre : famiglia.getWives(gc) )
						createCard(madre, Relation.PARENT, famiglia);
					for( Person fratello : famiglia.getChildren(gc) ) // solo i figli degli stessi due genitori, non i fratellastri
						if( !fratello.equals(uno) )
							createCard(fratello, Relation.SIBLING, famiglia);
				}
				// Fratellastri e sorellastre
				for( Family famiglia : uno.getParentFamilies(gc) ) {
					for( Person padre : famiglia.getHusbands(gc) ) {
						List<Family> famigliePadre = padre.getSpouseFamilies(gc);
						famigliePadre.removeAll(listaFamiglie);
						for( Family fam : famigliePadre )
							for( Person fratellastro : fam.getChildren(gc) )
								createCard(fratellastro, Relation.HALF_SIBLING, fam);
					}
					for( Person madre : famiglia.getWives(gc) ) {
						List<Family> famiglieMadre = madre.getSpouseFamilies(gc);
						famiglieMadre.removeAll(listaFamiglie);
						for( Family fam : famiglieMadre )
							for( Person fratellastro : fam.getChildren(gc) )
								createCard(fratellastro, Relation.HALF_SIBLING, fam);
					}
				}
				// Coniugi e figli
				for( Family family : uno.getSpouseFamilies(gc) ) {
					for( Person marito : family.getHusbands(gc) )
						if( !marito.equals(uno) )
							createCard(marito, Relation.PARTNER, family);
					for( Person moglie : family.getWives(gc) )
						if( !moglie.equals(uno) )
							createCard(moglie, Relation.PARTNER, family);
					for( Person figlio : family.getChildren(gc) ) {
						createCard(figlio, Relation.CHILD, family);
					}
				}
			}
		}
		return vistaFamiglia;
	}

	void createCard(final Person person, Relation relation, Family family) {
		LinearLayout scatola = vistaFamiglia.findViewById(R.id.contenuto_scheda);
		View vistaPersona = U.mettiIndividuo(scatola, person,
				Famiglia.getRole(person, family, relation, false) + Famiglia.writeLineage(person, family));
		vistaPersona.setOnClickListener(v -> {
			getActivity().finish(); // Rimuove l'attività attale dallo stack
			Memoria.replacePrimo(person);
			Intent intento = new Intent(getContext(), Individuo.class);
			intento.putExtra("scheda", 2); // apre la scheda famiglia
			startActivity(intento);
		});
		registerForContextMenu(vistaPersona);
		vistaPersona.setTag(R.id.tag_famiglia, family); // Il principale scopo di questo tag è poter scollegare l'individuo dalla famiglia
		                                               // ma è usato anche qui sotto per spostare i molteplici matrimoni
	}

	private void spostaRiferimentoFamiglia(int direzione) {
		Collections.swap(uno.getSpouseFamilyRefs(), posFam, posFam + direzione);
		U.save(true, uno);
		refresh();
	}

	// Menu contestuale
	private String indiId;
	private Person person;
	private Family family;
	private int posFam; // posizione della famiglia coniugale per chi ne ha più di una
	@Override
	public void onCreateContextMenu( ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info ) {
		indiId = (String)vista.getTag();
		person = gc.getPerson(indiId);
		family = (Family)vista.getTag(R.id.tag_famiglia);
		posFam = -1;
		if( uno.getSpouseFamilyRefs().size() > 1 && !family.getChildren(gc).contains(person) ) { // solo i coniugi, non i figli
			List<SpouseFamilyRef> refi = uno.getSpouseFamilyRefs();
			for( SpouseFamilyRef sfr : refi )
				if( sfr.getRef().equals(family.getId()) )
					posFam = refi.indexOf(sfr);
		}
		// Meglio usare numeri che non confliggano con i menu contestuali delle altre schede individuo
		menu.add(0, 300, 0, R.string.diagram);
		String[] familyLabels = Diagram.getFamilyLabels(getContext(), person, family);
		if( familyLabels[0] != null )
			menu.add(0, 301, 0, familyLabels[0]);
		if( familyLabels[1] != null )
			menu.add(0, 302, 0, familyLabels[1]);
		if( posFam > 0 )
			menu.add(0, 303, 0, R.string.move_before);
		if( posFam >= 0 && posFam < uno.getSpouseFamilyRefs().size() - 1 )
			menu.add(0, 304, 0, R.string.move_after);
		menu.add(0, 305, 0, R.string.modify);
		if( Famiglia.findParentFamilyRef(person, family) != null )
			menu.add(0, 306, 0, "Lineage"); // todo traduci
		menu.add(0, 307, 0, R.string.unlink);
		if( !person.equals(uno) ) // Qui non può eliminare sè stesso
			menu.add(0, 308, 0, R.string.delete);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		int id = item.getItemId();
		if( id == 300 ) { // Diagramma
			U.qualiGenitoriMostrare(getContext(), person, 1);
		} else if( id == 301 ) { // Famiglia come figlio
			U.qualiGenitoriMostrare(getContext(), person, 2);
		} else if( id == 302 ) { // Famiglia come coniuge
			U.qualiConiugiMostrare(getContext(), person, family);
		} else if( id == 303 ) { // Sposta su
			spostaRiferimentoFamiglia(-1);
		} else if( id == 304 ) { // Sposta giù
			spostaRiferimentoFamiglia(1);
		} else if( id == 305 ) { // Modifica
			Intent intento = new Intent(getContext(), EditaIndividuo.class);
			intento.putExtra("idIndividuo", indiId);
			startActivity(intento);
		} else if( id == 306 ) { // Lineage
			Famiglia.chooseLineage(getContext(), person, family);
		} else if( id == 307 ) { // Scollega da questa famiglia
			Famiglia.scollega(indiId, family);
			refresh();
			U.controllaFamiglieVuote(getContext(), this::refresh, false, family);
			U.save(true, family, person);
		} else if( id == 308 ) { // Elimina
			new AlertDialog.Builder(getContext()).setMessage(R.string.really_delete_person)
					.setPositiveButton(R.string.delete, (dialog, i) -> {
						Anagrafe.deletePerson(getContext(), indiId);
						refresh();
						U.controllaFamiglieVuote(getContext(), this::refresh, false, family);
					}).setNeutralButton(R.string.cancel, null).show();
		} else {
			return false;
		}
		return true;
	}

	// Rinfresca il contenuto del frammento Familiari
	public void refresh() {
		FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
		fragmentManager.beginTransaction().detach(this).commit();
		fragmentManager.beginTransaction().attach(this).commit();
		requireActivity().invalidateOptionsMenu();
		// todo aggiorna la data cambiamento nella scheda Fatti
	}
}
