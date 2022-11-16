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
 * Class that represents the preferences saved in 'settings.json'
 */
public class Settings {

    /**
     * It's 'start' as soon as the app is installed (i.e. when 'files/settings.json' doesn't exist)
     * If the installation comes from a share it welcomes (accepts/receives/contains) a dateId type '20191003215337'
     * Soon becomes null and stays null unless all data is deleted
     */
    String referrer;
    List<Tree> trees;
    /**
     * Number of the tree currently opened. 0 means not a particular tree.
     * Must be consistent with the 'Global.gc' opened tree.
     * It is not reset by closing the tree, to be reused by 'Load last opened tree at startup'.
     */
    public int openTree;
    boolean autoSave;
    boolean loadTree;
    public boolean expert;
    boolean shareAgreement;
    Diagram diagram;

    /**
     * First boot values
     * False booleans don't need to be initialized
     */
    void init() {
        referrer = "start";
        trees = new ArrayList<>();
        autoSave = true;
        diagram = new Diagram().init();
    }

    int max() {
        int num = 0;
        for (Tree tree : trees) {
            if (tree.id > num)
                num = tree.id;
        }
        return num;
    }

    void add(Tree tree) {
        trees.add(tree);
    }

    void rename(int id, String newName) {
        for (Tree tree : trees) {
            if (tree.id == id) {
                tree.title = newName;
                break;
            }
        }
        save();
    }

    void deleteTree(int id) {
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
            FileUtils.writeStringToFile(new File(Global.context.getFilesDir(), "settings.json"), new Gson().toJson(this), "UTF-8"); //TODO extract all uses/new instances of Gson to global
        } catch (Exception e) {
            Toast.makeText(Global.context, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * The tree currently open
     */
    Tree getCurrentTree() {
        for (Tree alb : trees) {
            if (alb.id == openTree)
                return alb;
        }
        return null;
    }

    Tree getTree(int treeId) {
		/* 	Since I installed Android Studio 4.0, when I compile with minifyEnabled true
			mysteriously 'trees' here is null.
			But it is not null if AFTER there is 'trees = Global.settings.trees'
			Really incomprehensible!
		*/
        if (trees == null) {
            trees = Global.settings.trees;
        }
        if (trees != null)
            for (Tree tree : trees) {
                if (tree.id == treeId) {
                    if (tree.uris == null) // ferryman ( ?? "traghettatore") added to Family Gem 0.7.15
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

        /**
         * Default values
         */
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

    static class Tree {
        int id;
        String title;
        Set<String> dirs;
        Set<String> uris;
        int persons;
        int generations;
        int media;
        String root;
        /**
         * identification data of shares across time and space
         */
        List<Share> shares;
        /**
         * id of the Person root of the Sharing tree
         */
        String shareRoot;

        /**
         * "grade" (degree?) of sharing
         * <ul>
         * <li>0 tree created from scratch in Italy. it stays 0 even adding main submitter, sharing it and getting news</li>
         * <li>9 tree sent for sharing waiting to mark all submitters with 'passed'</li>
         * <li>10 tree received via sharing in Australia. Can never return to 0</li>
         * <li>20 tree returned to Italy proved to be a derivative of a zero (or a 10). Only if it is 10 can it become 20. If by chance it loses the status of derivative it returns 10 (never 0)</il>
         * <li>30 derived tree from which all novelties have been extracted OR with no novelties already upon arrival (gray). Disposable</il>
         * </ul>
         */
        int grade;

        Set<Birthday> birthdays;

        Tree(int id, String title, String dir, int persons, int generations, String root, List<Share> shares, int grade) {
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

        void aggiungiCondivisione(Share share) {
            if (shares == null)
                shares = new ArrayList<>();
            shares.add(share);
        }
    }

    /**
     * The essential data of a share
     */
    static class Share {
        /**
         * on compressed date and time format: YYYYMMDDhhmmss
         */
        String dateId;
        /**
         * Submitter id
         */
        String submitter;

        Share(String dateId, String submitter) {
            this.dateId = dateId;
            this.submitter = submitter;
        }
    }

    /**
     * Birthday of one person
     */
    static class Birthday {
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
     * Blueprint of the file 'settings.json' inside a backup, share or example ZIP file
     * It contains basic info of the zipped tree
     */
    static class ZippedTree {
        String title;
        int persons;
        int generations;
        String root;
        List<Share> shares;
        int grade; // the destination "grade" (degree?) of the zipped tree

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
            try {
                FileUtils.writeStringToFile(settingsFile, new Gson().toJson(this), "UTF-8");
            } catch (Exception e) {
            }
            return settingsFile;
        }
    }
}