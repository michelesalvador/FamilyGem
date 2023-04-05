package app.familygem.share;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;

import org.folg.gedcom.model.Change;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.Submitter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import app.familygem.BaseActivity;
import app.familygem.Global;
import app.familygem.R;
import app.familygem.Settings;
import app.familygem.TreesActivity;
import app.familygem.U;
import app.familygem.constant.Extra;

/**
 * Activity that introduces the process for importing updates in an existing tree,
 * taking them from a tree received on sharing.
 */
public class CompareActivity extends BaseActivity {

    Date sharingDate;
    SimpleDateFormat changeDateFormat;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.compara);
        int idTree1 = getIntent().getIntExtra(Extra.TREE_ID, 1); // Old tree present in the app
        int idTree2 = getIntent().getIntExtra(Extra.TREE_ID_2, 1); // New tree received in sharing
        Global.treeId2 = idTree2; // It will be used by ProcessActivity and ConfirmationActivity
        Global.gc = TreesActivity.openGedcomTemporarily(idTree1, true);
        Global.gc2 = TreesActivity.openGedcomTemporarily(idTree2, false);
        if (Global.gc == null || Global.gc2 == null) {
            Toast.makeText(this, R.string.no_useful_data, Toast.LENGTH_LONG).show();
            onBackPressed();
            return;
        }

        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome")); // Synchronizes all dates to the italian time zone
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH);
            sharingDate = dateFormat.parse(getIntent().getStringExtra(Extra.DATE_ID));
        } catch (ParseException e) {
            e.printStackTrace();
        }

        changeDateFormat = new SimpleDateFormat("d MMM yyyyHH:mm:ss", Locale.ENGLISH);
        Comparison.reset(); // It's necessary for example after a device configuration change

        // Compares all the records of the two Gedcoms
        for (Family o2 : Global.gc2.getFamilies())
            compare(Global.gc.getFamily(o2.getId()), o2, 7);
        for (Family o : Global.gc.getFamilies())
            reconcile(o, Global.gc2.getFamily(o.getId()), 7);

        for (Person o2 : Global.gc2.getPeople())
            compare(Global.gc.getPerson(o2.getId()), o2, 6);
        for (Person o : Global.gc.getPeople())
            reconcile(o, Global.gc2.getPerson(o.getId()), 6);

        for (Source o2 : Global.gc2.getSources())
            compare(Global.gc.getSource(o2.getId()), o2, 5);
        for (Source o : Global.gc.getSources())
            reconcile(o, Global.gc2.getSource(o.getId()), 5);

        for (Media o2 : Global.gc2.getMedia())
            compare(Global.gc.getMedia(o2.getId()), o2, 4);
        for (Media o : Global.gc.getMedia())
            reconcile(o, Global.gc2.getMedia(o.getId()), 4);

        for (Repository o2 : Global.gc2.getRepositories())
            compare(Global.gc.getRepository(o2.getId()), o2, 3);
        for (Repository o : Global.gc.getRepositories())
            reconcile(o, Global.gc2.getRepository(o.getId()), 3);

        for (Submitter o2 : Global.gc2.getSubmitters())
            compare(Global.gc.getSubmitter(o2.getId()), o2, 2);
        for (Submitter o : Global.gc.getSubmitters())
            reconcile(o, Global.gc2.getSubmitter(o.getId()), 2);

        for (Note o2 : Global.gc2.getNotes())
            compare(Global.gc.getNote(o2.getId()), o2, 1);
        for (Note o : Global.gc.getNotes())
            reconcile(o, Global.gc2.getNote(o.getId()), 1);

        Settings.Tree tree2 = Global.settings.getTree(idTree2);
        if (Comparison.getList().isEmpty()) {
            setTitle(R.string.tree_without_news);
            if (tree2.grade != 30) {
                tree2.grade = 30;
                Global.settings.save();
            }
        } else if (tree2.grade != 20) {
            tree2.grade = 20;
            Global.settings.save();
        }

        populateCard(Global.gc, idTree1, R.id.compara_vecchio);
        populateCard(Global.gc2, idTree2, R.id.compara_nuovo);

        ((TextView)findViewById(R.id.compara_testo)).setText(getString(R.string.tree_news_imported, Comparison.getList().size()));

        Button button1 = findViewById(R.id.compara_bottone1);
        Button button2 = findViewById(R.id.compara_bottone2);
        if (Comparison.getList().size() > 0) {
            // 'Review singly' button
            button1.setOnClickListener(v -> {
                startActivity(new Intent(CompareActivity.this, ProcessActivity.class).putExtra("posizione", 1));
            });
            // 'Accept all' button
            button2.setOnClickListener(v -> {
                v.setEnabled(false);
                Comparison.get().numChoices = 0;
                for (Comparison.Front front : Comparison.getList()) {
                    if (front.canBothAddAndReplace)
                        Comparison.get().numChoices++;
                }
                Intent intent = new Intent(CompareActivity.this, ProcessActivity.class);
                intent.putExtra("posizione", 1);
                if (Comparison.get().numChoices > 0) { // Dialog requesting a revision
                    new AlertDialog.Builder(this)
                            .setTitle(Comparison.get().numChoices == 1 ? getString(R.string.one_update_choice)
                                    : getString(R.string.many_updates_choice, Comparison.get().numChoices))
                            .setMessage(R.string.updates_replace_add)
                            .setPositiveButton(android.R.string.ok, (dialog, id) -> {
                                Comparison.get().autoContinue = true;
                                Comparison.get().choicesMade = 1;
                                startActivity(intent);
                            }).setNeutralButton(android.R.string.cancel, (dialog, id) -> button2.setEnabled(true))
                            .setOnCancelListener(dialog -> button2.setEnabled(true)).show();
                } else { // Continues automatically
                    Comparison.get().autoContinue = true;
                    startActivity(intent);
                }
            });
        } else {
            button1.setText(R.string.delete_imported_tree);
            button1.setOnClickListener(v -> {
                TreesActivity.deleteTree(CompareActivity.this, idTree2);
                onBackPressed();
            });
            button2.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        findViewById(R.id.compara_bottone2).setEnabled(true); // In case it's disabled
        Comparison.get().autoContinue = false; // Resets it in case 'Accept all' was chosen
    }

    /**
     * Choose whether to add the two objects to the evaluation list.
     */
    private void compare(Object o, Object o2, int type) {
        Change c = getChange(o);
        Change c2 = getChange(o2);
        int modification = 0;
        if (o == null && isRecent(c2)) // o2 has been added in the new tree -> ADD
            modification = 1;
        else {
            if (c == null && c2 != null)
                modification = 1;
            else if (c != null && c2 != null &&
                    !(c.getDateTime().getValue().equals(c2.getDateTime().getValue()) // The two dates must be different
                            && c.getDateTime().getTime().equals(c2.getDateTime().getTime()))) {
                if (isRecent(c) && isRecent(c2)) { // Both changed after sharing -> ADD / REPLACE
                    modification = 2;
                } else if (isRecent(c2)) // Only o2 has been changed -> REPLACE
                    modification = 1;
            }
        }
        if (modification > 0) {
            Comparison.Front front = Comparison.addFront(o, o2, type);
            if (modification == 2)
                front.canBothAddAndReplace = true;
        }
    }

    /**
     * The same for the remaining objects deleted in the old tree.
     */
    private void reconcile(Object o, Object o2, int type) {
        if (o2 == null && !isRecent(getChange(o)))
            Comparison.addFront(o, null, type);
    }

    /**
     * Finds if a top-level record has been modified after the date of sharing.
     *
     * @param change Actual change date of the top-level record
     * @return true if the record is more recent than the date of sharing
     */
    private boolean isRecent(Change change) {
        boolean itIs = false;
        if (change != null && change.getDateTime() != null) {
            try { // TODO: test also with null Time
                String zoneId = U.castJsonString(change.getExtension("zone"));
                if (zoneId == null)
                    zoneId = "UTC";
                TimeZone timeZone = TimeZone.getTimeZone(zoneId);
                changeDateFormat.setTimeZone(timeZone);
                Date recordDate = changeDateFormat.parse(change.getDateTime().getValue() + change.getDateTime().getTime());
                itIs = recordDate.after(sharingDate);
                //long oreSfaso = TimeUnit.MILLISECONDS.toMinutes( timeZone.getOffset(dataobject.getTime()) );
                //s.l( dataobject+"\t"+ ok +"\t"+ (oreSfaso>0?"+":"")+oreSfaso +"\t"+ timeZone.getID() );
            } catch (ParseException e) {
            }
        }
        return itIs;
    }

    /**
     * Returns the Change date of any top-level object.
     */
    Change getChange(Object object) {
        Change change = null;
        try {  // TODO: to avoid this, split the check on all the objects with instanceof
            change = (Change)object.getClass().getMethod("getChange").invoke(object);
        } catch (Exception e) {
        }
        return change;
    }

    void populateCard(Gedcom gedcom, int treeId, int cardId) {
        CardView card = findViewById(cardId);
        Settings.Tree tree = Global.settings.getTree(treeId);
        TextView title = card.findViewById(R.id.confronto_titolo);
        TextView data = card.findViewById(R.id.confronto_testo);
        title.setText(tree.title);
        data.setText(TreesActivity.writeData(this, tree));
        if (cardId == R.id.compara_nuovo) {
            if (tree.grade == 30) {
                card.setCardBackgroundColor(getResources().getColor(R.color.consumed));
                title.setTextColor(getResources().getColor(R.color.gray_text));
                data.setTextColor(getResources().getColor(R.color.gray_text));
            } else
                card.setCardBackgroundColor(getResources().getColor(R.color.accent_medium));
            Submitter submitter = gedcom.getSubmitter(tree.shares.get(tree.shares.size() - 1).submitter);
            StringBuilder txt = new StringBuilder();
            if (submitter != null) {
                String name = submitter.getName();
                if (name == null || name.isEmpty())
                    name = getString(android.R.string.unknownName);
                txt.append(getString(R.string.sent_by, name)).append("\n");
            }
            //if (Comparison.getList().size() > 0)
            //    txt.append("Updates:\t");
            for (int i = 7; i > 0; i--) {
                txt.append(writeDifferences(i));
            }
            if (txt.toString().endsWith("\n"))
                txt = new StringBuilder(txt.substring(0, txt.length() - 1));
            ((TextView)card.findViewById(R.id.confronto_sottotesto)).setText(txt.toString());
            card.findViewById(R.id.confronto_sottotesto).setVisibility(View.VISIBLE);
        }
        card.findViewById(R.id.confronto_data).setVisibility(View.GONE);
    }

    int[] singulars = {R.string.shared_note, R.string.submitter, R.string.repository, R.string.shared_media, R.string.source, R.string.person, R.string.family};
    int[] plurals = {R.string.shared_notes, R.string.submitters, R.string.repositories, R.string.shared_medias, R.string.sources, R.string.persons, R.string.families};

    String writeDifferences(int type) {
        int changes = 0;
        for (Comparison.Front front : Comparison.getList()) {
            if (front.type == type) {
                changes++;
            }
        }
        String text = "";
        if (changes > 0) {
            type--;
            int description = changes == 1 ? singulars[type] : plurals[type];
            text = "\t\t+" + changes + " " + getString(description).toLowerCase() + "\n";
        }
        return text;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem i) {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Comparison.reset(); // Resets the Comparison singleton
    }
}
