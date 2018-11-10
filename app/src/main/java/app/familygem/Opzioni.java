package app.familygem;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

public class Opzioni extends AppCompatActivity {

	@Override
	protected void onCreate( Bundle stato ) {
		super.onCreate( stato );
		setContentView( R.layout.opzioni );

		// Modalità esperto
		((TextView)findViewById( R.id.opzioni_esperto_testo )).setText( R.string.show_advanced_functions );
		Switch esperto = findViewById( R.id.opzioni_esperto );
		esperto.setChecked( Globale.preferenze.esperto );
		esperto.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged( CompoundButton coso, boolean attivo ) {
				Globale.preferenze.esperto = attivo;
				Globale.preferenze.salva();
				Globale.editato = true;
			}
		});

		// Salvataggio volontario
		((TextView)findViewById( R.id.opzioni_salva_testo )).setText( R.string.choose_to_save );
		Switch salva = findViewById( R.id.opzioni_salva );
		salva.setChecked( Globale.preferenze.salvaVolontario );
		salva.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged( CompoundButton coso, boolean attivo ) {
				Globale.preferenze.salvaVolontario = attivo;
				Globale.preferenze.salva();
				Globale.editato = true;
			}
		});

	}
}
