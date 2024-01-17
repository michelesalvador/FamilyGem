package app.familygem;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonPrimitive;

import org.folg.gedcom.model.Change;
import org.folg.gedcom.model.ChildRef;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.ExtensionContainer;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.GedcomTag;
import org.folg.gedcom.model.Header;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.NoteRef;
import org.folg.gedcom.model.ParentFamilyRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.PersonFamilyCommonContainer;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.RepositoryRef;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;
import org.folg.gedcom.model.SpouseFamilyRef;
import org.folg.gedcom.model.SpouseRef;
import org.folg.gedcom.model.Submitter;
import org.folg.gedcom.parser.ModelParser;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.joda.time.Months;
import org.joda.time.Years;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.familygem.constant.Choice;
import app.familygem.constant.Extra;
import app.familygem.constant.Format;
import app.familygem.constant.Gender;
import app.familygem.constant.Json;
import app.familygem.constant.Relation;
import app.familygem.detail.ChangeActivity;
import app.familygem.detail.FamilyActivity;
import app.familygem.detail.MediaActivity;
import app.familygem.detail.NoteActivity;
import app.familygem.detail.RepositoryRefActivity;
import app.familygem.detail.SourceActivity;
import app.familygem.detail.SourceCitationActivity;
import app.familygem.detail.SubmitterActivity;
import app.familygem.list.FamiliesFragment;
import app.familygem.list.MediaAdapter;
import app.familygem.list.PersonsFragment;
import app.familygem.list.SourcesFragment;
import app.familygem.list.SubmittersFragment;
import app.familygem.util.ChangeUtil;
import app.familygem.util.FileUtil;
import app.familygem.util.MediaUtil;
import app.familygem.util.TreeUtils;
import app.familygem.visitor.FindStack;
import app.familygem.visitor.ListOfSourceCitations;
import app.familygem.visitor.MediaContainerList;
import app.familygem.visitor.MediaContainers;
import app.familygem.visitor.NoteContainers;
import app.familygem.visitor.NoteReferences;

/**
 * Static methods used all across the app.
 */
public class U {

    public static String s(int id) {
        return Global.context.getString(id);
    }

    /**
     * @return The ID of the main person of the tree
     */
    public static String getRootId(Gedcom gedcom, Settings.Tree tree) {
        if (tree.root != null) {
            Person root = gedcom.getPerson(tree.root);
            if (root != null)
                return root.getId();
        }
        return findRootId(gedcom);
    }

    /**
     * @return The ID of initial person of a GEDCOM or null
     */
    public static String findRootId(Gedcom gedcom) {
        // Family Historian root
        if (gedcom.getHeader() != null)
            if (valoreTag(gedcom.getHeader().getExtensions(), "_ROOT") != null)
                return valoreTag(gedcom.getHeader().getExtensions(), "_ROOT");
        if (!gedcom.getPeople().isEmpty()) {
            // Lower numeric ID
            String minId = null;
            int minNum = Integer.MAX_VALUE;
            for (Person person : gedcom.getPeople()) {
                int num = extractNum(person.getId());
                if (num < minNum) {
                    minNum = num;
                    minId = person.getId();
                }
            }
            if (minNum < Integer.MAX_VALUE) return minId;
            // ID of the first person
            return gedcom.getPeople().get(0).getId();
        }
        return null;
    }

    /**
     * Returns the first available full name of a person.
     */
    public static String properName(Person person) {
        return properName(person, false);
    }

    static String properName(Person person, boolean multiLines) {
        if (person != null && !person.getNames().isEmpty())
            return firstAndLastName(person.getNames().get(0), multiLines ? "\n" : " ");
        return "[" + s(R.string.no_name) + "]";
    }

    /**
     * The given name of a person or a name placeholder.
     */
    static String givenName(Person person) {
        if (person.getNames().isEmpty()) {
            return "[" + s(R.string.no_name) + "]";
        } else {
            String given = "";
            Name name = person.getNames().get(0);
            if (name.getValue() != null) {
                String value = name.getValue().trim();
                if (value.indexOf('/') == 0 && value.lastIndexOf('/') == 1 && value.length() > 2) // Suffix only
                    given = value.substring(2);
                else if (value.indexOf('/') == 0 && value.lastIndexOf('/') > 1) // Surname only
                    given = value.substring(1, value.lastIndexOf('/'));
                else if (value.indexOf('/') > 0) // Name and surname
                    given = value.substring(0, value.indexOf('/'));
                else if (!value.isEmpty()) // Name only
                    given = value;
            } else if (name.getGiven() != null) {
                given = name.getGiven();
            } else if (name.getSurname() != null) {
                given = name.getSurname();
            }
            given = given.trim();
            return given.isEmpty() ? "[" + s(R.string.empty_name) + "]" : given;
        }
    }

    // riceve una Person e restituisce il titolo nobiliare
    public static String titolo(Person p) {
        // GEDCOM standard INDI.TITL
        for (EventFact ef : p.getEventsFacts())
            if (ef.getTag() != null && ef.getTag().equals("TITL") && ef.getValue() != null)
                return ef.getValue();
        // Così invece prende INDI.NAME._TYPE.TITL, vecchio metodo di org.folg.gedcom
        for (Name n : p.getNames())
            if (n.getType() != null && n.getType().equals("TITL") && n.getValue() != null)
                return n.getValue();
        return "";
    }

    /**
     * Returns the full name completed of prefix, nickname and suffix.
     *
     * @param divider Can be a space " " or a new line "\n"
     */
    public static String firstAndLastName(Name name, String divider) { // TODO writeFullName()
        String full = "";
        // Full name from the Value
        if (name.getValue() != null) {
            String value = name.getValue().trim();
            int slashPos = value.indexOf('/');
            int lastSlashPos = value.lastIndexOf('/');
            if (slashPos > -1) // If there is a surname between two "/"
                full = value.substring(0, slashPos).trim(); // Given name
            else // Or it's only a given name without surname
                full = value;
            if (name.getNickname() != null)
                full += divider + "\"" + name.getNickname() + "\"";
            if (slashPos < lastSlashPos)
                full += divider + value.substring(slashPos + 1, lastSlashPos).trim(); // Surname
            if (lastSlashPos > -1 && value.length() - 1 > lastSlashPos)
                full += " " + value.substring(lastSlashPos + 1).trim(); // After the surname
        } else { // Full name from Name pieces
            if (name.getPrefix() != null)
                full = name.getPrefix();
            if (name.getGiven() != null)
                full += " " + name.getGiven();
            if (name.getNickname() != null)
                full += divider + "\"" + name.getNickname() + "\"";
            if (name.getSurname() != null)
                full += divider + name.getSurname();
            if (name.getSuffix() != null)
                full += " " + name.getSuffix();
        }
        full = full.trim();
        return full.isEmpty() ? "[" + s(R.string.empty_name) + "]" : full;
    }

    /**
     * Returns the surname of a person, possibly lowercase for comparaison. Can return null.
     */
    public static String surname(Person person) {
        return surname(person, false);
    }

    public static String surname(Person person, boolean lowerCase) {
        String surname = null;
        if (!person.getNames().isEmpty()) {
            Name name = person.getNames().get(0);
            String value = name.getValue();
            if (value != null && value.lastIndexOf('/') - value.indexOf('/') > 1) //value.indexOf('/') < value.lastIndexOf('/')
                surname = value.substring(value.indexOf('/') + 1, value.lastIndexOf('/')).trim();
            else if (name.getSurname() != null)
                surname = name.getSurname().trim();
        }
        if (surname != null) {
            if (surname.isEmpty())
                return null;
            else if (lowerCase)
                surname = surname.toLowerCase();
        }
        return surname;
    }

    // Riceve una person e trova se è morto o seppellito
    public static boolean isDead(Person person) {
        for (EventFact eventFact : person.getEventsFacts()) {
            if (eventFact.getTag().equals("DEAT") || eventFact.getTag().equals("BURI"))
                return true;
        }
        return false;
    }

    /**
     * Checks whether a family has a marriage event of type 'marriage'.
     */
    public static boolean areMarried(Family family) {
        if (family != null) {
            for (EventFact eventFact : family.getEventsFacts()) {
                String tag = eventFact.getTag();
                if (tag.equals("MARR")) {
                    String type = eventFact.getType();
                    if (type == null || type.isEmpty() || type.equals("marriage")
                            || type.equals("civil") || type.equals("religious") || type.equals("common law"))
                        return true;
                } else if (tag.equals("MARB") || tag.equals("MARC") || tag.equals("MARL") || tag.equals("MARS"))
                    return true;
            }
        }
        return false;
    }

    /**
     * Writes the basic dates of a person's life with the age.
     *
     * @param vertical Dates and age could result on multiple lines
     * @return A string with date of birth an death
     */
    static String twoDates(Person person, boolean vertical) {
        StringBuilder builder = new StringBuilder();
        String endDateStr = "";
        GedcomDateConverter start = null, end = null;
        boolean ageBelow = false;
        List<EventFact> facts = person.getEventsFacts();
        // Birth date
        for (EventFact fact : facts) {
            if (fact.getTag() != null && fact.getTag().equals("BIRT") && fact.getDate() != null) {
                start = new GedcomDateConverter(fact.getDate());
                builder.append("★ ").append(start.writeDate(false));
                break;
            }
        }
        // Christening or baptism
        if (builder.length() == 0) {
            for (EventFact fact : facts) {
                if (fact.getTag() != null && (fact.getTag().equals("CHR") || fact.getTag().equals("BAPM")) && fact.getDate() != null) {
                    builder.append("≈ ").append(new GedcomDateConverter(fact.getDate()).writeDate(false));
                    break;
                }
            }
        }
        // Death date
        for (EventFact fact : facts) {
            if (fact.getTag() != null && fact.getTag().equals("DEAT") && fact.getDate() != null) {
                end = new GedcomDateConverter(fact.getDate());
                endDateStr = end.writeDate(false);
                if (builder.length() != 0) {
                    if (vertical && (builder.length() > 7 || endDateStr.length() > 7)) {
                        builder.append("\n");
                        ageBelow = true;
                    } else {
                        builder.append("  ");
                    }
                }
                builder.append("✛ ").append(endDateStr);
                break;
            }
        }
        // Otherwise find the first available date
        if (builder.length() == 0) {
            for (EventFact fact : facts) {
                if (fact.getDate() != null) {
                    return new GedcomDateConverter(fact.getDate()).writeDate(false);
                }
            }
        }
        // Add the age between parentheses
        if (start != null && start.isSingleKind() && !start.firstDate.isFormat(Format.D_M) && !start.firstDate.isFormat(Format.OTHER)) {
            Settings.TreeSettings treeSettings = Global.settings.getCurrentTree().settings;
            LocalDate startDate = new LocalDate(start.firstDate.date); // Converted to joda time
            // If the person is still alive the end is a fixed date or now
            LocalDate now;
            if (treeSettings.customDate) now = new LocalDate(treeSettings.fixedDate);
            else now = LocalDate.now();
            if (end == null && (startDate.isBefore(now) || startDate.isEqual(now))
                    && Years.yearsBetween(startDate, now).getYears() <= treeSettings.lifeSpan
                    && !isDead(person)) {
                end = new GedcomDateConverter(now.toDate());
                endDateStr = end.writeDate(false);
            }
            if (end != null && end.isSingleKind() && !end.firstDate.isFormat(Format.D_M) && !endDateStr.isEmpty()) { // Plausible dates
                LocalDate endDate = new LocalDate(end.firstDate.date);
                if (startDate.isBefore(endDate) || startDate.isEqual(endDate)) {
                    String units = "";
                    int age = Years.yearsBetween(startDate, endDate).getYears();
                    if (age < 2) {
                        // Without day and/or month the years start at 1 January
                        age = Months.monthsBetween(startDate, endDate).getMonths();
                        units = " " + Global.context.getText(R.string.months);
                        if (age < 2) {
                            age = Days.daysBetween(startDate, endDate).getDays();
                            units = " " + Global.context.getText(R.string.days);
                        }
                    }
                    if (ageBelow) builder.append("\n");
                    else builder.append(" ");
                    builder.append("(").append(age).append(units).append(")");
                }
            }
        }
        return builder.toString();
    }

    /**
     * Writes the two main places of a person (initial – final) or null.
     */
    static String twoPlaces(Person person) {
        List<EventFact> facts = person.getEventsFacts();
        // One single event
        if (facts.size() == 1) {
            String place = facts.get(0).getPlace();
            if (place != null)
                return stripCommas(place);
        } // Sex and another event
        else if (facts.size() == 2 && ("SEX".equals(facts.get(0).getTag()) || "SEX".equals(facts.get(1).getTag()))) {
            String place;
            if ("SEX".equals(facts.get(0).getTag()))
                place = facts.get(1).getPlace();
            else
                place = facts.get(0).getPlace();
            if (place != null)
                return stripCommas(place);
        } // Multiple events
        else if (facts.size() >= 2) {
            String[] places = new String[7];
            for (EventFact ef : facts) {
                String place = ef.getPlace();
                if (place != null) {
                    switch (ef.getTag()) {
                        case "BIRT":
                            places[0] = place;
                            break;
                        case "BAPM":
                            places[1] = place;
                            break;
                        case "DEAT":
                            places[4] = place;
                            break;
                        case "CREM":
                            places[5] = place;
                            break;
                        case "BURI":
                            places[6] = place;
                            break;
                        default:
                            if (places[2] == null) // First of other events
                                places[2] = place;
                            if (!place.equals(places[2]))
                                places[3] = place; // Last of other events
                    }
                }
            }
            String text = null;
            int i;
            // Write initial place
            for (i = 0; i < places.length; i++) {
                String place = places[i];
                if (place != null) {
                    text = stripCommas(place);
                    break;
                }
            }
            // Priority to death event as final place
            if (text != null && i < 4 && places[4] != null) {
                String place = stripCommas(places[4]);
                if (!place.equals(text))
                    text += " – " + place;
            } else {
                for (int j = places.length - 1; j > i; j--) {
                    String place = places[j];
                    if (place != null) {
                        place = stripCommas(place);
                        if (!place.equals(text)) {
                            text += " – " + place;
                            break;
                        }
                    }
                }
            }
            return text;
        }
        return null;
    }

    /**
     * Receives a GEDCOM-style (comma separated) place name and returns the first locality.
     */
    private static String stripCommas(String place) {
        // Salta le virgole iniziali per luoghi come ",,,England"
        int start = 0;
        for (char c : place.toCharArray()) {
            if (c != ',' && c != ' ')
                break;
            start++;
        }
        place = place.substring(start);
        if (place.indexOf(",") > 0)
            place = place.substring(0, place.indexOf(","));
        return place;
    }

    /**
     * Extracts only digits from a string that can also contain letters.
     */
    public static int extractNum(String id) {
        //return Integer.parseInt(id.replaceAll("\\D+", "")); // Too slow
        int num = 0;
        int x = 1;
        for (int i = id.length() - 1; i >= 0; --i) {
            int c = id.charAt(i);
            if (c > 47 && c < 58) {
                num += (c - 48) * x;
                x *= 10; // To convert positional notation into a base-10 representation
            }
        }
        return num;
    }

    // Genera il nuovo ID seguente a quelli già esistenti
    static int max;

    public static String newID(Gedcom gc, Class classe) { // TODO: newId()
        max = 0;
        String pre = "";
        if (classe == Note.class) {
            pre = "T";
            for (Note n : gc.getNotes())
                calcolaMax(n);
        } else if (classe == Submitter.class) {
            pre = "U";
            for (Submitter a : gc.getSubmitters())
                calcolaMax(a);
        } else if (classe == Repository.class) {
            pre = "R";
            for (Repository r : gc.getRepositories())
                calcolaMax(r);
        } else if (classe == Media.class) {
            pre = "M";
            for (Media m : gc.getMedia())
                calcolaMax(m);
        } else if (classe == Source.class) {
            pre = "S";
            for (Source f : gc.getSources())
                calcolaMax(f);
        } else if (classe == Person.class) {
            pre = "I";
            for (Person p : gc.getPeople())
                calcolaMax(p);
        } else if (classe == Family.class) {
            pre = "F";
            for (Family f : gc.getFamilies())
                calcolaMax(f);
        }
        return pre + (max + 1);
    }

    private static void calcolaMax(Object object) {
        try {
            String idStringa = (String)object.getClass().getMethod("getId").invoke(object);
            int num = extractNum(idStringa);
            if (num > max) max = num;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Copies text to clipboard.
     */
    static void copyToClipboard(CharSequence label, CharSequence text) {
        ClipboardManager clipboard = (ClipboardManager)Global.context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        if (clipboard != null) clipboard.setPrimaryClip(clip);
    }

    // Restituisce la lista di estensioni
    @SuppressWarnings("unchecked")
    public static List<Extension> findExtensions(ExtensionContainer contenitore) {
        if (contenitore.getExtension(ModelParser.MORE_TAGS_EXTENSION_KEY) != null) {
            List<Extension> lista = new ArrayList<>();
            for (GedcomTag est : (List<GedcomTag>)contenitore.getExtension(ModelParser.MORE_TAGS_EXTENSION_KEY)) {
                String testo = traverseExtension(est, 0);
                if (testo.endsWith("\n"))
                    testo = testo.substring(0, testo.length() - 1);
                lista.add(new Extension(est.getTag(), testo, est));
            }
            return lista;
        }
        return Collections.emptyList();
    }

    /**
     * Composes a string with the recursive content of an extension.
     */
    public static String traverseExtension(GedcomTag pacco, int level) {
        StringBuilder builder = new StringBuilder();
        if (level > 0)
            builder.append(pacco.getTag()).append(" ");
        if (pacco.getValue() != null)
            builder.append(pacco.getValue()).append("\n");
        else if (pacco.getId() != null)
            builder.append(pacco.getId()).append("\n");
        else if (pacco.getRef() != null)
            builder.append(pacco.getRef()).append("\n");
        for (GedcomTag unPezzo : pacco.getChildren())
            builder.append(traverseExtension(unPezzo, ++level));
        return builder.toString();
    }

    public static void deleteExtension(GedcomTag extension, Object container, View view) {
        if (container instanceof ExtensionContainer) { // ProfileFactsFragment
            ExtensionContainer exc = (ExtensionContainer)container;
            @SuppressWarnings("unchecked")
            List<GedcomTag> lista = (List<GedcomTag>)exc.getExtension(ModelParser.MORE_TAGS_EXTENSION_KEY);
            lista.remove(extension);
            if (lista.isEmpty())
                exc.getExtensions().remove(ModelParser.MORE_TAGS_EXTENSION_KEY);
            if (exc.getExtensions().isEmpty())
                exc.setExtensions(null);
        } else if (container instanceof GedcomTag) { // DetailActivity
            GedcomTag gt = (GedcomTag)container;
            gt.getChildren().remove(extension);
            if (gt.getChildren().isEmpty())
                gt.setChildren(null);
        }
        Memory.setInstanceAndAllSubsequentToNull(extension);
        if (view != null)
            view.setVisibility(View.GONE);
    }

    // Restituisce il valore di un determinato tag in una estensione (GedcomTag).
    @SuppressWarnings("unchecked")
    static String valoreTag(Map<String, Object> mappaEstensioni, String nomeTag) {
        for (Map.Entry<String, Object> estensione : mappaEstensioni.entrySet()) {
            List<GedcomTag> listaTag = (ArrayList<GedcomTag>)estensione.getValue();
            for (GedcomTag unPezzo : listaTag) {
                //l( unPezzo.getTag() +" "+ unPezzo.getValue() );
                if (unPezzo.getTag().equals(nomeTag)) {
                    if (unPezzo.getId() != null)
                        return unPezzo.getId();
                    else if (unPezzo.getRef() != null)
                        return unPezzo.getRef();
                    else
                        return unPezzo.getValue();
                }
            }
        }
        return null;
    }

    // Methods to create list items

    /**
     * Add a generic not editable title-text item to a Layout.
     */
    public static void place(LinearLayout layout, String title, String text) {
        View pieceView = LayoutInflater.from(layout.getContext()).inflate(R.layout.event_view, layout, false);
        layout.addView(pieceView);
        ((TextView)pieceView.findViewById(R.id.event_title)).setText(title);
        TextView textView = pieceView.findViewById(R.id.event_text);
        if (text == null) textView.setVisibility(View.GONE);
        else textView.setText(text);
    }

    // Compone il testo coi dettagli di un individuo e lo mette nella vista testo
    // inoltre restituisce lo stesso testo per Confrontatore
    public static String details(Person person, TextView detailsView) {
        String dates = twoDates(person, false);
        String places = twoPlaces(person);
        if (dates.isEmpty() && places == null && detailsView != null) {
            detailsView.setVisibility(View.GONE);
        } else {
            if (!dates.isEmpty() && places != null && (dates.length() >= 10 || places.length() >= 20))
                dates += "\n" + places;
            else if (places != null)
                dates += "   " + places;
            if (detailsView != null) {
                detailsView.setText(dates.trim());
                detailsView.setVisibility(View.VISIBLE);
            }
        }
        return dates.trim();
    }

    /**
     * Inflates in layout a person card with their main details.
     */
    public static View placePerson(LinearLayout layout, Person person, String role) {
        View personView = LayoutInflater.from(layout.getContext()).inflate(R.layout.piece_person, layout, false);
        layout.addView(personView);
        TextView roleView = personView.findViewById(R.id.person_info);
        if (role == null) roleView.setVisibility(View.GONE);
        else roleView.setText(role);
        TextView nameView = personView.findViewById(R.id.person_name);
        String name = properName(person);
        if (name.isEmpty() && role != null) nameView.setVisibility(View.GONE);
        else nameView.setText(name);
        TextView titleView = personView.findViewById(R.id.person_title);
        String title = titolo(person);
        if (title.isEmpty()) titleView.setVisibility(View.GONE);
        else titleView.setText(title);
        details(person, personView.findViewById(R.id.person_details));
        FileUtil.INSTANCE.selectMainImage(person, personView.findViewById(R.id.person_image));
        if (!isDead(person))
            personView.findViewById(R.id.person_mourning).setVisibility(View.GONE);
        if (Gender.isMale(person))
            personView.findViewById(R.id.person_border).setBackgroundResource(R.drawable.casella_bordo_maschio);
        else if (Gender.isFemale(person))
            personView.findViewById(R.id.person_border).setBackgroundResource(R.drawable.casella_bordo_femmina);
        personView.setTag(person.getId());
        return personView;
    }

    // Place all the notes of an object
    public static void placeNotes(LinearLayout layout, Object container, boolean detailed) {
        for (final Note nota : ((NoteContainer)container).getAllNotes(Global.gc)) {
            placeNote(layout, nota, detailed);
        }
    }

    // Place a single note on a layout, with details or not
    static void placeNote(final LinearLayout layout, final Note note, boolean detailed) {
        final Context context = layout.getContext();
        View noteView = LayoutInflater.from(context).inflate(R.layout.piece_note, layout, false);
        layout.addView(noteView);
        TextView textView = noteView.findViewById(R.id.note_text);
        textView.setText(note.getValue());
        int sourceCiteNum = note.getSourceCitations().size();
        TextView sourceCiteView = noteView.findViewById(R.id.note_sources);
        if (sourceCiteNum > 0 && detailed) sourceCiteView.setText(String.valueOf(sourceCiteNum));
        else sourceCiteView.setVisibility(View.GONE);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        if (detailed) {
            textView.setMaxLines(10);
            noteView.setTag(R.id.tag_object, note);
            if (context instanceof ProfileActivity) { // ProfileFactsFragment
                ((ProfileActivity)context).getPageFragment(1).registerForContextMenu(noteView);
            } else if (layout.getId() != R.id.dispensa_scatola) // nelle AppCompatActivity tranne che nella dispensa
                ((AppCompatActivity)context).registerForContextMenu(noteView);
            noteView.setOnClickListener(v -> {
                if (note.getId() != null)
                    Memory.setLeader(note);
                else
                    Memory.add(note);
                context.startActivity(new Intent(context, NoteActivity.class));
            });
        } else {
            textView.setMaxLines(3);
        }
    }

    static void disconnectNote(Note note, Object container, View view) {
        List<NoteRef> list = ((NoteContainer)container).getNoteRefs();
        for (NoteRef ref : list)
            if (ref.getNote(Global.gc).equals(note)) {
                list.remove(ref);
                break;
            }
        ((NoteContainer)container).setNoteRefs(list);
        if (view != null)
            view.setVisibility(View.GONE);
    }

    // Elimina una Nota inlinea o condivisa
    // Restituisce un array dei capostipiti modificati
    public static Object[] deleteNote(Note note, View view) {
        Set<Object> capi;
        if (note.getId() != null) { // OBJECT note
            // Prima rimuove i ref alla nota con un bel Visitor
            NoteReferences eliminatoreNote = new NoteReferences(Global.gc, note.getId(), true);
            Global.gc.accept(eliminatoreNote);
            Global.gc.getNotes().remove(note); // ok la rimuove se è un'object note
            capi = eliminatoreNote.leaders;
            if (Global.gc.getNotes().isEmpty())
                Global.gc.setNotes(null);
        } else { // LOCAL note
            new FindStack(Global.gc, note);
            NoteContainer nc = (NoteContainer)Memory.getSecondToLastObject();
            nc.getNotes().remove(note); // rimuove solo se è una nota locale, non se object note
            if (nc.getNotes().isEmpty())
                nc.setNotes(null);
            capi = new HashSet<>();
            capi.add(Memory.getLeaderObject());
            Memory.stepBack();
        }
        Memory.setInstanceAndAllSubsequentToNull(note);
        if (view != null)
            view.setVisibility(View.GONE);
        return capi.toArray();
    }

    /**
     * Lists all media of a container object and adds them to the layout.
     *
     * @param detailed More or less details displayed
     */
    public static void placeMedia(LinearLayout layout, MediaContainer container, boolean detailed) {
        List<Media> allMedia = container.getAllMedia(Global.gc);
        if (!allMedia.isEmpty()) {
            RecyclerView recyclerView = new MediaAdapter.UnclickableRecyclerView(layout.getContext(), detailed);
            recyclerView.setHasFixedSize(true);
            RecyclerView.LayoutManager layoutManager = new GridLayoutManager(layout.getContext(), detailed ? 2 : 3);
            recyclerView.setLayoutManager(layoutManager);
            List<MediaContainerList.MedCont> mediaList = new ArrayList<>();
            for (Media media : allMedia)
                mediaList.add(new MediaContainerList.MedCont(media, container));
            MediaAdapter adapter = new MediaAdapter(mediaList, detailed);
            recyclerView.setAdapter(adapter);
            layout.addView(recyclerView);
        }
    }

    // Di un object inserisce le citazioni alle fonti
    public static void placeSourceCitations(LinearLayout layout, Object container) {
        if (Global.settings.expert) {
            List<SourceCitation> listaCitaFonti;
            if (container instanceof Note) // Note non estende SourceCitationContainer
                listaCitaFonti = ((Note)container).getSourceCitations();
            else listaCitaFonti = ((SourceCitationContainer)container).getSourceCitations();
            for (final SourceCitation citaz : listaCitaFonti) {
                View vistaCita = LayoutInflater.from(layout.getContext()).inflate(R.layout.pezzo_citazione_fonte, layout, false);
                layout.addView(vistaCita);
                if (citaz.getSource(Global.gc) != null) // source CITATION
                    ((TextView)vistaCita.findViewById(R.id.fonte_testo)).setText(SourcesFragment.titoloFonte(citaz.getSource(Global.gc)));
                else // source NOTE, oppure Citazione di fonte che è stata eliminata
                    vistaCita.findViewById(R.id.citazione_fonte).setVisibility(View.GONE);
                String t = "";
                if (citaz.getValue() != null) t += citaz.getValue() + "\n";
                if (citaz.getPage() != null) t += citaz.getPage() + "\n";
                if (citaz.getDate() != null) t += new GedcomDateConverter(citaz.getDate()).writeDateLong() + "\n";
                // Vale sia per sourceNote che per sourceCitation
                if (citaz.getText() != null) t += citaz.getText() + "\n";
                TextView vistaTesto = vistaCita.findViewById(R.id.citazione_testo);
                if (t.isEmpty()) vistaTesto.setVisibility(View.GONE);
                else vistaTesto.setText(t.substring(0, t.length() - 1));
                // Tutto il resto
                LinearLayout scatolaAltro = vistaCita.findViewById(R.id.citazione_note);
                placeNotes(scatolaAltro, citaz, false);
                placeMedia(scatolaAltro, citaz, false);
                vistaCita.setTag(R.id.tag_object, citaz);
                if (layout.getContext() instanceof ProfileActivity) { // ProfileFactsFragment
                    ((ProfileActivity)layout.getContext()).getPageFragment(1).registerForContextMenu(vistaCita);
                } else // AppCompatActivity
                    ((AppCompatActivity)layout.getContext()).registerForContextMenu(vistaCita);

                vistaCita.setOnClickListener(v -> {
                    Intent intent = new Intent(layout.getContext(), SourceCitationActivity.class);
                    Memory.add(citaz);
                    layout.getContext().startActivity(intent);
                });
            }
        }
    }

    // Inserisce nella scatola il richiamo ad una fonte, con dettagli o essenziale
    public static void placeSource(final LinearLayout layout, final Source source, boolean detailed) {
        View vistaFonte = LayoutInflater.from(layout.getContext()).inflate(R.layout.pezzo_fonte, layout, false);
        layout.addView(vistaFonte);
        TextView vistaTesto = vistaFonte.findViewById(R.id.fonte_testo);
        String txt = "";
        if (detailed) {
            if (source.getTitle() != null)
                txt = source.getTitle() + "\n";
            else if (source.getAbbreviation() != null)
                txt = source.getAbbreviation() + "\n";
            if (source.getType() != null)
                txt += source.getType().replaceAll("\n", " ") + "\n";
            if (source.getPublicationFacts() != null)
                txt += source.getPublicationFacts().replaceAll("\n", " ") + "\n";
            if (source.getText() != null)
                txt += source.getText().replaceAll("\n", " ");
            if (txt.endsWith("\n"))
                txt = txt.substring(0, txt.length() - 1);
            LinearLayout scatolaAltro = vistaFonte.findViewById(R.id.fonte_scatola);
            placeNotes(scatolaAltro, source, false);
            placeMedia(scatolaAltro, source, false);
            vistaFonte.setTag(R.id.tag_object, source);
            ((AppCompatActivity)layout.getContext()).registerForContextMenu(vistaFonte);
        } else {
            vistaTesto.setMaxLines(2);
            txt = SourcesFragment.titoloFonte(source);
        }
        vistaTesto.setText(txt);
        vistaFonte.setOnClickListener(v -> {
            Memory.setLeader(source);
            layout.getContext().startActivity(new Intent(layout.getContext(), SourceActivity.class));
        });
    }

    /**
     * Creates into layout a small card of a person, linked to the person profile.
     * Used by Cabinet and SharingActivity.
     */
    public static View placeSmallPerson(LinearLayout layout, Person person) {
        View personView = LayoutInflater.from(layout.getContext()).inflate(R.layout.pezzo_individuo_piccolo, layout, false);
        layout.addView(personView);
        FileUtil.INSTANCE.selectMainImage(person, personView.findViewById(R.id.collega_foto));
        ((TextView)personView.findViewById(R.id.collega_nome)).setText(properName(person));
        String dates = twoDates(person, false);
        TextView detailView = personView.findViewById(R.id.collega_dati);
        if (dates.isEmpty()) detailView.setVisibility(View.GONE);
        else detailView.setText(dates);
        if (!isDead(person))
            personView.findViewById(R.id.collega_lutto).setVisibility(View.GONE);
        if (Gender.isMale(person))
            personView.findViewById(R.id.collega_bordo).setBackgroundResource(R.drawable.casella_bordo_maschio);
        else if (Gender.isFemale(person))
            personView.findViewById(R.id.collega_bordo).setBackgroundResource(R.drawable.casella_bordo_femmina);
        personView.setOnClickListener(view -> {
            Memory.setLeader(person);
            Intent intent = new Intent(layout.getContext(), ProfileActivity.class);
            intent.putExtra(Extra.PAGE, 1);
            layout.getContext().startActivity(intent);
        });
        return personView;
    }

    public static String testoFamiglia(Context contesto, Gedcom gc, Family fam, boolean unaLinea) {
        String testo = "";
        for (Person marito : fam.getHusbands(gc))
            testo += properName(marito) + "\n";
        for (Person moglie : fam.getWives(gc))
            testo += properName(moglie) + "\n";
        if (fam.getChildren(gc).size() == 1) {
            testo += properName(fam.getChildren(gc).get(0));
        } else if (fam.getChildren(gc).size() > 1)
            testo += contesto.getString(R.string.num_children, fam.getChildren(gc).size());
        if (testo.endsWith("\n")) testo = testo.substring(0, testo.length() - 1);
        if (unaLinea)
            testo = testo.replaceAll("\n", ", ");
        if (testo.isEmpty())
            testo = "[" + contesto.getString(R.string.empty_family) + "]";
        return testo;
    }

    // Usato da dispensa
    static void linkaFamiglia(LinearLayout scatola, Family fam) {
        View vistaFamiglia = LayoutInflater.from(scatola.getContext()).inflate(R.layout.piece_family, scatola, false);
        scatola.addView(vistaFamiglia);
        ((TextView)vistaFamiglia.findViewById(R.id.family_text)).setText(testoFamiglia(scatola.getContext(), Global.gc, fam, false));
        vistaFamiglia.setOnClickListener(v -> {
            Memory.setLeader(fam);
            scatola.getContext().startActivity(new Intent(scatola.getContext(), FamilyActivity.class));
        });
    }

    // Used by cabinet
    static void placeMedia(LinearLayout layout, Media media) {
        View mediaView = LayoutInflater.from(layout.getContext()).inflate(R.layout.pezzo_media, layout, false);
        layout.addView(mediaView);
        MediaUtil.INSTANCE.furnishMedia(media, mediaView.findViewById(R.id.media_testo), mediaView.findViewById(R.id.media_num));
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)mediaView.getLayoutParams();
        params.height = dpToPx(80);
        FileUtil.INSTANCE.showImage(media, mediaView.findViewById(R.id.media_img), 0, mediaView.findViewById(R.id.media_circolo));
        mediaView.setOnClickListener(v -> {
            Memory.setLeader(media);
            layout.getContext().startActivity(new Intent(layout.getContext(), MediaActivity.class));
        });
    }

    // Aggiunge un autore al layout
    static void linkAutore(LinearLayout scatola, Submitter autor) {
        Context contesto = scatola.getContext();
        View vista = LayoutInflater.from(contesto).inflate(R.layout.piece_note, scatola, false);
        scatola.addView(vista);
        TextView testoNota = vista.findViewById(R.id.note_text);
        testoNota.setText(autor.getName());
        vista.findViewById(R.id.note_sources).setVisibility(View.GONE);
        vista.setOnClickListener(v -> {
            Memory.setLeader(autor);
            contesto.startActivity(new Intent(contesto, SubmitterActivity.class));
        });
    }

    /**
     * Adds to the Layout a box containing a list of assorted items.
     * Used at bottom of {@link DetailActivity} to display various links to related records.
     *
     * @param object Can be a single or an array of Gedcom objects
     * @param label  Title above the cabinet
     */
    public static void placeCabinet(LinearLayout layout, Object object, int label) {
        View cabinetView = LayoutInflater.from(layout.getContext()).inflate(R.layout.dispensa, layout, false);
        TextView vistaTit = cabinetView.findViewById(R.id.dispensa_titolo);
        vistaTit.setText(label);
        vistaTit.setBackground(AppCompatResources.getDrawable(layout.getContext(), R.drawable.sghembo)); // For KitKat
        layout.addView(cabinetView);
        LinearLayout cabinet = cabinetView.findViewById(R.id.dispensa_scatola);
        if (object instanceof Object[]) {
            for (Object obj : (Object[])object)
                placeObject(cabinet, obj);
        } else
            placeObject(cabinet, object);
    }

    /**
     * Recognizes the record type and adds the appropriate link to the cabinet.
     */
    static void placeObject(LinearLayout layout, Object record) {
        if (record instanceof Person)
            placeSmallPerson(layout, (Person)record);
        else if (record instanceof Source)
            placeSource(layout, (Source)record, false);
        else if (record instanceof Family)
            linkaFamiglia(layout, (Family)record);
        else if (record instanceof Repository)
            RepositoryRefActivity.putRepository(layout, (Repository)record);
        else if (record instanceof Note)
            placeNote(layout, (Note)record, true);
        else if (record instanceof Media)
            placeMedia(layout, (Media)record);
        else if (record instanceof Submitter)
            linkAutore(layout, (Submitter)record);
    }

    // Aggiunge al layout il pezzo con la data e tempo di Cambiamento
    public static void placeChangeDate(final LinearLayout layout, final Change change) {
        View changeView = null;
        if (change != null && Global.settings.expert) {
            changeView = LayoutInflater.from(layout.getContext()).inflate(R.layout.pezzo_data_cambiamenti, layout, false);
            layout.addView(changeView);
            TextView textView = changeView.findViewById(R.id.cambi_testo);
            if (change.getDateTime() != null) {
                String txt = "";
                if (change.getDateTime().getValue() != null)
                    txt = new GedcomDateConverter(change.getDateTime().getValue()).writeDateLong();
                if (change.getDateTime().getTime() != null)
                    txt += " - " + change.getDateTime().getTime();
                textView.setText(txt);
            }
            LinearLayout scatolaNote = changeView.findViewById(R.id.cambi_note);
            for (Extension altroTag : findExtensions(change))
                place(scatolaNote, altroTag.name, altroTag.text);
            // Grazie al mio contributo la data cambiamento può avere delle note
            placeNotes(scatolaNote, change, false);
            changeView.setOnClickListener(v -> {
                Memory.add(change);
                layout.getContext().startActivity(new Intent(layout.getContext(), ChangeActivity.class));
            });
        }
    }

    // Chiede conferma di eliminare un elemento.
    public static boolean preserva(Object cosa) {
        // todo Conferma elimina
        return false;
    }

    public static int castJsonInt(Object unknown) {
        if (unknown instanceof Integer) return (int)unknown;
        else return ((JsonPrimitive)unknown).getAsInt();
    }

    public static String castJsonString(Object unknown) {
        if (unknown == null) return null;
        else if (unknown instanceof String) return (String)unknown;
        else return ((JsonPrimitive)unknown).getAsString();
    }

    static float pxToDp(float pixels) {
        return pixels / Global.context.getResources().getDisplayMetrics().density;
    }

    public static int dpToPx(float dips) {
        return (int)(dips * Global.context.getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * Evaluates whether there are people connectable with respect to a given person.
     * Used to decide whether to show 'Link existing person' menu item.
     */
    static boolean linkablePersons(Person person) {
        int total = Global.gc.getPeople().size();
        if (total > 0 && (Global.settings.expert // Expert user can always
                || person == null)) // In an empty family oneFamilyMember is null
            return true;
        int kinship = PersonsFragment.countRelatives(person);
        return total > kinship + 1;
    }

    // Chiede se referenziare un autore nell'header dell'albero
    static void autorePrincipale(Context contesto, final String idAutore) {
        final Header[] testa = {Global.gc.getHeader()};
        if (testa[0] == null || testa[0].getSubmitterRef() == null) {
            new AlertDialog.Builder(contesto).setMessage(R.string.make_main_submitter)
                    .setPositiveButton(android.R.string.yes, (dialog, id) -> {
                        if (testa[0] == null) {
                            testa[0] = TreeUtils.INSTANCE.createHeader(Global.settings.openTree + ".json");
                            Global.gc.setHeader(testa[0]);
                        }
                        testa[0].setSubmitterRef(idAutore);
                        TreeUtils.INSTANCE.save(true);
                    }).setNegativeButton(R.string.no, null).show();
        }
    }

    /**
     * Check if a submitter has participated in the shares.
     * Used by {@link SubmittersFragment} and {@link DetailActivity} to avoid deleting the submitter.
     */
    public static boolean submitterHasShared(Submitter submitter) {
        List<Settings.Share> shares = Global.settings.getCurrentTree().shares;
        boolean shared = false;
        if (shares != null)
            for (Settings.Share share : shares)
                if (submitter.getId().equals(share.submitter))
                    shared = true;
        return shared;
    }

    // Elenco di stringhe dei membri rappresentativi delle famiglie
    static String[] elencoFamiglie(List<Family> listaFamiglie) {
        List<String> famigliePerno = new ArrayList<>();
        for (Family fam : listaFamiglie) {
            String etichetta = testoFamiglia(Global.context, Global.gc, fam, true);
            famigliePerno.add(etichetta);
        }
        return famigliePerno.toArray(new String[0]);
    }

    /**
     * May ask which family to show of a person who is child in more than one family.
     *
     * @param whatToOpen Activity/Fragment to open:
     *                   0: diagram of the previous family, without asking which family (first click on Diagram)
     *                   1: diagram possibly asking which family
     *                   2: family possibly asking which family
     */
    public static void whichParentsToShow(Context context, Person person, int whatToOpen) {
        if (person == null) {
            finishParentSelection(context, null, 1, 0);
        } else {
            List<Family> famiglie = person.getParentFamilies(Global.gc);
            if (famiglie.size() > 1 && whatToOpen > 0) {
                new AlertDialog.Builder(context).setTitle(R.string.which_family)
                        .setItems(elencoFamiglie(famiglie), (dialog, quale) -> {
                            finishParentSelection(context, person, whatToOpen, quale);
                        }).show();
            } else finishParentSelection(context, person, whatToOpen, 0);
        }

    }

    private static void finishParentSelection(Context context, Person person, int whatToOpen, int whichFamily) {
        if (person != null)
            Global.indi = person.getId();
        if (whatToOpen > 0) // Sets the family to show
            Global.familyNum = whichFamily; // Normally it is 0
        if (whatToOpen < 2) { // Displays the diagram
            if (context instanceof Principal) { // DiagramFragment, PersonsFragment or Principal itself
                FragmentManager fm = ((AppCompatActivity)context).getSupportFragmentManager();
                // Previous fragment in the backstack
                Fragment previousFragment = fm.getFragments().get(fm.getFragments().size() - 1);
                if (previousFragment instanceof DiagramFragment)
                    fm.popBackStack(); // Clicking on Diagram removes the previous diagram fragment from the backstack
                fm.beginTransaction().replace(R.id.contenitore_fragment, new DiagramFragment()).addToBackStack(null).commit();
            } else { // From ProfileActivity or from FamilyActivity
                context.startActivity(new Intent(context, Principal.class));
            }
        } else { // The family is shown
            Family family = person.getParentFamilies(Global.gc).get(whichFamily);
            if (context instanceof FamilyActivity) { // Moving from family to family does not accumulate activities in the stack
                Memory.replaceLeader(family);
                ((Activity)context).recreate();
            } else {
                Memory.setLeader(family);
                context.startActivity(new Intent(context, FamilyActivity.class));
            }
        }
    }

    /**
     * For a person who has multiple marriages asks which one to show.
     */
    public static void whichSpousesToShow(Context context, Person person, Family family) {
        if (person.getSpouseFamilies(Global.gc).size() > 1 && family == null) {
            new AlertDialog.Builder(context).setTitle(R.string.which_family)
                    .setItems(elencoFamiglie(person.getSpouseFamilies(Global.gc)), (dialog, which) -> {
                        finishSpousesSelection(context, person, null, which);
                    }).show();
        } else {
            finishSpousesSelection(context, person, family, 0);
        }
    }

    private static void finishSpousesSelection(Context context, Person person, Family family, int whichFamily) {
        Global.indi = person.getId();
        family = family == null ? person.getSpouseFamilies(Global.gc).get(whichFamily) : family;
        if (context instanceof FamilyActivity) {
            Memory.replaceLeader(family);
            ((Activity)context).recreate(); // Non accumula activity nello stack
        } else {
            Memory.setLeader(family);
            context.startActivity(new Intent(context, FamilyActivity.class));
        }
    }

    /**
     * Used to link one person to another, in non-expert mode only.
     * Checks if the pivot has multiple marriages and asks which one to attach a spouse or child to.
     * Is also responsible for setting FAMILY_ID or DESTINATION.
     *
     * @return True if a dialog is shown
     */
    static boolean checkMultiMarriages(Intent intent, Context context, Fragment fragment) {
        String pivotId = intent.getStringExtra(Extra.PERSON_ID);
        Person pivot = Global.gc.getPerson(pivotId);
        List<Family> parentFamilies = pivot.getParentFamilies(Global.gc);
        List<Family> spouseFamilies = pivot.getSpouseFamilies(Global.gc);
        Relation relation = (Relation)intent.getSerializableExtra(Extra.RELATION);
        ArrayAdapter<NewRelativeDialog.FamilyItem> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1);

        // Parents: there is already a family that has at least one empty space
        if (relation == Relation.PARENT && parentFamilies.size() == 1
                && (parentFamilies.get(0).getHusbandRefs().isEmpty() || parentFamilies.get(0).getWifeRefs().isEmpty()))
            intent.putExtra(Extra.FAMILY_ID, parentFamilies.get(0).getId()); // Adds 'FAMILY_ID' to the existing intent
        // If this unique family is already full of parents, FAMILY_ID remains null
        // then the recipient's existing family will be searched, or a new family will be created

        // Parents: there are many families
        if (relation == Relation.PARENT && parentFamilies.size() > 1) {
            for (Family fam : parentFamilies)
                if (fam.getHusbandRefs().isEmpty() || fam.getWifeRefs().isEmpty())
                    adapter.add(new NewRelativeDialog.FamilyItem(context, fam));
            if (adapter.getCount() == 1)
                intent.putExtra(Extra.FAMILY_ID, adapter.getItem(0).family.getId());
            else if (adapter.getCount() > 1) {
                new AlertDialog.Builder(context).setTitle(R.string.which_family_add_parent)
                        .setAdapter(adapter, (dialog, quale) -> {
                            intent.putExtra(Extra.FAMILY_ID, adapter.getItem(quale).family.getId());
                            concludeMultiMarriages(context, intent, fragment);
                        }).show();
                return true;
            }
        }
        // Sibling
        else if (relation == Relation.SIBLING && parentFamilies.size() == 1) {
            intent.putExtra(Extra.FAMILY_ID, parentFamilies.get(0).getId());
        } else if (relation == Relation.SIBLING && parentFamilies.size() > 1) {
            new AlertDialog.Builder(context).setTitle(R.string.which_family_add_sibling)
                    .setItems(elencoFamiglie(parentFamilies), (dialog, quale) -> {
                        intent.putExtra(Extra.FAMILY_ID, parentFamilies.get(quale).getId());
                        concludeMultiMarriages(context, intent, fragment);
                    }).show();
            return true;
        }
        // Partner
        else if (relation == Relation.PARTNER && spouseFamilies.size() == 1) {
            if (spouseFamilies.get(0).getHusbandRefs().isEmpty() || spouseFamilies.get(0).getWifeRefs().isEmpty())
                intent.putExtra(Extra.FAMILY_ID, spouseFamilies.get(0).getId());
        } else if (relation == Relation.PARTNER && spouseFamilies.size() > 1) {
            for (Family family : spouseFamilies) {
                if (family.getHusbandRefs().isEmpty() || family.getWifeRefs().isEmpty())
                    adapter.add(new NewRelativeDialog.FamilyItem(context, family));
            }
            // In the case of zero eligible families, FAMILY_ID remains null
            if (adapter.getCount() == 1) {
                intent.putExtra(Extra.FAMILY_ID, adapter.getItem(0).family.getId());
            } else if (adapter.getCount() > 1) {
                new AlertDialog.Builder(context).setTitle(R.string.which_family_add_spouse)
                        .setAdapter(adapter, (dialog, which) -> {
                            intent.putExtra(Extra.FAMILY_ID, adapter.getItem(which).family.getId());
                            concludeMultiMarriages(context, intent, fragment);
                        }).show();
                return true;
            }
        }
        // Child: there is already a family with or without children
        else if (relation == Relation.CHILD && spouseFamilies.size() == 1) {
            intent.putExtra(Extra.FAMILY_ID, spouseFamilies.get(0).getId());
        } // Child: there are many spouse families
        else if (relation == Relation.CHILD && spouseFamilies.size() > 1) {
            new AlertDialog.Builder(context).setTitle(R.string.which_family_add_child)
                    .setItems(elencoFamiglie(spouseFamilies), (dialog, quale) -> {
                        intent.putExtra(Extra.FAMILY_ID, spouseFamilies.get(quale).getId());
                        concludeMultiMarriages(context, intent, fragment);
                    }).show();
            return true;
        }
        // Not having found a family of pivot, tells PersonsFragment to try to place the pivot in the recipient's family
        if (intent.getStringExtra(Extra.FAMILY_ID) == null && intent.getBooleanExtra(Choice.PERSON, false))
            intent.putExtra(Extra.DESTINATION, "EXISTING_FAMILY");
        return false;
    }

    // Conclusion of the previous function
    static void concludeMultiMarriages(Context context, Intent intent, Fragment fragment) {
        if (intent.getBooleanExtra(Choice.PERSON, false)) {
            // Opens PersonsFragment
            if (fragment != null)
                ((DiagramFragment)fragment).choosePersonLauncher.launch(intent);
            else
                ((ProfileActivity)context).choosePersonLauncher.launch(intent);
        } else // Opens PersonEditorActivity
            context.startActivity(intent);
    }

    // Controlla che una o più famiglie siano vuote e propone di eliminarle
    // 'ancheKo' dice di eseguire 'cheFare' anche cliccando Cancel o fuori dal dialogo
    public static boolean controllaFamiglieVuote(Context contesto, Runnable cheFare, boolean ancheKo, Family... famiglie) {
        List<Family> vuote = new ArrayList<>();
        for (Family fam : famiglie) {
            int membri = fam.getHusbandRefs().size() + fam.getWifeRefs().size() + fam.getChildRefs().size();
            if (membri <= 1 && fam.getEventsFacts().isEmpty() && fam.getAllMedia(Global.gc).isEmpty()
                    && fam.getAllNotes(Global.gc).isEmpty() && fam.getSourceCitations().isEmpty()) {
                vuote.add(fam);
            }
        }
        if (vuote.size() > 0) {
            new AlertDialog.Builder(contesto).setMessage(R.string.empty_family_delete)
                    .setPositiveButton(android.R.string.yes, (dialog, i) -> {
                        for (Family fam : vuote)
                            FamiliesFragment.deleteFamily(fam); // Così capita di salvare più volte insieme... ma vabè
                        if (cheFare != null) cheFare.run();
                    }).setNeutralButton(android.R.string.cancel, (dialog, i) -> {
                        if (ancheKo) cheFare.run();
                    }).setOnCancelListener(dialog -> {
                        if (ancheKo) cheFare.run();
                    }).show();
            return true;
        }
        return false;
    }

    // Display a dialog to edit the ID of any record
    public static void editId(Context context, ExtensionContainer record, Runnable refresh) {
        View view = ((Activity)context).getLayoutInflater().inflate(R.layout.id_editor, null);
        EditText inputField = view.findViewById(R.id.edit_id_input_field);
        try {
            String oldId = (String)record.getClass().getMethod("getId").invoke(record);
            inputField.setText(oldId);
            AlertDialog alertDialog = new AlertDialog.Builder(context)
                    .setTitle(R.string.edit_id).setView(view)
                    .setPositiveButton(R.string.save, (dialog, i) -> {
                        String newId = inputField.getText().toString().trim();
                        if (newId.equals(oldId)) return;
                        if (record instanceof Person) {
                            Person person = (Person)record;
                            person.setId(newId);
                            Set<PersonFamilyCommonContainer> modified = new HashSet<>();
                            modified.add(person);
                            for (Family family : Global.gc.getFamilies()) {
                                for (SpouseRef ref : family.getHusbandRefs())
                                    if (oldId.equals(ref.getRef())) {
                                        ref.setRef(newId);
                                        modified.add(family);
                                    }
                                for (SpouseRef ref : family.getWifeRefs())
                                    if (oldId.equals(ref.getRef())) {
                                        ref.setRef(newId);
                                        modified.add(family);
                                    }
                                for (ChildRef ref : family.getChildRefs())
                                    if (oldId.equals(ref.getRef())) {
                                        ref.setRef(newId);
                                        modified.add(family);
                                    }
                            }
                            TreeUtils.INSTANCE.save(true, modified.toArray());
                            Settings.Tree tree = Global.settings.getCurrentTree();
                            if (oldId.equals(tree.root)) {
                                tree.root = newId;
                                Global.settings.save();
                            }
                            Global.indi = newId;
                        } else if (record instanceof Family) {
                            Family family = (Family)record;
                            family.setId(newId);
                            Set<PersonFamilyCommonContainer> modified = new HashSet<>();
                            modified.add(family);
                            for (Person person : Global.gc.getPeople()) {
                                for (ParentFamilyRef ref : person.getParentFamilyRefs())
                                    if (oldId.equals(ref.getRef())) {
                                        ref.setRef(newId);
                                        modified.add(person);
                                    }
                                for (SpouseFamilyRef ref : person.getSpouseFamilyRefs())
                                    if (oldId.equals(ref.getRef())) {
                                        ref.setRef(newId);
                                        modified.add(person);
                                    }
                            }
                            TreeUtils.INSTANCE.save(true, modified.toArray());
                        } else if (record instanceof Media) {
                            Media media = (Media)record;
                            MediaContainers mediaContainers = new MediaContainers(Global.gc, media, newId);
                            media.setId(newId);
                            ChangeUtil.INSTANCE.updateChangeDate(media);
                            TreeUtils.INSTANCE.save(true, mediaContainers.containers.toArray());
                        } else if (record instanceof Note) {
                            Note note = (Note)record;
                            NoteContainers noteContainers = new NoteContainers(Global.gc, note, newId);
                            note.setId(newId);
                            ChangeUtil.INSTANCE.updateChangeDate(note);
                            TreeUtils.INSTANCE.save(true, noteContainers.containers.toArray());
                        } else if (record instanceof Source) {
                            ListOfSourceCitations citations = new ListOfSourceCitations(Global.gc, oldId);
                            for (ListOfSourceCitations.Triplet triple : citations.list)
                                triple.citation.setRef(newId);
                            Source source = (Source)record;
                            source.setId(newId);
                            ChangeUtil.INSTANCE.updateChangeDate(source);
                            TreeUtils.INSTANCE.save(true, citations.getProgenitors());
                        } else if (record instanceof Repository) {
                            Set<Source> modified = new HashSet<>();
                            for (Source source : Global.gc.getSources()) {
                                RepositoryRef repoRef = source.getRepositoryRef();
                                if (repoRef != null && oldId.equals(repoRef.getRef())) {
                                    repoRef.setRef(newId);
                                    modified.add(source);
                                }
                            }
                            Repository repo = (Repository)record;
                            repo.setId(newId);
                            ChangeUtil.INSTANCE.updateChangeDate(repo);
                            TreeUtils.INSTANCE.save(true, modified.toArray());
                        } else if (record instanceof Submitter) {
                            for (Settings.Share share : Global.settings.getCurrentTree().shares)
                                if (oldId.equals(share.submitter))
                                    share.submitter = newId;
                            Global.settings.save();
                            Header header = Global.gc.getHeader();
                            if (oldId.equals(header.getSubmitterRef()))
                                header.setSubmitterRef(newId);
                            Submitter submitter = (Submitter)record;
                            submitter.setId(newId);
                            TreeUtils.INSTANCE.save(true, submitter);
                        }
                        Global.gc.createIndexes();
                        refresh.run();
                    }).setNeutralButton(R.string.cancel, null).show();
            // Focus
            view.postDelayed(() -> {
                inputField.requestFocus();
                inputField.setSelection(inputField.getText().length());
                InputMethodManager inputMethodManager = (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT);
            }, 300);
            // All other IDs
            Set<String> allIds = new HashSet<>();
            for (Person person : Global.gc.getPeople())
                allIds.add(person.getId());
            for (Family family : Global.gc.getFamilies())
                allIds.add(family.getId());
            for (Media media : Global.gc.getMedia())
                allIds.add(media.getId());
            for (Note note : Global.gc.getNotes())
                allIds.add(note.getId());
            for (Source source : Global.gc.getSources())
                allIds.add(source.getId());
            for (Repository repo : Global.gc.getRepositories())
                allIds.add(repo.getId());
            for (Submitter submitter : Global.gc.getSubmitters())
                allIds.add(submitter.getId());
            allIds.remove(oldId);
            // Validation
            TextInputLayout inputLayout = view.findViewById(R.id.edit_id_input_layout);
            Button okButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            inputField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence sequence, int start, int before, int count) {
                    String error = null;
                    String proposal = sequence.toString().trim();
                    if (allIds.contains(proposal))
                        error = context.getString(R.string.existing_id);
                    else if (proposal.isEmpty() || proposal.matches("^[#].*|.*[@:!].*"))
                        error = context.getString(R.string.invalid_id);
                    inputLayout.setError(error);
                    okButton.setEnabled(error == null);
                }

                @Override
                public void afterTextChanged(Editable e) {
                }
            });
            inputField.setOnEditorActionListener((textView, actionId, keyEvent) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE && okButton.isEnabled()) {
                    okButton.performClick();
                    return true;
                }
                return false;
            });
        } catch (Exception e) {
        }
    }

    /**
     * Connects to the online server to get credentials.
     * Must be called from a working thread.
     */
    public static JSONObject getCredential(String request) {
        try {
            String protocol = "https";
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) protocol = "http";
            URL url = new URL(protocol + "://www.familygem.app/credential.php");
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("POST");
            OutputStream stream = connection.getOutputStream();
            String query = "passKey=" + URLEncoder.encode(BuildConfig.PASS_KEY, "UTF-8")
                    + "&request=" + request;
            stream.write(query.getBytes(StandardCharsets.UTF_8));
            stream.flush();
            stream.close();
            // Answer
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line = reader.readLine();
            connection.disconnect();
            reader.close();
            if (line.contains(Json.USER)) {
                return new JSONObject(new JSONTokener(line));
            } else
                toast(line);
        } catch (Exception ignored) {
            // Usually no connection to internet
        }
        return null;
    }

    /**
     * Shows a toast message coming also from a working thread.
     */
    public static void toast(int message) {
        toast(s(message));
    }

    public static void toast(String message) {
        if (message != null) {
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(
                    Global.context, message, Toast.LENGTH_LONG).show());
        }
    }
}
