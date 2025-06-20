package app.familygem.share;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;

import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.ChildRef;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.ParentFamilyRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SpouseFamilyRef;
import org.folg.gedcom.model.SpouseRef;
import org.folg.gedcom.model.Submitter;
import org.folg.gedcom.model.Visitable;

import java.io.File;
import java.io.IOException;

import app.familygem.BaseActivity;
import app.familygem.FileUri;
import app.familygem.Global;
import app.familygem.R;
import app.familygem.Settings;
import app.familygem.TreesActivity;
import app.familygem.U;
import app.familygem.util.ChangeUtil;
import app.familygem.util.FileUtil;
import app.familygem.util.TreeUtil;
import app.familygem.util.TreeUtilKt;
import app.familygem.visitor.ListOfSourceCitations;
import app.familygem.visitor.MediaContainers;
import app.familygem.visitor.MediaLeaders;
import app.familygem.visitor.NoteContainers;
import kotlin.Pair;

/**
 * Final activity of the process of importing updates in an existing tree.
 */
public class ConfirmationActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.confirmation_activity);
        if (!Comparison.getList().isEmpty()) {

            // Old tree
            CardView card = findViewById(R.id.confirmation_old);
            Settings.Tree tree = Global.settings.getTree(Global.settings.openTree);
            ((TextView)card.findViewById(R.id.compare_title)).setText(tree.title);
            String txt = TreeUtilKt.getBasicData(tree);
            ((TextView)card.findViewById(R.id.compare_text)).setText(txt);
            card.findViewById(R.id.compare_date).setVisibility(View.GONE);

            int add = 0;
            int replace = 0;
            int delete = 0;
            for (Comparison.Front front : Comparison.getList()) {
                switch (front.destiny) {
                    case 1:
                        add++;
                        break;
                    case 2:
                        replace++;
                        break;
                    case 3:
                        delete++;
                }
            }
            String text = getString(R.string.accepted_news, add + replace + delete, add, replace, delete);
            ((TextView)findViewById(R.id.confirmation_text)).setText(text);

            findViewById(R.id.confirmation_cancel).setOnClickListener(v -> {
                Comparison.reset();
                startActivity(new Intent(ConfirmationActivity.this, TreesActivity.class));
            });

            findViewById(R.id.confirmation_ok).setOnClickListener(v -> {
                // Change the ID and all refs in all the objects with canBothAddAndReplace and destiny 'add'
                boolean changed = false;
                for (Comparison.Front front : Comparison.getList()) {
                    if (front.canBothAddAndReplace && front.destiny == 1) {
                        String newId;
                        changed = true;
                        switch (front.type) {
                            case 1: // Note
                                newId = maxId(Note.class);
                                Note n2 = (Note)front.object2;
                                new NoteContainers(Global.gc2, n2, newId); // Updates all refs to the note
                                n2.setId(newId); // Then update the note id
                                break;
                            case 2: // Submitter
                                newId = maxId(Submitter.class);
                                ((Submitter)front.object2).setId(newId);
                                break;
                            case 3: // Repository
                                newId = maxId(Repository.class);
                                Repository repo2 = (Repository)front.object2;
                                for (Source fon : Global.gc2.getSources())
                                    if (fon.getRepositoryRef() != null && fon.getRepositoryRef().getRef().equals(repo2.getId()))
                                        fon.getRepositoryRef().setRef(newId);
                                repo2.setId(newId);
                                break;
                            case 4: // Media
                                newId = maxId(Media.class);
                                Media m2 = (Media)front.object2;
                                new MediaContainers(Global.gc2, m2, newId);
                                m2.setId(newId);
                                break;
                            case 5: // Source
                                newId = maxId(Source.class);
                                Source s2 = (Source)front.object2;
                                ListOfSourceCitations sourceCitations = new ListOfSourceCitations(Global.gc2, s2.getId());
                                for (ListOfSourceCitations.Triplet tri : sourceCitations.list)
                                    tri.citation.setRef(newId);
                                s2.setId(newId);
                                break;
                            case 6: // Person
                                newId = maxId(Person.class);
                                Person p2 = (Person)front.object2;
                                for (Family fam : Global.gc2.getFamilies()) {
                                    for (SpouseRef sr : fam.getHusbandRefs())
                                        if (sr.getRef().equals(p2.getId()))
                                            sr.setRef(newId);
                                    for (SpouseRef sr : fam.getWifeRefs())
                                        if (sr.getRef().equals(p2.getId()))
                                            sr.setRef(newId);
                                    for (ChildRef cr : fam.getChildRefs())
                                        if (cr.getRef().equals(p2.getId()))
                                            cr.setRef(newId);
                                }
                                p2.setId(newId);
                                break;
                            case 7: // Family
                                newId = maxId(Family.class);
                                Family f2 = (Family)front.object2;
                                for (Person per : Global.gc2.getPeople()) {
                                    for (ParentFamilyRef pfr : per.getParentFamilyRefs())
                                        if (pfr.getRef().equals(f2.getId()))
                                            pfr.setRef(newId);
                                    for (SpouseFamilyRef sfr : per.getSpouseFamilyRefs())
                                        if (sfr.getRef().equals(f2.getId()))
                                            sfr.setRef(newId);
                                }
                                f2.setId(newId);
                        }
                    }
                }
                if (changed) TreeUtil.INSTANCE.saveJsonAsync(Global.gc2, Global.treeId2);

                // Regular addition / replacement / deletion of records from tree2 to tree
                for (Comparison.Front front : Comparison.getList()) {
                    switch (front.type) {
                        case 1: // Note
                            if (front.destiny > 1)
                                Global.gc.getNotes().remove(front.object);
                            if (front.destiny > 0 && front.destiny < 3) {
                                Global.gc.addNote((Note)front.object2);
                                copyAllFiles(front.object2);
                            }
                            break;
                        case 2: // Submitter
                            if (front.destiny > 1)
                                Global.gc.getSubmitters().remove(front.object);
                            if (front.destiny > 0 && front.destiny < 3)
                                Global.gc.addSubmitter((Submitter)front.object2);
                            break;
                        case 3: // Repository
                            if (front.destiny > 1)
                                Global.gc.getRepositories().remove(front.object);
                            if (front.destiny > 0 && front.destiny < 3) {
                                Global.gc.addRepository((Repository)front.object2);
                                copyAllFiles(front.object2);
                            }
                            break;
                        case 4: // Media
                            if (front.destiny > 1)
                                Global.gc.getMedia().remove(front.object);
                            if (front.destiny > 0 && front.destiny < 3) {
                                Global.gc.addMedia((Media)front.object2);
                                copyFile((Media)front.object2, (NoteContainer)front.object2);
                            }
                            break;
                        case 5: // Source
                            if (front.destiny > 1)
                                Global.gc.getSources().remove(front.object);
                            if (front.destiny > 0 && front.destiny < 3) {
                                Global.gc.addSource((Source)front.object2);
                                copyAllFiles(front.object2);
                            }
                            break;
                        case 6: // Person
                            if (front.destiny > 1)
                                Global.gc.getPeople().remove(front.object);
                            if (front.destiny > 0 && front.destiny < 3) {
                                Global.gc.addPerson((Person)front.object2);
                                copyAllFiles(front.object2);
                            }
                            break;
                        case 7: // Family
                            if (front.destiny > 1)
                                Global.gc.getFamilies().remove(front.object);
                            if (front.destiny > 0 && front.destiny < 3) {
                                Global.gc.addFamily((Family)front.object2);
                                copyAllFiles(front.object2);
                            }
                    }
                }
                TreeUtil.INSTANCE.saveJsonAsync(Global.gc, Global.settings.openTree);

                // If all updates are imported proposes to delete the shared tree
                boolean allOk = true;
                for (Comparison.Front front : Comparison.getList())
                    if (front.destiny == 0) {
                        allOk = false;
                        break;
                    }
                if (allOk) {
                    Global.settings.getTree(Global.treeId2).grade = 30;
                    Global.settings.save();
                    new AlertDialog.Builder(ConfirmationActivity.this)
                            .setMessage(R.string.all_imported_delete)
                            .setPositiveButton(android.R.string.ok, (d, i) -> {
                                TreeUtil.INSTANCE.deleteTree(Global.treeId2);
                                done();
                            }).setNegativeButton(R.string.no, (d, i) -> done())
                            .setOnCancelListener(dialog -> done()).show();
                } else
                    done();
            });
        } else onBackPressed();
    }

    /**
     * Completes the process opening TreesActivity.
     */
    void done() {
        Comparison.reset();
        startActivity(new Intent(this, TreesActivity.class));
    }

    /**
     * Returns the highest ID of a certain class taking in count old and new tree.
     */
    String maxId(Class aClass) {
        String id = U.newID(Global.gc, aClass); // New ID against old tree records
        String id2 = U.newID(Global.gc2, aClass); // and against new tree
        if (Integer.parseInt(id.substring(1)) > Integer.parseInt(id2.substring(1))) // Removes the initial letter
            return id;
        else
            return id2;
    }

    /**
     * If a new object has media, considers copying the files to the image folder of the old tree.
     */
    void copyAllFiles(Object object) {
        MediaLeaders searchMedia = new MediaLeaders();
        ((Visitable)object).accept(searchMedia);
        for (MediaLeaders.MediaWrapper wrapper : searchMedia.getList()) {
            copyFile(wrapper.getMedia(), wrapper.getLeader());
        }
    }

    /**
     * Copies the Media file into the external storage of old tree, avoiding duplicates, and in case updates the Media link.
     */
    private void copyFile(Media media, NoteContainer leader) {
        FileUri fileUri = new FileUri(this, media, Global.treeId2, true);
        if (fileUri.exists()) {
            File originFile = fileUri.getFile();
            File externalDir = getExternalFilesDir(String.valueOf(Global.settings.openTree));
            Pair<File, Boolean> destination = FileUtil.INSTANCE.nextAvailableFileName(externalDir, fileUri.getName(), originFile);
            File destinationFile = destination.component1();
            if (destination.component2()) { // originFile does not already exist in externalDir
                try {
                    FileUtils.copyFile(originFile, destinationFile);
                } catch (IOException ignored) {
                }
            }
            if (!media.getFile().equals(destinationFile.getName())) {
                media.setFile(destinationFile.getName());
                ChangeUtil.INSTANCE.updateChangeDate(leader); // Updates change date of leader object
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem i) {
        onBackPressed();
        return true;
    }
}
