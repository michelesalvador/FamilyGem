// Classe di servizio che crea un "combo box" per scegliere un name type in Nome

package app.familygem;

import android.content.Context;
import android.text.InputType;
import android.util.AttributeSet;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import androidx.appcompat.widget.AppCompatAutoCompleteTextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TipoNome extends AppCompatAutoCompleteTextView {

	List<String> tipiCompleti = new ArrayList<>();

	public TipoNome( Context contesto, AttributeSet as ) {
		super( contesto, as );
		for( int i = 0; i < 5; i++ ) {
			String tipo = U.NAME_TYPES[i];
			if( !Locale.getDefault().getLanguage().equals("en") )
				tipo += " - " + U.TIPI_NOME[i]; // Traduzione in tutte le lingue diverse dall'inglese
			tipiCompleti.add( tipo );
		}
		AdattatoreLista adattatoreLista = new AdattatoreLista( contesto, android.R.layout.simple_spinner_dropdown_item, tipiCompleti );
		setAdapter( adattatoreLista );
		setId( R.id.fatto_edita );
		//setThreshold(0); // inutile, il minimo è 1
		setInputType( InputType.TYPE_CLASS_TEXT );
		setOnItemClickListener( (parent, view, position, id) -> setText(U.NAME_TYPES[position]) );
		setOnFocusChangeListener( (view, hasFocus) -> {
			if( hasFocus )
				showDropDown();
		});
	}

	@Override
	public boolean enoughToFilter() {
		return true; // Mostra sempre i suggerimenti
	}

	class AdattatoreLista extends ArrayAdapter<String> {
		AdattatoreLista( Context contesto, int pezzo, List<String> stringhe ) {
			super( contesto, pezzo, stringhe );
		}
		@Override
		public Filter getFilter() {
			return new Filter() {
				@Override
				protected FilterResults performFiltering( CharSequence constraint ) {
					FilterResults result = new FilterResults();
					result.values = tipiCompleti;
					result.count = tipiCompleti.size();
					return result;
				}
				@Override
				protected void publishResults( CharSequence constraint, FilterResults results ) {
					notifyDataSetChanged();
				}
			};
		}
	}
}
