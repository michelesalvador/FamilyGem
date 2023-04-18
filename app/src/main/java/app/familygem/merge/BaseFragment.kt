package app.familygem.merge

import android.view.View
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import app.familygem.R
import app.familygem.Settings.Tree
import app.familygem.U
import app.familygem.util.getBasicData

open class BaseFragment(layout: Int) : Fragment(layout) {

    val model: MergeViewModel by activityViewModels()

    fun setupTreeView(treeView: View, tree: Tree) {
        treeView.background = ResourcesCompat.getDrawable(resources, R.drawable.generic_background, null)
        val layout = treeView.findViewById<RelativeLayout>(R.id.tree_layout)
        layout.setPadding(U.dpToPx(15f), U.dpToPx(5f), U.dpToPx(15f), U.dpToPx(7f))
        layout.findViewById<TextView>(R.id.tree_title).text = tree.title
        layout.findViewById<TextView>(R.id.tree_data).text = tree.getBasicData()
        layout.findViewById<ImageButton>(R.id.tree_menu).visibility = View.GONE
    }
}
