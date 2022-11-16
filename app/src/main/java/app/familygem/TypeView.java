package app.familygem;

import android.content.Context;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import androidx.appcompat.widget.AppCompatAutoCompleteTextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Create a combo box to choose a type text from a list of predefined values
 * */
public class TypeView extends AppCompatAutoCompleteTextView {

	enum Combo {NAME, RELATIONSHIP}
	List<String> completeTypes = new ArrayList<>();

	public TypeView(Context context, Combo combo) {
		super( context );
		Map<String, Integer> types = getTypes(combo);
		for( String type : types.keySet() ) {
			if( !Locale.getDefault().getLanguage().equals("en") )
				type += " - " + context.getString(types.get(type)); // Translation into all languages other than English
			completeTypes.add(type);
		}
		ListAdapter listAdapter = new ListAdapter( context, android.R.layout.simple_spinner_dropdown_item, completeTypes);
		setAdapter( listAdapter );
		setId( R.id.fatto_edita );
		//setThreshold(0); // useless, the minimum is 1
		setInputType( InputType.TYPE_CLASS_TEXT );
		setOnItemClickListener( (parent, view, position, id) -> {
			setText((String)types.keySet().toArray()[position]);
		});
		setOnFocusChangeListener( (view, hasFocus) -> {
			if( hasFocus )
				showDropDown();
		});
	}

	static Map<String, Integer> getTypes(Combo combo) {
		switch( combo ) {
			case NAME:
				return ImmutableMap(
						"aka", R.string.aka,
						"birth", R.string.birth,
						"immigrant", R.string.immigrant,
						"maiden", R.string.maiden,
						"married", R.string.married
				);
			case RELATIONSHIP:
				return ImmutableMap(
						"unknown", R.string.unknown_relationship,
						"marriage", R.string.marriage,
						"not married", R.string.not_married,
						"civil", R.string.civil_marriage,
						"religious", R.string.religious_marriage,
						"common law", R.string.common_law_marriage,
						"partnership", R.string.partnership,
						"registered partnership", R.string.registered_partnership,
						"living together", R.string.living_together,
						"living apart together", R.string.living_apart_together
				);
			default:
				return null;
		}
	}

	/**
	 * Create a Map from a list of values
	 */
	static Map<String, Integer> ImmutableMap(Object... keyValPair) {
		Map<String, Integer> map = new LinkedHashMap<>();
		if( keyValPair.length % 2 != 0 ) {
			throw new IllegalArgumentException("Keys and values must be pairs.");
		}
		for( int i = 0; i < keyValPair.length; i += 2 ) {
			map.put((String)keyValPair[i], (Integer)keyValPair[i + 1]);
		}
		return Collections.unmodifiableMap(map);
	}

	@Override
	public boolean enoughToFilter() {
		return true; // Always show hints
	}

	class ListAdapter extends ArrayAdapter<String> {
		ListAdapter(Context context, int piece, List<String> strings ) {
			super( context, piece, strings );
		}
		@Override
		public Filter getFilter() {
			return new Filter() {
				@Override
				protected FilterResults performFiltering( CharSequence constraint ) {
					FilterResults result = new FilterResults();
					result.values = completeTypes;
					result.count = completeTypes.size();
					return result;
				}
				@Override
				protected void publishResults( CharSequence constraint, FilterResults results ) {
					notifyDataSetChanged();
				}
			};
		}
	}

	/**
	 * Find the translation for predefined English types, or returns the provided type
	 */
	static String getTranslatedType(String type, Combo combo) {
		Map<String, Integer> types = getTypes(combo);
		Integer translation = types.get(type);
		return translation != null ? Global.context.getString(translation).toLowerCase() : type;
	}
}
