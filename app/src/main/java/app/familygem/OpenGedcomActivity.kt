package app.familygem

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import app.familygem.util.TreeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OpenGedcomActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.launcher_activity)

        val uri = intent.data
        if (uri != null) {
            lifecycleScope.launch(Dispatchers.Default) {
                TreeUtils.importGedcom(this@OpenGedcomActivity, uri, {
                    // Successful conclusion of importing
                    startActivity(Intent(this@OpenGedcomActivity, TreesActivity::class.java))
                    Toast.makeText(this@OpenGedcomActivity, R.string.tree_imported_ok, Toast.LENGTH_LONG).show()
                }, { // Unsuccessful conclusion of importing
                    startActivity(Intent(this@OpenGedcomActivity, TreesActivity::class.java))
                })
            }
        } else {
            startActivity(Intent(this, TreesActivity::class.java))
            Toast.makeText(this, R.string.something_wrong, Toast.LENGTH_LONG).show()
        }
    }
}
