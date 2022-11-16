package app.familygem;

import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

public class TombstoneActivity extends BaseActivity {

	@Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.lapide);

		TextView version = findViewById(R.id.lapide_versione);
		version.setText(getString(R.string.version_name, BuildConfig.VERSION_NAME));

		TextView link = findViewById(R.id.lapide_link);
		//TODO replace with LinkMovementMethod and (or?) LinkifyCompat.addLinks()
		link.setPaintFlags(link.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
		link.setOnClickListener(v -> startActivity(
				new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.familygem.app")))
		);
	}
}
