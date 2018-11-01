// Scheda Foto
package app.familygem;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Person;
import java.util.Map;
import static app.familygem.Globale.gc;

public class IndividuoMedia extends Fragment {

	Person uno;

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState ) {
		View vistaMedia = inflater.inflate(R.layout.individuo_scheda, container, false);
		final LinearLayout scatola = vistaMedia.findViewById( R.id.contenuto_scheda );
		uno = gc.getPerson( Globale.individuo );
		VisitaListaMedia visitaMedia = new VisitaListaMedia(true);
		uno.accept( visitaMedia );
		for( Map.Entry<Media,Object> m :visitaMedia.listaMedia.entrySet() )
			Galleria.poniMedia( scatola, m.getValue(), m.getKey(), true );
		return vistaMedia;
	}

	// Menu contestuale
	View vistaFoto;
	Media med;
	Object contenit;	// Le immagini non sono solo di 'uno', ma anche dei suoi subordinati EventFact, SourceCitation...
	@Override
	public void onCreateContextMenu( ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info ) {
		vistaFoto = vista;
		med = (Media) vistaFoto.getTag( R.id.tag_oggetto );
		contenit = vistaFoto.getTag( R.id.tag_contenitore );
		if( uno.getAllMedia(Globale.gc).size() > 1 )
			menu.add(0, 0, 0, R.string.primary_media );
		if( med.getId() != null  )
			menu.add(0, 1, 0, R.string.unlink );
		menu.add(0, 2, 0, R.string.delete );
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		int id = item.getItemId();
		if( id == 0 ) {	// Principale todo oltre a setPrimary bisogna cambiare l'ordine
			for( Media m : uno.getAllMedia(Globale.gc) )	// Li resetta tutti poi ne contrassegna uno
				m.setPrimary( null );
			med.setPrimary( "Y" );
			U.salvaJson();
			getActivity().recreate();
			Globale.editato = true;
			return true;
		} else if( id == 1 ) {	// Scollega
			Galleria.scollegaMedia( med, contenit, vistaFoto );
			U.salvaJson();
			Globale.editato = true;
			return true;
		} else if( id == 2 ) {	// Elimina
			Galleria.eliminaMedia( med, contenit, vistaFoto );
			U.salvaJson();
			Globale.editato = true;
			return true;
		}
		return false;
	}
}