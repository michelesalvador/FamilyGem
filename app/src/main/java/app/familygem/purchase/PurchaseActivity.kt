package app.familygem.purchase

import android.os.Bundle
import android.view.LayoutInflater
import app.familygem.BaseActivity
import app.familygem.R
import app.familygem.constant.Extra

/** Here the user can buy Family Gem Premium to activate some premium feature. */
class PurchaseActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bar.setTitle(intent.getIntExtra(Extra.STRING, 0))
        setContent(LayoutInflater.from(this).inflate(R.layout.purchase_activity, null))
    }
}
