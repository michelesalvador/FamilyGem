package app.familygem.visitor;

/**
 * Closely connected to {@link FindStack}, locates in the stack the objects to keep or delete.
 */
class CleanStack extends TotalVisitor {

    private Object target;
    boolean toDelete = true;

    CleanStack(Object target) {
        this.target = target;
    }

    @Override
    boolean visit(Object object, boolean isLeader) { // The boolean is unused here
        if (object.equals(target))
            toDelete = false;
        return true;
    }
}

