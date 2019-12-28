package app.familygem;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;

public class Opzioni extends AppCompatActivity {

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		setContentView( R.layout.opzioni );

		// Salvataggio automatico
		Switch salva = findViewById( R.id.opzioni_salva );
		salva.setChecked( Globale.preferenze.autoSalva );
		salva.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged( CompoundButton coso, boolean attivo ) {
				Globale.preferenze.autoSalva = attivo;
				Globale.preferenze.salva();
			}
		});

		// Carica albero all'avvio
		Switch carica = findViewById( R.id.opzioni_carica );
		carica.setChecked( Globale.preferenze.caricaAlbero );
		carica.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged( CompoundButton coso, boolean attivo ) {
				Globale.preferenze.caricaAlbero = attivo;
				Globale.preferenze.salva();
			}
		});

		// Modalità esperto
		Switch esperto = findViewById( R.id.opzioni_esperto );
		esperto.setChecked( Globale.preferenze.esperto );
		esperto.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged( CompoundButton coso, boolean attivo ) {
				Globale.preferenze.esperto = attivo;
				Globale.preferenze.salva();
				Globale.editato = true;
			}
		});

		findViewById( R.id.opzioni_lapide ).setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View view ) {
				startActivity( new Intent( Opzioni.this, Lapide.class) );
			}
		});
	}
}
