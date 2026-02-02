package app.familygem.main

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.ActionBar
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import app.familygem.Global
import app.familygem.R
import app.familygem.U
import app.familygem.util.TreeUtil
import me.zhanghai.android.fastscroll.FastScroller
import me.zhanghai.android.fastscroll.FastScrollerBuilder

abstract class BaseFragment(layout: Int) : Fragment(layout) {

    /** Determines whether the fragment content will be updated onResume(). */
    var mayShow = false

    /** Callback to apply insets to a fragment that extends BaseFragment. */
    var interfacer: ((insets: Insets) -> Unit)? = null
    var fastScroller: FastScroller? = null

    constructor() : this(0)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mayShow = true
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    /** Setups title and menu of the toolbar. */
    open fun updateToolbar(bar: ActionBar, menu: Menu, inflater: MenuInflater) {
    }

    /** Manages clicking on an item of the menu. */
    open fun selectItem(id: Int) {
    }

    /** Displays the fragment content only (not the toolbar). */
    open fun showContent() {
    }

    /** Returns true if there is an active search. */
    open fun isSearching(): Boolean {
        return false
    }

    /** Just makes the search text white. */
    fun stylizeSearchView(searchView: SearchView?) {
        searchView?.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)?.setTextColor(Color.WHITE)
    }

    /** Creates the FastScroller and an [interfacer] which sets insets for FAB, RecyclerView and FastScroller. */
    fun setInterfacer(fab: View, recyclerView: ViewGroup, scrollerAlso: Boolean = true) {
        if (scrollerAlso) {
            val thumbDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.thumb)
            val lineDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.empty)
            fastScroller = FastScrollerBuilder(recyclerView).setThumbDrawable(thumbDrawable!!).setTrackDrawable(lineDrawable!!).build()
        }
        interfacer = { insets ->
            fab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left; rightMargin = insets.right; bottomMargin = insets.bottom
            }
            val morePadding = if (this is PersonsFragment) U.dpToPx(8F) else 0
            val bottomPadding = insets.bottom + resources.getDimensionPixelSize(R.dimen.bottom_padding)
            recyclerView.updatePadding(insets.left + morePadding, morePadding, insets.right + morePadding, bottomPadding)
            fastScroller?.setPadding(insets.left, 0, insets.right, bottomPadding)
        }
    }

    // Called almost every time a fragment is displayed, except onBackPressed from another BaseFragment
    override fun onStart() {
        super.onStart()
        TreeUtil.isGlobalGedcomOk { // Checks for Global.gc not null
            onResume()
            requireActivity().invalidateMenu()
        }
    }

    /** Responsible to display the content and invalidate Global.edited if necessary. */
    override fun onResume() {
        super.onResume()
        if (Global.gc != null) {
            val mainActivity = requireActivity() as MainActivity
            if ((Global.edited || mayShow) && this == mainActivity.frontFragment) {
                showContent()
                mainActivity.refreshInterface()
                if (mainActivity.manager.fragments.size <= 1) Global.edited = false // Reset when first fragment of the stack is displayed
                // TODO: There is a logic issue using ActivityResult from ProfileActivity or from DetailActivity: the loaded fragment is always the first one in fragment manager
                mayShow = false
            }
        }
    }
}
