package app.familygem.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import app.familygem.Global
import app.familygem.Memory
import app.familygem.R
import org.folg.gedcom.model.Person

abstract class BaseFragment : Fragment() {

    lateinit var layout: LinearLayout
    lateinit var person: Person

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val pageView = inflater.inflate(R.layout.profile_page_fragment, container, false)
        layout = pageView.findViewById(R.id.profile_page)
        createContent()
        return pageView
    }

    /** Must be called at the beginning of [createContent] */
    fun prepareContent(): Boolean {
        if (Global.gc == null) return false
        Memory.getLeaderObject()?.let { person = it as Person } ?: return false
        layout.removeAllViews()
        return true
    }

    abstract fun createContent()

    /** Updates all activity content. */
    fun refresh() {
        // Theoretically activity could be null
        activity?.let { (it as ProfileActivity).refresh() }
    }
}
