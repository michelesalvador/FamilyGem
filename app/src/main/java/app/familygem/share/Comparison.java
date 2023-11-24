package app.familygem.share;

import android.app.Activity;

import java.util.ArrayList;
import java.util.List;

import app.familygem.constant.Extra;

/**
 * Singleton that manages the objects of the 2 Gedcoms during the import of updates.
 */
public class Comparison {

    private static final Comparison comparison = new Comparison();
    private final List<Front> list = new ArrayList<>();
    boolean autoContinue; // Determines whether to automatically accept all updates
    int numChoices; // Total choices in case of autoContinue
    int choicesMade; // Position in case of autoContinue //Posizione in caso di autoProsegui

    static Comparison get() {
        return comparison;
    }

    public static List<Front> getList() {
        return get().list;
    }

    static Front addFront(Object object, Object object2, int type) {
        Front front = new Front();
        front.object = object;
        front.object2 = object2;
        front.type = type;
        getList().add(front);
        return front;
    }

    /**
     * Returns the currently active front.
     */
    static Front getFront(Activity activity) {
        return getList().get(activity.getIntent().getIntExtra(Extra.POSITION, 0) - 1);
    }

    /**
     * To call when exiting the comparison process.
     */
    static void reset() {
        getList().clear();
        get().autoContinue = false;
    }

    static class Front {
        Object object;
        Object object2;
        int type; // Number from 1 to 7 that defines the type: 1 Note -> 7 Family
        boolean canBothAddAndReplace; // Has both the options: add object2 to the tree and replace object with object2
        /**
         * what to do with these two objects:
         * 0: nothing
         * 1: object2 is added to the tree
         * 2: object2 replaces object
         * 3: object is deleted
         */
        int destiny;
    }
}
