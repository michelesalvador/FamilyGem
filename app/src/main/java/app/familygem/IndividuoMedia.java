// Scheda Foto
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
import java.util.Map;
import app.familygem.visita.ListaMediaContenitore;
import static app.familygem.Globale.gc;

public class IndividuoMedia extends Fragment {

	Person uno;
	ListaMediaContenitore visitaMedia;

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState ) {
		View vistaMedia = inflater.inflate(R.layout.individuo_scheda, container, false);
		if( gc != null ) {
			final LinearLayout scatola = vistaMedia.findViewById( R.id.contenuto_scheda );
			uno = gc.getPerson( getActivity().getIntent().getStringExtra("idIndividuo") );
			if( uno != null ) {
				visitaMedia = new ListaMediaContenitore( gc, true );
				uno.accept( visitaMedia );
				RecyclerView griglia = new RecyclerView( scatola.getContext() );
				griglia.setHasFixedSize( true );
				RecyclerView.LayoutManager gestoreLayout = new GridLayoutManager( getContext(), 2 );
				griglia.setLayoutManager( gestoreLayout );
				AdattatoreGalleriaMedia adattatore = new AdattatoreGalleriaMedia( visitaMedia.listaMedia, true );
				griglia.setAdapter( adattatore );
				scatola.addView( griglia );
			}
		}
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
		if( visitaMedia.listaMedia.size() > 1 && med.getPrimary() == null )
			menu.add(0, 0, 0, R.string.primary_media );
		if( med.getId() != null  )
			menu.add(0, 1, 0, R.string.unlink );
		menu.add(0, 2, 0, R.string.delete );
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		int id = item.getItemId();
		if( id == 0 ) {	// Principale todo oltre a setPrimary bisogna cambiare l'ordine
			for( Map.Entry<Media,Object> entri : visitaMedia.listaMedia.entrySet() )	// Li resetta tutti poi ne contrassegna uno
				entri.getKey().setPrimary( null );
			med.setPrimary( "Y" );
			if( med.getId() != null ) // Per aggiornare la data cambiamento nel Media record piuttosto che nella Person
				U.salvaJson( true, med );
			else
				U.salvaJson( true, uno );
			aggiorna();
			return true;
		} else if( id == 1 ) {	// Scollega
			Galleria.scollegaMedia( med.getId(), (MediaContainer)contenit, vistaFoto );
			U.salvaJson( true, uno );
			aggiorna();
			return true;
		} else if( id == 2 ) {	// Elimina
			Object[] capi = Galleria.eliminaMedia( med, vistaFoto );
			U.salvaJson( true, capi );
			aggiorna();
			return true;
		}
		return false;
	}

	// Rinfresca il contenuto del frammento Media
	void aggiorna() {
		// ricarica il fragment
		getActivity().getSupportFragmentManager().beginTransaction().detach( this ).attach( this ).commit();
		U.unaFoto( Globale.gc, uno, getActivity().findViewById(R.id.persona_foto) );
		U.unaFoto( Globale.gc, uno, getActivity().findViewById(R.id.persona_sfondo) );
		// Scheda eventi
		IndividuoEventi tabEventi = (IndividuoEventi) getActivity().getSupportFragmentManager().findFragmentByTag( "android:switcher:" + R.id.schede_persona + ":1" );
		tabEventi.aggiorna( 1 );
	}
}