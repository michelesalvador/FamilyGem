package app.familygem;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.WindowManager;
import android.widget.ImageView;
import com.squareup.picasso.Picasso;
import java.io.File;

public class Lavagna extends AppCompatActivity {

	@Override
	protected void onCreate( Bundle stato ) {
		super.onCreate( stato );
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView( R.layout.lavagna );
		// Mostra il file a piena risoluzione
		String percorso = getIntent().getStringExtra( "percorso" );
		Picasso.get().load( new File(percorso) ).into( (ImageView)findViewById(R.id.lavagna_immagine) );
	}
}