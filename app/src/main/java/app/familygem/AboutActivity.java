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
        webSite.setOnClickListener(view -> startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.familygem.app"))));

        // Premium product layout
        View subscribedLayout = findViewById(R.id.about_subscribed);
        if (Global.premium) {
            subscribedLayout.setVisibility(View.VISIBLE);
        } else {
            webSite.setVisibility(View.GONE);
            ProductLayout productLayout = findViewById(R.id.about_product);
            productLayout.initialize(() -> { // Makes it also visible
                runOnUiThread(() -> {
                    productLayout.setVisibility(View.GONE);
                    subscribedLayout.setVisibility(View.VISIBLE);
                });
            });
        }
    }
}
