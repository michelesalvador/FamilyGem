package app.familygem;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.ParentFamilyRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SpouseFamilyRef;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import app.familygem.dettaglio.Famiglia;
import static app.familygem.Globale.gc;

public class Chiesa extends Fragment {
	
	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle stato ) {
		View vista = inflater.inflate( R.layout.magazzino, container, false );
		LinearLayout scatola = vista.findViewById( R.id.magazzino_scatola );
		if( gc != null ) {
			List<Family> listaFamiglie = gc.getFamilies();
			((AppCompatActivity)getActivity()).getSupportActionBar().setTitle( listaFamiglie.size() + " "
					+ getString(listaFamiglie.size()==1 ? R.string.family : R.string.families).toLowerCase() );
			for( final Family fam : listaFamiglie )
				mettiFamiglia( scatola, fam );
			setHasOptionsMenu( true );
			vista.findViewById( R.id.fab ).setOnClickListener( v -> {
				Family nuovaFamiglia = nuovaFamiglia(true);
				U.salvaJson( true, nuovaFamiglia );
				// Se torna subito indietro in Chiesa rinfresca la lista con la famiglia vuota
				Memoria.setPrimo( nuovaFamiglia );
				startActivity( new Intent( getContext(), Famiglia.class ) );
			});
		}
		return vista;
	}

	void mettiFamiglia( LinearLayout scatola, Family fam ) {
		View vistaFamiglia = LayoutInflater.from(scatola.getContext()).inflate( R.layout.pezzo_famiglia, scatola, false );
		scatola.addView( vistaFamiglia );
		String genitori = "";
		for( Person marito : fam.getHusbands(gc) )
			genitori += U.epiteto( marito ) + "\n";
		for( Person moglie : fam.getWives(gc) )
			genitori += U.epiteto( moglie ) + "\n";
		if( !genitori.isEmpty() )
			genitori = genitori.substring( 0, genitori.length() - 1 );
		((TextView)vistaFamiglia.findViewById( R.id.famiglia_genitori )).setText( genitori );
		String figli = "";
		for( Person figlio : fam.getChildren(gc) )
			figli += U.epiteto( figlio ) + "\n";
		if( !figli.isEmpty() )
			figli = figli.substring( 0, figli.length() - 1 );
		if( figli.isEmpty() )
			vistaFamiglia.findViewById( R.id.famiglia_strut ).setVisibility( View.GONE );
		else
			((TextView)vistaFamiglia.findViewById( R.id.famiglia_figli )).setText( figli );
		registerForContextMenu( vistaFamiglia );
		vistaFamiglia.setOnClickListener( v -> {
			Memoria.setPrimo( fam );
			scatola.getContext().startActivity( new Intent( scatola.getContext(), Famiglia.class ) );
		});
		vistaFamiglia.setTag( fam.getId() );	// solo per il menu contestuale Elimina qui in Chiesa
	}

	// Elimina una famiglia, rimuove i ref dai membri
	static void eliminaFamiglia( String idFamiglia ) {
		Family f = gc.getFamily( idFamiglia );
		Set<Person> membri = new HashSet<>();
		// Prima rimuove i ref alla famiglia negli indi membri
		for( Person marito : f.getHusbands(gc) ) {
			Iterator<SpouseFamilyRef> refi = marito.getSpouseFamilyRefs().iterator();
			while( refi.hasNext() ) {
				SpouseFamilyRef sfr = refi.next();
				if( sfr.getRef().equals(f.getId()) ) {
					refi.remove();
					membri.add( marito );
				}
			}
		}
		for( Person moglie : f.getWives(gc) ) {
			Iterator<SpouseFamilyRef> refi = moglie.getSpouseFamilyRefs().iterator();
			while( refi.hasNext() ) {
				SpouseFamilyRef sfr = refi.next();
				if( sfr.getRef().equals(f.getId()) ) {
					refi.remove();
					membri.add( moglie );
				}
			}
		}
		for( Person figlio : f.getChildren(gc) ) {
			Iterator<ParentFamilyRef> refi = figlio.getParentFamilyRefs().iterator();
			while( refi.hasNext() ) {
				ParentFamilyRef pfr = refi.next();
				if( pfr.getRef().equals(f.getId()) ) {
					refi.remove();
					membri.add( figlio );
				}
			}
		}
		// Poi può rimuovere la famiglia
		gc.getFamilies().remove(f);
		gc.createIndexes();	// necessario per aggiornare gli individui
		Memoria.annullaIstanze(f);
		U.salvaJson( true, membri.toArray(new Object[0]) );
	}

	static Family nuovaFamiglia( boolean aggiungi ) {
		Family nuova = new Family();
		nuova.setId( U.nuovoId( gc, Family.class ));
		if( aggiungi )
			gc.addFamily( nuova );
		return nuova;
	}

	private View vistaScelta;
	@Override
	public void onCreateContextMenu( ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info ) {
		vistaScelta = vista;
		menu.add(0, 0, 0, R.string.delete );
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		if( item.getItemId() == 0 ) {	// Elimina
			new AlertDialog.Builder(getContext()).setMessage( R.string.really_delete_family )
					.setPositiveButton(android.R.string.yes, (dialog, i) -> {
						eliminaFamiglia( (String) vistaScelta.getTag() );
						getActivity().recreate();
					}).setNeutralButton(android.R.string.cancel, null).show();
		} else {
			return false;
		}
		return true;
	}
}