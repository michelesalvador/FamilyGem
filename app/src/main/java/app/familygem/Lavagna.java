package app.familygem;

import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.WindowManager;
import android.widget.ImageView;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import java.io.File;

public class Lavagna extends AppCompatActivity {

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView( R.layout.lavagna );
		// Mostra il file a piena risoluzione
		String percorso = getIntent().getStringExtra( "percorso" );
		Picasso picasso = Picasso.get();
		RequestCreator creatore;
		if( percorso != null ) {
			creatore = picasso.load( new File(percorso) );
		} else {
			Uri uri = Uri.parse( getIntent().getStringExtra("uri") );
			creatore = picasso.load( uri );
		}
		creatore.into( (ImageView)findViewById(R.id.lavagna_immagine) );
	}
}