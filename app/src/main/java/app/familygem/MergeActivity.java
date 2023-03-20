package app.familygem;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.ChildRef;
import org.folg.gedcom.model.ExtensionContainer;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.ParentFamilyRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.RepositoryRef;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SpouseFamilyRef;
import org.folg.gedcom.model.SpouseRef;
import org.folg.gedcom.model.Submitter;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import app.familygem.constant.Extra;
import app.familygem.visitor.ListOfSourceCitations;
import app.familygem.visitor.MediaContainersGuarded;
import app.familygem.visitor.MediaList;
import app.familygem.visitor.NoteContainersGuarded;

/**
 * Here we can select a tree to merge into the previously selected one, and choose how to merge them.
 */
public class MergeActivity extends BaseActivity {

    private int baseNum; // ID of base tree (the one receiving the selected tree)
    private int selectedNum; // ID of selected tree (the one merged into base tree)
    private int newNum; // ID of the third tree created from base with selected
    private List<Settings.Tree> trees;
    private LinearLayout listLayout;
    private RadioButton radioAnnex;
    private RadioButton radioGenerate;
    private EditText titleText;
    private boolean titleEdited; // To avoid modify an edited title
    private final Records records = new Records();
    private final String GUARDIAN = "modifiedId";
    private Thread mergeThread;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.merge_activity);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        baseNum = getIntent().getIntExtra(Extra.TREE_ID, 0);
        Settings.Tree baseTree = Global.settings.getTree(baseNum);
        // Base tree view
        View baseView = findViewById(R.id.merge_base);
        baseView.setBackground(getResources().getDrawable(R.drawable.generic_background));
        baseView.setPadding(U.dpToPx(15), U.dpToPx(5), U.dpToPx(15), U.dpToPx(7));
        ((TextView)findViewById(R.id.albero_titolo)).setText(baseTree.title);
        ((TextView)findViewById(R.id.albero_dati)).setText(TreesActivity.writeData(this, baseTree));
        findViewById(R.id.albero_menu).setVisibility(View.GONE);
        // Trees that can be merged
        trees = new ArrayList<>(Global.settings.trees);
        trees.remove(baseTree);
        Iterator<Settings.Tree> iterator = trees.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().grade >= 20) // Derived or exhausted trees
                iterator.remove();
        }
        // Places available trees into layout
        for (Settings.Tree tree : trees) {
            listLayout = findViewById(R.id.merge_list);
            View treeView = getLayoutInflater().inflate(R.layout.merge_piece, listLayout, false);
            listLayout.addView(treeView);
            TextView titleView = treeView.findViewById(R.id.merge_title);
            titleView.setText(tree.title);
            TextView detailView = treeView.findViewById(R.id.merge_detail);
            detailView.setText(TreesActivity.writeData(this, tree));
            treeView.setOnClickListener(view -> selectTree(tree.id));
            treeView.findViewById(R.id.merge_radio).setOnClickListener(view -> selectTree(tree.id));
        }
        // Output radio buttons
        radioAnnex = findViewById(R.id.merge_radio_annex);
        radioAnnex.setText(getString(R.string.merge_into, baseTree.title, "..."));
        radioGenerate = findViewById(R.id.merge_radio_generate);
        titleText = findViewById(R.id.merge_rename);
        titleText.setText(baseTree.title);
        titleText.setOnFocusChangeListener((view, focus) -> titleEdited = true);
        // Merging thread
        mergeThread = new Thread() {
            @Override
            public void run() {
                Global.gc = TreesActivity.readJson(baseNum);
                Global.gc2 = TreesActivity.readJson(selectedNum);
                // Merge selected tree into base tree
                if (radioAnnex.isChecked()) {
                    copyMediaFiles(Global.gc2, selectedNum, baseNum);
                    if (isInterrupted()) return;
                    doMerge();
                    if (isInterrupted()) return;
                    U.saveJson(Global.gc, baseNum); // Saves also Global.settings through Notifier
                    if (isInterrupted()) return;
                    Global.settings.openTree = baseNum; // For consistency with Global.gc
                } // Generate a third tree from the two
                else if (radioGenerate.isChecked()) {
                    newNum = Global.settings.max() + 1;
                    int persons = Global.gc.getPeople().size() + Global.gc2.getPeople().size();
                    int generations = Math.max(baseTree.generations, Global.settings.getTree(selectedNum).generations);
                    Global.settings.addTree(new Settings.Tree(newNum, titleText.getText().toString(), null,
                            persons, generations, baseTree.root, null, 0));
                    copyMediaFiles(Global.gc, baseNum, newNum);
                    copyMediaFiles(Global.gc2, selectedNum, newNum);
                    if (isInterrupted()) return;
                    doMerge();
                    if (isInterrupted()) return;
                    U.saveJson(Global.gc, newNum);
                    if (isInterrupted()) return;
                    Global.settings.openTree = newNum;
                }
                if (isInterrupted()) return;
                Global.indi = baseTree.root; // Resets it to display the correct person in the result tree
                runOnUiThread(() -> {
                    onBackPressed();
                    Toast.makeText(MergeActivity.this, R.string.merge_complete, Toast.LENGTH_LONG).show();
                });
            }
        };
        // Merge button
        Button mergeButton = findViewById(R.id.merge_button);
        mergeButton.setOnClickListener(button -> {
            if (Global.settings.premium) {
                showMergingState();
                mergeThread.start();
            } else {
                startActivity(new Intent(this, PurchaseActivity.class).putExtra(Extra.STRING, R.string.merge_tree));
            }
        });
        // Selecting a radio button
        RadioGroup radioGroup = findViewById(R.id.merge_radiogroup);
        radioGroup.setOnCheckedChangeListener((group, checked) -> {
            if (checked == R.id.merge_radio_generate)
                titleText.setVisibility(View.VISIBLE);
            else
                titleText.setVisibility(View.GONE);
            mergeButton.setEnabled(selectedNum > 0);
        });
        // Populates the records array with the 7 types
        records.add(Person.class, "I");
        records.add(Family.class, "F");
        records.add(Media.class, "M");
        records.add(Note.class, "T");
        records.add(Source.class, "S");
        records.add(Repository.class, "R");
        records.add(Submitter.class, "U");
    }

    /**
     * Select a tree from the list.
     */
    private void selectTree(int treeId) {
        selectedNum = treeId;
        for (int i = 0; i < listLayout.getChildCount(); i++) {
            View treeItem = listLayout.getChildAt(i);
            RadioButton radioButton = treeItem.findViewById(R.id.merge_radio);
            if (trees.get(i).id == treeId) {
                treeItem.setBackgroundColor(getResources().getColor(R.color.accent_light));
                radioButton.setChecked(true);
            } else {
                treeItem.setBackgroundColor(0x0000);
                radioButton.setChecked(false);
            }
        }
        // Annex radio button
        radioAnnex.setText(getString(R.string.merge_into,
                Global.settings.getTree(baseNum).title, Global.settings.getTree(selectedNum).title));
        // Suggested title
        if (!titleEdited) {
            String title = Global.settings.getTree(baseNum).title + " " + Global.settings.getTree(selectedNum).title;
            titleText.setText(title);
        }
        // Merge button
        if (radioAnnex.isChecked() || radioGenerate.isChecked())
            findViewById(R.id.merge_button).setEnabled(true);
    }

    /**
     * Disables all views and displays the progress wheel.
     */
    private void showMergingState() {
        for (int i = 0; i < listLayout.getChildCount(); i++) {
            View treeView = listLayout.getChildAt(i);
            treeView.setOnClickListener(null);
            ((TextView)treeView.findViewById(R.id.merge_title))
                    .setTextColor(getResources().getColor(R.color.gray_text));
            treeView.findViewById(R.id.merge_radio).setEnabled(false);
        }
        radioAnnex.setEnabled(false);
        radioGenerate.setEnabled(false);
        titleText.setEnabled(false);
        findViewById(R.id.merge_button).setEnabled(false);
        findViewById(R.id.progress_wheel).setVisibility(View.VISIBLE);
    }

    /**
     * Executes the merge of Global.gc2 into Global.gc.
     */
    private void doMerge() {
        // Loops in records of base tree seeking for maximum ID of each record type
        for (Person person : Global.gc.getPeople())
            findMaxId(person);
        for (Family family : Global.gc.getFamilies())
            findMaxId(family);
        for (Media media : Global.gc.getMedia())
            findMaxId(media);
        for (Note note : Global.gc.getNotes())
            findMaxId(note);
        for (Source source : Global.gc.getSources())
            findMaxId(source);
        for (Repository repo : Global.gc.getRepositories())
            findMaxId(repo);
        for (Submitter submitter : Global.gc.getSubmitters())
            findMaxId(submitter);

        // Loops in records of the selected tree
        // If their ID is less than max, updates it and each related ID
        for (Person person : Global.gc2.getPeople()) {
            if (isEligible(person)) {
                if (checkNotDone(person)) {
                    person.setId(newId);
                    person.putExtension(GUARDIAN, true);
                }
                for (Family family : Global.gc2.getFamilies()) {
                    for (SpouseRef ref : family.getHusbandRefs()) {
                        if (checkNotDone(ref) && oldId.equals(ref.getRef())) {
                            ref.setRef(newId);
                            ref.putExtension(GUARDIAN, true);
                        }
                    }
                    for (SpouseRef ref : family.getWifeRefs()) {
                        if (checkNotDone(ref) && oldId.equals(ref.getRef())) {
                            ref.setRef(newId);
                            ref.putExtension(GUARDIAN, true);
                        }
                    }
                    for (ChildRef ref : family.getChildRefs()) {
                        if (checkNotDone(ref) && oldId.equals(ref.getRef())) {
                            ref.setRef(newId);
                            ref.putExtension(GUARDIAN, true);
                        }
                    }
                }
            }
        }
        for (Family family : Global.gc2.getFamilies()) {
            if (isEligible(family)) {
                if (checkNotDone(family)) {
                    family.setId(newId);
                    family.putExtension(GUARDIAN, true);
                }
                for (Person person : Global.gc2.getPeople()) {
                    for (ParentFamilyRef ref : person.getParentFamilyRefs()) {
                        if (checkNotDone(ref) && oldId.equals(ref.getRef())) {
                            ref.setRef(newId);
                            ref.putExtension(GUARDIAN, true);
                        }
                    }
                    for (SpouseFamilyRef ref : person.getSpouseFamilyRefs()) {
                        if (checkNotDone(ref) && oldId.equals(ref.getRef())) {
                            ref.setRef(newId);
                            ref.putExtension(GUARDIAN, true);
                        }
                    }
                }
            }
        }
        for (Media media : Global.gc2.getMedia()) {
            if (isEligible(media)) {
                media.setId(newId);
                new MediaContainersGuarded(Global.gc2, oldId, newId, false);
            }
        }
        for (Note note : Global.gc2.getNotes()) {
            if (isEligible(note)) {
                note.setId(newId);
                new NoteContainersGuarded(Global.gc2, oldId, newId, false);
            }
        }
        for (Source source : Global.gc2.getSources()) {
            if (isEligible(source)) {
                source.setId(newId);
                ListOfSourceCitations citations = new ListOfSourceCitations(Global.gc2, oldId);
                for (ListOfSourceCitations.Triplet triplet : citations.list) {
                    if (checkNotDone(triplet.citation)) {
                        triplet.citation.setRef(newId);
                        triplet.citation.putExtension(GUARDIAN, true);
                    }
                }
            }
        }
        for (Repository repo : Global.gc2.getRepositories()) {
            if (isEligible(repo)) {
                repo.setId(newId);
                for (Source source : Global.gc2.getSources()) {
                    RepositoryRef repoRef = source.getRepositoryRef();
                    if (repoRef != null && checkNotDone(repoRef) && repoRef.getRef().equals(oldId)) {
                        repoRef.setRef(newId);
                        repoRef.putExtension(GUARDIAN, true);
                    }
                }
            }
        }
        for (Submitter submitter : Global.gc2.getSubmitters()) {
            if (isEligible(submitter)) {
                submitter.setId(newId);
            }
        }

        // Removes guardian extensions from the selected tree
        for (Person person : Global.gc2.getPeople()) {
            removeGuardian(person);
            for (ParentFamilyRef ref : person.getParentFamilyRefs())
                removeGuardian(ref);
            for (SpouseFamilyRef ref : person.getSpouseFamilyRefs())
                removeGuardian(ref);
        }
        for (Family family : Global.gc2.getFamilies()) {
            removeGuardian(family);
            for (SpouseRef ref : family.getHusbandRefs())
                removeGuardian(ref);
            for (SpouseRef ref : family.getWifeRefs())
                removeGuardian(ref);
            for (ChildRef ref : family.getChildRefs())
                removeGuardian(ref);
        }
        new MediaContainersGuarded(Global.gc2, null, null, true);
        new NoteContainersGuarded(Global.gc2, null, null, true);
        for (Source source : Global.gc2.getSources()) {
            ListOfSourceCitations citations = new ListOfSourceCitations(Global.gc2, source.getId());
            for (ListOfSourceCitations.Triplet triplet : citations.list)
                removeGuardian(triplet.citation);
            RepositoryRef repoRef = source.getRepositoryRef();
            if (repoRef != null) removeGuardian(repoRef);
        }

        // Merges the records from selected tree into base tree
        for (Person person : Global.gc2.getPeople())
            Global.gc.addPerson(person);
        for (Family family : Global.gc2.getFamilies())
            Global.gc.addFamily(family);
        for (Media media : Global.gc2.getMedia())
            Global.gc.addMedia(media);
        for (Note note : Global.gc2.getNotes())
            Global.gc.addNote(note);
        for (Source source : Global.gc2.getSources())
            Global.gc.addSource(source);
        for (Repository repo : Global.gc2.getRepositories())
            Global.gc.addRepository(repo);
        for (Submitter submitter : Global.gc2.getSubmitters())
            Global.gc.addSubmitter(submitter);
    }

    private void copyMediaFiles(Gedcom sourceGedcom, int sourceId, int destinationId) {
        // Collects existing media and file paths of source tree, but only from externalFilesDir
        MediaList mediaList = new MediaList(sourceGedcom, 0);
        sourceGedcom.accept(mediaList);
        Map<Media, String> mediaPaths = new HashMap<>();
        final String extSourceDir = getExternalFilesDir(String.valueOf(sourceId)).getPath();
        for (Media media : mediaList.list) {
            String path = F.mediaPath(sourceId, media);
            if (path != null && path.startsWith(extSourceDir))
                mediaPaths.put(media, path);
        }
        // Copies the files to media folder of destination tree, renaming them if necessary
        if (!mediaPaths.isEmpty()) {
            File extDestinationDir = getExternalFilesDir(String.valueOf(destinationId)); // Creates the folder if not existing
            if (extDestinationDir.list().length == 0) { // Empty folder, probably because just created
                Global.settings.getTree(destinationId).dirs.add(extDestinationDir.getPath());
                // No need to save Global.settings here because U.saveJson() will do
            }
            for (Map.Entry<Media, String> mediaPath : mediaPaths.entrySet()) {
                String path = mediaPath.getValue();
                File sourceFile = new File(path);
                File destinationFile = F.nextAvailableFileName(
                        extDestinationDir.getPath(), path.substring(path.lastIndexOf('/') + 1));
                try {
                    FileInputStream sourceStream = new FileInputStream(sourceFile);
                    FileUtils.copyInputStreamToFile(sourceStream, destinationFile);
                } catch (Exception ignored) {
                }
                // Updates file link inside media
                Media media = mediaPath.getKey();
                if (media.getFile().contains("/"))
                    media.setFile(destinationFile.getPath());
            }
        }
    }

    /**
     * Populates {@link #records} with maximum ID number of a record.
     */
    private void findMaxId(ExtensionContainer record) {
        Class<?> aClass = record.getClass();
        try {
            String id = (String)aClass.getMethod("getId").invoke(record);
            int num = U.extractNum(id);
            if (num > records.getMax(aClass)) records.setMax(aClass, num);
        } catch (Exception ignored) {
        }
    }

    String oldId;
    String newId;

    /**
     * Finds a new ID for a record to be merged and returns true.
     */
    private boolean isEligible(ExtensionContainer record) {
        Class<?> aClass = record.getClass();
        try {
            oldId = (String)aClass.getMethod("getId").invoke(record);
        } catch (Exception ignored) {
        }
        int maxNum = records.getMax(aClass);
        if (maxNum >= U.extractNum(oldId)) {
            maxNum++;
            newId = records.getPrefix(aClass) + maxNum;
            records.setMax(aClass, maxNum);
            return true;
        }
        return false;
    }

    /**
     * Checks if the GUARDIAN extension doesn't exist.
     */
    private boolean checkNotDone(ExtensionContainer object) {
        Object done = object.getExtension(GUARDIAN);
        return done == null;
    }

    /**
     * Removes the guardian extension.
     */
    private void removeGuardian(ExtensionContainer object) {
        if (object.getExtension(GUARDIAN) != null)
            object.getExtensions().remove(GUARDIAN);
        if (object.getExtensions().isEmpty())
            object.setExtensions(null);
    }

    /**
     * List of 7 record types.
     */
    private static class Records extends ArrayList<Record> {
        void add(Class<?> theClass, String prefix) {
            add(new Record(theClass, prefix));
        }

        public int getMax(Class<?> aClass) {
            for (Record record : this) {
                if (record.theClass.equals(aClass)) return record.max;
            }
            return 0;
        }

        public void setMax(Class<?> aClass, int num) {
            for (Record record : this) {
                if (record.theClass.equals(aClass)) {
                    record.max = num;
                    break;
                }
            }
        }

        public String getPrefix(Class<?> aClass) {
            for (Record record : this) {
                if (record.theClass.equals(aClass)) return record.prefix;
            }
            return null;
        }
    }

    private static class Record {
        Class<?> theClass; // Person, Family...
        String prefix; // "I", "F"...
        int max; // Maximum ID number for this type of record

        Record(Class<?> theClass, String prefix) {
            this.theClass = theClass;
            this.prefix = prefix;
            max = 0;
        }

    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt("selectedId", selectedNum);
        outState.putBoolean("merging", findViewById(R.id.progress_wheel).getVisibility() == View.VISIBLE);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedState) {
        super.onRestoreInstanceState(savedState);
        selectedNum = savedState.getInt("selectedId");
        if (selectedNum > 0) selectTree(selectedNum);
        if (savedState.getBoolean("merging"))
            showMergingState();
    }

    @Override
    public void onBackPressed() {
        if (mergeThread.isAlive()) {
            new AlertDialog.Builder(this).setMessage(R.string.sure_delete)
                    .setNegativeButton(android.R.string.no, (dialog, i) -> dialog.dismiss())
                    .setPositiveButton(android.R.string.yes, (dialog, i) -> {
                        mergeThread.interrupt();
                        Global.settings.openTree = 0;
                        if (newNum > 0)
                            TreesActivity.deleteTree(this, newNum);
                        super.onBackPressed();
                    }).show();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        onBackPressed();
        return true;
    }
}
