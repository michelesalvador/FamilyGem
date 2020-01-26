// Lista dei Repositories (archivi)

package app.familygem;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.RepositoryRef;
import org.folg.gedcom.model.Source;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import app.familygem.dettaglio.Archivio;
import static app.familygem.Globale.gc;

public class Magazzino extends Fragment {

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle bandolo ) {
		View vista = inflater.inflate( R.layout.magazzino, container, false);
		LinearLayout scatola = vista.findViewById( R.id.magazzino_scatola );
		if( gc != null ) {
			setHasOptionsMenu(true);
			List<Repository> listArchivi = gc.getRepositories();
			((AppCompatActivity) getActivity()).getSupportActionBar().setTitle( listArchivi.size() + " " + getString(R.string.repositories).toLowerCase() );
			for( Repository rep : listArchivi ) {
				if( rep.getExtension("fonti") == null )
					rep.putExtension( "fonti", quanteFonti(rep,gc) );
			}
			Collections.sort( listArchivi, ( r1, r2 ) -> {
				switch( Globale.ordineMagazzino ) {
					case 1:	// Ordina per id
						return Integer.parseInt(r1.getId().substring(1)) - Integer.parseInt(r2.getId().substring(1));
					case 2:	// Ordine alfabeto
						return r1.getName().compareToIgnoreCase(r2.getName());
					case 3:	// Ordina per numero di fonti
						return U.castaJsonInt(r2.getExtension("fonti")) - U.castaJsonInt(r1.getExtension("fonti"));
					default:
						return 0;
				}
			});
			for( final Repository rep : listArchivi ) {
				View vistaPezzo = inflater.inflate( R.layout.magazzino_pezzo, scatola, false );
				scatola.addView( vistaPezzo );
				((TextView)vistaPezzo.findViewById( R.id.magazzino_nome )).setText( rep.getName() );
				((TextView)vistaPezzo.findViewById( R.id.magazzino_archivi )).setText( String.valueOf(rep.getExtension("fonti")) );
				vistaPezzo.setOnClickListener( v -> {
					if( getActivity().getIntent().getBooleanExtra("magazzinoScegliArchivio",false) ) {
						Intent intento = new Intent();
						intento.putExtra("idArchivio", rep.getId() );
						getActivity().setResult( Activity.RESULT_OK, intento );
						getActivity().finish();
					} else {
						Memoria.setPrimo( rep );
						startActivity( new Intent( getContext(), Archivio.class ) );
					}
				} );
				registerForContextMenu( vistaPezzo );
				vistaPezzo.setTag( rep );
			}
			vista.findViewById( R.id.fab ).setOnClickListener( v -> nuovoArchivio( getContext(), null ) );
		}
		return vista;
	}

	// Conta quante fonti sono presenti nel tal archivio
	static int quanteFonti( Repository rep, Gedcom gc ) {
		int quante = 0;
		for( Source fon : gc.getSources() ) {
			if( fon.getRepositoryRef() != null && fon.getRepositoryRef().getRef().equals(rep.getId()) )
				quante++;
		}
		return quante;
	}

	// Crea un archivio nuovo, se riceve una fonte glielo collega
	public static void nuovoArchivio( Context contesto, Source fonte ) {
		Repository arch = new Repository();
		arch.setId( U.nuovoId( gc, Repository.class ) );
		arch.setName( "" );
		gc.addRepository( arch );
		if( fonte != null ) {
			RepositoryRef archRef = new RepositoryRef();
			archRef.setRef( arch.getId() );
			fonte.setRepositoryRef( archRef );
		}
		Memoria.setPrimo( arch );
		contesto.startActivity( new Intent( contesto, Archivio.class ) );
	}

	/* Elimina l'archivio e i ref dalle fonti in cui è citato l'archivio
		Restituisce un array delle Source modificate
	Secondo le specifiche Gedcom 5.5, la labreria FS e Family Historian una SOUR prevede un solo Ref a un REPO
	Invece secondo Gedcom 5.5.1 può avere molteplici Ref ad archivi */
	public static Source[] elimina( Repository arch ) {
		Set<Source> fonti = new HashSet<>();
		for( Source fon : gc.getSources() )
			if( fon.getRepositoryRef() != null && fon.getRepositoryRef().getRef().equals(arch.getId()) ) {
				fon.setRepositoryRef( null );
				fonti.add( fon );
			}
		gc.getRepositories().remove( arch );
		Memoria.annullaIstanze( arch );
		return fonti.toArray( new Source[0] );
	}

	// menu opzioni nella toolbar
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater ) {
		menu.add( R.string.order_by ).setEnabled( false );
		menu.add( 0,1,0, R.string.id );
		menu.add( 0,2,0, R.string.name );
		menu.add( 0,3,0, R.string.sources_number );
	}
	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
		switch( item.getItemId() ) {
			case 1:
				Globale.ordineMagazzino = 1;
				break;
			case 2:
				Globale.ordineMagazzino = 2;
				break;
			case 3:
				Globale.ordineMagazzino = 3;
				break;
			default:
				return false;
		}
		getFragmentManager().beginTransaction().replace( R.id.contenitore_fragment, new Magazzino() ).commit();
		return true;
	}

	// Menu contestuale
	View vistaArchio;
	@Override
	public void onCreateContextMenu(ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info ) {
		vistaArchio = vista;
		menu.add( 0, 0, 0, R.string.delete );
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		switch( item.getItemId() ) {
			case 0:
				vistaArchio.setVisibility( View.GONE );
				Source[] fonti = elimina( (Repository)vistaArchio.getTag() );
				U.salvaJson( false, (Object[])fonti );
				return true;
		}
		return false;
	}
}