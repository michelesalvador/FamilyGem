// Scheda Foto
package app.familygem;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
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
import app.familygem.visita.ListaMediaContenitore;
import static app.familygem.Global.gc;

public class IndividuoMedia extends Fragment {

	Person uno;
	ListaMediaContenitore visitaMedia;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View vistaMedia = inflater.inflate(R.layout.individuo_scheda, container, false);
		if( gc != null ) {
			final LinearLayout scatola = vistaMedia.findViewById(R.id.contenuto_scheda);
			uno = gc.getPerson(Global.indi);
			if( uno != null ) {
				visitaMedia = new ListaMediaContenitore(gc, true);
				uno.accept(visitaMedia);
				RecyclerView griglia = new RecyclerView(scatola.getContext());
				griglia.setHasFixedSize(true);
				RecyclerView.LayoutManager gestoreLayout = new GridLayoutManager(getContext(), 2);
				griglia.setLayoutManager(gestoreLayout);
				AdattatoreGalleriaMedia adattatore = new AdattatoreGalleriaMedia(visitaMedia.listaMedia, true);
				griglia.setAdapter(adattatore);
				scatola.addView(griglia);
			}
		}
		return vistaMedia;
	}

	// Menu contestuale
	Media media;
	Object container; // Le immagini non sono solo di 'uno', ma anche dei suoi subordinati EventFact, SourceCitation...
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo info) {
		media = (Media)view.getTag(R.id.tag_oggetto);
		container = view.getTag(R.id.tag_contenitore);
		if( visitaMedia.listaMedia.size() > 1 && media.getPrimary() == null )
			menu.add(0, 0, 0, R.string.primary_media);
		if( media.getId() != null )
			menu.add(0, 1, 0, R.string.unlink);
		menu.add(0, 2, 0, R.string.delete);
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		int id = item.getItemId();
		if( id == 0 ) { // Principale
			for( ListaMediaContenitore.MedCont medCont : visitaMedia.listaMedia ) // Li resetta tutti poi ne contrassegna uno
				medCont.media.setPrimary(null);
			media.setPrimary("Y");
			if( media.getId() != null ) // Per aggiornare la data cambiamento nel Media record piuttosto che nella Person
				U.salvaJson(true, media);
			else
				U.salvaJson(true, uno);
			refresh();
			return true;
		} else if( id == 1 ) { // Scollega
			Galleria.scollegaMedia(media.getId(), (MediaContainer)container);
			U.salvaJson(true, uno);
			refresh();
			return true;
		} else if( id == 2 ) { // Elimina
			Object[] capi = Galleria.eliminaMedia(media, null);
			U.salvaJson(true, capi);
			refresh();
			return true;
		}
		return false;
	}

	// Rinfresca il contenuto del frammento Media
	void refresh() {
		// ricarica il fragment
		FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
		fragmentManager.beginTransaction().detach(this).commit();
		fragmentManager.beginTransaction().attach(this).commit();
		F.unaFoto(Global.gc, uno, requireActivity().findViewById(R.id.persona_foto));
		F.unaFoto(Global.gc, uno, requireActivity().findViewById(R.id.persona_sfondo));
		// Scheda eventi
		IndividuoEventi tabEventi = (IndividuoEventi)requireActivity().getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.schede_persona + ":1");
		tabEventi.refresh(1);
	}
}
