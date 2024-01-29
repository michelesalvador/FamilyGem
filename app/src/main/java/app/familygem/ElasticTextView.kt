package app.familygem

import android.content.Context
import android.text.Layout
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.ceil

/**
 * TextView that shrinks correctly the width even when contains multiple lines.
 */
class ElasticTextView(context: Context, attrs: AttributeSet?) : AppCompatTextView(context, attrs) {

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        var widthSpec = widthSpec
        val layout = layout
        if (layout != null) {
            val maxWidth = ceil(getMaxLineWidth(layout).toDouble()).toInt()
            widthSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST)
        }
        super.onMeasure(widthSpec, heightSpec)
    }

    private fun getMaxLineWidth(layout: Layout): Float {
        var maxWidth = 0f
        for (i in 0 until layout.lineCount) {
            if (layout.getLineWidth(i) > maxWidth) {
                maxWidth = layout.getLineWidth(i)
            }
        }
        return maxWidth
    }
}
