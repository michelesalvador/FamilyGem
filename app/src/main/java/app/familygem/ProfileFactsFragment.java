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
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import org.folg.gedcom.model.Address;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.GedcomTag;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;
import org.folg.gedcom.model.SpouseRef;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.familygem.constant.Gender;
import app.familygem.detail.EventActivity;
import app.familygem.detail.ExtensionActivity;
import app.familygem.detail.NameActivity;
import app.familygem.util.TreeUtils;

public class ProfileFactsFragment extends Fragment {

    Person one;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View vistaEventi = inflater.inflate(R.layout.individuo_scheda, container, false);
        if (gc != null) {
            LinearLayout layout = vistaEventi.findViewById(R.id.contenuto_scheda);
            one = gc.getPerson(Global.indi);
            if (one != null) {
                for (Name nome : one.getNames()) {
                    String tit = getString(R.string.name);
                    if (nome.getType() != null && !nome.getType().isEmpty()) {
                        tit += " (" + TypeView.getTranslatedType(nome.getType(), TypeView.Combo.NAME) + ")";
                    }
                    placeEvent(layout, tit, U.firstAndLastName(nome, " "), nome);
                }
                for (EventFact fact : one.getEventsFacts()) {
                    placeEvent(layout, writeEventTitle(fact), writeEventText(fact), fact);
                }
                for (Extension est : U.findExtensions(one)) {
                    placeEvent(layout, est.name, est.text, est.gedcomTag);
                }
                U.placeNotes(layout, one, true);
                U.placeSourceCitations(layout, one);
                U.placeChangeDate(layout, one.getChange());
            }
        }
        return vistaEventi;
    }

    // Scopre se è un nome con name pieces o un suffisso nel value
    boolean nomeComplesso(Name n) {
        // Name pieces
        boolean ricco = n.getGiven() != null || n.getSurname() != null
                || n.getPrefix() != null || n.getSurnamePrefix() != null || n.getSuffix() != null
                || n.getFone() != null || n.getRomn() != null;
        // Qualcosa dopo il cognome
        String nome = n.getValue();
        boolean suffisso = false;
        if (nome != null) {
            nome = nome.trim();
            if (nome.lastIndexOf('/') < nome.length() - 1)
                suffisso = true;
        }
        return ricco || suffisso;
    }

    // Compose the title of an event of the person
    public static String writeEventTitle(EventFact event) {
        int str = 0;
        switch (event.getTag()) {
            case "SEX":
                str = R.string.sex;
                break;
            case "BIRT":
                str = R.string.birth;
                break;
            case "BAPM":
                str = R.string.baptism;
                break;
            case "BURI":
                str = R.string.burial;
                break;
            case "DEAT":
                str = R.string.death;
                break;
            case "EVEN":
                str = R.string.event;
                break;
            case "OCCU":
                str = R.string.occupation;
                break;
            case "RESI":
                str = R.string.residence;
        }
        String txt;
        if (str != 0)
            txt = Global.context.getString(str);
        else
            txt = event.getDisplayType();
        if (event.getType() != null)
            txt += " (" + event.getType() + ")";
        return txt;
    }

    public static String writeEventText(EventFact event) {
        String txt = "";
        if (event.getValue() != null) {
            if (event.getValue().equals("Y") && event.getTag() != null &&
                    (event.getTag().equals("BIRT") || event.getTag().equals("CHR") || event.getTag().equals("DEAT")))
                txt = Global.context.getString(R.string.yes);
            else txt = event.getValue();
            txt += "\n";
        }
        //if( fatto.getType() != null ) txt += fatto.getType() + "\n"; // Included in event title
        if (event.getDate() != null)
            txt += new GedcomDateConverter(event.getDate()).writeDateLong() + "\n";
        if (event.getPlace() != null) txt += event.getPlace() + "\n";
        Address indirizzo = event.getAddress();
        if (indirizzo != null) txt += DetailActivity.writeAddress(indirizzo, true) + "\n";
        if (event.getCause() != null) txt += event.getCause();
        return txt.trim();
    }

    private int chosenSex;

    private void placeEvent(LinearLayout layout, String title, String text, Object object) {
        View eventView = LayoutInflater.from(layout.getContext()).inflate(R.layout.individuo_eventi_pezzo, layout, false);
        layout.addView(eventView);
        ((TextView)eventView.findViewById(R.id.evento_titolo)).setText(title);
        TextView textView = eventView.findViewById(R.id.evento_testo);
        if (text.isEmpty()) textView.setVisibility(View.GONE);
        else textView.setText(text);
        if (Global.settings.expert && object instanceof SourceCitationContainer) {
            List<SourceCitation> sourceCitations = ((SourceCitationContainer)object).getSourceCitations();
            TextView sourceView = eventView.findViewById(R.id.evento_fonti);
            if (!sourceCitations.isEmpty()) {
                sourceView.setText(String.valueOf(sourceCitations.size()));
                sourceView.setVisibility(View.VISIBLE);
            }
        }
        LinearLayout otherLayout = eventView.findViewById(R.id.evento_altro);
        if (object instanceof NoteContainer)
            U.placeNotes(otherLayout, object, false);
        eventView.setTag(R.id.tag_object, object);
        registerForContextMenu(eventView);
        if (object instanceof Name) {
            U.placeMedia(otherLayout, object, false);
            eventView.setOnClickListener(v -> {
                // Se è un nome complesso propone la modalità esperto
                if (!Global.settings.expert && nomeComplesso((Name)object)) {
                    new AlertDialog.Builder(getContext()).setMessage(R.string.complex_tree_advanced_tools)
                            .setPositiveButton(android.R.string.ok, (dialog, i) -> {
                                Global.settings.expert = true;
                                Global.settings.save();
                                Memory.add(object);
                                startActivity(new Intent(getContext(), NameActivity.class));
                            }).setNegativeButton(android.R.string.cancel, (dialog, i) -> {
                                Memory.add(object);
                                startActivity(new Intent(getContext(), NameActivity.class));
                            }).show();
                } else {
                    Memory.add(object);
                    startActivity(new Intent(getContext(), NameActivity.class));
                }
            });
        } else if (object instanceof EventFact) {
            // Sex fact
            if (((EventFact)object).getTag() != null && ((EventFact)object).getTag().equals("SEX")) {
                Map<String, String> sexes = new LinkedHashMap<>();
                sexes.put("M", getString(R.string.male));
                sexes.put("F", getString(R.string.female));
                sexes.put("U", getString(R.string.unknown));
                textView.setText(text);
                chosenSex = 0;
                for (Map.Entry<String, String> sex : sexes.entrySet()) {
                    if (text.equals(sex.getKey())) {
                        textView.setText(sex.getValue());
                        break;
                    }
                    chosenSex++;
                }
                if (chosenSex > 2) chosenSex = -1;
                eventView.setOnClickListener(view -> new AlertDialog.Builder(view.getContext())
                        .setSingleChoiceItems(sexes.values().toArray(new String[0]), chosenSex, (dialog, item) -> {
                            ((EventFact)object).setValue(new ArrayList<>(sexes.keySet()).get(item));
                            updateMaritalRoles(one);
                            dialog.dismiss();
                            refresh();
                            TreeUtils.INSTANCE.save(true, one);
                        }).show());
            } else { // All other events
                U.placeMedia(otherLayout, object, false);
                eventView.setOnClickListener(v -> {
                    Memory.add(object);
                    startActivity(new Intent(getContext(), EventActivity.class));
                });
            }
        } else if (object instanceof GedcomTag) {
            eventView.setOnClickListener(v -> {
                Memory.add(object);
                startActivity(new Intent(getContext(), ExtensionActivity.class));
            });
        }
    }

    // In tutte le famiglie coniugali rimuove gli spouse ref di 'person' e ne aggiunge uno corrispondente al sesso
    // Serve soprattutto in caso di esportazione del Gedcom per avere allineati gli HUSB e WIFE con il sesso
    static void updateMaritalRoles(Person person) {
        SpouseRef spouseRef = new SpouseRef();
        spouseRef.setRef(person.getId());
        boolean removed = false;
        for (Family fam : person.getSpouseFamilies(gc)) {
            if (Gender.isFemale(person)) { // Female 'person' will become a wife
                Iterator<SpouseRef> husbandRefs = fam.getHusbandRefs().iterator();
                while (husbandRefs.hasNext()) {
                    String hr = husbandRefs.next().getRef();
                    if (hr != null && hr.equals(person.getId())) {
                        husbandRefs.remove();
                        removed = true;
                    }
                }
                if (removed) {
                    fam.addWife(spouseRef);
                    removed = false;
                }
            } else { // For all other sexs 'person' will become husband
                Iterator<SpouseRef> wifeRefs = fam.getWifeRefs().iterator();
                while (wifeRefs.hasNext()) {
                    String wr = wifeRefs.next().getRef();
                    if (wr != null && wr.equals(person.getId())) {
                        wifeRefs.remove();
                        removed = true;
                    }
                }
                if (removed) {
                    fam.addHusband(spouseRef);
                    removed = false;
                }
            }
        }
    }

    // Menu contestuale
    View pieceView;
    Object pieceObject;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo info) {
        // menuInfo come al solito è null
        pieceView = view;
        pieceObject = view.getTag(R.id.tag_object);
        if (pieceObject instanceof Name) {
            menu.add(0, 200, 0, R.string.copy);
            if (one.getNames().indexOf(pieceObject) > 0)
                menu.add(0, 201, 0, R.string.move_up);
            if (one.getNames().indexOf(pieceObject) < one.getNames().size() - 1)
                menu.add(0, 202, 0, R.string.move_down);
            menu.add(0, 203, 0, R.string.delete);
        } else if (pieceObject instanceof EventFact) {
            if (view.findViewById(R.id.evento_testo).getVisibility() == View.VISIBLE)
                menu.add(0, 210, 0, R.string.copy);
            if (one.getEventsFacts().indexOf(pieceObject) > 0)
                menu.add(0, 211, 0, R.string.move_up);
            if (one.getEventsFacts().indexOf(pieceObject) < one.getEventsFacts().size() - 1)
                menu.add(0, 212, 0, R.string.move_down);
            menu.add(0, 213, 0, R.string.delete);
        } else if (pieceObject instanceof GedcomTag) {
            menu.add(0, 220, 0, R.string.copy);
            menu.add(0, 221, 0, R.string.delete);
        } else if (pieceObject instanceof Note) {
            if (((TextView)view.findViewById(R.id.note_text)).getText().length() > 0)
                menu.add(0, 225, 0, R.string.copy);
            if (((Note)pieceObject).getId() != null)
                menu.add(0, 226, 0, R.string.unlink);
            menu.add(0, 227, 0, R.string.delete);
        } else if (pieceObject instanceof SourceCitation) {
            menu.add(0, 230, 0, R.string.copy);
            menu.add(0, 231, 0, R.string.delete);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        List<Name> nomi = one.getNames();
        List<EventFact> fatti = one.getEventsFacts();
        switch (item.getItemId()) {
            // Nome
            case 200: // Copia nome
            case 210: // Copia evento
            case 220: // Copia estensione
                U.copyToClipboard(((TextView)pieceView.findViewById(R.id.evento_titolo)).getText(),
                        ((TextView)pieceView.findViewById(R.id.evento_testo)).getText());
                return true;
            case 201: // Sposta su
                nomi.add(nomi.indexOf(pieceObject) - 1, (Name)pieceObject);
                nomi.remove(nomi.lastIndexOf(pieceObject));
                break;
            case 202: // Sposta giù
                nomi.add(nomi.indexOf(pieceObject) + 2, (Name)pieceObject);
                nomi.remove(nomi.indexOf(pieceObject));
                break;
            case 203: // Elimina
                if (U.preserva(pieceObject)) return false;
                one.getNames().remove(pieceObject);
                Memory.setInstanceAndAllSubsequentToNull(pieceObject);
                pieceView.setVisibility(View.GONE);
                break;
            // Evento generico
            case 211: // Sposta su
                fatti.add(fatti.indexOf(pieceObject) - 1, (EventFact)pieceObject);
                fatti.remove(fatti.lastIndexOf(pieceObject));
                break;
            case 212: // Sposta giu
                fatti.add(fatti.indexOf(pieceObject) + 2, (EventFact)pieceObject);
                fatti.remove(fatti.indexOf(pieceObject));
                break;
            case 213:
                // todo Conferma elimina
                one.getEventsFacts().remove(pieceObject);
                Memory.setInstanceAndAllSubsequentToNull(pieceObject);
                pieceView.setVisibility(View.GONE);
                break;
            // Estensione
            case 221: // Elimina
                U.deleteExtension((GedcomTag)pieceObject, one, pieceView);
                break;
            // Nota
            case 225: // Copia
                U.copyToClipboard(getText(R.string.note), ((TextView)pieceView.findViewById(R.id.note_text)).getText());
                return true;
            case 226: // Scollega
                U.disconnectNote((Note)pieceObject, one, pieceView);
                break;
            case 227:
                Object[] capi = U.deleteNote((Note)pieceObject, pieceView);
                TreeUtils.INSTANCE.save(true, capi);
                refresh();
                return true;
            // Citazione fonte
            case 230: // Copia
                U.copyToClipboard(getText(R.string.source_citation),
                        ((TextView)pieceView.findViewById(R.id.fonte_testo)).getText() + "\n"
                                + ((TextView)pieceView.findViewById(R.id.citazione_testo)).getText());
                return true;
            case 231: // Elimina
                // todo conferma : Vuoi eliminare questa citazione della fonte? La fonte continuerà ad esistere.
                one.getSourceCitations().remove(pieceObject);
                Memory.setInstanceAndAllSubsequentToNull(pieceObject);
                pieceView.setVisibility(View.GONE);
                break;
            default:
                return false;
        }
        refresh();
        TreeUtils.INSTANCE.save(true, one);
        return true;
    }

    // Update content
    void refresh() {
        ((ProfileActivity)requireActivity()).refresh();
    }
}
