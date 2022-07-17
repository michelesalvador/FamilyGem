package app.familygem;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.NoteRef;
import java.util.List;
import app.familygem.dettaglio.Nota;
import app.familygem.visita.ListaNote;
import app.familygem.visita.RiferimentiNota;
import app.familygem.visita.TrovaPila;
import static app.familygem.Global.gc;

public class Quaderno extends Fragment {

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
		boolean scegliNota = getActivity().getIntent().getBooleanExtra("quadernoScegliNota", false);
		View view = inflater.inflate(R.layout.magazzino, container, false);
		if( gc != null ) {
			LinearLayout layout = view.findViewById(R.id.magazzino_scatola);
			// Note condivise
			List<Note> listaNoteCondivise = gc.getNotes();
			if( !listaNoteCondivise.isEmpty() ) {
				if( !scegliNota ) {
					View tit = inflater.inflate(R.layout.titoletto, layout, true);
					String txt = listaNoteCondivise.size() + " " +
							getString(listaNoteCondivise.size()==1 ? R.string.shared_note : R.string.shared_notes).toLowerCase();
					((TextView)tit.findViewById(R.id.titolo_testo)).setText( txt );
				}
				for( Note not : listaNoteCondivise )
					registerForContextMenu( placeNote(layout,not) );
			}
			// Note inlinea
			ListaNote visitaNote = new ListaNote();
			if( !scegliNota ) {
				gc.accept( visitaNote );
				if( !visitaNote.listaNote.isEmpty() ) {
					View tit = inflater.inflate(R.layout.titoletto, layout, false);
					String txt = visitaNote.listaNote.size() + " " +
							getString(visitaNote.listaNote.size() == 1 ? R.string.simple_note : R.string.simple_notes).toLowerCase();
					((TextView)tit.findViewById(R.id.titolo_testo)).setText(txt);
					//((TextView)tit.findViewById(R.id.titolo_numero)).setText( String.valueOf(visitaNote.listaNote.size()) );
					layout.addView(tit);
					for( Object nota : visitaNote.listaNote )
						registerForContextMenu(placeNote(layout, (Note)nota));
				}
			}
			int totaleNote = listaNoteCondivise.size() + visitaNote.listaNote.size();
			((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(totaleNote + " "
					+ getString(totaleNote == 1 ? R.string.note : R.string.notes).toLowerCase());
			view.findViewById(R.id.fab).setOnClickListener(v -> nuovaNota(getContext(), null));
		}
		return view;
	}

	// Andandosene dall'attività senza aver scelto una nota condivisa resetta l'extra
	@Override
	public void onPause() {
		super.onPause();
		getActivity().getIntent().removeExtra("quadernoScegliNota");
	}

	View placeNote(final LinearLayout layout, final Note note) {
		View noteView = LayoutInflater.from(layout.getContext()).inflate(R.layout.quaderno_pezzo, layout, false);
		layout.addView(noteView);
		String text = note.getValue().replace("@@","@");
		((TextView)noteView.findViewById(R.id.nota_testo)).setText(text);
		TextView vistaCita = noteView.findViewById(R.id.nota_citazioni);
		if( note.getId() == null )
			vistaCita.setVisibility(View.GONE);
		else {
			RiferimentiNota contaUso = new RiferimentiNota(gc, note.getId(), false);
			vistaCita.setText(String.valueOf(contaUso.tot));
		}
		noteView.setOnClickListener(v -> {
			// Restituisce l'id di una nota a Individuo e Dettaglio
			if( getActivity().getIntent().getBooleanExtra("quadernoScegliNota", false) ) {
				Intent intento = new Intent();
				intento.putExtra("idNota", note.getId());
				getActivity().setResult(AppCompatActivity.RESULT_OK, intento);
				getActivity().finish();
			} else { // Apre il dettaglio della nota
				Intent intento = new Intent(layout.getContext(), Nota.class);
				if( note.getId() != null ) { // Nota condivisa
					Memoria.setPrimo(note);
				} else { // Nota semplice
					new TrovaPila(gc, note);
					intento.putExtra("daQuaderno", true);
				}
				layout.getContext().startActivity(intento);
			}
		});
		noteView.setTag(note); // per il menu contestuale Elimina
		return noteView;
	}

	// Crea una nuova nota condivisa, attaccata a un contenitore oppure slegata
	static void nuovaNota(Context contesto, Object contenitore) {
		Note notaNova = new Note();
		String id = U.nuovoId(gc, Note.class);
		notaNova.setId(id);
		notaNova.setValue("");
		gc.addNote(notaNova);
		if( contenitore != null ) {
			NoteRef refNota = new NoteRef();
			refNota.setRef(id);
			((NoteContainer)contenitore).addNoteRef(refNota);
		}
		U.salvaJson(true, notaNova);
		Memoria.setPrimo(notaNova);
		contesto.startActivity(new Intent(contesto, Nota.class));
	}

	private Note nota;
	@Override
	public void onCreateContextMenu(ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info) {
		nota = (Note)vista.getTag();
		menu.add(0, 0, 0, R.string.delete);
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		if( item.getItemId() == 0 ) { // Elimina
			Object[] capi = U.eliminaNota( nota, null );
			U.salvaJson( false, capi );
			getActivity().recreate();
		} else {
			return false;
		}
		return true;
	}
}