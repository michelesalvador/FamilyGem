package app.familygem;

import static app.familygem.Global.gc;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.theartofdev.edmodo.cropper.CropImage;

import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaRef;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SourceCitation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.familygem.constant.Choice;
import app.familygem.constant.Extra;
import app.familygem.constant.Gender;
import app.familygem.constant.Image;
import app.familygem.constant.Relation;
import app.familygem.constant.Type;
import app.familygem.detail.EventActivity;
import app.familygem.detail.MediaActivity;
import app.familygem.detail.NameActivity;
import app.familygem.detail.NoteActivity;
import app.familygem.main.MainActivity;
import app.familygem.main.MediaFragment;
import app.familygem.main.SourcesFragment;
import app.familygem.util.FamilyUtil;
import app.familygem.util.FileUtil;
import app.familygem.util.NoteUtil;
import app.familygem.util.PersonUtilKt;
import app.familygem.util.TreeUtil;
import app.familygem.visitor.FindStack;

public class ProfileActivity extends AppCompatActivity {

    private Person one;
    private TabLayout tabLayout;
    private final Fragment[] pages = new Fragment[3];
    private PagesAdapter adapter;
    private FloatingActionButton fabView;
    private final String[] mainEventTags = {"BIRT", "CHR", "RESI", "OCCU", "DEAT", "BURI"};
    private List<Pair<String, String>> otherEvents; // List of tag + label
    ActivityResultLauncher<Intent> choosePersonLauncher;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.profile_activity);
        fabView = findViewById(R.id.fab);
        // Toolbar
        Toolbar toolbar = findViewById(R.id.profile_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Brings up the back arrow and the options menu
        // Assigns to the pager an adapter that manages the three pages
        ViewPager2 viewPager = findViewById(R.id.profile_pager);
        adapter = new PagesAdapter(this);
        viewPager.setAdapter(adapter);
        // Furnishes tab layout
        tabLayout = findViewById(R.id.profile_tabs);
        tabLayout.addTab(tabLayout.newTab().setText(R.string.media));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.events));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.relatives));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            // Changes tab when swiping
            @Override
            public void onPageSelected(int position) {
                tabLayout.selectTab(tabLayout.getTabAt(position));
            }

            // Animates the FAB
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                // positionOffset is 0.0 > 1.0 during swiping, and becomes 0.0 at the end
                if (positionOffset > 0) fabView.hide();
                else fabView.show();
            }
        });
        // Only at first creation selects one specific tab
        if (bundle == null) tabLayout.getTabAt(getIntent().getIntExtra(Extra.PAGE, 1)).select();

        // List of other events
        String[] otherEventTags = {"CREM", "ADOP", "BAPM", "BARM", "BATM", "BLES", "CONF", "FCOM", "ORDN", //Events
                "NATU", "EMIG", "IMMI", "CENS", "PROB", "WILL", "GRAD", "RETI", "EVEN",
                "CAST", "DSCR", "EDUC", "NATI", "NCHI", "PROP", "RELI", "SSN", "TITL", // Attributes
                "_MILT"}; // User-defined
        // Standard GEDCOM tags missing in the EventFact.DISPLAY_TYPE list: BASM (there is BATM instead) CHRA IDNO NMR FACT
        otherEvents = new ArrayList<>();
        for (String tag : otherEventTags) {
            EventFact event = new EventFact();
            event.setTag(tag);
            String label = event.getDisplayType();
            if (Global.settings.expert)
                label += " — " + tag;
            otherEvents.add(new Pair<>(tag, label));
        }
        // Alphabetically sorted by label
        Collections.sort(otherEvents, (item1, item2) -> item1.second.compareTo(item2.second));

        // Manages the result of choosing a person in PersonsFragment
        choosePersonLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == AppCompatActivity.RESULT_OK) {
                Intent data = result.getData();
                Object[] modified = PersonEditorActivity.addRelative(
                        data.getStringExtra(Extra.PERSON_ID), // Corresponds to one.getId()
                        data.getStringExtra(Extra.RELATIVE_ID),
                        data.getStringExtra(Extra.FAMILY_ID),
                        (Relation)data.getSerializableExtra(Extra.RELATION),
                        data.getStringExtra(Extra.DESTINATION));
                TreeUtil.INSTANCE.save(true, modified);
                refresh(); // To display the result in Relatives fragment
            }
        });
        // One person
        one = (Person)Memory.getLeaderObject();
        if (TreeUtil.INSTANCE.isGlobalGedcomOk(() -> {
            setOne(bundle);
            refresh();
        })) setOne(bundle);
    }

    /**
     * Tries to set the person that is displayed here in profile.
     */
    private void setOne(Bundle bundle) {
        // If the app goes to background and is stopped, 'Memory' is reset and therefore 'one' will be null
        if (one == null && bundle != null) {
            String oneId = bundle.getString("oneId");
            if (oneId != null) { // Rarely the bundle doesn't do its job
                Memory.setLeader(one); // Otherwise Memory is without a stack
                one = gc.getPerson(oneId);
            }
        }
        if (one != null) Global.indi = one.getId();
    }

    private class PagesAdapter extends FragmentStateAdapter {
        private final int PAGES_NUM = 3;
        private int itemIdIncrement = PAGES_NUM;

        public PagesAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0)
                pages[0] = new ProfileMediaFragment();
            else if (position == 1)
                pages[1] = new ProfileFactsFragment();
            else if (position == 2)
                pages[2] = new ProfileRelativesFragment();
            return pages[position];
        }

        @Override
        public int getItemCount() {
            return PAGES_NUM;
        }

        @Override
        public long getItemId(int position) {
            long id = super.getItemId(position);
            if (itemIdIncrement > PAGES_NUM) id += itemIdIncrement;
            return id;
        }

        @Override
        public boolean containsItem(long itemId) {
            return super.containsItem(itemId);
        }

        /**
         * Reloads the three pages.
         */
        public void notifyPagesChanged() {
            itemIdIncrement += PAGES_NUM;
            for (int i = 0; i < 3; i++) {
                notifyItemChanged(i);
            }
        }
    }

    public Fragment getPageFragment(int page) {
        Fragment fragment = pages[page];
        if (fragment == null) fragment = adapter.createFragment(page);
        return fragment;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if ((one == null || Global.edited) && gc != null) one = gc.getPerson(Global.indi);
        if (one == null) { // Coming back to the profile of a person who has been deleted
            onBackPressed();
            return;
        }
        // Person ID in the header
        TextView idView = findViewById(R.id.profile_id);
        if (Global.settings.expert) {
            idView.setText("INDI " + one.getId());
            idView.setOnClickListener(v -> {
                U.editId(this, one, this::refresh);
            });
        } else idView.setVisibility(View.GONE);
        // Person name in the header
        CollapsingToolbarLayout toolbarLayout = findViewById(R.id.profile_toolbar_layout);
        toolbarLayout.setTitle(U.properName(one));
        toolbarLayout.setExpandedTitleTextAppearance(R.style.AppTheme_ExpandedAppBar);
        toolbarLayout.setCollapsedTitleTextAppearance(R.style.AppTheme_CollapsedAppBar);
        toolbarLayout.setOnClickListener(v -> { // Not only the name view but all the expanded toolbar
            if (!one.getNames().isEmpty()) {
                Memory.add(one.getNames().get(0));
                startActivity(new Intent(this, NameActivity.class));
            }
        });
        setImages();
        if (Global.edited) {
            adapter.notifyPagesChanged();
            invalidateOptionsMenu();
        }
        // FAB menu
        fabView.setOnClickListener(view -> {
            if (gc == null) return;
            PopupMenu popup = new PopupMenu(this, view);
            Menu menu = popup.getMenu();
            switch (tabLayout.getSelectedTabPosition()) {
                case 0: // Media
                    menu.add(0, 10, 0, R.string.new_media);
                    menu.add(0, 11, 0, R.string.new_shared_media);
                    if (!gc.getMedia().isEmpty())
                        menu.add(0, 12, 0, R.string.link_shared_media);
                    break;
                case 1: // Facts
                    menu.add(0, 20, 0, R.string.name);
                    // Sex
                    if (Gender.getGender(one) == Gender.NONE)
                        menu.add(0, 21, 0, R.string.sex);
                    // Main events
                    SubMenu eventSubMenu = menu.addSubMenu(R.string.event);
                    CharSequence[] mainEventLabels = {getText(R.string.birth), getText(R.string.christening), getText(R.string.residence),
                            getText(R.string.occupation), getText(R.string.death), getText(R.string.burial)};
                    int i;
                    for (i = 0; i < mainEventLabels.length; i++) {
                        CharSequence label = mainEventLabels[i];
                        if (Global.settings.expert)
                            label += " — " + mainEventTags[i];
                        eventSubMenu.add(0, 40 + i, 0, label);
                    }
                    // Other events
                    SubMenu otherSubMenu = eventSubMenu.addSubMenu(R.string.other);
                    i = 0;
                    for (Pair<String, String> item : otherEvents) {
                        otherSubMenu.add(0, 50 + i, 0, item.second);
                        i++;
                    }
                    SubMenu noteSubMenu = menu.addSubMenu(R.string.note);
                    noteSubMenu.add(0, 22, 0, R.string.new_note);
                    noteSubMenu.add(0, 23, 0, R.string.new_shared_note);
                    if (!gc.getNotes().isEmpty())
                        noteSubMenu.add(0, 24, 0, R.string.link_shared_note);
                    if (Global.settings.expert) {
                        SubMenu sourceSubMenu = menu.addSubMenu(R.string.source);
                        sourceSubMenu.add(0, 25, 0, R.string.new_source);
                        if (!gc.getSources().isEmpty())
                            sourceSubMenu.add(0, 26, 0, R.string.link_source);
                    }
                    break;
                case 2: // Relatives
                    menu.add(0, 30, 0, R.string.new_relative);
                    if (U.linkablePersons(one))
                        menu.add(0, 31, 0, R.string.link_person);
            }
            popup.show();
            popup.setOnMenuItemClickListener(item -> {
                CharSequence[] relatives = {getText(R.string.parent), getText(R.string.sibling), getText(R.string.partner), getText(R.string.child)};
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                switch (item.getItemId()) {
                    case 0: // When a submenu is clicked
                        break;
                    // Media page
                    case 10: // Choose local media
                        F.displayImageCaptureDialog(this, null, 2173, one);
                        break;
                    case 11: // Choose media record
                        F.displayImageCaptureDialog(this, null, 2174, one);
                        break;
                    case 12: // Link media from MediaFragment
                        Intent intent1 = new Intent(this, MainActivity.class);
                        intent1.putExtra(Choice.MEDIA, true);
                        startActivityForResult(intent1, 43614);
                        break;
                    // Facts page
                    case 20: // Create name
                        Name name = new Name();
                        name.setValue("//");
                        one.addName(name);
                        Memory.add(name);
                        startActivity(new Intent(this, NameActivity.class));
                        TreeUtil.INSTANCE.save(true, one);
                        break;
                    case 21: // Create sex
                        String[] sexNames = {getString(R.string.male), getString(R.string.female), getString(R.string.unknown)};
                        new AlertDialog.Builder(tabLayout.getContext())
                                .setSingleChoiceItems(sexNames, -1, (dialog, i) -> {
                                    EventFact gender = new EventFact();
                                    gender.setTag("SEX");
                                    String[] sexValues = {"M", "F", "U"};
                                    gender.setValue(sexValues[i]);
                                    one.addEventFact(gender);
                                    dialog.dismiss();
                                    FamilyUtil.INSTANCE.updateSpouseRoles(one);
                                    refresh();
                                    TreeUtil.INSTANCE.save(true, one);
                                }).show();
                        break;
                    case 22: // Create note
                        Note note = new Note();
                        note.setValue("");
                        one.addNote(note);
                        Memory.add(note);
                        startActivity(new Intent(this, NoteActivity.class));
                        // TODO: maybe make it editable with DetailActivity.edit(value);
                        TreeUtil.INSTANCE.save(true, one);
                        break;
                    case 23: // Create shared note
                        NoteUtil.INSTANCE.createSharedNote(this, one);
                        break;
                    case 24: // Link shared note
                        Intent intent2 = new Intent(this, MainActivity.class);
                        intent2.putExtra(Choice.NOTE, true);
                        startActivityForResult(intent2, 4074);
                        break;
                    case 25: // New source
                        SourcesFragment.newSource(this, one);
                        break;
                    case 26: // Link existing source
                        Intent intent3 = new Intent(this, MainActivity.class);
                        intent3.putExtra(Choice.SOURCE, true);
                        startActivityForResult(intent3, 50473);
                        break;
                    // Relatives page
                    case 30:// Link new person
                        if (Global.settings.expert) {
                            new NewRelativeDialog(one, null, null, true, null).show(getSupportFragmentManager(), null);
                        } else {
                            builder.setItems(relatives, (dialog, selected) -> {
                                Intent intent4 = new Intent(getApplicationContext(), PersonEditorActivity.class);
                                intent4.putExtra(Extra.PERSON_ID, one.getId());
                                intent4.putExtra(Extra.RELATION, Relation.get(selected));
                                if (U.checkMultiMarriages(intent4, this, null))
                                    return;
                                startActivity(intent4);
                            }).show();
                        }
                        break;
                    case 31: // Link existing person
                        if (Global.settings.expert) {
                            new NewRelativeDialog(one, null, null, false, null).show(getSupportFragmentManager(), null);
                        } else {
                            builder.setItems(relatives, (dialog, selected) -> {
                                Intent intent5 = new Intent(getApplication(), MainActivity.class);
                                intent5.putExtra(Choice.PERSON, true);
                                intent5.putExtra(Extra.PERSON_ID, one.getId());
                                intent5.putExtra(Extra.RELATION, Relation.get(selected));
                                if (U.checkMultiMarriages(intent5, this, null))
                                    return;
                                choosePersonLauncher.launch(intent5);
                            }).show();
                        }
                        break;
                    default: // Events
                        String keyTag = null;
                        if (item.getItemId() >= 50) {
                            keyTag = otherEvents.get(item.getItemId() - 50).first;
                        } else if (item.getItemId() >= 40)
                            keyTag = mainEventTags[item.getItemId() - 40];
                        if (keyTag == null)
                            return false;
                        EventFact event = new EventFact();
                        event.setTag(keyTag);
                        switch (keyTag) {
                            case "EVEN":
                                event.setType("");
                                event.setDate("");
                            case "OCCU":
                            case "TITL":
                                event.setValue("");
                                break;
                            case "RESI":
                                event.setPlace("");
                                break;
                            case "BIRT":
                            case "DEAT":
                            case "CHR":
                            case "BAPM":
                            case "BURI":
                                event.setPlace("");
                                event.setDate("");
                        }
                        one.addEventFact(event);
                        Memory.add(event);
                        startActivity(new Intent(this, EventActivity.class));
                        TreeUtil.INSTANCE.save(true, one);
                }
                return true;
            });
        });
    }

    /**
     * Displays two images in the profile header: a regular one and the same blurred on background.
     */
    private void setImages() {
        ImageView imageView = findViewById(R.id.profile_image);
        Media media = FileUtil.INSTANCE.selectMainImage(one, imageView);
        // Same image blurred on background
        ImageView backImageView = findViewById(R.id.profile_background);
        if (media != null) {
            imageView.setOnClickListener(v -> {
                new FindStack(Global.gc, media);
                startActivity(new Intent(this, MediaActivity.class));
            });
            // imageView waits for the image to be loaded
            imageView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    Type type = (Type)imageView.getTag(R.id.tag_file_type);
                    if (type == Type.CROPPABLE || type == Type.PREVIEW) {
                        FileUtil.INSTANCE.showImage(media, backImageView, Image.BLUR | Image.DARK);
                        backImageView.setVisibility(View.VISIBLE);
                    } else backImageView.setVisibility(View.GONE);
                    if (type != Type.NONE) {
                        imageView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                }
            });
        } else backImageView.setVisibility(View.GONE);
    }

    /**
     * Refreshes header and pages without recreating the activity.
     */
    public void refresh() {
        // Name in the header
        CollapsingToolbarLayout toolbarLayout = findViewById(R.id.profile_toolbar_layout);
        toolbarLayout.setTitle(U.properName(one));
        // Header images
        setImages();
        // ID in the header
        if (Global.settings.expert) {
            TextView idView = findViewById(R.id.profile_id);
            idView.setText("INDI " + one.getId());
        }
        // Three pages
        adapter.notifyPagesChanged();
        // Menu
        invalidateOptionsMenu();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("oneId", one.getId());
        super.onSaveInstanceState(outState);/**/
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == 2173) { // File provided by another app becomes local media
                Media media = new Media();
                media.setFileTag("FILE");
                one.addMedia(media);
                if (F.setFileAndProposeCropping(this, null, data, media))
                    TreeUtil.INSTANCE.save(true, one);
            } else if (requestCode == 2174) { // Shared media
                Media sharedMedia = MediaFragment.newSharedMedia(one);
                if (F.setFileAndProposeCropping(this, null, data, sharedMedia))
                    TreeUtil.INSTANCE.save(true, sharedMedia, one);
            } else if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) { // Gets the image cropped by Android Image Cropper
                F.endImageCropping(data);
                TreeUtil.INSTANCE.save(true); // The change date for shared media is already saved in the previous step
                // TODO: pass Global.croppedMedia?
            } else if (requestCode == 43614) { // Media from MediaFragment
                MediaRef mediaRef = new MediaRef();
                mediaRef.setRef(data.getStringExtra(Extra.MEDIA_ID));
                one.addMediaRef(mediaRef);
                TreeUtil.INSTANCE.save(true, one);
            } else if (requestCode == 4074) { // Note
                NoteRef noteRef = new NoteRef();
                noteRef.setRef(data.getStringExtra(Extra.NOTE_ID));
                one.addNoteRef(noteRef);
                TreeUtil.INSTANCE.save(true, one);
            } else if (requestCode == 50473) { // Source
                SourceCitation citation = new SourceCitation();
                citation.setRef(data.getStringExtra(Extra.SOURCE_ID));
                one.addSourceCitation(citation);
                TreeUtil.INSTANCE.save(true, one);
            }
        } else if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) { // After back arrow in Image Cropper
            F.saveFolderInSettings();
        }
    }

    @Override
    public void onBackPressed() {
        Memory.stepBack();
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (gc == null || one == null) return false;
        menu.add(0, 0, 0, R.string.diagram);
        String[] familyLabels = PersonUtilKt.getFamilyLabels(one, this, null);
        if (familyLabels[0] != null)
            menu.add(0, 1, 0, familyLabels[0]);
        if (familyLabels[1] != null)
            menu.add(0, 2, 0, familyLabels[1]);
        if (Global.settings.getCurrentTree().root == null || !Global.settings.getCurrentTree().root.equals(one.getId()))
            menu.add(0, 3, 0, R.string.make_root);
        menu.add(0, 4, 0, R.string.modify);
        menu.add(0, 5, 0, R.string.delete);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 0: // DiagramFragment
                U.whichParentsToShow(this, one, 1);
                return true;
            case 1: // Family as child
                U.whichParentsToShow(this, one, 2);
                return true;
            case 2: // Family as partner
                U.whichSpousesToShow(this, one, null);
                return true;
            case 3: // Set as root
                Global.settings.getCurrentTree().root = one.getId();
                Global.settings.save();
                Toast.makeText(this, getString(R.string.this_is_root, U.properName(one)), Toast.LENGTH_LONG).show();
                return true;
            case 4: // Edit
                Intent intent = new Intent(this, PersonEditorActivity.class);
                intent.putExtra(Extra.PERSON_ID, one.getId());
                startActivity(intent);
                return true;
            case 5: // Delete
                new AlertDialog.Builder(this).setMessage(R.string.really_delete_person)
                        .setPositiveButton(R.string.delete, (dialog, i) -> {
                            Family[] families = PersonUtilKt.delete(one);
                            if (!U.deleteEmptyFamilies(this, this::onBackPressed, true, families))
                                onBackPressed();
                        }).setNeutralButton(R.string.cancel, null).show();
                return true;
            default:
                onBackPressed();
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        F.permissionsResult(this, null, code, permissions, results, one);
    }
}
