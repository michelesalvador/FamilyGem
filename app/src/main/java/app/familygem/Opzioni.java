package app.familygem;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.Switch;

public class Opzioni extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.opzioni);

		// Salvataggio automatico
		Switch salva = findViewById(R.id.opzioni_salva);
		salva.setChecked(Global.settings.autoSave);
		salva.setOnCheckedChangeListener((coso, attivo) -> {
			Global.settings.autoSave = attivo;
			Global.settings.save();
		});

		// Carica albero all'avvio
		Switch carica = findViewById(R.id.opzioni_carica);
		carica.setChecked(Global.settings.loadTree);
		carica.setOnCheckedChangeListener((coso, attivo) -> {
			Global.settings.loadTree = attivo;
			Global.settings.save();
		});

		// Modalità esperto
		Switch esperto = findViewById(R.id.opzioni_esperto);
		esperto.setChecked(Global.settings.expert);
		esperto.setOnCheckedChangeListener((coso, attivo) -> {
			Global.settings.expert = attivo;
			Global.settings.save();
		});

		findViewById(R.id.opzioni_lapide).setOnClickListener(view -> startActivity(
				new Intent(Opzioni.this, Lapide.class)
		));
	}
}
