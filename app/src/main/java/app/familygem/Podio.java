// Lista dei Submitter (autori)

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
import org.folg.gedcom.model.Header;
import org.folg.gedcom.model.Submitter;
import java.util.List;
import app.familygem.dettaglio.Autore;
import static app.familygem.Globale.gc;

public class Podio extends Fragment {

    @Override
    public void onCreate( Bundle stato ) {
        super.onCreate( stato );
    }

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle stato ) {
		List<Submitter> listAutori = gc.getSubmitters();
		((AppCompatActivity) getActivity()).getSupportActionBar().setTitle( listAutori.size() + " " + getString(R.string.submitters).toLowerCase() );
		setHasOptionsMenu(true);
		View vista = inflater.inflate( R.layout.magazzino, container, false);
		LinearLayout scatola = vista.findViewById( R.id.magazzino_scatola );
		for( final Submitter autor : listAutori ) {
			View vistaPezzo = inflater.inflate( R.layout.magazzino_pezzo, scatola, false );
			scatola.addView( vistaPezzo );
			((TextView)vistaPezzo.findViewById( R.id.magazzino_nome )).setText( autor.getName() );
			vistaPezzo.findViewById( R.id.magazzino_archivi ).setVisibility( View.GONE );
			vistaPezzo.setOnClickListener( new View.OnClickListener() {
				public void onClick( View vista ) {
					if( getActivity().getIntent().getBooleanExtra("podioScegliAutore",false) ) {
						Intent intento = new Intent();
						intento.putExtra("idAutore", autor.getId() );
						getActivity().setResult( Activity.RESULT_OK, intento );
						getActivity().finish();
					} else {
						Ponte.manda( autor, "oggetto" );
						startActivity( new Intent( getContext(), Autore.class ) );
					}
				}
			});
			registerForContextMenu( vistaPezzo );
			vistaPezzo.setTag( autor );
		}
		return vista;
	}

	// Crea un Autore nuovo, se riceve un contesto lo apre in modalità editore
	public static Submitter nuovoAutore( Context contesto ) {
		Submitter subm = new Submitter();
		int val, max = 0;
		for( Submitter a : gc.getSubmitters() ) {
			val = Anagrafe.idNumerico( a.getId() );
			if( val > max )	max = val;
		}
		subm.setId(  "U" + (max+1) );
		subm.setName( "" );
		gc.addSubmitter( subm );
		if( contesto != null ) {
			Ponte.manda( subm, "oggetto" );
			contesto.startActivity( new Intent( contesto, Autore.class ) );
		}
		return subm;
	}

	public static void elimina( Submitter aut ) {
		gc.getSubmitters().remove( aut );
		Header testa = gc.getHeader();
		if( testa != null )
			if( testa.getSubmitterRef() != null )
				if( testa.getSubmitterRef().equals(aut.getId()) )
					testa.setSubmitterRef( null );
		gc.createIndexes();	// serve?
	}

	// menu opzioni nella toolbar
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater ) {
		menu.add( 0, 0, 0, R.string.new_m );
	}
	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
		switch( item.getItemId() ) {
			case 0:
				nuovoAutore( getContext() );
				return true;
			default:
				return false;
		}
	}

	// Menu contestuale
	View vistaAutore;
	@Override
	public void onCreateContextMenu(ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info ) {
		vistaAutore = vista;
		if( gc.getHeader().getSubmitter(gc) == null || !gc.getHeader().getSubmitter(gc).equals( vista.getTag() ) )
			menu.add( 0, 0, 0, R.string.make_default );
		menu.add( 0, 1, 0, R.string.delete );
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		switch( item.getItemId() ) {
			case 0:
				gc.getHeader().setSubmitterRef( ((Submitter)vistaAutore.getTag()).getId() );
				U.salvaJson();
				return true;
			case 1:
				// Todo conferma elimina
				elimina( (Submitter) vistaAutore.getTag() );
				vistaAutore.setVisibility( View.GONE );
				U.salvaJson();
				return true;
		}
		return false;
	}
}