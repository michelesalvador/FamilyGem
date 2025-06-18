package app.familygem;

import static app.familygem.Global.gc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.util.Pair;

import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.folg.gedcom.model.Address;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.ExtensionContainer;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.GedcomTag;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;
import org.folg.gedcom.model.MediaRef;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.NoteRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.RepositoryRef;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;
import org.folg.gedcom.model.SpouseFamilyRef;
import org.folg.gedcom.model.SpouseRef;
import org.folg.gedcom.model.Submitter;
import org.folg.gedcom.model.Visitable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.familygem.constant.Choice;
import app.familygem.constant.Destination;
import app.familygem.constant.Extra;
import app.familygem.constant.Relation;
import app.familygem.detail.AddressActivity;
import app.familygem.detail.EventActivity;
import app.familygem.detail.ExtensionActivity;
import app.familygem.detail.FamilyActivity;
import app.familygem.detail.MediaActivity;
import app.familygem.detail.NameActivity;
import app.familygem.detail.NoteActivity;
import app.familygem.detail.SourceCitationActivity;
import app.familygem.detail.SubmitterActivity;
import app.familygem.main.MainActivity;
import app.familygem.main.RepositoriesFragment;
import app.familygem.main.SourcesFragment;
import app.familygem.main.SubmittersFragment;
import app.familygem.profile.ProfileActivity;
import app.familygem.util.AddressUtilKt;
import app.familygem.util.ChangeUtil;
import app.familygem.util.EventUtilKt;
import app.familygem.util.FamilyUtil;
import app.familygem.util.FamilyUtilKt;
import app.familygem.util.FileUtil;
import app.familygem.util.MediaUtil;
import app.familygem.util.MediaUtilKt;
import app.familygem.util.NoteUtil;
import app.familygem.util.PersonUtil;
import app.familygem.util.PersonUtilKt;
import app.familygem.util.RepositoryUtil;
import app.familygem.util.SourceUtil;
import app.familygem.util.TreeUtil;
import app.familygem.util.Util;
import app.familygem.visitor.FindStack;
import app.familygem.visitor.MediaReferences;
import kotlin.Unit;

public abstract class DetailActivity extends AppCompatActivity {

    protected LinearLayout box;
    protected Object object; // Name, Media, SourceCitation etc.
    private final List<Egg> eggs = new ArrayList<>(); // List of all the possible editable pieces
    private List<Pair<String, String>> otherEvents; // Events for the Family FAB
    protected boolean surnameBefore; // The given name comes after the surname, e.g. "/Simpson/ Homer"
    protected Person oneFamilyMember; // A family member used to hide in the FAB 'Link person'
    private DateEditorLayout dateEditor;
    private FloatingActionButton fabView;
    private ActionBar actionBar;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_detail);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        box = findViewById(R.id.detail_box);
        fabView = findViewById(R.id.fab);
        actionBar = getSupportActionBar();

        // List of other Family events
        String[] otherEventTags = {"ANUL", "CENS", "DIVF", "ENGA", "MARB", "MARC", "MARL", "MARS", "RESI", "EVEN", "NCHI"};
        otherEvents = new ArrayList<>();
        for (String tag : otherEventTags) {
            EventFact event = new EventFact();
            event.setTag(tag);
            String label = event.getDisplayType();
            if (Global.settings.expert)
                label += " — " + tag;
            otherEvents.add(new Pair<>(tag, label));
        }
        Collections.sort(otherEvents, (item1, item2) -> item1.second.compareTo(item2.second));

        object = Memory.getLastObject();
        if (object == null) {
            goBack(); // Skips this detail without object
        } else if (TreeUtil.INSTANCE.isGlobalGedcomOk(this::setupInterface)) {
            setupInterface();
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Memory.stepBack();
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
    }

    private void setupInterface() {
        // Content
        format();
        // Floating Action Button
        fabView.setOnClickListener(view -> {
            PopupMenu popup = createFabMenu();
            popup.setOnMenuItemClickListener(fabMenuListener);
            popup.show();
        });
        // If the FAB menu is empty hides the FAB
        if (!createFabMenu().getMenu().hasVisibleItems()) fabView.hide();
        // TODO: when the FAB was hidden, if a piece is deleted the FAB should reappear
    }

    /**
     * Creates FAB menu: only with methods that are not already present in box.
     */
    private PopupMenu createFabMenu() {
        PopupMenu popup = new PopupMenu(this, fabView);
        Menu menu = popup.getMenu();
        String[] withAddress = {"Www", "Email", "Phone", "Fax"}; // These objects appear in the Event FAB if an Address exists
        int counter = 0;
        for (Egg egg : eggs) {
            boolean alreadyPut = false;
            boolean addressPresent = false;
            for (int i = 0; i < box.getChildCount(); i++) {
                Object object = box.getChildAt(i).getTag(R.id.tag_object);
                if (object != null && object.equals(egg.yolk))
                    alreadyPut = true;
                if (object instanceof Address)
                    addressPresent = true;
            }
            if (!alreadyPut) {
                if (egg.common || (addressPresent && Arrays.asList(withAddress).contains(egg.yolk)))
                    menu.add(0, counter, 0, egg.title);
            }
            counter++;
        }
        if (object instanceof Family) {
            Family family = (Family)object;
            boolean hasChildren = !family.getChildRefs().isEmpty();
            SubMenu newMemberMenu = menu.addSubMenu(0, 100, 0, R.string.new_relative);
            // Inexpert can add maximum two parents, expert also more than two
            if (family.getHusbandRefs().size() + family.getWifeRefs().size() < 2)
                newMemberMenu.add(0, 120, 0, hasChildren ? R.string.parent : R.string.partner);
            newMemberMenu.add(0, 121, 0, R.string.child);
            // Inexpert can add people only not already member of the family
            Set<Person> members = new HashSet<>(family.getHusbands(gc));
            members.addAll(family.getWives(gc));
            members.addAll(family.getChildren(gc));
            if (Global.settings.expert || !members.containsAll(gc.getPeople())) {
                SubMenu linkMemberMenu = menu.addSubMenu(0, 100, 0, R.string.link_person);
                if (family.getHusbandRefs().size() + family.getWifeRefs().size() < 2)
                    linkMemberMenu.add(0, 122, 0, hasChildren ? R.string.parent : R.string.partner);
                linkMemberMenu.add(0, 123, 0, R.string.child);
            }
            SubMenu eventSubMenu = menu.addSubMenu(0, 100, 0, R.string.event);
            String marriageLabel = getString(R.string.marriage) + " / " + getString(R.string.relationship);
            String divorceLabel = getString(R.string.divorce) + " / " + getString(R.string.separation);
            if (Global.settings.expert) {
                marriageLabel += " — MARR";
                divorceLabel += " — DIV";
            }
            eventSubMenu.add(0, 130, 0, marriageLabel);
            eventSubMenu.add(0, 131, 0, divorceLabel);

            // The other events that can be placed
            SubMenu otherSubMenu = eventSubMenu.addSubMenu(0, 100, 0, R.string.other);
            int i = 0;
            for (Pair<String, String> event : otherEvents) {
                otherSubMenu.add(0, 200 + i, 0, event.second);
                i++;
            }
        }
        if (object instanceof Source && findViewById(R.id.sourceCitation) == null) {
            SubMenu subRepository = menu.addSubMenu(0, 100, 0, R.string.repository);
            subRepository.add(0, 101, 0, R.string.new_repository);
            if (!gc.getRepositories().isEmpty())
                subRepository.add(0, 102, 0, R.string.link_repository);
        }
        if (object instanceof NoteContainer) {
            SubMenu subNote = menu.addSubMenu(0, 100, 0, R.string.note);
            subNote.add(0, 103, 0, R.string.new_note);
            subNote.add(0, 104, 0, R.string.new_shared_note);
            if (!gc.getNotes().isEmpty())
                subNote.add(0, 105, 0, R.string.link_shared_note);
        }
        if (object instanceof MediaContainer) {
            SubMenu subMedia = menu.addSubMenu(0, 100, 0, R.string.media);
            subMedia.add(0, 106, 0, R.string.new_media);
            subMedia.add(0, 107, 0, R.string.new_shared_media);
            if (!gc.getMedia().isEmpty())
                subMedia.add(0, 108, 0, R.string.link_shared_media);
        }
        if ((object instanceof SourceCitationContainer || object instanceof Note) && Global.settings.expert) {
            SubMenu subSource = menu.addSubMenu(0, 100, 0, R.string.source);
            subSource.add(0, 110, 0, R.string.new_source);
            if (!gc.getSources().isEmpty())
                subSource.add(0, 111, 0, R.string.link_source);
        }
        return popup;
    }

    /**
     * File coming from SAF or other app becomes simple media.
     */
    private final ActivityResultLauncher<Intent> localMediaLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Media media = new Media();
            media.setFileTag("FILE");
            ((MediaContainer)object).addMedia(media);
            if (FileUtil.INSTANCE.setFileAndProposeCropping(this, result.getData(), media)) {
                TreeUtil.INSTANCE.save(true, Memory.getLeaderObject());
                refresh(); // Sometimes necessary
            }
        }
    });

    /**
     * File coming from SAF or other app becomes shared media.
     */
    private final ActivityResultLauncher<Intent> sharedMediaLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Media media = MediaUtil.INSTANCE.newSharedMedia((MediaContainer)object);
            if (FileUtil.INSTANCE.setFileAndProposeCropping(this, result.getData(), media)) {
                TreeUtil.INSTANCE.save(true, media, Memory.getLeaderObject());
                refresh(); // Sometimes necessary
            }
        }
    });

    /**
     * Actions to place a new piece (egg) optionally making it immediately editable.
     */
    PopupMenu.OnMenuItemClickListener fabMenuListener = item -> {
        int id = item.getItemId();
        boolean toBeSaved = false;
        if (id < 100) {
            Object thing = eggs.get(id).yolk;
            if (thing instanceof Address) { // thing is a new Address()
                if (object instanceof EventFact)
                    ((EventFact)object).setAddress((Address)thing);
                else if (object instanceof Submitter)
                    ((Submitter)object).setAddress((Address)thing);
                else if (object instanceof Repository)
                    ((Repository)object).setAddress((Address)thing);
            }
            // Tags needed to then export to Gedcom
            if (object instanceof Name && thing.equals("Type")) {
                ((Name)object).setTypeTag("TYPE");
            } else if (object instanceof Repository) {
                if (thing.equals("Www"))
                    ((Repository)object).setWwwTag("WWW");
                if (thing.equals("Email"))
                    ((Repository)object).setEmailTag("EMAIL");
            } else if (object instanceof Submitter) {
                if (thing.equals("Www"))
                    ((Submitter)object).setWwwTag("WWW");
                if (thing.equals("Email"))
                    ((Submitter)object).setEmailTag("EMAIL");
            }
            View piece = placePiece(eggs.get(id).title, "", thing, eggs.get(id).inputType);
            if (thing instanceof String)
                edit(piece);
            // TODO: open new Address for editing
        } else if (id == 101) { // TODO: code smell: use of magic numbers
            RepositoriesFragment.newRepository(this, (Source)object);
        } else if (id == 102) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(Choice.REPOSITORY, true);
            startActivityForResult(intent, 4562);
        } else if (id == 103) { // New note
            Note note = new Note();
            note.setValue("");
            ((NoteContainer)object).addNote(note);
            Memory.add(note);
            startActivity(new Intent(this, NoteActivity.class));
            toBeSaved = true;
        } else if (id == 104) { // New shared note
            NoteUtil.INSTANCE.createSharedNote(this, (NoteContainer)object);
        } else if (id == 105) { // Link shared note
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(Choice.NOTE, true);
            startActivityForResult(intent, 7074);
        } else if (id == 106) { // Search for simple media
            FileUtil.INSTANCE.displayFileChooser(this, localMediaLauncher, Destination.SIMPLE_MEDIA);
        } else if (id == 107) { // Search for shared media
            FileUtil.INSTANCE.displayFileChooser(this, sharedMediaLauncher, Destination.SHARED_MEDIA);
        } else if (id == 108) { // Link shared media
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(Choice.MEDIA, true);
            startActivityForResult(intent, 43616);
        } else if (id == 109) { // New note-source (source citation without reference to a source)
            SourceCitation citation = new SourceCitation();
            citation.setValue("");
            if (object instanceof Note) ((Note)object).addSourceCitation(citation);
            else ((SourceCitationContainer)object).addSourceCitation(citation);
            Memory.add(citation);
            startActivity(new Intent(this, SourceCitationActivity.class));
            toBeSaved = true;
        } else if (id == 110) { // New source
            SourcesFragment.newSource(this, object);
        } else if (id == 111) { // Link existing source
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(Choice.SOURCE, true);
            startActivityForResult(intent, 5065);
        } else if (id == 120 || id == 121) { // Create new family member
            Intent intent = new Intent(this, PersonEditorActivity.class);
            intent.putExtra(Extra.FAMILY_ID, ((Family)object).getId());
            intent.putExtra(Extra.RELATION, Relation.get(id - 118));
            intent.putExtra(Extra.FROM_FAMILY, true);
            startActivity(intent);
        } else if (id == 122 || id == 123) { // Link existing person
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(Choice.PERSON, true);
            intent.putExtra(Extra.RELATION, Relation.get(id - 120));
            startActivityForResult(intent, 34417);
        } else if (id == 130) { // Place marriage event
            EventFact marriage = new EventFact();
            marriage.setTag("MARR");
            marriage.setDate("");
            marriage.setPlace("");
            marriage.setType("");
            ((Family)object).addEventFact(marriage);
            Memory.add(marriage);
            startActivity(new Intent(this, EventActivity.class));
            toBeSaved = true;
        } else if (id == 131) { // Place divorce event
            EventFact divorce = new EventFact();
            divorce.setTag("DIV");
            divorce.setDate("");
            ((Family)object).addEventFact(divorce);
            Memory.add(divorce);
            startActivity(new Intent(this, EventActivity.class));
            toBeSaved = true;
        } else if (id >= 200) { // Place another event
            EventFact event = new EventFact();
            event.setTag(otherEvents.get(id - 200).first);
            ((Family)object).addEventFact(event);
            refresh();
            toBeSaved = true;
        }
        if (toBeSaved) TreeUtil.INSTANCE.save(true, Memory.getLeaderObject());
        return true;
    };

    /**
     * Places what has been chosen in the lists.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            // From the 'Link...' submenu in FAB
            if (requestCode == 34417) { // Family member chosen in PersonsFragment
                Person personToBeAdded = gc.getPerson(data.getStringExtra(Extra.RELATIVE_ID));
                FamilyUtil.INSTANCE.linkPerson(personToBeAdded, (Family)object, (Relation)data.getSerializableExtra(Extra.RELATION));
                TreeUtil.INSTANCE.save(true, personToBeAdded, Memory.getLeaderObject());
                return;
            } else if (requestCode == 5065) { // Source chosen in SourcesFragment
                SourceCitation sourceCitation = new SourceCitation();
                sourceCitation.setRef(data.getStringExtra(Extra.SOURCE_ID));
                if (object instanceof Note) ((Note)object).addSourceCitation(sourceCitation);
                else ((SourceCitationContainer)object).addSourceCitation(sourceCitation);
            } else if (requestCode == 7074) { // Shared note
                NoteRef noteRef = new NoteRef();
                noteRef.setRef(data.getStringExtra(Extra.NOTE_ID));
                ((NoteContainer)object).addNoteRef(noteRef);
            } else if (requestCode == 43616) { // Media from GalleryFragment
                MediaRef mediaRef = new MediaRef();
                mediaRef.setRef(data.getStringExtra(Extra.MEDIA_ID));
                ((MediaContainer)object).addMediaRef(mediaRef);
            } else if (requestCode == 4562) { // Repository selected in RepositoriesFragment
                RepositoryRef archRef = new RepositoryRef();
                archRef.setRef(data.getStringExtra("repoId"));
                ((Source)object).setRepositoryRef(archRef);
            }
            // From the context menu 'Choose...'
            if (requestCode == 5390) { // Sets the repository that has been chosen in the RepositoriesFragment list by RepositoryRefActivity
                ((RepositoryRef)object).setRef(data.getStringExtra("repoId"));
            } else if (requestCode == 7047) { // Sets the source that has been chosen in SourcesFragment by SourceCitationActivity
                ((SourceCitation)object).setRef(data.getStringExtra(Extra.SOURCE_ID));
            }
            TreeUtil.INSTANCE.save(true, Memory.getLeaderObject()); // Since is in a coroutine, could set Global.edited true too late for onResume
            Global.edited = true; // For the following onResume
        }
    }

    private boolean isActivityRestarting; // To call refresh() only onBackPressed()

    @Override
    public void onRestart() {
        super.onRestart();
        isActivityRestarting = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Updates contents when coming back with onBackPressed()
        if (isActivityRestarting && Global.edited) {
            if (Memory.getLastObject() == null) goBack(); // Maybe object was deleted in another activity
            else refresh();
            isActivityRestarting = false;
        }
    }

    protected abstract void format();

    /**
     * Updates the title in the toolbar.
     */
    protected void setTitle() {
    }

    public void delete() {
    }

    /**
     * Reloads the contents of the detail, including the change date.
     */
    public void refresh() {
        box.removeAllViews();
        eggs.clear();
        format();
    }

    // Place the tags slug
    public void placeSlug(String tag) {
        placeSlug(tag, null);
    }

    public void placeSlug(String tag, String id) {
        FlexboxLayout slugLayout = findViewById(R.id.dettaglio_bava);
        if (Global.settings.expert) {
            slugLayout.removeAllViews();
            for (final Memory.Step step : Memory.getLastStack()) {
                View stepView = LayoutInflater.from(this).inflate(R.layout.pezzo_bava, box, false);
                TextView stepText = stepView.findViewById(R.id.bava_goccia);
                if (Memory.getLastStack().indexOf(step) < Memory.getLastStack().size() - 1) {
                    if (step.object instanceof Visitable) // GedcomTag extensions are not Visitable and it is impossible to find the stack of them
                        stepView.setOnClickListener(v -> {
                            new FindStack(gc, step.object, true);
                            startActivity(new Intent(this, Memory.classes.get(step.object.getClass())));
                        });
                } else {
                    step.tag = tag;
                    stepText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                }
                String label = step.tag;
                if (id != null) {
                    label += " " + id; // Id for main records INDI, FAMI, REPO... e.g. 'SOUR S123'
                    stepView.setOnClickListener(v -> {
                        concludeActivePieces();
                        U.editId(this, (ExtensionContainer)object, this::refresh);
                    });
                }
                stepText.setText(label);
                slugLayout.addView(stepView);
            }
        } else
            slugLayout.setVisibility(View.GONE);
    }

    /**
     * Concludes the possible editing of an active piece.
     */
    private void concludeActivePieces() {
        for (int i = 0; i < box.getChildCount(); i++) {
            View piece = box.getChildAt(i);
            EditText editText = piece.findViewById(R.id.event_edit);
            if (editText != null && editText.isShown()) {
                TextView textView = piece.findViewById(R.id.event_text);
                if (!editText.getText().toString().equals(textView.getText().toString())) // If there has been editing
                    save(piece);
                else
                    restore(piece);
            }
        }
    }

    /**
     * Return 'object' casted in the required class,
     * or a new instance of the class, but in this case it immediately goes back.
     * TODO: code smell: no type safety and reflection creating new classes.
     */
    public Object cast(Class<?> aClass) {
        Object casted = null;
        try {
            // If it goes wrong will return a new instance of the class, just to not crash DetailActivity
            if (aClass.equals(GedcomTag.class))
                casted = new GedcomTag(null, null, null);
            else
                casted = aClass.newInstance();
            casted = aClass.cast(object);
        } catch (Exception e) {
            goBack();
        }
        return casted;
    }

    /**
     * A wrapper for every possible item that can be displayed on a 'Details...' activity:
     * the FAB menu items and the editable pieces in the
     */
    class Egg {
        String title;
        Object yolk; // Can be a method string ("Value", "Date", "Type"...) or an Address
        boolean common; // Indicates whether to make it appear in the FAB menu to insert the piece
        int inputType;

        Egg(String title, Object yolk, boolean common, int inputType) {
            this.title = title;
            this.yolk = yolk;
            this.common = common;
            this.inputType = inputType;
            eggs.add(this); // TODO: this is bad form: it relies on the side effect of creating an object, which should naturally be stateless, and makes it unclear from reading the code what should happen, besides for the compiler not necessarily knowing that anything was done with the object, which I would imagine makes garbage collection slower - besides for being bad form.
        }
    }

    /**
     * Attempts to put a basic editable text piece in the layout.
     */
    public void place(String title, String method) {
        place(title, method, true, 0);
    }

    public void place(String title, String method, boolean common, int inputType) {
        new Egg(title, method, common, inputType);
        String text;
        try {
            text = (String)object.getClass().getMethod("get" + method).invoke(object); // TODO: this reflection is bad performance
        } catch (Exception e) {
            text = "ERROR: " + e.getMessage();
        }
        // Value 'Y' is hidden for non-experts
        if (!Global.settings.expert && object instanceof EventFact && method.equals("Value")
                && text != null && text.equals("Y")) {
            String tag = ((EventFact)object).getTag();
            if (tag != null && (tag.equals("BIRT") || tag.equals("CHR") || tag.equals("DEAT")
                    || tag.equals("MARR") || tag.equals("DIV")))
                return;
        }
        placePiece(title, text, method, inputType);
    }

    /**
     * Places this address in the layout. [place] is implemented with different signatures to
     * accommodate various types of objects being placed.
     */
    public void place(String title, Address address) {
        Address addressNotNull = address == null ? new Address() : address;
        new Egg(title, addressNotNull, true, 0);
        // TODO: simplify in Kotlin: address?.toString(false)
        placePiece(title, address == null ? null : AddressUtilKt.toString(address, false), addressNotNull, 0);
    }

    /**
     * @param event Events of {@link FamilyActivity}
     */
    public void place(String title, EventFact event) {
        EventFact eventNotNull = event == null ? new EventFact() : event;
        placePiece(title, event == null ? null : EventUtilKt.writeContent(event), eventNotNull, 0);
    }

    public View placePiece(String title, String text, Object object, int inputType) {
        if (text == null) return null;
        View pieceView = getLayoutInflater().inflate(R.layout.event_item, box, false);
        box.addView(pieceView);
        ((TextView)pieceView.findViewById(R.id.event_title)).setText(title);
        ((TextView)pieceView.findViewById(R.id.event_text)).setText(text);
        EditText editText = pieceView.findViewById(R.id.event_edit);
        if (inputType > 0) {
            // Multi line is always capital sentences too
            if (inputType == InputType.TYPE_TEXT_FLAG_MULTI_LINE)
                inputType |= InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
            editText.setInputType(inputType);
        } else // Default edit text is single line capital sentences
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        View.OnClickListener click = null;
        if (object instanceof Integer) { // Given name or surname in non-expert mode
            click = this::edit;
        } else if (object instanceof String) { // Method
            click = this::edit;
            // If it is a date
            if (object.equals("Date")) {
                dateEditor = pieceView.findViewById(R.id.event_date);
                editText.setText(text); // To pass the date to DateEditorLayout
                dateEditor.initialize(editText);
            }
        } else if (object instanceof Address) { // Address
            click = v -> {
                Memory.add(object);
                startActivity(new Intent(this, AddressActivity.class));
            };
        } else if (object instanceof EventFact) { // Event
            click = v -> {
                Memory.add(object);
                startActivity(new Intent(this, EventActivity.class));
            };
            // Family EventFacts can have notes, media and source count
            LinearLayout noteLayout = pieceView.findViewById(R.id.event_other);
            NoteUtil.INSTANCE.placeNotes(noteLayout, (NoteContainer)object, false);
            U.placeMedia(noteLayout, (MediaContainer)object, false);
            if (Global.settings.expert) {
                List<SourceCitation> sourceCitations = ((SourceCitationContainer)object).getSourceCitations();
                TextView sourceView = pieceView.findViewById(R.id.event_sources);
                if (!sourceCitations.isEmpty()) {
                    sourceView.setText(String.valueOf(sourceCitations.size()));
                    sourceView.setVisibility(View.VISIBLE);
                }
            }
        } else if (object instanceof GedcomTag) { // Extension
            click = v -> {
                Memory.add(object);
                startActivity(new Intent(this, ExtensionActivity.class));
            };
        }
        pieceView.setOnClickListener(click);
        registerForContextMenu(pieceView);
        pieceView.setTag(R.id.tag_object, object); // Serves various processes to recognize the piece
        return pieceView;
    }

    public void placeExtensions(ExtensionContainer container) {
        for (Extension extension : U.findExtensions(container)) {
            placePiece(extension.name, extension.text, extension.gedcomTag, 0);
        }
    }

    /**
     * Adds to the Layout a box containing a list of assorted items linked to related detail activities.
     *
     * @param object Can be a single or an array of GEDCOM records
     * @param title  Title above the cabinet
     */
    protected void placeCabinet(Object object, int title) {
        View cabinetView = getLayoutInflater().inflate(R.layout.cabinet_layout, box, false);
        TextView titleView = cabinetView.findViewById(R.id.cabinet_title);
        titleView.setText(title);
        box.addView(cabinetView);
        LinearLayout cabinetBox = cabinetView.findViewById(R.id.cabinet_box);
        if (object instanceof Object[]) {
            for (Object obj : (Object[])object)
                placeObject(cabinetBox, obj);
        } else
            placeObject(cabinetBox, object);
    }

    /**
     * Recognizes the record type and adds the appropriate link to the cabinet.
     */
    private void placeObject(LinearLayout layout, Object record) {
        if (record instanceof Person) {
            View personView = PersonUtil.INSTANCE.placeSmallPerson(layout, (Person)record);
            personView.setOnClickListener(view -> {
                Memory.setLeader(record);
                Intent intent = new Intent(this, ProfileActivity.class);
                intent.putExtra(Extra.PAGE, 1);
                startActivity(intent);
            });
            personView.setTag(R.id.tag_object, record);
            registerForContextMenu(personView);
        } else if (record instanceof Family)
            FamilyUtil.INSTANCE.placeFamily(layout, (Family)record);
        else if (record instanceof Media)
            placeMedia(layout, (Media)record);
        else if (record instanceof Note)
            NoteUtil.INSTANCE.placeNote(layout, (Note)record, null, true);
        else if (record instanceof Source)
            SourceUtil.INSTANCE.placeSource(layout, (Source)record, false);
        else if (record instanceof Repository)
            RepositoryUtil.INSTANCE.placeRepository(layout, (Repository)record);
        else if (record instanceof Submitter)
            placeSubmitter(layout, (Submitter)record); // TODO I think this is never used
    }

    private void placeMedia(LinearLayout layout, Media media) {
        View mediaView = LayoutInflater.from(this).inflate(R.layout.media_layout, layout, false);
        layout.addView(mediaView);
        MediaUtil.INSTANCE.furnishMedia(media, mediaView.findViewById(R.id.media_caption), mediaView.findViewById(R.id.media_number));
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)mediaView.getLayoutParams();
        params.height = U.dpToPx(80);
        FileUtil.INSTANCE.showImage(media, mediaView.findViewById(R.id.media_image), 0, mediaView.findViewById(R.id.media_progress));
        mediaView.setOnClickListener(v -> {
            Memory.setLeader(media);
            startActivity(new Intent(this, MediaActivity.class));
        });
    }

    // TODO: Probably never used, may be deleted
    private void placeSubmitter(LinearLayout layout, Submitter submitter) {
        View view = LayoutInflater.from(this).inflate(R.layout.note_layout, layout, false);
        layout.addView(view);
        TextView textView = view.findViewById(R.id.note_text);
        textView.setText(submitter.getName());
        view.findViewById(R.id.note_sources).setVisibility(View.GONE);
        view.setOnClickListener(v -> {
            Memory.setLeader(submitter);
            startActivity(new Intent(this, SubmitterActivity.class));
        });
    }

    /**
     * Delete an address from the 3 possible containers.
     */
    public void deleteAddress(Object container) {
        if (container instanceof EventFact)
            ((EventFact)container).setAddress(null);
        else if (container instanceof Repository)
            ((Repository)container).setAddress(null);
        else if (container instanceof Submitter)
            ((Submitter)container).setAddress(null);
    }

    /**
     * Composes the title of family events.
     */
    public String writeEventTitle(Family family, EventFact event) {
        String tit;
        switch (event.getTag()) {
            case "MARR":
                tit = U.areMarried(family) ? getString(R.string.marriage) : getString(R.string.relationship);
                break;
            case "DIV":
                tit = U.areMarried(family) ? getString(R.string.divorce) : getString(R.string.separation);
                break;
            case "EVEN":
                tit = getString(R.string.event);
                break;
            case "RESI":
                tit = getString(R.string.residence);
                break;
            default:
                tit = event.getDisplayType();
        }
        if (event.getType() != null && !event.getType().isEmpty() && !event.getType().equals("marriage"))
            tit += " (" + TypeView.getTranslatedType(event.getType(), TypeView.Combo.RELATIONSHIP) + ")";
        return tit;
    }

    EditText editText;
    int whichMenu = 1; // Used to hide the options menu when entering editor mode TODO: replace with a boolean as 'editMode'

    void edit(View pieceView) {
        concludeActivePieces();
        TextView textView = pieceView.findViewById(R.id.event_text);
        textView.setVisibility(View.GONE);
        fabView.hide();
        Object pieceObject = pieceView.getTag(R.id.tag_object);
        boolean showInput = false;
        editText = pieceView.findViewById(R.id.event_edit);
        ViewGroup.LayoutParams params = editText.getLayoutParams();
        // Place
        if (pieceObject.equals("Place")) {
            showInput = true;
            // If it hasn't already done so, it replaces EditText with PlaceFinderTextView
            if (!(editText instanceof PlaceFinderTextView)) {
                ViewGroup parent = (ViewGroup)pieceView;
                int index = parent.indexOfChild(editText);
                parent.removeView(editText);
                editText = new PlaceFinderTextView(this, null);
                editText.setId(R.id.event_edit);
                parent.addView(editText, index, params);
            } else
                editText.setVisibility(View.VISIBLE);
        } // Name type
        else if (object instanceof Name && pieceObject.equals("Type")) {
            if (!(editText instanceof TypeView)) {
                ViewGroup parent = (ViewGroup)pieceView;
                parent.removeView(editText);
                editText = new TypeView(editText.getContext(), TypeView.Combo.NAME);
                parent.addView(editText, parent.indexOfChild(editText), params);
            } else
                editText.setVisibility(View.VISIBLE);
        } // Marriage/relationship type
        else if (object instanceof EventFact && pieceObject.equals("Type") && ((EventFact)object).getTag().equals("MARR")) {
            if (!(editText instanceof TypeView)) {
                ViewGroup parent = (ViewGroup)pieceView;
                parent.removeView(editText);
                editText = new TypeView(editText.getContext(), TypeView.Combo.RELATIONSHIP);
                parent.addView(editText, parent.indexOfChild(editText), params);
            } else
                editText.setVisibility(View.VISIBLE);
        } // Date
        else if (pieceObject.equals("Date")) {
            editText.setVisibility(View.VISIBLE);
        } // All other normal editing cases
        else {
            showInput = true;
            editText.setVisibility(View.VISIBLE);
        }
        CharSequence text = textView.getText();
        editText.setText(text);
        editText.requestFocus(); // In case of phrase date, parentheses will be removed
        editText.setSelection(editText.getText().length()); // Cursor at the end
        if (showInput) {
            editText.postDelayed(() -> {
                InputMethodManager input = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                input.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
            }, 100);
        }

        // Intercepts the 'Done' and 'Next' on the keyboard
        editText.setOnEditorActionListener((view, actionId, keyEvent) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                save(pieceView);
            } else if (actionId == EditorInfo.IME_ACTION_NEXT) {
                if (!editText.getText().toString().equals(textView.getText().toString()))
                    save(pieceView);
                else
                    restore(pieceView);
                View nextPiece = box.getChildAt(box.indexOfChild(pieceView) + 1);
                if (nextPiece != null && nextPiece.getTag(R.id.tag_object) instanceof String)
                    edit(nextPiece);
            }
            return false;
        });

        // Custom ActionBar
        actionBar.setDisplayHomeAsUpEnabled(false); // Hides the back arrow
        whichMenu = 0;
        invalidateOptionsMenu();
        View editBar = getLayoutInflater().inflate(R.layout.barra_edita, new LinearLayout(box.getContext()), false);
        editBar.findViewById(R.id.edita_annulla).setOnClickListener(v -> {
            editText.setText(textView.getText());
            restore(pieceView);
        });
        editBar.findViewById(R.id.edita_salva).setOnClickListener(v -> save(pieceView));
        actionBar.setCustomView(editBar);
        actionBar.setDisplayShowCustomEnabled(true);
    }

    void save(View pieceView) {
        Object pieceObject = pieceView.getTag(R.id.tag_object);
        if (pieceObject.equals("Date"))
            dateEditor.finishEditing();
        String text = editText.getText().toString().trim();
        if (pieceObject instanceof Integer) { // Saves given name and surname on non-expert mode
            String givenName = "";
            String surname = "";
            // Editing given name
            if (pieceObject.equals(4043)) {
                givenName = text.replaceAll("/", ""); // Character "/" is reserved to identify surname
                surname = ((TextView)box.getChildAt(1).findViewById(R.id.event_text)).getText().toString();
            } // Editing surname
            else if (pieceObject.equals(6064)) {
                givenName = ((TextView)box.getChildAt(0).findViewById(R.id.event_text)).getText().toString();
                surname = text.replaceAll("/", ""); // TODO: Should be better to PREVENT insertion of "/" (e.g. with InputFilter)
            }
            String value = givenName;
            if (!surname.isEmpty()) {
                if (surnameBefore) value = "/" + surname + "/ " + value;
                else value += " /" + surname + "/";
            }
            ((Name)object).setValue(value.trim());
        } else try { // All other normal methods
            object.getClass().getMethod("set" + pieceObject, String.class).invoke(object, text); // TODO: reflection
        } catch (Exception e) {
            Toast.makeText(box.getContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            return; // In case of error it remains in editor mode
        }
        ((TextView)pieceView.findViewById(R.id.event_text)).setText(text);
        restore(pieceView);
        TreeUtil.INSTANCE.save(true, Memory.getLeaderObject());
		/*if( Memory.getStepStack().size() == 1 ) {
			refresh(); // TODO: The record change date should be updated, but perhaps without reloading everything
		}*/
        // Refreshes the image in MediaActivity if the File path has been edited
        if (this instanceof MediaActivity && pieceObject.equals("File"))
            ((MediaActivity)this).updateImage();
            // If a submitter has been edited, asks to reference him in the Gedcom header
        else if (object instanceof Submitter)
            U.autorePrincipale(this, ((Submitter)object).getId());
        else if (this instanceof NameActivity || this instanceof EventActivity)
            setTitle();
    }

    /**
     * Operations common to Save and Cancel
     */
    void restore(View pieceView) {
        InputMethodManager input = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        input.hideSoftInputFromWindow(editText.getWindowToken(), 0);
        editText.setVisibility(View.GONE);
        pieceView.findViewById(R.id.event_date).setVisibility(View.GONE);
        pieceView.findViewById(R.id.event_text).setVisibility(View.VISIBLE);
        actionBar.setDisplayShowCustomEnabled(false); // Hides custom toolbar
        actionBar.setDisplayHomeAsUpEnabled(true);
        whichMenu = 1;
        invalidateOptionsMenu();
        if (!(object instanceof Note && !Global.settings.expert)) // NoteActivity on non-expert mode has no FAB
            fabView.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (whichMenu == 1) { // Standard bar menu
            if (object instanceof Submitter && (gc.getHeader() == null || // Non-main submitter
                    gc.getHeader().getSubmitter(gc) == null || !gc.getHeader().getSubmitter(gc).equals(object)))
                menu.add(0, 1, 0, R.string.make_default);
            if (object instanceof Media) {
                menu.add(0, 5, 0, R.string.choose_file);
                Media media = (Media)object;
                if (media.getId() != null) {
                    MediaReferences mediaReferences = new MediaReferences(gc, media, false);
                    if (mediaReferences.num > 0)
                        menu.add(0, 6, 0, R.string.make_media);
                } else menu.add(0, 7, 0, R.string.make_shared_media);
            }
            if (object instanceof Family)
                menu.add(0, 10, 0, R.string.delete);
            else if (!(object instanceof Submitter && U.submitterHasShared((Submitter)object))) // Submitter who shared cannot be deleted
                menu.add(0, 15, 0, R.string.delete);
        }
        return true;
    }

    /**
     * Saves in Media a file chosen with the apps from MediaActivity
     */
    public ActivityResultLauncher<Intent> chooseMediaLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            if (FileUtil.INSTANCE.setFileAndProposeCropping(this, result.getData(), (Media)object)) {
                TreeUtil.INSTANCE.save(true, Memory.getLeaderObject());
                refresh();
            }
        }
    });

    /**
     * Is called when a menu item is chosen AND by clicking the back arrow
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == 1) { // Main submitter // TODO code smell : magic number
            SubmittersFragment.mainSubmitter((Submitter)object);
        } else if (id == 5) { // Choose image
            FileUtil.INSTANCE.displayFileChooser(this, chooseMediaLauncher);
        } else if (id == 6) { // Make simple media
            Object[] modifiedObjects = MediaUtil.INSTANCE.makeSimpleMedia((Media)object);
            TreeUtil.INSTANCE.save(true, modifiedObjects);
            Memory.makeLastStep(object);
            getIntent().putExtra(Extra.ALONE, true); // To display the cabinet
            refresh();
        } else if (id == 7) { // Make shared media
            Object[] modifiedObjects = MediaUtil.INSTANCE.makeSharedMedia((Media)object);
            TreeUtil.INSTANCE.save(true, modifiedObjects);
            Memory.makeLeaderStep(object);
            refresh();
        } else if (id == 10) { // Delete family
            Family family = (Family)object;
            if (family.getHusbandRefs().size() + family.getWifeRefs().size() + family.getChildRefs().size() > 0) {
                new AlertDialog.Builder(this).setMessage(R.string.really_delete_family)
                        .setPositiveButton(android.R.string.yes, (dialog, i) -> {
                            FamilyUtilKt.delete(family);
                            goBack();
                        }).setNeutralButton(android.R.string.cancel, null).show();
            } else {
                Util.INSTANCE.confirmDelete(this, () -> {
                    FamilyUtilKt.delete(family);
                    goBack();
                    return Unit.INSTANCE;
                });
            }
        } else if (id == 15) { // Delete all other objects
            Util.INSTANCE.confirmDelete(this, () -> {
                delete();
                TreeUtil.INSTANCE.save(true); // Update of change date takes place in the overrides of delete()
                goBack();
                return Unit.INSTANCE;
            });
        } else if (id == android.R.id.home) {
            goBack();
        }
        return true;
    }

    public void goBack() {
        getOnBackPressedDispatcher().onBackPressed();
    }

    // Contextual menu
    View pieceView; // Editable text, notes, citations, media...
    Object pieceObject;
    Person person; // As it is used a lot, we make it a pieceObject in its own right
    int husbandRefIndex; // Husband index between same-sex partners
    int wifeRefIndex; // Wife index between same-sex partners
    int sharedNoteIndex; // Index of the shared note in the container

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo info) { // info is null
        if (whichMenu != 0) { // If we are in edit mode shows the editor menu with 'Cancel' and 'Done' buttons
            pieceView = view;
            pieceObject = view.getTag(R.id.tag_object);
            if (view.getId() == R.id.smallPerson) { // Small person inside cabinet
                person = (Person)pieceObject;
                menu.add(0, 10, 0, R.string.diagram);
                String[] familyLabels = PersonUtilKt.getFamilyLabels(person, this, null);
                if (familyLabels[0] != null)
                    menu.add(0, 12, 0, familyLabels[0]);
                if (familyLabels[1] != null)
                    menu.add(0, 13, 0, familyLabels[1]);
                menu.add(0, 20, 0, R.string.modify);
            } else if (pieceObject instanceof Person) { // Regular person in family
                person = (Person)pieceObject;
                Family fam = (Family)object;
                // Generates labels for "Family..." entries (such as child and partner)
                String[] familyLabels = {null, null};
                if (person.getParentFamilies(gc).size() > 1 && person.getSpouseFamilies(gc).size() > 1) {
                    familyLabels[0] = getString(R.string.family_as_child);
                    familyLabels[1] = getString(R.string.family_as_spouse);
                }
                menu.add(0, 10, 0, R.string.diagram);
                menu.add(0, 11, 0, R.string.card);
                if (familyLabels[0] != null)
                    menu.add(0, 12, 0, familyLabels[0]);
                if (familyLabels[1] != null)
                    menu.add(0, 13, 0, familyLabels[1]);
                boolean homosexual = FamilyUtil.INSTANCE.areSpousesHomosexual(fam);
                husbandRefIndex = fam.getHusbands(gc).indexOf(person);
                wifeRefIndex = fam.getWives(gc).indexOf(person);
                if (homosexual) {
                    if (husbandRefIndex == 0) menu.add(0, 14, 0, R.string.move_down);
                    else if (wifeRefIndex == 0) menu.add(0, 15, 0, R.string.move_up);
                }
                if (husbandRefIndex > 0 || wifeRefIndex > 0)
                    menu.add(0, 16, 0, R.string.move_up);
                if (!homosexual && husbandRefIndex == 0 && fam.getHusbandRefs().size() > 1
                        || husbandRefIndex >= 1 && husbandRefIndex < fam.getHusbandRefs().size() - 1
                        || wifeRefIndex >= 0 && wifeRefIndex < fam.getWifeRefs().size() - 1)
                    menu.add(0, 17, 0, R.string.move_down);
                if (fam.getChildren(gc).indexOf(person) > 0)
                    menu.add(0, 18, 0, R.string.move_before);
                if (fam.getChildren(gc).indexOf(person) < fam.getChildren(gc).size() - 1 && fam.getChildren(gc).contains(person))
                    menu.add(0, 19, 0, R.string.move_after);
                menu.add(0, 20, 0, R.string.modify);
                // TODO: if (person.parentFamilyRefs.any { it.ref == fam.id })
                if (FamilyUtil.INSTANCE.findParentFamilyRef(person, fam) != null)
                    menu.add(0, 21, 0, R.string.lineage);
                menu.add(0, 22, 0, R.string.unlink);
                menu.add(0, 23, 0, R.string.delete);
            } else if (pieceObject instanceof Note) {
                menu.add(0, 25, 0, R.string.copy);
                NoteContainer container = (NoteContainer)object;
                Note note = (Note)pieceObject;
                if (note.getId() != null) { // Shared note
                    sharedNoteIndex = container.getNoteRefs().indexOf(view.getTag(R.id.tag_ref));
                    if (sharedNoteIndex > 0)
                        menu.add(0, 26, 0, R.string.move_up);
                    if (sharedNoteIndex < container.getNoteRefs().size() - 1)
                        menu.add(0, 27, 0, R.string.move_down);
                    menu.add(0, 28, 0, R.string.unlink);
                } else { // Inline note
                    if (container.getNotes().indexOf(note) > 0)
                        menu.add(0, 29, 0, R.string.move_up);
                    if (container.getNotes().indexOf(note) < container.getNotes().size() - 1)
                        menu.add(0, 30, 0, R.string.move_down);
                }
                menu.add(0, 31, 0, R.string.delete);
            } else if (pieceObject instanceof SourceCitation) {
                menu.add(0, 35, 0, R.string.copy);
                List<SourceCitation> sourceCitations;
                if (object instanceof Note) sourceCitations = ((Note)object).getSourceCitations();
                else sourceCitations = ((SourceCitationContainer)object).getSourceCitations();
                if (sourceCitations.indexOf(pieceObject) > 0)
                    menu.add(0, 36, 0, R.string.move_up);
                if (sourceCitations.indexOf(pieceObject) < sourceCitations.size() - 1)
                    menu.add(0, 37, 0, R.string.move_down);
                menu.add(0, 38, 0, R.string.delete);
            } else if (pieceObject instanceof Media) {
                if (((Media)pieceObject).getId() != null) {
                    menu.add(0, 40, 0, R.string.make_media);
                    menu.add(0, 41, 0, R.string.unlink);
                } else menu.add(0, 42, 0, R.string.make_shared_media);
                menu.add(0, 43, 0, R.string.delete);
            } else if (pieceObject instanceof Address) {
                menu.add(0, 50, 0, R.string.copy);
                menu.add(0, 51, 0, R.string.delete);
            } else if (pieceObject instanceof EventFact) {
                menu.add(0, 55, 0, R.string.copy);
                Family fam = (Family)object;
                if (fam.getEventsFacts().indexOf(pieceObject) > 0)
                    menu.add(0, 56, 0, R.string.move_up);
                if (fam.getEventsFacts().contains(pieceObject)
                        && fam.getEventsFacts().indexOf(pieceObject) < fam.getEventsFacts().size() - 1)
                    menu.add(0, 57, 0, R.string.move_down);
                menu.add(0, 58, 0, R.string.delete);
            } else if (pieceObject instanceof GedcomTag) {
                menu.add(0, 60, 0, R.string.copy);
                menu.add(0, 61, 0, R.string.delete);
            } else if (pieceObject instanceof Source) {
                menu.add(0, 70, 0, R.string.copy);
                menu.add(0, 71, 0, R.string.choose_source);
            } else if (pieceObject instanceof RepositoryRef) {
                menu.add(0, 80, 0, R.string.copy);
                menu.add(0, 81, 0, R.string.delete);
            } else if (pieceObject instanceof Repository) {
                menu.add(0, 90, 0, R.string.copy);
                menu.add(0, 91, 0, R.string.choose_repository);
            } else if (pieceObject instanceof Integer) {
                if (pieceObject.equals(43614)) { // The image in MediaActivity
                    menu.add(0, 100, 0, R.string.choose_file);
                } else if (pieceObject.equals(4043) || pieceObject.equals(6064)) // Name and surname for non-expert
                    menu.add(0, 0, 0, R.string.copy);
            } else if (pieceObject instanceof String) {
                if (((TextView)view.findViewById(R.id.event_text)).getText().length() > 0)
                    menu.add(0, 0, 0, R.string.copy);
                if (pieceObject.equals("File") && ((MediaActivity)this).fileUri.getTreeDirFilename())
                    menu.add(0, 1, 0, R.string.shrink_path);
                menu.add(0, 2, 0, R.string.delete);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // TODo all deletes require deletion confirmation
            // Copy
            case 0: // Editable piece
            case 50: // Address
            case 55: // Event
            case 60: // Extension
                U.copyToClipboard(((TextView)pieceView.findViewById(R.id.event_title)).getText(),
                        ((TextView)pieceView.findViewById(R.id.event_text)).getText());
                return true;
            case 1: // Shrink Media file path
                String filename = ((MediaActivity)this).fileUri.getName();
                ((MediaActivity)this).media.setFile(filename);
                break;
            case 2: // Delete editable piece
                Util.INSTANCE.confirmDelete(this, () -> {
                    try {
                        object.getClass().getMethod("set" + pieceObject, String.class).invoke(object, (Object)null);
                        TreeUtil.INSTANCE.save(true, Memory.getLeaderObject());
                        refresh();
                    } catch (Exception exception) {
                        Toast.makeText(box.getContext(), exception.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    }
                    return Unit.INSTANCE;
                });
                return true;
            case 10: // Diagram
                U.whichParentsToShow(this, person, 1);
                return true;
            case 11: // Person profile
                Memory.setLeader(person);
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            case 12: // Family (as child)
                U.whichParentsToShow(this, person, 2);
                return true;
            case 13: // Family (as partner)
                U.whichSpousesToShow(this, person);
                return true;
            case 14: // Swap husband and wife
            case 15:
                swapSpouses();
                break;
            case 16: // Move partner up
                swapPartners(-1);
                break;
            case 17: // Move partner down
                swapPartners(1);
                break;
            case 18: // Move child before
                swapChildren(-1);
                break;
            case 19: // Move child after
                swapChildren(1);
                break;
            case 20: // Edit
                Intent intent = new Intent(this, PersonEditorActivity.class);
                intent.putExtra(Extra.PERSON_ID, person.getId());
                startActivity(intent);
                return true;
            case 21: // Lineage
                PersonUtil.INSTANCE.chooseLineage(this, person, (Family)object);
                break;
            case 22: // Unlink family member
                FamilyUtil.INSTANCE.unlinkRefs((SpouseFamilyRef)pieceView.getTag(R.id.tag_family_ref),
                        (SpouseRef)pieceView.getTag(R.id.tag_ref));
                ChangeUtil.INSTANCE.updateChangeDate(person);
                FamilyUtil.INSTANCE.updateSpouseRoles((Family)object);
                findAnotherRepresentativeOfTheFamily(person);
                break;
            case 23: // Delete family member
                Util.INSTANCE.confirmDelete(this, () -> {
                    PersonUtilKt.delete(person);
                    findAnotherRepresentativeOfTheFamily(person);
                    refresh();
                    return Unit.INSTANCE;
                });
                return true;
            case 25: // Copy note text
                U.copyToClipboard(getText(R.string.note), ((TextView)pieceView.findViewById(R.id.note_text)).getText());
                return true;
            case 26: // Move up shared note
                swapSharedNotes(-1);
                break;
            case 27: // Move down shared note
                swapSharedNotes(1);
                break;
            case 28: // Unlink shared note
                ((NoteContainer)object).getNoteRefs().remove(sharedNoteIndex);
                break;
            case 29: // Move up inline note
                swapNotes(-1);
                break;
            case 30: // Move down inline note
                swapNotes(1);
                break;
            case 31: // Delete note
                Util.INSTANCE.confirmDelete(this, () -> {
                    Object[] leaders = U.deleteNote((Note)pieceObject);
                    TreeUtil.INSTANCE.save(true, leaders);
                    refresh();
                    return Unit.INSTANCE;
                });
                return true;
            case 35: // Copy source citation
                U.copyToClipboard(getText(R.string.source_citation),
                        ((TextView)pieceView.findViewById(R.id.source_text)).getText() + "\n"
                                + ((TextView)pieceView.findViewById(R.id.sourceCitation_text)).getText());
                return true;
            case 36: // Move up source citation
                List<SourceCitation> sourceCitations1;
                if (object instanceof Note) sourceCitations1 = ((Note)object).getSourceCitations();
                else sourceCitations1 = ((SourceCitationContainer)object).getSourceCitations();
                int index1 = sourceCitations1.indexOf(pieceObject);
                Collections.swap(sourceCitations1, index1, index1 - 1);
                break;
            case 37: // Move down source citation
                List<SourceCitation> sourceCitations2;
                if (object instanceof Note) sourceCitations2 = ((Note)object).getSourceCitations();
                else sourceCitations2 = ((SourceCitationContainer)object).getSourceCitations();
                int index2 = sourceCitations2.indexOf(pieceObject);
                Collections.swap(sourceCitations2, index2, index2 + 1);
                break;
            case 38: // Delete source citation
                Util.INSTANCE.confirmDelete(this, () -> {
                    if (object instanceof Note) ((Note)object).getSourceCitations().remove(pieceObject);
                    else ((SourceCitationContainer)object).getSourceCitations().remove(pieceObject);
                    Memory.setInstanceAndAllSubsequentToNull(pieceObject);
                    TreeUtil.INSTANCE.save(true, Memory.getLeaderObject());
                    refresh();
                    return Unit.INSTANCE;
                });
                return true;
            case 40: // Make simple media
                Object[] modified = MediaUtil.INSTANCE.makeSimpleMedia((Media)pieceObject);
                TreeUtil.INSTANCE.save(true, modified);
                Memory.setInstanceAndAllSubsequentToNull(pieceObject);
                refresh();
                return true;
            case 41: // Unlink shared media
                MediaUtilKt.unlinkMedia((MediaContainer)object, ((Media)pieceObject).getId());
                break;
            case 42: // Make shared media
                Object[] modified1 = MediaUtil.INSTANCE.makeSharedMedia((Media)pieceObject);
                TreeUtil.INSTANCE.save(true, modified1);
                Memory.setInstanceAndAllSubsequentToNull(pieceObject);
                refresh();
                return true;
            case 43: // Delete media
                Util.INSTANCE.confirmDelete(this, () -> {
                    Object[] modified2 = MediaUtil.INSTANCE.deleteMedia((Media)pieceObject);
                    TreeUtil.INSTANCE.save(true, modified2);
                    refresh();
                    return Unit.INSTANCE;
                });
                return true;
            case 51: // Delete address
                deleteAddress(object);
                break;
            case 56: // Move up family event
                int index3 = ((Family)object).getEventsFacts().indexOf(pieceObject);
                Collections.swap(((Family)object).getEventsFacts(), index3, index3 - 1);
                break;
            case 57: // Move down family event
                int index4 = ((Family)object).getEventsFacts().indexOf(pieceObject);
                Collections.swap(((Family)object).getEventsFacts(), index4, index4 + 1);
                break;
            case 58: // Delete family event
                ((Family)object).getEventsFacts().remove(pieceObject);
                Memory.setInstanceAndAllSubsequentToNull(pieceObject);
                break;
            case 61: // Delete extension
                U.deleteExtension((GedcomTag)pieceObject, object, null);
                break;
            case 70: // Copy source text
                U.copyToClipboard(getText(R.string.source), ((TextView)pieceView.findViewById(R.id.source_text)).getText());
                return true;
            case 71: // Choose source in SourcesFragment
                Intent intent1 = new Intent(this, MainActivity.class);
                intent1.putExtra(Choice.SOURCE, true);
                startActivityForResult(intent1, 7047);
                return true;
            case 80: // Copy repository citation text
                U.copyToClipboard(getText(R.string.repository_citation),
                        ((TextView)pieceView.findViewById(R.id.source_text)).getText() + "\n"
                                + ((TextView)pieceView.findViewById(R.id.sourceCitation_text)).getText());
                return true;
            case 81: // Delete repository citation
                ((Source)object).setRepositoryRef(null);
                Memory.setInstanceAndAllSubsequentToNull(pieceObject);
                break;
            case 90: // Copy repository text
                U.copyToClipboard(getText(R.string.repository), ((TextView)pieceView.findViewById(R.id.source_text)).getText());
                return true;
            case 91: // Choose repository in RepositoriesFragment
                Intent intent2 = new Intent(this, MainActivity.class);
                intent2.putExtra(Choice.REPOSITORY, true);
                startActivityForResult(intent2, 5390);
                return true;
            case 100: // Choose image
                FileUtil.INSTANCE.displayFileChooser(this, chooseMediaLauncher);
                return true;
            default:
                return false;
        }
        TreeUtil.INSTANCE.save(true, Memory.getLeaderObject());
        refresh();
        return true;
    }

    /**
     * Swaps husband and wife refs.
     */
    private void swapSpouses() {
        Family family = (Family)object;
        SpouseRef husbandRef = family.getHusbandRefs().remove(0);
        SpouseRef wifeRef = family.getWifeRefs().remove(0);
        family.getWifeRefs().add(0, husbandRef);
        family.getHusbandRefs().add(0, wifeRef);
    }

    /**
     * Swaps position of same-role partners.
     */
    private void swapPartners(int direction) {
        Family family = (Family)object;
        if (husbandRefIndex >= 0) {
            Collections.swap(family.getHusbandRefs(), husbandRefIndex, husbandRefIndex + direction);
        } else if (wifeRefIndex >= 0) {
            Collections.swap(family.getWifeRefs(), wifeRefIndex, wifeRefIndex + direction);
        }
    }

    private void swapChildren(int direction) {
        Family family = (Family)object;
        int index = family.getChildren(gc).indexOf(person);
        Collections.swap(family.getChildRefs(), index, index + direction);
    }

    private void swapSharedNotes(int direction) {
        Collections.swap(((NoteContainer)object).getNoteRefs(), sharedNoteIndex, sharedNoteIndex + direction);
    }

    private void swapNotes(int direction) {
        int index = ((NoteContainer)object).getNotes().indexOf(pieceObject);
        Collections.swap(((NoteContainer)object).getNotes(), index, index + direction);
    }

    /**
     * Fixes 'oneFamilyMember' to correctly show "Link existing person" in the menu.
     */
    private void findAnotherRepresentativeOfTheFamily(Person person) {
        if (oneFamilyMember.equals(person)) {
            Family fam = (Family)object;
            if (!fam.getHusbands(gc).isEmpty())
                oneFamilyMember = fam.getHusbands(gc).get(0);
            else if (!fam.getWives(gc).isEmpty())
                oneFamilyMember = fam.getWives(gc).get(0);
            else if (!fam.getChildren(gc).isEmpty())
                oneFamilyMember = fam.getChildren(gc).get(0);
            else
                oneFamilyMember = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // When activity goes on background, saves data that could be on editing
        concludeActivePieces();
        if (object instanceof EventFact && EventUtilKt.cleanUpFields((EventFact)object)) {
            TreeUtil.INSTANCE.save(true, Memory.getLeaderObject());
        }
    }
}
