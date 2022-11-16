package app.familygem;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.RecyclerView;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import app.familygem.detail.SourceActivity;
import app.familygem.visitor.ListOfSourceCitations;
import static app.familygem.Global.gc;

/**
 * List of Sources (Sources)
 * Unlike {@link ChurchFragment} it uses an adapter for the RecyclerView
 * */
public class LibraryFragment extends Fragment {

	private List<Source> listOfSources;
	private LibraryAdapter adapter;
	private int order;

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle bandolo ) {
		listOfSources = gc.getSources();
		((AppCompatActivity) getActivity()).getSupportActionBar().setTitle( listOfSources.size() + " " +
				getString(listOfSources.size()==1 ? R.string.source : R.string.sources).toLowerCase() );
		if( listOfSources.size() > 1 )
			setHasOptionsMenu(true);
		View view = inflater.inflate(R.layout.biblioteca, container, false);
		RecyclerView sources = view.findViewById( R.id.riciclatore );
		adapter = new LibraryAdapter();
		sources.setAdapter(adapter);
		view.findViewById( R.id.fab ).setOnClickListener( v -> newSource( getContext(), null ) );
		return view;
	}

	public class LibraryAdapter extends RecyclerView.Adapter<SourceViewHolder> implements Filterable {
		@Override
		public SourceViewHolder onCreateViewHolder(ViewGroup parent, int type ) {
			View sourceView = LayoutInflater.from( parent.getContext() )
					.inflate(R.layout.biblioteca_pezzo, parent, false);
			registerForContextMenu( sourceView );
			return new SourceViewHolder( sourceView );
		}
		@Override
		public void onBindViewHolder(SourceViewHolder holder, int position ) {
			Source source = listOfSources.get(position);
			holder.id.setText( source.getId() );
			holder.id.setVisibility( order == 1 || order == 2 ? View.VISIBLE : View.GONE  );
			holder.title.setText( sourceTitle(source) );
			Object times = source.getExtension("citaz");
			// Count citations with my method
			if( times == null ) {
				times = countCitations( source );
				source.putExtension("citaz", times );
			}
			holder.times.setText( String.valueOf(times) );
		}
		// Filter source titles based on search words
		@Override
		public Filter getFilter() {
			return new Filter() {
				@Override
				protected FilterResults performFiltering(CharSequence charSequence) {
					String query = charSequence.toString();
					if (query.isEmpty()) {
						listOfSources = gc.getSources();
					} else {
						List<Source> filteredList = new ArrayList<>();
						for (Source source : gc.getSources()) {
							if( sourceTitle(source).toLowerCase().contains(query.toLowerCase()) ) {
								filteredList.add(source);
							}
						}
						listOfSources = filteredList;
					}
					sortSources(); // Sorting the query reorders those that appear
					FilterResults filterResults = new FilterResults();
					filterResults.values = listOfSources;
					return filterResults;
				}
				@Override
				protected void publishResults(CharSequence cs, FilterResults fr) {
					notifyDataSetChanged();
				}
			};
		}
		@Override
		public int getItemCount() {
			return listOfSources.size();
		}
	}

	class SourceViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
		TextView id;
		TextView title;
		TextView times;
		SourceViewHolder(View view ) {
			super( view );
			id = view.findViewById( R.id.biblioteca_id );
			title = view.findViewById( R.id.biblioteca_titolo );
			times = view.findViewById( R.id.biblioteca_volte );
			view.setOnClickListener(this);
		}
		@Override
		public void onClick( View v ) {
			// Returns the id of a source to IndividualPersonActivity and DetailActivity
			if( getActivity().getIntent().getBooleanExtra("bibliotecaScegliFonte",false) ) {
				Intent intent = new Intent();
				intent.putExtra("idFonte", id.getText().toString() );
				getActivity().setResult( Activity.RESULT_OK, intent );
				getActivity().finish();
			} else {
				Source source = gc.getSource( id.getText().toString() );
				Memory.setFirst( source );
				startActivity( new Intent( getContext(), SourceActivity.class ) );
			}
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		getActivity().getIntent().removeExtra("bibliotecaScegliFonte");
	}

	/**
	 * Sort the sources according to one of the criteria.
	 * The order then becomes permanent in the Json.
	 * */
	private void sortSources() {
		if( order > 0 ) {
			if( order == 5 || order == 6 ) {
				for( Source source : listOfSources) {
					if( source.getExtension("citaz") == null )
						source.putExtension( "citaz", countCitations(source) );
				}
			}
			Collections.sort(listOfSources, (f1, f2) -> {
				switch(order) {
					case 1:	// Sort by numeric id
						return U.extractNum(f1.getId()) - U.extractNum(f2.getId());
					case 2:
						return U.extractNum(f2.getId()) - U.extractNum(f1.getId());
					case 3:	// Alphabetical order of titles
						return sourceTitle(f1).compareToIgnoreCase( sourceTitle(f2) );
					case 4:
						return sourceTitle(f2).compareToIgnoreCase( sourceTitle(f1) );
					case 5:	// Sort by number of citations
						return U.castJsonInt(f1.getExtension("citaz")) - U.castJsonInt(f2.getExtension("citaz"));
					case 6:
						return U.castJsonInt(f2.getExtension("citaz")) - U.castJsonInt(f1.getExtension("citaz"));
				}
				return 0;
			});
		}
	}

	/**
	 * Returns the title of the source
	 * */
	static String sourceTitle(Source fon ) {
		String tit = "";
		if( fon != null )
			if( fon.getAbbreviation() != null )
				tit = fon.getAbbreviation();
			else if( fon.getTitle() != null )
				tit = fon.getTitle();
			else if( fon.getText() != null ) {
				tit = fon.getText().replaceAll("\n", " ");
				//tit = tit.length() > 35 ? tit.substring(0,35)+"…" : tit;
			} else if( fon.getPublicationFacts() != null ) {
				tit = fon.getPublicationFacts().replaceAll("\n", " ");
			}
		return tit;
	}

	private int count;
	/**
	 * Returns how many times a source is cited in Gedcom
	 * I tried to rewrite it as Visitor, but it's much slower
	 * */
	private int countCitations(Source source ) {
		count = 0;
		for( Person p : Global.gc.getPeople() ) {
			countCitations( p, source );
			for( Name n : p.getNames() )
				countCitations( n, source );
			for( EventFact ef : p.getEventsFacts() )
				countCitations( ef, source );
		}
		for( Family f : Global.gc.getFamilies() ) {
			countCitations( f, source );
			for( EventFact ef : f.getEventsFacts() )
				countCitations( ef, source );
		}
		for( Note n : Global.gc.getNotes() )
			countCitations( n, source );
		return count;
	}

	/**
	 * receives an Object (Person, Name, EventFact...) and counts how many times the source is cited
	 * */
	private void countCitations(Object object, Source source ) {
		List<SourceCitation> sourceCitations;
		if( object instanceof Note )	// if it is a Note
			sourceCitations = ((Note) object).getSourceCitations();
		else {
			for( Note n : ((NoteContainer) object).getNotes() )
				countCitations( n, source );
			sourceCitations = ((SourceCitationContainer) object).getSourceCitations();
		}
		for( SourceCitation sc : sourceCitations ) {
			if( sc.getRef() != null )
				if( sc.getRef().equals(source.getId()) )
					count++;
		}
	}

	static void newSource(Context context, Object container ){
		Source source = new Source();
		source.setId( U.newID( gc, Source.class ) );
		source.setTitle( "" );
		gc.addSource( source );
		if( container != null ) {
			SourceCitation sourceCitation = new SourceCitation();
			sourceCitation.setRef( source.getId() );
			if( container instanceof Note ) ((Note)container).addSourceCitation( sourceCitation );
			else ((SourceCitationContainer)container).addSourceCitation( sourceCitation );
		}
		U.save( true, source );
		Memory.setFirst( source );
		context.startActivity( new Intent( context, SourceActivity.class ) );
	}

	/**
	 * // Remove the source, the Refs in all SourceCitations pointing to it, and empty SourceCitations
	 * All citations to the deleted Source become [{@link Source}]s to which a Source should be able to be reattached
	 * @return an array of modified parents
	 *  */
	public static Object[] deleteSource(Source source ) {
		ListOfSourceCitations citations = new ListOfSourceCitations( gc, source.getId() );
		for( ListOfSourceCitations.Triplet citation : citations.list) {
			SourceCitation sc = citation.citation;
			sc.setRef( null );
			// If the SourceCitation contains nothing else, it can be deleted
			boolean deletable = true;
			if( sc.getPage()!=null || sc.getDate()!=null || sc.getText()!=null || sc.getQuality()!=null
					|| !sc.getAllNotes(gc).isEmpty() || !sc.getAllMedia(gc).isEmpty() || !sc.getExtensions().isEmpty() )
				deletable = false;
			if( deletable ) {
				Object container = citation.container;
				List<SourceCitation> list;
				if( container instanceof Note )
					list = ((Note)container).getSourceCitations();
				else
					list = ((SourceCitationContainer)container).getSourceCitations();
				list.remove( sc );
				if( list.isEmpty() ) {
					if( container instanceof Note )
						((Note)container).setSourceCitations( null );
					else
						((SourceCitationContainer)container).setSourceCitations( null );
				}
			}
		}
		gc.getSources().remove( source );
		if( gc.getSources().isEmpty() )
			gc.setSources( null );
		gc.createIndexes();	// necessary
		Memory.setInstanceAndAllSubsequentToNull( source );
		return citations.getProgenitors();
	}

	// options menu in the toolbar
	@Override
	public void onCreateOptionsMenu( Menu menu, MenuInflater inflater ) {
		SubMenu subMenu = menu.addSubMenu(R.string.order_by);
		if( Global.settings.expert )
			subMenu.add(0, 1, 0, R.string.id);
		subMenu.add(0, 2, 0, R.string.title);
		subMenu.add(0, 3, 0, R.string.citations);

		// Search in the Library
		inflater.inflate(R.menu.cerca, menu);
		final SearchView searchView = (SearchView)menu.findItem(R.id.ricerca).getActionView();
		searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextChange(String query) {
				adapter.getFilter().filter(query);
				return true;
			}
			@Override
			public boolean onQueryTextSubmit(String q) {
				searchView.clearFocus();
				return false;
			}
		});
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
		int id = item.getItemId();
		if( id > 0 && id <= 3 ) {
			if( order == id*2-1 )
				order++;
			else if( order == id*2 )
				order--;
			else
				order = id*2-1;
			sortSources();
			adapter.notifyDataSetChanged();
			return true;
		}
		return false;
	}

	private Source source;
	@Override
	public void onCreateContextMenu(ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info) {
		source = gc.getSource(((TextView)vista.findViewById(R.id.biblioteca_id)).getText().toString());
		if( Global.settings.expert )
			menu.add(0, 0, 0, R.string.edit_id);
		menu.add(0, 1, 0, R.string.delete);
	}
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if( item.getItemId() == 0 ) { // Edit source ID
			U.editId(getContext(), source, getActivity()::recreate);
		} else if( item.getItemId() == 1 ) { // Delete source
			Object[] objects = deleteSource(source);
			U.save(false, objects);
			getActivity().recreate();
		} else {
			return false;
		}
		return true;
	}
}