package app.familygem.visitor;

import org.folg.gedcom.model.Change;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Header;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.RepositoryRef;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.Submitter;
import org.folg.gedcom.model.Visitable;
import org.folg.gedcom.model.Visitor;
import java.util.Iterator;
import java.util.List;
import app.familygem.Memory;
/**
 *
 * // Visitor that produces in Memory the hierarchical stack of objects between the parent record and a given object
 * // e.g. Person> Simple media
 * // or Family> Note> SourceCitation> Simple Note
 *
 * // Visitatore che produce in Memoria la pila gerarchica degli oggetti tra il record capostipite e un object dato
 * // ad es. Person > Media semplice
 * // oppure Family > Note > SourceCitation > Note semplice
 * */
public class FindStack extends Visitor {

	private List<Memory.Step> stack;
	private Object scope;
	private boolean found;

	public FindStack(Gedcom gc, Object scopo ) {
		stack = Memory.addStack(); //in a new stack on purpose
		this.scope = scopo;
		gc.accept( this );
	}

	private boolean opera( Object object, String tag, boolean progenitor ) {
		if( !found) {
			if( progenitor )
				stack.clear(); // every progenitor makes a stack start all over again
			Memory.Step step = new Memory.Step();
			step.object = object;
			step.tag = tag;
			if( !progenitor )
				step.clearStackOnBackPressed = true; // onBackPressed marks them to delete them in bulk
			stack.add(step);
		}
		if( object.equals(scope) ) {
			Iterator<Memory.Step> steps = stack.iterator();
			while( steps.hasNext() ) {
				CleanStack janitor = new CleanStack(scope);
				((Visitable)steps.next().object).accept( janitor );
				if( janitor.toDelete)
					steps.remove();
			}
			found = true;
			//Memoria.stampa("FindStack"); log?
		}
		return true;
	}

	@Override
	public boolean visit( Header step ) {
		return opera(step,"HEAD",true);
	}
	@Override
	public boolean visit( Person step ) {
		return opera(step,"INDI",true);
	}
	@Override
	public boolean visit( Family step ) {
		return opera(step,"FAM",true);
	}
	@Override
	public boolean visit( Source step ) {
		return opera(step,"SOUR",true);
	}
	@Override
	public boolean visit( Repository step ) {
		return opera(step,"REPO",true);
	}
	@Override
	public boolean visit( Submitter step ) {
		return opera(step,"SUBM",true);
	}
	@Override
	public boolean visit( Media step ) {
		return opera(step,"OBJE",step.getId()!=null);
	}
	@Override
	public boolean visit( Note step ) {
		return opera(step,"NOTE",step.getId()!=null);
	}
	@Override
	public boolean visit( Name step ) {
		return opera(step,"NAME",false);
	}
	@Override
	public boolean visit( EventFact step ) {
		return opera(step,step.getTag(),false);
	}
	@Override
	public boolean visit( SourceCitation step ) {
		return opera(step,"SOUR",false);
	}
	@Override
	public boolean visit( RepositoryRef step ) {
		return opera(step,"REPO",false);
	}
	@Override
	public boolean visit( Change step ) {
		return opera(step,"CHAN",false);
	}
	/* ok but then GedcomTag is not Visitable and therefore does not continue the visit
	@Override
	public boolean visit( String chiave, Object estensioni ) {
		if( chiave.equals("folg.more_tags") ) {
			for( GedcomTag est : (List<GedcomTag>)estensioni ) {
				//s.l(est.getClass().getName()+" "+est.getTag());
				opera( est, est.getTag(), false );
			}
		}
		return true;
	}*/
}