
package app.familygem;

import android.widget.Toast;

import com.google.gson.Gson;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Represents the app preferences saved in '/data/data/app.familygem/files/settings.json'.
 */
public class Settings {

    /**
     * It is "start" as soon as the app is installed (when 'settings.json' doesn't exist).
     * If the installation comes from a sharing it receives a 'dateID' as "20203112005959" to download the shared tree.
     * Then it becomes null and remains null forever.
     * If 'settings.json' is deleted it re-becomes "start" and then immediately null.
     */
    public String referrer;
    public List<Tree> trees;
    /**
     * Number of the tree currently opened. '0' means not any particular tree.
     * Must be consistent with the {@link Global#gc} opened tree.
     * It is not reset by closing the tree, to load last opened tree at startup.
     */
    public int openTree;
    public boolean autoSave;
    /**
     * At startup load last opened tree.
     */
    boolean loadTree;
    /**
     * Used to display or hide all advanced tools.
     */
    public boolean expert;
    public boolean shareAgreement;
    public boolean premium;
    Diagram diagram;

    /**
     * Initializes first boot values.
     * False booleans don't need to be initialized.
     */
    void init() {
        referrer = "start";
        trees = new ArrayList<>();
        autoSave = true;
        diagram = new Diagram().init();
    }

    public int max() {
        int num = 0;
        for (Tree tree : trees) {
            if (tree.id > num)
                num = tree.id;
        }
        return num;
    }

    public void addTree(Tree tree) {
        trees.add(tree);
    }

    void renameTree(int id, String newTitle) {
        for (Tree tree : trees) {
            if (tree.id == id) {
                tree.title = newTitle;
                break;
            }
        }
        save();
    }

    public void deleteTree(int id) {
        for (Tree tree : trees) {
            if (tree.id == id) {
                trees.remove(tree);
                break;
            }
        }
        if (id == openTree) {
            openTree = 0;
        }
        save();
    }

    public void save() {
        try {
            Gson gson = new Gson();
            String json = gson.toJson(this);
            FileUtils.writeStringToFile(new File(Global.context.getFilesDir(), "settings.json"), json, "UTF-8");
        } catch (Exception e) {
            Toast.makeText(Global.context, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // The tree currently open
    public Tree getCurrentTree() {
        for (Tree alb : trees) {
            if (alb.id == openTree)
                return alb;
        }
        return null;
    }

    public Tree getTree(int treeId) {
        /* Da quando ho installato Android Studio 4.0, quando compilo con minifyEnabled true
           misteriosamente 'alberi' qui è null.
           Però non è null se DOPO c'è 'trees = Global.settings.trees'
           Davvero incomprensibile!
        */
        if (trees == null) {
            trees = Global.settings.trees;
        }
        if (trees != null)
            for (Tree tree : trees) {
                if (tree.id == treeId) {
                    if (tree.uris == null) // traghettatore inserito in Family Gem 0.7.15
                        tree.uris = new LinkedHashSet<>();
                    return tree;
                }
            }
        return null;
    }

    static class Diagram {
        int ancestors;
        int uncles;
        int descendants;
        int siblings;
        int cousins;
        boolean spouses;

        // Default values
        Diagram init() {
            ancestors = 3;
            uncles = 2;
            descendants = 3;
            siblings = 2;
            cousins = 1;
            spouses = true;
            return this;
        }
    }

    public static class Tree {
        public int id;
        public String title;
        public Set<String> dirs;
        public Set<String> uris;
        public int persons;
        public int generations;
        public int media;
        public String root;
        public List<Share> shares; // Dati identificativi delle condivisioni attraverso il tempo e lo spazio
        public String shareRoot; // Id della Person radice dell'albero in Condivisione
        /**
         * Sharing grade:
         * <ol>
         *     <li value="0">Albero creato da zero in Italia.
         *     Rimane 0 anche aggiungendo il submitter principale, condividendolo e ricevendo novità.
         *     <li value="9">Albero spedito per la condivisione in attesa di marchiare con 'passato' tutti i submitter.
         *     <li value="10">Albero ricevuto tramite condivisione in Australia. Non potrà mai più ritornare 0.
         *     <li value="20">Albero ritornato in Italia dimostratosi un derivato da uno zero (o da un 10).
         *     Solo se è 10 può diventare 20. Se per caso perde lo status di derivato ritorna 10 (mai 0).
         *     <li value="30">Albero derivato da cui sono state estratte tutte le novità OPPURE privo di novità già all'arrivo (grigio).
         *     Può essere eliminato.
         * </ol>
         */
        public int grade;
        public Set<Birthday> birthdays;

        public Tree(int id, String title, String dir, int persons, int generations, String root, List<Share> shares, int grade) {
            this.id = id;
            this.title = title;
            dirs = new LinkedHashSet<>();
            if (dir != null)
                dirs.add(dir);
            uris = new LinkedHashSet<>();
            this.persons = persons;
            this.generations = generations;
            this.root = root;
            this.shares = shares;
            this.grade = grade;
            birthdays = new HashSet<>();
        }

        public void addShare(Share share) {
            if (shares == null)
                shares = new ArrayList<>();
            shares.add(share);
        }
    }

    // The essential data of a share
    public static class Share {
        public String dateId; // On compressed date and time format: YYYYMMDDhhmmss
        public String submitter; // Submitter id

        public Share(String dateId, String submitter) {
            this.dateId = dateId;
            this.submitter = submitter;
        }
    }

    // Birthday of one person
    public static class Birthday {
        String id; // E.g. 'I123'
        String given; // 'John'
        String name; // 'John Doe III'
        long date; // Date of next birthday in Unix time
        int age; // Turned years

        public Birthday(String id, String given, String name, long date, int age) {
            this.id = id;
            this.given = given;
            this.name = name;
            this.date = date;
            this.age = age;
        }

        @Override
        public String toString() {
            DateFormat sdf = new SimpleDateFormat("d MMM y", Locale.US);
            return "[" + name + ": " + age + " (" + sdf.format(date) + ")]";
        }
    }

    /**
     * Model of the file 'settings.json' inside a backup, shared or example ZIP file.
     * It contains basic info of the zipped tree.
     */
    public static class ZippedTree {
        public String title;
        public int persons;
        public int generations;
        public String root;
        public List<Share> shares;
        /**
         * Coming from {@link Tree#grade}.
         */
        public int grade;

        ZippedTree(String title, int persons, int generations, String root, List<Share> shares, int grade) {
            this.title = title;
            this.persons = persons;
            this.generations = generations;
            this.root = root;
            this.shares = shares;
            this.grade = grade;
        }

        File save() {
            File settingsFile = new File(Global.context.getCacheDir(), "settings.json");
            Gson gson = new Gson();
            String json = gson.toJson(this);
            try {
                FileUtils.writeStringToFile(settingsFile, json, "UTF-8");
            } catch (Exception e) {
            }
            return settingsFile;
        }
    }
}
