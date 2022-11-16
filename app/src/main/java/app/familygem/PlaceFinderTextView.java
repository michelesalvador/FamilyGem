package app.familygem;

import android.content.Context;
import android.text.InputType;
import android.util.AttributeSet;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import androidx.appcompat.widget.AppCompatAutoCompleteTextView;
import org.geonames.Style;
import org.geonames.Toponym;
import org.geonames.ToponymSearchCriteria;
import org.geonames.ToponymSearchResult;
import org.geonames.WebService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TextView that suggests Gedcom-style place names using GeoNames
 * */
public class PlaceFinderTextView extends AppCompatAutoCompleteTextView {

	ToponymSearchCriteria searchCriteria;

	public PlaceFinderTextView(Context context, AttributeSet as) {
		super( context, as );
		ListAdapter listAdapter = new ListAdapter( context, android.R.layout.simple_spinner_dropdown_item );
		setAdapter(listAdapter);
		setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
		//setThreshold(2);

		// GeoNames settings
		WebService.setUserName(BuildConfig.utenteGeoNames);
		searchCriteria = new ToponymSearchCriteria();
		searchCriteria.setLanguage(Locale.getDefault().getLanguage()); // en, es, it...
		searchCriteria.setStyle(Style.FULL);
		searchCriteria.setMaxRows(3);
		//searchCriteria.setFuzzy(0.9); // Not with setNameStartsWith
		//searchCriteria.setFeatureClass( FeatureClass.A ); // either one or the other
		//searchCriteria.setFeatureClass( FeatureClass.P );
	}

	class ListAdapter extends ArrayAdapter<String> implements Filterable {
		List<String> places;
		ListAdapter(Context context, int piece ) {
			super( context, piece );
			places = new ArrayList<>();
		}
		@Override
		public int getCount() {
			return places.size();
		}
		@Override
		public String getItem(int index) {
			if( places.size() > 0 && index < places.size() ) // Avoid IndexOutOfBoundsException
				return places.get(index);
			return "";
		}
		@Override
		public Filter getFilter() {
			return new Filter() {
				@Override
				protected FilterResults performFiltering( CharSequence constraint ) {
					FilterResults filterResults = new FilterResults();
					if( constraint != null ) {
						//searchCriteria.setQ(constraint.toString());
						searchCriteria.setNameStartsWith(constraint.toString());
						try {
							ToponymSearchResult searchResult = WebService.search(searchCriteria);
							places.clear();
							for( Toponym topo : searchResult.getToponyms() ) {
								String str = topo.getName(); // Toponym
								if(topo.getAdminName4() != null && !topo.getAdminName4().equals(str))
									str += ", " + topo.getAdminName4(); // Village
								if(topo.getAdminName3() != null && !str.contains(topo.getAdminName3()))
									str += ", " + topo.getAdminName3(); // municipality
								if(!topo.getAdminName2().isEmpty() && !str.contains(topo.getAdminName2()))
									str += ", " + topo.getAdminName2(); // Province/district
								if(!str.contains(topo.getAdminName1()))
									str += ", " + topo.getAdminName1(); // Region
								if(!str.contains(topo.getCountryName()))
									str += ", " + topo.getCountryName(); // Country
								if( str != null && !places.contains( str ) ) // Avoid null and duplicates
									places.add( str );
							}
							filterResults.values = places;
							filterResults.count = places.size();
						} catch( Exception e ) {}
					}
					return filterResults;
				}
				@Override
				protected void publishResults( CharSequence constraint, FilterResults results ) {
					if (results != null && results.count > 0) {
						notifyDataSetChanged();
					} else {
						notifyDataSetInvalidated();
					}
				}
			};
		}
	}
}
