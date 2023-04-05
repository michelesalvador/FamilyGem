package app.familygem.merge

import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import app.familygem.BaseActivity
import app.familygem.R

class MergeActivity : BaseActivity() {

    private lateinit var navController: NavController
    private val model: MergeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.merge_activity)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Physical back button
        onBackPressedDispatcher.addCallback(this) {
            onSupportNavigateUp()
        }
    }

    // Intercepts the back arrow in actionbar
    override fun onSupportNavigateUp(): Boolean {
        if (navController.currentDestination?.id == R.id.matchFragment)
            model.previousMatch()
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
