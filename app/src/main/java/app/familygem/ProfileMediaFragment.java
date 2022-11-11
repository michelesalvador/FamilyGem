package app.familygem;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;
import org.folg.gedcom.model.Person;
import app.familygem.list.GalleryFragment;
import app.familygem.list.GalleryAdapter;
import app.familygem.visitor.ListaMediaContenitore;
import static app.familygem.Global.gc;

// Profile media tab
public class ProfileMediaFragment extends Fragment {

	Person one;
	ListaMediaContenitore mediaVisitor;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View vistaMedia = inflater.inflate(R.layout.individuo_scheda, container, false);
		if( gc != null ) {
			final LinearLayout scatola = vistaMedia.findViewById(R.id.contenuto_scheda);
			one = gc.getPerson(Global.indi);
			if( one != null ) {
				mediaVisitor = new ListaMediaContenitore(gc, true);
				one.accept(mediaVisitor);
				RecyclerView griglia = new RecyclerView(scatola.getContext());
				griglia.setHasFixedSize(true);
				RecyclerView.LayoutManager gestoreLayout = new GridLayoutManager(getContext(), 2);
				griglia.setLayoutManager(gestoreLayout);
				GalleryAdapter adattatore = new GalleryAdapter(mediaVisitor.listaMedia, true);
				griglia.setAdapter(adattatore);
				scatola.addView(griglia);
			}
		}
		return vistaMedia;
	}

	// Context menu
	Media media;
	Object container; // Le immagini non sono solo di 'uno', ma anche dei suoi subordinati EventFact, SourceCitation...
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo info) {
		media = (Media)view.getTag(R.id.tag_oggetto);
		container = view.getTag(R.id.tag_contenitore);
		if( mediaVisitor.listaMedia.size() > 1 && media.getPrimary() == null )
			menu.add(0, 0, 0, R.string.primary_media);
		if( media.getId() != null )
			menu.add(0, 1, 0, R.string.unlink);
		menu.add(0, 2, 0, R.string.delete);
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		int id = item.getItemId();
		if( id == 0 ) { // Principale
			for( ListaMediaContenitore.MedCont medCont : mediaVisitor.listaMedia ) // Li resetta tutti poi ne contrassegna uno
				medCont.media.setPrimary(null);
			media.setPrimary("Y");
			if( media.getId() != null ) // Per aggiornare la data cambiamento nel Media record piuttosto che nella Person
				U.save(true, media);
			else
				U.save(true, one);
			refresh();
			return true;
		} else if( id == 1 ) { // Scollega
			GalleryFragment.scollegaMedia(media.getId(), (MediaContainer)container);
			U.save(true, one);
			refresh();
			return true;
		} else if( id == 2 ) { // Elimina
			Object[] capi = GalleryFragment.eliminaMedia(media, null);
			U.save(true, capi);
			refresh();
			return true;
		}
		return false;
	}

	// Refresh the content
	void refresh() {
		((ProfileActivity)requireActivity()).refresh();
	}
}
