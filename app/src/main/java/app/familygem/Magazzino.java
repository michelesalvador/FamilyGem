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
import java.util.Comparator;
import java.util.List;
import app.familygem.dettaglio.Archivio;
import static app.familygem.Globale.gc;

public class Magazzino extends Fragment {

    @Override
    public void onCreate( Bundle stato ) {
        super.onCreate( stato );
    }

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle stato ) {
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
			Collections.sort( listArchivi, new Comparator<Repository>() {
				public int compare( Repository r1, Repository r2 ) {
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
				}
			});
			for( final Repository rep : listArchivi ) {
				View vistaPezzo = inflater.inflate( R.layout.magazzino_pezzo, scatola, false );
				scatola.addView( vistaPezzo );
				((TextView)vistaPezzo.findViewById( R.id.magazzino_nome )).setText( rep.getName() );
				((TextView)vistaPezzo.findViewById( R.id.magazzino_archivi )).setText( String.valueOf(rep.getExtension("fonti")) );
				vistaPezzo.setOnClickListener( new View.OnClickListener() {
					public void onClick( View vista ) {
						if( getActivity().getIntent().getBooleanExtra("magazzinoScegliArchivio",false) ) {
							Intent intento = new Intent();
							intento.putExtra("idArchivio", rep.getId() );
							getActivity().setResult( Activity.RESULT_OK, intento );
							getActivity().finish();
						} else {
							Ponte.manda( rep, "oggetto" );
							startActivity( new Intent( getContext(), Archivio.class ) );
						}
					}
				});
				registerForContextMenu( vistaPezzo );
				vistaPezzo.setTag( rep );
			}
		}
		return vista;
	}

	// Conta quante fonti sono presenti nel tal archivio
	static int quanteFonti( Repository rep, Gedcom gc ) {
		int quante = 0;
		for( Source fon : gc.getSources() ) {
			if( fon.getRepository(gc) != null )
				if( fon.getRepository(gc).equals(rep) )
					quante++;
		}
		return quante;
	}

	// Crea un archivio nuovo, se riceve una fonte glielo collega
	public static void nuovoArchivio( Context contesto, Source fonte ) {
		Repository arch = new Repository();
		int val, max = 0;
		for( Repository a : gc.getRepositories() ) {
			val = Anagrafe.idNumerico( a.getId() );
			if( val > max )	max = val;
		}
		arch.setId(  "R" + (max+1) );
		arch.setName( "" );
		gc.addRepository( arch );
		if( fonte != null ) {
			RepositoryRef archRef = new RepositoryRef();
			archRef.setRef( arch.getId() );
			fonte.setRepositoryRef( archRef );
			Ponte.manda( fonte, "contenitore" );
		}
		Ponte.manda( arch, "oggetto" );
		contesto.startActivity( new Intent( contesto, Archivio.class ) );
	}

	public static void elimina( Repository arch ) {
		// todo eliminare ref dalle fonti in cui è citato
		gc.getRepositories().remove( arch );
		gc.createIndexes();	// serve?
	}

	// menu opzioni nella toolbar
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater ) {
		Menu subOrdina = menu.addSubMenu(0,0,0, R.string.order_by );
		subOrdina.add( 0,1,0, R.string.id );
		subOrdina.add( 0,2,0, R.string.name );
		subOrdina.add( 0,3,0, R.string.sources_number );
		menu.add( 0, 4, 0, R.string.new_m );
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
			case 4:
				nuovoArchivio( getContext(), null );
				return true;
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
				elimina( (Repository) vistaArchio.getTag() );
				vistaArchio.setVisibility( View.GONE );
				U.salvaJson();
				return true;
		}
		return false;
	}
}