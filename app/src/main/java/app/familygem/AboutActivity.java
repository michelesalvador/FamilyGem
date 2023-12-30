package app.familygem;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class AboutActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.about_activity);

        TextView version = findViewById(R.id.about_version);
        version.setText(getString(R.string.version_name, BuildConfig.VERSION_NAME));

        TextView webSite = findViewById(R.id.about_link);
        if (Global.settings.premium)
            webSite.setOnClickListener(view -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.familygem.app"))));
        else
            webSite.setVisibility(View.GONE);
    }
}
