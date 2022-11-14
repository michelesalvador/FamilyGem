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

/**
 * List of Submitters (authors)
 * */
public class ListOfAuthorsFragment extends Fragment {

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle stato) {
		List<Submitter> authors = gc.getSubmitters();
		((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(authors.size() + " " +
				getString(authors.size() == 1 ? R.string.submitter : R.string.submitters).toLowerCase());
		setHasOptionsMenu(true);
		View view = inflater.inflate(R.layout.magazzino, container, false);
		LinearLayout layout = view.findViewById(R.id.magazzino_scatola);
		for( final Submitter author : authors ) {
			View pieceView = inflater.inflate(R.layout.magazzino_pezzo, layout, false);
			layout.addView(pieceView);
			((TextView)pieceView.findViewById(R.id.magazzino_nome)).setText(TreeInfoActivity.nomeAutore(author));
			pieceView.findViewById(R.id.magazzino_archivi).setVisibility(View.GONE);
			pieceView.setOnClickListener(v -> {
				Memory.setFirst(author);
				startActivity(new Intent(getContext(), AuthorActivity.class));
			});
			registerForContextMenu(pieceView);
			pieceView.setTag(author);
		}
		view.findViewById(R.id.fab).setOnClickListener(v -> {
			newAuthor(getContext());
			U.save(true);
		});
		return view;
	}

	/**
	 * Remove an author
	 * All I know is that any SubmitterRef should be searched for in all records
	 * */
	public static void deleteAuthor(Submitter aut) {
		Header testa = gc.getHeader();
		if( testa != null && testa.getSubmitterRef() != null
				&& testa.getSubmitterRef().equals(aut.getId()) ) {
			testa.setSubmitterRef(null);
		}
		gc.getSubmitters().remove(aut);
		if( gc.getSubmitters().isEmpty() )
			gc.setSubmitters(null);
		Memory.setInstanceAndAllSubsequentToNull(aut);
	}

	/**
	 * Create a new Author, if it receives a context it opens it in editor mode
	 * */
	static Submitter newAuthor(Context context) {
		Submitter submitter = new Submitter();
		submitter.setId(U.newID(gc, Submitter.class));
		submitter.setName("");
		U.updateChangeDate(submitter);
		gc.addSubmitter(submitter);
		if( context != null ) {
			Memory.setFirst(submitter);
			context.startActivity(new Intent(context, AuthorActivity.class));
		}
		return submitter;
	}

	static void mainAuthor(Submitter subm) {
		Header testa = gc.getHeader();
		if( testa == null ) {
			testa = NewTree.createHeader(Global.settings.openTree + ".json");
			gc.setHeader(testa);
		}
		testa.setSubmitterRef(subm.getId());
		U.save(false, subm);
	}

	// context Menu
	Submitter subm;
	@Override
	public void onCreateContextMenu(ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info ) {
		subm = (Submitter)vista.getTag();
		if( gc.getHeader() == null || gc.getHeader().getSubmitter(gc) == null || !gc.getHeader().getSubmitter(gc).equals(subm) )
			menu.add(0, 0, 0, R.string.make_default);
		if( !U.submitterHasShared(subm) ) // it can only be deleted if it has never been shared
			menu.add(0, 1, 0, R.string.delete);
		// todo explain why it can't be deleted?
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		switch( item.getItemId() ) {
			case 0:
				mainAuthor(subm);
				return true;
			case 1:
				// Todo confirm deletion
				deleteAuthor(subm);
				U.save(false);
				getActivity().recreate();
				return true;
		}
		return false;
	}
}