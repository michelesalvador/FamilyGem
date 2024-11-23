package app.familygem.merge

import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import app.familygem.BaseActivity
import app.familygem.R
import app.familygem.util.TreeUtil
import app.familygem.util.Util

class MergeActivity : BaseActivity() {

    private lateinit var navController: NavController
    private val model: MergeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.merge_activity)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Intercepts physical back button
        onBackPressedDispatcher.addCallback(this) { onSupportNavigateUp() }
    }

    // Intercepts the back arrow in actionbar
    override fun onSupportNavigateUp(): Boolean {
        val destination = navController.currentDestination?.id
        if (destination == R.id.choiceFragment && model.state.value == State.ACTIVE) {
            model.coroutine.cancel()
        } else if (destination == R.id.matchFragment) {
            model.previousMatch()
        } // Cancel active merging
        else if (destination == R.id.resultFragment && model.state.value == State.ACTIVE) {
            Util.confirmDelete(this) {
                model.coroutine.cancel()
                model.state.value = State.QUIET
                if (model.newNum > 0) {
                    TreeUtil.deleteTree(model.newNum)
                    model.newNum = 0
                }
                navController.navigateUp()
            }
            return false
        }
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
