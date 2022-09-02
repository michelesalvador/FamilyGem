// Lista dei Media

package app.familygem;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import com.theartofdev.edmodo.cropper.CropImage;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;
import org.folg.gedcom.model.MediaRef;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import app.familygem.visitor.ListaMediaContenitore;
import app.familygem.visitor.RiferimentiMedia;
import app.familygem.visitor.TrovaPila;
import static app.familygem.Global.gc;

public class Galleria extends Fragment {

	ListaMediaContenitore visitaMedia;
	AdattatoreGalleriaMedia adattatore;

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle bandolo ) {
		setHasOptionsMenu( true );
		View vista = inflater.inflate( R.layout.galleria, container, false );
		RecyclerView griglia = vista.findViewById( R.id.galleria );
		griglia.setHasFixedSize( true );
		if( gc != null ) {
			visitaMedia = new ListaMediaContenitore( gc, !getActivity().getIntent().getBooleanExtra("galleriaScegliMedia",false ) );
			gc.accept( visitaMedia );
			arredaBarra();
			RecyclerView.LayoutManager gestoreLayout = new GridLayoutManager( getContext(), 2 );
			griglia.setLayoutManager( gestoreLayout );
			adattatore = new AdattatoreGalleriaMedia( visitaMedia.listaMedia, true );
			griglia.setAdapter( adattatore );
			vista.findViewById( R.id.fab ).setOnClickListener( v ->
					F.appAcquisizioneImmagine( getContext(), Galleria.this, 4546, null )
			);
		}
		return vista;
	}

	// Andandosene dall'attività resetta l'extra se non è stato scelto un media condiviso
	@Override
	public void onPause() {
		super.onPause();
		getActivity().getIntent().removeExtra("galleriaScegliMedia");
	}

	// Scrive il titolo nella barra
	void arredaBarra() {
		((AppCompatActivity)getActivity()).getSupportActionBar().setTitle( visitaMedia.listaMedia.size()
				+ " " + getString(R.string.media).toLowerCase() );
	}

	// Aggiorna i contenuti della galleria
	void ricrea() {
		visitaMedia.listaMedia.clear();
		gc.accept( visitaMedia );
		adattatore.notifyDataSetChanged();
		arredaBarra();
	}

	// todo bypassabile?
	static int popolarita( Media med ) {
		RiferimentiMedia riferiMedia = new RiferimentiMedia( gc, med, false );
		return riferiMedia.num;
	}

	static Media nuovoMedia( Object contenitore ){
		Media media = new Media();
		media.setId( U.nuovoId(gc,Media.class) );
		media.setFileTag("FILE"); // Necessario per poi esportare il Gedcom
		gc.addMedia( media );
		if( contenitore != null ) {
			MediaRef rifMed = new MediaRef();
			rifMed.setRef( media.getId() );
			((MediaContainer)contenitore).addMediaRef( rifMed );
		}
		return media;
	}

	// Scollega da un contenitore un media condiviso
	static void scollegaMedia(String mediaId, MediaContainer container) {
		Iterator<MediaRef> refs = container.getMediaRefs().iterator();
		while( refs.hasNext() ) {
			MediaRef ref = refs.next();
			if( ref.getMedia( Global.gc ) == null // Eventuale ref a un media inesistente
					|| ref.getRef().equals(mediaId) )
				refs.remove();
		}
		if( container.getMediaRefs().isEmpty() )
			container.setMediaRefs( null );
	}

	// Elimina un media condiviso o locale e rimuove i riferimenti nei contenitori
	// Restituisce un array con i capostipiti modificati
	public static Object[] eliminaMedia(Media media, View vista) {
		Set<Object> capi;
		if( media.getId() != null ) { // media OBJECT
			gc.getMedia().remove(media);
			// Elimina i riferimenti in tutti i contenitori
			RiferimentiMedia eliminaMedia = new RiferimentiMedia(gc, media, true);
			capi = eliminaMedia.capostipiti;
		} else { // media LOCALE
			new TrovaPila(gc, media); // trova temporaneamente la pila del media per individuare il container
			MediaContainer container = (MediaContainer)Memoria.oggettoContenitore();
			container.getMedia().remove(media);
			if( container.getMedia().isEmpty() )
				container.setMedia(null);
			capi = new HashSet<>(); // set con un solo Object capostipite
			capi.add( Memoria.oggettoCapo() );
			Memoria.arretra(); // elimina la pila appena creata
		}
		Memoria.annullaIstanze(media);
		if( vista != null )
			vista.setVisibility(View.GONE);
		return capi.toArray(new Object[0]);
	}

	// Il file pescato dal file manager diventa media condiviso
	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data ) {
		if( resultCode == Activity.RESULT_OK ) {
			if( requestCode == 4546 ) { // File preso da app fornitrice viene salvato in Media ed eventualmente ritagliato
				Media media = nuovoMedia(null);
				if( F.proponiRitaglio(getContext(), this, data, media) ) { // se è un'immagine (quindi ritagliabile)
					U.save(false, media);
							// Non deve scattare onRestart() + recreate() perché poi il fragment di arrivo non è più lo stesso
					return;
				}
			} else if( requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE ) {
				F.fineRitaglioImmagine(data);
			}
			U.save(true, Global.mediaCroppato);
		} else if( requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE ) // se clic su freccia indietro in Crop Image
			Global.edited = true;
	}

	// Menu contestuale
	private Media media;
	@Override
	public void onCreateContextMenu( ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info ) {
		media = (Media) vista.getTag( R.id.tag_oggetto );
		menu.add(0, 0, 0, R.string.delete );
	}
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if( item.getItemId() == 0 ) {
			Object[] modificati = eliminaMedia(media, null);
			ricrea();
			U.save(false, modificati);
			return true;
		}
		return false;
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		menu.add(0, 0, 0, R.string.media_folders);
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if( item.getItemId() == 0 ) {
			startActivity(new Intent(getContext(), CartelleMedia.class)
					.putExtra("idAlbero", Global.settings.openTree)
			);
			return true;
		}
		return false;
	}

	@Override
	public void onRequestPermissionsResult(int codice, String[] permessi, int[] accordi) {
		F.risultatoPermessi(getContext(), this, codice, permessi, accordi, null);
	}
}
