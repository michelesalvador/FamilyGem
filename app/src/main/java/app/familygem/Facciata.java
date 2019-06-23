package app.familygem;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;

public class Facciata extends AppCompatActivity {

	Thread apertura;

	@Override
	protected void onCreate( Bundle stato ) {
		super.onCreate( stato );
		setContentView( R.layout.facciata );
		findViewById( R.id.facciata_circolo ).setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View v ) {
				apertura.interrupt();
				// ToDo https://stackoverflow.com/questions/36843785
				startActivity( new Intent( Facciata.this, Alberi.class ) );
			}
		});
		apertura = new Thread( new Runnable() {
			public void run() {
				if( Globale.preferenze.idAprendo == 0 )	// cioè praticamente alla prima apertura
					startActivity( new Intent( Facciata.this, AlberoNuovo.class) );
				else if( Globale.preferenze.caricaAlbero ) {
					if( Globale.gc == null ) {
						if( !Alberi.apriJson( Globale.preferenze.idAprendo, false ) )
							// TOdo file mancante -> Toast inchioda: Can't create handler inside thread that has not called Looper.prepare()
							startActivity( new Intent( Facciata.this, Lapide.class ) );
					}
					startActivity( new Intent( Facciata.this, Principe.class ) );
				} else
					startActivity( new Intent( Facciata.this, Alberi.class ) );
			}
		});
		apertura.start();
	}
}
