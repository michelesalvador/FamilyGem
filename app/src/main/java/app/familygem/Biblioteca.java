// Lista delle Fonti (Sources)
// TODO: tutto da semplificare copiando Chiesa o Anagrafe. o forse no

package app.familygem;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import android.widget.TextView;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import app.familygem.dettaglio.Fonte;
import static app.familygem.Globale.gc;

public class Biblioteca extends Fragment {

    List<Source> listaFonti;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState ) {
		listaFonti = gc.getSources();
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle( listaFonti.size() + " " + getString(R.string.sources).toLowerCase() );
		setHasOptionsMenu(true);
		if( Globale.ordineBiblioteca == 3 ) {
			for( Source fonte : listaFonti ) {
				if( fonte.getExtension("citaz") == null )
					fonte.putExtension( "citaz", quanteCitazioni(fonte) );	// 1 minuto
			}
		}
		Collections.sort( listaFonti, new Comparator<Source>() {
			public int compare( Source f1, Source f2 ) {
				switch( Globale.ordineBiblioteca ) {
					case 1:	// Ordina per id
						// TODO: dà per scontato che ID della fonte sia tipo 'S1' ma può anche essere 'SOURCE1'
						return Integer.parseInt(f1.getId().substring(1)) - Integer.parseInt(f2.getId().substring(1));
					case 2:	// Ordine alfabeto
						return titoloFonte(f1).compareToIgnoreCase( titoloFonte(f2) );
					case 3:	// Ordina per numero di citazioni
						return U.castaJsonInt(f2.getExtension("citaz")) - U.castaJsonInt(f1.getExtension("citaz"));
				}
				return 0;
			}
		});
        View vista = inflater.inflate(R.layout.ricicla_vista, container, false);
		RecyclerView vistaFonti = vista.findViewById( R.id.riciclatore );
		vistaFonti.setAdapter( new BibliotecAdapter() );
        return vista;
    }

    public class BibliotecAdapter extends RecyclerView.Adapter<GestoreBiblioteca> {
		@Override
		public void onBindViewHolder( final GestoreBiblioteca gestore, int i ) {
			gestore.vistaId.setText( listaFonti.get(i).getId() );
			if( Globale.ordineBiblioteca != 1 )
				gestore.vistaId.setVisibility( View.GONE );
			gestore.vistaTitolo.setText( titoloFonte(listaFonti.get(i)) );
			Object volte = listaFonti.get(i).getExtension("citaz");
			// Conta delle citazioni con il mio metodo
			if( volte == null ) {
				volte = quanteCitazioni( listaFonti.get(i) );
				listaFonti.get(i).putExtension("citaz", volte );
			}
			gestore.vistaVolte.setText( String.valueOf(volte) );
		}
        @Override
        public GestoreBiblioteca onCreateViewHolder( ViewGroup parent, int tipo ) {
            View vistaFonte = LayoutInflater.from( parent.getContext() )
					.inflate(R.layout.biblioteca_pezzo, parent, false);
	        registerForContextMenu( vistaFonte );
            return new GestoreBiblioteca( vistaFonte );
        }
		@Override
		public int getItemCount() {
			return listaFonti.size();
		}
    }

    class GestoreBiblioteca extends RecyclerView.ViewHolder {
        TextView vistaId;
        TextView vistaTitolo;
		TextView vistaVolte;
        GestoreBiblioteca( View vista ) {
        	super( vista );
			vistaId = vista.findViewById( R.id.biblioteca_id );
			vistaTitolo = vista.findViewById( R.id.biblioteca_titolo );
			vistaVolte = vista.findViewById( R.id.biblioteca_volte );
			vista.setOnClickListener( new View.OnClickListener() {
				@Override
				public void onClick( View v ) {
					// Restituisce l'id di una fonte a Individuo e Dettaglio
					if( getActivity().getIntent().getBooleanExtra("bibliotecaScegliFonte",false) ) {
						Intent intent = new Intent();
						intent.putExtra("idFonte", vistaId.getText().toString() );
						getActivity().setResult( Activity.RESULT_OK, intent );
						getActivity().finish();
					} else {
						Ponte.manda( gc.getSource( vistaId.getText().toString() ), "oggetto" );
						startActivity( new Intent( getContext(), Fonte.class ) );
					}
				}
			});
        }
    }

	// Restituisce il titolo della fonte
	static String titoloFonte( Source fon ) {
		String tit = "";
		if( fon != null )
			if( fon.getAbbreviation() != null )
				tit = fon.getAbbreviation();
			else if( fon.getTitle() != null )
				tit = fon.getTitle();
			else if( fon.getText() != null ) {
				tit = fon.getText().replaceAll("\n", " ");
				//tit = tit.length() > 35 ? tit.substring(0,35)+"…" : tit;
			} else if( fon.getPublicationFacts() != null ) {
				tit = fon.getPublicationFacts().replaceAll("\n", " ");
			}
		return tit;
	}

	// Restituisce quante volte una fonte viene citata nel Gedcom
	// Ho provato a riscriverlo come Visitor, che però è molto più lento
	int quante;
	int quanteCitazioni( Source fon ) {
		quante = 0;
		for( Person p : Globale.gc.getPeople() ) {
			cita( p, fon );
			for( Name n : p.getNames() )
				cita( n, fon );
			for( EventFact ef : p.getEventsFacts() )
				cita( ef, fon );
		}
		for( Family f : Globale.gc.getFamilies() ) {
			cita( f, fon );
			for( EventFact ef : f.getEventsFacts() )
				cita( ef, fon );
		}
		for( Note n : Globale.gc.getNotes() )
			cita( n, fon );
		return quante;
	}

	// riceve un Object (Person, Name, EventFact...) e conta quante volte è citata la fonte
	void cita( Object ogg, Source fonte ) {
		List<SourceCitation> listaSc;
		if( ogg instanceof Note )	// se è una Nota
			listaSc = ((Note) ogg).getSourceCitations();
		else {
			for( Note n : ((NoteContainer) ogg).getNotes() )
				cita( n, fonte );
			listaSc = ((SourceCitationContainer) ogg).getSourceCitations();
		}
		for( SourceCitation sc : listaSc ) {
			if( sc.getRef() != null )
				if( sc.getRef().equals(fonte.getId()) )
					quante++;
		}
	}

	public static void nuovaFonte( Context contesto, Object contenitore ){
		Source fonte = new Source();
		int val, max = 0;
		for( Source fon : gc.getSources() ) {
			val = Anagrafe.idNumerico( fon.getId() );
			if( val > max )	max = val;
		}
		fonte.setId(  "S" + (max+1) );
		fonte.setTitle( "" );
		gc.addSource( fonte );
		if( contenitore != null ) {
			SourceCitation citaFonte = new SourceCitation();
			citaFonte.setRef( fonte.getId() );
			if( contenitore instanceof Note ) ((Note)contenitore).addSourceCitation( citaFonte );
			else ((SourceCitationContainer)contenitore).addSourceCitation( citaFonte );
			//Ponte.manda( contenitore, "contenitore" );
		}
		Ponte.manda( fonte, "oggetto" );
		contesto.startActivity( new Intent( contesto, Fonte.class ) );
	}

	// menu opzioni nella toolbar
	@Override
	public void onCreateOptionsMenu( Menu menu, MenuInflater inflater ) {
		SubMenu subOrdina = menu.addSubMenu(0,0,0, R.string.order_by );
		subOrdina.add( 0, 1, 0, R.string.id );
		subOrdina.add( 0, 2, 0, R.string.title );
		subOrdina.add( 0, 3, 0, R.string.citations );
		menu.add( 0, 4, 0, R.string.new_f );
	}
	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
		switch( item.getItemId() ) {
			case 1:
				Globale.ordineBiblioteca = 1;
				break;
			case 2:
				Globale.ordineBiblioteca = 2;
				break;
			case 3:
				Globale.ordineBiblioteca = 3;
				break;
			case 4:
				nuovaFonte( getContext(), null );
				return true;
			default:
				return false;
		}
		getFragmentManager().beginTransaction().replace( R.id.contenitore_fragment, new Biblioteca() ).commit();
		return true;
	}

	View vistaScelta;
	@Override
	public void onCreateContextMenu( ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info ) {
		vistaScelta = vista;
		menu.add(0, 0, 0, R.string.delete );
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		if( item.getItemId() == 0 ) {	// Elimina
			// todo e le citazioni alla fonte non vanno eliminate?
			gc.getSources().remove( gc.getSource(((TextView)vistaScelta.findViewById(R.id.biblioteca_id)).getText().toString()) );
			gc.createIndexes();
			vistaScelta.setVisibility( View.GONE );
			U.salvaJson();
		} else {
			return false;
		}
		return true;
	}
}