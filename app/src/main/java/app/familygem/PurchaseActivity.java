package app.familygem;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;

import app.familygem.constant.Extra;

/**
 * Here you can buy Family Gem Premium from Google Play.
 */
public class PurchaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.purchase_activity);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        int titleId = getIntent().getIntExtra(Extra.STRING, 0);
        setTitle(titleId);
        ProductLayout productLayout = findViewById(R.id.purchase_product);
        productLayout.initialize(() -> runOnUiThread(this::onBackPressed));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        onBackPressed();
        return true;
    }
}
