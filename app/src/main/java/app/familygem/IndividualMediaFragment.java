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
import app.familygem.visitor.MediaListContainer;
import static app.familygem.Global.gc;

/**
 * Photo tab
 * */
public class IndividualMediaFragment extends Fragment {

	Person person;
	MediaListContainer mediaListContainer;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View vistaMedia = inflater.inflate(R.layout.individuo_scheda, container, false);
		if( gc != null ) {
			final LinearLayout layout = vistaMedia.findViewById(R.id.contenuto_scheda);
			person = gc.getPerson(Global.indi);
			if( person != null ) {
				mediaListContainer = new MediaListContainer(gc, true);
				person.accept(mediaListContainer);
				RecyclerView recyclerView = new RecyclerView(layout.getContext());
				recyclerView.setHasFixedSize(true);
				RecyclerView.LayoutManager layoutManager = new GridLayoutManager(getContext(), 2);
				recyclerView.setLayoutManager(layoutManager);
				MediaGalleryAdapter adapter = new MediaGalleryAdapter(mediaListContainer.mediaList, true);
				recyclerView.setAdapter(adapter);
				layout.addView(recyclerView);
			}
		}
		return vistaMedia;
	}

	// context Menu
	Media media;
	/**
	 * The images are not only of {@link #person}, but also of its subordinates {@link org.folg.gedcom.model.EventFact}, {@link org.folg.gedcom.model.SourceCitation} ...
	 * */
	Object container;
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo info) {
		media = (Media)view.getTag(R.id.tag_object);
		container = view.getTag(R.id.tag_contenitore);
		if( mediaListContainer.mediaList.size() > 1 && media.getPrimary() == null )
			menu.add(0, 0, 0, R.string.primary_media);
		if( media.getId() != null )
			menu.add(0, 1, 0, R.string.unlink);
		menu.add(0, 2, 0, R.string.delete);
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		int id = item.getItemId();
		if( id == 0 ) { // Principal
			for( MediaListContainer.MedCont medCont : mediaListContainer.mediaList) // It resets them all then marks this.person
				medCont.media.setPrimary(null);
			media.setPrimary("Y");
			if( media.getId() != null ) // To update the change date in the Media record rather than in the Person
				U.save(true, media);
			else
				U.save(true, person);
			refresh();
			return true;
		} else if( id == 1 ) { // Scollega
			GalleryFragment.disconnectMedia(media.getId(), (MediaContainer)container);
			U.save(true, person);
			refresh();
			return true;
		} else if( id == 2 ) { // Delete
			Object[] capi = GalleryFragment.deleteMedia(media, null);
			U.save(true, capi);
			refresh();
			return true;
		}
		return false;
	}

	/**
	 * Refresh the contents of the Media snippet
	 * */
	void refresh() {
		// refill the fragment
		FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
		fragmentManager.beginTransaction().detach(this).commit();
		fragmentManager.beginTransaction().attach(this).commit();
		F.showMainImageForPerson(Global.gc, person, requireActivity().findViewById(R.id.persona_foto));
		F.showMainImageForPerson(Global.gc, person, requireActivity().findViewById(R.id.persona_sfondo));
		// Events tab
		IndividualEventsFragment eventsTab = (IndividualEventsFragment)requireActivity().getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.schede_persona + ":1");
		eventsTab.refresh(1);
	}
}
