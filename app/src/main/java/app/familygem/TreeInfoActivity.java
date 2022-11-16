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

import app.familygem.visitor.MediaList;

public class TreeInfoActivity extends BaseActivity {

    Gedcom gc;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.info_albero);
        LinearLayout layout = findViewById(R.id.info_scatola);

        final int treeId = getIntent().getIntExtra("idAlbero", 1);
        final Settings.Tree tree = Global.settings.getTree(treeId);
        final File file = new File(getFilesDir(), treeId + ".json");
        StringBuilder i = new StringBuilder(getText(R.string.title) + ": " + tree.title);
        if (!file.exists()) {
            i.append("\n\n").append(getText(R.string.item_exists_but_file)).append("\n").append(file.getAbsolutePath());
        } else {
            i.append("\n").append(getText(R.string.file)).append(": ").append(file.getAbsolutePath());
            gc = TreesActivity.openGedcomTemporarily(treeId, false);
            if (gc == null)
                i.append("\n\n").append(getString(R.string.no_useful_data));
            else {
                // Automatic or on-demand data update
                if (tree.persons < 100) {
                    refreshData(gc, tree);
                } else {
                    Button updateButton = findViewById(R.id.info_aggiorna);
                    updateButton.setVisibility(View.VISIBLE);
                    updateButton.setOnClickListener(v -> {
                        refreshData(gc, tree);
                        recreate();
                    });
                }
                i
                        .append("\n\n")
						.append(getText(R.string.persons)).append(": ").append(tree.persons)
						.append("\n")
						.append(getText(R.string.families)).append(": ").append(gc.getFamilies().size())
						.append("\n")
						.append(getText(R.string.generations)).append(": ").append(tree.generations)
						.append("\n")
						.append(getText(R.string.media)).append(": ").append(tree.media)
						.append("\n")
						.append(getText(R.string.sources)).append(": ").append(gc.getSources().size())
						.append("\n")
						.append(getText(R.string.repositories)).append(": ").append(gc.getRepositories().size());
                if (tree.root != null) {
                    i.append("\n").append(getText(R.string.root)).append(": ").append(U.properName(gc.getPerson(tree.root)));
                }
                if (tree.shares != null && !tree.shares.isEmpty()) {
                    i.append("\n\n").append(getText(R.string.shares)).append(":");
                    for (Settings.Share share : tree.shares) {
                        i.append("\n").append(dataIdToDate(share.dateId));
                        if (gc.getSubmitter(share.submitter) != null)
                            i.append(" - ").append(submitterName(gc.getSubmitter(share.submitter)));
                    }
                }
            }
        }
        ((TextView) findViewById(R.id.info_statistiche)).setText(i.toString());

        Button headerButton = layout.findViewById(R.id.info_gestisci_testata);
        if (gc != null) {
            Header h = gc.getHeader();
            if (h == null) {
                headerButton.setText(R.string.create_header);
                headerButton.setOnClickListener(view -> {
                    gc.setHeader(NewTree.createHeader(file.getName()));
                    U.saveJson(gc, treeId);
                    recreate();
                });
            } else {
                layout.findViewById(R.id.info_testata).setVisibility(View.VISIBLE);
                if (h.getFile() != null)
                    place(getText(R.string.file), h.getFile());
                if (h.getCharacterSet() != null) {
                    place(getText(R.string.characrter_set), h.getCharacterSet().getValue());
                    place(getText(R.string.version), h.getCharacterSet().getVersion());
                }
                space();   // a little space
                place(getText(R.string.language), h.getLanguage());
                space();
                place(getText(R.string.copyright), h.getCopyright());
                space();
                if (h.getGenerator() != null) {
                    place(getText(R.string.software), h.getGenerator().getName() != null ? h.getGenerator().getName() : h.getGenerator().getValue());
                    place(getText(R.string.version), h.getGenerator().getVersion());
                    if (h.getGenerator().getGeneratorCorporation() != null) {
                        place(getText(R.string.corporation), h.getGenerator().getGeneratorCorporation().getValue());
                        if (h.getGenerator().getGeneratorCorporation().getAddress() != null)
                            place(getText(R.string.address), h.getGenerator().getGeneratorCorporation().getAddress().getDisplayValue()); // non è male
                        place(getText(R.string.telephone), h.getGenerator().getGeneratorCorporation().getPhone());
                        place(getText(R.string.fax), h.getGenerator().getGeneratorCorporation().getFax());
                    }
                    space();
                    if (h.getGenerator().getGeneratorData() != null) {
                        place(getText(R.string.source), h.getGenerator().getGeneratorData().getValue());
                        place(getText(R.string.date), h.getGenerator().getGeneratorData().getDate());
                        place(getText(R.string.copyright), h.getGenerator().getGeneratorData().getCopyright());
                    }
                }
                space();
                if (h.getSubmitter(gc) != null)
                    place(getText(R.string.submitter), submitterName(h.getSubmitter(gc))); // todo: make it clickable?
                if (gc.getSubmission() != null)
                    place(getText(R.string.submission), gc.getSubmission().getDescription()); // todo: clickable
                space();
                if (h.getGedcomVersion() != null) {
                    place(getText(R.string.gedcom), h.getGedcomVersion().getVersion());
                    place(getText(R.string.form), h.getGedcomVersion().getForm());
                }
                place(getText(R.string.destination), h.getDestination());
                space();
                if (h.getDateTime() != null) {
                    place(getText(R.string.date), h.getDateTime().getValue());
                    place(getText(R.string.time), h.getDateTime().getTime());
                }
                space();
                for (Extension est : U.findExtensions(h)) {    // each extension in its own line
                    place(est.name, est.text);
                }
                space();
                if (ruler != null)
                    ((TableLayout) findViewById(R.id.info_tabella)).removeView(ruler);

                // Button to update the GEDCOM header with the Family Gem parameters
                headerButton.setOnClickListener(view -> {
                    h.setFile(treeId + ".json");
                    CharacterSet charSet = h.getCharacterSet();
                    if (charSet == null) {
                        charSet = new CharacterSet();
                        h.setCharacterSet(charSet);
                    }
                    charSet.setValue("UTF-8");
                    charSet.setVersion(null);

                    Locale loc = new Locale(Locale.getDefault().getLanguage());
                    h.setLanguage(loc.getDisplayLanguage(Locale.ENGLISH));

                    Generator generator = h.getGenerator();
                    if (generator == null) {
                        generator = new Generator();
                        h.setGenerator(generator);
                    }
                    generator.setValue("FAMILY_GEM");
                    generator.setName(getString(R.string.app_name));
                    //generator.setVersion( BuildConfig.VERSION_NAME ); // will saveJson()
                    generator.setGeneratorCorporation(null);

                    GedcomVersion gedcomVersion = h.getGedcomVersion();
                    if (gedcomVersion == null) {
                        gedcomVersion = new GedcomVersion();
                        h.setGedcomVersion(gedcomVersion);
                    }
                    gedcomVersion.setVersion("5.5.1");
                    gedcomVersion.setForm("LINEAGE-LINKED");
                    h.setDestination(null);

                    U.saveJson(gc, treeId);
                    recreate();
                });

                U.placeNotes(layout, h, true);
            }
            // Extensions of Gedcom, i.e. non-standard level 0 zero tags
            for (Extension est : U.findExtensions(gc)) {
                U.place(layout, est.name, est.text);
            }
        } else
            headerButton.setVisibility(View.GONE);
    }

    String dataIdToDate(String id) {
        if (id == null) return "";
        return id.substring(0, 4) + "-" + id.substring(4, 6) + "-" + id.substring(6, 8) + " "
                + id.substring(8, 10) + ":" + id.substring(10, 12) + ":" + id.substring(12);
    }

    static String submitterName(Submitter submitter) {
        String name = submitter.getName();
        if (name == null)
            name = "[" + Global.context.getString(R.string.no_name) + "]";
        else if (name.isEmpty())
            name = "[" + Global.context.getString(R.string.empty_name) + "]";
        return name;
    }

    /**
	 * Refresh the data displayed below the tree title in {@link TreesActivity} list
	 */
    static void refreshData(Gedcom gedcom, Settings.Tree treeItem) {
        treeItem.persons = gedcom.getPeople().size();
        treeItem.generations = countGenerations(gedcom, U.getRootId(gedcom, treeItem));
        MediaList mediaList = new MediaList(gedcom, 0);
        gedcom.accept(mediaList);
        treeItem.media = mediaList.list.size();
        Global.settings.save();
    }

    boolean putText;  // prevents putting more than one consecutive space()

    void place(CharSequence title, String text) {
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
            ((TableLayout) findViewById(R.id.info_tabella)).addView(row);
            putText = true;
        }
    }

    TableRow ruler;

    void space() {
        if (putText) {
            ruler = new TableRow(getApplicationContext());
            View cell = new View(getApplicationContext());
            cell.setBackgroundResource(R.color.primario);
            ruler.addView(cell);
            TableRow.LayoutParams param = (TableRow.LayoutParams) cell.getLayoutParams();
            param.weight = 1;
            param.span = 2;
            param.height = 1;
            param.topMargin = 5;
            param.bottomMargin = 5;
            cell.setLayoutParams(param);
            ((TableLayout) findViewById(R.id.info_tabella)).addView(ruler);
            putText = false;
        }
    }

    static int genMin;
    static int genMax;

    public static int countGenerations(Gedcom gc, String root) {
        if (gc.getPeople().isEmpty())
            return 0;
        genMin = 0;
        genMax = 0;
        goToUpEarliestGeneration(gc.getPerson(root), gc, 0);
        goDownToEarliestGeneration(gc.getPerson(root), gc, 0);
        // Removes the 'gen' extension from people to allow for later counting
        for (Person person : gc.getPeople()) {
            person.getExtensions().remove("gen");
            if (person.getExtensions().isEmpty())
                person.setExtensions(null);
        }
        return 1 - genMin + genMax;
    }

    /**
	 * accepts a Person and finds the number of the earliest generation of ancestors
	 */
    static void goToUpEarliestGeneration(Person person, Gedcom gc, int gen) {
        if (gen < genMin)
            genMin = gen;
        // adds the extension to indicate that it has passed from this Person
        person.putExtension("gen", gen);
        // if he is a progenitor it counts the generations of descendants or goes back to any other marriages
        if (person.getParentFamilies(gc).isEmpty())
            goDownToEarliestGeneration(person, gc, gen);
        for (Family family : person.getParentFamilies(gc)) {
            // intercept any siblings of the root
            for (Person sibling : family.getChildren(gc))
                if (sibling.getExtension("gen") == null)
                    goDownToEarliestGeneration(sibling, gc, gen);
            for (Person father : family.getHusbands(gc))
                if (father.getExtension("gen") == null)
                    goToUpEarliestGeneration(father, gc, gen - 1);
            for (Person mother : family.getWives(gc))
                if (mother.getExtension("gen") == null)
                    goToUpEarliestGeneration(mother, gc, gen - 1);
        }
    }

    /**
	 * receives a Person and finds the number of the earliest generation of descendants
	 * */
    static void goDownToEarliestGeneration(Person person, Gedcom gc, int gen) {
        if (gen > genMax)
            genMax = gen;
        person.putExtension("gen", gen);
        for (Family family : person.getSpouseFamilies(gc)) {
            // also identifies the spouses' family
            for (Person wife : family.getWives(gc))
                if (wife.getExtension("gen") == null)
                    goToUpEarliestGeneration(wife, gc, gen);
            for (Person husband : family.getHusbands(gc))
                if (husband.getExtension("gen") == null)
                    goToUpEarliestGeneration(husband, gc, gen);
            for (Person child : family.getChildren(gc))
                if (child.getExtension("gen") == null)
                    goDownToEarliestGeneration(child, gc, gen + 1);
        }
    }

    /**
	 * back arrow in the toolbar like the hardware one
	 * */
    @Override
    public boolean onOptionsItemSelected(MenuItem i) {
        onBackPressed();
        return true;
    }
}
