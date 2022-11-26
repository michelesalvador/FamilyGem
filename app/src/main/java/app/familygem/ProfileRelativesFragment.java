package app.familygem;

import static app.familygem.Global.gc;

import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SpouseFamilyRef;

import java.util.Collections;
import java.util.List;

import app.familygem.constant.Relation;
import app.familygem.detail.FamilyActivity;
import app.familygem.list.PersonsFragment;

public class ProfileRelativesFragment extends Fragment {

    private View tabView;
    Person one;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        tabView = inflater.inflate(R.layout.individuo_scheda, container, false);
        if (gc != null) {
            one = gc.getPerson(Global.indi);
            if (one != null) {
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
                List<Family> listaFamiglie = one.getParentFamilies(gc);
                for (Family famiglia : listaFamiglie) {
                    for (Person padre : famiglia.getHusbands(gc))
                        createCard(padre, Relation.PARENT, famiglia);
                    for (Person madre : famiglia.getWives(gc))
                        createCard(madre, Relation.PARENT, famiglia);
                    for (Person fratello : famiglia.getChildren(gc)) // solo i figli degli stessi due genitori, non i fratellastri
                        if (!fratello.equals(one))
                            createCard(fratello, Relation.SIBLING, famiglia);
                }
                // Fratellastri e sorellastre
                for (Family famiglia : one.getParentFamilies(gc)) {
                    for (Person padre : famiglia.getHusbands(gc)) {
                        List<Family> famigliePadre = padre.getSpouseFamilies(gc);
                        famigliePadre.removeAll(listaFamiglie);
                        for (Family fam : famigliePadre)
                            for (Person fratellastro : fam.getChildren(gc))
                                createCard(fratellastro, Relation.HALF_SIBLING, fam);
                    }
                    for (Person madre : famiglia.getWives(gc)) {
                        List<Family> famiglieMadre = madre.getSpouseFamilies(gc);
                        famiglieMadre.removeAll(listaFamiglie);
                        for (Family fam : famiglieMadre)
                            for (Person fratellastro : fam.getChildren(gc))
                                createCard(fratellastro, Relation.HALF_SIBLING, fam);
                    }
                }
                // Coniugi e figli
                for (Family family : one.getSpouseFamilies(gc)) {
                    for (Person marito : family.getHusbands(gc))
                        if (!marito.equals(one))
                            createCard(marito, Relation.PARTNER, family);
                    for (Person moglie : family.getWives(gc))
                        if (!moglie.equals(one))
                            createCard(moglie, Relation.PARTNER, family);
                    for (Person figlio : family.getChildren(gc)) {
                        createCard(figlio, Relation.CHILD, family);
                    }
                }
            }
        }
        return tabView;
    }

    void createCard(final Person person, Relation relation, Family family) {
        LinearLayout scatola = tabView.findViewById(R.id.contenuto_scheda);
        View vistaPersona = U.placeIndividual(scatola, person,
                FamilyActivity.getRole(person, family, relation, false) + FamilyActivity.writeLineage(person, family));
        vistaPersona.setOnClickListener(v -> {
            getActivity().finish(); // Rimuove l'attività attale dallo stack
            Memory.replaceFirst(person);
            Intent intento = new Intent(getContext(), ProfileActivity.class);
            intento.putExtra("scheda", 2); // apre la scheda famiglia
            startActivity(intento);
        });
        registerForContextMenu(vistaPersona);
        vistaPersona.setTag(R.id.tag_famiglia, family); // Il principale scopo di questo tag è poter scollegare l'individuo dalla famiglia
        // ma è usato anche qui sotto per spostare i molteplici matrimoni
    }

    private void moveFamilyRef(int direction) {
        Collections.swap(one.getSpouseFamilyRefs(), posFam, posFam + direction);
        U.save(true, one);
        refresh();
    }

    // Menu contestuale
    private String indiId;
    private Person person;
    private Family family;
    private int posFam; // posizione della famiglia coniugale per chi ne ha più di una

    @Override
    public void onCreateContextMenu(ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info) {
        indiId = (String)vista.getTag();
        person = gc.getPerson(indiId);
        family = (Family)vista.getTag(R.id.tag_famiglia);
        posFam = -1;
        if (one.getSpouseFamilyRefs().size() > 1 && !family.getChildren(gc).contains(person)) { // solo i coniugi, non i figli
            List<SpouseFamilyRef> refi = one.getSpouseFamilyRefs();
            for (SpouseFamilyRef sfr : refi)
                if (sfr.getRef().equals(family.getId()))
                    posFam = refi.indexOf(sfr);
        }
        // Meglio usare numeri che non confliggano con i menu contestuali delle altre schede individuo
        menu.add(0, 300, 0, R.string.diagram);
        String[] familyLabels = DiagramFragment.getFamilyLabels(getContext(), person, family);
        if (familyLabels[0] != null)
            menu.add(0, 301, 0, familyLabels[0]);
        if (familyLabels[1] != null)
            menu.add(0, 302, 0, familyLabels[1]);
        if (posFam > 0)
            menu.add(0, 303, 0, R.string.move_before);
        if (posFam >= 0 && posFam < one.getSpouseFamilyRefs().size() - 1)
            menu.add(0, 304, 0, R.string.move_after);
        menu.add(0, 305, 0, R.string.modify);
        if (FamilyActivity.findParentFamilyRef(person, family) != null)
            menu.add(0, 306, 0, R.string.lineage);
        menu.add(0, 307, 0, R.string.unlink);
        if (!person.equals(one)) // Qui non può eliminare sè stesso
            menu.add(0, 308, 0, R.string.delete);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == 300) { // Diagramma
            U.askWhichParentsToShow(getContext(), person, 1);
        } else if (id == 301) { // Famiglia come figlio
            U.askWhichParentsToShow(getContext(), person, 2);
        } else if (id == 302) { // Famiglia come coniuge
            U.askWhichSpouceToShow(getContext(), person, family);
        } else if (id == 303) { // Sposta su
            moveFamilyRef(-1);
        } else if (id == 304) { // Sposta giù
            moveFamilyRef(1);
        } else if (id == 305) { // Modifica
            Intent intent = new Intent(getContext(), PersonEditorActivity.class);
            intent.putExtra("idIndividuo", indiId);
            startActivity(intent);
        } else if (id == 306) { // Lineage
            FamilyActivity.chooseLineage(getContext(), person, family);
        } else if (id == 307) { // Scollega da questa famiglia
            FamilyActivity.disconnect(indiId, family);
            refresh();
            U.controllaFamiglieVuote(getContext(), this::refresh, false, family);
            U.save(true, family, person);
        } else if (id == 308) { // Elimina
            new AlertDialog.Builder(getContext()).setMessage(R.string.really_delete_person)
                    .setPositiveButton(R.string.delete, (dialog, i) -> {
                        PersonsFragment.deletePerson(getContext(), indiId);
                        refresh();
                        U.controllaFamiglieVuote(getContext(), this::refresh, false, family);
                    }).setNeutralButton(R.string.cancel, null).show();
        } else {
            return false;
        }
        return true;
    }

    // Refresh the content
    public void refresh() {
        ((ProfileActivity)requireActivity()).refresh();
    }
}
