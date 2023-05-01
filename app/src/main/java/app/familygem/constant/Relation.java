package app.familygem.constant;

public enum Relation {
    PARENT, SIBLING, HALF_SIBLING, PARTNER, CHILD;

    public static Relation get(int num) {
        switch (num) {
            case 0:
                return PARENT;
            case 1:
                return SIBLING;
            case 2:
                return PARTNER;
            case 3:
                return CHILD;
        }
        return null;
    }
}
