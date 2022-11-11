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
 * Visitor that generates in Memory a hierarchic stack of objects from a leader record to a given object (target)
 * E.g. Person > inline Media
 * or Family > inline Note > SourceCitation > inline Note
 */
public class TrovaPila extends Visitor {

	private List<Memory.Passo> stack;
	private Object target;
	private boolean found;

	public TrovaPila(Gedcom gc, Object target) {
		stack = Memory.addPila(); // In a new dedicated stack
		this.target = target;
		gc.accept(this);
	}

	private boolean opera(Object object, String tag, boolean isLeader) {
		if( !found ) {
			if( isLeader )
				stack.clear(); // A leader restarts the stack from the beginning
			Memory.Passo passo = new Memory.Passo();
			passo.oggetto = object;
			passo.tag = tag;
			if( !isLeader )
				passo.filotto = true; // li marchia per eliminarli poi in blocco onBackPressed
			stack.add(passo);
		}
		if( object.equals(target) ) {
			Iterator<Memory.Passo> passi = stack.iterator();
			while( passi.hasNext() ) {
				PulisciPila pulitore = new PulisciPila(target);
				((Visitable)passi.next().oggetto).accept(pulitore);
				if( pulitore.daEliminare )
					passi.remove();
			}
			found = true;
			//Memory.stampa("TrovaPila");
		}
		return true;
	}

	@Override
	public boolean visit( Header passo ) {
		return opera(passo,"HEAD",true);
	}
	@Override
	public boolean visit( Person passo ) {
		return opera(passo,"INDI",true);
	}
	@Override
	public boolean visit( Family passo ) {
		return opera(passo,"FAM",true);
	}
	@Override
	public boolean visit( Source passo ) {
		return opera(passo,"SOUR",true);
	}
	@Override
	public boolean visit( Repository passo ) {
		return opera(passo,"REPO",true);
	}
	@Override
	public boolean visit( Submitter passo ) {
		return opera(passo,"SUBM",true);
	}
	@Override
	public boolean visit( Media passo ) {
		return opera(passo,"OBJE",passo.getId()!=null);
	}
	@Override
	public boolean visit( Note passo ) {
		return opera(passo,"NOTE",passo.getId()!=null);
	}
	@Override
	public boolean visit( Name passo ) {
		return opera(passo,"NAME",false);
	}
	@Override
	public boolean visit( EventFact passo ) {
		return opera(passo,passo.getTag(),false);
	}
	@Override
	public boolean visit( SourceCitation passo ) {
		return opera(passo,"SOUR",false);
	}
	@Override
	public boolean visit( RepositoryRef passo ) {
		return opera(passo,"REPO",false);
	}
	@Override
	public boolean visit( Change passo ) {
		return opera(passo,"CHAN",false);
	}
	/* ok ma poi tanto GedcomTag non è Visitable e quindi non prosegue la visita
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