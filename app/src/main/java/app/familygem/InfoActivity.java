package app.familygem;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.folg.gedcom.model.CharacterSet;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.GedcomVersion;
import org.folg.gedcom.model.Generator;
import org.folg.gedcom.model.Header;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Submitter;

import java.io.File;
import java.util.Locale;

import app.familygem.constant.Extra;
import app.familygem.util.TreeUtils;
import app.familygem.visitor.MediaList;

public class InfoActivity extends BaseActivity {

    Gedcom gc;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.info_albero);
        LinearLayout scatola = findViewById(R.id.info_scatola);

        final int treeId = getIntent().getIntExtra(Extra.TREE_ID, 1);
        final Settings.Tree tree = Global.settings.getTree(treeId);
        final File file = new File(getFilesDir(), treeId + ".json");
        String i = getText(R.string.title) + ": " + tree.title;
        if (!file.exists()) {
            i += "\n\n" + getText(R.string.item_exists_but_file) + "\n" + file.getAbsolutePath();
        } else {
            i += "\n" + getText(R.string.file) + ": " + file.getAbsolutePath();
            gc = TreeUtils.INSTANCE.openGedcomTemporarily(treeId, false);
            if (gc == null)
                i += "\n\n" + getString(R.string.no_useful_data);
            else {
                // Aggiornamento dei dati automatico o su richiesta
                if (tree.persons < 100) {
                    refreshData(gc, tree);
                } else {
                    Button bottoneAggiorna = findViewById(R.id.info_aggiorna);
                    bottoneAggiorna.setVisibility(View.VISIBLE);
                    bottoneAggiorna.setOnClickListener(v -> {
                        refreshData(gc, tree);
                        recreate();
                    });
                }
                i += "\n\n" + getText(R.string.persons) + ": " + tree.persons
                        + "\n" + getText(R.string.families) + ": " + gc.getFamilies().size()
                        + "\n" + getText(R.string.generations) + ": " + tree.generations
                        + "\n" + getText(R.string.media) + ": " + tree.media
                        + "\n" + getText(R.string.sources) + ": " + gc.getSources().size()
                        + "\n" + getText(R.string.repositories) + ": " + gc.getRepositories().size();
                if (tree.root != null) {
                    i += "\n" + getText(R.string.root) + ": " + U.properName(gc.getPerson(tree.root));
                }
                if (tree.shares != null && !tree.shares.isEmpty()) {
                    i += "\n\n" + getText(R.string.shares) + ":";
                    for (Settings.Share share : tree.shares) {
                        i += "\n" + dataIdVersoData(share.dateId);
                        if (gc.getSubmitter(share.submitter) != null)
                            i += " - " + submitterName(gc.getSubmitter(share.submitter));
                    }
                }
            }
        }
        ((TextView)findViewById(R.id.info_statistiche)).setText(i);

        Button bottoneHeader = scatola.findViewById(R.id.info_gestisci_testata);
        if (gc != null) {
            Header h = gc.getHeader();
            if (h == null) {
                bottoneHeader.setText(R.string.create_header);
                bottoneHeader.setOnClickListener(view -> {
                    gc.setHeader(NewTreeActivity.createHeader(file.getName()));
                    U.saveJson(gc, treeId);
                    recreate();
                });
            } else {
                scatola.findViewById(R.id.info_testata).setVisibility(View.VISIBLE);
                if (h.getFile() != null)
                    poni(getText(R.string.file), h.getFile());
                if (h.getCharacterSet() != null) {
                    poni(getText(R.string.characrter_set), h.getCharacterSet().getValue());
                    poni(getText(R.string.version), h.getCharacterSet().getVersion());
                }
                spazio(); // uno spazietto
                poni(getText(R.string.language), h.getLanguage());
                spazio();
                poni(getText(R.string.copyright), h.getCopyright());
                spazio();
                if (h.getGenerator() != null) {
                    poni(getText(R.string.software), h.getGenerator().getName() != null ? h.getGenerator().getName() : h.getGenerator().getValue());
                    poni(getText(R.string.version), h.getGenerator().getVersion());
                    if (h.getGenerator().getGeneratorCorporation() != null) {
                        poni(getText(R.string.corporation), h.getGenerator().getGeneratorCorporation().getValue());
                        if (h.getGenerator().getGeneratorCorporation().getAddress() != null)
                            poni(getText(R.string.address), h.getGenerator().getGeneratorCorporation().getAddress().getDisplayValue()); // non è male
                        poni(getText(R.string.telephone), h.getGenerator().getGeneratorCorporation().getPhone());
                        poni(getText(R.string.fax), h.getGenerator().getGeneratorCorporation().getFax());
                    }
                    spazio();
                    if (h.getGenerator().getGeneratorData() != null) {
                        poni(getText(R.string.source), h.getGenerator().getGeneratorData().getValue());
                        poni(getText(R.string.date), h.getGenerator().getGeneratorData().getDate());
                        poni(getText(R.string.copyright), h.getGenerator().getGeneratorData().getCopyright());
                    }
                }
                spazio();
                if (h.getSubmitter(gc) != null)
                    poni(getText(R.string.submitter), submitterName(h.getSubmitter(gc))); // todo: renderlo cliccabile?
                if (gc.getSubmission() != null)
                    poni(getText(R.string.submission), gc.getSubmission().getDescription()); // todo: cliccabile
                spazio();
                if (h.getGedcomVersion() != null) {
                    poni(getText(R.string.gedcom), h.getGedcomVersion().getVersion());
                    poni(getText(R.string.form), h.getGedcomVersion().getForm());
                }
                poni(getText(R.string.destination), h.getDestination());
                spazio();
                if (h.getDateTime() != null) {
                    poni(getText(R.string.date), h.getDateTime().getValue());
                    poni(getText(R.string.time), h.getDateTime().getTime());
                }
                spazio();
                for (Extension est : U.findExtensions(h)) {    // ogni estensione nella sua riga
                    poni(est.name, est.text);
                }
                spazio();
                if (row != null)
                    ((TableLayout)findViewById(R.id.info_tabella)).removeView(row);

                // Bottone per aggiorna l'header GEDCOM coi parametri di Family Gem
                bottoneHeader.setOnClickListener(view -> {
                    h.setFile(treeId + ".json");
                    CharacterSet caratteri = h.getCharacterSet();
                    if (caratteri == null) {
                        caratteri = new CharacterSet();
                        h.setCharacterSet(caratteri);
                    }
                    caratteri.setValue("UTF-8");
                    caratteri.setVersion(null);

                    Locale loc = new Locale(Locale.getDefault().getLanguage());
                    h.setLanguage(loc.getDisplayLanguage(Locale.ENGLISH));

                    Generator programma = h.getGenerator();
                    if (programma == null) {
                        programma = new Generator();
                        h.setGenerator(programma);
                    }
                    programma.setValue("FAMILY_GEM");
                    programma.setName(getString(R.string.app_name));
                    //programma.setVersion( BuildConfig.VERSION_NAME ); // lo farà salvaJson()
                    programma.setGeneratorCorporation(null);

                    GedcomVersion versioneGc = h.getGedcomVersion();
                    if (versioneGc == null) {
                        versioneGc = new GedcomVersion();
                        h.setGedcomVersion(versioneGc);
                    }
                    versioneGc.setVersion("5.5.1");
                    versioneGc.setForm("LINEAGE-LINKED");
                    h.setDestination(null);

                    U.saveJson(gc, treeId);
                    recreate();
                });

                U.placeNotes(scatola, h, true);
            }
            // Estensioni del Gedcom, ovvero tag non standard di livello 0 zero
            for (Extension est : U.findExtensions(gc)) {
                U.place(scatola, est.name, est.text);
            }
        } else
            bottoneHeader.setVisibility(View.GONE);
    }

    String dataIdVersoData(String id) {
        if (id == null) return "";
        return id.substring(0, 4) + "-" + id.substring(4, 6) + "-" + id.substring(6, 8) + " "
                + id.substring(8, 10) + ":" + id.substring(10, 12) + ":" + id.substring(12);
    }

    public static String submitterName(Submitter submitter) {
        String nome = submitter.getName();
        if (nome == null)
            nome = "[" + Global.context.getString(R.string.no_name) + "]";
        else if (nome.isEmpty())
            nome = "[" + Global.context.getString(R.string.empty_name) + "]";
        return nome;
    }

    // Refresh the data displayed below the tree title in TreesActivity list
    static void refreshData(Gedcom gedcom, Settings.Tree treeItem) {
        treeItem.persons = gedcom.getPeople().size();
        treeItem.generations = quanteGenerazioni(gedcom, U.getRootId(gedcom, treeItem));
        MediaList visitaMedia = new MediaList(gedcom, 0);
        gedcom.accept(visitaMedia);
        treeItem.media = visitaMedia.list.size();
        Global.settings.save();
    }

    boolean testoMesso;  // impedisce di mettere più di uno spazio() consecutivo

    void poni(CharSequence title, String text) {
        if (text != null) {
            TableRow row = new TableRow(this);
            TextView cell1 = new TextView(this);
            cell1.setTextSize(14);
            cell1.setTypeface(null, Typeface.BOLD);
            cell1.setPaddingRelative(0, 0, 10, 0);
            cell1.setGravity(Gravity.END); // Does not work on RTL layout
            cell1.setText(title);
            row.addView(cell1);
            TextView cell2 = new TextView(this);
            cell2.setTextSize(14);
            cell2.setPadding(0, 0, 0, 0);
            cell2.setGravity(Gravity.START);
            cell2.setText(text);
            row.addView(cell2);
            ((TableLayout)findViewById(R.id.info_tabella)).addView(row);
            testoMesso = true;
        }
    }

    TableRow row;

    void spazio() {
        if (testoMesso) {
            row = new TableRow(this);
            View cella = new View(this);
            cella.setBackgroundResource(R.color.primary);
            row.addView(cella);
            TableRow.LayoutParams param = (TableRow.LayoutParams)cella.getLayoutParams();
            param.weight = 1;
            param.span = 2;
            param.height = 1;
            param.topMargin = 5;
            param.bottomMargin = 5;
            cella.setLayoutParams(param);
            ((TableLayout)findViewById(R.id.info_tabella)).addView(row);
            testoMesso = false;
        }
    }

    static int genMin;
    static int genMax;

    public static int quanteGenerazioni(Gedcom gc, String radice) {
        if (gc.getPeople().isEmpty())
            return 0;
        genMin = 0;
        genMax = 0;
        risaliGenerazioni(gc.getPerson(radice), gc, 0);
        discendiGenerazioni(gc.getPerson(radice), gc, 0);
        // Rimuove dalle persone l'estensione 'gen' per permettere successivi conteggi
        for (Person person : gc.getPeople()) {
            person.getExtensions().remove("gen");
            if (person.getExtensions().isEmpty())
                person.setExtensions(null);
        }
        return 1 - genMin + genMax;
    }

    // riceve una Person e trova il numero della generazione di antenati più remota
    static void risaliGenerazioni(Person person, Gedcom gc, int gen) {
        if (gen < genMin)
            genMin = gen;
        // aggiunge l'estensione per indicare che è passato da questa Persona
        person.putExtension("gen", gen);
        // se è un capostipite va a contare le generazioni di discendenti o risale su eventuali altri matrimoni
        if (person.getParentFamilies(gc).isEmpty())
            discendiGenerazioni(person, gc, gen);
        for (Family family : person.getParentFamilies(gc)) {
            // intercetta eventuali fratelli della radice
            for (Person sibling : family.getChildren(gc))
                if (sibling.getExtension("gen") == null)
                    discendiGenerazioni(sibling, gc, gen);
            for (Person father : family.getHusbands(gc))
                if (father.getExtension("gen") == null)
                    risaliGenerazioni(father, gc, gen - 1);
            for (Person mother : family.getWives(gc))
                if (mother.getExtension("gen") == null)
                    risaliGenerazioni(mother, gc, gen - 1);
        }
    }

    // riceve una Person e trova il numero della generazione più remota di discendenti
    static void discendiGenerazioni(Person person, Gedcom gc, int gen) {
        if (gen > genMax)
            genMax = gen;
        person.putExtension("gen", gen);
        for (Family family : person.getSpouseFamilies(gc)) {
            // individua anche la famiglia dei coniugi
            for (Person wife : family.getWives(gc))
                if (wife.getExtension("gen") == null)
                    risaliGenerazioni(wife, gc, gen);
            for (Person husband : family.getHusbands(gc))
                if (husband.getExtension("gen") == null)
                    risaliGenerazioni(husband, gc, gen);
            for (Person child : family.getChildren(gc))
                if (child.getExtension("gen") == null)
                    discendiGenerazioni(child, gc, gen + 1);
        }
    }

    // freccia indietro nella toolbar come quella hardware
    @Override
    public boolean onOptionsItemSelected(MenuItem i) {
        onBackPressed();
        return true;
    }
}
