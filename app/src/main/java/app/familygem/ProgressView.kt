package app.familygem

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** A progress wheel plus percent progress bar. */
class ProgressView(context: Context, attrs: AttributeSet) : RelativeLayout(context, attrs) {

    private val circle: ProgressBar
    private val box: View
    private val textView: TextView
    private val bar: ProgressBar
    private var maximum: Long = 0
    private val lifecycleScope: CoroutineScope?
        get() = findViewTreeLifecycleOwner()?.lifecycleScope

    var progress: Long
        set(value) {
            if (maximum > 0) bar.progress = (value * 100 / maximum).toInt()
        }
        get() = bar.progress * maximum / 100

    init {
        val view = inflate(context, R.layout.progress_view, this)
        circle = view.findViewById(R.id.progress_circle)
        box = view.findViewById(R.id.progress_box)
        textView = view.findViewById(R.id.progress_text)
        bar = view.findViewById(R.id.progress_bar)
        box.setOnClickListener {
            Toast.makeText(context, "${bar.progress}%", Toast.LENGTH_SHORT).show()
        }
    }

    fun displayBar(text: String, max: Long) {
        if (max > 0) {
            lifecycleScope?.launch {
                circle.visibility = GONE
                box.visibility = VISIBLE
                textView.text = text
                maximum = max
                bar.progress = 0
            }
        }
    }

    fun hideBar() {
        lifecycleScope?.launch {
            box.visibility = GONE
            circle.visibility = VISIBLE
        }
    }
}
