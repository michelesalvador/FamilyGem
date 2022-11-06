package app.familygem;

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

/**
 * Activity for importing news in an existing tree
 */
public class CompareActivity extends BaseActivity {

    Date sharingDate;
    SimpleDateFormat changeDateFormat;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.compara);
        int idTree1 = getIntent().getIntExtra("idAlbero", 1); // Old tree
        int idTree2 = getIntent().getIntExtra("idAlbero2", 1); // New tree received in sharing
        Global.treeId2 = idTree2; // it will be used for the Comparator and Confirmation images
        Global.gc = TreesActivity.openGedcomTemporarily(idTree1, true);
        Global.gc2 = TreesActivity.openGedcomTemporarily(idTree2, false);
        if (Global.gc == null || Global.gc2 == null) {
            Toast.makeText(this, R.string.no_useful_data, Toast.LENGTH_LONG).show();
            onBackPressed();
            return;
        }

        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Rome")); // brings all dates back to the Aruba time zone //riconduce tutte le date al fuso orario di Aruba
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH);
            sharingDate = dateFormat.parse(getIntent().getStringExtra("idData"));
        } catch (ParseException e) {
            e.printStackTrace();
        }

        changeDateFormat = new SimpleDateFormat("d MMM yyyyHH:mm:ss", Locale.ENGLISH);
        Comparison.reset(); // Necessary to empty it, for example after a configuration change //Necessario svuotarlo, ad esempio dopo un cambio di configurazione

        // Compare all the records of the two Gedcoms
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

        ((TextView) findViewById(R.id.compara_testo)).setText(getString(R.string.tree_news_imported, Comparison.getList().size()));

        Button button1 = findViewById(R.id.compara_bottone1);
        Button button2 = findViewById(R.id.compara_bottone2);
        if (Comparison.getList().size() > 0) {
            // Review individually //Rivedi singolarmente
            button1.setOnClickListener(v -> {
                startActivity(new Intent(CompareActivity.this, TreeComparatorActivity.class).putExtra("posizione", 1));
            });
            // Accept everything //Accetta tutto
            button2.setOnClickListener(v -> {
                v.setEnabled(false);
                Comparison.get().numChoices = 0;
                for (Comparison.Fronte fronte : Comparison.getList()) {
                    if (fronte.doppiaOpzione)
                        Comparison.get().numChoices++;
                }
                Intent intent = new Intent(CompareActivity.this, TreeComparatorActivity.class);
                intent.putExtra("posizione", 1);
                if (Comparison.get().numChoices > 0) { // Revision request dialog //Dialogo di richiesta revisione
                    new AlertDialog.Builder(this)
                            .setTitle(Comparison.get().numChoices == 1 ? getString(R.string.one_update_choice)
                                    : getString(R.string.many_updates_choice, Comparison.get().numChoices))
                            .setMessage(R.string.updates_replace_add)
                            .setPositiveButton(android.R.string.ok, (dialog, id) -> {
                                Comparison.get().autoProsegui = true;
                                Comparison.get().scelteFatte = 1;
                                startActivity(intent);
                            }).setNeutralButton(android.R.string.cancel, (dialog, id) -> button2.setEnabled(true))
                            .setOnCancelListener(dialog -> button2.setEnabled(true)).show();
                } else { // Start automatically //Avvio in automatico
                    Comparison.get().autoProsegui = true;
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
        findViewById(R.id.compara_bottone2).setEnabled(true); // if possibly(?) //se eventualmente
        Comparison.get().autoProsegui = false; // It resets it if the automatism(?) was eventually chosen //Lo resetta se eventualmente era stato scelto l'automatismo
    }

    /**
     * See whether to add the two objects to the list of those to be evaluated
     * Vede se aggiungere i due oggetti alla lista di quelli da valutare
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
                    !(c.getDateTime().getValue().equals(c2.getDateTime().getValue()) // the two dates must be different
                            && c.getDateTime().getTime().equals(c2.getDateTime().getTime()))) {
                if (isRecent(c) && isRecent(c2)) { // both changed after sharing -> ADD / REPLACE //entrambi modificati dopo la condivisione --> AGGIUNGI/SOSTITUISCI
                    modification = 2;
                } else if (isRecent(c2)) // only o2 has been changed -> REPLACE
                    modification = 1;
            }
        }
        if (modification > 0) {
            Comparison.Fronte fronte = Comparison.addFronte(o, o2, type);
            if (modification == 2)
                fronte.doppiaOpzione = true;
        }
    }

    /**
     * Ditto for the remaining objects deleted in the old tree
     * Idem per i rimanenti oggetti eliminati nell'albero vecchio
     */
    private void reconcile(Object o, Object o2, int tipo) {
        if (o2 == null && !isRecent(getChange(o)))
            Comparison.addFronte(o, null, tipo);
    }

    /**
     * Find if a top-level record has been modified after the date of sharing
     *
     * @param change Actual change date of the top-level record
     * @return true if the record is more recent than the date of sharing
     */
    private boolean isRecent(Change change) {
        boolean itIs = false;
        if (change != null && change.getDateTime() != null) {
            try { // TODO with null time(?) //con time null
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

    Change getChange(Object ogg) {
        Change change = null;
        try {
            change = (Change) ogg.getClass().getMethod("getChange").invoke(ogg); //TODO change doesn't have this function...?
        } catch (Exception e) {
        }
        return change;
    }

    void populateCard(Gedcom gc, int treeId, int cardId) {
        CardView card = findViewById(cardId);
        Settings.Tree tree = Global.settings.getTree(treeId);
        TextView title = card.findViewById(R.id.confronto_titolo);
        TextView data = card.findViewById(R.id.confronto_testo);
        title.setText(tree.title);
        data.setText(TreesActivity.writeData(this, tree));
        if (cardId == R.id.compara_nuovo) {
            if (tree.grade == 30) {
                card.setCardBackgroundColor(getResources().getColor(R.color.consumed));
                title.setTextColor(getResources().getColor(R.color.grayText));
                data.setTextColor(getResources().getColor(R.color.grayText));
            } else
                card.setCardBackgroundColor(getResources().getColor(R.color.evidenziaMedio));
            Submitter submitter = gc.getSubmitter(tree.shares.get(tree.shares.size() - 1).submitter);
            StringBuilder txt = new StringBuilder();
            if (submitter != null) {
                String name = submitter.getName();
                if (name == null || name.isEmpty())
                    name = getString(android.R.string.unknownName);
                txt.append(getString(R.string.sent_by, name)).append("\n");
            }
            //if( Confronto.getLista().size() > 0 )
            //	txt += "Updates:\t";
            for (int i = 7; i > 0; i--) {
                txt.append(writeDifferences(i));
            }
            if (txt.toString().endsWith("\n"))
                txt = new StringBuilder(txt.substring(0, txt.length() - 1));
            ((TextView) card.findViewById(R.id.confronto_sottotesto)).setText(txt.toString());
            card.findViewById(R.id.confronto_sottotesto).setVisibility(View.VISIBLE);
        }
        card.findViewById(R.id.confronto_data).setVisibility(View.GONE);
    }

    int[] singulars = {R.string.shared_note, R.string.submitter, R.string.repository, R.string.shared_media, R.string.source, R.string.person, R.string.family};
    int[] plurals = {R.string.shared_notes, R.string.submitters, R.string.repositories, R.string.shared_medias, R.string.sources, R.string.persons, R.string.families};

    String writeDifferences(int type) {
        int changes = 0;
        for (Comparison.Fronte fronte : Comparison.getList()) {
            if (fronte.type == type) {
                changes++;
            }
        }
        String testo = "";
        if (changes > 0) {
            type--;
            int definizione = changes == 1 ? singulars[type] : plurals[type];
            testo = "\t\t+" + changes + " " + getString(definizione).toLowerCase() + "\n";
        }
        return testo;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem i) {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Comparison.reset(); // resets the Comparison singleton
    }
}