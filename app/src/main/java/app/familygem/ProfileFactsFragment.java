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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.GedcomTag;
import org.folg.gedcom.model.MediaContainer;
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
import app.familygem.util.EventUtilKt;
import app.familygem.util.TreeUtil;

public class ProfileFactsFragment extends Fragment {

    private Person one;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View factsView = inflater.inflate(R.layout.profile_page_fragment, container, false);
        if (gc != null) {
            LinearLayout layout = factsView.findViewById(R.id.profile_page);
            one = gc.getPerson(Global.indi);
            if (one != null) {
                for (Name name : one.getNames()) {
                    placeEvent(layout, writeNameTitle(name), U.firstAndLastName(name, " "), name);
                }
                for (EventFact event : one.getEventsFacts()) {
                    placeEvent(layout, writeEventTitle(event), EventUtilKt.writeContent(event), event);
                }
                for (Extension extension : U.findExtensions(one)) {
                    placeEvent(layout, extension.name, extension.text, extension.gedcomTag);
                }
                U.placeNotes(layout, one, true);
                U.placeSourceCitations(layout, one);
                U.placeChangeDate(layout, one.getChange());
            }
        }
        return factsView;
    }

    /**
     * Finds out if it is a name with name pieces or a suffix in the value.
     */
    boolean isNameComplex(Name name) {
        // Name pieces
        boolean hasPieces = name.getGiven() != null || name.getSurname() != null
                || name.getPrefix() != null || name.getSurnamePrefix() != null || name.getSuffix() != null
                || name.getFone() != null || name.getRomn() != null;
        // Suffix after the surname
        String value = name.getValue();
        boolean hasSuffix = false;
        if (value != null) {
            value = value.trim();
            if (value.indexOf('/') > 0 && value.lastIndexOf('/') < value.length() - 1)
                hasSuffix = true;
        }
        return hasPieces || hasSuffix;
    }

    /**
     * Composes the title of a name, optionally with the type.
     */
    public static String writeNameTitle(Name name) {
        String txt = U.s(R.string.name);
        if (name.getType() != null && !name.getType().isEmpty()) {
            txt += " (" + TypeView.getTranslatedType(name.getType(), TypeView.Combo.NAME) + ")";
        }
        return txt;
    }

    /**
     * Composes the title of an event.
     */
    public static String writeEventTitle(EventFact event) {
        int str = 0;
        switch (event.getTag()) {
            case "SEX":
                str = R.string.sex;
                break;
            case "BIRT":
                str = R.string.birth;
                break;
            case "CHR":
                str = R.string.christening;
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
            U.placeMedia(otherLayout, (MediaContainer)object, false);
            eventView.setOnClickListener(v -> {
                // If it is a complex name, suggests expert mode
                if (!Global.settings.expert && isNameComplex((Name)object)) {
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
                            updateSpouseRoles(one);
                            dialog.dismiss();
                            refresh();
                            TreeUtil.INSTANCE.save(true, one);
                        }).show());
            } else { // All other events
                U.placeMedia(otherLayout, (MediaContainer)object, false);
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

    /**
     * Removes the spouse refs in all spouse families of the person and adds one corresponding to the gender.
     * It is especially useful when exporting GEDCOM to have the HUSB and WIFE tags aligned with the gender.
     */
    static void updateSpouseRoles(Person person) {
        SpouseRef spouseRef = new SpouseRef();
        spouseRef.setRef(person.getId());
        for (Family family : person.getSpouseFamilies(gc)) {
            if (Gender.isFemale(person)) { // Female person will become a wife
                Iterator<SpouseRef> iterator = family.getHusbandRefs().iterator();
                while (iterator.hasNext()) {
                    String husbandRef = iterator.next().getRef();
                    if (husbandRef != null && husbandRef.equals(person.getId())) {
                        iterator.remove();
                        family.addWife(spouseRef);
                    }
                }
            } else { // For all other genders person will become a husband
                Iterator<SpouseRef> iterator = family.getWifeRefs().iterator();
                while (iterator.hasNext()) {
                    String wifeRef = iterator.next().getRef();
                    if (wifeRef != null && wifeRef.equals(person.getId())) {
                        iterator.remove();
                        family.addHusband(spouseRef);
                    }
                }
            }
        }
    }

    // Context menu
    private View pieceView;
    private Object pieceObject;

    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, View view, ContextMenu.ContextMenuInfo info) {
        // 'info' as usual is null
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
        List<Name> names = one.getNames();
        List<EventFact> events = one.getEventsFacts();
        switch (item.getItemId()) {
            case 200: // Copy name
            case 210: // Copy event
            case 220: // Copy extension
                U.copyToClipboard(((TextView)pieceView.findViewById(R.id.evento_titolo)).getText(),
                        ((TextView)pieceView.findViewById(R.id.evento_testo)).getText());
                return true;
            // Name
            case 201: // Move up
                names.add(names.indexOf(pieceObject) - 1, (Name)pieceObject);
                names.remove(names.lastIndexOf(pieceObject));
                break;
            case 202: // Move down
                names.add(names.indexOf(pieceObject) + 2, (Name)pieceObject);
                names.remove(pieceObject);
                break;
            case 203: // Delete
                //if (U.preserva(pieceObject)) return false; TODO: confirm delete
                one.getNames().remove(pieceObject);
                Memory.setInstanceAndAllSubsequentToNull(pieceObject);
                break;
            // Event
            case 211: // Move up
                events.add(events.indexOf(pieceObject) - 1, (EventFact)pieceObject);
                events.remove(events.lastIndexOf(pieceObject));
                break;
            case 212: // Move down
                events.add(events.indexOf(pieceObject) + 2, (EventFact)pieceObject);
                events.remove(pieceObject);
                break;
            case 213: // Delete
                // TODO Confirm delete
                one.getEventsFacts().remove(pieceObject);
                Memory.setInstanceAndAllSubsequentToNull(pieceObject);
                break;
            // Extension
            case 221: // Delete
                U.deleteExtension((GedcomTag)pieceObject, one, pieceView);
                break;
            // Note
            case 225: // Copy text
                U.copyToClipboard(getText(R.string.note), ((TextView)pieceView.findViewById(R.id.note_text)).getText());
                return true;
            case 226: // Unlink
                U.disconnectNote((Note)pieceObject, one, null);
                break;
            case 227: // Delete
                Object[] leaders = U.deleteNote((Note)pieceObject, null);
                TreeUtil.INSTANCE.save(true, leaders);
                refresh();
                return true;
            // Source citation
            case 230: // Copy text
                U.copyToClipboard(getText(R.string.source_citation),
                        ((TextView)pieceView.findViewById(R.id.fonte_testo)).getText() + "\n"
                                + ((TextView)pieceView.findViewById(R.id.citazione_testo)).getText());
                return true;
            case 231: // Delete
                // TODO: Confirm : Do you want to delete this source citation? The source will continue to exist.
                one.getSourceCitations().remove(pieceObject);
                Memory.setInstanceAndAllSubsequentToNull(pieceObject);
                pieceView.setVisibility(View.GONE);
                break;
            default:
                return false;
        }
        TreeUtil.INSTANCE.save(true, one);
        refresh();
        return true;
    }

    // Updates activity content
    void refresh() {
        ((ProfileActivity)requireActivity()).refresh();
    }
}
