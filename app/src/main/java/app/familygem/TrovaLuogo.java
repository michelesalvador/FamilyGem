// Classe di servizio per suggerire i nomi dei luoghi formattati in stile Gedcom grazie a GeoNames

package app.familygem;

import android.content.Context;
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

public class TrovaLuogo extends AppCompatAutoCompleteTextView {

	ToponymSearchCriteria searchCriteria;

	public TrovaLuogo( Context contesto, AttributeSet as ) {
		super( contesto, as );
		AdattatoreLista adattatoreLista = new AdattatoreLista( contesto, android.R.layout.simple_spinner_dropdown_item );
		setAdapter(adattatoreLista);
		//setThreshold(2);

		// GeoNames settings
		WebService.setUserName(BuildConfig.utenteGeoNames);
		searchCriteria = new ToponymSearchCriteria();
		searchCriteria.setLanguage(Locale.getDefault().getLanguage()); // en, es, it...
		searchCriteria.setStyle(Style.FULL);
		searchCriteria.setMaxRows(3);
		//searchCriteria.setFuzzy(0.9); // No con setNameStartsWith
		//searchCriteria.setFeatureClass( FeatureClass.A ); // o uno o l'altro
		//searchCriteria.setFeatureClass( FeatureClass.P );
	}

	class AdattatoreLista extends ArrayAdapter<String> implements Filterable {
		List<String> places;
		AdattatoreLista( Context contesto, int pezzo ) {
			super( contesto, pezzo );
			places = new ArrayList<>();
		}
		@Override
		public int getCount() {
			return places.size();
		}
		@Override
		public String getItem(int index) {
			return places.get(index);
		}
		@Override
		public Filter getFilter() {
			return new Filter() {
				@Override
				protected FilterResults performFiltering( CharSequence constraint ) {
					FilterResults filterResults = new FilterResults();
					if (constraint != null && !BuildConfig.utenteGeoNames.isEmpty()) {
						//searchCriteria.setQ(constraint.toString());
						searchCriteria.setNameStartsWith(constraint.toString());
						try {
							ToponymSearchResult searchResult = WebService.search(searchCriteria);
							places.clear();
							for( Toponym topo : searchResult.getToponyms() ) {
								String str = topo.getName(); // Toponimo
								if(topo.getAdminName4() != null && !topo.getAdminName4().equals(str))
									str += ", " + topo.getAdminName4(); // Paese
								if(topo.getAdminName3() != null && !str.contains(topo.getAdminName3()))
									str += ", " + topo.getAdminName3(); // Comune
								if(!topo.getAdminName2().isEmpty() && !str.contains(topo.getAdminName2()))
									str += ", " + topo.getAdminName2(); // Provincia
								if(!str.contains(topo.getAdminName1()))
									str += ", " + topo.getAdminName1(); // Regione
								if(!str.contains(topo.getCountryName()))
									str += ", " + topo.getCountryName(); // Nazione
								if( places.indexOf( str ) < 0 ) // Avoid duplicates
									places.add( str );
							}
							filterResults.values = places;
							filterResults.count = places.size();
						} catch( Exception e ) {
							//e.printStackTrace();
						}
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
