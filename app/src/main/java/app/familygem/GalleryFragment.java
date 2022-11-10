package app.familygem;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
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
import app.familygem.visitor.MediaListContainer;
import app.familygem.visitor.MediaReferences;
import app.familygem.visitor.FindStack;
import static app.familygem.Global.gc;

/**
 * List of Media
 * */
public class GalleryFragment extends Fragment {

	MediaListContainer mediaVisitor;
	MediaGalleryAdapter adapter;

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle bandolo ) {
		setHasOptionsMenu( true );
		View view = inflater.inflate( R.layout.galleria, container, false );
		RecyclerView recyclerView = view.findViewById( R.id.galleria );
		recyclerView.setHasFixedSize( true );
		if( gc != null ) {
			mediaVisitor = new MediaListContainer( gc, !getActivity().getIntent().getBooleanExtra("galleriaScegliMedia",false ) );
			gc.accept(mediaVisitor);
			setToolbarTitle();
			RecyclerView.LayoutManager gestoreLayout = new GridLayoutManager( getContext(), 2 );
			recyclerView.setLayoutManager( gestoreLayout );
			adapter = new MediaGalleryAdapter( mediaVisitor.mediaList, true );
			recyclerView.setAdapter(adapter);
			view.findViewById( R.id.fab ).setOnClickListener( v ->
					F.displayImageCaptureDialog( getContext(), GalleryFragment.this, 4546, null )
			);
		}
		return view;
	}

	/**
	 * Leaving the activity resets the extra if no shared media has been chosen
	 * // Andandosene dall'attività resetta l'extra se non è stato scelto un media condiviso
	 * */
	@Override
	public void onPause() {
		super.onPause();
		getActivity().getIntent().removeExtra("galleriaScegliMedia");
	}

	void setToolbarTitle() {
		((AppCompatActivity)getActivity()).getSupportActionBar().setTitle( mediaVisitor.mediaList.size()
				+ " " + getString(R.string.media).toLowerCase() );
	}

	/**
	 * Update the contents of the gallery
	 * */
	void recreate() {
		mediaVisitor.mediaList.clear();
		gc.accept(mediaVisitor);
		adapter.notifyDataSetChanged();
		setToolbarTitle();
	}

	// todo bypassabile?
	static int popularity(Media med ) {
		MediaReferences riferiMedia = new MediaReferences( gc, med, false );
		return riferiMedia.num;
	}

	static Media newMedia(Object container ){
		Media media = new Media();
		media.setId( U.newID(gc,Media.class) );
		media.setFileTag("FILE"); // Necessary to then export the Gedcom
		gc.addMedia( media );
		if( container != null ) {
			MediaRef mediaRef = new MediaRef();
			mediaRef.setRef( media.getId() );
			((MediaContainer)container).addMediaRef( mediaRef );
		}
		return media;
	}

	/**
	 * Detach a shared media from a container
	 * */
	static void disconnectMedia(String mediaId, MediaContainer container) {
		Iterator<MediaRef> refs = container.getMediaRefs().iterator();
		while( refs.hasNext() ) {
			MediaRef ref = refs.next();
			if( ref.getMedia( Global.gc ) == null // Possible ref to a non-existent media
					|| ref.getRef().equals(mediaId) )
				refs.remove();
		}
		if( container.getMediaRefs().isEmpty() )
			container.setMediaRefs( null );
	}

	/**
	 * // Delete a shared or local media and remove references in containers
	 * // Return an array with modified progenitors
	 *
	 * // Elimina un media condiviso o locale e rimuove i riferimenti nei contenitori
	 * // Restituisce un array con i capostipiti modificati
	 * */
	public static Object[] deleteMedia(Media media, View view) {
		Set<Object> heads;
		if( media.getId() != null ) { // media OBJECT
			gc.getMedia().remove(media);
			// Delete references in all containers
			MediaReferences deleteMedia = new MediaReferences(gc, media, true);
			heads = deleteMedia.founders;
		} else { // media LOCALE
			new FindStack(gc, media); //temporarily find the media stack to locate the container // trova temporaneamente la pila del media per individuare il container
			MediaContainer container = (MediaContainer) Memory.getSecondToLastObject();
			container.getMedia().remove(media);
			if( container.getMedia().isEmpty() )
				container.setMedia(null);
			heads = new HashSet<>(); // set with only one parent Object
			heads.add( Memory.firstObject() );
			Memory.clearStackAndRemove(); // delete the stack you just created
		}
		Memory.setInstanceAndAllSubsequentToNull(media);
		if( view != null )
			view.setVisibility(View.GONE);
		return heads.toArray(new Object[0]);
	}

	/**
	 * The file fished by the file manager becomes shared media
	 * // Il file pescato dal file manager diventa media condiviso
	 * */
	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data ) {
		if( resultCode == Activity.RESULT_OK ) {
			if( requestCode == 4546 ) { //File taken from the supplier app is saved in Media and possibly cropped // File preso da app fornitrice viene salvato in Media ed eventualmente ritagliato
				Media media = newMedia(null);
				if( F.proposeCropping(getContext(), this, data, media) ) { // if it is an image (therefore it can be cropped)
					U.save(false, media);
							//onRestart () + recreate () must not be triggered because then the arrival fragment is no longer the same // Non deve scattare onRestart() + recreate() perché poi il fragment di arrivo non è più lo stesso
					return;
				}
			} else if( requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE ) {
				F.endImageCropping(data);
			}
			U.save(true, Global.croppedMedia);
		} else if( requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE ) // if you click the back arrow in Crop Image
			Global.edited = true;
	}

	// contextual Menu
	private Media media;
	@Override
	public void onCreateContextMenu( ContextMenu menu, View view, ContextMenu.ContextMenuInfo info ) {
		media = (Media) view.getTag( R.id.tag_object );
		menu.add(0, 0, 0, R.string.delete );
	}
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if( item.getItemId() == 0 ) {
			Object[] modified = deleteMedia(media, null);
			recreate();
			U.save(false, modified);
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
			startActivity(new Intent(getContext(), MediaFolders.class)
					.putExtra("idAlbero", Global.settings.openTree)
			);
			return true;
		}
		return false;
	}

	@Override
	public void onRequestPermissionsResult(int codice, @NonNull String[] permission, @NonNull int[] grantResults) {
		F.permissionsResult(getContext(), this, codice, permission, grantResults, null);
	}
}
