package app.familygem

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton

/** Balloon popup with a hint that appears above the FAB. */
class FabPopup(context: Context, val fabBox: LinearLayout, textId: Int) {

    private val popup = LayoutInflater.from(context).inflate(R.layout.fab_popup_layout, null)

    init {
        popup.visibility = View.INVISIBLE
        fabBox.addView(
            popup, 0,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
        popup.findViewById<TextView>(R.id.fabPopup_text).setText(textId)
        popup.setOnTouchListener { _, _ ->
            hide()
            true
        }
        fabBox.findViewById<FloatingActionButton>(R.id.fab).setOnTouchListener { _, _ ->
            hide()
            false // To execute following click on FAB
        }
    }

    fun show() {
        fabBox.postDelayed({
            popup.visibility = View.VISIBLE
        }, 1000)
    }

    fun hide() {
        popup.visibility = View.INVISIBLE
    }
}
