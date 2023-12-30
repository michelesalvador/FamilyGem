package app.familygem.purchase

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.familygem.R
import app.familygem.constant.Extra

/**
 * Here the user can buy Family Gem Premium to activate some premium feature.
 */
class PurchaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.purchase_activity)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        setTitle(intent.getIntExtra(Extra.STRING, 0))
    }

    // Back arrow on actionbar
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return super.onSupportNavigateUp()
    }
}
