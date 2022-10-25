// Lista dei Submitter (autori)

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
import org.folg.gedcom.model.Header;
import org.folg.gedcom.model.Submitter;
import java.util.List;
import app.familygem.detail.AuthorActivity;
import static app.familygem.Global.gc;

public class ListOfAuthorsFragment extends Fragment {

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle stato) {
		List<Submitter> listAutori = gc.getSubmitters();
		((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(listAutori.size() + " " +
				getString(listAutori.size() == 1 ? R.string.submitter : R.string.submitters).toLowerCase());
		setHasOptionsMenu(true);
		View vista = inflater.inflate(R.layout.magazzino, container, false);
		LinearLayout scatola = vista.findViewById(R.id.magazzino_scatola);
		for( final Submitter autor : listAutori ) {
			View vistaPezzo = inflater.inflate(R.layout.magazzino_pezzo, scatola, false);
			scatola.addView(vistaPezzo);
			((TextView)vistaPezzo.findViewById(R.id.magazzino_nome)).setText(TreeInfoActivity.nomeAutore(autor));
			vistaPezzo.findViewById(R.id.magazzino_archivi).setVisibility(View.GONE);
			vistaPezzo.setOnClickListener(v -> {
				Memory.setPrimo(autor);
				startActivity(new Intent(getContext(), AuthorActivity.class));
			});
			registerForContextMenu(vistaPezzo);
			vistaPezzo.setTag(autor);
		}
		vista.findViewById(R.id.fab).setOnClickListener(v -> {
			nuovoAutore(getContext());
			U.save(true);
		});
		return vista;
	}

	// Elimina un autore
	// Todo mi sa che andrebbe cercato eventuale SubmitterRef in tutti i record
	public static void eliminaAutore(Submitter aut) {
		Header testa = gc.getHeader();
		if( testa != null && testa.getSubmitterRef() != null
				&& testa.getSubmitterRef().equals(aut.getId()) ) {
			testa.setSubmitterRef(null);
		}
		gc.getSubmitters().remove(aut);
		if( gc.getSubmitters().isEmpty() )
			gc.setSubmitters(null);
		Memory.annullaIstanze(aut);
	}

	// Crea un Autore nuovo, se riceve un contesto lo apre in modalità editore
	static Submitter nuovoAutore(Context contesto) {
		Submitter subm = new Submitter();
		subm.setId(U.nuovoId(gc, Submitter.class));
		subm.setName("");
		U.updateChangeDate(subm);
		gc.addSubmitter(subm);
		if( contesto != null ) {
			Memory.setPrimo(subm);
			contesto.startActivity(new Intent(contesto, AuthorActivity.class));
		}
		return subm;
	}

	static void autorePrincipale(Submitter subm) {
		Header testa = gc.getHeader();
		if( testa == null ) {
			testa = NewTree.creaTestata(Global.settings.openTree + ".json");
			gc.setHeader(testa);
		}
		testa.setSubmitterRef(subm.getId());
		U.save(false, subm);
	}

	// Menu contestuale
	Submitter subm;
	@Override
	public void onCreateContextMenu(ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info ) {
		subm = (Submitter)vista.getTag();
		if( gc.getHeader() == null || gc.getHeader().getSubmitter(gc) == null || !gc.getHeader().getSubmitter(gc).equals(subm) )
			menu.add(0, 0, 0, R.string.make_default);
		if( !U.autoreHaCondiviso(subm) ) // può essere eliminato solo se non ha mai condiviso
			menu.add(0, 1, 0, R.string.delete);
		// todo spiegare perché non può essere eliminato?
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		switch( item.getItemId() ) {
			case 0:
				autorePrincipale(subm);
				return true;
			case 1:
				// Todo conferma elimina
				eliminaAutore(subm);
				U.save(false);
				getActivity().recreate();
				return true;
		}
		return false;
	}
}