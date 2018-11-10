package app.familygem;

import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

public class Lapide extends AppCompatActivity {

	@Override
	protected void onCreate( Bundle stato ) {
		super.onCreate( stato );
		setContentView( R.layout.lapide );

		TextView versione = findViewById( R.id.lapide_versione );
		versione.setText( getString(R.string.version_name,BuildConfig.VERSION_NAME) );

		TextView collega = findViewById( R.id.lapide_link );
		collega.setPaintFlags( collega.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG );
		collega.setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View v ) {
				startActivity( new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.familygem.app")) );
			}
		});
	}
}
