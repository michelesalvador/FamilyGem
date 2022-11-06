package app.familygem.visitor;

/**
 * Closely connected to [FindStack, locate objects to keep in the stack
 * */
class CleanStack extends TotalVisitor {

	private Object scope; //scopo: scope, object, goal, aim, etc.
	boolean toDelete = true;

	CleanStack(Object scopo ) {
		this.scope = scopo;
	}

	@Override
	boolean visit(Object object, boolean isProgenitor) { // the boolean is unused here
		if( object.equals(scope) )
			toDelete = false;
		return true;
	}
}

