package app.familygem

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import app.familygem.databinding.BaseActivityBinding
import app.familygem.util.InsetsUtil
import com.google.android.material.floatingactionbutton.FloatingActionButton

/** Extendable activity that provides basic elements: toolbar, progress bar and floating action button. */
abstract class BaseActivity(val titleId: Int? = null) : AppCompatActivity() {

    lateinit var bar: Toolbar
    lateinit var mainView: ViewGroup
    lateinit var contentView: FrameLayout
    lateinit var progressView: ProgressView
    var fabBox: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BaseActivityBinding.inflate(layoutInflater).run {
            setContentView(root)
            bar = baseToolbar
            mainView = baseScrollView
            contentView = baseContent
            progressView = baseProgress
            fabBox = baseFab.root
            InsetsUtil(root) {
                var bottomPadding = it.bottom
                if (fabBox?.isVisible == true) bottomPadding += resources.getDimensionPixelSize(R.dimen.bottom_padding)
                mainView.updatePadding(it.left, it.top, it.right, bottomPadding)
                fabBox?.updateLayoutParams<ViewGroup.MarginLayoutParams> { rightMargin = it.right; bottomMargin = it.bottom }
            }
        }
        bar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        if (titleId != null) bar.title = getString(titleId)
        progressView.visibility = View.GONE
        fabBox?.visibility = View.GONE
    }

    /** Adds the content inside the scroll view. */
    fun setContent(content: View) {
        contentView.addView(content)
    }

    fun getVisibleFab(): FloatingActionButton {
        fabBox?.visibility = View.VISIBLE
        return fabBox?.findViewById(R.id.fab)!!
    }
}
