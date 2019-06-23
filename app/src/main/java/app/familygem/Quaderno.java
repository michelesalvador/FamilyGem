package app.familygem;

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
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.NoteRef;
import java.util.List;
import java.util.Map;
import app.familygem.dettaglio.Nota;
import app.familygem.visita.ListaNote;
import app.familygem.visita.RiferimentiNota;
import static app.familygem.Globale.gc;

public class Quaderno extends Fragment {
	
	@Override
	public void onCreate( Bundle stato ) {
		super.onCreate( stato );
	}

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle stato ) {
		boolean scegliNota = getActivity().getIntent().getBooleanExtra("quadernoScegliNota",false );
		View vista = inflater.inflate( R.layout.magazzino, container, false );
		if( gc != null ) {
			LinearLayout scatola = vista.findViewById( R.id.magazzino_scatola );
			// Note condivise
			List<Note> listaNoteCondivise = gc.getNotes();
			if( !listaNoteCondivise.isEmpty() ) {
				if( !scegliNota ) {
					View tit = inflater.inflate( R.layout.titoletto, scatola, true );
					String txt = listaNoteCondivise.size() +" "+ getText(R.string.shared_notes);
					((TextView)tit.findViewById(R.id.titolo_testo)).setText( txt );
				}
				for( Note not : listaNoteCondivise )
					registerForContextMenu( mettiNota(scatola,not,null) );
			}
			// Note inlinea
			ListaNote visitaNote = new ListaNote();
			if( !scegliNota ) {
				gc.accept( visitaNote );
				if( !visitaNote.listaNote.isEmpty() ) {
					View tit = inflater.inflate( R.layout.titoletto, scatola, false );
					String txt = visitaNote.listaNote.size() +" "+ getText(R.string.simple_notes);
					((TextView)tit.findViewById(R.id.titolo_testo)).setText( txt );
					//((TextView)tit.findViewById(R.id.titolo_numero)).setText( String.valueOf(visitaNote.listaNote.size()) );
					scatola.addView( tit );
					for( Map.Entry<Note,Object> dato : visitaNote.listaNote.entrySet() )
						registerForContextMenu( mettiNota(scatola,dato.getKey(),dato.getValue()) );
				}
			}
			((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(
					(listaNoteCondivise.size()+visitaNote.listaNote.size()) + " " + getString(R.string.notes).toLowerCase() );
			setHasOptionsMenu(true);
		}
		return vista;
	}

	View mettiNota( final LinearLayout scatola, final Note nota, final Object contenitore ) {
		View vistaNota = LayoutInflater.from(scatola.getContext()).inflate( R.layout.quaderno_pezzo, scatola, false );
		scatola.addView( vistaNota );
		String testo = nota.getValue();
		((TextView)vistaNota.findViewById( R.id.nota_testo )).setText( testo );
		TextView vistaCita = vistaNota.findViewById( R.id.nota_citazioni );
		if( nota.getId() == null )
			vistaCita.setVisibility( View.GONE );
		else {
			RiferimentiNota contaUso = new RiferimentiNota( nota.getId(), false );
			gc.accept( contaUso );
			vistaCita.setText( String.valueOf(contaUso.num) );
		}
		vistaNota.setOnClickListener( new View.OnClickListener() {
			public void onClick( View vista ) {
				// Restituisce l'id di una nota a Individuo e Dettaglio
				if( getActivity().getIntent().getBooleanExtra("quadernoScegliNota",false) ) {
					Intent intento = new Intent();
					intento.putExtra( "idNota", nota.getId() );
					getActivity().setResult( AppCompatActivity.RESULT_OK, intento );
					getActivity().finish();
				} else {
					Ponte.manda( nota, "oggetto" );
					if( contenitore != null ) // per le note condivise è null
						Ponte.manda( contenitore, "contenitore" );
					scatola.getContext().startActivity( new Intent( scatola.getContext(), Nota.class ) );
				}
			}
		});
		vistaNota.setTag( R.id.tag_oggetto, nota );	// per il menu contestuale Elimina
		vistaNota.setTag( R.id.tag_contenitore, contenitore );
		return vistaNota;
	}

	static void nuovaNota( Context contesto, Object contenitore ){
		Note notaNova = new Note();
		int val, max = 0;
		for( Note n : gc.getNotes() ) {
			val = Anagrafe.idNumerico( n.getId() );
			if( val > max )	max = val;
		}
		String id = "N" + (max+1);
		notaNova.setId( id );
		notaNova.setValue( "" );
		gc.addNote( notaNova );
		if( contenitore != null ) {
			NoteRef refNota = new NoteRef();
			refNota.setRef( id );
			((NoteContainer)contenitore).addNoteRef( refNota );
			Ponte.manda( contenitore, "contenitore" );
		}
		Ponte.manda( notaNova, "oggetto" );
		contesto.startActivity( new Intent( contesto, Nota.class ) );
	}

	@Override
	public void onCreateOptionsMenu( Menu menu, MenuInflater inflater ) {
		menu.add(0,0,0, R.string.new_f );
	}
	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
		switch( item.getItemId() ) {
			case 0:
				nuovaNota( getContext(), null );
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
		if( item.getItemId() == 0 ) { // Elimina
			U.eliminaNota( (Note)vistaScelta.getTag(R.id.tag_oggetto), vistaScelta.getTag(R.id.tag_contenitore), vistaScelta );
			U.salvaJson();
		} else {
			return false;
		}
		return true;
	}
}