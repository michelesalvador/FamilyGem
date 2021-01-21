// Visitatore che produce in Memoria la pila gerarchica degli oggetti tra il record capostipite e un oggetto dato
// ad es. Person > Media semplice
// oppure Family > Note > SourceCitation > Note semplice

package app.familygem.visita;

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
import app.familygem.Memoria;

public class TrovaPila extends Visitor {

	private List<Memoria.Passo> pila;
	private Object scopo;
	private boolean trovato;

	public TrovaPila( Gedcom gc, Object scopo ) {
		pila = Memoria.addPila(); // in una nuova pila apposta
		this.scopo = scopo;
		gc.accept( this );
	}

	private boolean opera( Object oggetto, String tag, boolean capostipite ) {
		if( !trovato ) {
			if( capostipite )
				pila.clear(); // ogni capostipite fa ricominciare da capo una pila
			Memoria.Passo passo = new Memoria.Passo();
			passo.oggetto = oggetto;
			passo.tag = tag;
			if( !capostipite )
				passo.filotto = true; // li marchia per eliminarli poi in blocco onBackPressed
			pila.add(passo);
		}
		if( oggetto.equals(scopo) ) {
			Iterator<Memoria.Passo> passi = pila.iterator();
			while( passi.hasNext() ) {
				PulisciPila pulitore = new PulisciPila( scopo );
				((Visitable)passi.next().oggetto).accept( pulitore );
				if( pulitore.daEliminare )
					passi.remove();
			}
			trovato = true;
			//Memoria.stampa("TrovaPila");
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