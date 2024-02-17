package app.familygem.main

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.ActionBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import app.familygem.Global
import app.familygem.R
import app.familygem.util.TreeUtil
import me.zhanghai.android.fastscroll.FastScrollerBuilder

abstract class BaseFragment(layout: Int) : Fragment(layout) {

    /** Determinates whether the fragment content will be updated onResume(). */
    var mayShow = false

    constructor() : this(0)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mayShow = true
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    /**
     * Setups title and menu of the toolbar.
     */
    open fun updateToolbar(bar: ActionBar, menu: Menu, inflater: MenuInflater) {
    }

    /**
     * Manages clicking on an item of the menu.
     */
    open fun selectItem(id: Int) {
    }

    /**
     * Displays the fragment content only (not the toolbar).
     */
    open fun showContent() {
    }

    fun setupFastScroller(recyclerView: RecyclerView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val thumbDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.thumb)
            val lineDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.empty)
            FastScrollerBuilder(recyclerView).setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.bottom_padding))
                    .setThumbDrawable(thumbDrawable!!).setTrackDrawable(lineDrawable!!).build()
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

    /**
     * Responsible to display the content and invalidate Global.edited if necessary.
     */
    override fun onResume() {
        super.onResume()
        if (Global.gc != null) {
            val mainActivity = requireActivity() as MainActivity
            if ((Global.edited || mayShow) && this == mainActivity.frontFragment) {
                showContent()
                mainActivity.refreshInterface()
                if (mainActivity.manager.fragments.size <= 1) Global.edited = false // Reset when first fragment of the stack is displayed
                mayShow = false
            }
        }
    }
}
