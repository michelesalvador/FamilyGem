package app.familygem;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.ParentFamilyRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SpouseFamilyRef;
import java.util.Iterator;
import java.util.List;
import app.familygem.dettaglio.Famiglia;
import static app.familygem.Globale.gc;

public class Chiesa extends Fragment {
	
	@Override
	public void onCreate( Bundle stato ) {
		super.onCreate( stato );
	}

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle stato ) {
		View vista = inflater.inflate( R.layout.magazzino, container, false );
		LinearLayout scatola = vista.findViewById( R.id.magazzino_scatola );
		List<Family> listaFamiglie = gc.getFamilies();
		((AppCompatActivity)getActivity()).getSupportActionBar().setTitle( listaFamiglie.size() + " " + getString(R.string.families).toLowerCase() );
		for( final Family fam : listaFamiglie )
			mettiFamiglia( scatola, fam );
		setHasOptionsMenu(true);
		return vista;
	}

	public static void mettiFamiglia( final LinearLayout scatola, final Family fam ) {
		View vistaFamiglia = LayoutInflater.from(scatola.getContext()).inflate( R.layout.pezzo_famiglia, scatola, false );
		scatola.addView( vistaFamiglia );
		String testo = "";
		for( Person marito : fam.getHusbands(gc) )
			testo += U.epiteto( marito ) + "\n";
		for( Person moglie : fam.getWives(gc) )
			testo += U.epiteto( moglie ) + "\n";
		if( !fam.getChildren(gc).isEmpty() ) {
			testo += fam.getChildren(gc).size() + " " + scatola.getContext().getString(R.string.children);
		}
		if( testo.endsWith("\n") ) testo = testo.substring( 0, testo.length()-1 );
		((TextView)vistaFamiglia.findViewById( R.id.famiglia_testo )).setText( testo );
		if( scatola.getContext() instanceof Principe ) // Fragment Chiesa
			((AppCompatActivity)scatola.getContext()).getSupportFragmentManager()
					.findFragmentById( R.id.contenitore_fragment ).registerForContextMenu( vistaFamiglia );
		else	// AppCompatActivity
			((AppCompatActivity)scatola.getContext()).registerForContextMenu( vistaFamiglia );
		vistaFamiglia.setOnClickListener( new View.OnClickListener() {
			public void onClick( View vista ) {
				Intent intento = new Intent( scatola.getContext(), Famiglia.class );
				intento.putExtra( "idFamiglia", fam.getId() );
				scatola.getContext().startActivity( intento );
			}
		});
		vistaFamiglia.setTag( fam.getId() );	// solo per il menu contestuale Elimina qui in Chiesa
	}

	// Elimina una famiglia rimuovendo i ref dai membri
	public static void elimina( String idFamiglia ) {
		// Todo: Conferma con avviso: Vuoi davvero eliminare questa famiglia? (i suoi membri continueranno a esistere) non le persone che la compongono.
		Family f = gc.getFamily( idFamiglia );
		// Prima rimuove i ref alla famiglia negli indi membri
		for( Person marito : f.getHusbands(gc) ) {
					/* for produce ConcurrentModificationException casomai ci sono vari ref alla stessa famiglia
					for( SpouseFamilyRef sfr : marito.getSpouseFamilyRefs() )
						if( sfr.getRef().equals(f.getId()) )
							marito.getSpouseFamilyRefs().remove( sfr );*/
			Iterator<SpouseFamilyRef> refi = marito.getSpouseFamilyRefs().iterator();
			while( refi.hasNext() ) {
				SpouseFamilyRef sfr = refi.next();
				if( sfr.getRef().equals(f.getId()) )
					refi.remove();
			}
		}
		for( Person moglie : f.getWives(gc) ) {
			Iterator<SpouseFamilyRef> refi = moglie.getSpouseFamilyRefs().iterator();
			while( refi.hasNext() ) {
				SpouseFamilyRef sfr = refi.next();
				if( sfr.getRef().equals(f.getId()) )
					refi.remove();
			}
		}
		for( Person figlio : f.getChildren(gc) ) {
			Iterator<ParentFamilyRef> refi = figlio.getParentFamilyRefs().iterator();
			while( refi.hasNext() ) {
				ParentFamilyRef pfr = refi.next();
				if( pfr.getRef().equals(f.getId()) )
					refi.remove();
			}
		}
		// Poi può rimuovere la famiglia
		gc.getFamilies().remove(f);
		gc.createIndexes();	// necessario per aggiornare gli individui
	}

	@Override
	public void onCreateOptionsMenu( Menu menu, MenuInflater inflater ) {
		menu.add(0,0,0, R.string.new_f );
	}
	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
		switch( item.getItemId() ) {
			case 0:
				Family nuova = new Family();
				int val, max = 0;
				for( Family fam : gc.getFamilies() ) {
					val = Anagrafe.idNumerico( fam.getId() );
					if( val > max )	max = val;
				}
				nuova.setId( "F" + (max + 1) );
				gc.addFamily( nuova );
				U.salvaJson();
				Intent intento = new Intent( getContext(), Famiglia.class );
				intento.putExtra( "idFamiglia", nuova.getId() );
				startActivity( intento );
				return true;
			default:
				return false;
		}
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
			elimina( (String) vistaScelta.getTag() );
			vistaScelta.setVisibility( View.GONE );
			U.salvaJson();
		} else {
			return false;
		}
		return true;
	}
}
