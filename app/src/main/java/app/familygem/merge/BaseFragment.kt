package app.familygem.merge

import android.view.View
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import app.familygem.R
import app.familygem.Settings.Tree
import app.familygem.TreesActivity
import app.familygem.U

open class BaseFragment(layout: Int) : Fragment(layout) {

    val model: MergeViewModel by activityViewModels()

    fun setupTreeView(treeView: View, tree: Tree) {
        treeView.background = ResourcesCompat.getDrawable(resources, R.drawable.generic_background, null)
        treeView.setPadding(U.dpToPx(15f), U.dpToPx(5f), U.dpToPx(15f), U.dpToPx(7f))
        treeView.findViewById<TextView>(R.id.albero_titolo).text = tree.title
        treeView.findViewById<TextView>(R.id.albero_dati).text = TreesActivity.writeData(context, tree)
        treeView.findViewById<View>(R.id.albero_menu).visibility = View.GONE
    }
}
