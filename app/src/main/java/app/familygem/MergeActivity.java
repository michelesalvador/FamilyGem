package app.familygem;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.ChildRef;
import org.folg.gedcom.model.ExtensionContainer;
import org.folg.gedcom.model.Family;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import app.familygem.visitor.ListOfSourceCitations;
import app.familygem.visitor.MediaContainersGuarded;
import app.familygem.visitor.MediaList;
import app.familygem.visitor.NoteContainersGuarded;

/**
 * List of available trees that can be merged into the previously selected one.
 */
public class MergeActivity extends BaseActivity {

    private int baseId; // ID of base tree (that receiving the selected tree)
    private int selectedId; // ID of selected tree (that merged into base tree)
    private List<Settings.Tree> trees;
    private LinearLayout listLayout;
    private final Records records = new Records();
    private final String GUARDIAN = "modifiedId";

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.merge_activity);
        baseId = getIntent().getIntExtra("treeId", 0);
        Settings.Tree baseTree = Global.settings.getTree(baseId);
        TextView suggestionView = findViewById(R.id.merge_suggestion);
        suggestionView.setText("Select a tree to merge into " + baseTree.title + ":"); // TODO: translate
        //suggestionView.setText(getString(R.string.select_tree_merge, baseTree.title));
        trees = new ArrayList<>(Global.settings.trees);
        trees.remove(baseTree);
        Iterator<Settings.Tree> iterator = trees.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().grade >= 20)
                iterator.remove();
        }

        // Places all other trees into the list
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
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        findViewById(R.id.merge_button).setOnClickListener(button -> {
            button.setEnabled(false);
            executeMerge();
            onBackPressed();
        });
    }

    private void selectTree(int treeId) {
        selectedId = treeId;
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
        findViewById(R.id.merge_button).setEnabled(true);
    }

    private void executeMerge() {
        Global.gc = TreesActivity.openGedcomTemporarily(baseId, true);
        Global.gc2 = TreesActivity.openGedcomTemporarily(selectedId, false);

        // Populates the array
        records.add(Person.class, "I");
        records.add(Family.class, "F");
        records.add(Media.class, "M");
        records.add(Note.class, "T");
        records.add(Source.class, "S");
        records.add(Repository.class, "R");
        records.add(Submitter.class, "U");

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

        // Loops in records of the selected tree: if their ID is less than max, update it AND every related ID
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

        // Collects existing media and file paths of selected tree, but only from externalFilesDir
        MediaList mediaList = new MediaList(Global.gc2, 0);
        Global.gc2.accept(mediaList);
        Map<Media, String> mediaPaths = new HashMap<>();
        final String externalSelectedDir = getExternalFilesDir(String.valueOf(selectedId)).getPath();
        for (Media media : mediaList.list) {
            String path = F.mediaPath(selectedId, media);
            if (path != null && path.startsWith(externalSelectedDir))
                mediaPaths.put(media, path);
        }
        // Copies the files to media folder of base tree, renaming them if necessary
        if (!mediaPaths.isEmpty()) {
            File externalBaseDir = getExternalFilesDir(String.valueOf(baseId)); // Creates the folder if not existing
            if (externalBaseDir.list().length == 0) { // Empty folder, probably because just created
                if (Global.settings.getTree(baseId).dirs.add(externalBaseDir.getPath()))
                    Global.settings.save();
            }
            for (Map.Entry<Media, String> mediaPath : mediaPaths.entrySet()) {
                String path = mediaPath.getValue();
                File sourceFile = new File(path);
                File destinationFile = F.nextAvailableFileName(
                        externalBaseDir.getPath(), path.substring(path.lastIndexOf('/') + 1));
                try {
                    FileUtils.copyFile(sourceFile, destinationFile);
                } catch (Exception e) {
                }
                // Updates file link inside media
                Media media = mediaPath.getKey();
                if (media.getFile().contains("/"))
                    media.setFile(destinationFile.getPath());
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

        U.saveJson(Global.gc, baseId);
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
        } catch (Exception e) {
        }
    }

    String oldId;
    String newId;

    /**
     * Finds a new ID for a record to be merged.
     */
    private boolean isEligible(ExtensionContainer record) {
        Class<?> aClass = record.getClass();
        try {
            oldId = (String)aClass.getMethod("getId").invoke(record);
        } catch (Exception e) {
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
    public boolean onOptionsItemSelected(MenuItem item) {
        onBackPressed();
        return true;
    }
}
